package db

import java.sql.Timestamp
import java.util.Date
import javax.inject.Inject

import form.SignUpForm
import models.User
import org.mindrot.jbcrypt.BCrypt
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc.Security
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._
import sso.SSOConfig

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Contains all [[User]] information.
  */
trait UserDBO {

  val dbConfig: DatabaseConfig[JdbcProfile]
  val users = TableQuery[UserTable]

  /** The argument to be supplied to [[org.mindrot.jbcrypt.BCrypt.gensalt()]] */
  val passwordSaltLogRounds: Int
  /** The maximum wait time for database queries */
  val timeout: Long

  private def await[R](future: Future[R]): R = Await.result(future, timeout.millis)

  /**
    * Creates a new [[User]] using the specified [[SignUpForm]].
    *
    * @param formData Form data to process
    * @return         New user
    */
  def createUser(formData: SignUpForm): User = {
    val pwdHash = BCrypt.hashpw(formData.password, BCrypt.gensalt(this.passwordSaltLogRounds))
    val user = new User(formData.copy(password = pwdHash)).copy(createdAt = Some(new Timestamp(new Date().getTime)))
    val query = this.users returning this.users += user
    await(this.dbConfig.db.run(query))
  }

  /**
    * Verifies the specified username's password and returns the user if
    * successful.
    *
    * @param username Username to check password of
    * @param password Plain text password to check
    * @return         User if successful, None otherwise
    */
  def verify(username: String, password: String): Option[User] = withName(username).flatMap { user =>
    if (BCrypt.checkpw(password, user.password))
      Some(user)
    else
      None
  }

  /**
    * Removes all [[User]]s from the database.
    */
  def removeAll() = await(this.dbConfig.db.run(this.users.delete))

  /**
    * Returns the [[User]] with the specified ID, if any.
    *
    * @param id ID to get
    * @return   User if found, None otherwise
    */
  def get(id: Int): Option[User] = await(this.dbConfig.db.run(this.users.filter(_.id === id).result).map(_.headOption))

  /**
    * Returns the [[User]] with the specified username if any.
    *
    * @param username Username to lookup
    * @return         User if found, None otherwise
    */
  def withName(username: String): Option[User] = {
    await(this.dbConfig.db.run(this.users
      .filter(_.username.toLowerCase === username.toLowerCase).result)
      .map(_.headOption))
  }

  /**
    * Returns the currently authenticated [[User]], if any.
    *
    * @param session  Session of user
    * @return         Authenticated User if found, None otherwise
    */
  def current(implicit session: play.api.mvc.Session): Option[User] = session.get(Security.username).flatMap(withName)

}

class UserDBOImpl @Inject()(provider: DatabaseConfigProvider, config: SSOConfig) extends UserDBO {
  override val dbConfig = this.provider.get[JdbcProfile]
  override val passwordSaltLogRounds = this.config.sso.getInt("password.saltLogRounds").get
  override val timeout = this.config.db.getLong("timeout").get
}
