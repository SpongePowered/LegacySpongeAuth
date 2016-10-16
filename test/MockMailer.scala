import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import mail.{Email, Mailer}

import scala.concurrent.duration._

@Singleton
class MockMailer @Inject()(actorSystem: ActorSystem) extends Mailer {

  override val username = null
  override val email = null
  override val password = null
  override val smtpHost = null

  override val interval = 1.millisecond
  override val scheduler = this.actorSystem.scheduler

  var sentEmails: Map[String, Email] = Map.empty

  this.suppressLogger = true

  override def send(email: Email) = this.sentEmails += email.recipient -> email

  //noinspection ComparingUnrelatedTypes
  def getToken(email: String): Option[String] = {
    this.sentEmails.get(email).map(_.content.toString).map { rawHtml =>
      val tokenPrefix = "/email/confirm/"
      val tokenStartIndex = rawHtml.indexOf(tokenPrefix)
      if (tokenStartIndex == -1)
        throw new IllegalStateException("token not found")
      val slicedHtml = rawHtml.substring(tokenStartIndex + tokenPrefix.length)
      val tokenEndIndex = slicedHtml.indexOf("\"")
      if (tokenEndIndex == -1)
        throw new IllegalStateException("token not found")
      val token = slicedHtml.substring(0, tokenEndIndex)
      token
    }
  }

  start()

}
