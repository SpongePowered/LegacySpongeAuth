import com.google.inject.AbstractModule
import db.{UserDAO, UserDAOImpl}
import external._
import security.{GoogleAuth, GoogleAuthImpl}
import security.pwd.{PasswordFactory, PasswordFactoryImpl}
import security.totp.qr.{QrCodeRenderer, QrCodeRendererImpl}
import security.totp.{TotpAuth, TotpAuthImpl}

/**
  * Base Module for Sponge SSO
  */
class Module extends AbstractModule {

  def configure() = {
    bind(classOf[UserDAO]).to(classOf[UserDAOImpl])
    bind(classOf[TotpAuth]).to(classOf[TotpAuthImpl])
    bind(classOf[GoogleAuth]).to(classOf[GoogleAuthImpl])
    bind(classOf[PasswordFactory]).to(classOf[PasswordFactoryImpl])
    bind(classOf[QrCodeRenderer]).to(classOf[QrCodeRendererImpl])
    bind(classOf[MojangApi]).to(classOf[MojangApiImpl])
    bind(classOf[GitHubApi]).to(classOf[GitHubApiImpl])
    bind(classOf[GravatarApi]).to(classOf[Gravatar])
  }

}
