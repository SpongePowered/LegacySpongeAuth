package db.schema

import java.sql.Timestamp

import models.User
import slick.driver.PostgresDriver.api._

final class UserTable(tag: Tag) extends Table[User](tag, "users") {

  def id                = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def createdAt         = column[Timestamp]("created_at")
  def email             = column[String]("email")
  def isEmailConfirmed  = column[Boolean]("is_email_confirmed")
  def username          = column[String]("username")
  def password          = column[String]("password")
  def mcUsername        = column[String]("mc_username")
  def ircNick           = column[String]("irc_nick")
  def ghUsername        = column[String]("gh_username")
  def totpSecret        = column[String]("totp_secret")
  def isTotpConfirmed   = column[Boolean]("is_totp_confirmed")

  override def * = (id.?, createdAt.?, email, isEmailConfirmed, username, password, mcUsername.?, ircNick.?,
                    ghUsername.?, totpSecret.?, isTotpConfirmed) <> (User.tupled, User.unapply)

}
