package ch.seidel.mobilequeue.http

import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.WordSpec

import authentikat.jwt.JsonWebToken
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JwtSupportSpec extends WordSpec with Matchers with JwtSupport {

  "JwtSupport" should {
    "be valid after creation" in {
      val userid = 4711L
      val jwtClaims = setClaims(userid, 3)
      val jwt = JsonWebToken(jwtHeader, jwtClaims, jwtSecretKey)
      JsonWebToken.validate(jwt, jwtSecretKey) should ===(true)
    }

    "be consistent with userid in set-/getClaims" in {
      val userid = 4711L
      val jwtClaims = setClaims(userid, 3)
      val jwt = JsonWebToken(jwtHeader, jwtClaims, jwtSecretKey)
      val claims = getClaims(jwt)
      val returneduserId = getUserID(claims)

      userid should ===(returneduserId)
    }
  }
}
