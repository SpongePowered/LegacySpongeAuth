package db

import java.sql.Timestamp
import java.util.{Date, UUID}
import javax.inject.Inject

import com.google.common.base.Preconditions._
import db.schema.{EmailConfirmationTable, SessionTable, UserTable}
import form.SignUpForm
import models.{EmailConfirmation, TokenExpirable, User}
import org.mindrot.jbcrypt.BCrypt._
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc.{Cookie, Request}
import security.CryptoUtils._
import security.sso.SSOConfig
import security.totp.TotpAuth
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.languageFeature.implicitConversions

/**
  * Contains all [[User]] information.
  */
trait UserDBO {

  val dbConfig: DatabaseConfig[JdbcProfile]
  val users = TableQuery[UserTable]
  val sessions = TableQuery[SessionTable]
  val emailConfirms = TableQuery[EmailConfirmationTable]

  /** The argument to be supplied to [[org.mindrot.jbcrypt.BCrypt.gensalt()]] */
  val passwordSaltLogRounds: Int = 10
  /** The maximum wait time for database queries */
  val timeout: Duration = 10.seconds
  /** The maximum age of a [[models.Session]] */
  val maxSessionAge: Int
  /** The maximum age of an [[EmailConfirmation]] */
  val maxEmailConfirmationAge: Long
  /** [[TotpAuth]] instance */
  val totp: TotpAuth
  /** Secret string used for two-way encryption */
  val encryptionSecret: String

  implicit val self = this

  import dbConfig.db
  import models.{Session => DbSession}

  private def await[R](future: Future[R]): R = Await.result(future, this.timeout)

  protected def theTime: Timestamp = new Timestamp(new Date().getTime)

  /**
    * Creates a new [[User]] using the specified [[SignUpForm]].
    *
    * @param formData Form data to process
    * @return         New user
    */
  def createUser(formData: SignUpForm): EmailConfirmation = {
    checkNotNull(formData, "null form data", "")
    // Create user
    val pwdHash = hashpw(formData.password, gensalt(this.passwordSaltLogRounds))
    var user = new User(formData.copy(password = pwdHash)).copy(createdAt = Some(theTime))
    val userInsert = this.users returning this.users += user
    user = await(db.run(userInsert))
    createEmailConfirmation(user)
  }

  /**
    * Deletes the pending [[EmailConfirmation]] of the specified token if it
    * exists and marks the user as having their email confirmed.
    *
    * @param token  Token to lookup
    * @return       New session if email confirmed
    */
  def confirmEmail(token: String) = {
    checkNotNull(token, "null token", "")
    getTokenExpirable[EmailConfirmation](this.emailConfirms, token).map { confirmation =>
      db.run(this.emailConfirms.filter(_.token === token).delete)
      val updateUserQuery = for { u <- this.users if u.email === confirmation.email } yield u.isEmailConfirmed
      await(db.run(updateUserQuery.update(true)))
    }
  }

  /**
    * Returns the [[EmailConfirmation]] associated with the specified email,
    * if any. The [[EmailConfirmation]] is deleted immediately if it has
    * expired and None will be returned.
    *
    * @param email  Email to lookup
    * @return       EmailConfirmation if found
    */
  def getEmailConfirmation(email: String): Option[EmailConfirmation] = {
    checkNotNull(email, "null email", "")
    await(db.run(this.emailConfirms.filter(_.email === email).result).map(_.headOption)).flatMap { confirmation =>
      if (confirmation.hasExpired) {
        await(db.run(this.emailConfirms.filter(_.id === confirmation.id).delete))
        None
      } else
        Some(confirmation)
    }
  }

  /**
    * Creates a new [[EmailConfirmation]] for the specified [[User]].
    *
    * @param user User to create confirmation for
    * @return     New EmailConfirmation
    */
  def createEmailConfirmation(user: User): EmailConfirmation = {
    checkNotNull(user, "null user", "")
    checkArgument(user.id.isDefined, "undefined user", "")
    val emailToken = UUID.randomUUID().toString.replace("-", "")
    val confirmExpiration = new Timestamp(new Date().getTime + this.maxEmailConfirmationAge)
    val emailConfirm = EmailConfirmation(None, theTime, confirmExpiration, user.email, emailToken)
    val emailConfirmInsert = this.emailConfirms returning this.emailConfirms += emailConfirm
    await(db.run(emailConfirmInsert))
  }

  /**
    * Deletes any [[EmailConfirmation]]s for the specified email.
    *
    * @param email Email to delete confirmations for
    */
  def deleteEmailConfirmation(email: String) = {
    checkNotNull(email, "null email", "")
    await(db.run(this.emailConfirms.filter(_.email === email).delete))
  }

  /**
    * Creates a new [[models.Session]] for the specified [[User]].
    *
    * @param user User to create session for
    * @return     New session
    */
  def createSession(user: User, authenticated: Boolean = false): DbSession = {
    checkNotNull(user, "null user", "")
    checkArgument(user.id.isDefined, "undefined user", "")
    val token = UUID.randomUUID().toString
    val expiration = new Timestamp(new Date().getTime + maxSessionAge * 1000L)
    var session = DbSession(
      createdAt = theTime,
      expiration = expiration,
      username = user.username,
      token = token,
      isAuthenticated = authenticated)
    val sessionInsert = this.sessions returning this.sessions += session
    await(db.run(sessionInsert))
  }

