package ch.seidel.mobilequeue.http

import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.{Flow, Sink, Source}
import java.util.concurrent.ThreadLocalRandom

trait WebSockets extends Directives {

  def websocket = {
    path("randomNums") {
      //send the the websocket random numbers
      val src =
      Source.fromIterator(() => Iterator.continually(ThreadLocalRandom.current.nextInt()))
      .filter(i => i > 0 && i % 2 == 0).map(i => TextMessage(i.toString))

      extractUpgradeToWebSocket { upgrade =>
        complete(upgrade.handleMessagesWithSinkSource(Sink.ignore, src))
      }
    }
  }
}
