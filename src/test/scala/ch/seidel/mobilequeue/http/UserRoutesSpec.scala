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
class UserRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest
    with UserRoutes {
  //#test-top

  // Here we need to implement all the abstract members of UserRoutes.
  // We use the real UserRegistryActor to test it while we hit the Routes, 
  // but we could "mock" it by implementing it in-place or by using a TestProbe() 

  lazy val routes = userRoutes

  //#set-up

  //#actual-test
  "UserRoutes" should {
    "return no users if no present (GET /users)" in {
      // note that there's no need for the host part in the uri:
      val request = HttpRequest(uri = "/users")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"users":[]}""")
      }
    }
    //#actual-test

    //#testing-post
    "be able to add users (POST /users)" in {
      val user = User(0, "Testuser", "password", "test@test.ch", "00411234556")
      val userEntity = Marshal(user).to[MessageEntity].futureValue // futureValue is from ScalaFutures

      // using the RequestBuilding DSL:
      val request = Post("/users").withEntity(userEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and we know what message we're expecting back:
        entityAs[String] should ===("""{"user":{"name":"Testuser","id":1,"mail":"test@test.ch","mobile":"00411234556","password":"***"},"description":"User Testuser with id 1 created."}""")
      }
    }
    //#testing-post

    "be able to remove users (DELETE /users)" in {
      // user the RequestBuilding DSL provided by ScalatestRouteSpec:
      val request = Delete(uri = "/users/1")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"user":{"name":"Testuser","id":1,"mail":"test@test.ch","mobile":"00411234556","password":"***"},"description":"User 1 deleted."}""")
      }
    }
    //#actual-test
    
    "be able to remove already removed users (DELETE /users)" in {
      // ticket the RequestBuilding DSL provided by ScalatestRouteSpec:
      val request = Delete(uri = "/users/34")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[String] should ===("""{"user":{"name":"not existing!","id":34,"mail":"","mobile":"","password":"***"},"description":"User 34 deleted."}""")
      }
    }
    //#actual-test
  }
  //#actual-test

  //#set-up
}
//#set-up
