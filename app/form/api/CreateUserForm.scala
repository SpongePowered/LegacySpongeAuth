package form.api

import form.TSignUpForm

case class CreateUserForm(override val email: String,
                          override val username: String,
                          pwd: String,
                          override val mcUsername: Option[String],
                          override val ircNick: Option[String],
                          override val ghUsername: Option[String],
                          isVerified: Boolean,
                          isDummy: Boolean,
                          apiKey: String) extends TSignUpForm {

  override val password = Some(this.pwd)

}
