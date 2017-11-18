package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props, ActorRef, Terminated }
//import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.Failure

import ch.seidel.mobilequeue.model._
import ch.seidel.mobilequeue.akka.UserRegistryActor.ClientConnected
import ch.seidel.mobilequeue.akka.EventRegistryActor.ConnectEventUser

object TicketRegistryActor {
  sealed trait TicketRegistryMessage
  final case object GetTickets extends TicketRegistryMessage
  final case class GetNextTickets(next: Int) extends TicketRegistryMessage
  final case object GetAccepted extends TicketRegistryMessage
  final case class CreateTicket(ticket: Ticket, client: ActorRef) extends TicketRegistryMessage
  final case class JoinTicket(ticket: Ticket, client: ActorRef) extends TicketRegistryMessage
  final case class GetTicket(id: Long) extends TicketRegistryMessage
  final case class CloseTicket(id: Long) extends TicketRegistryMessage

  sealed trait TicketRegistryEvent
  final case class OwnerEventDeleted(event: Event) extends TicketRegistryEvent
  final case class ActionPerformed(ticket: Ticket, description: String) extends TicketRegistryEvent
  final case class TicketCreated(ticket: TicketIssued, requestingClient: ActorRef) extends TicketRegistryEvent
  final case class EventTicketsSummary(event: Event, invites: Iterable[Ticket]) extends TicketRegistryEvent {
    private lazy val waiting = invites.filter(t => t.state match { case Issued => true case Called => true case Skipped => true case _ => false })
    private lazy val waitingCnt = waiting.map(_.participants).sum
    private lazy val calledCnt = invites.filter(t => t.state match { case Confirmed => true case Called => true case Skipped => true case _ => false }).map(_.participants).sum
    private lazy val acceptedCnt = invites.filter(t => t.state == Confirmed).map(_.participants).sum
    private lazy val skippedCnt = invites.filter(t => t.state == Skipped).map(_.participants).sum
    private lazy val closedCnt = invites.filter(t => t.state == Closed).map(_.participants).sum

    def toUserTicketSummary(eventId: Long, userId: Long, groupSize: Int) = {
      val isGroupIdxRelevant = waiting.exists(t => t.userid == userId)
      val (groupIdx, _) = if (!isGroupIdxRelevant) (0, 0) else waiting.toSeq.sortBy(_.id).takeWhile(t => t.userid != userId).foldLeft((1, 0)) { (acc, ticket) =>
        val (groupIdx, groupSize) = acc
        val newGroupSize = groupSize + ticket.participants
        if (newGroupSize > groupSize) {
          (groupIdx + 1, ticket.participants)
        } else {
          (groupIdx, newGroupSize)
        }
      }
      UserTicketsSummary(eventId, groupIdx, waitingCnt, calledCnt, acceptedCnt, skippedCnt, closedCnt)
    }
  }

  val name = "ticketRegistryActor"
  def props(event: Event) = Props(classOf[TicketRegistryActor], event)
}

class TicketRegistryActor(event: Event) extends Actor /*with ActorLogging*/ {
  import TicketRegistryActor._
  import context._

  import akka.pattern.pipe
  // Get the implicit ExecutionContext from this import
  import scala.concurrent.ExecutionContext.Implicits.global

  val WITHOUT_SKIPPED = false;
  val WITHOUT_CLOSING = false;

  var requiredGroupSize: Int = event.groupsize
  var eventOwner: Option[ActorRef] = None

  override def postStop: Unit = {
    eventOwner match {
      case Some(clientActor) =>
        clientActor ! OwnerEventDeleted(event)
        eventOwner = None
      case _ =>
    }
  }

  case class TicketClientHolder(ticket: Ticket, clients: Set[ActorRef]) {
    def toNewState(newstate: TicketState) = {
      val ticketCopy = ticket.copy(state = newstate)
      val thcopy = copy(ticket = ticketCopy)
      thcopy
    }
    def sendSummaryToClient(summary: EventTicketsSummary) {
      val userTicketSummary = summary.toUserTicketSummary(event.id, ticket.userid, requiredGroupSize)
      clients.foreach(_ ! userTicketSummary)
    }
  }

  def sendTicketsSummaries(tickets: Map[Long, TicketClientHolder]) = {
    val ts = EventTicketsSummary(event, tickets.values.map(_.ticket))
    eventOwner.foreach(_ ! ts)
    tickets.values.foreach(_.sendSummaryToClient(ts))
    ts
  }

