package models

import java.sql.Timestamp

import db.UserDAO
import db.schema.EmailConfirmationTable

/**
  * Represents a pending email confirmation for a user.
  *
  * @param id         Unique ID
  * @param createdAt  Timestamp of creation
  * @param expiration Timestamp of expiration
  * @param email      Email that is to be confirmed
  * @param token      Unique token for verification
  */
case class EmailConfirmation(id: Option[Int],
                             createdAt: Timestamp,
                             override val expiration: Timestamp,
                             email: String,
                             override val token: String) extends TokenExpirable {

  override type M = EmailConfirmation
  override type T = EmailConfirmationTable

  /**
    * Returns the [[User]] associated with this EmailConfirmation.
    *
    * @param users [[UserDAO]] instance
    * @return       Associated user
    */
  def user(implicit users: UserDAO) = users.withEmail(this.email).get

}
