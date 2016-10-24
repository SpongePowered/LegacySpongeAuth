package external

import javax.inject.Inject

import external.WSUtils.parseJson
import play.api.http.Status
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import security.SpongeAuthConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Handles requests to the GitHub API
  */
trait GitHubApi {

  val ws: WSClient
  val timeout: Duration
  val baseUrl: String = "https://api.github.com"

  val Logger = play.api.Logger("GitHub API")

  /**
    * Represents a GitHub user.
    *
    * @param login Username
    * @param id Unique ID
    * @param userType Type of user
    * @param name Full name of user
    */
  case class GitHubUser(login: String,
                        id: Long,
                        userType: String,
                        name: String)

  implicit private val gitHubUserReads: Reads[GitHubUser] = (
    (JsPath \ "login").read[String] and
    (JsPath \ "id").read[Long] and
    (JsPath \ "type").read[String] and
    (JsPath \ "name").read[String]
  )(GitHubUser.apply _)

  /**
    * Returns the [[GitHubUser]] with the specified login string if found,
    * None otherwise.
    *
    * @param login  User login
    * @return       GitHubUser if found
    */
  def getUser(login: String): Option[GitHubUser] = {
    Logger.info(s"Getting user $login...")
    await(this.ws.url(route(s"/users/$login")).get().map { response =>
      if (response.status == Status.NOT_FOUND) {
        Logger.info("User not found.")
        None
      } else
        parseJson(response, Logger).map(_.as[GitHubUser])
    })
  }

  private def await[A](result: Future[A]) = Await.result(result, timeout)

  private def route(route: String) = this.baseUrl + route

}

final class GitHubApiImpl @Inject()(override val ws: WSClient, config: SpongeAuthConfig) extends GitHubApi {
  override val timeout = this.config.external.getLong("github.timeout").get.millis
}
