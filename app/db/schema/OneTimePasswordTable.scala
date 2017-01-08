package db.schema

import java.sql.Timestamp

import models.OneTimePassword
import slick.driver.PostgresDriver.api._

class OneTimePasswordTable(tag: Tag) extends Table[OneTimePassword](tag, "one_time_passwords") {

  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def createdAt = column[Timestamp]("created_at")
  def userId = column[Int]("user_id")
  def code = column[Int]("code")

  override def * = (id.?, createdAt, userId, code) <> (OneTimePassword.tupled, OneTimePassword.unapply)

}
