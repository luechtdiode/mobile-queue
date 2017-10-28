package ch.seidel.mobilequeue.akka

import scala.concurrent.Future
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Graph, OverflowStrategy, SinkShape}
import spray.json._
import ch.seidel.mobilequeue.model._
import ch.seidel.mobilequeue.akka.TicketRegistryActor.CreateTicket
import ch.seidel.mobilequeue.akka.EventRegistryActor.{ActionPerformed => EventActionPerformed}
import ch.seidel.mobilequeue.akka.UserRegistryActor.{ActionPerformed => UserActionPerformed}
import ch.seidel.mobilequeue.akka.TicketRegistryActor.{ActionPerformed => TicketActionPerformed}
import ch.seidel.mobilequeue.akka.UserRegistryActor.Authenticate
import scala.util.Try
import scala.util.Success
import ch.seidel.mobilequeue.http.JsonSupport
import akka.actor.ActorLogging
import ch.seidel.mobilequeue.akka.EventRegistryActor.CreateEventTicket
import ch.seidel.mobilequeue.model.HelloImOnline
import java.util.UUID
import ch.seidel.mobilequeue.akka.TicketRegistryActor.DeleteTicket
import ch.seidel.mobilequeue.akka.EventRegistryActor.DeleteEventTicket
import ch.seidel.mobilequeue.http.JsonSupport
import akka.http.scaladsl.model.ws.BinaryMessage
import scala.util.Failure
import akka.actor.PoisonPill

class ClientActor(eventRegistryActor: ActorRef, userRegistryActor: ActorRef) extends Actor with Hashing with ActorLogging with JsonSupport {
  import akka.pattern.pipe
  // Get the implicit ExecutionContext from this import
  import scala.concurrent.ExecutionContext.Implicits.global
  // Required by the `ask` (?) method below
  private implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration
  private case object KeepAlive
//  val mediator = DistributedPubSub(context.system).mediator
  var wsSend: Option[ActorRef] = None
  var user: Option[User] = None
  var ticket: Option[Ticket] = None
  val liveticker = context.system.scheduler.schedule(15.second, 15.second) {
        self ! KeepAlive
      }
  override def preStart(): Unit = {
    println("Starting client actor")
  }

  override def postStop: Unit = {
    liveticker.cancel()
    println("client actor stopped")
    wsSend.foreach(context.stop)
  }
   
  def authenticated: Receive = {
    case KeepAlive =>
      wsSend.foreach(_ ! TextMessage("keepAlive"))
      
      
    case ta @ TicketActionPerformed(t, text) => 
      ticket = Some(t)
      val tm = TextMessage(ta.toJson.toJsonStringWithType(ta))
      wsSend.foreach(_ ! tm)
      
    case s @ Subscribe(channel) => // with channel = (event, ticket) => registriere ticket in der TicketRegistry
      println(s"Subscribe $s")
      eventRegistryActor ! CreateEventTicket(Ticket(0L, user.get.id, channel, "", ""), self)

    case s @ UnSubscribe(channel) => // abmeldung
      println(s"UnSubscribe $s")
      ticket.filter(t => t.eventid == channel).foreach(t => { 
        ticket = None
        eventRegistryActor ! DeleteEventTicket(t.eventid, t.id)
      })
      
    case ClientActor.Stop =>
      println("Closing client actor")
      context.stop(self)
      
    case _: Unit =>
      println("Closing client actor")
      context.stop(self)
  }
  
  override def receive = {
    case ref: ActorRef => 
      wsSend = Some(ref)
      wsSend.foreach(_ ! TextMessage("Connection established. Please authenticate with Username and Password!"))
      
    case KeepAlive =>
      println("i'm still alive")
      
    case h @ HelloImOnline(username, deviceId) => 
      val di = deviceId.filter(i => i != "").getOrElse(UUID.randomUUID().toString())
      userRegistryActor ? Authenticate(username, "", di) pipeTo self
    
    case UserActionPerformed(authenticatedUser, _) =>
      println("user authenticated: " + authenticatedUser)
      user = Some(authenticatedUser)
      wsSend.foreach(_ ! TextMessage(authenticatedUser.deviceIds.head))
      context.become(authenticated)

    case ClientActor.Stop =>
      println("Closing client actor")
      context.stop(self)
    case _: Unit =>
      println("Closing client actor")
      context.stop(self)

  }
}

object ClientActor extends JsonSupport with EnrichedJson {
  case object Stop
  import ch.seidel.mobilequeue.app.Core._
  
  def websocketFlow(actorRef: ActorRef): Flow[Message, PubSub, Any] =
    Flow[Message]
      .mapAsync(1) {
        case TextMessage.Strict(text) => Future.successful(text.asType[PubSub])
        case TextMessage.Streamed(stream) => stream.runFold("")(_ + _).map(s => s.asType[PubSub])
        case b: BinaryMessage => throw new Exception("Binary message cannot be handled")
      }.via(reportErrorsFlow(actorRef))
  
  def reportErrorsFlow[T](actorRef: ActorRef): Flow[T, T, Any] =
    Flow[T]
      .watchTermination()((_, f) => f.onComplete {
        case Failure(cause) =>
          println(s"WS stream failed with $cause")
        case _ => // ignore regular completion
          println(s"WS stream closed")
      })
      
  def createActorSinkSource(clientActor: ActorRef): Flow[Message, Message, Any] = {
    
    val source: Source[Nothing, ActorRef] = Source.actorRef(256, OverflowStrategy.fail).mapMaterializedValue { wsSend =>
      clientActor ! wsSend
      wsSend
    }

    val sink: Graph[SinkShape[Message], Any] = websocketFlow(clientActor).to(Sink.actorRef(clientActor, Stop))
    
    Flow.fromSinkAndSource(sink, source)
  }
}