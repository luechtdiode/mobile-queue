package ch.seidel.mobilequeue.http

import scala.concurrent.ExecutionContext.Implicits.global

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Directives
import ch.seidel.mobilequeue.akka.ClientActorSupervisor

trait WebSockets extends Directives {
 
  def websocket = {
    path("ticketTrigger") {
      extractRequest { request =>
        complete(
          request.header[UpgradeToWebSocket] match {
            case Some(upgrade) =>
              ClientActorSupervisor.createClient().map { sinkSource =>
                upgrade.handleMessagesWithSinkSource(sinkSource.sink, sinkSource.source)
              }
            case None => HttpResponse(400, entity = "Not a valid websocket request!")
          }
        )
      }
    }
  }
}
