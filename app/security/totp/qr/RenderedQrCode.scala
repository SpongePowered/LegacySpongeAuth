package security.totp.qr

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO
import javax.xml.bind.DatatypeConverter

/**
  * A wrapper around a [[BufferedImage]] to represent a rendered QR code.
  *
  * @param underlying Underlying image
  */
case class RenderedQrCode(underlying: BufferedImage) {

  final val DefaultFormat = "png"

  /**
    * Returns the underlying image as a Base64 data URI that can be set to an
    * img tag's src.
    *
    * @param format Image format
    * @return       Encoded data URI
    */
  def toDataUri(format: String = DefaultFormat) = {
    val out = new ByteArrayOutputStream
    ImageIO.write(this.underlying, format, out)
    s"data:image/$format;base64,${DatatypeConverter.printBase64Binary(out.toByteArray)}"
  }

  /**
    * Writes the underlying image to the specified path.
    *
    * @param path   Path to write image to
    * @param format Image format
    */
  def writeToFile(path: Path, format: String = DefaultFormat) = {
    val out = Files.newOutputStream(path)
    ImageIO.write(this.underlying, format, out)
    out.flush()
    out.close()
  }

}
