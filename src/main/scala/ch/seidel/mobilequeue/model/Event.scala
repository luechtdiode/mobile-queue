package ch.seidel.mobilequeue.model

import io.swagger.annotations.{ ApiModel, ApiModelProperty }
import scala.annotation.meta.field

@ApiModel(description = "A event object")
case class Event(
  @(ApiModelProperty @field)(value = "unique identifier for the event") id: Long,
  @(ApiModelProperty @field)(value = "unique identifier for the event") userid: Long,
  @(ApiModelProperty @field)(value = "title for the event") eventTitle: String,
  @(ApiModelProperty @field)(value = "date for the event") date: String
)

@ApiModel(description = "A List of event object")
final case class Events(events: Seq[Event])