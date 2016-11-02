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
                salt: String,
                isAdmin: Boolean = false,
                mcUsername: Option[String] = None,
                ircNick: Option[String] = None,
                ghUsername: Option[String] = None,
                totpSecret: Option[String] = None,
                isTotpConfirmed: Boolean = false,
                failedTotpAttempts: Int = 0) {

  def this(formData: SignUpForm, salt: String) = this(
    email = formData.email,
    username = formData.username,
    password = formData.password,
    salt = salt,
    mcUsername = formData.mcUsername,
    ircNick = formData.ircNick,
    ghUsername = formData.ghUsername
  )

}
