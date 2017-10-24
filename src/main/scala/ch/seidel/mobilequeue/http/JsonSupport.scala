package ch.seidel.mobilequeue.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import ch.seidel.mobilequeue.akka.UserRegistryActor.{ ActionPerformed => UserActionPerformed}
import ch.seidel.mobilequeue.akka.EventRegistryActor.{ ActionPerformed => EventActionPerformed}
import ch.seidel.mobilequeue.akka.TicketRegistryActor.{ ActionPerformed => TicketActionPerformed}
import spray.json.DefaultJsonProtocol
import ch.seidel.mobilequeue.model._
import spray.json.JsonReader
import spray.json.JsObject

trait JsonSupport extends SprayJsonSupport with EnrichedJson {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val loginFormat = jsonFormat2(LogIn)
  implicit val subscribeFormat = jsonFormat1(Subscribe)
//  implicit val publishFormat = jsonFormat3(Publish)
  implicit val unsubscribeFormat = jsonFormat1(UnSubscribe)
//  implicit val channelMessageFormat = jsonFormat3(ChannelMessage)

  implicit val userJsonFormat = jsonFormat5(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val eventJsonFormat = jsonFormat4(Event)
  implicit val eventsJsonFormat = jsonFormat1(Events)

  implicit val ticketJsonFormat = jsonFormat5(Ticket)
  implicit val ticketsJsonFormat = jsonFormat1(Tickets)

  implicit val userActionPerformedJsonFormat = jsonFormat2(UserActionPerformed)
  implicit val eventActionPerformedJsonFormat = jsonFormat2(EventActionPerformed)
  implicit val ticketActionPerformedJsonFormat = jsonFormat2(TicketActionPerformed)
  
  implicit val messagesFormat: JsonReader[PubSub] = { json =>
    json.asOpt[JsObject].flatMap(_.fields.get("type").flatMap(_.asOpt[String])) match {
      case Some("LogIn") => json.convertTo[LogIn]
      case Some("Subscribe") => json.convertTo[Subscribe]
      case Some("UnSubscribe") => json.convertTo[UnSubscribe]
      case _ => throw new Exception(s"Unable to parse $json to PubSub")
    }
  }
}
