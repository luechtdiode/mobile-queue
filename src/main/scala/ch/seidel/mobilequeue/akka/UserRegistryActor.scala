package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props }
import akka.actor.actorRef2Scala

import ch.seidel.mobilequeue.model._

object UserRegistryActor {
  sealed trait UserRegistryMessage
  final case class ActionPerformed(user: User, description: String) extends UserRegistryMessage
  final case class Authenticate(username: String, password: String) extends UserRegistryMessage
  final case object GetUsers extends UserRegistryMessage
  final case class CreateUser(user: User) extends UserRegistryMessage
  final case class GetUser(id: Long) extends UserRegistryMessage
  final case class DeleteUser(id: Long) extends UserRegistryMessage

  def props: Props = Props[UserRegistryActor]
}

class UserRegistryActor extends Actor with ActorLogging {
  import UserRegistryActor._

  var users = Set.empty[User]
  
  def isUnique(username: String) = users.forall(u => u.name != username)
  
  def createUser(user: User): User = {
    val withId = user.copy(id = users.foldLeft(0L)((acc, user) => {math.max(user.id, acc)}) + 1L)
    users += withId
    withId
  }
  
  def receive: Receive = {
    
    case Authenticate(name, pw) => 
      if(isUnique(name)) {
        val withId = createUser(User(0, name, pw, "", ""))
        sender() ! ActionPerformed(withId, s"User ${withId.id} authenticated.")
        log.debug("user created " + withId)
      } else {
        users
          .filter(u => u.name == name && u.password == pw)
          .foreach{u => 
            sender() ! ActionPerformed(u, s"User ${u.id} authenticated.")
            log.debug("user authenticated " + u)
          }
      }
      
    case GetUsers =>
      sender() ! Users(users.toSeq.map(_.copy(password = "***")))
      
    case CreateUser(user) =>
      if(isUnique(user.name)) {
        val withId = createUser(user)
        sender() ! ActionPerformed(withId.copy(password = "***") , s"User ${withId.name} with id ${withId.id} created.")
      } else {
        sender() ! ActionPerformed(user.copy(password = "***") , s"User ${user.name} exists already.")
      }
      
    case GetUser(id) =>
      sender() ! users.find(_.id == id).getOrElse(User(id, "not existing!", "", "", "")).copy(password = "***")
      
    case DeleteUser(id) =>
      val toDelete = users.find(_.id == id).getOrElse(User(id, "not existing!", "", "", ""))
      toDelete match {
        case User(id,_,_,_,_) if (id > 0) =>
          users -= toDelete
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete.copy(password = "***"), s"User ${id} deleted.")
  }
}
