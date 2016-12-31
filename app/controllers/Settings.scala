package controllers

import javax.inject.Inject

import api.SpongeAuthWrites
import db.UserDAO
import external.GravatarApi
import form.SpongeAuthForms
import org.apache.commons.io.FileUtils
import org.spongepowered.play.security.SingleSignOnConsumer
import play.api.cache.CacheApi
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json.toJson
import play.api.mvc.{Action, Controller}
import security.SpongeAuthConfig

final class Settings @Inject()(override val messagesApi: MessagesApi,
                               override val users: UserDAO,
                               implicit override val config: SpongeAuthConfig,
                               override val ssoConsumer: SingleSignOnConsumer,
                               override val cache: CacheApi,
                               forms: SpongeAuthForms,
                               gravatar: GravatarApi)
                               extends Controller with Actions with I18nSupport with SpongeAuthWrites {

  /**
    * Displays the user account settings page.
    *
    * @return User account settings
    */
  def showSettings() = Authenticated { implicit request =>
    Ok(views.html.settings(request.user))
  }

  /**
    * Submits changes to a user's settings.
    *
    * @return Redirect to settings page
    */
  def saveSettings() = Authenticated { implicit request =>
    this.forms.SaveSettings.bindFromRequest().fold(
      hasErrors =>
        FormError(routes.Settings.showSettings(), hasErrors),
      formData => {
        val user = request.user
        formData.check(user).map { error =>
          Redirect(routes.Settings.showSettings()).withError(error)
        } getOrElse {
          this.users.saveSettings(user, formData)
          Redirect(routes.Settings.showSettings())
        }
      }
    )
  }

  /**
    * Submits a avatar file update for the currently authenticated user.
    *
    * @return BadRequest if no file, Ok if successful
    */
  def updateAvatar() = Authenticated { implicit request =>
    var user = request.user
    this.forms.UpdateAvatar.bindFromRequest().get match {
      case "file" => request.body.asMultipartFormData.get.file("avatar-file") match {
        case None =>
          BadRequest
        case Some(file) =>
          user = this.users.setAvatar(user, file.filename, file.ref.file)
          Ok(toJson(user))
      }
      case "gravatar" =>
        val email = user.email
        if (this.gravatar.exists(email))
          user = this.users.setAvatar(user, this.gravatar.get(email))
        Ok(toJson(user))
    }
  }

  /**
    * Resets the currently authenticated user's avatar to the default value.
    *
    * @return JSON user
    */
  def resetAvatar() = Authenticated { implicit request =>
    Ok(toJson(this.users.resetAvatar(request.user)))
  }

  /**
    * Displays the specified user's file avatar if any, NotFound otherwise.
    *
    * @param username Username to get avatar of
    * @return         Avatar if found
    */
  def showAvatar(username: String) = (Action andThen WithUser(username)) { implicit request =>
    this.users.getAvatarPath(request.user) match {
      case None =>
        NotFound
      case Some(path) =>
        Ok(FileUtils.readFileToByteArray(path.toFile)).as("image/jpeg")
    }
  }

}
