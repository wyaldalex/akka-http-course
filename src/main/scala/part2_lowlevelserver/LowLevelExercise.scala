package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethod, HttpMethods, HttpRequest, HttpResponse, StatusCode, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future

object LowLevelExercise extends App {


  //1) boilerplate/required system context
  implicit val system = ActorSystem("lowLevelServer")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  //2) define the handler
  val requestHandler : HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET,Uri.Path("/about"),_,_,_) =>
      Future(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |    <h1>Exercise Response</h1>
            |  </body>
            |</html>
            |""".stripMargin
        )
      ))
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) =>
      Future(HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`text/plain(UTF-8)`,
          "front door"
        )
      ))
    case request: HttpRequest =>
      Future(HttpResponse(
        StatusCodes.NotFound,
        entity = HttpEntity.Empty
      ))
  }

  //3) create the connection handler using the request handler
  val connectionHandler = Sink.foreach[IncomingConnection] { connection =>
    connection.handleWithAsyncHandler(requestHandler)
  }

  //4) Create the binding to start service requests
  Http().bind("localhost",8388).runWith(connectionHandler)




}
