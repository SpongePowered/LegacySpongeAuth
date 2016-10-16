package models

import java.sql.Timestamp
import java.util.Date

import db.schema.TokenExpirableTable

/**
  * Represents something that can expire and is identified by a [[String]]
  * token.
  */
trait TokenExpirable { self =>

  type M <: TokenExpirable { type M = self.M }
  type T <: TokenExpirableTable[M]

  val id: Option[Int]
  val token: String
  val expiration: Timestamp

  /**
    * Returns true if this instance has expired and should no longer be
    * considered valid.
    *
    * @return True if expired
    */
  def hasExpired: Boolean = this.expiration.before(new Date)

}
