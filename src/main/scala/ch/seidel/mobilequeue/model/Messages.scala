package ch.seidel.mobilequeue.model

import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, _}

sealed trait PubSub

case class LogIn(name: String, password: String) extends PubSub
case class Subscribe(channel: Long) extends PubSub
//case class Publish(channel: String, event: String, data: JsValue) extends PubSub
case class UnSubscribe(channel: Long) extends PubSub

//case class ChannelMessage(channel: String, event: String, data: JsValue)

