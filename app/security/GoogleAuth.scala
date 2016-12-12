package security

import java.util.Collections
import javax.inject.Inject

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.repackaged.com.google.common.base.Preconditions.checkNotNull
import db.UserDAO
import models.User

import scala.util.Try

/**
  * Validates incoming Google Sign-in ID tokens
  */
final class GoogleAuth @Inject()(users: UserDAO) {

  val Logger = play.api.Logger("GoogleAuth")

  val Verifier =  new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new JacksonFactory)
    .setAudience(Collections.singletonList("271228207405-6nd06eoe03d0betqqhffk3d50ufj3rl6.apps.googleusercontent.com"))
    .build()

  /**
    * Verifies the specified token string against the submitted email.
    *
    * @param tokenString    Token to verify
    * @param supposedEmail  Email to verify against
    * @return               True if verified
    */
  def verifyIdToken(tokenString: String, supposedEmail: String): Option[String] = {
    val payload = verifyIdToken(tokenString)
    Logger.info(s"Email : " + supposedEmail)
    val subject = payload.flatMap { payload =>
      if (!payload.getEmail.equals(supposedEmail))
        None
      else
        Some(payload.getSubject)
    }

    if (subject.isDefined)
      Logger.info(s"[Success] Subject ${subject.get} verified!")
    else
      Logger.info("[Failure] Could not verify payload.")

    subject
  }

  def authenticate(tokenString: String): Option[User] = {
    Logger.info("Authenticating user")
    val payload = verifyIdToken(tokenString)
    val user = payload.map(_.getSubject).flatMap(this.users.withGoogleId)
    if (user.isDefined)
      Logger.info(s"[Success] User ${user.get.username} authenticated!")
    else
      Logger.info(s"[Failure] Could not authenticate any user from the given payload.")
    user
  }

  private def verifyIdToken(tokenString: String): Option[Payload] = {
    checkNotNull(tokenString, "null token", "")
    Logger.info("Verifying Google sign-in")
    Logger.info(s"Token : $tokenString")
    Option(Try(Verifier.verify(tokenString)).toOption.orNull).map(_.getPayload)
  }

}
