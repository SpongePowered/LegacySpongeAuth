package db.schema.user

import slick.driver.PostgresDriver.api._

final class DeletedUserTable(tag: Tag) extends AbstractUserTable(tag, "users_deleted")
