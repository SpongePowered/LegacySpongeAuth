package db

import java.sql.Timestamp

import slick.driver.PostgresDriver.api._

class SessionTable(tag: Tag) extends Table[models.Session](tag, "sessions") {

  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def createdAt = column[Timestamp]("created_at")
  def expiration = column[Timestamp]("expiration")
  def username = column[String]("username")
  def token = column[String]("token")

  override def * = (id.?, createdAt, expiration, username, token) <> (models.Session.tupled, models.Session.unapply)

}
