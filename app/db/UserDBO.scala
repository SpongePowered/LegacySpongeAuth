package db

import java.sql.Timestamp
import java.util.{Date, UUID}
import javax.inject.Inject

import form.SignUpForm
import models.User
import org.mindrot.jbcrypt.BCrypt
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc.{Cookie, Request, Security}
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
  val sessions = TableQuery[SessionTable]

  /** The argument to be supplied to [[org.mindrot.jbcrypt.BCrypt.gensalt()]] */
  val passwordSaltLogRounds: Int
  /** The maximum wait time for database queries */
  val timeout: Long
  val maxSessionAge: Int

  private def await[R](future: Future[R]): R = Await.result(future, timeout.millis)

  private def theTime: Timestamp = new Timestamp(new Date().getTime)

  /**
    * Creates a new [[User]] using the specified [[SignUpForm]].
    *
    * @param formData Form data to process
    * @return         New user
    */
  def createUser(formData: SignUpForm): models.Session = {
    // Create user
    val pwdHash = BCrypt.hashpw(formData.password, BCrypt.gensalt(this.passwordSaltLogRounds))
    var user = new User(formData.copy(password = pwdHash)).copy(createdAt = Some(theTime))
    val userInsert = this.users returning this.users += user
    user = await(this.dbConfig.db.run(userInsert))
    createSession(user)
  }

  def createSession(user: User): models.Session = {
    val token = UUID.randomUUID().toString
    val expiration = new Timestamp(new Date().getTime + maxSessionAge * 1000L)
    var session = new models.Session(theTime, expiration, user.username, token)
    val sessionInsert = this.sessions returning this.sessions += session
    await(this.dbConfig.db.run(sessionInsert))
  }

  def createSessionCookie(session: models.Session) = Cookie("_token", session.token, Some(this.maxSessionAge))

  /**
    * Returns the currently authenticated [[User]], if any.
    *
    * @param request  Request of user
    * @return         Authenticated User if found, None otherwise
    */
  def current(implicit request: Request[_]): Option[User] = {
    val username = request.session.get(Security.username)
    if (username.isDefined)
      withName(username.get)
    else {
      request.cookies.get("_token").flatMap { token =>
        getSession(token.value).flatMap(session => withName(session.username))
      }
    }
  }

  /**
    * Retrieves the session for the specified token if it exists and has not
    * expired. If expired, the session will be deleted immediately and None
    * will be returned.
    *
    * @param token  Session token
    * @return       Session if found and has not expired
    */
  def getSession(token: String): Option[models.Session] = {
    await(this.dbConfig.db.run(this.sessions.filter(_.token === token).result).map(_.headOption)).flatMap { session =>
      if (session.hasExpired) {
        await(this.dbConfig.db.run(this.sessions.filter(_.id === session.id).delete))
        None
      } else
        Some(session)
    }
  }

  /**
    * Deletes the session with the specified token if any.
    *
    * @param token Token of session
    */
  def deleteSession(token: String) = await(this.dbConfig.db.run(this.sessions.filter(_.token === token).delete))

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

}

class UserDBOImpl @Inject()(provider: DatabaseConfigProvider, config: SSOConfig) extends UserDBO {
  override val dbConfig = this.provider.get[JdbcProfile]
  override val passwordSaltLogRounds = this.config.sso.getInt("password.saltLogRounds").get
  override val timeout = this.config.db.getLong("timeout").get
  override val maxSessionAge = this.config.play.getInt("http.session.maxAge").get
}
