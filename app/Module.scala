import com.google.inject.AbstractModule
import db.{UserDBO, UserDBOImpl}

class Module extends AbstractModule {

  def configure() = {
    bind(classOf[UserDBO]).to(classOf[UserDBOImpl])
  }

}
