import com.google.inject.AbstractModule
import db.{UserDBO, UserDBOImpl}

/**
  * Base Module for Sponge SSO
  */
class Module extends AbstractModule {

  def configure() = {
    bind(classOf[UserDBO]).to(classOf[UserDBOImpl])
  }

}
