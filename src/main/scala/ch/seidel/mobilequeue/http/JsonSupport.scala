package ch.seidel.mobilequeue.http

import scala.reflect.ClassTag
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import ch.seidel.mobilequeue.akka.UserRegistryActor.{ ActionPerformed => UserActionPerformed }
import ch.seidel.mobilequeue.akka.EventRegistryActor.{ ActionPerformed => EventActionPerformed }
import ch.seidel.mobilequeue.akka.TicketRegistryActor.{ ActionPerformed => TicketTicketActionPerformed }
import ch.seidel.mobilequeue.akka.TicketRegistryActor.EventTicketsSummary
import ch.seidel.mobilequeue.model._
import ch.seidel.mobilequeue.akka.EventRegistryActor.EventDeleted
import ch.seidel.mobilequeue.akka.EventRegistryActor.EventUpdated

trait JsonSupport extends SprayJsonSupport with EnrichedJson {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit object TicketStateJsonSupport extends CaseObjectJsonSupport[TicketState]

  implicit val orderTypeJsonFormat: RootJsonFormat[TicketState] = TicketStateJsonSupport
  implicit val eventJsonFormat = jsonFormat5(Event)
  implicit val eventsJsonFormat = jsonFormat1(Events)
  implicit val eventUpdatedFormat = jsonFormat1(EventUpdated)
  implicit val eventDeletedFormat = jsonFormat1(EventDeleted)

  implicit val ticketJsonFormat = jsonFormat5(Ticket)
  implicit val ticketsJsonFormat = jsonFormat1(Tickets)

  implicit val userJsonFormat = jsonFormat6(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val userActionPerformedJsonFormat = jsonFormat2(UserActionPerformed)
  implicit val eventActionPerformedJsonFormat = jsonFormat2(EventActionPerformed)
  implicit val ticketActionPerformedJsonFormat = jsonFormat2(TicketTicketActionPerformed)

  implicit val helloFormat = jsonFormat3(HelloImOnline)
  implicit val userAuthenticatedFormat = jsonFormat2(UserAuthenticated)
  implicit val userAuthenticatedFailedFormat = jsonFormat3(UserAuthenticationFailed)
  implicit val subscribeFormat = jsonFormat2(Subscribe)
  implicit val unsubscribeFormat = jsonFormat1(UnSubscribe)
  implicit val messageAckFormat = jsonFormat1(MessageAck)
  implicit val ticketIssued = jsonFormat1(TicketIssued)
  implicit val ticketCalledFormat = jsonFormat1(TicketCalled)
  implicit val ticketReactivatedFormat = jsonFormat1(TicketReactivated)
  implicit val ticketSkippedFormat = jsonFormat1(TicketSkipped)
  implicit val ticketConfirmedFormat = jsonFormat1(TicketConfirmed)
  implicit val ticketExpiredFormat = jsonFormat1(TicketExpired)
  implicit val ticketAcceptedFormat = jsonFormat1(TicketAccepted)
  implicit val ticketDeletedFormat = jsonFormat1(TicketClosed)
  implicit val summaryFormat = jsonFormat(EventTicketsSummary, "event", "invites")
  implicit val userTicketSummaryFormat = jsonFormat7(UserTicketsSummary)

  // support for websocket incoming json-messages
  val caseClassesJsonReader: Map[String, JsonReader[_ <: MobileTicketQueueProtokoll]] = Map(
    classOf[TicketTicketActionPerformed].getSimpleName -> userAuthenticatedFailedFormat, classOf[UserAuthenticated].getSimpleName -> userAuthenticatedFormat, classOf[MessageAck].getSimpleName -> messageAckFormat, classOf[TicketClosed].getSimpleName -> ticketDeletedFormat, classOf[TicketAccepted].getSimpleName -> ticketAcceptedFormat, classOf[TicketIssued].getSimpleName -> ticketIssued, classOf[TicketExpired].getSimpleName -> ticketExpiredFormat, classOf[TicketConfirmed].getSimpleName -> ticketConfirmedFormat, classOf[HelloImOnline].getSimpleName -> helloFormat, classOf[Subscribe].getSimpleName -> subscribeFormat, classOf[UnSubscribe].getSimpleName -> unsubscribeFormat, classOf[TicketCalled].getSimpleName -> ticketCalledFormat, classOf[TicketReactivated].getSimpleName -> ticketReactivatedFormat, classOf[TicketSkipped].getSimpleName -> ticketSkippedFormat
  )

  implicit val messagesFormat: JsonReader[MobileTicketQueueProtokoll] = { json =>
    json.asOpt[JsObject].flatMap(_.fields.get("type").flatMap(_.asOpt[String])).map(caseClassesJsonReader) match {
      case Some(jsonReader) =>
        val plain = json.withoutFields("type")
        jsonReader.read(plain)
      case _ => throw new Exception(s"Unable to parse $json to PubSub")
    }
  }
}
