package ch.seidel.mobilequeue.akka

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import akka.stream.{ Graph, OverflowStrategy, SinkShape }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.Timeout

import spray.json._

import ch.seidel.mobilequeue.akka.EventRegistryActor.CreateEventTicket
import ch.seidel.mobilequeue.akka.EventRegistryActor.CloseEventTicket
import ch.seidel.mobilequeue.akka.TicketRegistryActor.{ ActionPerformed => TicketActionPerformed, JoinTicket }
import ch.seidel.mobilequeue.akka.UserRegistryActor.{ ActionPerformed => UserActionPerformed, Authenticate }
import ch.seidel.mobilequeue.akka.UserRegistryActor.{ PropagateTicketIssued, PropagateTicketCalled, PropagateTicketSkipped, PropagateTicketClosed, PropagateTicketConfirmed }
import ch.seidel.mobilequeue.http.JsonSupport
import ch.seidel.mobilequeue.model._

class ClientActor(eventRegistryActor: ActorRef, userRegistryActor: ActorRef) extends Actor with JsonSupport /*with Hashing*/ {
  import akka.pattern.pipe
  // Get the implicit ExecutionContext from this import
  import scala.concurrent.ExecutionContext.Implicits.global
  import context._
  // these states are bound to the active connection to the user's device and won't be persisted
  var wsSend: Option[ActorRef] = None
  var pendingKeepAliveAck: Option[Int] = None
  var pendingTicketAcks: Map[TicketCalled, ActorRef] = Map.empty
  var pendingTicketAcksCleanupPhases: Map[Ticket, Int] = Map.empty

  // send keepalive messages to prevent closing the websocket connection
  private case object KeepAlive
  val liveticker = context.system.scheduler.schedule(15.second, 15.second) {
    self ! KeepAlive
  }

  override def preStart(): Unit = {
    println("Starting client actor")
  }

  override def postStop: Unit = {
    liveticker.cancel()
    println("client actor stopped")
    wsSend.foreach(context.stop)
  }

  /**
   * subscribed stage (authenticated + subscribed)
   */
  def subscribed(user: User, tickets: Set[Ticket]): Receive = {
    // user actions
    case s @ UnSubscribe(channel) =>
      println(s"UnSubscribe $s")
      val removed = removeObservedTicket(user, tickets, channel)
      removed.foreach(t => {
        eventRegistryActor ! CloseEventTicket(t.eventid, t.id)
      })

    // user events
    case tc: TicketConfirmed =>
      handleTicketConfirmed(tc)
      removeObservedTicket(user, tickets, tc.ticket.eventid)
      userRegistryActor ! PropagateTicketConfirmed(tc, sender)

    case PropagateTicketConfirmed(tc: TicketConfirmed, ticketActor: ActorRef) =>
      handleTicketConfirmed(tc)
      removeObservedTicket(user, tickets, tc.ticket.eventid)

    case ts: TicketSkipped =>
      handleTicketSkipped(ts)
      userRegistryActor ! PropagateTicketSkipped(ts, sender)

    case PropagateTicketSkipped(ts: TicketSkipped, ticketActor: ActorRef) =>
      handleTicketSkipped(ts)

    // system events
    case tc: TicketCalled =>
      handleTicketCalled(tc, sender)
      userRegistryActor ! PropagateTicketCalled(tc, sender)

    case PropagateTicketCalled(tc: TicketCalled, ticketActor: ActorRef) =>
      handleTicketCalled(tc, ticketActor)

    case ta: TicketActionPerformed => // println(ta)
    case KeepAlive => handleKeepAlive

    // handle authenticated messages for other subscriptions and take care of handleStop
    case msg: Any => authenticated(user, tickets)(msg)
  }

