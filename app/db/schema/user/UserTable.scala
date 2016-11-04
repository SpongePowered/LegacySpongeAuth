package db.schema.user

import slick.driver.PostgresDriver.api._

final class UserTable(tag: Tag) extends AbstractUserTable(tag, "users")
