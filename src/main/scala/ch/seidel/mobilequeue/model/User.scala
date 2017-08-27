package ch.seidel.mobilequeue.model

import io.swagger.annotations.{ApiModel, ApiModelProperty}
import scala.annotation.meta.field

@ApiModel(description = "A user object")
final case class User
(
  @(ApiModelProperty @field)(value = "unique identifier for the user")
  id: Long,
  @(ApiModelProperty @field)(value = "name of the user")
  name: String,
  @(ApiModelProperty @field)(value = "email of the user")
  mail: String,
  @(ApiModelProperty @field)(value = "mobile-phone of the user")
  mobile: String
)

@ApiModel(description = "A List of user object")
final case class Users(users: Seq[User])
