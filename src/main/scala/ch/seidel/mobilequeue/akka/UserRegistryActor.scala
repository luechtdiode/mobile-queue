package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props }
import akka.actor.actorRef2Scala

import ch.seidel.mobilequeue.model._

object UserRegistryActor {
  sealed trait UserRegistryMessage
  final case class ActionPerformed(user: User, description: String) extends UserRegistryMessage
  final case object GetUsers extends UserRegistryMessage
  final case class CreateUser(user: User) extends UserRegistryMessage
  final case class GetUser(id: Long) extends UserRegistryMessage
  final case class DeleteUser(id: Long) extends UserRegistryMessage

  def props: Props = Props[UserRegistryActor]
}

class UserRegistryActor extends Actor with ActorLogging {
  import UserRegistryActor._

  var users = Set.empty[User]

  def receive: Receive = {
    case GetUsers =>
      sender() ! Users(users.toSeq)
    case CreateUser(user) =>
      val withId = user.copy(id = users.foldLeft(0L)((acc, user) => {math.max(user.id, acc)}) + 1L)
      users += withId
      sender() ! ActionPerformed(withId, s"User ${withId.name} with id ${withId.id} created.")
    case GetUser(id) =>
      sender() ! users.find(_.id == id)
    case DeleteUser(id) =>
      val toDelete = users.find(_.id == id).getOrElse(User(id, "not existing!", "", ""))
      toDelete match {
        case User(id,_,_,_) if (id > 0) =>
          users -= toDelete
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete, s"User ${id} deleted.")
  }
}
