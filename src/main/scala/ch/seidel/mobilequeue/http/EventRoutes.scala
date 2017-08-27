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

import ch.seidel.mobilequeue.akka.EventRegistryActor._
import ch.seidel.mobilequeue.model._
import akka.actor.ActorLogging

//#event-routes-class
trait EventRoutes extends JsonSupport with RouterLogging {
  //#event-routes-class

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  // other dependencies that EventRoutes use
  def eventRegistryActor: ActorRef

  // Required by the `ask` (?) method below
  private implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  //#all-routes
  //#events-get-post
  //#events-get-delete   
  lazy val eventRoutes: Route = {
    pathPrefix("events") {
      //#events-get-delete
      pathEnd {
        get {
          val events: Future[Events] =
            (eventRegistryActor ? GetEvents).mapTo[Events]
          complete(events)
        } ~
        post {
          entity(as[Event]) { event =>
            val eventCreated: Future[ActionPerformed] =
              (eventRegistryActor ? CreateEvent(event)).mapTo[ActionPerformed]
            onSuccess(eventCreated) { performed =>
              log.info("Created event [{}]: {}", performed.event, performed.description)
              complete((StatusCodes.Created, performed))
            }
          }
        }
      
      } ~
      //#events-get-post
      //#events-get-delete
      path(LongNumber) { id =>
        get {
          //#retrieve-event-info
          val maybeEvent: Future[Option[Event]] =
            (eventRegistryActor ? GetEvent(id)).mapTo[Option[Event]]
          rejectEmptyResponse {
            complete(maybeEvent)
          }
          //#retrieve-event-info
        } ~
        delete {
          //#events-delete-logic
          val eventDeleted: Future[ActionPerformed] =
            (eventRegistryActor ? DeleteEvent(id)).mapTo[ActionPerformed]
          onSuccess(eventDeleted) { performed =>
            log.info("Deleted event [{}]: {}", id, performed.description)
            complete((StatusCodes.OK, performed))
          }
          //#events-delete-logic
        }
      }
      //#events-get-delete
    }
  }
  //#all-routes
}