  /**
   * authenticated stage (authenticated but not subscribed)
   */
  def authenticated(user: User, ticket: Set[Ticket] = Set.empty): Receive = {
    // user actions
    case s @ Subscribe(channel, cnt) =>
      println(s"Subscribe $s")
      eventRegistryActor ! CreateEventTicket(Ticket(0L, user.id, channel, Requested, cnt), self)

    // system events
    case ti: TicketIssued =>
      handleTicketIssued(ti, user, ticket)
      userRegistryActor ! PropagateTicketIssued(ti, sender)

    case PropagateTicketIssued(ti: TicketIssued, ticketActor: ActorRef) =>
      eventRegistryActor ! JoinTicket(ti.ticket, self)
      handleTicketIssued(ti, user, ticket)

    case tra: TicketReactivated =>
      val tm = TextMessage(tra.toJson.toJsonStringWithType(tra))
      tra.ticket.state match {
        case Called =>
          wsSend.foreach(_ ! tm)
          handleTicketCalled(TicketCalled(tra.ticket), sender)
        case _ =>
          wsSend.foreach(_ ! tm)
      }
      become(subscribed(user, ticket + tra.ticket))

    case tc: TicketClosed =>
      val tm = TextMessage(tc.toJson.toJsonStringWithType(tc))
      wsSend.foreach(_ ! tm)
      userRegistryActor ! PropagateTicketClosed(tc, sender)

    case PropagateTicketClosed(tc: TicketClosed, ticketActor: ActorRef) =>
      val tm = TextMessage(tc.toJson.toJsonStringWithType(tc))
      wsSend.foreach(_ ! tm)

    // system actions
    case ts: UserTicketsSummary =>
      val tm = TextMessage(ts.toJson.toJsonStringWithType(ts))
      wsSend.foreach(_ ! tm)

    case KeepAlive => handleKeepAlive

    // handle authenticated messages for other subscriptions and take care of handleStop
    case msg: Any => receive(msg)
  }

  /**
   * initial stage (unauthenticated + unsubscribed)
   */
  override def receive = {
    // user actions
    case h @ HelloImOnline(username, deviceId) =>
      val di = deviceId.filter(i => i != "").getOrElse(UUID.randomUUID().toString())
      userRegistryActor ! Authenticate(username, "", di)

    // system events
    case UserActionPerformed(authenticatedUser, reason) =>
      if (authenticatedUser.deviceIds.isEmpty) {
        wsSend.foreach(_ ! TextMessage(reason))
      } else {
        wsSend.foreach(_ ! TextMessage("deviceId=" + authenticatedUser.deviceIds.headOption.getOrElse("")))
        become(authenticated(authenticatedUser))
      }

    case ref: ActorRef =>
      wsSend = Some(ref)
      wsSend.foreach(_ ! TextMessage("Connection established. Please authenticate with Username and Password!"))

    // system actions
    case KeepAlive => println("i'm still alive")
    case MessageAck(txt) if (txt.equals("keepAlive")) => handleKeepAliveAck

    case ClientActor.Stop => handleStop
    case _: Unit => handleStop
    case _ =>
  }

  private def handleStop {
    println("Closing client actor")
    stop(self)
  }

  private def handleKeepAlive {
    wsSend.foreach(_ ! TextMessage("keepAlive"))
    pendingKeepAliveAck = pendingKeepAliveAck.map(_ + 1) match {
      case Some(i) if (i < 10) =>
        recallAndCleanupPendingTicketAcks
        Some(i)
      case Some(i) if (i >= 10) =>
        handleStop
        None
      case _ =>
        recallAndCleanupPendingTicketAcks
        Some(1)
    }
  }

  private def handleKeepAliveAck {
    pendingKeepAliveAck = pendingKeepAliveAck.map(_ - 1) match {
      case Some(i) if (i > 0) => Some(i)
      case _ => None
    }
  }

  private def nextPendingStage(ticket: Ticket): Int = {
    pendingTicketAcksCleanupPhases.get(ticket).getOrElse(0) + 1
  }

