package form

import db.{UserDBO, UserTable}
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

  import users.isFieldUnique

  val username = nonEmptyText(
    minLength = this.config.sso.getInt("username.minLen").get,
    maxLength = this.config.sso.getInt("username.maxLen").get
  )

  val password = nonEmptyText(
    minLength = this.config.sso.getInt("password.minLen").get,
    maxLength = this.config.sso.getInt("password.maxLen").get
  )

  implicit final class UniqueStringMapping(mapping: Mapping[String]) {
    def unique(rep: UserTable => Rep[String])
    = mapping.verifying("error.unique", str => isFieldUnique(rep(_).toLowerCase, str.toLowerCase))
  }

  implicit final class UniqueStringOptionMapping(mapping: Mapping[Option[String]]) {
    def unique(rep: UserTable => Rep[String])
    = mapping.verifying("error.unique", _.forall(str => isFieldUnique(rep(_).toLowerCase, str.toLowerCase)))
  }

}
