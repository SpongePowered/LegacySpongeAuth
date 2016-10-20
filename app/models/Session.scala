package models

import java.sql.Timestamp

import db.UserDBO
import db.schema.SessionTable

/**
  * Represents a [[User]] session associated with some device.
  *
  * @param id               Unique ID
  * @param createdAt        Timestamp of creation
  * @param expiration       Timestamp of when this session should no longer be considered valid
  * @param username         Username of user session is associated with
  * @param token            Unique token to be given to the client as a cookie for session identification
  * @param isAuthenticated  True if this session should be considered as authenticated
  */
case class Session(id: Option[Int] = None,
                   createdAt: Timestamp,
                   override val expiration: Timestamp,
                   username: String,
                   override val token: String,
                   isAuthenticated: Boolean = false) extends TokenExpirable {

  override type M = Session
  override type T = SessionTable

  /**
    * Returns the [[User]] this Session is associated with.
    *
    * @param users  [[UserDBO]] instance
    * @return       Associated User
    */
  def user(implicit users: UserDBO) = users.withName(this.username).get

}
