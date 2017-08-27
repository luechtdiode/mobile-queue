package ch.seidel.mobilequeue.http

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.swagger.akka.model.{Contact, Info}
import com.github.swagger.akka.SwaggerHttpService

import com.github.swagger.akka.SwaggerHttpService

import ch.seidel.mobilequeue.app.Config

trait SwaggerDocService extends SwaggerHttpService with RouterLogging with Config {
  implicit val materializer: ActorMaterializer
  override val apiClasses = Set(classOf[UserRoutes])//, classOf[EventRoutes], classOf[TicketRoutes]
  override val host = s"${httpInterface}:${httpPort}" //the url of your api, not swagger's json endpoint
  override val basePath = "/"    //the basePath for the API you are exposing
  override val apiDocsPath = "/api-docs" //where you want the swagger-json endpoint exposed
  override val info = Info(
      "API docs for Mobile Queue App","v1","Mobile Queue App",
      "",
      Some(Contact("Roland Seidel","www.irgendwas.net","irgenwas@gmail.com")),
      None,
      Map()) //provides license and other description details
  val swaggerRoutes = routes
}
