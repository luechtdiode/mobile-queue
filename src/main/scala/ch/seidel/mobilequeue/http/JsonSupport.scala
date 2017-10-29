package ch.seidel.mobilequeue.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import ch.seidel.mobilequeue.akka.UserRegistryActor.{ ActionPerformed => UserActionPerformed}
import ch.seidel.mobilequeue.akka.EventRegistryActor.{ ActionPerformed => EventActionPerformed}
import ch.seidel.mobilequeue.akka.TicketRegistryActor.{ ActionPerformed => TicketTicketActionPerformed}
import spray.json._
import ch.seidel.mobilequeue.model._
import spray.json.JsonReader
import spray.json.JsObject
import ch.seidel.mobilequeue.akka.TicketRegistryActor.InvokedTicketsSummary

trait JsonSupport extends SprayJsonSupport with EnrichedJson {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val eventJsonFormat = jsonFormat4(Event)
  implicit val eventsJsonFormat = jsonFormat1(Events)

  implicit val ticketJsonFormat = jsonFormat5(Ticket)
  implicit val ticketsJsonFormat = jsonFormat1(Tickets)

  implicit val userJsonFormat = jsonFormat6(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val userActionPerformedJsonFormat = jsonFormat2(UserActionPerformed)
  implicit val eventActionPerformedJsonFormat = jsonFormat2(EventActionPerformed)
  implicit val ticketActionPerformedJsonFormat = jsonFormat2(TicketTicketActionPerformed)

//  implicit val registerFormat = jsonFormat1(Register)
//  implicit val loginFormat = jsonFormat2(LogIn)
  implicit val helloFormat = jsonFormat2(HelloImOnline)
  implicit val subscribeFormat = jsonFormat2(Subscribe)
  implicit val unsubscribeFormat = jsonFormat1(UnSubscribe)
  implicit val ticketCalledFormat = jsonFormat2(TicketCalled)
  implicit val summaryFormat = jsonFormat1(InvokedTicketsSummary)
 
  val caseClassesJsonReader: Map[String, JsonReader[_ <: PubSub]] = Map(      
        classOf[HelloImOnline].getSimpleName -> helloFormat
//      , classOf[Register].getSimpleName -> registerFormat
//      , classOf[LogIn].getSimpleName -> loginFormat
      , classOf[Subscribe].getSimpleName -> subscribeFormat
      , classOf[UnSubscribe].getSimpleName -> unsubscribeFormat
      , classOf[TicketCalled].getSimpleName -> ticketCalledFormat
      )
  
  implicit val messagesFormat: JsonReader[PubSub] = { json =>
    json.asOpt[JsObject].flatMap(_.fields.get("type").flatMap(_.asOpt[String])).map(caseClassesJsonReader) match {
      case Some(jsonReader) => 
        val plain = json.withoutFields("type")
        val ret = jsonReader.read(plain)
//        println(ret)
        ret
      case _ => throw new Exception(s"Unable to parse $json to PubSub")
    }
  }
}

