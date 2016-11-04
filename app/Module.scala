import com.google.inject.AbstractModule
import db.{UserDBO, UserDBOImpl}
import external._
import security.pwd.{PasswordFactory, PasswordFactoryImpl}
import security.totp.qr.{QrCodeRenderer, QrCodeRendererImpl}
import security.totp.{TotpAuth, TotpAuthImpl}

/**
  * Base Module for Sponge SSO
  */
class Module extends AbstractModule {

  def configure() = {
    bind(classOf[UserDBO]).to(classOf[UserDBOImpl])
    bind(classOf[TotpAuth]).to(classOf[TotpAuthImpl])
    bind(classOf[PasswordFactory]).to(classOf[PasswordFactoryImpl])
    bind(classOf[QrCodeRenderer]).to(classOf[QrCodeRendererImpl])
    bind(classOf[MojangApi]).to(classOf[MojangApiImpl])
    bind(classOf[GitHubApi]).to(classOf[GitHubApiImpl])
  }

}
