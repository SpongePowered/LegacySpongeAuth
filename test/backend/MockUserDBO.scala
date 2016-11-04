package backend

import javax.inject.Inject

import db.UserDBO
import models.User
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import security.SpongeAuthConfig
import security.pwd.PasswordFactory
import security.totp.TotpAuth
import slick.driver.JdbcProfile

final class MockUserDBO @Inject()(@NamedDatabase("test") provider: DatabaseConfigProvider,
                                  override val totp: TotpAuth,
                                  config: SpongeAuthConfig) extends UserDBO {

  override val dbConfig = this.provider.get[JdbcProfile]
  override val maxSessionAge = MockUserDBO.maxSessionAge
  override val maxEmailConfirmationAge: Long = 10000
  override val maxPasswordResetAge: Long = 10000
  override val encryptionSecret = this.config.play.getString("crypto.secret").get
  override val passwords: PasswordFactory = new PasswordFactory {}
}

object MockUserDBO {

  var maxSessionAge = 60 * 60

}

object FakeUser extends User(
  email = "spongie@spongepowered.org",
  username = "Spongie",
  password = "wubalubadubdub",
  salt = ""
)
