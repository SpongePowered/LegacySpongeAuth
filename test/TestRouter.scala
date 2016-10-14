import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.http.HttpVerbs._

import scala.runtime.AbstractPartialFunction

class TestRouter extends SimpleRouter {

  var sso: SSOConsumer = _
  val ConsumeSSO = "/test/sso_consume"

  def routes: Routes = new AbstractPartialFunction[RequestHeader, Handler] {
    def isDefinedAt(x: RequestHeader): Boolean = x.path.equals(ConsumeSSO)
    override def applyOrElse[A <: RequestHeader, B >: Handler](rh: A, default: A => B) = {
      (rh.method, rh.path) match {
        case (GET, ConsumeSSO) => consumeSSO(rh.getQueryString("sso"), rh.getQueryString("sig"))
        case _ => default(rh)
      }
    }
  }

  def consumeSSO(sso: Option[String], sig: Option[String]) = Action {
    if (sso.isEmpty || sig.isEmpty)
      BadRequest
    else {
      this.sso.authenticate(sso.get, sig.get) match {
        case None =>
          BadRequest
        case Some(user) =>
          if (!user.username.equals(FakeUser.username) || !user.email.equalsIgnoreCase(FakeUser.email))
            BadRequest
          else
            Ok
      }
    }
  }

}
