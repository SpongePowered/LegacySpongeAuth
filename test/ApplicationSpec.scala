import java.net.URLDecoder

import db.UserDBO
import mail.Mailer
import models.EmailConfirmation
import org.apache.commons.lang3.RandomStringUtils.{randomAlphanumeric => randomString}
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.Mode
import play.api.cache.CacheApi
import play.api.inject._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Cookie, Security}
import play.api.test.Helpers._
import play.api.test._
import sso.SSOConfig

import scala.concurrent.Future

@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

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

  this.sso.secret = this.config.sso.getString("secret").get

  val users = this.injector.instanceOf[UserDBO]
  val mailer = this.injector.instanceOf[Mailer].asInstanceOf[MockMailer]

  val ssoQuery = this.sso.getQuery()
  val badSSOQuery = this.sso.getQuery(badSig = true)

  this.users.removeAll()

  import users.getSession

  def doSignUp(request: FakeRequest[_],
               email: String = FakeUser.email,
               username: String = FakeUser.username,
               password: String = FakeUser.password) = {
    route(this.app, request.withFormUrlEncodedBody(
      "email" -> email,
      "username" -> username,
      "password" -> password
    )).get
  }

  def doLogIn(request: FakeRequest[_], username: String, password: String) = {
    route(this.app, request.withFormUrlEncodedBody(
      "username" -> username,
      "password" -> password
    )).get
  }

  def doLogOut(authToken: Cookie) = {
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
    val error = flash(result).get("error")
    error.isDefined must equalTo(true)
    error.get must equalTo(errorId)
  }

  def assertAuthenticated(result: Future[play.api.mvc.Result]) = {
    val token = getAuthToken(result)
    token.isDefined must equalTo(true)

    val serverSession = getSession(token.get.value)
    serverSession.isDefined must equalTo(true)
    serverSession.get.username must equalTo(FakeUser.username)

    val clientSession = session(result).get(Security.username)
    clientSession.isDefined must equalTo(true)
    clientSession.get must equalTo(FakeUser.username)
  }

  def assertNotAuthenticated(result: Future[play.api.mvc.Result]) = {
    getAuthToken(result).isEmpty must equalTo(true)
    session(result).get(Security.username).isEmpty must equalTo(true)
  }

  def assertCreated(result: Future[play.api.mvc.Result]): EmailConfirmation = {
    status(result) must equalTo(OK)
    assertNotAuthenticated(result)
    val user = ApplicationSpec.this.users.withName(FakeUser.username)
    user.isDefined must equalTo(true)

    val confirmation = this.users.getEmailConfirmation(user.get.email)
    confirmation.isDefined must equalTo(true)
    confirmation.get
  }

  def assertSSOSuccess(result: Future[play.api.mvc.Result]) = {
    val homeRedirect = redirectLocation(result).get
    val queryIndex = homeRedirect.indexOf('?')
    queryIndex must not equalTo(-1)
    val queryParams = homeRedirect.substring(queryIndex + 1).split('&')
    queryParams.length must beGreaterThanOrEqualTo(1)

    val finalRedirect = queryParams.find(_.startsWith("redirect=")).map(r => r.substring(r.indexOf('=') + 1))
    finalRedirect.isDefined must equalTo(true)

    // Follow the redirect
    val redirectUrl = URLDecoder.decode(finalRedirect.get, "UTF-8")
    val consumeSSO = route(ApplicationSpec.this.app, FakeRequest(GET, redirectUrl)).get
    status(consumeSSO) must equalTo(OK)
  }

  def getAuthToken(result: Future[play.api.mvc.Result]): Option[Cookie] = cookies(result).get("_token")

  def getSSOToken(result: Future[play.api.mvc.Result]): Option[String] = session(result).get("sso")

  "Application" should {
    var authToken: Cookie = null

    "showHome" should {
      "redirect when unauthenticated" in {
        val home = route(ApplicationSpec.this.app, FakeRequest(GET, "/")).get
        status(home) must equalTo(SEE_OTHER)
        assertNotAuthenticated(home)
      }

      "delete expired sessions" in new WithServer {
        val ogAge = MockUserDBO.maxSessionAge
        MockUserDBO.maxSessionAge = 1

        // create the user
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        val confirmation = assertCreated(signUp)

        // confirm the user's email
        Thread.sleep(1000) // wait for mailer
        val token = ApplicationSpec.this.mailer.getToken(confirmation.email)
        token.isDefined must equalTo(true)
        val confirmCall = route(ApplicationSpec.this.app, FakeRequest(GET, "/email/confirm/" + token.get)).get
        status(confirmCall) must equalTo(SEE_OTHER)
        assertAuthenticated(confirmCall)

        // ensure session will expire
        Thread.sleep(2000)
        val session = getSession(getAuthToken(confirmCall).get.value)
        session.isEmpty must equalTo(true)
        ApplicationSpec.this.users.removeAll()

        MockUserDBO.maxSessionAge = ogAge
      }
    }

    "signUp" should {
      "reject invalid sso signatures" in {
        val request = FakeRequest(GET, "/signup" + ApplicationSpec.this.badSSOQuery)
        val showSignUp = route(ApplicationSpec.this.app, request).get
        status(showSignUp) must equalTo(OK)
        assertNotAuthenticated(showSignUp)
        getSSOToken(showSignUp).isEmpty must equalTo(true)
      }

      "complete sso" in new WithServer {
        // Get the sign up form to cache the SSO request
        val request = FakeRequest(GET, "/signup" + ApplicationSpec.this.ssoQuery)
        val showSignUp = route(ApplicationSpec.this.app, request).get
        status(showSignUp) must equalTo(OK)
        assertNotAuthenticated(showSignUp)
        val ssoToken = getSSOToken(showSignUp)
        ssoToken.isDefined must equalTo(true)

        // Complete account creation
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        val confirmation = assertCreated(signUp)

        Thread.sleep(1000) // wait for mailer
        val token = ApplicationSpec.this.mailer.getToken(confirmation.email)
        token.isDefined must equalTo(true)
        val confirmRequest = FakeRequest(GET, "/email/confirm/" + token.get).withSession("sso" -> ssoToken.get)
        val confirmCall = route(ApplicationSpec.this.app, confirmRequest).get
        status(confirmCall) must equalTo(SEE_OTHER)
        assertAuthenticated(confirmCall)

        // Follow redirects to SSO origin
        assertSSOSuccess(confirmCall)

        ApplicationSpec.this.users.removeAll()
      }

      "create user" in new WithServer {
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        val confirmation = assertCreated(signUp)

        Thread.sleep(1000)
        val token = ApplicationSpec.this.mailer.getToken(confirmation.email)
        token.isDefined must equalTo(true)
        val confirmCall = route(ApplicationSpec.this.app, FakeRequest(GET, "/email/confirm/" + token.get)).get
        status(confirmCall) must equalTo(SEE_OTHER)
        assertAuthenticated(confirmCall)
        authToken = getAuthToken(confirmCall).get
      }

      "fail when authenticated" in {
        val signUp = doSignUp(FakeRequest(POST, "/signup").withCookies(authToken))
        status(signUp) must equalTo(BAD_REQUEST)
      }

      "fail with taken email" in {
        val signUp = doSignUp(
          request = FakeRequest(POST, "/signup"),
          username = randomString(10))
        status(signUp) must equalTo(SEE_OTHER)
        assertHasError(signUp, "error.unique.email")
      }

      "fail with taken username" in {
        val signUp = doSignUp(
          request = FakeRequest(POST, "/signup"),
          email = "johnsmith@example.com")
        status(signUp) must equalTo(SEE_OTHER)
        assertHasError(signUp, "error.unique.username")
      }

      "fail with malformed username" in {
        val signUp = doSignUp(
          request = FakeRequest(POST, "/signup"),
          email = "johnsmith@example.com",
          username = "my bad username")
        status(signUp) must equalTo(SEE_OTHER)
        assertHasError(signUp, "error.malformed.username")
      }
    }

    "showSignUp" should {
      "redirect when authenticated" in {
        val signUp = route(ApplicationSpec.this.app, FakeRequest(GET, "/signup").withCookies(authToken)).get
        status(signUp) must equalTo(SEE_OTHER)
      }
    }

    "showLogIn" should {
      "redirect when authenticated" in {
        val logIn = route(ApplicationSpec.this.app, FakeRequest(GET, "/login").withCookies(authToken)).get
        status(logIn) must equalTo(SEE_OTHER)
      }
    }

    "logIn" should {
      "fail when authenticated" in {
        val logIn = doLogIn(FakeRequest(POST, "/login").withCookies(authToken), FakeUser.username, FakeUser.password)
        status(logIn) must equalTo(BAD_REQUEST)
      }

      "fail with invalid username" in {
        val logIn = doLogIn(FakeRequest(POST, "/login"), "urmom", FakeUser.password)
        status(logIn) must equalTo(SEE_OTHER)
        assertHasError(logIn, "error.verify.user")
      }

      "fail with invalid password" in {
        val logIn = doLogIn(FakeRequest(POST, "/login"), FakeUser.username, "franksandbeans")
        status(logIn) must equalTo(SEE_OTHER)
        assertHasError(logIn, "error.verify.user")
      }

      "reject invalid sso signatures" in {
        val request = FakeRequest(GET, "/login" + ApplicationSpec.this.badSSOQuery)
        val showSignUp = route(ApplicationSpec.this.app, request).get
        status(showSignUp) must equalTo(OK)
        getSSOToken(showSignUp).isEmpty must equalTo(true)
      }

      "complete sso" in new WithServer {
        val showLogIn = route(ApplicationSpec.this.app, FakeRequest(GET, "/login" + ApplicationSpec.this.ssoQuery)).get
        status(showLogIn) must equalTo(OK)
        val ssoToken = getSSOToken(showLogIn)
        ssoToken.isDefined must equalTo(true)

        val request = FakeRequest(POST, "/login").withSession("sso" -> ssoToken.get)
        val logIn = doLogIn(request, FakeUser.username, FakeUser.password)
        assertAuthenticated(logIn)
        assertSSOSuccess(logIn)
      }

      "success" in new WithServer {
        assertAuthenticated(doLogIn(FakeRequest(POST, "/login"), FakeUser.username, FakeUser.password))
      }
    }

    "showVerification" should {
      "fail without sso" in {
        val verify = route(this.app, FakeRequest(GET, "/verify")).get
        status(verify) must equalTo(BAD_REQUEST)
      }
    }

    "verify" should {
      "fail with invalid username" in {
        val verify = doLogIn(FakeRequest(POST, "/verify"), "urmom", FakeUser.password)
        status(verify) must equalTo(BAD_REQUEST)
      }

      "fail with invalid password" in {
        val verify = doLogIn(FakeRequest(POST, "/verify"), FakeUser.username, "franksandbeans")
        status(verify) must equalTo(BAD_REQUEST)
      }

      "reject invalid sso signatures" in {
        val request = FakeRequest(GET, "/verify" + ApplicationSpec.this.badSSOQuery)
        val showSignUp = route(ApplicationSpec.this.app, request).get
        status(showSignUp) must equalTo(BAD_REQUEST)
        getSSOToken(showSignUp).isEmpty must equalTo(true)
      }

      "complete sso" in new WithServer {
        val verifyRequest = FakeRequest(GET, "/verify" + ApplicationSpec.this.ssoQuery)
        val showVerify = route(ApplicationSpec.this.app, verifyRequest).get
        status(showVerify) must equalTo(OK)
        val ssoToken = getSSOToken(showVerify)
        ssoToken.isDefined must equalTo(true)

        val request = FakeRequest(POST, "/verify").withSession("sso" -> ssoToken.get)
        val verify = doLogIn(request, FakeUser.username, FakeUser.password)
        status(verify) must equalTo(SEE_OTHER)
        assertSSOSuccess(verify)
      }
    }

    "logOut" should {
      "delete session" in doLogOut(authToken)
    }
  }

}
