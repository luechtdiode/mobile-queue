package ch.seidel.mobilequeue.model

import io.swagger.annotations.{ApiModel, ApiModelProperty}
import scala.annotation.meta.field

@ApiModel(description = "A ticket object")
case class Ticket
(
  @(ApiModelProperty @field)(value = "unique identifier for the ticket")
  id: Long,
  @(ApiModelProperty @field)(value = "identifier for the user")
  userid: Long,
  @(ApiModelProperty @field)(value = "identifier for the event")
  eventid: Long,
  @(ApiModelProperty @field)(value = "whish to be applied not before")
  notBefore: String,
  @(ApiModelProperty @field)(value = "whish to be applied not after")
  notAfter: String
)

@ApiModel(description = "A List of ticket object")
final case class Tickets(tickets: Seq[Ticket])