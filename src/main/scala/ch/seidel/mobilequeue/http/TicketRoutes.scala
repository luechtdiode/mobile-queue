package ch.seidel.mobilequeue.http

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete

import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.Future

import ch.seidel.mobilequeue.akka.TicketRegistryActor._
import ch.seidel.mobilequeue.model._
import akka.actor.ActorLogging

//#ticket-routes-class
trait TicketRoutes extends JsonSupport with RouterLogging {
  //#ticket-routes-class

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  // other dependencies that TicketRoutes use
  def ticketRegistryActor: ActorRef

  // Required by the `ask` (?) method below
  private implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  //#all-routes
  //#tickets-get-post
  //#tickets-get-delete   
  lazy val ticketRoutes: Route = {
    pathPrefix("tickets") {
      //#tickets-get-delete
      pathEnd {
        get {
          val tickets: Future[Tickets] =
            (ticketRegistryActor ? GetTickets).mapTo[Tickets]
          complete(tickets)
        } ~
        post {
          entity(as[Ticket]) { ticket =>
            val ticketCreated: Future[ActionPerformed] =
              (ticketRegistryActor ? CreateTicket(ticket)).mapTo[ActionPerformed]
            onSuccess(ticketCreated) { performed =>
              log.info("Created ticket [{}]: {}", performed.ticket, performed.description)
              complete((StatusCodes.Created, performed))
            }
          }
        }
      
      } ~
      //#tickets-get-post
      //#tickets-get-delete
      path(LongNumber) { id =>
        get {
          //#retrieve-ticket-info
          val maybeTicket: Future[Option[Ticket]] =
            (ticketRegistryActor ? GetTicket(id)).mapTo[Option[Ticket]]
          rejectEmptyResponse {
            complete(maybeTicket)
          }
          //#retrieve-ticket-info
        } ~
        delete {
          //#tickets-delete-logic
          val ticketDeleted: Future[ActionPerformed] =
            (ticketRegistryActor ? DeleteTicket(id)).mapTo[ActionPerformed]
          onSuccess(ticketDeleted) { performed =>
            log.info("Deleted ticket [{}]: {}", id, performed.description)
            complete((StatusCodes.OK, performed))
          }
          //#tickets-delete-logic
        }
      }
      //#tickets-get-delete
    }
  }
  //#all-routes
}
