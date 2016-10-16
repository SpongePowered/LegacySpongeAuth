package mail

import play.twirl.api.Html

case class Email(recipient: String, subject: String, content: Html)
