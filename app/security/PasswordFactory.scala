package security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

import org.apache.commons.codec.binary.Hex.{decodeHex, encodeHexString}

/**
  * Handles password creation and validation within the application.
  */
trait PasswordFactory {

  final val Random = new SecureRandom
  final val KeyLength = 256

  val algo = "PBKDF2WithHmacSHA256"
  val iterations = 64000

  /**
    * Represents a hashed password.
    *
    * @param hash Password hash
    * @param salt Password salt
    */
  case class Password(hash: String, salt: String)

  /**
    * Hashses the specified clear text password.
    *
    * @param pwd Password to hash
    * @return    Hashed password
    */
  def hash(pwd: String): Password = {
    val salt = new Array[Byte](16)
    Random.nextBytes(salt)
    val hash = pbkdf2(pwd.toCharArray, salt)
    Password(encodeHexString(hash), encodeHexString(salt))
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
    val check = pbkdf2(pwd.toCharArray, decodeHex(salt.toCharArray))
    encodeHexString(check).equals(hash)
  }

  private def pbkdf2(pwd: Array[Char], salt: Array[Byte]): Array[Byte] = {
    val keySpec = new PBEKeySpec(pwd, salt, this.iterations, KeyLength)
    SecretKeyFactory.getInstance(this.algo).generateSecret(keySpec).getEncoded
  }

}

final class PasswordFactoryImpl @Inject()(config: SpongeAuthConfig) extends PasswordFactory {

  override val algo = this.config.security.getString("password.algorithm").get
  override val iterations = this.config.security.getInt("password.iterations").get

}
