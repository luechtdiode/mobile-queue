package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props, ActorRef, PoisonPill, Terminated }
import akka.actor.actorRef2Scala

import ch.seidel.mobilequeue.model._

import ch.seidel.mobilequeue.akka.TicketRegistryActor.GetTickets
import ch.seidel.mobilequeue.akka.TicketRegistryActor.GetNextTickets
import ch.seidel.mobilequeue.akka.TicketRegistryActor.CreateTicket
import ch.seidel.mobilequeue.akka.TicketRegistryActor.GetTicket
import ch.seidel.mobilequeue.akka.TicketRegistryActor.CloseTicket
import ch.seidel.mobilequeue.akka.TicketRegistryActor.TicketCreated

import ch.seidel.mobilequeue.akka.UserRegistryActor.ClientConnected
import ch.seidel.mobilequeue.akka.TicketRegistryActor.GetAccepted
import ch.seidel.mobilequeue.akka.TicketRegistryActor.JoinTicket

object EventRegistryActor {
  sealed trait EventRegistryMessage
  final case class ActionPerformed(event: Event, description: String) extends EventRegistryMessage
  final case object GetEvents extends EventRegistryMessage
  final case class CreateEvent(event: Event) extends EventRegistryMessage
  final case class GetEvent(id: Long) extends EventRegistryMessage
  final case class DeleteEvent(id: Long) extends EventRegistryMessage

  final case class GetEventTickets(eventId: Long) extends EventRegistryMessage
  final case class GetNextEventTickets(eventId: Long, cnt: Int) extends EventRegistryMessage
  final case class GetEventAccepted(eventId: Long) extends EventRegistryMessage
  final case class CreateEventTicket(ticket: Ticket, clientActor: ActorRef) extends EventRegistryMessage
  final case class GetEventTicket(eventId: Long, id: Long) extends EventRegistryMessage
  final case class CloseEventTicket(eventId: Long, id: Long) extends EventRegistryMessage
  def props: Props = Props[EventRegistryActor]
}

class EventRegistryActor extends Actor /*with ActorLogging*/ {
  import EventRegistryActor._
  import context._

  def operateWith(ticketsForEventActors: Map[Event, ActorRef], events: Map[Long, Event]): Receive = {

    // events crud
    case GetEvents =>
      sender() ! Events(ticketsForEventActors.keys.toSeq)

    case CreateEvent(event) =>
      val withId = event.copy(id = events.foldLeft(0L)((acc, event) => { math.max(event._1, acc) }) + 1L)
      val tickets = context.actorOf(TicketRegistryActor.props(withId), s"${TicketRegistryActor.name}-${withId.id}")
      watch(tickets)
      become(operateWith(ticketsForEventActors + (withId -> tickets), events + (withId.id -> withId)))
      sender() ! ActionPerformed(withId, s"Event ${withId.id} created.")

    case GetEvent(id) =>
      sender() ! events.get(id)

    case DeleteEvent(id) =>
      val toDelete = events.get(id).getOrElse(Event(id, 0, "not existing!", "", 0))
      toDelete match {
        case Event(id, _, _, _, _) if (id > 0) =>
          ticketsForEventActors.get(toDelete).foreach(_ ! PoisonPill)
          become(operateWith(ticketsForEventActors - toDelete, events - id))
        case _ =>
      }
      sender() ! ActionPerformed(toDelete, s"Event ${id} deleted.")

    case GetEventTickets(eventId: Long) =>
      events.get(eventId).foreach(ticketsForEventActors.get(_).foreach(_.forward(GetTickets)))

    // event routing to ticket actors
    case GetNextEventTickets(eventId: Long, cnt: Int) =>
      events.get(eventId).foreach(ticketsForEventActors.get(_).foreach(_.forward(GetNextTickets(cnt))))

    case CreateEventTicket(ticket, clientActor) =>
      events.get(ticket.eventid).foreach(event =>
        ticketsForEventActors.get(event).foreach { ticketingActor =>
          ticketingActor.forward(CreateTicket(ticket, clientActor))
        })
    case GetEventAccepted(eventId) =>
      events.get(eventId).foreach(event =>
        ticketsForEventActors.get(event).foreach { ticketingActor =>
          ticketingActor.forward(GetAccepted)
        })

    // broadcast to all other connected clients of the same user
    case tj: JoinTicket =>
      ticketsForEventActors
        .filter(ta => ta._1.id == tj.ticket.eventid)
        .map(_._2)
        .foreach(_.forward(tj))
    //    case tc: TicketCreated =>
    //      ticketsForEventActors.values
    //        .filter(_ != sender)
    //        .foreach(_.forward(tc))

    case GetEventTicket(eventId: Long, id: Long) =>
      events.get(eventId).foreach(ticketsForEventActors.get(_).foreach(_.forward(GetTicket(id))))

    case CloseEventTicket(eventId: Long, id: Long) =>
      events.get(eventId).foreach(ticketsForEventActors.get(_).foreach(_.forward(CloseTicket(id))))

    //    case td: TicketClosed =>
    //      ticketsForEventActors.values
    //        .filter(_ != sender)
    //        .foreach(_.forward(td))

    // supervision of connected clients
    case connected: ClientConnected =>
      ticketsForEventActors.values.foreach(_.forward(connected))

    // supervision of child-actors (tickets per event)
    case Terminated(tickets) =>
      unwatch(tickets)
      val (tkts, evts) = ticketsForEventActors
        .filter(pair => pair._2 == tickets)
        .map(_._1)
        .foldLeft((ticketsForEventActors, events)) { (acc, toDelete) =>
          (ticketsForEventActors - toDelete, events - toDelete.id)
        }
      become(operateWith(tkts, evts))
  }

  def receive = operateWith(Map.empty[Event, ActorRef], Map.empty[Long, Event])
}