  def dumpTickets(tickets: Map[Long, TicketClientHolder]) {
    println(tickets.values.mkString("\n"))
  }

  def workWith(tickets: Map[Long, TicketClientHolder]): EventTicketsSummary = {
    //    dumpTickets(tickets)
    become(operateWith(tickets))
    sendTicketsSummaries(tickets)
  }

  def operateWith(tickets: Map[Long, TicketClientHolder] = Map.empty[Long, TicketClientHolder]): Receive = {
    case GetTickets =>
      sender() ! Tickets(tickets.values.map(_.ticket).toSeq)

    case GetNextTickets(cnt) =>
      println("calling GetNextTickets")
      requiredGroupSize = if (cnt > 0) cnt else event.groupsize
      val selected = selectNextTickets(tickets)
      val newTicketCollection = callInvitations(selected, tickets)
      sender ! workWith(newTicketCollection)

    case GetAccepted =>
      sender ! EventTicketsSummary(event, tickets.values.map(_.ticket).filter(t => t.state match { case Confirmed => true case Called => true case _ => false }))

    case TicketConfirmed(ticket) =>
      workWith(mapWithNewState(Map(ticket.id -> tickets(ticket.id)), Confirmed, tickets))

    case TicketSkipped(ticket) =>
      val updatedTickets = mapWithNewState(Map(ticket.id -> tickets(ticket.id)), Skipped, tickets)
      val selected = selectNextTickets(updatedTickets, WITHOUT_SKIPPED)
      val newTicketCollection = callInvitations(selected, updatedTickets, WITHOUT_CLOSING)
      workWith(newTicketCollection)

    case CreateTicket(ticket, clientActor) =>
      val newId = tickets.keys.foldLeft(0L)((acc, ticketId) => { math.max(ticketId, acc) }) + 1L
      val ticketWithId = ticket.copy(id = newId, state = Issued)
      val issuedTicket = TicketIssued(ticketWithId)
      clientActor ! issuedTicket
      context.watch(clientActor)
      workWith(tickets + (ticketWithId.id -> TicketClientHolder(ticketWithId, Set(clientActor))))

    case tj: JoinTicket =>
      val selectedTickets = tickets
        .map(_._2)
        .filter(ticketholder => ticketholder.ticket.userid == tj.ticket.userid)
        .filter(ticketholder => ticketholder.ticket.state match {
          case Closed => false
          case Confirmed => false
          case _ => true
        })
      if (selectedTickets.nonEmpty) context.watch(tj.client)
      val newTickets = selectedTickets.foldLeft(tickets) { (acc, ticketholder) =>
        println(s"joining client-actor to ticket ${ticketholder.ticket}")
        val newClientActorList = ticketholder.clients + tj.client
        println(s"actually registered clients: ${newClientActorList.size}")
        acc - ticketholder.ticket.id + (ticketholder.ticket.id -> TicketClientHolder(ticketholder.ticket, newClientActorList))
      }
      workWith(newTickets)

    case GetTicket(id) => tickets.get(id).foreach(sender() ! _)

    case CloseTicket(id) =>
      tickets.get(id) match {
        case Some(ticketholder) if (ticketholder.ticket.id > 0) =>
          val closedTicketHolder = ticketholder.toNewState(Closed)
          val td = TicketClosed(closedTicketHolder.ticket)
          //          parent ! td
          tickets
            .map(_._2)
            .filter(th => th.ticket.userid == closedTicketHolder.ticket.userid)
            .filter(th => th.clients.nonEmpty)
            .foreach { th =>
              th.clients.foreach(_ ! td)
            }
          workWith(mapWithNewState(Map(id -> tickets(id)), Closed, tickets))
        case _ =>
      }

    case connected @ ConnectEventUser(userid, clientActor) =>
      if (userid == event.userid) {
        watch(clientActor)
        eventOwner = Some(clientActor)
        clientActor ! EventTicketsSummary(event, tickets.values.map(_.ticket))
      }

    case connected @ ClientConnected(user, _, clientActor) =>
      if (user.id == event.userid) {
        watch(clientActor)
        eventOwner = Some(clientActor)
        clientActor ! EventTicketsSummary(event, tickets.values.map(_.ticket))
      }
      val selectedTickets = tickets
        .map(_._2)
        .filter(ticketholder => ticketholder.ticket.userid == user.id)
        .filter(ticketholder => ticketholder.ticket.state match {
          case Closed => false
          case Confirmed => false
          case _ => true
        })
      if (selectedTickets.nonEmpty) watch(clientActor)
      val newTickets = selectedTickets.foldLeft(tickets) { (acc, ticketholder) =>
        println(s"connecting client-actor to ticket ${ticketholder.ticket}")
        val newClientActorList = ticketholder.clients + clientActor
        println(s"actually registered clients: ${newClientActorList.size}")
        clientActor ! TicketReactivated(ticketholder.ticket)
        acc - ticketholder.ticket.id + (ticketholder.ticket.id -> TicketClientHolder(ticketholder.ticket, newClientActorList))
      }
      workWith(newTickets)

    case Terminated(clientActor) =>
      println("TicketRegistry unwatching " + clientActor)
      context.unwatch(clientActor)
      eventOwner match {
        case Some(owner) if (owner == clientActor) => eventOwner = None
        case _ =>
      }
      tickets
        .filter(pair => pair._2.clients.contains(clientActor))
        .map(_._1)
        .foreach { ticket =>
          println(s"disconnecting client-actor from ticket $ticket.")
          val newClientActorList = tickets(ticket).clients - clientActor
          println(s"remaining registered clients on Ticket ${ticket}: ${newClientActorList.size}")
          workWith(tickets - ticket + (ticket -> TicketClientHolder(tickets(ticket).ticket, newClientActorList)))
        }
  }

