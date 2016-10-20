package controllers

import javax.inject.Inject

import com.google.common.base.Preconditions._
import controllers.routes.{Application, TwoFactorAuth}
import db.UserDBO
import form.SSOForms
import mail.{Emails, Mailer}
import play.api.cache.CacheApi
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import security.sso.{SSOConfig, SingleSignOn}
import security.totp.TotpAuth
import security.totp.qr.QrCodeRenderer

import scala.concurrent.duration._

/**
  * Main entry point for Sponge SSO.
  */
final class Application @Inject()(override val messagesApi: MessagesApi,
                                  forms: SSOForms,
                                  mailer: Mailer,
                                  emails: Emails,
                                  totp: TotpAuth,
                                  qrRenderer: QrCodeRenderer,
                                  implicit override val users: UserDBO,
                                  implicit val cache: CacheApi,
                                  implicit val config: SSOConfig) extends Controller with I18nSupport with Actions {

  private val ssoSecret = this.config.sso.getString("secret").get
  private val ssoMaxAge = this.config.sso.getLong("maxAge").get.millis

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
  def showSignUp(sso: Option[String], sig: Option[String]) = NotAuthenticated { implicit request =>
    var result = Ok(views.html.signup.view(sso, sig))
    // Parse and cache any incoming SSO request
    val signOn = SingleSignOn.parseValidateAndCache(this.ssoSecret, this.ssoMaxAge, sso, sig)
    // If the user is already logged in, redirect home
    this.users.current.foreach(user => result = Redirect(Application.showHome()))
    // Return result with SSO request reference (if any)
    SingleSignOn.addToResult(result, signOn)
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
  def showLogIn(sso: Option[String], sig: Option[String]) = NotAuthenticated { implicit request =>
    var result = Ok(views.html.logIn())
    // Parse and cache any incoming SSO request
    val signOn = SingleSignOn.parseValidateAndCache(this.ssoSecret, this.ssoMaxAge, sso, sig)
    // If the user is already logged in, redirect home
    this.users.current.foreach(user => result = Redirect(Application.showHome()))
    // Return result with SSO reference (if any)
    SingleSignOn.addToResult(result, signOn)
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
            user.totpSecret match {
              case None =>
                // Nope
                Redirect(Application.showHome()).authenticatedAs(user)
              case Some(encSecret) =>
                // Yup, have them verify
                Redirect(TwoFactorAuth.showVerification()).remembering(user)
            }
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
  def showVerification(sso: Option[String], sig: Option[String]) = Action { implicit request =>
    SingleSignOn.parseValidateAndCache(this.ssoSecret, this.ssoMaxAge, sso, sig) match {
      case None =>
        // SSO request required
        BadRequest
      case Some(so) =>
        // Show form with SSO reference
        SingleSignOn.addToResult(Ok(views.html.verify(sso, sig)), Some(so))
    }
  }

  /**
    * Attempts to verify the authenticated user with the submitted credentials.
    *
    * @return BadRequest if no SSO request is present, redirect to SSO origin
    *         otherwise
    */
  def verify() = Action { implicit request =>
    SingleSignOn.bindFromRequest() match {
      case None =>
        // SSO required
        BadRequest
      case Some(so) =>
        // Validate log in form
        this.forms.LogIn.bindFromRequest().fold(
          hasErrors => {
            // User error
            val firstError = hasErrors.errors.head
            val call = Application.showVerification(Some(so.payload), Some(so.sig))
            Redirect(call).flashing("error" -> (firstError.message + '.' + firstError.key))
          },
          formData => {
            // Verify username and password
            this.users.verify(formData.username, formData.password) match {
              case None =>
                // Verification failed
                val call = Application.showVerification(Some(so.payload), Some(so.sig))
                Redirect(call).flashing("error" -> "error.verify.user")
              case Some(user) =>
                // Redirect directly back to SSO origin
                Redirect(so.getRedirect(user)).discardingCookies(DiscardingCookie("_sso"))
            }
          }
        )
    }
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
