package security.sso

import java.net.{URLDecoder, URLEncoder}
import java.util.{Base64, UUID}

import models.User
import play.api.Logger
import play.api.cache.CacheApi
import play.api.mvc.{Cookie, Request, Result, Session}
import security.CryptoUtils.hmac_sha256

import scala.concurrent.duration.Duration

/**
  * Represents an incoming request for single-sign-on authentication. This
  * object can potentially survive across multiple requests and ultimately
  * achieves the following:
  *
  * 1. Ensures that the incoming signature is valid and matches our secret
  *    key
  *
  * 2. After a [[User]] has been authenticated whether it be a new User
  *    through a sign up or an old one through a log in, generates a payload to
  *    send back to the origin.
  *
  * 3. Constructs a URL to redirect a request to using our constructed
  *    payload, indicating that authentication was successful.
  *
  * This instance is kept alive through multiple requests by caching it using
  * a UUID that is also given to the Session for later retrieval.
  *
  * @param secret         SSO secret key
  * @param maxAge         The maximum age of this object once it has been cached
  * @param payload        Incoming payload
  * @param sig            Incoming signature
  * @param id             Unique ID paired to the Session
  * @param ignoreSession  Whether the client's session information should be
  *                       ignored. This is used for verifying already
  *                       authenticated users.
  * @param session  The Session of the request
  * @param cacheApi CacheApi instance
  */
class SingleSignOn private (secret: String,
                            val maxAge: Duration,
                            val payload: String,
                            val sig: String,
                            var id: String = null,
                            var ignoreSession: Boolean = false)
                           (implicit session: Session,
                            cacheApi: CacheApi) {

  private val CharEncoding = "UTF-8"

  /**
    * Validates the incoming signature.
    *
    * @return True if validated
    */
  def validateSignature(): Boolean = {
    val params = decodePayload().split('&')
    if (params.exists(_.equals("validate=true")))
      this.ignoreSession = true
    hmac_sha256(this.secret, payload.getBytes(CharEncoding)).equals(sig)
  }

  /**
    * Generates a new payload and builds a URL to return to the original
    * sender. This indicates that the [[User]] was successfully authenticated.
    *
    * @param user User that was authenticated
    * @return     URL String
    */
  def getRedirect(user: User): String = {
    val decodedPayload = decodePayload()
    val params = decodedPayload.split('&')

    var returnUrl = params.find(_.startsWith("return_sso_url="))
      .getOrElse(throw new RuntimeException("sso payload missing return url"))
    returnUrl = returnUrl.substring(returnUrl.indexOf('=') + 1)

    var nonce = params.find(_.startsWith("nonce=")).getOrElse(throw new RuntimeException("sso payload missing nonce"))
    nonce = nonce.substring(nonce.indexOf('=') + 1)

    var newPayload = s"nonce=$nonce" +
      s"&email=${user.email}" +
      s"&external_id=${user.id.get}" +
      s"&username=${user.username}"

    user.mcUsername.foreach(mcUsername => newPayload += s"&custom.user_field_1=$mcUsername")
    user.ghUsername.foreach(ghUsername => newPayload += s"&custom.user_field_2=$ghUsername")
    user.ircNick.foreach(ircNick => newPayload += s"&custom.user_field_3=$ircNick")

    newPayload = new String(Base64.getEncoder.encode(newPayload.getBytes(this.CharEncoding)))
    val urlEncodedPayload = URLEncoder.encode(newPayload, this.CharEncoding)
    val newSig = hmac_sha256(this.secret, newPayload.getBytes(this.CharEncoding))

    s"$returnUrl?sso=$urlEncodedPayload&sig=$newSig"
  }

  private def decodePayload(): String
  = URLDecoder.decode(new String(Base64.getMimeDecoder.decode(this.payload)), this.CharEncoding)

  /**
    * Generates a new ID for this instance and caches it. The newly generated
    * ID should be added to the session for later retrieval.
    *
    * @return This instance
    */
  def cache(): SingleSignOn = {
    this.id = UUID.randomUUID().toString
    this.cacheApi.set(id, this, this.maxAge)
    Logger.info("Cached SSO request with value: " + this)
    this
  }

  override def toString = {
    "SingleSignOn {\n" +
      s"\tID: $id\n" +
      s"\tPayload: $payload\n" +
      s"\tSignature: $sig\n" +
      "}"
  }

}

object SingleSignOn {

  /**
    * Parses an incoming SSO request.
    *
    * @param secret   SSO secret
    * @param maxAge   The maximum age of the SSO once cached
    * @param sso      Incoming payload
    * @param sig      Incoming signature
    * @param session  Session of request
    * @param cache    CacheApi instance
    * @return         SSO instance if was a SSO request
    */
  def parseValidateAndCache(secret: String,
                            maxAge: Duration,
                            sso: Option[String],
                            sig: Option[String])
                           (implicit session: Session, cache: CacheApi): Option[SingleSignOn] = {
    sso.flatMap(payload => sig.flatMap(sig => {
      val signOn = new SingleSignOn(secret, maxAge, payload, sig)
      if (signOn.validateSignature()) {
        signOn.cache()
        Logger.info("Parsed SSO request of value: " + signOn)
        Some(signOn)
      } else {
        Logger.warn("Could not verify authenticity of SSO payload: " + signOn)
        None
      }
    }))
  }

  def addToResult(result: Result, sso: Option[SingleSignOn]): Result
  = sso.map(s => result.withCookies(Cookie("_sso", s.id, Some(s.maxAge.toSeconds.toInt)))).getOrElse(result)

  /**
    * Retrieves a cached [[SingleSignOn]] for the Session of the request, if
    * any.
    *
    * @param request  Incoming request
    * @param cache    CacheApi instance
    * @return         SSO instance, if any
    */
  def bindFromRequest()(implicit request: Request[_], cache: CacheApi): Option[SingleSignOn] = {
    request.cookies.get("_sso").flatMap { token =>
      val so = cache.get[SingleSignOn](token.value)
      if (so.isDefined)
        Logger.info("Retrieved SSO request from cache of value: " + so.get)
      so
    }
  }

}
