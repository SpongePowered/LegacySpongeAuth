package form

case class SignUpForm(email: String,
                      username: String,
                      password: String,
                      mcUsername: Option[String],
                      ircNick: Option[String],
                      ghUsername: Option[String])
