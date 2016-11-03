package mail

import javax.inject.Inject

import models.{EmailConfirmation, PasswordReset}
import org.spongepowered.play.mail.Email
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Request
import security.SpongeAuthConfig

/**
  * Helper class for [[Email]] composition.
  */
final class Emails @Inject()(implicit override val messagesApi: MessagesApi, config: SpongeAuthConfig) extends I18nSupport {

  /**
    * Composes a new confirmation email.
    *
    * @param model    [[EmailConfirmation]] model
    * @param request  Request context
    * @return         New confirmation email
    */
  def confirmation(model: EmailConfirmation)(implicit request: Request[_]) = Email(
    recipient = model.email,
    subject = this.messagesApi("email.confirm.subject"),
    content = views.html.emails.confirm(model)
  )

  /**
    * Composes a new "reset password" email.
    *
    * @param model    [[PasswordReset]] model
    * @param request  Request context
    * @return         New email
    */
  def resetPassword(model: PasswordReset)(implicit request: Request[_]) = Email(
    recipient = model.email,
    subject = this.messagesApi("email.resetPassword.subject"),
    content = views.html.emails.resetPassword(model)
  )

}
