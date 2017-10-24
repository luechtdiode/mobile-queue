package ch.seidel.mobilequeue.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import spray.json.{JsValue, JsonReader, _}

import scala.util.Try

trait EnrichedJson {
  implicit class RichJson(jsValue: JsValue) {
    def asOpt[T](implicit reader: JsonReader[T]): Option[T] = Try(jsValue.convertTo[T]).toOption
    def canConvert[T](implicit reader: JsonReader[T]): Boolean = Try(jsValue.convertTo[T]).isSuccess
  }

  implicit class JsonString(string: String) {
    def asJsonOpt[T](implicit reader: JsonReader[T]): Option[T] = Try(string.parseJson.convertTo[T]).toOption
    def canConvert[T](implicit reader: JsonReader[T]): Boolean = Try(string.parseJson.convertTo[T]).isSuccess
  }
}

trait Hashing {
  def sha256(text: String): String = {
    // Create a message digest every time since it isn't thread safe!
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(text.getBytes(StandardCharsets.UTF_8)).map("%02X".format(_)).mkString
  }
}