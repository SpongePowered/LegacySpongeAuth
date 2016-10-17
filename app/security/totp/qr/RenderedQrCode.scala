package security.totp.qr

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO
import javax.xml.bind.DatatypeConverter

case class RenderedQrCode(underlying: BufferedImage) {

  val DefaultFormat = "png"

  def toDataUri(format: String = DefaultFormat) = {
    val out = new ByteArrayOutputStream
    ImageIO.write(this.underlying, format, out)
    s"data:image/$format;base64,${DatatypeConverter.printBase64Binary(out.toByteArray)}"
  }

  def writeToFile(path: Path, format: String = DefaultFormat) = {
    val out = Files.newOutputStream(path)
    ImageIO.write(this.underlying, format, out)
    out.flush()
    out.close()
  }

}
