import com.google.inject.AbstractModule
import db.{UserDBO, UserDBOImpl}
import mail.{Mailer, MailerImpl}

/**
  * Base Module for Sponge SSO
  */
class Module extends AbstractModule {

  def configure() = {
    bind(classOf[UserDBO]).to(classOf[UserDBOImpl])
    bind(classOf[Mailer]).to(classOf[MailerImpl])
  }

}
