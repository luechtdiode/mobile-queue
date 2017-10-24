package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props }
import akka.actor.actorRef2Scala

import ch.seidel.mobilequeue.model._
import akka.actor.ActorRef

object TicketRegistryActor {
  sealed trait TicketRegistryMessage
  final case class ActionPerformed(ticket: Ticket, description: String) extends TicketRegistryMessage
  final case object GetTickets extends TicketRegistryMessage
  final case class GetNextTickets(next: Int) extends TicketRegistryMessage
  final case class CreateTicket(ticket: Ticket, client: ActorRef) extends TicketRegistryMessage
  final case class GetTicket(id: Long) extends TicketRegistryMessage
  final case class DeleteTicket(id: Long) extends TicketRegistryMessage

  def props: Props = Props[TicketRegistryActor]
}

class TicketRegistryActor extends Actor with ActorLogging {
  import TicketRegistryActor._

  var tickets = Map.empty[Ticket, ActorRef]

  def receive: Receive = {
    case GetTickets =>
      sender() ! Tickets(tickets.keys.toSeq)
      
    case GetNextTickets(cnt) => 
      val selected = tickets.toSeq
//        .filter(t => t.eventid == eventId)
//        .filter(t => t.notAfter > now && t.notBefore < now)
        .sortBy(t => t._1.id).take(cnt)
      
      selected.foreach{pair => 
        pair._2 ! ActionPerformed(pair._1, s"Ticket ${pair._1.id} called.")
        tickets = tickets - pair._1
      }
      
    case CreateTicket(ticket, clientActor) =>
      val newId = tickets.keys.foldLeft(0L)((acc, ticket) => {math.max(ticket.id, acc)}) + 1L
      val ticketWithId = ticket.copy(id = newId)
      tickets = tickets + (ticketWithId -> clientActor)
      sender() ! ActionPerformed(ticketWithId, s"Ticket ${newId} created.")
      
    case GetTicket(id) =>
      sender() ! tickets.keys.find(_.id == id)
      
    case DeleteTicket(id) =>
      val toDelete = tickets.keys.find(_.id == id).getOrElse(ch.seidel.mobilequeue.model.Ticket(id, 0, 0, "not existing!", ""))
      toDelete match {
        case Ticket(id,_,_,_,_) if (id > 0) =>
          tickets = tickets - toDelete
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete, s"Ticket ${id} deleted.")
  }
}
