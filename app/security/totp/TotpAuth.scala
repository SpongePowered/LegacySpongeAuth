package security.totp

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Date
import javax.inject.Inject

import org.apache.commons.codec.binary.Base32
import security.CryptoUtils.hmac_sha1

import scala.concurrent.duration._

/**
  * Handles generation and validation of TOTP token as specified by RFC 6238
  *
  * https://tools.ietf.org/html/rfc6238
  */
trait TotpAuth {

  /** The interval in which tokens are invalidated */
  val timeStep: Duration = 30.seconds
  /** The amount of generations we look back and ahead to check a code */
  val windows = 3

  val CharEncoding = "UTF-8"
  val SecretSize = 10
  val Random = new SecureRandom
  val Codec = new Base32

  /**
    * Generates a new secret key.
    *
    * @return New secret key
    */
  def generateSecret(): String = {
    val buffer: Array[Byte] = new Array(SecretSize)
    Random.nextBytes(buffer)
    Codec.encodeAsString(buffer)
  }

  /**
    * Validates that the specified code is valid with the specified secret.
    *
    * @param secret Secret key
    * @param code   Validation code
    * @return       True if valid code
    */
  def checkCode(secret: String, code: Int): Boolean = {
    val time = currentTimeInterval
    val decodedSecret = Codec.decode(secret)
    // Check codes up to "Windows" generations ago
    for (window <- -this.windows to this.windows) {
      if (code == generateCode(decodedSecret, time + window))
        return true
    }
    false
  }

  /**
    * Generates the code for the specified secret at the specified time.
    *
    * @param secret Secret key
    * @param time   Time interval (i.e. 30 = 30 seconds)
    * @return       Validation code
    */
  def generateCode(secret: Array[Byte], time: Long): Int = {
    // Generate hash from time
    val data = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(time).array()
    val hash = hmac_sha1(secret, data)

    // Truncate the hash to 4 bytes using the last 4 bits as an offset
    val offset = hash(hash.length - 1) & 0xf
    var truncatedHash: Long = 0
    for (i <- 0 until 4) {
      truncatedHash <<= 8
      truncatedHash |= (hash(offset + i) & 0xff)
    }
    truncatedHash &= 0x7FFFFFFF
    truncatedHash %= 1000000
    truncatedHash.toInt
  }

  /**
    * Generates the code for the specified secret at the current time.
    *
    * @param secret Secret key
    * @return       Validation code
    */
  def generateCode(secret: String): Int = generateCode(Codec.decode(secret), currentTimeInterval)

  /**
    * Returns the current time interval for the algorithm.
    *
    * @return Current time interval
    */
  def currentTimeInterval: Long = new Date().getTime / this.timeStep.toMillis

}

class TotpAuthImpl @Inject() extends TotpAuth
