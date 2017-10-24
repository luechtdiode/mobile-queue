package ch.seidel.mobilequeue.http

import akka.event.LoggingAdapter
import akka.actor.ActorSystem
import ch.seidel.mobilequeue.app.Core._

trait RouterLogging {
  lazy val log = akka.event.Logging(system, classOf[RouterLogging])
}