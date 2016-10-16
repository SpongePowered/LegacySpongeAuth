package db.schema

import java.sql.Timestamp

import models.TokenExpirable
import slick.driver.PostgresDriver.api._

/**
  * Represents a table for something that can expire based on token-based
  * lookups.
  */
abstract class TokenExpirableTable[M <: TokenExpirable](tag: Tag, name: String) extends Table[M](tag, name) {

  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def expiration = column[Timestamp]("expiration")
  def token = column[String]("token")

}
