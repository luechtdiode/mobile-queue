package ch.seidel.mobilequeue.app

import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import ch.seidel.mobilequeue.http.ApiService
import scala.util.{ Success, Failure }
import scala.io.StdIn

object Boot extends App with Config with BootedCore with ApiService {

  import system.dispatcher

  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  
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
  
  println(s"Server online at http://localhost:$httpPort/\nPress RETURN to stop...")

  StdIn.readLine()

  shutDown()
}
