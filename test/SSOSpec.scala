import backend.FakeUser
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.runner.JUnitRunner
import play.api.test.{FakeRequest, WithServer}
import play.api.test.Helpers._

@RunWith(classOf[JUnitRunner])
final class SSOSpec extends Specification with ApplicationHelpers {

  "Single Sign On" should {
    "via sign up" in {
      "reject invalid signatures" in {
        val request = FakeRequest(GET, "/signup" + this.badSSOQuery)
        val showSignUp = route(this.app, request).get
        status(showSignUp) must equalTo(OK)
        getSSOToken(showSignUp).isEmpty must equalTo(true)
      }

      "reject signatures that require verification" in new WithServer {
        // Send SSO request
        val ssoQuery = SSOSpec.this.sso.getQuery(verify = true)
        val request = FakeRequest(GET, "/signup" + ssoQuery)
        val showSignUp = route(SSOSpec.this.app, request).get
        status(showSignUp) must equalTo(OK)

        val ssoToken = getSSOToken(showSignUp)
        ssoToken.isDefined must equalTo(true)

        // Create account
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        val confirmation = assertCreated(signUp)

        // Confirm account
        Thread.sleep(1000)
        val token = SSOSpec.this.mailer.scrapeToken(confirmation.email)
        token.isDefined must equalTo(true)

        val confirmRequest = FakeRequest(GET, "/email/confirm/" + token.get).withSSO(ssoToken.get)
        val confirmCall = route(SSOSpec.this.app, confirmRequest).get
        status(confirmCall) must equalTo(SEE_OTHER)

        // Ensure that we are redirected from home
        val homeRedirect = redirectLocation(confirmCall).get
        val home = route(SSOSpec.this.app, FakeRequest(GET, homeRedirect)).get
        status(home) must equalTo(SEE_OTHER)

        SSOSpec.this.users.removeAll()
      }

      "complete sso" in new WithServer {
        // Get the sign up form to cache the SSO request
        val request = FakeRequest(GET, "/signup" + SSOSpec.this.ssoQuery)
        val showSignUp = route(SSOSpec.this.app, request).get
        status(showSignUp) must equalTo(OK)

        val ssoToken = getSSOToken(showSignUp)
        ssoToken.isDefined must equalTo(true)

        // Complete account creation
        val signUp = doSignUp(FakeRequest(POST, "/signup"))
        val confirmation = assertCreated(signUp)

        Thread.sleep(1000) // wait for mailer
        val token = SSOSpec.this.mailer.scrapeToken(confirmation.email)
        token.isDefined must equalTo(true)

        val confirmRequest = FakeRequest(GET, "/email/confirm/" + token.get).withSSO(ssoToken.get)
        val confirmCall = route(SSOSpec.this.app, confirmRequest).get
        status(confirmCall) must equalTo(SEE_OTHER)

        // Follow redirects to SSO origin
        assertSSOSuccess(confirmCall, ssoToken.get, getAuthToken(signUp).get)
      }
    }

    "via log in" in {
      "reject invalid signatures" in {
        val request = FakeRequest(GET, "/login" + this.badSSOQuery)
        val showLogIn = route(this.app, request).get
        status(showLogIn) must equalTo(OK)
        getSSOToken(showLogIn).isEmpty must equalTo(true)
      }

      "reject signatures that require verification" in new WithServer {
        // Send security.sso request
        val ssoQuery = SSOSpec.this.sso.getQuery(verify = true)
        val request = FakeRequest(GET, "/login" + ssoQuery)
        val showLogIn = route(SSOSpec.this.app, request).get
        status(showLogIn) must equalTo(OK)

        val ssoToken = getSSOToken(showLogIn)
        ssoToken.isDefined must equalTo(true)

        // Log in
        val logIn = doLogIn(FakeRequest(POST, "/login"), FakeUser.username, FakeUser.password)
        assertAuthenticated(logIn)
        status(logIn) must equalTo(SEE_OTHER)

        // Ensure we are redirected from home
        val homeRedirect = redirectLocation(logIn).get
        val home = route(SSOSpec.this.app, FakeRequest(GET, homeRedirect)).get
        status(home) must equalTo(SEE_OTHER)
      }

      "complete sso" in new WithServer {
        val showLogIn = route(SSOSpec.this.app, FakeRequest(GET, "/login" + SSOSpec.this.ssoQuery)).get
        status(showLogIn) must equalTo(OK)
        val ssoToken = getSSOToken(showLogIn)
        ssoToken.isDefined must equalTo(true)

        val request = FakeRequest(POST, "/login").withSSO(ssoToken.get)
        val logIn = doLogIn(request, FakeUser.username, FakeUser.password)
        assertAuthenticated(logIn)
        assertSSOSuccess(logIn, ssoToken.get, getAuthToken(logIn).get)
      }
    }

    "via verify" in {
      "fail without sso" in {
        val verify = route(this.app, FakeRequest(GET, "/verify")).get
        status(verify) must equalTo(BAD_REQUEST)
      }

      "fail with invalid username" in {
        val verify = doLogIn(FakeRequest(POST, "/verify"), "urmom", FakeUser.password)
        status(verify) must equalTo(BAD_REQUEST)
      }

      "fail with invalid password" in {
        val verify = doLogIn(FakeRequest(POST, "/verify"), FakeUser.username, "franksandbeans")
        status(verify) must equalTo(BAD_REQUEST)
      }

      "reject invalid signatures" in {
        val request = FakeRequest(GET, "/verify" + this.badSSOQuery)
        val showSignUp = route(this.app, request).get
        status(showSignUp) must equalTo(BAD_REQUEST)
        getSSOToken(showSignUp).isEmpty must equalTo(true)
      }

      "complete sso" in new WithServer {
        val verifyRequest = FakeRequest(GET, "/verify" + SSOSpec.this.ssoQuery)
        val showVerify = route(SSOSpec.this.app, verifyRequest).get
        status(showVerify) must equalTo(OK)
        val ssoToken = getSSOToken(showVerify)
        ssoToken.isDefined must equalTo(true)

        val request = FakeRequest(POST, "/verify").withSSO(ssoToken.get)
        val verify = doLogIn(request, FakeUser.username, FakeUser.password)
        status(verify) must equalTo(SEE_OTHER)
        val redirect = redirectLocation(verify).get
        redirect must contain(SSOSpec.this.sso.ConsumeSSO)

        val origin = route(SSOSpec.this.app, FakeRequest(GET, redirect)).get
        status(origin) must equalTo(OK)
      }
    }
  }

}
