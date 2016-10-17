import backend.{FakeUser, MockCacheApi, MockMailer, MockUserDBO}
import db.UserDBO
import mail.Mailer
import models.EmailConfirmation
import org.specs2.mutable._
import play.api.Mode
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Cookie, Security}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sso.{SSOConfig, SingleSignOn}

import scala.concurrent.Future

trait ApplicationHelpers extends Specification {

  val sso = new SSOConsumer

  val app = GuiceApplicationBuilder()
    .overrides(bind[CacheApi].to[MockCacheApi])
    .overrides(bind[UserDBO].to[MockUserDBO])
    .overrides(bind[Mailer].to[MockMailer])
    .eagerlyLoaded()
    .additionalRouter(this.sso.Router)
    .in(Mode.Test)
    .build()

  val injector = this.app.injector
  val config = this.injector.instanceOf[SSOConfig]
  val cache = this.injector.instanceOf[CacheApi]
  val messages = this.injector.instanceOf[MessagesApi]

  this.sso.secret = this.config.sso.getString("secret").get

  implicit val users = this.injector.instanceOf[UserDBO]
  val mailer = this.injector.instanceOf[Mailer].asInstanceOf[MockMailer]

  val ssoQuery = this.sso.getQuery()
  val badSSOQuery = this.sso.getQuery(badSig = true)

  this.users.removeAll()

  import users.getSession

  implicit final class FakeRequestWrapper[A](request: FakeRequest[A]) {

    def withSSO(token: String): FakeRequest[A] = this.request.withCookies(Cookie("_sso", token))

    def withUser(username: String): FakeRequest[A] = this.request.withSession(Security.username -> username)

  }

  def doSignUp(request: FakeRequest[_],
               email: String = FakeUser.email,
               username: String = FakeUser.username,
               password: String = FakeUser.password) = {
    // Sends a sign up request
    route(this.app, request.withFormUrlEncodedBody(
      "email" -> email,
      "username" -> username,
      "password" -> password
    )).get
  }

  def doLogIn(request: FakeRequest[_], username: String, password: String) = {
    // Sends a log in request
    route(this.app, request.withFormUrlEncodedBody(
      "username" -> username,
      "password" -> password
    )).get
  }

  def doLogOut(authToken: Cookie) = {
    // Sends a log out request and ensures the session was cleared
    var serverSession = getSession(authToken.value)
    serverSession.isDefined must equalTo(true)
    serverSession.get.username must equalTo(FakeUser.username)

    val logOut = route(this.app, FakeRequest(GET, "/logout").withCookies(authToken)).get
    status(logOut) must equalTo(SEE_OTHER)
    serverSession = getSession(authToken.value)
    serverSession.isEmpty must equalTo(true)
    session(logOut).get(Security.username).isEmpty must equalTo(true)
  }

  def assertHasError(result: Future[play.api.mvc.Result], errorId: String) = {
    // Checks for a specific error
    val error = flash(result).get("error")
    error.isDefined must equalTo(true)
    error.get must equalTo(errorId)
  }

  def assertAuthenticated(result: Future[play.api.mvc.Result]) = {
    // Ensures a result contains an authentication token and session
    val token = getAuthToken(result)
    token.isDefined must equalTo(true)

    val serverSession = getSession(token.get.value)
    serverSession.isDefined must equalTo(true)
    serverSession.get.username must equalTo(FakeUser.username)

    val clientSession = session(result).get(Security.username)
    clientSession.isDefined must equalTo(true)
    clientSession.get must equalTo(FakeUser.username)
  }

  def assertNotAuthenticated(result: Future[play.api.mvc.Result]) = getAuthToken(result).isEmpty must equalTo(true)

  def assertCreated(result: Future[play.api.mvc.Result]): EmailConfirmation = {
    // Ensures a user is created and has an email confirmation
    status(result) must equalTo(SEE_OTHER)
    assertNotAuthenticated(result)
    val user = this.users.withName(FakeUser.username)
    user.isDefined must equalTo(true)

    val confirmation = this.users.getEmailConfirmation(user.get.email)
    confirmation.isDefined must equalTo(true)
    confirmation.get
  }

  def assertSSOSuccess(result: Future[play.api.mvc.Result], ssoToken: String, token: Cookie) = {
    // Completes and ensures an SSO request is successful
    val request = FakeRequest(GET, redirectLocation(result).get).withCookies(token).withSSO(ssoToken)
    val homeRedirect = route(this.app, request).get
    status(homeRedirect) must equalTo(OK)
    contentAsString(homeRedirect) must contain(sso.ConsumeSSO)

    val signOn = this.cache.get[SingleSignOn](ssoToken)
    signOn.isDefined must equalTo(true)
    val user = getSession(token.value).get.user
    val finalRedirect = route(this.app, FakeRequest(GET, signOn.get.getRedirect(user))).get
    status(finalRedirect) must equalTo(OK)
  }

  def getAuthToken(result: Future[play.api.mvc.Result]): Option[Cookie] = cookies(result).get("_token")

  def getSSOToken(result: Future[play.api.mvc.Result]): Option[String] = cookies(result).get("_sso").map(_.value)

}
