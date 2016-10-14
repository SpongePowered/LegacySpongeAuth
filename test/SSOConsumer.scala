import java.math.BigInteger
import java.net.{URLDecoder, URLEncoder}
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import models.User
import org.apache.commons.codec.binary.Hex
import play.api.mvc.Results._
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.Router._
import play.api.http.HttpVerbs._
import play.api.mvc._
import play.api.routing.SimpleRouter

import scala.runtime.AbstractPartialFunction

/**
  * Manages authentication to Sponge services.
  */
class SSOConsumer {

  var secret: String = _

  val CharEncoding = "UTF-8"
  val Algo = "HmacSHA256"
  val Random = new SecureRandom

  val ConsumeSSO = "/test/sso_consume"
  val Router = new Router

  /**
    * Returns the URL with a generated SSO payload to the SSO instance.
    *
    * @return URL to SSO
    */
  def getQuery(badSig: Boolean = false): String = {
    val payload = "return_sso_url=" + ConsumeSSO + "&nonce=" + nonce
    val encoded = new String(Base64.getEncoder.encode(payload.getBytes(this.CharEncoding)))
    val urlEncoded = URLEncoder.encode(encoded, this.CharEncoding)
    val hmac = if (!badSig) hmac_sha256(encoded.getBytes(this.CharEncoding)) else "invalid_signature"
    "?sso=" + urlEncoded + "&sig=" + hmac
  }

  /**
    * Validates an incoming payload and extracts user information. The
    * incoming payload indicates that the User was authenticated successfully
    * off-site.
    *
    * @param payload  Incoming SSO payload
    * @param sig      Incoming SSO signature
    */
  def authenticate(payload: String, sig: String): Option[User] = {
    if (!hmac_sha256(payload.getBytes(this.CharEncoding)).equals(sig))
      return None

    // decode payload
    val decoded = URLDecoder.decode(new String(Base64.getMimeDecoder.decode(payload)), this.CharEncoding)

    // extract info
    val params = decoded.split('&')
    var externalId: Int = -1
    var username: String = null
    var email: String = null

    for (param <- params) {
      val data = param.split('=')
      val value = if (data.length > 1) data(1) else null
      data(0) match {
        case "external_id" => externalId = Integer.parseInt(value)
        case "username" => username = value
        case "email" => email = value
        case _ =>
      }
    }

    if (externalId == -1 || username == null || email == null)
      return None

    Some(User(id = Some(externalId), email = email, username = username, password = null))
  }

  protected def nonce: String = new BigInteger(130, Random).toString(32)

  private def hmac_sha256(data: Array[Byte]): String = {
    val hmac = Mac.getInstance(this.Algo)
    val keySpec = new SecretKeySpec(this.secret.getBytes(this.CharEncoding), this.Algo)
    hmac.init(keySpec)
    Hex.encodeHexString(hmac.doFinal(data))
  }

  class Router extends SimpleRouter {

    def routes: Routes = new AbstractPartialFunction[RequestHeader, Handler] {
      def isDefinedAt(x: RequestHeader): Boolean = x.path.equals(ConsumeSSO)
      override def applyOrElse[A <: RequestHeader, B >: Handler](rh: A, default: A => B) = {
        (rh.method, rh.path) match {
          case (GET, ConsumeSSO) => consumeSSO(rh.getQueryString("sso"), rh.getQueryString("sig"))
          case _ => default(rh)
        }
      }
    }

    def consumeSSO(sso: Option[String], sig: Option[String]) = Action {
      if (sso.isEmpty || sig.isEmpty)
        BadRequest
      else {
        SSOConsumer.this.authenticate(sso.get, sig.get) match {
          case None =>
            BadRequest
          case Some(user) =>
            if (!user.username.equals(FakeUser.username) || !user.email.equalsIgnoreCase(FakeUser.email))
              BadRequest
            else
              Ok
        }
      }
    }
  }

}
