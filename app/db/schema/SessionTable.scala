package db.schema

import java.sql.Timestamp

import slick.driver.PostgresDriver.api._

final class SessionTable(tag: Tag) extends TokenExpirableTable[models.Session](tag, "sessions") {

  def createdAt = column[Timestamp]("created_at")
  def username = column[String]("username")
  def isAuthenticated = column[Boolean]("is_authenticated")

  override def * = (id.?, createdAt, expiration, username, token, isAuthenticated) <> (models.Session.tupled,
                    models.Session.unapply)

}
