package form

case class SignUpForm(override val email: String,
                      override val username: String,
                      override val password: String,
                      private val _setup2fa: Option[Boolean],
                      override val mcUsername: Option[String],
                      override val ircNick: Option[String],
                      override val ghUsername: Option[String]) extends TSignUpForm {

  val setup2fa: Boolean = this._setup2fa.getOrElse(false)

}