  def receive = operateWith()

  private def selectNextTickets(tickets: Map[Long, TicketClientHolder], withSkipped: Boolean = true): Map[Long, TicketClientHolder] = {
    tickets.toSeq
      .filter(t => t._2.clients.nonEmpty) // select just clients, which are online
      .filter(t => t._2.ticket.state match {
        case Issued => true
        case Called => true
        case Skipped => withSkipped
        case Confirmed => !withSkipped
        case _ => false
      })
      .filter(t => t._2.ticket.participants <= requiredGroupSize)
      .sortBy(t => t._1)
      .foldLeft(List[(TicketClientHolder, Int)]()) { (acc, idAndClientHolder) =>
        val (ticketId, clientHolder) = idAndClientHolder
        val actGroupSize = acc.headOption.map(_._2).getOrElse(0)
        val newGroupSize = clientHolder.ticket.participants + actGroupSize
        if (newGroupSize <= requiredGroupSize) {
          (clientHolder, newGroupSize) +: acc
        } else {
          acc
        }
      }
      .dropWhile(pair => pair._2 > requiredGroupSize)
      .filter(t => t._1.ticket.state match {
        case Issued => true
        case Called => withSkipped
        case Skipped => withSkipped
        case _ => false
      })
      .map(entry => (entry._1.ticket.id, entry._1))
      .toMap
  }

  private def mapWithNewState(ticketsToChange: Map[Long, TicketClientHolder], newState: TicketState, tickets: Map[Long, TicketClientHolder]) = {
    tickets.map { t =>
      val (id, ticketholder) = t
      ticketsToChange.get(id) match {
        case Some(candidate) =>
          (id, candidate.toNewState(newState))
        case None => (id, ticketholder)
      }
    }
  }

  private def callInvitations(candidates: Map[Long, TicketClientHolder], tickets: Map[Long, TicketClientHolder], close: Boolean = true): Map[Long, TicketClientHolder] = {
    val newTicketCollection = tickets.filter(th => th._2.ticket.state match {
      // sort out closed first to keep memory clean
      case Closed => false
      case _ => true
    }).map { t =>
      val (id, ticketholder) = t
      candidates.get(id) match {
        case Some(candidate) =>
          val calledTicket = candidate.toNewState(Called)
          calledTicket.clients.foreach { _ ! TicketCalled(calledTicket.ticket) }
          (id, calledTicket)
        case None => ticketholder.ticket.state match {
          case Skipped if (close) => (id, ticketholder.toNewState(Issued)) // reset Skipped to Issued
          case Confirmed if (close) => (id, ticketholder.toNewState(Closed)) // close confirmed ticket
          case _ => (id, ticketholder)
        }
      }
    }
    newTicketCollection
  }
}
