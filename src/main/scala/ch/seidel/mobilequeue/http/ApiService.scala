package ch.seidel.mobilequeue.http

//import ch.seidel.mobilequeue.app.BootedCore 
import akka.http.scaladsl.server.RouteConcatenation
import akka.http.scaladsl.model.StatusCodes

trait ApiService extends RouteConcatenation
    with UserRoutes
    with EventRoutes
    //  with TicketRoutes 
    with SwaggerDocService
    with WebSockets
    with ResourceService {

  private implicit lazy val _ = ch.seidel.mobilequeue.app.Core.system.dispatcher

  lazy val allroutes = userRoutes ~
    eventRoutes ~
    //    ticketRoutes ~
    resourceRoutes ~
    swaggerRoutes ~
    websocket ~
    complete(StatusCodes.NotFound)
}
