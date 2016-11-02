package controllers

import javax.inject.Inject

import controllers.routes.{Application, TwoFactorAuth}
import db.UserDBO
import external.StatusZ
import form.SpongeAuthForms
import mail.{Emails, Mailer}
import play.api.cache.CacheApi
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import security.SpongeAuthConfig
import security.SingleSignOn
import security.totp.TotpAuth
import security.totp.qr.QrCodeRenderer

/**
  * Main entry point for Sponge SSO.
  */
final class Application @Inject()(override val messagesApi: MessagesApi,
                                  forms: SpongeAuthForms,
                                  mailer: Mailer,
                                  emails: Emails,
                                  totp: TotpAuth,
                                  qrRenderer: QrCodeRenderer,
                                  status: StatusZ,
                                  implicit override val users: UserDBO,
                                  implicit override val cache: CacheApi,
                                  implicit override val config: SpongeAuthConfig)
                                  extends Controller with I18nSupport with Actions {

  /**
    * Displays the sign up form. If there is an incoming SSO payload it will
    * be validated and cached for use once the user is authenticated. If the
    * user is already authenticated they will be redirected home.
    *
    * @param sso  Incoming SSO payload
    * @param sig  Incoming SSO signature
    * @return     Sign up form if unauthenticated, home page if authenticated and
    *             has no SSO request, or redirect to SSO origin if authenticated
    *             and has SSO request.
    */
  def showSignUp(sso: Option[String], sig: Option[String]) = SSORedirect(sso, sig) { implicit request =>
    // Parse and cache any incoming SSO request
    val signOn = SingleSignOn.parseValidateAndCache(this.ssoSecret, this.ssoMaxAge, sso, sig)
    // Return result with SSO request reference (if any)
    SingleSignOn.addToResult(Ok(views.html.signup.view(sso, sig)), signOn)
  }

  /**
    * Attempts to create a new user from the submitted data. The user must not
    * be already authenticated. If successful, a confirmation email will be
    * sent and the user will be redirected to one of two places:
    *
    *   1. The user will be redirected to the 2-factor authenticated setup page
    *      where they will verify their account with a TOTP. They will be
    *      given a cookie that points to a DB stored session marked as
    *      unauthenticated.
    *
    *   2. The user will be redirected home. They will be given a cookie that
    *      points to a DB stored session marked as authenticated.
    *
    * @return BadRequest if user is logged in already, redirect to sign up
    *         form with errors if any, redirect to home page if successful and
    *         has no SSO request, or redirect to SSO origin if successful and
    *         has SSO request.
    */
  def signUp() = NotAuthenticated { implicit request =>
    this.forms.SignUp.bindFromRequest.fold(
      hasErrors =>
        FormError(Application.showSignUp(None, None), hasErrors),
      formData => {
        // Create the user and send confirmation email
        val confirmation = this.users.createUser(formData)
        val user = confirmation.user
        this.mailer.push(this.emails.confirmation(confirmation))
        if (formData.setup2fa)
          Redirect(TwoFactorAuth.showSetup()).remembering(user)
        else
          Redirect(Application.showHome()).authenticatedAs(user)
      }
    )
  }

  /**
    * Displays the log in page. If there is an incoming SSO payload it will be
    * validated and cached for use once the user is authenticated. If the user
    * is already authenticated they will be redirected home.
    *
    * @param sso Incoming SSO payload
    * @param sig Incoming SSO signature
    * @return Log in page if unauthenticated, home page if authenticated and
    *         no SSO request, and redirect to SSO origin if authenticated and
    *         has SSO request
    */
  def showLogIn(sso: Option[String], sig: Option[String]) = SSORedirect(sso, sig) { implicit request =>
    // Parse and cache any incoming SSO request
    val signOn = SingleSignOn.parseValidateAndCache(this.ssoSecret, this.ssoMaxAge, sso, sig)
    // Return result with SSO reference (if any)
    SingleSignOn.addToResult(Ok(views.html.login.form()), signOn)
  }

  /**
    * Attempts to log the submitted user in. The user must not be already
    * authenticated.
    *
    * @return BadRequest if user is logged in already, log in form with errors
    *         if has errors, home page if successful and has no SSO request,
    *         or redirect to the SSO request origin if successful and has an
    *         SSO request.
    */
  def logIn() = NotAuthenticated { implicit request =>
    // Process form data
    val errorRedirect = Application.showLogIn(None, None)
    this.forms.LogIn.bindFromRequest().fold(
      hasErrors =>
        FormError(errorRedirect, hasErrors),
      formData => {
        // Form data validated, verify the username and password
        this.users.verify(formData.username, formData.password) match {
          case None =>
            // Validation failed
            Redirect(errorRedirect).flashing("error" -> "error.verify.user")
          case Some(user) =>
            // Success, check if the user has 2FA enabled
            if (!user.isTotpConfirmed)
              Redirect(Application.showHome()).authenticatedAs(user)
            else
              Redirect(TwoFactorAuth.showVerification()).remembering(user)
        }
      }
    )
  }

  /**
    * Displays the "home" page. A user must be authenticated to view the home page.
    *
    * @return Redirect to log in if not authenticated, home page otherwise
    */
  def showHome() = Action { implicit request =>
    this.users.current match {
      case None =>
        // Redirect to log in page
        Redirect(Application.showLogIn(None, None))
      case Some(user) =>
        // User found
        if (user.isEmailConfirmed) {
          // Look for cached SSO request
          SingleSignOn.bindFromRequest() match {
            case None =>
              // No SSO request, just show them the normal page
              Ok(views.html.home(user, None))
            case Some(sso) =>
              // SSO request exists
              if (sso.ignoreSession) {
                // We've been told to ignore session data, the SSO redirect
                // must go through verify()
                Redirect(Application.showVerification(None, None))
              } else {
                // Complete SSO request
                Ok(views.html.home(user, Some(sso.getRedirect(user)))).discardingCookies(DiscardingCookie("_sso"))
              }
          }
        } else {
          // The user hasn't confirmed their email, instruct them to do so
          this.users.getEmailConfirmation(user.email) match {
            case None =>
              // Send a new confirmation
              val confirmation = this.users.createEmailConfirmation(user)
              this.mailer.push(this.emails.confirmation(confirmation))
              Ok(views.html.signup.confirmEmail(confirmation))
            case Some(confirmation) =>
              // A confirmation is already out
              Ok(views.html.signup.confirmEmail(confirmation))
          }
        }
    }
  }

  /**
    * Marks an email with the specified confirmation token as confirmed.
    *
    * @param token Token of email confirmation
    * @return BadRequest if token is not associated with email or redirect to
    *         home page if successful
    */
  def confirmEmail(token: String) = Action { implicit request =>
    this.users.confirmEmail(token)
    Redirect(Application.showHome())
  }

  /**
    * Deletes any old confirmations associated with the given email and creates
    * and sends a new one.
    *
    * @return Bad request if no user is associated with the given email or Ok
    *         if successful
    */
  def resendConfirmationEmail() = Action { implicit request =>
    val email = this.forms.ResendConfirmationEmail.bindFromRequest().get.trim
    this.users.withEmail(email) match {
      case None =>
        // No user with email exists
        BadRequest
      case Some(user) =>
        // Delete any old confirmations and create and send a new one
        this.users.deleteEmailConfirmation(user.email)
        val newConfirmation = this.users.createEmailConfirmation(user)
        this.mailer.push(this.emails.confirmation(newConfirmation))
        Ok
    }
  }

  /**
    * Displays the page where a user can reset their password.
    *
    * @param token  Verification token if being directed from an email
    * @return       Password reset page
    */
  def showPasswordReset(token: Option[String]) = NotAuthenticated { implicit request =>
    Ok(views.html.login.passwordReset(token))
  }

  /**
    * Sends the submitted username an email with a link to reset their password.
    *
    * @return Password reset page
    */
  def sendPasswordReset() = NotAuthenticated { implicit request =>
    this.forms.SendPasswordReset.bindFromRequest().fold(
      hasErrors =>
        FormError(Application.showPasswordReset(None), hasErrors),
      username => {
        this.users.withName(username) match {
          case None =>
            Redirect(Application.showPasswordReset(None)).withError("error.notFound.username")
          case Some(user) =>
            val email = this.emails.resetPassword(this.users.createPasswordReset(user))
            this.mailer.push(email)
            Redirect(Application.showPasswordReset(None)).flashing("sent" -> "true")
        }
      }
    )
  }

  /**
    * Resets the password of the user with the associated token to the
    * submitted value.
    *
    * @param token  Authenticity token
    * @return       Log in page if successful
    */
  def resetPassword(token: String) = NotAuthenticated { implicit request =>
    this.forms.ResetPassword.bindFromRequest().fold(
      hasErrors =>
        FormError(Application.showPasswordReset(Some(token)), hasErrors),
      newPassword => {
        if (this.users.resetPassword(token, newPassword))
          Redirect(Application.showLogIn(None, None)).withSuccess("success.reset.password")
        else
          Redirect(Application.showPasswordReset(None)).withError("error.expired.password")
      }
    )
  }

  /**
    * Clears the current session.
    *
    * @return Redirection to login form
    */
  def logOut() = Action { implicit request =>
    // Clear the current session, delete auth cookie, and delete the
    // server-side Session
    request.cookies.get("_token").foreach(token => this.users.deleteSession(token.value))
    Redirect(Application.showLogIn(None, None)).withNewSession.discardingCookies(DiscardingCookie("_token"))
  }

  /**
    * Displays verification form for already authenticated Users.
    *
    * @param sso  Incoming SSO payload
    * @param sig  Incoming SSO signature
    * @return     BadRequest if SSO request is missing or has an invalid
    *             signature, verification form otherwise
    */
  def showVerification(sso: Option[String], sig: Option[String]) = Authenticated { implicit request =>
    SingleSignOn.parseValidateAndCache(this.ssoSecret, this.ssoMaxAge, sso, sig) match {
      case None =>
        // SSO request required
        BadRequest
      case Some(so) =>
        // Show form with SSO reference
        SingleSignOn.addToResult(Ok(views.html.login.verify(sso, sig)), Some(so))
    }
  }

  /**
    * Attempts to verify the authenticated user with the submitted credentials.
    *
    * @return BadRequest if no SSO request is present, redirect to SSO origin
    *         otherwise
    */
  def verify() = Authenticated { implicit request =>
    SingleSignOn.bindFromRequest() match {
      case None =>
        // SSO required
        BadRequest
      case Some(so) =>
        // Validate log in form
        val failCall = Application.showVerification(Some(so.payload), Some(so.sig))
        this.forms.LogIn.bindFromRequest().fold(
          hasErrors => {
            // User error
            val firstError = hasErrors.errors.head
            Redirect(failCall).flashing("error" -> (firstError.message + '.' + firstError.key))
          },
          formData => {
            // Verify username and password
            this.users.verify(formData.username, formData.password) match {
              case None =>
                // Verification failed
                Redirect(failCall).flashing("error" -> "error.verify.user")
              case Some(user) =>
                if (user.username.equals(request.user.username)) {
                  // Redirect directly back to SSO origin
                  Redirect(so.getRedirect(user)).discardingCookies(DiscardingCookie("_sso"))
                } else
                  Redirect(failCall).flashing("error" -> "error.verify.user")
            }
          }
        )
    }
  }

  /**
    * Displays status information about the application.
    *
    * @return Status information
    */
  def showStatusZ() = Action {
    Ok(this.status.json)
  }

  /**
    * Removes all users.
    *
    * @return Redirect to sign up form
    */
  def reset() = Action {
    this.config.checkDebug()
    this.users.removeAll()
    Redirect(Application.showSignUp(None, None))
  }

}
