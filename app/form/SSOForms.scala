package form

import javax.inject.Inject

import db.UserDBO
import play.api.data.Form
import play.api.data.Forms._
import sso.SSOConfig

/**
  * A collection of forms used by Sponge SSO.
  */
final class SSOForms @Inject()(override val config: SSOConfig, override val users: UserDBO) extends Constraints {

  /**
    * The form submitted upon log in.
    */
  lazy val LogIn = Form(mapping(
    "username" -> username,
    "password" -> password
  )(LogInForm.apply)(LogInForm.unapply))

  /**
    * The form submitted upon sign up.
    */
  lazy val SignUp = Form(mapping(
    "email" -> email.unique(_.email),
    "username" -> username.unique(_.username),
    "password" -> password,
    "mc-username" -> optional(nonEmptyText).unique(_.mcUsername),
    "irc-nick" -> optional(nonEmptyText).unique(_.ircNick),
    "gh-username" -> optional(nonEmptyText).unique(_.ghUsername)
  )(SignUpForm.apply)(SignUpForm.unapply))

}
