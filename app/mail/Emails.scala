package mail

import javax.inject.Inject

import models.EmailConfirmation
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Request
import sso.SSOConfig

/**
  * Helper class for [[Email]] composition.
  */
final class Emails @Inject()(implicit override val messagesApi: MessagesApi, config: SSOConfig) extends I18nSupport {

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

}
