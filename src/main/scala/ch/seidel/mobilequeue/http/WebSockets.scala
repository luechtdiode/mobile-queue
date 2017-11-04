package ch.seidel.mobilequeue.http

import scala.concurrent.ExecutionContext.Implicits.global

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Directives
import ch.seidel.mobilequeue.akka.ClientActorSupervisor

trait WebSockets extends Directives {

  def websocket = {
    path("api" / "ticketTrigger") {
      handleWebSocketMessages(ClientActorSupervisor.createFlow)
    }
  }
}
