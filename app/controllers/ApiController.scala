package controllers

import javax.inject.Inject

import api.SpongeAuthWrites
import db.UserDBO
import form.SpongeAuthForms
import mail.Emails
import org.spongepowered.play.mail.Mailer
import play.api.cache.CacheApi
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json._
import play.api.mvc.{Action, Controller}
import security.SpongeAuthConfig

/**
  * Handles all API endpoints in the applicaiton.
  */
final class ApiController @Inject()(forms: SpongeAuthForms,
                                    emails: Emails,
                                    mailer: Mailer,
                                    override implicit val users: UserDBO,
                                    override val config: SpongeAuthConfig,
                                    override val cache: CacheApi,
                                    override val messagesApi: MessagesApi)
                                    extends Controller with Actions with I18nSupport with SpongeAuthWrites {

  /**
    * Creates a new user with the submitted data.
    *
    * @return New user represented as JSON
    */
  def createUser() = Action { implicit request =>
    this.forms.Api.CreateUser.bindFromRequest().fold(
      hasErrors => {
        val firstError = hasErrors.errors.head
        Ok(obj("error" -> this.messagesApi(firstError.message + '.' + firstError.key)))
      },
      formData => {
        val verified = formData.isVerified
        val user = this.users.createUser(formData, verified = verified, dummy = formData.isDummy)
        if (!verified) {
          val confirmation = this.users.createEmailConfirmation(user)
          this.mailer.push(this.emails.confirmation(confirmation))
        }
        Ok(toJson(user))
      }
    )
  }

  /**
    * Returns the user with the specified name.
    *
    * @param username Username to lookup
    * @param apiKey   API key
    * @return         User if exists
    */
  def getUser(username: String, apiKey: String) = (ApiAction(apiKey) andThen WithUser(username)) { implicit request =>
    Ok(toJson(request.user))
  }

  /**
    * Deletes the user with the specified username.
    *
    * @param username Username of user to delete
    * @param apiKey   API key
    * @return         Deleted user if successful
    */
  def deleteUser(username: String, apiKey: String) = {
    (ApiAction(apiKey) andThen WithUser(username)) { implicit request =>
      Ok(toJson(this.users.deleteUser(request.user)))
    }
  }

}
