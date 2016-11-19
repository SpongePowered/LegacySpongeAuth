package security

import javax.inject.Inject

import play.api.Configuration

/**
  * A convenience class for managing Sponge SSO's configuration.
  */
final class SpongeAuthConfig @Inject()(config: Configuration) {

  lazy val root = this.config
  lazy val play = this.root.getConfig("play").get
  lazy val app = this.root.getConfig("application").get
  lazy val sponge = this.root.getConfig("sponge").get
  lazy val security = this.root.getConfig("security").get
  lazy val sso = this.security.getConfig("sso").get
  lazy val totp = this.security.getConfig("totp").get
  lazy val db = this.root.getConfig("db").get
  lazy val mail = this.root.getConfig("mail").get
  lazy val external = this.root.getConfig("external").get

  /** Ensures that debug mode is enabled. */
  def checkDebug() = if (!this.sso.getBoolean("debug").get) throw new IllegalStateException("must be in debug mode")

}
