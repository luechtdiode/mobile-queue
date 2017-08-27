package ch.seidel.mobilequeue.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import ch.seidel.mobilequeue.akka.UserRegistryActor.{ ActionPerformed => UserActionPerformed}
import ch.seidel.mobilequeue.akka.EventRegistryActor.{ ActionPerformed => EventActionPerformed}
import ch.seidel.mobilequeue.akka.TicketRegistryActor.{ ActionPerformed => TicketActionPerformed}
import spray.json.DefaultJsonProtocol
import ch.seidel.mobilequeue.model._

trait JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat4(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val eventJsonFormat = jsonFormat4(Event)
  implicit val eventsJsonFormat = jsonFormat1(Events)

  implicit val ticketJsonFormat = jsonFormat5(Ticket)
  implicit val ticketsJsonFormat = jsonFormat1(Tickets)

  implicit val userActionPerformedJsonFormat = jsonFormat2(UserActionPerformed)
  implicit val eventActionPerformedJsonFormat = jsonFormat2(EventActionPerformed)
  implicit val ticketActionPerformedJsonFormat = jsonFormat2(TicketActionPerformed)
}
