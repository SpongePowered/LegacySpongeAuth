package models

import java.sql.Timestamp

import db.UserDBO
import db.schema.PasswordResetTable

case class PasswordReset(override val id: Option[Int],
                         createdAt: Timestamp,
                         override val expiration: Timestamp,
                         override val token: String,
                         email: String) extends TokenExpirable {

  override type M = PasswordReset
  override type T = PasswordResetTable

  def user(implicit users: UserDBO): User = users.withEmail(this.email).get

}
