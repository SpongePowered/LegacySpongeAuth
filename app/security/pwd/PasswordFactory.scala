package security.pwd

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

import org.apache.commons.codec.binary.Hex
import security.SpongeAuthConfig

/**
  * Handles password creation and validation within the application.
  */
trait PasswordFactory {

  final val Random = new SecureRandom
  final val KeyLength = 256
  final val SaltBytes = 16
  final val Charset = java.nio.charset.Charset.forName("ISO-8859-1")
  final val Hex = new Hex(Charset)

  val algo = "PBKDF2WithHmacSHA256"
  val iterations = 64000

  /**
    * Hashes the specified clear text password.
    *
    * @param pwd Password to hash
    * @return    Hashed password
    */
  def hash(pwd: String): Password = {
    val salt = encodeHex(generateSalt())
    val hash = encodeHex(pbkdf2(pwd, salt.getBytes(Charset)))
    Password(hash, salt)
  }

  /**
    * Verifies the specified clear text password matches the specified hash
    * and salt.
    *
    * @param pwd  Password to check
    * @param hash Password hash
    * @param salt Password salt
    * @return     True if verified
    */
  def check(pwd: String, hash: String, salt: String): Boolean = {
    val check = encodeHex(pbkdf2(pwd, salt.getBytes(Charset)))
    check.equals(hash)
  }

  private def generateSalt() = {
    val salt = new Array[Byte](SaltBytes)
    Random.nextBytes(salt)
    salt
  }

  private def pbkdf2(pwd: String, salt: Array[Byte]) = {
    val keySpec = new PBEKeySpec(pwd.toCharArray, salt, this.iterations, KeyLength)
    SecretKeyFactory.getInstance(this.algo).generateSecret(keySpec).getEncoded
  }

  private def encodeHex(data: Array[Byte]) = new String(Hex.encode(data), Charset)

}

final class PasswordFactoryImpl @Inject()(config: SpongeAuthConfig) extends PasswordFactory {

  override val algo = this.config.security.getString("password.algorithm").get
  override val iterations = this.config.security.getInt("password.iterations").get

}
