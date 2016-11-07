package controllers

import javax.inject.Inject

import controllers.routes.{Application, TwoFactorAuth}
import db.UserDBO
import form.SpongeAuthForms
import org.spongepowered.play.util.CryptoUtils._
import play.api.cache.CacheApi
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Controller
import security.SpongeAuthConfig
import security.totp.TotpAuth
import security.totp.qr.QrCodeRenderer

/**
  * Handles two-factor authentication based functions of the application.
  */
final class TwoFactorAuth @Inject()(override val messagesApi: MessagesApi,
                                    override implicit val users: UserDBO,
                                    totp: TotpAuth,
                                    qrRenderer: QrCodeRenderer,
                                    forms: SpongeAuthForms,
                                    override val cache: CacheApi,
                                    implicit override val config: SpongeAuthConfig)
                                    extends Controller with I18nSupport with Actions {

  val encryptionSecret: String = this.config.play.getString("crypto.secret").get

  /**
    * Generates a new TOTP secret for the authenticated user and displays the
    * setup form for 2-factor authentication. The user must not already have
    * 2FA enabled.
    *
    * @return Setup for for 2FA
    */
  def showSetup() = WithSession { implicit request =>
    val user = request.userSession.user
    if (user.isTotpConfirmed)
      Redirect(Application.showHome())
    else {
      // Generate a URI for the OTP auth
      val encSecret = user.totpSecret.getOrElse(this.users.generateTotpSecret(user).totpSecret.get)
      val secret = decrypt(encSecret, this.encryptionSecret)
      val uri = this.totp.generateUri(user.username, secret)

      // Encode in a QR code
      val totpConf = this.config.totp
      val qrWidth = totpConf.getInt("qr.width").get
      val qrHeight = totpConf.getInt("qr.height").get
      val qrCode = this.qrRenderer.render(uri, qrWidth, qrHeight)

      // Show user result
      Ok(views.html.tfa.setup(qrCode, secret))
    }
  }

  /**
    * Confirms a user's TOTP secret for the first time.
    *
    * @return BadRequest if user has no TOTP secret
    */
  def confirmTotp() = WithSession { implicit request =>
    val session = request.userSession
    val user = session.user
    this.forms.VerifyTotp.bindFromRequest().fold(
      hasErrors =>
        FormError(TwoFactorAuth.showSetup(), hasErrors),
      code => {
        if (!user.isTotpConfirmed && user.totpSecret.isDefined && this.users.verifyTotp(user, code)) {
          this.users.setSessionAuthenticated(session)
          this.users.setTotpConfirmed(user)
          Redirect(Application.showHome())
        } else
          Redirect(TwoFactorAuth.showSetup()).withError("2fa.code.invalid")
      }
    )
  }

  /**
    * Displays the two-factor authentication verification page.
    *
    * @return Verification page
    */
  def showVerification() = WithSession { implicit request =>
    val user = request.userSession.user
    if (!user.isTotpConfirmed)
      Redirect(Application.showHome())
    else
      Ok(views.html.tfa.verify(request.userSession.user))
  }

  /**
    * Verifies a submitted TOTP code and marks the session as authenticated if
    * successful.
    *
    * @return Redirect to home if successful or redirect to verification if
    *         error
    */
  def verifyTotp() = WithSession { implicit request =>
    val session = request.userSession
    var user = session.user
    val maxAttempts = this.config.security.getInt("totp.maxAttempts").get
    if (user.failedTotpAttempts >= maxAttempts)
      BadRequest
    else {
      this.forms.VerifyTotp.bindFromRequest().fold(
        hasErrors =>
          FormError(TwoFactorAuth.showVerification(), hasErrors),
        code => {
          if (user.isTotpConfirmed && user.totpSecret.isDefined && this.users.verifyTotp(user, code)) {
            this.users.setSessionAuthenticated(session)
            Redirect(Application.showHome())
          } else {
            user = this.users.addFailedTotpAttempt(user)
            Redirect(TwoFactorAuth.showVerification()).withError("2fa.code.invalid")
          }
        }
      )
    }
  }

}
