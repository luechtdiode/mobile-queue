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
import ch.seidel.mobilequeue.app.Core._

//#set-up
@RunWith(classOf[JUnitRunner])
class EventRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
    with EventRoutes {
  //#test-top


  lazy val routes = eventRoutes

  //#set-up

  //#actual-test
  "EventRoutes" should {
    "return no events if no present (GET /events)" in {
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = "/events")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"events":[]}""")
      }
    }
    //#actual-test

    //#testing-post
    "be able to add events (POST /events)" in {
      val event = Event(0, 1, "Testevent", "30. Nov 2018")
      val eventEntity = Marshal(event).to[MessageEntity].futureValue // futureValue is from ScalaFutures

      // using the RequestBuilding DSL:
      val request = Post("/events").withEntity(eventEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and we know what message we're expecting back:
        entityAs[String] should ===("""{"event":{"id":1,"userid":1,"eventTitle":"Testevent","date":"30. Nov 2018"},"description":"Event 1 created."}""")
      }
    }
    //#testing-post

    "be able to remove events (DELETE /events)" in {
      // event the RequestBuilding DSL provided by ScalatestRouteSpec:
      val request = Delete(uri = "/events/1")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"event":{"id":1,"userid":1,"eventTitle":"Testevent","date":"30. Nov 2018"},"description":"Event 1 deleted."}""")
      }
    }
    //#actual-test
    
    "be able to remove already removed events (DELETE /events)" in {
      // event the RequestBuilding DSL provided by ScalatestRouteSpec:
      val request = Delete(uri = "/events/34")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"event":{"id":34,"userid":0,"eventTitle":"not existing!","date":""},"description":"Event 34 deleted."}""")
      }
    }
    //#actual-test
  }
  //#actual-test

  //#set-up
}
//#set-up
