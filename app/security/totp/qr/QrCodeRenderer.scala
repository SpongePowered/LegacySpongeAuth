package security.totp.qr

import java.awt.Color
import java.awt.image.BufferedImage
import javax.inject.Inject

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
  * Helper class for rendering string content as a QR Code.
  */
trait QrCodeRenderer {

  val writer = new QRCodeWriter

  /**
    * Renders the specified content string as an image with the specified
    * width, height and background color. If no backrgound color is specified,
    * white will be used.
    *
    * @param content          Content to encode
    * @param width            Image width
    * @param height           Image height
    * @param backgroundColor  QR background
    * @return                 A new [[RenderedQrCode]]
    */
  def render(content: String, width: Int, height: Int, backgroundColor: Color = Color.WHITE) = {
    val matrix = this.writer.encode(content, BarcodeFormat.QR_CODE, width, height)
    val mWidth = matrix.getWidth
    val mHeight = matrix.getHeight
    val image = new BufferedImage(mWidth, mHeight, BufferedImage.TYPE_INT_RGB)
    for (y <- 0 until mHeight) {
      for (x <- 0 until mWidth) {
        val color = if (matrix.get(x, y)) 0 else backgroundColor.getRGB
        image.setRGB(x, y, color)
      }
    }
    RenderedQrCode(image)
  }

}

class QrCodeRendererImpl @Inject() extends QrCodeRenderer
