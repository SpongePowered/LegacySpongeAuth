package form

trait TSignUpForm {

  val email: String
  val username: String
  val password: String
  val mcUsername: Option[String]
  val ircNick: Option[String]
  val ghUsername: Option[String]

}
