package models

import java.sql.Timestamp
import java.util.Date

import db.UserDBO

/**
  * Represents a [[User]] session associated with some device.
  *
  * @param id         Unique ID
  * @param createdAt  Timestamp of creation
  * @param expiration Timestamp of when this session should no longer be considered valid
  * @param username   Username of user session is associated with
  * @param token      Unique token to be given to the client as a cookie for session identification
  */
case class Session(id: Option[Int] = None,
                   createdAt: Timestamp,
                   expiration: Timestamp,
                   username: String,
                   token: String) {

  def this(createdAt: Timestamp, expiration: Timestamp, username: String, token: String)
  = this(None, createdAt, expiration, username, token)

  /**
    * Returns the [[User]] this Session is associated with.
    *
    * @param users  [[UserDBO]] instance
    * @return       Associated User
    */
  def user(implicit users: UserDBO) = users.withName(this.username).get

  /**
    * Returns true if this Session has expired and should no longer be considered valid.
    *
    * @return True if expired
    */
  def hasExpired: Boolean = this.expiration.before(new Date)

}
