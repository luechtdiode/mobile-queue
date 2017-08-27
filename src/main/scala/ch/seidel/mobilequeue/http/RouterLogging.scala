package ch.seidel.mobilequeue.http

import akka.event.LoggingAdapter
import akka.actor.ActorSystem

trait RouterLogging {
  implicit def system: ActorSystem
  lazy val log = akka.event.Logging(system, classOf[RouterLogging])
}