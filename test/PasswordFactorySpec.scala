import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import security.pwd.PasswordFactory

@RunWith(classOf[JUnitRunner])
class PasswordFactorySpec extends Specification {

  val factory = new PasswordFactory {}

  "PasswordFactory" should {

    val clearPwd = "hunter2"
    val pwd = this.factory.hash(clearPwd)

    "check passwords" in {
      this.factory.check(clearPwd, pwd.hash, pwd.salt) must equalTo(true)
    }
  }

}
