package form

case class SignUpForm(email: String,
                      username: String,
                      password: String,
                      private val _setup2fa: Option[Boolean],
                      mcUsername: Option[String],
                      ircNick: Option[String],
                      ghUsername: Option[String]) {

  val setup2fa: Boolean = this._setup2fa.getOrElse(false)

}
