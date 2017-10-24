package ch.seidel.mobilequeue.akka

import scala.concurrent.Future
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
//import akka.cluster.pubsub.{DistributedPubSub, DistributedPubSubMediator}
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

case class ActorSinkSource(actor: ActorRef, sink: Graph[SinkShape[Message], Any], source: Source[Nothing, ActorRef])

class ClientActor(eventRegistryActor: ActorRef, userRegistryActor: ActorRef) extends Actor with Hashing with ActorLogging {
  import akka.pattern.pipe
  // Get the implicit ExecutionContext from this import
  import scala.concurrent.ExecutionContext.Implicits.global
  // Required by the `ask` (?) method below
  private implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration
//  val mediator = DistributedPubSub(context.system).mediator
  var wsSend: Option[ActorRef] = None
  var user: Option[User] = None
  
  override def preStart(): Unit = {
    log.debug("Starting client actor")
  }

  override def postStop: Unit = {
    wsSend.foreach(context.stop)
  }
  
  def authenticated: Receive = {
    // wird vom Event-Actor gesendet
//    case p @ Publish(channel, _, _, _) =>
//      println(s"Publish $p")
//      mediator ! DistributedPubSubMediator.Publish(sha256(channel), ChannelMessage.from(p))

//    case m @ ChannelMessage(_, _, _, _) => // ticket wird abgerufen => meldung an websocket-Client
//      println(m.toString)
//      wsSend.foreach(_ ! TextMessage(m.toJson.compactPrint))

    case TicketActionPerformed(_, text) => 
      wsSend.foreach(_ ! TextMessage(text))
      
    case s @ Subscribe(channel) => // with channel = (event, ticket) => registriere ticket in der TicketRegistry
      log.debug(s"Subscribe $s")
      eventRegistryActor ! CreateEventTicket(Ticket(0L, user.get.id, channel, "", ""), self)
//      mediator ! DistributedPubSubMediator.Subscribe(sha256(channel), self)
    case s: String =>
      log.debug(s"Subscribe $s")
      eventRegistryActor ! CreateEventTicket(Ticket(0L, user.get.id, 1L, "", ""), self)

    case s @ UnSubscribe(channel) => // abmeldung
      log.debug(s"UnSubscribe $s")
//      mediator ! DistributedPubSubMediator.Unsubscribe(sha256(channel), self)
    
  }
  
  override def receive = {
    case ref: ActorRef => 
      wsSend = Some(ref)
      wsSend.foreach(_ ! TextMessage("Connection established. Please authenticated with Username and Password!"))

    case l @ LogIn(name, password) => userRegistryActor ? Authenticate(name, password) pipeTo self
    case s: String if s.startsWith("LogIn") => userRegistryActor ? Authenticate("Roland", "123") pipeTo self
    
    case UserActionPerformed(authenticatedUser, _) =>
      log.debug("user authenticated: " + authenticatedUser)
      user = Some(authenticatedUser)
      wsSend.foreach(_ ! TextMessage(s"Hi ${authenticatedUser.name}, You're welcome. Please get your ticket and wait until we call you!"))
      context.become(authenticated)

    case _: Unit =>
      log.debug("Closing client actor")
      context.stop(self)

  }
//
//  // other dependencies that TicketRoutes use
//  def ticketRegistryActor: ActorRef
//  
//  // Required by the `ask` (?) method below
//  private implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration
//  
//  private var ticketTriggers = List[Promise[Message]]()
//  
//  def triggerNextClients(nextCnt: Integer) {
//    val triggerClients = ticketTriggers.take(nextCnt)
//    ticketTriggers = ticketTriggers.drop(nextCnt)
//    println("sending messag to " + triggerClients)
//    val msg = TextMessage("you're invited to arrive in the next 10 minutes to start our attraction!")
//    triggerClients.foreach(client => client.success(msg))
//  }
//  
//  def receiveIssueRequest(x: Message) {
//    x match {
//      case TextMessage.Strict(msg) => 
//        println(msg)
//        val t = Ticket(
//            id=1L, 
//            userid=1L,
//            eventid=Random.nextLong(),
//            notBefore=msg,
//            notAfter="")
//            
//        ticketRegistryActor ? CreateTicket(t)        
//      case _ =>
//    }
//  }  
}

object ClientActor extends JsonSupport with EnrichedJson {
  private case object Start
  
  val messageFlow = {
    val messageToPublishSubscribe: PartialFunction[Message, String] = Function.unlift {
      case TextMessage.Strict(text) => Some(text)//.asJsonOpt[PubSub]
      case _ => None
    }
    Flow[Message].collect(messageToPublishSubscribe)
  }

  def createActorSinkSource(clientActor: ActorRef): ActorSinkSource = {
    val source: Source[Nothing, ActorRef] = Source.actorRef(256, OverflowStrategy.fail).mapMaterializedValue { wsSend =>
      clientActor ! wsSend
      wsSend
    }

    val sink: Graph[SinkShape[Message], Any] = messageFlow.to(Sink.actorRef(clientActor, Start))

    ActorSinkSource(clientActor, sink, source)
  }
}