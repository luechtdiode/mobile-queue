package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props }
import akka.actor.actorRef2Scala

import ch.seidel.mobilequeue.model._
import akka.actor.ActorRef
import akka.actor.PoisonPill
import ch.seidel.mobilequeue.akka.TicketRegistryActor.GetTickets
import ch.seidel.mobilequeue.akka.TicketRegistryActor.GetNextTickets
import ch.seidel.mobilequeue.akka.TicketRegistryActor.CreateTicket
import ch.seidel.mobilequeue.akka.TicketRegistryActor.GetTicket
import ch.seidel.mobilequeue.akka.TicketRegistryActor.DeleteTicket
import akka.actor.Terminated

object EventRegistryActor {
  sealed trait EventRegistryMessage
  final case class ActionPerformed(event: Event, description: String) extends EventRegistryMessage
  final case object GetEvents extends EventRegistryMessage
  final case class CreateEvent(event: Event) extends EventRegistryMessage
  final case class GetEvent(id: Long) extends EventRegistryMessage
  final case class DeleteEvent(id: Long) extends EventRegistryMessage
  
  final case class GetEventTickets(eventId: Long) extends EventRegistryMessage
  final case class GetNextEventTickets(eventId: Long, cnt: Int) extends EventRegistryMessage
  final case class CreateEventTicket(ticket: Ticket, clientActor: ActorRef) extends EventRegistryMessage
  final case class GetEventTicket(eventId: Long, id: Long) extends EventRegistryMessage
  final case class DeleteEventTicket(eventId: Long, id: Long) extends EventRegistryMessage
  
  def props: Props = Props[EventRegistryActor]
}

class EventRegistryActor extends Actor with ActorLogging {
  import EventRegistryActor._

  var eventActors = Map.empty[Event, ActorRef]
  var events = Map.empty[Long, Event]

  def receive: Receive = {
    case GetEvents =>
      sender() ! Events(eventActors.keys.toSeq)
    case CreateEvent(event) =>
      val withId = event.copy(id = events.foldLeft(0L)((acc, event) => {math.max(event._1, acc)}) + 1L)
      val tickets = context.actorOf(TicketRegistryActor.props, s"ticketRegistryActor-${withId.id}")
      context.watch(tickets)
      eventActors += (withId -> tickets)
      events += (withId.id -> withId)
      sender() ! ActionPerformed(withId, s"Event ${withId.id} created.")
    case GetEvent(id) =>
      sender() ! events.get(id)
    case DeleteEvent(id) =>
      val toDelete = events.get(id).getOrElse(Event(id, 0, "not existing!", ""))
      toDelete match {
        case Event(id,_,_,_) if (id > 0) =>
          eventActors.get(toDelete).foreach(_ ! PoisonPill)
          eventActors -= toDelete
          events -= id
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete, s"Event ${id} deleted.")
      
    case GetEventTickets(eventId: Long) => 
      events.get(eventId).foreach(eventActors.get(_).foreach(_.forward(GetTickets)))
    case GetNextEventTickets(eventId: Long, cnt: Int) => 
      events.get(eventId).foreach(eventActors.get(_).foreach(_.forward(GetNextTickets(cnt))))
    case CreateEventTicket(ticket, clientActor) => 
      events.get(ticket.eventid).foreach(event =>
        eventActors.get(event).foreach{ ticketingActor =>
          ticketingActor.forward(CreateTicket(ticket, clientActor))
        }
      )
    case GetEventTicket(eventId: Long, id: Long) => 
      events.get(eventId).foreach(eventActors.get(_).foreach(_.forward(GetTicket)))
    case DeleteEventTicket(eventId: Long, id: Long) => 
      events.get(eventId).foreach(eventActors.get(_).foreach(_.forward(DeleteTicket)))
      
    case Terminated(tickets) =>
      eventActors
        .filter(pair => pair._2 == tickets)
        .map(_._1)
        .foreach{event =>
          eventActors -= event
          events -= event.id
        }
  }
}
