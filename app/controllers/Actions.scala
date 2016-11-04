package controllers

import db.UserDBO
import models.{User, Session => DbSession}
import org.spongepowered.play.ActionHelpers
import play.api.cache.CacheApi
import play.api.mvc.Results._
import play.api.mvc._
import security.{SingleSignOn, SpongeAuthConfig}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * A collection of custom actions used by the application.
  */
trait Actions extends Requests with ActionHelpers {

  val users: UserDBO
  val config: SpongeAuthConfig
  val ssoSecret = this.config.sso.getString("secret").get
  val ssoMaxAge = this.config.sso.getLong("maxAge").get.millis

  implicit val cache: CacheApi

  /** Ensures a request is not authenticated */
  def SSORedirect(sso: Option[String], sig: Option[String]) = Action andThen notAuthActon(sso, sig)

  def NotAuthenticated = SSORedirect(None, None)

  /** Ensures a request is authenticated */
  def Authenticated = Action andThen authAction

  /** Ensures a request has a cookie that points to a valid session. */
  def WithSession = Action andThen withSessionAction

  /** Called when a user is not authorized to visit some page */
  def onUnauthorized(request: Request[_]) = Redirect(routes.Application.showLogIn(None, None))

  /**
    * An implicit wrapper for a Result to provide some added functionality.
    *
    * @param result Result to wrap
    */
  implicit final class ResultWrapper(result: Result) {

    val users = Actions.this.users

    /**
      * Creates a new [[DbSession]] that is not authenticated and adds a
      * reference cookie to the result.
      *
      * @param user User to remember
      * @return     Result with token
      */
    def remembering(user: User) = withSession(this.users.createSession(user, authenticated = false))

    /**
      * Creates a new [[DbSession]] that is authenticated and adds a reference
      * cookie to the result.
      *
      * @param user User to authenticate
      * @return     Result with token
      */
    def authenticatedAs(user: User) = withSession(this.users.createSession(user, authenticated = true))

    /**
      * Adds a reference cookie to the specified [[DbSession]] to the result.
      *
      * @param session  Session to add to result.
      * @return         Result with token
      */
    def withSession(session: DbSession) = {
      val cookie = this.users.createSessionCookie(session)
      result.withSession(Security.username -> session.username).withCookies(cookie)
    }

  }

  // Action impl

  private def notAuthActon(sso: Option[String], sig: Option[String]) = new ActionFilter[Request] {
    def filter[A](request: Request[A]): Future[Option[Result]] = Future.successful {
      val signOn = SingleSignOn.parseValidateAndCache(
        secret = Actions.this.ssoSecret,
        maxAge = Actions.this.ssoMaxAge,
        sso = sso,
        sig = sig
      )(request.session, Actions.this.cache)
      val result = SingleSignOn.addToResult(Redirect(routes.Application.showHome()), signOn)
      Actions.this.users.current(request).map(_ => result)
    }
  }

  private def authAction = new ActionRefiner[Request, AuthRequest] {
    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] = Future.successful {
      Actions.this.users.current(request)
        .map(AuthRequest(_, request))
        .toRight(onUnauthorized(request))
    }
  }

  private def withSessionAction = new ActionRefiner[Request, SessionRequest] {
    def refine[A](request: Request[A]): Future[Either[Result, SessionRequest[A]]] = Future.successful {
      Actions.this.users.getSession(request)
        .map(SessionRequest(_, request))
        .toRight(onUnauthorized(request))
    }
  }

}
