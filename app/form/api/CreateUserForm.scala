package form.api

import form.TSignUpForm

case class CreateUserForm(override val email: String,
                          override val username: String,
                          override val password: String,
                          override val mcUsername: Option[String],
                          override val ircNick: Option[String],
                          override val ghUsername: Option[String],
                          private val _isVerified: Option[Boolean],
                          apiKey: String) extends TSignUpForm {

  def isVerified: Boolean = this._isVerified.getOrElse(false)

}
