package models

import java.sql.Timestamp

case class OneTimePassword(id: Option[Int] = None, createdAt: Timestamp, userId: Int, code: Int)
