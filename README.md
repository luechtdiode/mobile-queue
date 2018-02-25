# Mobile-Ticket-Queue

[![Travis Build Status](https://travis-ci.org/luechtdiode/mobile-queue.svg?branch=master)](https://travis-ci.org/luechtdiode/mobile-queue.svg?branch=master)

Digital and mobile queue for events, which allows the interested parties to share the waiting time for more meaningful activities as standing the whole time in the real queue.

# Support as Tester
## Try as Android-Device Tester and download Alpha-Version from Play-Store
[Mobile Ticket Queue (alpha testversion) on Google Play-Store](https://play.google.com/store/apps/details?id=ch.seidel.mobile_ticket_queue&rdid=ch.seidel.mobile_ticket_queue)

## Try as Tester with Browser-Client
[Mobile Ticket Queue as Browser App](https://38qniweusmuwjkbr.myfritz.net/mbq/)

# Support for Translations
Feel free to translate the i18n-Files by forking this repo and publish a pull-request.

# Architecture

* Client-Server
* Mobile-First
* Hybrid Client to take advantage of device-apis such as Vibrate or Background-Mode
## Entities
Entity | Description
-|-
User | A User is identified primarily by his used device's id. He can have more than one device-id, but then he should use a password. Actually, we just want to know his username.
Event | A Event represents a digital counter-desk for somewhat-ever kindful thing. On that digital counter-desk, User can issue tickets to be invited in the correct order of issuance.
Ticket | A Ticket represents the promise to be called, when the ticket-holders turn will start.
Client | A Client is the physical connection to the User's device. If the user is online, he has always a Client per used device as companion. If the User uses two or more Clients, such as a Mobile-Device and a Tablet-Device at the same time, there will be two Clients instantiated. All messages from and to the user go through his device's Client. Also, all messages are propagated to all Clients of the user.
## State-Handling
As it's a kind of QCRS-Application, new state comes from Events/Actions and is propagated to all subscribers
### Backend-State
* No persistence (atm),
* Per Entity a Akka-Actor (Event => EventRegistryActor), could be regional shared (scaling)
* Hierarchical Supervision (EventRegistryActor owns per Event a TicketRegistryActor)
* Beyond, the UserRegistryActor as root-actor (could be outplaced or connected with a external user-service)
* As Facade per Websocket-Connection exists a ClientActor.
### Client-State
* HTML5-LocalStore to remember Device-ID and Username
* Not yet implemented Redux-Store.
## Technology-Layers
Layer | Technology
-|-
Backend | Java8, Scala, Akka-http
Api | Rest, Websocket over https/wss
Frontend | Ionic3 / Angular4, Rxjs, Cordova

# Concepts
## Ticket-Statehandling

```
          State |  Issued  |  Called  | Skipped |   Confirmed  |  Closed
 Actor (Action) |          |          |         |              |        
 ---------------|----------|----------|---------|--------------|--------
 User subscribe							                          
 System ----------->O												   
 System (Next n) 1)[O----------O]->O
                 2)                                    O----------->O
                 3) O<---------------------O
   
 User (confirm)                O---------------------->O                
 User (skip)     1)            O---------->O
                 2)[O----------O]->O                             
 User (unsubscribe)[O----------O-----------O]---------------------->O  
                                                              
 Statistic                                                    
 ---------------|----------|----------|---------|--------------|--------
 Sums           |             Waiting           |   Accepted   |        
 -----------------------------------------------------------------------
```
## Communication-Flow
```
From    \     To | Event Registry     | Ticket Registry | User Registry      | ClientActor                | Web-Client     | Protocol
-----------------|--------------------|-----------------|--------------------|----------------------------|----------------|---------
Event Registry   |                    | IssueTicket     | EventCreated(*)    |                            |                | Akka
                 |       ****         | CloseTicket     | EventUpdated(*)    |                            |                | Akka    
                 |                    | EventUpdated    | EventDeleted(*)    |                            |                | Akka    
-----------------|--------------------|-----------------|--------------------|----------------------------|----------------|---------
Ticket Registry  |                    |                 |                    | TicketIssued               | **             | Akka
                 |                    |                 |                    | TicketCalled               | **             | Akka
                 |                    |      ****       |                    | TicketConfirmed            | **             | Akka                 
                 |                    |                 |                    | TicketClosed               | **             | Akka
-----------------|--------------------|-----------------|--------------------|----------------------------|----------------|---------
User Registry    |                    |                 |                    | UserAuthenticated          | **             | Akka
                 |                    |                 |                    | UserAuthenticationFailed   | **             | Akka
                 |                    |                 |                    | TicketIssuedPropagated     | **             | Akka
                 |                    |                 |                    | TicketCalledPropagated     | **             | Akka
                 |                    |                 |       ****         | TicketConfirmedPropagated  | **             | Akka
                 |                    |                 |                    | TicketClosedPropagated     | **             | Akka
                 |                    |                 |                    | EventCreated               | **             | Akka
                 |                    |                 |                    | EventUpdated               | **             | Akka
                 |                    |                 |                    | EventDeleted               | **             | Akka
-----------------|--------------------|-----------------|--------------------|----------------------------|----------------|---------
ClientActor      |                    |                 |                    |                            | **>>           | WS
                 | IssueEventTicket   | TicketConfirmed | Authenticate(*)    |                            |                | Akka          
                 | CloseEventTicket   | TicketSkipped   | TicketIssued(*)    |                            |                | Akka
                 |                    |                 | TicketCalled(*)    |           ****             |                | Akka
                 |                    |                 | TicketConfirmed(*) |                            |                | Akka
                 |                    |                 | TicketClosed(*)    |                            |                | Akka
-----------------|--------------------|-----------------|--------------------|----------------------------|----------------|---------
Web-Client       | request all events |                 | request user-names |                            |                | Rest         
(Device-App)     | getNextTickets     |                 |                    |                            |                | Rest
                 | addEvent           |                 |                    |                            |                | Rest
                 | updateEvent        |                 |                    |                            |                | Rest
                 | deleteEvent        |                 |                    |                            |      ****      | Rest
                 |                    |                 |                    | HiImOnline                 |                | WS
                 |                    |                 |                    | Subscribe                  |                | WS
                 |                    |                 |                    | TicketConfirmed            |                | WS
                 |                    |                 |                    | TicketSkipped              |                | WS
-----------------|--------------------|-----------------|--------------------|----------------------------|----------------|---------

(*) Propagation via user to broadcast the event to all connected Clients
** Events, that are streamed to the Web-Client's Websocket

```
# Dev thoughts
## Starting-point

``sbt -Dsbt.version=0.13.15 new https://github.com/akka/akka-http-scala-seed.g8``
## Technology- / Implementation- Motivations
### Reacitive, (event-)stream-based
* See [akka-streams-a-motivating-example from blog.colinbreck.com](http://blog.colinbreck.com/akka-streams-a-motivating-example/)
* See [swagger-akka-http-sample from github.com/pjfanning](https://github.com/pjfanning/swagger-akka-http-sample)
### CI
* See [travis-integration1](https://github.com/svenlaater/travis-ci-ionic-yml)
* See [travis-integration2](https://github.com/okode/ionic-travis)
* See [travis s3 deployment](https://medium.com/@itsdavidthai/comprehensive-aws-ec2-deployment-with-travisci-guide-7cafa9c754fc)
* See [use of travis fastlane](https://blog.fossasia.org/tag/travis/)


