import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._
import security.totp.TotpAuth
import security.totp.qr.QrCodeRenderer

@RunWith(classOf[JUnitRunner])
class TotpSpec extends Specification {

  val totp = new TotpAuth {}
  val qr = new QrCodeRenderer {}
  val secret = "MPCESOLM6F5I7JJG"

  "TOTP" should {
    "generate key" in {
      println("code = " + this.totp.generateCode(this.secret))
      true must equalTo(true)
    }
  }

}
