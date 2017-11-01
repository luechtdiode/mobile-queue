package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props, ActorRef, Terminated }
//import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.util.Failure

import ch.seidel.mobilequeue.model._
import ch.seidel.mobilequeue.akka.UserRegistryActor.ClientConnected

object TicketRegistryActor {
  sealed trait TicketRegistryMessage
  final case object GetTickets extends TicketRegistryMessage
  final case class GetNextTickets(next: Int) extends TicketRegistryMessage
  final case object GetAccepted extends TicketRegistryMessage
  final case class InvokedTicketsSummary(invites: List[(Ticket, Int)])
  final case class CreateTicket(ticket: Ticket, cnt: Int, client: ActorRef) extends TicketRegistryMessage
  final case class GetTicket(id: Long) extends TicketRegistryMessage
  final case class DeleteTicket(id: Long) extends TicketRegistryMessage

  sealed trait TicketRegistryEvent
  final case class ActionPerformed(ticket: Ticket, description: String) extends TicketRegistryEvent
  final case class TicketAknowlidged(ticket: Ticket, count: Int) extends TicketRegistryEvent
  final case class TicketCreated(ticket: Ticket, requestingClient: ActorRef) extends TicketRegistryEvent

  val name = "ticketRegistryActor"
  def props: Props = Props[TicketRegistryActor]
}

class TicketRegistryActor extends Actor /*with ActorLogging*/ {
  import TicketRegistryActor._
  import context._
  import akka.pattern.pipe
  // Get the implicit ExecutionContext from this import
  import scala.concurrent.ExecutionContext.Implicits.global
  //  private lazy val askTicketAkTimeout = Timeout(60.seconds)
  var acceptedInvites: List[(Ticket, Int)] = List.empty

  case class TicketClientHolder(count: Int, clients: Set[ActorRef])

  def operateWith(tickets: Map[Ticket, TicketClientHolder] = Map.empty[Ticket, TicketClientHolder]): Receive = {
    case GetTickets =>
      sender() ! Tickets(tickets.keys.toSeq)

    case GetNextTickets(cnt) =>
      println("callin GetNextTickets from " + sender())
      acceptedInvites = List.empty
      val selected = tickets.toSeq
        .filter(t => t._2.clients.nonEmpty) // select just clients, which are online
        .filter(t => t._2.count <= cnt)
        //        .filter(t => t.eventid == eventId)
        //        .filter(t => t.notAfter > now && t.notBefore < now)
        .sortBy(t => t._1.id)
        .foldLeft(List[(Ticket, Int, Int)]()) { (acc, ticketAndActor) =>
          val actGroupSize = acc.headOption.map(_._2).getOrElse(0)
          val newGroupSize = ticketAndActor._2.count + actGroupSize
          if (newGroupSize <= cnt) {
            (ticketAndActor._1, newGroupSize, ticketAndActor._2.count) +: acc
          } else {
            acc
          }
        }
        .takeWhile(pair => pair._2 <= cnt)
        .map(pair => (pair._1, pair._3, tickets(pair._1).clients))

      //      implicit val timeout = askTicketAkTimeout;
      println("calling tickets for " + selected)
      selected.foreach { pair =>
        pair._3.foreach { _ ! TicketCalled(pair._1, pair._2) }
      }
      sender ! InvokedTicketsSummary(selected.map(p => (p._1, p._2)).toList)

    case GetAccepted =>
      sender ! InvokedTicketsSummary(acceptedInvites)

    case TicketCalled(ticket, cnt) =>
      acceptedInvites :+= (ticket, cnt)
      println(s"Actually accepted invites: ${acceptedInvites.size}")
      sender() ! ActionPerformed(ticket, s"Ticket ${ticket.id} called ${cnt} participants.")
      become(operateWith(tickets - ticket))

    case CreateTicket(ticket, cnt, clientActor) =>
      val newId = tickets.keys.foldLeft(0L)((acc, ticket) => { math.max(ticket.id, acc) }) + 1L
      val ticketWithId = ticket.copy(id = newId)
      context.watch(clientActor)
      clientActor ! ActionPerformed(ticketWithId, s"Ticket ${newId} created.")
      parent ! TicketCreated(ticketWithId, clientActor)
      become(operateWith(tickets + (ticketWithId -> TicketClientHolder(cnt, Set(clientActor)))))

    case TicketCreated(ticket, requestingClient) =>
      tickets
        .filter(pair => pair._1.userid == ticket.userid)
        .map(_._2.clients)
        .foreach { clientActors =>
          clientActors.filter(_ != requestingClient).foreach(_ ! ActionPerformed(ticket, s"Ticket ${ticket.id} activated."))
        }

    case GetTicket(id) =>
      sender() ! tickets.keys.find(_.id == id)

    case DeleteTicket(id) =>
      val toDelete = tickets.keys.find(_.id == id).getOrElse(ch.seidel.mobilequeue.model.Ticket(id, 0, 0, "not existing!", ""))
      toDelete match {
        case Ticket(id, _, _, _, _) if (id > 0) =>
          become(operateWith(tickets - toDelete))
        case _ =>
      }
      sender() ! ActionPerformed(toDelete, s"Ticket ${id} deleted.")

    case connected @ ClientConnected(user, _, clientActor) =>
      val selectedTickets = tickets
        .filter(pair => pair._1.userid == user.id)
        .map(_._1)

      selectedTickets.foreach { ticket =>
        println(s"connecting client-actor to ticket $ticket")
        val newClientActorList = tickets(ticket).clients + clientActor
        println(s"actual registered clients: ${newClientActorList.size}")
        become(operateWith(tickets - ticket + (ticket -> TicketClientHolder(tickets(ticket).count, newClientActorList))))
        sender() ! ActionPerformed(ticket, s"Ticket ${ticket.id} activated.")
      }
      if (selectedTickets.nonEmpty) context.watch(clientActor)

    case Terminated(clientActor) =>
      println("unwatching " + clientActor)
      context.unwatch(clientActor)
      tickets
        .filter(pair => pair._2.clients.contains(clientActor))
        .map(_._1)
        .foreach { ticket =>
          println(s"disconnecting client-actor from ticket $ticket.")
          val newClientActorList = tickets(ticket).clients - clientActor
          println(s"remaining registered clients: ${newClientActorList.size}")
          become(operateWith(tickets - ticket + (ticket -> TicketClientHolder(tickets(ticket).count, newClientActorList))))
        }
  }

  def receive = operateWith()
}
