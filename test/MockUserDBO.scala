import javax.inject.Inject

import db.UserDBO
import models.User
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.driver.JdbcProfile

final class MockUserDBO @Inject()(@NamedDatabase("test") provider: DatabaseConfigProvider) extends UserDBO {

  override val dbConfig = this.provider.get[JdbcProfile]
  override val passwordSaltLogRounds = 10
  override val timeout: Long = 10000
  override val maxSessionAge = MockUserDBO.maxSessionAge

}

object MockUserDBO {

  var maxSessionAge = 60 * 60

}

object FakeUser extends User(
  email = "spongie@spongepowered.org",
  username = "Spongie",
  password = "wubalubadubdub"
)
