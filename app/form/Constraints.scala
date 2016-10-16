package form

import db.schema.UserTable
import db.UserDBO
import play.api.data.Forms._
import play.api.data.Mapping
import sso.SSOConfig
import slick.driver.PostgresDriver.api._

/**
  * Custom form constraints.
  */
trait Constraints {

  val config: SSOConfig
  val users: UserDBO

  import this.users.isFieldUnique
  import this.config.sso.getInt

  val usernameRegex = "^\\S*$"

  val username = nonEmptyText(
    minLength = getInt("username.minLen").get,
    maxLength = getInt("username.maxLen").get
  ) verifying("error.malformed", _.matches(this.usernameRegex))

  val password = nonEmptyText(
    minLength = getInt("password.minLen").get,
    maxLength = getInt("password.maxLen").get
  )

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
