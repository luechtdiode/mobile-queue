package ch.seidel.mobilequeue.http

import org.junit.runner.RunWith
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri.apply
import akka.http.scaladsl.testkit.ScalatestRouteTest

import ch.seidel.mobilequeue.akka._
import ch.seidel.mobilequeue.http._
import ch.seidel.mobilequeue.model._

import io.swagger.annotations.ApiModel

//#set-up
@RunWith(classOf[JUnitRunner])
class TicketRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
    with TicketRoutes {
  //#test-top

  // Here we need to implement all the abstract members of TicketRoutes.
  // We use the real TicketRegistryActor to test it while we hit the Routes, 
  // but we could "mock" it by implementing it in-place or by using a TestProbe() 
  override val ticketRegistryActor: ActorRef =
    system.actorOf(TicketRegistryActor.props, "ticketRegistry")

  lazy val routes = ticketRoutes

  //#set-up

  //#actual-test
  "TicketRoutes" should {
    "return no tickets if no present (GET /tickets)" in {
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = "/tickets")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"tickets":[]}""")
      }
    }
    //#actual-test

    //#testing-post
    "be able to add tickets (POST /tickets)" in {
      val ticket = Ticket(0, 1, 1, "10:00", "11:00")
      val ticketEntity = Marshal(ticket).to[MessageEntity].futureValue // futureValue is from ScalaFutures

      // using the RequestBuilding DSL:
      val request = Post("/tickets").withEntity(ticketEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and we know what message we're expecting back:
        entityAs[String] should ===("""{"ticket":{"notAfter":"11:00","userid":1,"id":1,"notBefore":"10:00","eventid":1},"description":"Ticket 1 created."}""")
      }
    }
    //#testing-post

    "be able to remove tickets (DELETE /tickets)" in {
      // ticket the RequestBuilding DSL provided by ScalatestRouteSpec:
      val request = Delete(uri = "/tickets/1")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"ticket":{"notAfter":"11:00","userid":1,"id":1,"notBefore":"10:00","eventid":1},"description":"Ticket 1 deleted."}""")
      }
    }
    //#actual-test
    
    "be able to remove already removed tickets (DELETE /tickets)" in {
      // ticket the RequestBuilding DSL provided by ScalatestRouteSpec:
      val request = Delete(uri = "/tickets/34")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"ticket":{"notAfter":"","userid":0,"id":34,"notBefore":"not existing!","eventid":0},"description":"Ticket 34 deleted."}""")
      }
    }
    //#actual-test
  }
  //#actual-test

  //#set-up
}
//#set-up
