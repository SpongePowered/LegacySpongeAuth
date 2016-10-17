package security.totp.qr

import java.awt.Color
import java.awt.image.BufferedImage
import javax.inject.Inject

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

trait QrCodeRenderer {

  val writer = new QRCodeWriter

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
