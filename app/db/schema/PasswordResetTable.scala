package db.schema

import java.sql.Timestamp

import models.PasswordReset
import slick.driver.PostgresDriver.api._

class PasswordResetTable(tag: Tag) extends TokenExpirableTable[PasswordReset](tag, "password_resets") {

  def createdAt = column[Timestamp]("created_at")
  def email = column[String]("email")

  override def * = (id.?, createdAt, expiration, token, email) <> (PasswordReset.tupled, PasswordReset.unapply)

}
