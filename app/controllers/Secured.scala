package controllers

import db.UserDBO
import models.{User, Session => DbSession}
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent.Future

trait Secured {

  val users: UserDBO

  case class AuthRequest[A](user: User, request: Request[A]) extends WrappedRequest[A](request)

  case class SessionRequest[A](userSession: DbSession, request: Request[A]) extends WrappedRequest[A](request)

  def NotAuthenticated = Action andThen notAuthActon

  def Authenticated = Action andThen authAction

  def WithSession = Action andThen withSessionAction

  def onUnauthorized(request: Request[_]) = Redirect(routes.Application.showLogIn(None, None))

  private def notAuthActon = new ActionFilter[Request] {
    def filter[A](request: Request[A]): Future[Option[Result]] = Future.successful {
      Secured.this.users.current(request).map(user => Redirect(routes.Application.showHome()))
    }
  }

  private def authAction = new ActionRefiner[Request, AuthRequest] {
    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] = Future.successful {
      Secured.this.users.current(request)
        .map(AuthRequest(_, request))
        .toRight(onUnauthorized(request))
    }
  }

  private def withSessionAction = new ActionRefiner[Request, SessionRequest] {
    def refine[A](request: Request[A]): Future[Either[Result, SessionRequest[A]]] = Future.successful {
      Secured.this.users.getSession(request)
        .map(SessionRequest(_, request))
        .toRight(onUnauthorized(request))
    }
  }

  implicit final class ResultWrapper(result: Result) {

    val users = Secured.this.users

    def remembering(user: User) = addSession(user, authenticated = false)

    def authenticatedAs(user: User) = addSession(user, authenticated = true)

    def withSession(session: DbSession) = {
      val cookie = this.users.createSessionCookie(session)
      result.withSession(Security.username -> session.username).withCookies(cookie)
    }

    private def addSession(user: User, authenticated: Boolean) = {
      val cookie = this.users.createSessionCookie(this.users.createSession(user, authenticated))
      result.withSession(Security.username -> user.username).withCookies(cookie)
    }

  }

}
