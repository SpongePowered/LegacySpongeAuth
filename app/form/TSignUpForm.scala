package form

trait TSignUpForm {

  val email: String
  val username: String
  val password: Option[String]
  val mcUsername: Option[String]
  val ircNick: Option[String]
  val ghUsername: Option[String]

  val googleIdToken: Option[String] = None
  var googleSubject: Option[String] = None

}
