import db.UserDBO
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Cookie, Security}
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Future

/**
  * Add your spec here.
  * You can mock out a whole application including requests, plugins etc.
  * For more information, consult the wiki.
  */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  val app = GuiceApplicationBuilder().overrides(bind[UserDBO].to[MockUserDBO]).build
  val users = app.injector.instanceOf[UserDBO]

  this.users.removeAll()

  def doSignUp(request: FakeRequest[_]) = {
    route(this.app, request.withFormUrlEncodedBody(
      "email" -> FakeUser.email,
      "username" -> FakeUser.username,
      "password" -> FakeUser.password
    )).get
  }

  def doLogIn(request: FakeRequest[_], username: String, password: String) = {
    route(this.app, request.withFormUrlEncodedBody(
      "username" -> username,
      "password" -> password
    )).get
  }

  def checkError(result: Future[play.api.mvc.Result], errorId: String) = {
    val error = flash(result).get("error")
    error.isDefined must equalTo(true)
    error.get must equalTo(errorId)
  }

  def checkTokenAndSession(result: Future[play.api.mvc.Result]) = {
    val token = cookies(result).get("_token")
    token.isDefined must equalTo(true)

    val serverSession = this.users.getSession(token.get.value)
    serverSession.isDefined must equalTo(true)
    serverSession.get.username must equalTo(FakeUser.username)

    val clientSession = session(result).get(Security.username)
    clientSession.isDefined must equalTo(true)
    clientSession.get must equalTo(FakeUser.username)
  }

  def createAndCheckUser(result: Future[play.api.mvc.Result]) = {
    status(result) must equalTo(SEE_OTHER)
    val user = ApplicationSpec.this.users.withName(FakeUser.username)
    user.isDefined must equalTo(true)
    checkTokenAndSession(result)
  }

  "Application" should {
    var authToken: Cookie = null

    "signUp" should {
      "create user" in new WithServer {
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        createAndCheckUser(signUp)
        authToken = cookies(signUp).get("_token").get
      }

      "fail when authenticated" in {
        val signUp = doSignUp(FakeRequest(POST, "/signup").withCookies(authToken))
        status(signUp) must equalTo(BAD_REQUEST)
      }
    }

    "showHome" should {
      "redirect when unauthenticated" in {
        val home = route(ApplicationSpec.this.app, FakeRequest(GET, "/")).get
        status(home) must equalTo(SEE_OTHER)
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
        checkError(logIn, "error.verify.user")
      }

      "fail with invalid password" in {
        val logIn = doLogIn(FakeRequest(POST, "/login"), FakeUser.username, "franksandbeans")
        status(logIn) must equalTo(SEE_OTHER)
        checkError(logIn, "error.verify.user")
      }

      "success" in new WithServer {
        checkTokenAndSession(doLogIn(FakeRequest(POST, "/login"), FakeUser.username, FakeUser.password))
      }
    }

    "logOut" should {
      "delete session" in {
        var serverSession = ApplicationSpec.this.users.getSession(authToken.value)
        serverSession.isDefined must equalTo(true)
        serverSession.get.username must equalTo(FakeUser.username)
        val logOut = route(this.app, FakeRequest(GET, "/logout").withCookies(authToken)).get
        status(logOut) must equalTo(SEE_OTHER)
        serverSession = ApplicationSpec.this.users.getSession(authToken.value)
        serverSession.isEmpty must equalTo(true)
        session(logOut).get(Security.username).isEmpty must equalTo(true)
      }
    }
  }

}
