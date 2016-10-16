import javax.inject.Inject

import db.UserDBO
import models.User
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.driver.JdbcProfile

final class MockUserDBO @Inject()(@NamedDatabase("test") provider: DatabaseConfigProvider) extends UserDBO {

  override val dbConfig = this.provider.get[JdbcProfile]
  override val maxSessionAge = MockUserDBO.maxSessionAge
  override val maxEmailConfirmationAge: Long = 10000

}

object MockUserDBO {

  var maxSessionAge = 60 * 60

}

object FakeUser extends User(
  email = "spongie@spongepowered.org",
  username = "Spongie",
  password = "wubalubadubdub"
)
