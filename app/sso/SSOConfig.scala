package sso

import javax.inject.Inject

import play.api.Configuration

final class SSOConfig @Inject()(config: Configuration) {

  lazy val play = this.config.getConfig("play").get
  lazy val sponge = this.config.getConfig("sponge").get
  lazy val sso = this.config.getConfig("sso").get
  lazy val db = this.config.getConfig("db").get

  def checkDebug() = if (!this.sso.getBoolean("debug").get) throw new IllegalStateException("must be in debug mode")

}
