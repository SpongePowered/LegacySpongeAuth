package form

import javax.inject.Inject

import db.UserDBO
import play.api.data.Form
import play.api.data.Forms._
import sso.SSOConfig

final class SSOForms @Inject()(override val config: SSOConfig, override val users: UserDBO) extends Constraints {

  lazy val LogIn = Form(mapping(
    "username" -> username,
    "password" -> password
  )(LogInForm.apply)(LogInForm.unapply))

  lazy val SignUp = Form(mapping(
    "email" -> email.unique(_.email),
    "username" -> username.unique(_.username),
    "password" -> password,
    "mc-username" -> optional(nonEmptyText).unique(_.mcUsername),
    "irc-nick" -> optional(nonEmptyText).unique(_.ircNick),
    "gh-username" -> optional(nonEmptyText).unique(_.ghUsername)
  )(SignUpForm.apply)(SignUpForm.unapply))

}
