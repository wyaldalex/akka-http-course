package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethod, HttpMethods, HttpRequest, HttpResponse, StatusCode, StatusCodes, Uri}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.CreateGuitar
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

case class Guitar(make: String, model: String)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars

  def props : Props = Props(new GuitarDB)
}

class GuitarDB extends Actor with ActorLogging {
  import GuitarDB._
  var guitars : Map[Int,Guitar] = Map()
  var guitarIdCounter : Int = 0

  override def receive: Receive = {
    case CreateGuitar(guitar) =>
      log.info(s"Creating guitar ${guitar.toString}")
      guitars = guitars + (guitarIdCounter -> guitar)
      sender() ! GuitarCreated(guitarIdCounter)
      guitarIdCounter += 1
    case FindGuitar(id) =>
      log.info(s"Looking for Guitar with id $id")
      sender() ! guitars.get(id)
    case FindAllGuitars =>
      log.info("Retrieving all guitars")
      sender() ! guitars.values.toList
  }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat2(Guitar)
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {
  //1) boilerplate/required system context
  implicit val system = ActorSystem("lowLevelServer")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  //Marshalling
  val simpleGuitar = Guitar("fender","stratocster")
  println(simpleGuitar.toJson.prettyPrint)

  //Unmarshalling
  val simpleJsonString =
    """
      |{
      | "make" : "fender",
      | "model" : "xyz1"
      |}
      |""".stripMargin

  val someOtherGuitar = simpleJsonString.parseJson.convertTo[Guitar]
  println(someOtherGuitar.toString)

  //Building the actual API
  //Prepare some data in the db
  val guitarDB = system.actorOf(GuitarDB.props,"LowLevelGuitarDBActor")
  val guitarList = List(
    Guitar("fender","stratocaster"),
    Guitar("gibson","12312xax"),
    Guitar("yamaha", "yamah11")
  )

  guitarList.foreach{ g =>
    guitarDB ! CreateGuitar(g)
  }

  implicit val defaultTimeout = Timeout(2 seconds)
  import GuitarDB._
  def getGuitar(query: Query) : Future[HttpResponse] = {
    val guitarId = query.get("id").map(_.toInt) //Default is string

    guitarId match {
      case None => Future(HttpResponse(StatusCodes.NotFound))
      case Some(id: Int) =>
        val futureGuitar : Future[Option[Guitar]] = (guitarDB ? FindGuitar(id)).mapTo[Option[Guitar]]
        futureGuitar.map {
          case None => HttpResponse(StatusCodes.NotFound)
          case Some(guitar) =>
            HttpResponse(
              entity = HttpEntity(
                ContentTypes.`application/json`,
                guitar.toJson.prettyPrint
              )
            )

        }

    }
  }
  //retrieve all guitars
  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET,uri@Uri.Path("/api/guitar"),_,_,_) =>
      val queryParam = uri.query()
      if (queryParam.isEmpty) {
        val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
        guitarsFuture.map { guitar =>
          HttpResponse(
            entity = HttpEntity(
              ContentTypes.`application/json`,
              guitar.toJson.prettyPrint
            )
          )
        }

      } else {
        //fetch guitar associated with the guitarId /api/guitar?id=121
        getGuitar(queryParam)
      }

    case HttpRequest(HttpMethods.POST,Uri.Path("/api/guitar"),_,entity,_) =>
      //entities are a Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]

        val guitarCreatedFuture : Future[GuitarCreated] = (guitarDB ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map{ r =>
          HttpResponse(
            StatusCodes.Created,
            entity = HttpEntity(
              ContentTypes.`text/plain(UTF-8)`,
              s"Guitar Created with Id: ${r.id.toString}"
            )
          )
        }
      }

    //discard bytes on any request that does not comply
    case httpRequest: HttpRequest =>
      httpRequest.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }
  }
  //Create the binding to run
  Http().bindAndHandleAsync(requestHandler,"localhost",10001)

}
