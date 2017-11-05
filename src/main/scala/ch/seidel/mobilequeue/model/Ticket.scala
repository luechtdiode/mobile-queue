package ch.seidel.mobilequeue.model

import io.swagger.annotations.{ ApiModel, ApiModelProperty }
import scala.annotation.meta.field

sealed trait TicketState
case object Requested extends TicketState // from the user requestet
case object Issued extends TicketState // from the issuer issued
case object Called extends TicketState // from the issuer called
case object Confirmed extends TicketState // from the user confirmed
case object Skipped extends TicketState // from the user skipped
case object Closed extends TicketState // from the issuer closed

//object TicketState {
//}
@ApiModel(description = "A ticket object")
case class Ticket(
  @(ApiModelProperty @field)(value = "unique identifier for the ticket") id: Long,
  @(ApiModelProperty @field)(value = "identifier for the user") userid: Long,
  @(ApiModelProperty @field)(value = "identifier for the event") eventid: Long,
  @(ApiModelProperty @field)(value = "one of the TicketState (Requested, Issued, Called, Confirmed, Skipped, Closed)") state: TicketState = Requested,
  @(ApiModelProperty @field)(value = "identifier for the event") participants: Int = 1
)

@ApiModel(description = "A List of ticket object")
final case class Tickets(tickets: Seq[Ticket])