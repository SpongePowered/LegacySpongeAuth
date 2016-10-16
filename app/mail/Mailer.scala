package mail

import java.security.Security
import java.util.Date
import javax.inject.{Inject, Singleton}
import javax.mail.Message.RecipientType
import javax.mail.Session
import javax.mail.internet.{InternetAddress, MimeMessage}

import akka.actor.{ActorSystem, Scheduler}
import com.sun.net.ssl.internal.ssl.Provider
import sso.SSOConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Handles dispatch of emails to users. Particularly for email verification.
  */
trait Mailer extends Runnable {

  /** The sender username */
  val username: String
  /** The sender email */
  val email: InternetAddress
  /** The sender password */
  val password: String

  val smtpHost: String
  val smtpPort: Int = 465

  /** The rate at which to send emails */
  val interval: FiniteDuration = 30.seconds
  val scheduler: Scheduler

  /** The properties to be applied to the [[Session]] */
  val properties: Map[String, Any] = Map.empty
  var queue: Seq[Email] = Seq.empty

  val Logger = play.api.Logger("Mailer")

  private var session: Session = _

  /**
    * Configures, initializes, and starts this Mailer.
    */
  def start() = {
    Security.addProvider(new Provider())
    val props = System.getProperties
    for (prop <- this.properties.keys)
      props.setProperty(prop, this.properties.get(prop).toString)
    this.session = Session.getInstance(props)
    this.scheduler.schedule(this.interval, this.interval, this)
    Logger.info("Started")
  }

  /**
    * Sends the specified [[Email]].
    *
    * @param email Email to send
    */
  def send(email: Email) = {
    Logger.info("Sending email to " + email.recipient + "...")
    val message = new MimeMessage(this.session)
    message.setFrom(this.email)
    message.setRecipients(RecipientType.TO, email.recipient)
    message.setSubject(email.subject)
    message.setContent(email.content.toString, "text/html")
    message.setSentDate(new Date())

    val transport = this.session.getTransport("smtps")
    transport.connect(this.smtpHost, this.smtpPort, this.username, this.password)
    transport.sendMessage(message, message.getAllRecipients)
    transport.close()
  }

  /**
    * Pushes a new [[Email]] to the queue.
    *
    * @param email Email to push
    */
  def push(email: Email) = this.queue :+= email

  /**
    * Sends all queued [[Email]]s.
    */
  def run() = {
    Logger.info(s"Sending ${this.queue.size} queued emails...")
    this.queue.foreach(send)
    this.queue = Seq.empty
    Logger.info("Done.")
  }

}

@Singleton
class MailerImpl @Inject()(config: SSOConfig, actorSystem: ActorSystem) extends Mailer {

  private val conf = this.config.mail

  override val username = this.conf.getString("username").get
  override val email = InternetAddress.parse(this.conf.getString("email").get)(0)
  override val password = this.conf.getString("password").get
  override val smtpHost = this.conf.getString("smtp.host").get
  override val smtpPort = this.conf.getInt("smtp.port").get
  override val interval = this.conf.getLong("interval").get.millis
  override val scheduler = this.actorSystem.scheduler
  override val properties = this.conf.getObject("properties").get.unwrapped().asScala.toMap

  start()

}
