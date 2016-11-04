package api

import models.User
import play.api.libs.json._

/**
  * Performs JSON conversions for API endpoints.
  */
trait SpongeAuthWrites {

  implicit val userWrites = new Writes[User] {
    def writes(user: User): JsValue = Json.obj(
      "id"          -> user.id.get,
      "created_at"  -> user.createdAt.get.toString,
      "email"       -> user.email,
      "username"    -> user.username,
      "is_admin"    -> user.isAdmin,
      "mc_username" -> user.mcUsername,
      "irc_nick"    -> user.ircNick,
      "gh_username" -> user.ghUsername,
      "deleted_at"  -> user.deletedAt.map(_.toString)
    )
  }

}
