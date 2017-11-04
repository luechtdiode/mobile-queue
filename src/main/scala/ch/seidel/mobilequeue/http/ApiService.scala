package ch.seidel.mobilequeue.http

import akka.http.scaladsl.server.RouteConcatenation
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

trait ApiService extends RouteConcatenation
    with UserRoutes
    with EventRoutes
    //  with TicketRoutes 
    with SwaggerDocService
    with WebSockets
    with ResourceService {

  private implicit lazy val _ = ch.seidel.mobilequeue.app.Core.system.dispatcher

  lazy val allroutes =
    userRoutes ~
      eventRoutes ~
      //    ticketRoutes ~
      resourceRoutes ~
      swaggerRoutes ~
      websocket ~
      complete(StatusCodes.NotFound)
}
