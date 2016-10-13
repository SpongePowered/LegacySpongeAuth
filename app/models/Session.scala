package models

import java.sql.Timestamp
import java.util.Date

import db.UserDBO

case class Session(id: Option[Int] = None,
                   createdAt: Timestamp,
                   expiration: Timestamp,
                   username: String,
                   token: String) {

  def this(createdAt: Timestamp, expiration: Timestamp, username: String, token: String)
  = this(None, createdAt, expiration, username, token)

  def user(implicit users: UserDBO) = users.withName(this.username).get

  def hasExpired: Boolean = this.expiration.before(new Date)

}
