package form

import javax.inject.Inject

import play.api.data.Form
import play.api.data.Forms._
import sso.SSOConfig

class SSOForms @Inject()(config: SSOConfig) {

  private val username = nonEmptyText(
    minLength = this.config.sso.getInt("username.minLen").get,
    maxLength = this.config.sso.getInt("username.maxLen").get
  )

  private val password = nonEmptyText(
    minLength = this.config.sso.getInt("password.minLen").get,
    maxLength = this.config.sso.getInt("password.maxLen").get
  )

  lazy val LogIn = Form(mapping(
    "username" -> username,
    "password" -> password
  )(LogInForm.apply)(LogInForm.unapply))

  lazy val SignUp = Form(mapping(
    "email" -> email,
    "username" -> username,
    "password" -> password,
    "mc-username" -> optional(nonEmptyText),
    "irc-nick" -> optional(nonEmptyText),
    "gh-username" -> optional(nonEmptyText)
  )(SignUpForm.apply)(SignUpForm.unapply))

}
