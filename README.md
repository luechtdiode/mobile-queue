# Mobile-queue

Digital and mobile queue for events, which allows the interested parties to share the waiting time for more meaningful activities as standing the whole time in the real queue.

# Technology motivation

* See [akka-streams-a-motivating-example from blog.colinbreck.com](http://blog.colinbreck.com/akka-streams-a-motivating-example/)
* See [swagger-akka-http-sample from github.com/pjfanning](https://github.com/pjfanning/swagger-akka-http-sample)

# Starting-point

``sbt -Dsbt.version=0.13.15 new https://github.com/akka/akka-http-scala-seed.g8``

# Ticket-Statehandling

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