  /**
    * Marks the specified session as authenticated.
    *
    * @param session Session to mark as authenticated
    */
  def setSessionAuthenticated(session: DbSession) = {
    checkNotNull(session, "null session", "")
    checkArgument(session.id.isDefined, "undefined session", "")
    val query = for { s <- this.sessions if s.id === session.id.get } yield s.isAuthenticated
    await(db.run(query.update(true)))
  }

  /**
    * Creates a new Cookie to be given to the client to identify their session.
    *
    * @param session  Session to create cookie for
    * @return         Session cookie
    */
  def createSessionCookie(session: DbSession) = {
    checkNotNull(session, "null session", "")
    checkArgument(session.id.isDefined, "undefined session", "")
    Cookie("_token", session.token, Some(this.maxSessionAge))
  }

  /**
    * Enables and generates a new secret for the specified [[User]]. If TOTP is
    * already enabled for this User an exception will be thrown.
    *
    * @param user User to enable TOTP for
    * @return     Updated User
    */
  def enableTotp(user: User): User = {
    checkNotNull(user, "null user", "")
    checkArgument(user.id.isDefined, "undefined user", "")
    user.totpSecret match {
      case None =>
        val query = for { u <- this.users if u.id === user.id.get } yield u.totpSecret
        val secret = encrypt(this.totp.generateSecret(), this.encryptionSecret)
        await(db.run(query.update(secret)))
        get(user.id.get).get
      case Some(secret) =>
        throw new Exception("user already has TOTP enabled")
    }
  }

  def verifyTotp(user: User, code: Int): Boolean = {
    checkNotNull(user, "null user", "")
    checkArgument(user.id.isDefined, "undefined user", "")
    checkArgument(user.totpSecret.isDefined, "totp disabled for user", "")
    val secret = decrypt(user.totpSecret.get, this.encryptionSecret)
    this.totp.checkCode(secret, code)
  }

  /**
    * Returns the currently authenticated [[User]], if any.
    *
    * @param request  Request of user
    * @return         Authenticated User if found, None otherwise
    */
  def current(implicit request: Request[_]): Option[User] = {
    checkNotNull(request, "null request", "")
    getSession.flatMap { session =>
      if (session.isAuthenticated)
        withName(session.username)
      else
        None
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
  def getSession(token: String): Option[DbSession] = getTokenExpirable[DbSession](this.sessions, token)

  def getSession(implicit request: Request[_]): Option[DbSession] = {
    checkNotNull(request, "null request", "")
    request.cookies.get("_token").flatMap(token => getSession(token.value))
  }

  private def getTokenExpirable[M <: TokenExpirable](query: TableQuery[M#T], token: String): Option[M] = {
    checkNotNull(query, "null query", "")
    checkNotNull(token, "null token", "")
    await(db.run(query.filter(_.token === token).result).map(_.headOption)).flatMap { model =>
      if (model.hasExpired) {
        await(db.run(query.filter(_.id === model.id).delete))
        None
      } else
        Some(model)
    }.map(_.asInstanceOf[M])
  }

  /**
    * Deletes the session with the specified token if any.
    *
    * @param token Token of session
    */
  def deleteSession(token: String) = {
    checkNotNull(token, "null token", "")
    await(db.run(this.sessions.filter(_.token === token).delete))
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
    checkNotNull(username, "null username", "")
    checkNotNull(password, "null password", "")
    if (checkpw(password, user.password))
      Some(user)
    else
      None
  }

  /**
    * Removes all [[User]]s from the database.
    */
  def removeAll() = await(db.run(this.users.delete))

  /**
    * Returns the [[User]] with the specified ID, if any.
    *
    * @param id ID to get
    * @return   User if found, None otherwise
    */
  def get(id: Int): Option[User] = await(db.run(this.users.filter(_.id === id).result).map(_.headOption))

  /**
    * Returns the [[User]] with the specified username if any.
    *
    * @param username Username to lookup
    * @return         User if found, None otherwise
    */
  def withName(username: String): Option[User] = {
    checkNotNull(username, "null username", "")
    await(db.run(this.users.filter(_.username.toLowerCase === username.toLowerCase).result).map(_.headOption))
  }

  /**
    * Returns the [[User]] with the specified email, if any.
    *
    * @param email  Email to lookup
    * @return       User if found, None otherwise
    */
  def withEmail(email: String): Option[User] = {
    checkNotNull(email, "null email", "")
    await(db.run(this.users.filter(_.email === email).result).map(_.headOption))
  }

  /**
    * Checks if the specified user field is unique for the specified value.
    *
    * @param rep    User field
    * @param value  Value to check
    * @return       True if unique
    */
  def isFieldUnique(rep: UserTable => Rep[String], value: String): Boolean = {
    checkNotNull(rep, "null rep", "")
    await(db.run((!this.users.filter(rep(_) === value).exists).result))
  }

}

final class UserDBOImpl @Inject()(provider: DatabaseConfigProvider,
                                  config: SSOConfig,
                                  override val totp: TotpAuth) extends UserDBO {

  override val dbConfig = this.provider.get[JdbcProfile]
  override val passwordSaltLogRounds = this.config.sso.getInt("password.saltLogRounds").get
  override val timeout = this.config.db.getLong("timeout").get.millis
  override val maxSessionAge = this.config.play.getInt("http.session.maxAge").get
  override val maxEmailConfirmationAge = this.config.mail.getLong("confirm.maxAge").get
  override val encryptionSecret: String = this.config.play.getString("crypto.secret").get

}
