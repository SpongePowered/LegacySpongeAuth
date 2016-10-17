package controllers

import db.UserDBO
import models.User
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent.Future

trait Secured {

  val users: UserDBO

  case class AuthRequest[A](user: User, request: Request[A]) extends WrappedRequest[A](request)

  def Authenticated = Action andThen authAction

  def onUnauthorized(request: Request[_]) = Redirect(routes.Application.showLogIn(None, None))

  private def authAction = new ActionRefiner[Request, AuthRequest] {
    def refine[A](request: Request[A]): Future[Either[Result, AuthRequest[A]]] = Future.successful {
      Secured.this.users.current(request)
        .map(AuthRequest(_, request))
        .toRight(onUnauthorized(request))
    }
  }

}
