package external

import java.security.MessageDigest
import java.text.MessageFormat
import javax.inject.Inject

import org.apache.commons.codec.binary.Hex.encodeHexString
import security.SpongeAuthConfig

trait LetterAvatars {

  final val Algo = MessageDigest.getInstance("MD5")
  final val Route = "/letter_avatar_proxy/v2/letter/{0}/{1}/{2}.png"

  val baseUrl: String

  def getUrl(username: String, size: Int = 240) = {
    this.baseUrl + MessageFormat.format(Route,
      username(0).toString,
      encodeHexString(Algo.digest(username.getBytes("UTF-8"))).substring(0, 6),
      size.toString)
  }

}

final class LetterAvatarsImpl @Inject()(config: SpongeAuthConfig) extends LetterAvatars {

  override val baseUrl = this.config.external.getString("forums.url").get

}
