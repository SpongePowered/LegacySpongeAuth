package models

import java.sql.Timestamp

import form.TSignUpForm
import security.pwd.Password

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
                createdAt: Timestamp,
                joinDate: Timestamp,
                email: String,
                isEmailConfirmed: Boolean = false,
                username: String,
                avatarUrl: String,
                password: Option[String],
                salt: Option[String],
                isAdmin: Boolean = false,
                mcUsername: Option[String] = None,
                ircNick: Option[String] = None,
                ghUsername: Option[String] = None,
                totpSecret: Option[String] = None,
                isTotpConfirmed: Boolean = false,
                failedTotpAttempts: Int = 0,
                googleId: Option[String] = None,
                deletedAt: Option[Timestamp] = None) {

  def hasGravatar: Boolean = this.avatarUrl.startsWith("https://www.gravatar.com")

  def this(formData: TSignUpForm, createdAt: Timestamp, avatarUrl: String, pwd: Option[Password]) = this(
    createdAt = createdAt,
    joinDate = createdAt,
    email = formData.email,
    username = formData.username,
    avatarUrl = avatarUrl,
    password = pwd.map(_.hash),
    salt = pwd.map(_.salt),
    mcUsername = formData.mcUsername,
    ircNick = formData.ircNick,
    ghUsername = formData.ghUsername,
    googleId = formData.googleSubject
  )

}
