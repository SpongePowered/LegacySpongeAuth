import backend.{FakeUser, MockUserDBO}
import org.apache.commons.lang3.RandomStringUtils.{randomAlphanumeric => randomString}
import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.mvc.{Cookie, Security}
import play.api.test.Helpers._
import play.api.test._

@RunWith(classOf[JUnitRunner])
final class ApplicationSpec extends Specification with ApplicationHelpers {

  import users.getSession

  "Application" should {
    var authToken: Cookie = null

    "showHome" should {
      "redirect when unauthenticated" in {
        val home = route(this.app, FakeRequest(GET, "/")).get
        status(home) must equalTo(SEE_OTHER)
        assertNotAuthenticated(home)
      }

      "instruct email confirmation if needed" in new WithServer {
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        status(signUp) must equalTo(SEE_OTHER)
        val confirmation = assertCreated(signUp)

        val user = session(signUp).get(Security.username)
        user.isDefined must equalTo(true)

        val redirect = redirectLocation(signUp).get
        val home = route(ApplicationSpec.this.app, FakeRequest(GET, redirect).withUser(user.get)).get
        status(home) must equalTo(OK)
        contentAsString(home) must contain(ApplicationSpec.this.messages("signup.confirmEmail", confirmation.email))
      }

      "delete expired sessions" in new WithServer {
        val ogAge = MockUserDBO.maxSessionAge
        MockUserDBO.maxSessionAge = 1

        // create the user
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        val confirmation = assertCreated(signUp)

        // confirm the user's email
        Thread.sleep(1000) // wait for mailer
        val token = ApplicationSpec.this.mailer.scrapeToken(confirmation.email)
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
      "create user" in new WithServer {
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        val confirmation = assertCreated(signUp)

        Thread.sleep(1000)
        val token = ApplicationSpec.this.mailer.scrapeToken(confirmation.email)
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

      "success" in new WithServer {
        assertAuthenticated(doLogIn(FakeRequest(POST, "/login"), FakeUser.username, FakeUser.password))
      }
    }

    "logOut" should {
      "delete session" in doLogOut(authToken)
    }
  }

}
