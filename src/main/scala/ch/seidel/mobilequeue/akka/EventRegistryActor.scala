package ch.seidel.mobilequeue.akka

import akka.actor.{ Actor, ActorLogging, Props }
import akka.actor.actorRef2Scala

import ch.seidel.mobilequeue.model._

object EventRegistryActor {
  sealed trait EventRegistryMessage
  final case class ActionPerformed(event: Event, description: String) extends EventRegistryMessage
  final case object GetEvents extends EventRegistryMessage
  final case class CreateEvent(event: Event) extends EventRegistryMessage
  final case class GetEvent(id: Long) extends EventRegistryMessage
  final case class DeleteEvent(id: Long) extends EventRegistryMessage

  def props: Props = Props[EventRegistryActor]
}

class EventRegistryActor extends Actor with ActorLogging {
  import EventRegistryActor._

  var events = Set.empty[Event]

  def receive: Receive = {
    case GetEvents =>
      sender() ! Events(events.toSeq)
    case CreateEvent(event) =>
      val withId = event.copy(id = events.foldLeft(0L)((acc, event) => {math.max(event.id, acc)}) + 1L)
      events += withId
      sender() ! ActionPerformed(withId, s"Event ${withId.id} created.")
    case GetEvent(id) =>
      sender() ! events.find(_.id == id)
    case DeleteEvent(id) =>
      val toDelete = events.find(_.id == id).getOrElse(Event(id, 0, "not existing!", ""))
      toDelete match {
        case Event(id,_,_,_) if (id > 0) =>
          events -= toDelete
        case _ =>
      }      
      sender() ! ActionPerformed(toDelete, s"Event ${id} deleted.")
  }
}
