package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props }
import akka.actor.actorRef2Scala

import ch.seidel.mobilequeue.model._

object TicketRegistryActor {
  sealed trait TicketRegistryMessage
  final case class ActionPerformed(ticket: Ticket, description: String) extends TicketRegistryMessage
  final case object GetTickets extends TicketRegistryMessage
  final case class CreateTicket(ticket: Ticket) extends TicketRegistryMessage
  final case class GetTicket(id: Long) extends TicketRegistryMessage
  final case class DeleteTicket(id: Long) extends TicketRegistryMessage

  def props: Props = Props[TicketRegistryActor]
}

class TicketRegistryActor extends Actor with ActorLogging {
  import TicketRegistryActor._

  var tickets = Set.empty[Ticket]

  def receive: Receive = {
    case GetTickets =>
      sender() ! Tickets(tickets.toSeq)
    case CreateTicket(ticket) =>
      val withId = ticket.copy(id = tickets.foldLeft(0L)((acc, ticket) => {math.max(ticket.id, acc)}) + 1L)
      tickets += withId
      sender() ! ActionPerformed(withId, s"Ticket ${withId.id} created.")
    case GetTicket(id) =>
      sender() ! tickets.find(_.id == id)
    case DeleteTicket(id) =>
      val toDelete = tickets.find(_.id == id).getOrElse(ch.seidel.mobilequeue.model.Ticket(id, 0, 0, "not existing!", ""))
      toDelete match {
        case Ticket(id,_,_,_,_) if (id > 0) =>
          tickets -= toDelete
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete, s"Ticket ${id} deleted.")
  }
}
