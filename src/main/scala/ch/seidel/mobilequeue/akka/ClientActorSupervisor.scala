package ch.seidel.mobilequeue.akka

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.util.control.NonFatal

import akka.actor.ActorRef
import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.ws.Message

import ch.seidel.mobilequeue.app.Core
import ch.seidel.mobilequeue.app.BootedCore
import ch.seidel.mobilequeue.model.User
import java.util.UUID

class ClientActorSupervisor extends Actor {
  import ClientActorSupervisor._
  
  override val supervisorStrategy = OneForOneStrategy() {
    case NonFatal(e) => 
      println("Error in client actor", e)
      Stop
  }

  override def receive = {
    case CreateClient(eventRegistryActor, userRegistryActor) => 
      sender() ! ClientActor.createActorSinkSource(context.actorOf(
          Props(classOf[ClientActor], eventRegistryActor, userRegistryActor), "client-" + UUID.randomUUID().toString()))
  }
}

object ClientActorSupervisor {
  import Core._

  private case class CreateClient(eventRegistryActor: ActorRef, userRegistryActor: ActorRef)
  val supervisor = system.actorOf(Props[ClientActorSupervisor])
     
  def createFlow(): Flow[Message, Message, Any] = {
    Await.result(
        ask(supervisor, CreateClient(eventRegistryActor, userRegistryActor))(5000 milli)
        .mapTo[Flow[Message, Message, Any]], 5000 milli)
  }
}