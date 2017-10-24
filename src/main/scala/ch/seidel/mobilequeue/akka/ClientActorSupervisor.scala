package ch.seidel.mobilequeue.akka

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, OneForOneStrategy, Props}
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import ch.seidel.mobilequeue.app.Core
import akka.actor.ActorRef
import ch.seidel.mobilequeue.app.BootedCore
import ch.seidel.mobilequeue.model.User

class ClientActorSupervisor extends Actor {
  import ClientActorSupervisor._
  
  override val supervisorStrategy = OneForOneStrategy() {
    case NonFatal(e) => 
      println("Error in client actor", e)
      Stop
  }

  override def receive = {
    case CreateClient(eventRegistryActor, userRegistryActor) => sender() ! ClientActor.createActorSinkSource(context.actorOf(Props(classOf[ClientActor], eventRegistryActor, userRegistryActor)))
  }
}

object ClientActorSupervisor {
  import Core._

  private case class CreateClient(eventRegistryActor: ActorRef, userRegistryActor: ActorRef)
  val supervisor = system.actorOf(Props[ClientActorSupervisor])

  def createClient(): Future[ActorSinkSource] = {
    ask(supervisor, CreateClient(eventRegistryActor, userRegistryActor))(5000 milli).mapTo[ActorSinkSource]
  }
}