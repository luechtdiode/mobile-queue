package ch.seidel.mobilequeue.akka

import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import akka.stream.{ Graph, OverflowStrategy, SinkShape }
import akka.stream.scaladsl.{ Flow, Sink, Source }
//import akka.pattern.ask
import akka.util.Timeout

import spray.json._

import ch.seidel.mobilequeue.akka.EventRegistryActor.CreateEventTicket
import ch.seidel.mobilequeue.akka.EventRegistryActor.DeleteEventTicket
import ch.seidel.mobilequeue.akka.TicketRegistryActor.{ ActionPerformed => TicketActionPerformed }
import ch.seidel.mobilequeue.akka.UserRegistryActor.{ ActionPerformed => UserActionPerformed }
import ch.seidel.mobilequeue.akka.UserRegistryActor.Authenticate
import ch.seidel.mobilequeue.http.JsonSupport
import ch.seidel.mobilequeue.model._

class ClientActor(eventRegistryActor: ActorRef, userRegistryActor: ActorRef) extends Actor with Hashing with JsonSupport {
  import akka.pattern.pipe
  // Get the implicit ExecutionContext from this import
  import scala.concurrent.ExecutionContext.Implicits.global
  // Required by the `ask` (?) method below
  private implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration
  private case object KeepAlive
  //  val mediator = DistributedPubSub(context.system).mediator
  var wsSend: Option[ActorRef] = None
  var user: Option[User] = None
  var ticket: Set[Ticket] = Set.empty
  var pendingTicketAks: Map[TicketCalled, ActorRef] = Map.empty
  var pendingTicketAksCleanupPhases: Map[TicketCalled, Int] = Map.empty

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

  def authenticated: Receive = {
    case KeepAlive =>
      wsSend.foreach(_ ! TextMessage("keepAlive"))
      // cleanup pending tickets when they're two keepalive cycles unused
      pendingTicketAksCleanupPhases = pendingTicketAks.foldLeft(Map[TicketCalled, Int]()) { (acc, pendingTicket) =>
        val pendingStage = pendingTicketAksCleanupPhases.get(pendingTicket._1).getOrElse(0) + 1
        if (pendingStage > 2) {
          pendingTicketAks -= pendingTicket._1
          val ta = TicketActionPerformed(pendingTicket._1.ticket, s"unused -> ticket with ${pendingTicket._1.count} persons has expired")
          val tm = TextMessage(ta.toJson.toJsonStringWithType(ta))
          wsSend.foreach(_ ! tm)
          acc
        } else {
          acc + (pendingTicket._1 -> pendingStage)
        }
      }

    case tc @ TicketCalled(t, cnt) =>
      if (sender.path.name.startsWith(TicketRegistryActor.name)) {
        pendingTicketAks += (tc -> sender())
        val tm = TextMessage(tc.toJson.toJsonStringWithType(tc))
        wsSend.foreach(_ ! tm)
      } else if (pendingTicketAks.keySet.contains(tc)) {
        pendingTicketAks(tc) ! tc
        pendingTicketAks -= tc
      }

    case ts @ TicketSkipped(t, cnt) =>
      val tc = TicketCalled(t, cnt)
      if (pendingTicketAks.keySet.contains(tc)) {
        pendingTicketAks(tc) ! ts
        pendingTicketAks -= tc
      }
      
    case tra: TicketReactivated =>
      ticket += tra.ticket
      val tm = TextMessage(tra.toJson.toJsonStringWithType(tra))
      println("sending TicketReactivated from " + self.path + " to " + wsSend)
      wsSend.foreach(_ ! tm)

    case ta @ TicketActionPerformed(t, text) =>
      ticket += t
      val tm = TextMessage(ta.toJson.toJsonStringWithType(ta))
      println("sending TicketActionPerformed from " + self.path + " to " + wsSend)
      wsSend.foreach(_ ! tm)

    case s @ Subscribe(channel, cnt) =>
      println(s"Subscribe $s")
      eventRegistryActor ! CreateEventTicket(Ticket(0L, user.get.id, channel, "", ""), cnt, self)

    case s @ UnSubscribe(channel) =>
      println(s"UnSubscribe $s")
      ticket.filter(t => t.eventid == channel).foreach(t => {
        ticket -= t
        eventRegistryActor ! DeleteEventTicket(t.eventid, t.id)
      })

    case ClientActor.Stop =>
      println("Closing client actor")
      context.stop(self)

    case _: Unit =>
      println("Closing client actor")
      context.stop(self)
  }

  override def receive = {
    case ref: ActorRef =>
      wsSend = Some(ref)
      wsSend.foreach(_ ! TextMessage("Connection established. Please authenticate with Username and Password!"))

    case KeepAlive =>
      println("i'm still alive")

    case h @ HelloImOnline(username, deviceId) =>
      val di = deviceId.filter(i => i != "").getOrElse(UUID.randomUUID().toString())
      userRegistryActor ! Authenticate(username, "", di) // pipeTo self

    case UserActionPerformed(authenticatedUser, reason) =>
      if (authenticatedUser.deviceIds.isEmpty) {
        wsSend.foreach(_ ! TextMessage(reason))
      } else {
        user = Some(authenticatedUser)
        wsSend.foreach(_ ! TextMessage("deviceId=" + authenticatedUser.deviceIds.headOption.getOrElse("")))
        context.become(authenticated)
      }

    case ClientActor.Stop =>
      println("Closing client actor")
      context.stop(self)
    case _: Unit =>
      println("Closing client actor")
      context.stop(self)

  }
}

object ClientActor extends JsonSupport with EnrichedJson {
  case object Stop

  import ch.seidel.mobilequeue.app.Core._

  def websocketFlow(actorRef: ActorRef): Flow[Message, PubSub, Any] =
    Flow[Message]
      .mapAsync(1) {
        case TextMessage.Strict(text) => Future.successful(text.asType[PubSub])
        case TextMessage.Streamed(stream) => stream.runFold("")(_ + _).map(s => s.asType[PubSub])
        case b: BinaryMessage => throw new Exception("Binary message cannot be handled")
      }.via(reportErrorsFlow(actorRef))

  def reportErrorsFlow[T](actorRef: ActorRef): Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          println(s"WS stream failed with $cause")
        case _ => // ignore regular completion
          println(s"WS stream closed")
      })

  def createActorSinkSource(clientActor: ActorRef): Flow[Message, Message, Any] = {

    val source: Source[Nothing, ActorRef] = Source.actorRef(1024, OverflowStrategy.dropNew).mapMaterializedValue { wsSend =>
      clientActor ! wsSend
      wsSend
    }

    val sink: Graph[SinkShape[Message], Any] = websocketFlow(clientActor).to(Sink.actorRef(clientActor, Stop))

    Flow.fromSinkAndSource(sink, source)
  }
}