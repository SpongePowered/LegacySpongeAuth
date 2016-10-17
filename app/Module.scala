import com.google.inject.AbstractModule
import db.{UserDBO, UserDBOImpl}
import mail.{Mailer, MailerImpl}
import security.totp.qr.{QrCodeRenderer, QrCodeRendererImpl}
import security.totp.{TotpAuth, TotpAuthImpl}

/**
  * Base Module for Sponge SSO
  */
class Module extends AbstractModule {

  def configure() = {
    bind(classOf[UserDBO]).to(classOf[UserDBOImpl])
    bind(classOf[Mailer]).to(classOf[MailerImpl])
    bind(classOf[TotpAuth]).to(classOf[TotpAuthImpl])
    bind(classOf[QrCodeRenderer]).to(classOf[QrCodeRendererImpl])
  }

}
