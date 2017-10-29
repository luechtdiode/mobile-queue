package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props, ActorRef, Terminated }

import ch.seidel.mobilequeue.model._
import ch.seidel.mobilequeue.akka.UserRegistryActor.ClientConnected

object TicketRegistryActor {
  sealed trait TicketRegistryMessage
  final case object GetTickets extends TicketRegistryMessage
  final case class GetNextTickets(next: Int) extends TicketRegistryMessage
  final case class CreateTicket(ticket: Ticket, cnt: Int, client: ActorRef) extends TicketRegistryMessage
  final case class GetTicket(id: Long) extends TicketRegistryMessage
  final case class DeleteTicket(id: Long) extends TicketRegistryMessage

  sealed trait TicketRegistryEvent
  final case class ActionPerformed(ticket: Ticket, description: String) extends TicketRegistryEvent
  final case class TicketCreated(ticket: Ticket, requestingClient: ActorRef) extends TicketRegistryEvent
  
  def props: Props = Props[TicketRegistryActor]
}

class TicketRegistryActor extends Actor with ActorLogging {
  import TicketRegistryActor._
  import context._
  
  case class TicketClientHolder(count: Int, clients: Set[ActorRef])
  
  def operateWith(tickets: Map[Ticket, TicketClientHolder] = Map.empty[Ticket, TicketClientHolder]): Receive = {
    case GetTickets =>
      sender() ! Tickets(tickets.keys.toSeq)
      
    case GetNextTickets(cnt) => 
      val selected = tickets.toSeq
        .filter(t => t._2.clients.nonEmpty) // select just clients, which are online
        .filter(t => t._2.count <= cnt)
//        .filter(t => t.eventid == eventId)
//        .filter(t => t.notAfter > now && t.notBefore < now)
        .sortBy(t => t._1.id)
        .foldLeft(List[(Ticket, (Int))]()){(acc, ticketAndActor) =>
          val actGroupSize = acc.headOption.map(_._2).getOrElse(0)
          val newGroupSize = ticketAndActor._2.count + actGroupSize
          if (newGroupSize <= cnt) {
            (ticketAndActor._1, newGroupSize) +: acc 
          } else {
            acc
          }
        }
        .takeWhile(pair => pair._2 <= cnt)
        .map(pair => (pair._1, tickets(pair._1).clients))
      
      selected.foreach{pair => 
        pair._2.foreach{client =>
          client ! ActionPerformed(pair._1, s"Ticket ${pair._1.id} called.")
          become(operateWith(tickets - pair._1))
        }
      }
      
    case CreateTicket(ticket, cnt, clientActor) =>
      val newId = tickets.keys.foldLeft(0L)((acc, ticket) => {math.max(ticket.id, acc)}) + 1L
      val ticketWithId = ticket.copy(id = newId)
      context.watch(clientActor)      
      clientActor ! ActionPerformed(ticketWithId, s"Ticket ${newId} created.")
      parent ! TicketCreated(ticketWithId, clientActor)
      become(operateWith(tickets + (ticketWithId -> TicketClientHolder(cnt, Set(clientActor)))))
      
    case TicketCreated(ticket, requestingClient) =>
      tickets
        .filter(pair => pair._1.userid == ticket.userid)
        .map(_._2.clients)
        .foreach{clientActors =>
          clientActors.filter(_ != requestingClient).foreach(_ ! ActionPerformed(ticket, s"Ticket ${ticket.id} activated."))
        }        
        
    case GetTicket(id) =>
      sender() ! tickets.keys.find(_.id == id)
      
    case DeleteTicket(id) =>
      val toDelete = tickets.keys.find(_.id == id).getOrElse(ch.seidel.mobilequeue.model.Ticket(id, 0, 0, "not existing!", ""))
      toDelete match {
        case Ticket(id,_,_,_,_) if (id > 0) =>
          become(operateWith(tickets - toDelete))
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete, s"Ticket ${id} deleted.")
    
    case connected @ ClientConnected(user, _, clientActor) =>
      val selectedTickets = tickets
        .filter(pair => pair._1.userid == user.id)
        .map(_._1)
        
      selectedTickets.foreach{ticket =>
        println(s"connecting client-actor to ticket $ticket")
        val newClientActorList = tickets(ticket).clients + clientActor
        println(s"actual registered clients: ${newClientActorList.size}")
        become(operateWith(tickets - ticket + (ticket -> TicketClientHolder(tickets(ticket).count, newClientActorList))))
        sender() ! ActionPerformed(ticket, s"Ticket ${ticket.id} activated.")
      }
      if (selectedTickets.nonEmpty) context.watch(clientActor)
      
    case Terminated(clientActor) =>
      context.unwatch(clientActor)
      tickets
        .filter(pair => pair._2.clients.contains(clientActor))
        .map(_._1)
        .foreach{ticket =>
          println(s"disconnecting client-actor from ticket $ticket.")
          val newClientActorList = tickets(ticket).clients - clientActor
          println(s"remaining registered clients: ${newClientActorList.size}")
          become(operateWith(tickets - ticket + (ticket -> TicketClientHolder(tickets(ticket).count, newClientActorList))))
        }
  }
  
  def receive = operateWith()
}
