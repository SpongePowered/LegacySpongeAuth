package sso

import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import models.User
import org.apache.commons.codec.binary.Hex
import play.api.cache.CacheApi
import play.api.mvc.Session

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
  * TODO: Expiration?
  *
  * @param secret   SSO secret key
  * @param payload  Incoming payload
  * @param sig      Incoming signature
  * @param id       Unique ID paired to the Session
  * @param session  The Session of the request
  * @param cacheApi CacheApi instance
  */
class SingleSignOn(secret: String,
                   val payload: String,
                   val sig: String,
                   var id: String = null)
                  (implicit session: Session,
                   cacheApi: CacheApi) {

  val CharEncoding = "UTF-8"
  val Algo = "HmacSHA256"

  /**
    * Validates the incoming signature.
    *
    * @return True if validated
    */
  def validateSignature(): Boolean = {
    false
  }

  /**
    * Generates a new payload and builds a URL to return to the original
    * sender. This indicates that the [[User]] was successfully authenticated.
    *
    * @param user User that was authenticated
    * @return     URL String
    */
  def getRedirect(user: User): String = {
    null
  }

  /**
    * Generates a new ID for this instance and caches it. The newly generated
    * ID should be added to the session for later retrieval.
    *
    * @return This instance
    */
  def cache(): SingleSignOn = {
    this.id = UUID.randomUUID().toString
    this.cacheApi.set(id, this)
    this
  }

  private def hmac_sha256(data: Array[Byte]): String = {
    val hmac = Mac.getInstance(this.Algo)
    val keySpec = new SecretKeySpec(this.secret.getBytes(this.CharEncoding), this.Algo)
    hmac.init(keySpec)
    Hex.encodeHexString(hmac.doFinal(data))
  }

}

object SingleSignOn {

  /**
    * Parses an incoming SSO request.
    *
    * @param secret   SSO secret
    * @param sso      Incoming payload
    * @param sig      Incoming signature
    * @param session  Session of request
    * @param cache    CacheApi instance
    * @return         SSO instance if was a SSO request
    */
  def parse(secret: String,
            sso: Option[String],
            sig: Option[String])
           (implicit session: Session, cache: CacheApi): Option[SingleSignOn]
  = sso.flatMap(payload => sig.map(new SingleSignOn(secret, payload, _)))

  /**
    * Retrieves a cached [[SingleSignOn]] for the Session of the request, if
    * any.
    *
    * @param session  Session of request
    * @param cache    CacheApi instance
    * @return         SSO instance, if any
    */
  def bindFromRequest()(implicit session: Session, cache: CacheApi): Option[SingleSignOn] = {
    session.get("sso").flatMap { id =>
      val so = cache.get[SingleSignOn](id)
      cache.remove(id)
      so
    }
  }

}
