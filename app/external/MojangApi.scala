package external

import javax.inject.Inject

import org.spongepowered.play.util.WSUtils.parseJson
import play.api.http.Status
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import security.SpongeAuthConfig

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Handles requests to the Mojang API.
  */
trait MojangApi {

  val ws: WSClient
  val timeout: Duration
  val baseUrl: String = "https://api.mojang.com"

  val Logger = play.api.Logger("Mojang API")

  /**
    * Represents a profile for the game Minecraft.
    *
    * @param id       Unique ID
    * @param username Profile username
    */
  case class MinecraftProfile(id: String, username: String)

  implicit private val minecraftProfileReads: Reads[MinecraftProfile] = (
    (JsPath \ "id").read[String] and
    (JsPath \ "name").read[String]
  )(MinecraftProfile.apply _)

  /**
    * Returns the [[MinecraftProfile]] with the specified username, if found,
    * None otherwise.
    *
    * @param username Username to lookup
    * @return         MinecraftProfile if found
    */
  def getMinecraftProfile(username: String): Option[MinecraftProfile] = {
    await(this.ws.url(route(s"/users/profiles/minecraft/$username")).get().map { response =>
      if (response.status == Status.NO_CONTENT)
        None
      else
        parseJson(response, Logger).map(_.as[MinecraftProfile])
    })
  }

  private def await[A](result: Future[A]) = Await.result(result, timeout)

  private def route(route: String) = this.baseUrl + route

}

final class MojangApiImpl @Inject()(override val ws: WSClient, config: SpongeAuthConfig) extends MojangApi {
  override val timeout = this.config.external.getLong("mojang.timeout").get.millis
}
