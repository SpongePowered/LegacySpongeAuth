package security

import javax.inject.Inject

import play.api.Configuration

/**
  * A convenience class for managing Sponge SSO's configuration.
  */
final class SpongeAuthConfig @Inject()(config: Configuration) {

  lazy val play = this.config.getConfig("play").get
  lazy val sponge = this.config.getConfig("sponge").get
  lazy val security = this.config.getConfig("security").get
  lazy val sso = this.security.getConfig("sso").get
  lazy val totp = this.security.getConfig("totp").get
  lazy val db = this.config.getConfig("db").get
  lazy val mail = this.config.getConfig("mail").get
  lazy val external = this.config.getConfig("external").get

  /** Ensures that debug mode is enabled. */
  def checkDebug() = if (!this.sso.getBoolean("debug").get) throw new IllegalStateException("must be in debug mode")

}
