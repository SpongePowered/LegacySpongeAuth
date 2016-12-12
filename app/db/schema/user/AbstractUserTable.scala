package db.schema.user

import java.sql.Timestamp

import models.User
import slick.driver.PostgresDriver.api._

abstract class AbstractUserTable(tag: Tag, name: String) extends Table[User](tag, name) {

  def id                  = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def createdAt           = column[Timestamp]("created_at")
  def joinDate            = column[Timestamp]("join_date")
  def email               = column[String]("email")
  def isEmailConfirmed    = column[Boolean]("is_email_confirmed")
  def username            = column[String]("username")
  def avatarUrl           = column[String]("avatar_url")
  def password            = column[String]("password")
  def salt                = column[String]("salt")
  def isAdmin             = column[Boolean]("is_admin")
  def mcUsername          = column[String]("mc_username")
  def ircNick             = column[String]("irc_nick")
  def ghUsername          = column[String]("gh_username")
  def totpSecret          = column[String]("totp_secret")
  def isTotpConfirmed     = column[Boolean]("is_totp_confirmed")
  def failedTotpAttempts  = column[Int]("failed_totp_attempts")
  def googleId            = column[String]("google_id")
  def deletedAt           = column[Timestamp]("deleted_at")

  override def * = (id.?, createdAt, joinDate, email, isEmailConfirmed, username, avatarUrl, password.?, salt.?,
                    isAdmin, mcUsername.?, ircNick.?, ghUsername.?, totpSecret.?, isTotpConfirmed, failedTotpAttempts,
                    googleId.?, deletedAt.?) <> (User.tupled, User.unapply)

}
