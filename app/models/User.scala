package models

import java.sql.Timestamp

import form.SignUpForm

/**
  * Represents a Sponge user.
  *
  * @param id               Unique ID
  * @param createdAt        Timestamp of creation
  * @param email            User email
  * @param isEmailConfirmed True if user has completed email confirmation
  * @param username         Username
  * @param password         User's password (hashed)
  * @param mcUsername       Minecraft username
  * @param ircNick          IRC nick
  * @param ghUsername       GitHub username
  * @param totpSecret       The user's TOTP secret if enabled (encrypted)
  */
case class User(id: Option[Int] = None,
                createdAt: Option[Timestamp] = None,
                email: String,
                isEmailConfirmed: Boolean = false,
                username: String,
                password: String,
                mcUsername: Option[String] = None,
                ircNick: Option[String] = None,
                ghUsername: Option[String] = None,
                totpSecret: Option[String] = None) {

  def this(email: String, username: String, password: String, mcUsername: String, ircNick: String,
           ghUsername: String) = {
    this(
      email = email,
      username = username,
      password = password,
      mcUsername = Option(mcUsername),
      ircNick = Option(ircNick),
      ghUsername = Option(ghUsername))
  }

  def this(formData: SignUpForm) = this(
    formData.email,
    formData.username,
    formData.password,
    formData.mcUsername.orNull,
    formData.ircNick.orNull,
    formData.ghUsername.orNull
  )

}
