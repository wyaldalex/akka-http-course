package part3_highlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethod, HttpMethods, HttpRequest, HttpResponse, StatusCode, StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.CreateGuitar
import spray.json._

object HighLevelIntro extends App {


  //1) boilerplate/required system context
  implicit val system = ActorSystem("lowLevelServer")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  //directives
  import akka.http.scaladsl.server.Directives._

  val simpleRoute: Route =
    path("home") { //DIRECTIVE
      complete(StatusCodes.OK) //DIRECTIVE
    }

  val chainedComplexRoute : Route =
    path("myEndPoint") {
      get {
        complete(StatusCodes.OK)
      } ~
      put {
        complete(StatusCodes.OK)
      }
    } ~
    path ("home") {
      get {
        complete(HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    <h1>Exercise Response</h1>
            |  </body>
            |</html>
            |""".stripMargin
        ))
      }
    }
  Http().bindAndHandle(chainedComplexRoute,"localhost",10001)

}
