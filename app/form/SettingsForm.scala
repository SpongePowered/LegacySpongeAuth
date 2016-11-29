package form

import db.UserDAO
import slick.driver.PostgresDriver.api._
import db.schema.user.UserTable
import models.User

case class SettingsForm(mcUsername: Option[String],
                        ghUsername: Option[String],
                        ircNick: Option[String])
                       (implicit users: UserDAO) {

  private def checkSetting(user: User, rep: UserTable => Rep[String], value: Option[String]): Boolean = {
    value.isEmpty || this.users.isFieldUnique(rep, value.get, excluding = user)
  }

  /**
    * Ensures the the submitted fields are unique and valid. We have to check
    * the uniqueness after normal validation because we need to check for
    * uniqueness <i>excluding</i> the currently authenticated user which we
    * can only access with the request in scope.
    *
    * @param user User to check settings for
    * @return     Error if failed, None otherwise
    */
  def check(user: User): Option[String] = {
    if (!checkSetting(user, _.mcUsername, this.mcUsername))
      Some("error.unique.mc-username")
    else if (!checkSetting(user, _.ghUsername, this.ghUsername))
      Some("error.unique.gh-username")
    else if (!checkSetting(user, _.ircNick, this.ircNick))
      Some("error.unique.irc-nick")
    else
      None
  }

}
