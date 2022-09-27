package part1_recap

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.util.{Failure, Success}

object AkkaStreamsRecap extends App {

  implicit val system = ActorSystem("AkkaStreamsRecap")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val source = Source(1 to 100) //from where the data is coming from
  val sink = Sink.foreach[Int](println) //final processing destination of the data
  val flow = Flow[Int].map(x => x + 1) //intermediary processing of the data

  //val runnableGraph = source.via(flow).to(sink)
  //runnableGraph.run()

  //val simplMatarializedValue = runnableGraph.run() //materialized value

  //More Materialized values
  val sumSink = Sink.fold[Int,Int](0)((currentSum, elem) => currentSum + elem)
  val sumFuture = source.runWith(sumSink)

  sumFuture.onComplete {
    case Success(value) => println(s"The sum of all numbers from the simple source $value")
    case Failure(exception) => println(s"Summing of the elments ${exception.getStackTrace}")
  }

  //val anotherMaterializedValue = source.viaMat(flow)(Keep.right).toMat(sink)(Keep.left).run()
  /*
  1 - materializing a graph means materializing all the components
  2 - a materialized value can be anything at all
   */
  /*
  Backpressure actions
  -buffer elements
  - apply a strategy in case the buffer overflows
  -fail the entire system

   */
  val bufferedFlow = Flow[Int].buffer(10, OverflowStrategy.dropHead)

  source.async
    .via(bufferedFlow).async
    .runForeach { e =>
      Thread.sleep(100)
      println(e)
    }




}
