package ch.seidel.mobilequeue.http

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.swagger.akka.model.{ Contact, Info }
import com.github.swagger.akka.SwaggerHttpService

import com.github.swagger.akka.SwaggerHttpService

import ch.seidel.mobilequeue.app.Config
import ch.seidel.mobilequeue.app.Core._
import com.github.swagger.akka.model.`package`.License

trait SwaggerDocService extends SwaggerHttpService with RouterLogging with Config {
  override val apiClasses = Set(classOf[UserRoutes]) //, classOf[EventRoutes], classOf[TicketRoutes]
  override val host = s"${httpInterface}:${httpPort}" //the url of your api, not swagger's json endpoint
  override val basePath = "/mbq" //the basePath for the API you are exposing
  override val apiDocsPath = "/api-docs" //where you want the swagger-json endpoint exposed
  override val info = Info(
    "API docs for Mobile Ticket Queue App", "v1", "Mobile Ticket Queue App",
    termsOfService = "use at your own risk",
    Some(Contact("Roland Seidel", "https://github.com/luechtdiode/mobile-queue", "")),
    license = Some(License("MIT", "https://raw.githubusercontent.com/luechtdiode/mobile-queue/master/LICENSE")),
    Map()
  ) //provides license and other description details
  val swaggerRoutes = routes
}
