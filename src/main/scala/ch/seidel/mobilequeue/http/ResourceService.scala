package ch.seidel.mobilequeue.http

import akka.http.scaladsl.server.Directives


trait ResourceService extends Directives {


  def appRoute = {
    pathPrefix("") { pathEndOrSingleSlash {
      getFromResource("app/index.html")
    }
    } ~
      getFromResourceDirectory("app")
  }

  def swaggerRoute =
    get {
      pathPrefix("swagger") { pathEndOrSingleSlash {
        getFromResource("swagger/index.html")
      }
      } ~
        getFromResourceDirectory("swagger")
    }

  val resourceRoutes = appRoute ~ swaggerRoute

}
