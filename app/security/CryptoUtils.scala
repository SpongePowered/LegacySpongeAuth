package security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import org.apache.commons.codec.binary.Hex

object CryptoUtils {

  val HmacSha256 = "HmacSHA256"
  val HmacSha1 = "HmacSHA1"
  val CharEncoding = "UTF-8"

  def hmac(algo: String, secret: Array[Byte], data: Array[Byte]): Array[Byte] = {
    val hmac = Mac.getInstance(algo)
    val keySpec = new SecretKeySpec(secret, algo)
    hmac.init(keySpec)
    hmac.doFinal(data)
  }

  def hmac_sha256(secret: String, data: Array[Byte]): String
  = Hex.encodeHexString(hmac(HmacSha256, secret.getBytes(CharEncoding), data))

  def hmac_sha1(secret: Array[Byte], data: Array[Byte]): Array[Byte] = hmac(HmacSha1, secret, data)

}
