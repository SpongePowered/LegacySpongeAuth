package external

import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject

import org.apache.commons.codec.binary.Hex.encodeHexString
import play.api.http.Status
import play.api.libs.ws.WSClient

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global

trait GravatarApi {

  final val Url = "https://www.gravatar.com"
  final val Algo = "MD5"
  final val Charset = "UTF-8"

  val ws: WSClient
  val timeout: Duration = 10.seconds

  def get(email: String, default: String = null): String = {
    val hash = encodeHexString(MessageDigest.getInstance(Algo).digest(email.toLowerCase.getBytes(Charset)))
    var url = s"$Url/avatar/$hash?s=400"
    if (default != null)
      url += "&d=" + URLEncoder.encode(default, Charset)
    url
  }

  def exists(email: String): Boolean = await(this.ws.url(get(email, "404")).get().map(_.status != Status.NOT_FOUND))

  private def await[A](future: Future[A]): A = Await.result(future, this.timeout)

}

final class Gravatar @Inject()(override val ws: WSClient) extends GravatarApi
