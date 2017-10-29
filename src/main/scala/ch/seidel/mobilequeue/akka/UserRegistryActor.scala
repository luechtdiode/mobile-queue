package ch.seidel.mobilequeue.akka

import java.util.UUID

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import ch.seidel.mobilequeue.model.Ticket
import ch.seidel.mobilequeue.model.User
import ch.seidel.mobilequeue.model.Users
import ch.seidel.mobilequeue.model.UserDefaults

object UserRegistryActor {
  sealed trait UserRegistryMessage
  final case class ActionPerformed(user: User, description: String) extends UserRegistryMessage
  final case class Authenticate(username: String, password: String, deviceId: String) extends UserRegistryMessage
  final case class ClientConnected(user: User, deviceId: String, client: ActorRef) extends UserRegistryMessage

  final case object GetUsers extends UserRegistryMessage
  final case class CreateUser(user: User) extends UserRegistryMessage
  final case class GetUser(id: Long) extends UserRegistryMessage
  final case class DeleteUser(id: Long) extends UserRegistryMessage

  def props(eventRegistry: ActorRef): Props = Props(classOf[UserRegistryActor], eventRegistry)
}

class UserRegistryActor(eventRegistry: ActorRef) extends Actor with ActorLogging {
  import UserRegistryActor._
  import context._
  
  def isUnique(username: String, deviceId: String)(implicit users: Map[User, Seq[Ticket]]) = 
    users.keys.forall(u => u.name != username && !u.deviceIds.contains(deviceId))
  
  def createUser(user: User)(implicit users: Map[User, Seq[Ticket]]): User = {
    val withId = user.copy(id = users.keys.foldLeft(0L)((acc, user) => {math.max(user.id, acc)}) + 1L)
    become(operateWith(users + (withId -> Seq.empty)))
    withId
  }
  
  def operateWith(implicit users: Map[User, Seq[Ticket]] = Map.empty[User, Seq[Ticket]]): Receive = {
    
    case Authenticate(name, pw, deviceId) => 
      if(isUnique(name, deviceId)) {
        val withId = createUser(User(0, Seq(deviceId), name, pw, "", ""))
        sender() ! ActionPerformed(withId.withHiddenPassword, s"User ${withId.id} authenticated.")
        eventRegistry.forward(ClientConnected(withId, deviceId, sender))
        log.debug("user created " + withId)
      } else {
        users.keys.filter(u => u.name == name && (u.password == pw || u.deviceIds.contains(deviceId))) match {
          case u if(u.nonEmpty) =>
            val ud = if (u.head.deviceIds.contains(deviceId)) u.head else {
              val udr = u.head.copy(deviceIds = u.head.deviceIds :+ deviceId)
              become(operateWith(users - u.head + (udr -> users(u.head))))
              udr.copy(deviceIds = Seq(deviceId)).withHiddenPassword
            }
            sender() ! ActionPerformed(ud, s"User ${u.head.id} authenticated.")
            eventRegistry.forward(ClientConnected(ud, deviceId, sender))
            log.debug("user authenticated " + ud)
          case _ =>
            sender() ! ActionPerformed(UserDefaults.empty(name), s"User ${name} exists already.")
        }
      }
      
    case GetUsers =>
      sender() ! Users(users.keys.toSeq.map(_.withHiddenPassword.withHiddenDeviceIds))
      
    case CreateUser(user) =>
      if(isUnique(user.name, user.deviceIds.headOption.getOrElse(UUID.randomUUID().toString()))) {
        val withId = createUser(user)
        sender() ! ActionPerformed(withId.withHiddenPassword , s"User ${withId.name} with id ${withId.id} created.")
      } else {
        sender() ! ActionPerformed(UserDefaults.empty(user.name), s"User ${user.name} exists already.")
      }
      
    case GetUser(id) =>
      sender() ! users.keys.find(_.id == id).getOrElse(UserDefaults.empty(id)).withHiddenPassword.withHiddenDeviceIds
      
    case DeleteUser(id) =>
      val toDelete = users.keys.find(_.id == id).getOrElse(UserDefaults.empty(id))
      toDelete match {
        case User(id,_,_,_,_,_) if (id > 0) =>
          become(operateWith(users - toDelete))
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete.withHiddenPassword.withHiddenDeviceIds, s"User ${id} deleted.")
  }
  
  def receive = operateWith()
}
