package form

import security.GoogleAuth

case class SignUpForm(override val email: String,
                      override val username: String,
                      override val password: Option[String],
                      private val _setup2fa: Option[Boolean],
                      override val mcUsername: Option[String],
                      override val ircNick: Option[String],
                      override val ghUsername: Option[String],
                      override val googleIdToken: Option[String])
                     (implicit googleAuth: GoogleAuth) extends TSignUpForm {

  val setup2fa: Boolean = this._setup2fa.getOrElse(false)

  /**
    * Determines if the user submitted a password (X)OR a Google ID token.
    *
    * @return True if auth method is present
    */
  def hasAuthMethod: Boolean = this.googleIdToken.isDefined != this.password.isDefined

  /**
    * Verifies and caches the google ID for a Google ID token.
    *
    * @return True if verified
    */
  def verifyGoogleIdToken(): Boolean = {
    if (this.googleIdToken.isEmpty)
      return true
    this.googleSubject = this.googleAuth.verifyIdToken(this.googleIdToken.get, this.email)
    this.googleSubject.isDefined
  }

}
