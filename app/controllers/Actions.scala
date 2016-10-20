package controllers

import com.google.common.base.Preconditions._
import db.UserDBO
import models.{User, Session => DbSession}
import play.api.data.Form
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent.Future

/**
  * A collection of custom actions used by the application.
  */
trait Actions extends Requests {

  val users: UserDBO

  /** Ensures a request is not authenticated */
  def NotAuthenticated = Action andThen notAuthActon

  /** Ensures a request is authenticated */
  def Authenticated = Action andThen authAction

  /** Ensures a request has a cookie that points to a valid session. */
  def WithSession = Action andThen withSessionAction

  /** Called when a user is not authorized to visit some page */
  def onUnauthorized(request: Request[_]) = Redirect(routes.Application.showLogIn(None, None))

  def FormError(call: Call, form: Form[_]) = {
    checkNotNull(call, "null call", "")
    checkNotNull(form, "null form", "")
    checkArgument(form.errors.nonEmpty, "no errors", "")
    val firstError = form.errors.head
    Redirect(call).flashing("error" -> (firstError.message + '.' + firstError.key))
  }

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

    def withError(error: String) = result.flashing("error" -> error)

  }

  // Action impl

  private def notAuthActon = new ActionFilter[Request] {
    def filter[A](request: Request[A]): Future[Option[Result]] = Future.successful {
      Actions.this.users.current(request).map(user => Redirect(routes.Application.showHome()))
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
