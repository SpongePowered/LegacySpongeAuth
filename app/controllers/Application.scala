package controllers

import javax.inject.Inject

import db.UserDBO
import form.SSOForms
import mail.{Emails, Mailer}
import models.User
import play.api.cache.CacheApi
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import sso.{SSOConfig, SingleSignOn}

import scala.concurrent.duration._

/**
  * Main entry point for Sponge SSO.
  */
final class Application @Inject()(override val messagesApi: MessagesApi,
                                  val forms: SSOForms,
                                  val mailer: Mailer,
                                  val emails: Emails,
                                  implicit val users: UserDBO,
                                  implicit val cache: CacheApi,
                                  implicit val config: SSOConfig) extends Controller with I18nSupport {

  private val ssoSecret = this.config.sso.getString("secret").get
  private val ssoMaxAge = this.config.sso.getLong("maxAge").get.millis

  /**
    * Displays the "home" page. A user must be authenticated to view the home page.
    *
    * @param redirect Final destination
    * @return         Redirect to log in if not authenticated, home page otherwise
    */
  def showHome(redirect: Option[String]) = Action { implicit request =>
    this.users.current match {
      case None =>
        Redirect(routes.Application.showLogIn(None, None))
      case Some(user) =>
        if (user.isEmailConfirmed)
          Ok(views.html.home(user, redirect))
        else {
          this.users.getEmailConfirmation(user.email) match {
            case None =>
              val confirmation = this.users.createEmailConfirmation(user)
              this.mailer.push(this.emails.confirmation(confirmation))
              Ok(views.html.signup.confirmEmail(confirmation))
            case Some(confirmation) =>
              Ok(views.html.signup.confirmEmail(confirmation))
          }
        }
    }
  }

  /**
    * Displays the log in page.
    *
    * @param sso Incoming SSO payload
    * @param sig Incoming SSO signature
    * @return Log in page if unauthenticated, home page if authenticated and
    *         no SSO request, and redirect to SSO origin if authenticated and
    *         has SSO request
    */
  def showLogIn(sso: Option[String], sig: Option[String]) = Action { implicit request =>
    var result = Ok(views.html.logIn(sso, sig))
    val signOn = SingleSignOn.parse(this.ssoSecret, this.ssoMaxAge, sso, sig)
    this.users.current match {
      case None =>
        // User not logged in, cache SSO request
        signOn.foreach(so => result = result.withSession("sso" -> so.cache().id))
      case Some(user) =>
        result = onSessionFound(user, signOn)
    }
    result
  }

  /**
    * Attempts to log the submitted user in.
    *
    * @return BadRequest if user is logged in already, log in form with errors
    *         if has errors, home page if successful and has no SSO request,
    *         or redirect to the SSO request origin if successful and has an
    *         SSO request.
    */
  def logIn() = Action { implicit request =>
    if (this.users.current.isDefined)
      BadRequest
    else {
      this.forms.LogIn.bindFromRequest().fold(
        hasErrors => {
          val firstError = hasErrors.errors.head
          val call = routes.Application.showLogIn(None, None)
          Redirect(call).flashing("error" -> (firstError.message + '.' + firstError.key))
        },
        formData => {
          this.users.verify(formData.username, formData.password) match {
            case None =>
              val call = routes.Application.showLogIn(None, None)
              Redirect(call).flashing("error" -> "error.verify.user")
            case Some(user) =>
              val signOn = SingleSignOn.bindFromRequest()
              val cookie = this.users.createSessionCookie(this.users.createSession(user))
              val call = routes.Application.showHome(signOn.map(_.getRedirect(user)))
              Redirect(call).withSession(Security.username -> user.username).withCookies(cookie)
          }
        }
      )
    }
  }

  /**
    * Clears the current session.
    *
    * @return Redirection to login form
    */
  def logOut() = Action { implicit request =>
    request.cookies.get("_token").foreach(token => this.users.deleteSession(token.value))
    Redirect(routes.Application.showLogIn(None, None)).withNewSession.discardingCookies(DiscardingCookie("_token"))
  }

  /**
    * Displays the sign up form.
    *
    * @param sso Incoming SSO payload
    * @param sig Incoming SSO signature
    * @return Sign up form if unauthenticated, home page if authenticated and
    *         has no SSO request, or redirect to SSO origin if authenticated
    *         and has SSO request.
    */
  def showSignUp(sso: Option[String], sig: Option[String]) = Action { implicit request =>
    var result = Ok(views.html.signup.view(sso, sig))
    val signOn = SingleSignOn.parse(this.ssoSecret, this.ssoMaxAge, sso, sig) // Parse the SSO request if any
    this.users.current match {
      case None =>
        signOn.foreach(so => result = result.withSession("sso" -> so.cache().id))
      case Some(user) =>
        result = onSessionFound(user, signOn)
    }
    result
  }

  /**
    * Attempts to create a new user from the submitted data.
    *
    * @return BadRequest if user is logged in already, redirect to sign up
    *         form with errors if any, redirect to home page if successful and
    *         has no SSO request, or redirect to SSO origin if successful and
    *         has SSO request.
    */
  def signUp() = Action { implicit request =>
    if (this.users.current.isDefined)
      BadRequest
    else {
      this.forms.SignUp.bindFromRequest.fold(
        hasErrors => {
          // User error
          val firstError = hasErrors.errors.head
          val call = routes.Application.showSignUp(None, None)
          Redirect(call).flashing("error" -> (firstError.message + '.' + firstError.key))
        },
        formData => {
          // Create the user and send confirmation email
          val confirmation = this.users.createUser(formData)
          this.mailer.push(this.emails.confirmation(confirmation))
          Ok(views.html.signup.confirmEmail(confirmation))
        }
      )
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
    this.users.confirmEmail(token) match {
      case None =>
        BadRequest
      case Some(session) =>
        val sso = SingleSignOn.bindFromRequest()
        val user = session.user
        val call = routes.Application.showHome(sso.map(_.getRedirect(user)))
        val token = this.users.createSessionCookie(session)
        Redirect(call).withSession(Security.username -> user.username).withCookies(token)
    }
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
        BadRequest
      case Some(user) =>
        this.users.deleteEmailConfirmation(user.email)
        val newConfirmation = this.users.createEmailConfirmation(user)
        this.mailer.push(this.emails.confirmation(newConfirmation))
        Ok
    }
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
    SingleSignOn.parse(this.ssoSecret, this.ssoMaxAge, sso, sig) match {
      case None =>
        BadRequest
      case Some(so) =>
        Ok(views.html.verify(sso, sig)).withSession("sso" -> so.cache().id)
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
        BadRequest
      case Some(so) =>
        this.forms.LogIn.bindFromRequest().fold(
          hasErrors => {
            val firstError = hasErrors.errors.head
            val call = routes.Application.showVerification(Some(so.payload), Some(so.sig))
            Redirect(call).flashing("error" -> (firstError.message + '.' + firstError.key))
          },
          formData => {
            this.users.verify(formData.username, formData.password) match {
              case None =>
                val call = routes.Application.showVerification(Some(so.payload), Some(so.sig))
                Redirect(call).flashing("error" -> "error.verify.user")
              case Some(user) =>
                Redirect(routes.Application.showHome(Some(so.getRedirect(user))))
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
    Redirect(routes.Application.showSignUp(None, None))
  }

  private def onSessionFound(user: User, sso: Option[SingleSignOn])
  = Redirect(routes.Application.showHome(sso.map(_.getRedirect(user))))

}
