package dev.aster.render

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

/** Software rasterizer for the terrain mesh.
  *
  * The rasterizer owns one reusable `TYPE_INT_RGB` image. Triangles are filled with scanline spans
  * and outlines are drawn with Bresenham lines, so the renderer never needs per-triangle Java2D
  * calls. Coordinates are clipped inside the rasterizer; callers only need to reject wholly
  * off-screen triangles.
  */
final class SoftwareRasterizer {

  private var buffer: BufferedImage = null
  private var pixels: Array[Int] = null
  private var width = 0
  private var height = 0

  /** Recreates the backing image only when the viewport size changes. */
  def ensureSize(newWidth: Int, newHeight: Int): Unit = {
    if (buffer != null && width == newWidth && height == newHeight) return

    buffer = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
    pixels = buffer.getRaster.getDataBuffer
      .asInstanceOf[DataBufferInt]
      .getData
    width = newWidth
    height = newHeight
  }

  def image: BufferedImage = buffer

  def clear(rgb: Int): Unit = {
    var i = 0
    while (i < pixels.length) {
      pixels(i) = rgb
      i += 1
    }
  }

  /** Fills a screen-space triangle using scanline spans. Per row only the two edge intersections
    * are computed, then the whole span is filled in one tight loop.
    */
  def fillTriangle(
      x1in: Int,
      y1in: Int,
      x2in: Int,
      y2in: Int,
      x3in: Int,
      y3in: Int,
      rgb: Int
  ): Unit = {
    var x1 = x1in
    var y1 = y1in
    var x2 = x2in
    var y2 = y2in
    var x3 = x3in
    var y3 = y3in

    // Sort the vertices by y so the triangle has a top and bottom half.
    if (y1 > y2) {
      var t = x1; x1 = x2; x2 = t
      t = y1; y1 = y2; y2 = t
    }
    if (y2 > y3) {
      var t = x2; x2 = x3; x3 = t
      t = y2; y2 = y3; y3 = t
    }
    if (y1 > y2) {
      var t = x1; x1 = x2; x2 = t
      t = y1; y1 = y2; y2 = t
    }

    if (y1 == y3 || y3 < 0 || y1 >= height) return

    val yStart = Math.max(0, Math.ceil(y1 - 0.5).toInt)
    val yEnd = Math.min(height - 1, Math.floor(y3 - 0.5).toInt)
    if (yStart > yEnd) return

    val dyTop = y2 - y1
    val dyBottom = y3 - y2
    val dyLong = y3 - y1
    val dxTop = x2 - x1
    val dxBottom = x3 - x2
    val dxLong = x3 - x1

    var y = yStart
    while (y <= yEnd) {
      val sampleY = y + 0.5
      val xa =
        if (sampleY < y2) x1 + (sampleY - y1) * dxTop / dyTop
        else x2 + (sampleY - y2) * dxBottom / dyBottom
      val xb = x1 + (sampleY - y1) * dxLong / dyLong

      var left = Math.ceil(xa - 0.5).toInt
      var right = Math.floor(xb - 0.5).toInt

      if (left > right) {
        val t = left
        left = right
        right = t
      }
      if (left < 0) left = 0
      if (right >= width) right = width - 1

      if (left <= right) {
        val rowOffset = y * width
        var x = left
        while (x <= right) {
          pixels(rowOffset + x) = rgb
          x += 1
        }
      }
      y += 1
    }
  }

  /** Draws a 1px Bresenham line, clipping it to the viewport first. */
  def drawLine(
      x0in: Int,
      y0in: Int,
      x1in: Int,
      y1in: Int,
      rgb: Int
  ): Unit = {
    var x0 = x0in
    var y0 = y0in
    var x1 = x1in
    var y1 = y1in

    var code0 = outCode(x0, y0)
    var code1 = outCode(x1, y1)

    // Cohen-Sutherland clipping
    var clipping = true
    while (clipping) {
      if ((code0 | code1) == 0) {
        clipping = false
      } else if ((code0 & code1) != 0) {
        return
      } else {
        val code = if (code0 != 0) code0 else code1
        var x = 0.0
        var y = 0.0
        if ((code & 4) != 0) {
          y = 0
          x = x0 + (x1 - x0).toDouble * (0 - y0) / (y1 - y0)
        } else if ((code & 8) != 0) {
          y = height - 1
          x = x0 + (x1 - x0).toDouble * (height - 1 - y0) / (y1 - y0)
        } else if ((code & 1) != 0) {
          x = 0
          y = y0 + (y1 - y0).toDouble * (0 - x0) / (x1 - x0)
        } else {
          x = width - 1
          y = y0 + (y1 - y0).toDouble * (width - 1 - x0) / (x1 - x0)
        }

        val ix = Math.round(x).toInt
        val iy = Math.round(y).toInt
        if (code == code0) {
          x0 = ix
          y0 = iy
          code0 = outCode(x0, y0)
        } else {
          x1 = ix
          y1 = iy
          code1 = outCode(x1, y1)
        }
      }
    }

    val dx = Math.abs(x1 - x0)
    val sx = if (x0 < x1) 1 else -1
    val dy = -Math.abs(y1 - y0)
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy

    var drawing = true
    while (drawing) {
      pixels(y0 * width + x0) = rgb
      if (x0 == x1 && y0 == y1) {
        drawing = false
      } else {
        val e2 = err << 1
        if (e2 >= dy) {
          err += dy
          x0 += sx
        }
        if (e2 <= dx) {
          err += dx
          y0 += sy
        }
      }
    }
  }

  private def outCode(x: Int, y: Int): Int = {
    var code = 0
    if (x < 0) code |= 1
    else if (x >= width) code |= 2
    if (y < 0) code |= 4
    else if (y >= height) code |= 8
    code
  }
}
