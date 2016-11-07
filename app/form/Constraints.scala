package form

import db.UserDBO
import db.schema.user.UserTable
import external.{GitHubApi, MojangApi}
import play.api.data.Forms._
import play.api.data.Mapping
import security.SpongeAuthConfig
import slick.driver.PostgresDriver.api._

/**
  * Custom form constraints.
  */
trait Constraints {

  val config: SpongeAuthConfig
  val users: UserDBO
  val mojang: MojangApi
  val gitHub: GitHubApi

  import config.security.{getInt, getString}
  import users.isFieldUnique

  val usernameRegex = "^\\S*$"

  val username = nonEmptyText(
    minLength = getInt("username.minLen").get,
    maxLength = getInt("username.maxLen").get
  ) verifying("error.malformed", _.matches(this.usernameRegex)) unique(_.username)

  val password = nonEmptyText(
    minLength = getInt("password.minLen").get,
    maxLength = getInt("password.maxLen").get
  )

  val totp = number verifying("error.digits", _.toString.length == this.config.totp.getInt("digits").get)

  val minecraftUsername = optional(
    nonEmptyText verifying("error.notFound", this.mojang.getMinecraftProfile(_).isDefined)
  )

  val gitHubUsername = optional(
    nonEmptyText verifying("error.notFound", this.gitHub.getUser(_).isDefined)
  )

  val ircNick = optional(nonEmptyText)

  val apiKey = nonEmptyText verifying("error.invalidKey", _.equals(getString("api.key").get))

  /**
    * A wrapper for a String [[Mapping]].
    *
    * @param mapping Mapping to wrap
    */
  implicit final class UniqueStringMapping(mapping: Mapping[String]) {

    /**
      * Verifies that this mapping's value is unique in the [[UserTable]].
      *
      * @param rep  UserTable [[Rep]]
      * @return     Modified mapping
      */
    def unique(rep: UserTable => Rep[String])
    = mapping.verifying("error.unique", str => isFieldUnique(rep(_).toLowerCase, str.toLowerCase))

  }

  /**
    * A wrapper for an String Option [[Mapping]].
    *
    * @param mapping Mapping to wrap
    */
  implicit final class UniqueStringOptionMapping(mapping: Mapping[Option[String]]) {

    /**
      * Verifies that this mapping's value is unique in the [[UserTable]].
      *
      * @param rep  UserTable [[Rep]]
      * @return     Modified mapping
      */
    def unique(rep: UserTable => Rep[String])
    = mapping.verifying("error.unique", _.forall(str => isFieldUnique(rep(_).toLowerCase, str.toLowerCase)))

  }

}
