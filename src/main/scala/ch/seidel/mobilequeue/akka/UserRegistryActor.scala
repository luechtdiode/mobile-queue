package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props }
import akka.actor.actorRef2Scala

import ch.seidel.mobilequeue.model._
import java.util.UUID

object UserRegistryActor {
  sealed trait UserRegistryMessage
  final case class ActionPerformed(user: User, description: String) extends UserRegistryMessage
  final case class Authenticate(username: String, password: String, deviceId: String) extends UserRegistryMessage
  final case object GetUsers extends UserRegistryMessage
  final case class CreateUser(user: User) extends UserRegistryMessage
  final case class GetUser(id: Long) extends UserRegistryMessage
  final case class DeleteUser(id: Long) extends UserRegistryMessage

  def props: Props = Props[UserRegistryActor]
}

class UserRegistryActor extends Actor with ActorLogging {
  import UserRegistryActor._

  var users = Map.empty[User, Seq[Ticket]]
  
  def isUnique(username: String, deviceId: String) = users.keys.forall(u => u.name != username && !u.deviceIds.contains(deviceId))
  
  def createUser(user: User): User = {
    val withId = user.copy(id = users.keys.foldLeft(0L)((acc, user) => {math.max(user.id, acc)}) + 1L)
    users += (withId -> Seq.empty)
    withId
  }
  
  def receive: Receive = {
    
    case Authenticate(name, pw, deviceId) => 
      if(isUnique(name, deviceId)) {
        val withId = createUser(User(0, Seq(deviceId), name, pw, "", ""))
        sender() ! ActionPerformed(withId, s"User ${withId.id} authenticated.")
        log.debug("user created " + withId)
      } else {
        users.keys
          .filter(u => u.name == name && (u.password == pw || u.deviceIds.contains(deviceId)))
          .foreach{u => 
            val ud = if (u.deviceIds.contains(deviceId)) u else {
              val udr = u.copy(deviceIds = u.deviceIds :+ deviceId)
              users = users - u + (udr -> users(u))
              udr.copy(deviceIds = Seq(deviceId))
            }
            sender() ! ActionPerformed(ud, s"User ${u.id} authenticated.")
            log.debug("user authenticated " + ud)
          }
      }
      
    case GetUsers =>
      sender() ! Users(users.keys.toSeq.map(_.copy(password = "***")))
      
    case CreateUser(user) =>
      if(isUnique(user.name, user.deviceIds.headOption.getOrElse(UUID.randomUUID().toString()))) {
        val withId = createUser(user)
        sender() ! ActionPerformed(withId.copy(password = "***") , s"User ${withId.name} with id ${withId.id} created.")
      } else {
        sender() ! ActionPerformed(user.copy(password = "***") , s"User ${user.name} exists already.")
      }
      
    case GetUser(id) =>
      sender() ! users.keys.find(_.id == id).getOrElse(User(id, Seq.empty, "not existing!", "", "", "")).copy(password = "***")
      
    case DeleteUser(id) =>
      val toDelete = users.keys.find(_.id == id).getOrElse(User(id, Seq.empty, "not existing!", "", "", ""))
      toDelete match {
        case User(id,_,_,_,_,_) if (id > 0) =>
          users -= toDelete
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete.copy(password = "***"), s"User ${id} deleted.")
  }
}
