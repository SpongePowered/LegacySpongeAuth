package db.schema

import java.sql.Timestamp

import models.EmailConfirmation
import slick.driver.PostgresDriver.api._

class EmailConfirmationTable(tag: Tag) extends TokenExpirableTable[EmailConfirmation](tag, "email_confirmations") {

  def createdAt = column[Timestamp]("created_at")
  def email = column[String]("email")

  override def * = (id.?, createdAt, expiration, email, token) <> (EmailConfirmation.tupled, EmailConfirmation.unapply)

}
