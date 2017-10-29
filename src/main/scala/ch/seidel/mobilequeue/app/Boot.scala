package ch.seidel.mobilequeue.app

import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import ch.seidel.mobilequeue.http.ApiService
import scala.util.{ Success, Failure }
import scala.io.StdIn
import ch.seidel.mobilequeue.akka.ClientActorSupervisor
import Core._
import ch.seidel.mobilequeue.akka.EventRegistryActor.GetNextEventTickets

object Boot extends App with Config with BootedCore with ApiService {

  val binding = Http().bindAndHandle(allroutes, httpInterface, httpPort)

  override def shutDown() {
    println(s"Server stops ...")
    binding
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete { done =>
        done.failed.map { ex => log.error(ex, "Failed unbinding") }
        super.shutDown()
      }
  }

  println(s"Server online at http://localhost:$httpPort/\ntype 'quit' to stop...")

  while (true) {
    StdIn.readLine() match {
      case s: String if (s.endsWith("quit")) => shutDown()
      //      case "createEvent" => (eventRegistryActor ? CreateEvent(Event())).andThen {
      //        case ActionPerformed(eventId, msg) => println(msg, eventId)
      //      }
      case _ => eventRegistryActor ! GetNextEventTickets(1L, 10)
    }
  }
  shutDown()
}
