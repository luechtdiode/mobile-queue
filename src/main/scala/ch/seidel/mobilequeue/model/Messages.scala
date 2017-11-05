package ch.seidel.mobilequeue.model

sealed trait MobileTicketQueueProtokoll

sealed trait MobileTicketQueueAction extends MobileTicketQueueProtokoll
case class HelloImOnline(username: String, deviceId: Option[String]) extends MobileTicketQueueAction // ->
case class LogIn(name: String, password: String) extends MobileTicketQueueAction // unused atm
case class Subscribe(channel: Long, count: Int) extends MobileTicketQueueAction // -> TicketIssued
case class UnSubscribe(channel: Long) extends MobileTicketQueueAction // -> TicketClosed

sealed trait MobileTicketQueueEvent extends MobileTicketQueueProtokoll
case class TicketIssued(ticket: Ticket) extends MobileTicketQueueEvent
case class TicketReactivated(ticket: Ticket) extends MobileTicketQueueEvent
case class TicketCalled(ticket: Ticket) extends MobileTicketQueueEvent
case class TicketExpired(ticket: Ticket) extends MobileTicketQueueEvent
case class TicketConfirmed(ticket: Ticket) extends MobileTicketQueueEvent
case class TicketAccepted(ticket: Ticket) extends MobileTicketQueueEvent
case class TicketSkipped(ticket: Ticket) extends MobileTicketQueueEvent
case class TicketClosed(ticket: Ticket) extends MobileTicketQueueEvent
case class UserTicketsSummary(waitingPosition: Int, waitingCnt: Int, calledCnt: Int, acceptedCnt: Int, skippedCnt: Int, closedCnt: Int) extends MobileTicketQueueEvent