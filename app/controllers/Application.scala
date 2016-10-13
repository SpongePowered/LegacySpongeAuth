package controllers

import javax.inject.Inject

import db.UserDBO
import form.SSOForms
import models.User
import play.api.cache.CacheApi
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import sso.{SSOConfig, SingleSignOn}

class Application @Inject()(override val messagesApi: MessagesApi,
                            val forms: SSOForms,
                            val users: UserDBO,
                            implicit val cache: CacheApi,
                            implicit val config: SSOConfig) extends Controller with I18nSupport {

  val ssoSecret = this.config.sso.getString("secret").get

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
    val signOn = SingleSignOn.parse(this.ssoSecret, sso, sig)
    this.users.current match {
      case None =>
        // User not logged in, cache SSO request
        signOn.foreach { so => if (so.validateSignature())
          result = result.withSession("sso" -> so.cache().id)
        }
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
      val signOn = SingleSignOn.bindFromRequest()
      this.forms.LogIn.bindFromRequest().fold(
        hasErrors => {
          val firstError = hasErrors.errors.head
          Redirect(routes.Application.showLogIn(signOn.map(_.payload), signOn.map(_.sig)))
            .flashing("error" -> (firstError.message + '.' + firstError.key))
        },
        formData => {
          this.users.verify(formData.username, formData.password) match {
            case None =>
              Redirect(routes.Application.showLogIn(signOn.map(_.payload), signOn.map(_.sig)))
                .flashing("error" -> "error.verify.user")
            case Some(user) =>
              signOn match {
                case None =>
                  Ok(views.html.home(user)).withSession(Security.username -> user.username)
                case Some(so) =>
                  Redirect(so.getRedirect(user))
              }
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
    Redirect(routes.Application.showLogIn(None, None)).withNewSession
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
    var result = Ok(views.html.signUp(sso, sig))
    val signOn = SingleSignOn.parse(this.ssoSecret, sso, sig) // Parse the SSO request if any
    this.users.current match {
      case None =>
        signOn.foreach { so => if (so.validateSignature()) // Validate the SSO payload and cache it
          result = result.withSession("sso" -> so.cache().id)
        }
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
      val signOn = SingleSignOn.bindFromRequest() // Retrieve a cached SSO request
      this.forms.SignUp.bindFromRequest.fold(
        hasErrors => {
          // User error
          val firstError = hasErrors.errors.head
          Redirect(routes.Application.showSignUp(signOn.map(_.payload), signOn.map(_.sig)))
            .flashing("error" -> (firstError.message + '.' + firstError.key))
        },
        formData => {
          val user = this.users.createUser(formData)
          signOn match {
            case None =>
              Ok(views.html.home(user))
            case Some(so) =>
              // Return to original origin
              Redirect(so.getRedirect(user))
          }
        }
      )
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
    SingleSignOn.parse(this.ssoSecret, sso, sig) match {
      case None =>
        BadRequest
      case Some(so) =>
        if (!so.validateSignature())
          BadRequest
        else
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
            Redirect(routes.Application.showVerification(Some(so.payload), Some(so.sig)))
              .flashing("error" -> (firstError.message + '.' + firstError.key))
          },
          formData => {
            this.users.verify(formData.username, formData.password) match {
              case None =>
                Redirect(routes.Application.showVerification(Some(so.payload), Some(so.sig)))
                  .flashing("error" -> "error.verify.user")
              case Some(user) =>
                Redirect(so.getRedirect(user))
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

  private def onSessionFound(user: User, sso: Option[SingleSignOn]) = {
    sso match {
      case None =>
        Ok(views.html.home(user))
      case Some(so) =>
        Redirect(so.getRedirect(user))
    }
  }

}
