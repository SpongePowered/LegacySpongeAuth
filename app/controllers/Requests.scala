package controllers

import models.{Session, User}
import play.api.mvc.{Request, WrappedRequest}

/**
  * A collection of wrapped requests used by the application.
  */
trait Requests {

  /**
    * Represents a request that has been authenticated.
    *
    * @param user     Authenticated user
    * @param request  Original request
    */
  case class AuthRequest[A](user: User, request: Request[A]) extends WrappedRequest[A](request)

  /**
    * Represents a request that contains a [[Session]].
    *
    * @param userSession  Session of request
    * @param request      Original request
    */
  case class SessionRequest[A](userSession: Session, request: Request[A]) extends WrappedRequest[A](request)

}