  private def recallAndCleanupPendingTicketAcks {
    // cleanup pending tickets when they're two keepalive cycles unused
    pendingTicketAcksCleanupPhases = pendingTicketAcks.foldLeft(Map[Ticket, Int]()) { (acc, pendingTicket) =>
      val pendingStage = nextPendingStage(pendingTicket._1.ticket)
      if (pendingStage > 2) {
        pendingTicketAcks -= pendingTicket._1
        val te = TicketExpired(pendingTicket._1.ticket)
        val tm = TextMessage(te.toJson.toJsonStringWithType(te))
        wsSend.foreach(_ ! tm)
        acc
      } else {
        acc + (pendingTicket._1.ticket -> pendingStage)
      }
    }
  }

  private def handleTicketIssued(ti: TicketIssued, user: User, ticket: Set[Ticket]) {
    val tm = TextMessage(ti.toJson.toJsonStringWithType(ti))
    wsSend.foreach(_ ! tm)
    println("ticket issued delivered from " + self)
    become(subscribed(user, ticket + ti.ticket))
  }

  private def handleTicketCalled(tc: TicketCalled, ticketActor: ActorRef) {
    pendingTicketAcks += (tc -> ticketActor)
    val tm = TextMessage(tc.toJson.toJsonStringWithType(tc))
    wsSend.foreach(_ ! tm)
  }

  private def handleTicketConfirmed(tcm: TicketConfirmed) {
    pendingTicketAcks = pendingTicketAcks
      .foldLeft(Map[TicketCalled, ActorRef]()) { (acc, entry) =>
        if (entry._1.ticket.id == tcm.ticket.id) {
          entry._2 ! tcm
          val ta = TicketAccepted(tcm.ticket.copy(state = Confirmed))
          val tm = TextMessage(ta.toJson.toJsonStringWithType(ta))
          wsSend.foreach(_ ! tm)
          acc
        } else {
          acc + entry;
        }
      }
  }

  def removeObservedTicket(user: User, tickets: Set[Ticket], channel: Long) = {
    val selectedTickets = tickets.filter(t => t.eventid == channel)
    val remainingTickets = tickets -- selectedTickets
    if (remainingTickets.nonEmpty) {
      become(subscribed(user, remainingTickets))
    } else {
      become(authenticated(user, remainingTickets))
    }
    selectedTickets
  }

  private def handleTicketSkipped(tsk: TicketSkipped) {
    pendingTicketAcks = pendingTicketAcks
      .foldLeft(Map[TicketCalled, ActorRef]()) { (acc, entry) =>
        if (entry._1.ticket.id == tsk.ticket.id) {
          entry._2 ! tsk
          acc
        } else {
          acc + entry;
        }
      }
    val ta = TicketSkipped(tsk.ticket.copy(state = Skipped))
    val tm = TextMessage(ta.toJson.toJsonStringWithType(ta))
    wsSend.foreach(_ ! tm)
  }
}

object ClientActor extends JsonSupport with EnrichedJson {
  case object Stop

  import ch.seidel.mobilequeue.app.Core._

  def reportErrorsFlow[T]: Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          println(s"WS stream failed with $cause")
        case _ => // ignore regular completion
          println(s"WS stream closed")
      })

  def tryMapText(text: String): MobileTicketQueueProtokoll = try {
    text.asType[MobileTicketQueueProtokoll]
  } catch {
    case e: Exception => MessageAck(text)
  }

  def websocketFlow: Flow[Message, MobileTicketQueueProtokoll, Any] =
    Flow[Message]
      .mapAsync(1) {
        case TextMessage.Strict(text) => Future.successful(tryMapText(text))
        case TextMessage.Streamed(stream) => stream.runFold("")(_ + _).map(tryMapText(_))
        case b: BinaryMessage => throw new Exception("Binary message cannot be handled")
      }.via(reportErrorsFlow)

  def createActorSinkSource(clientActor: ActorRef): Flow[Message, Message, Any] = {

    val source: Source[Nothing, ActorRef] = Source.actorRef(256, OverflowStrategy.dropNew).mapMaterializedValue { wsSend =>
      clientActor ! wsSend
      wsSend
    }

    val sink = websocketFlow.to(Sink.actorRef(clientActor, Stop))

    Flow.fromSinkAndSource(sink, source)
  }
}