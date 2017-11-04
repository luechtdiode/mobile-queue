package ch.seidel.mobilequeue.http

import javax.ws.rs.Path

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
import io.swagger.annotations._

import ch.seidel.mobilequeue.akka.UserRegistryActor._
import ch.seidel.mobilequeue.model._
import akka.actor.ActorLogging

import ch.seidel.mobilequeue.app.Core._

//#user-routes-class
@Api(value = "/api/users", produces = "application/json", description = "Operations on users")
@Path("/api/users")
trait UserRoutes extends JsonSupport with RouterLogging {
  //#user-routes-class

  // Required by the `ask` (?) method below
  private implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  val userRoutes = getUsers ~ getUser ~ addUser ~ deleteUser

  @ApiOperation(value = "Get all users", notes = "Returns all users",
    nickname = "getUsers", httpMethod = "GET",
    response = classOf[Seq[User]],
    responseContainer = "List")
  @ApiImplicitParams(Array())
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "User retrieved"),
    new ApiResponse(code = 204, message = "No user found")
  ))
  def getUsers = get {
    pathPrefix("api" / "users") {
      pathEnd {
        val users: Future[Users] =
          (userRegistryActor ? GetUsers).mapTo[Users]
        complete(users)
      }
    }
  }

  @ApiOperation(value = "Add a new user", nickname = "addUser", httpMethod = "POST", consumes = "application/json, application/vnd.custom.user", response = classOf[ActionPerformed])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "User object", dataType = "User", required = true, paramType = "body")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 405, message = "Invalid input"),
    new ApiResponse(code = 200, message = "User added")

  ))
  def addUser = post {
    pathPrefix("api" / "users") {
      pathEnd {
        entity(as[User]) { user =>
          val userCreated: Future[ActionPerformed] =
            (userRegistryActor ? CreateUser(user)).mapTo[ActionPerformed]
          onSuccess(userCreated) { performed =>
            log.info("Created user [{}]: {}", performed.user, performed.description)
            complete((StatusCodes.Created, performed))
          }
        }
      }
    }
  }

  @ApiOperation(value = "Find a user by ID", notes = "Returns a user based on ID", httpMethod = "GET", response = classOf[User])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "userId", value = "ID of user that needs to be fetched", required = true, dataType = "integer", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 400, message = "Invalid ID supplied")
  ))
  @Path("{userid}")
  def getUser = get {
    pathPrefix("api" / "users") {
      path(LongNumber) { id =>
        //#retrieve-user-info
        val maybeUser: Future[Option[User]] =
          (userRegistryActor ? GetUser(id)).mapTo[Option[User]]
        rejectEmptyResponse {
          complete(maybeUser)
        }
        //#retrieve-user-info
      }
    }
  }

  @ApiOperation(value = "Find a user by ID", notes = "Returns a user based on ID", httpMethod = "DELETE", response = classOf[ActionPerformed])
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "userId", value = "ID of user that needs to be fetched", required = true, dataType = "integer", paramType = "path")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 404, message = "User not found"),
    new ApiResponse(code = 400, message = "Invalid ID supplied")
  ))
  def deleteUser = delete {
    pathPrefix("api" / "users") {
      path(LongNumber) { id =>
        //#users-delete-logic
        val userDeleted: Future[ActionPerformed] =
          (userRegistryActor ? DeleteUser(id)).mapTo[ActionPerformed]
        onSuccess(userDeleted) { performed =>
          log.info("Deleted user [{}]: {}", id, performed.description)
          complete((StatusCodes.OK, performed))
        }
        //#users-delete-logic
      }
    }
  }

  //#all-routes
  //#users-get-post
  //#users-get-delete   
  lazy val _userRoutes: Route = {
    pathPrefix("api" / "users") {
      //#users-get-delete
      pathEnd {
        get {
          val users: Future[Users] =
            (userRegistryActor ? GetUsers).mapTo[Users]
          complete(users)
        } ~
          post {
            entity(as[User]) { user =>
              val userCreated: Future[ActionPerformed] =
                (userRegistryActor ? CreateUser(user)).mapTo[ActionPerformed]
              onSuccess(userCreated) { performed =>
                log.info("Created user [{}]: {}", performed.user, performed.description)
                complete((StatusCodes.Created, performed))
              }
            }
          }
      } ~
        //#users-get-post
        //#users-get-delete
        path(LongNumber) { id =>
          get {
            //#retrieve-user-info
            val maybeUser: Future[Option[User]] =
              (userRegistryActor ? GetUser(id)).mapTo[Option[User]]
            rejectEmptyResponse {
              complete(maybeUser)
            }
            //#retrieve-user-info
          } ~
            delete {
              //#users-delete-logic
              val userDeleted: Future[ActionPerformed] =
                (userRegistryActor ? DeleteUser(id)).mapTo[ActionPerformed]
              onSuccess(userDeleted) { performed =>
                log.info("Deleted user [{}]: {}", id, performed.description)
                complete((StatusCodes.OK, performed))
              }
              //#users-delete-logic
            }
        }
      //#users-get-delete
    }
  }
  //#all-routes
}
