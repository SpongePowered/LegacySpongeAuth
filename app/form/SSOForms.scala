package form

import javax.inject.Inject

import db.UserDBO
import play.api.data.Form
import play.api.data.Forms._
import security.sso.SSOConfig

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
    "2fa" -> optional(boolean),
    "mc-username" -> optional(nonEmptyText).unique(_.mcUsername),
    "irc-nick" -> optional(nonEmptyText).unique(_.ircNick),
    "gh-username" -> optional(nonEmptyText).unique(_.ghUsername)
  )(SignUpForm.apply)(SignUpForm.unapply))

  /**
    * The form submitted to resend a confirmation email.
    */
  lazy val ResendConfirmationEmail = Form(single("email" -> email))

  /**
    * The form submitted to verify a user with 2FA using a Time-based one
    * time password.
    */
  lazy val VerifyTotp = Form(single(
    "totp" -> number.verifying("error.digits", _.toString.length == this.config.totp.getInt("digits").get)
  ))

  /**
    * The form submitted to send an email where a user can reset their
    * password.
    */
  lazy val SendPasswordReset = Form(single("username" -> username))

  /**
    * The form submitted to reset a user's password.
    */
  lazy val ResetPassword = Form(single("password" -> password))

}
