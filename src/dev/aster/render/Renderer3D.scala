package dev.aster.render

import dev.aster.camera.{Camera, CollisionDetector}
import dev.aster.terrain.Terrain
import dev.aster.ui.HeadsUpDisplay

import java.awt.{
  BasicStroke,
  Color,
  Cursor,
  Dimension,
  Font,
  Graphics,
  Graphics2D,
  Point,
  RenderingHints,
  Robot,
  Toolkit
}
import java.awt.event.{
  ActionEvent,
  ActionListener,
  KeyEvent,
  KeyListener,
  MouseAdapter,
  MouseEvent,
  MouseMotionAdapter
}
import java.awt.image.{BufferedImage, DataBufferInt}
import java.util.Arrays
import javax.swing.{JPanel, Timer}
import scala.collection.mutable

object Renderer3D {
  val RENDER_DISTANCE = 120

  private val FOV = 70.0
  private val NEAR_PLANE = 0.5
  private val FAR_PLANE = RENDER_DISTANCE * 2.0
  private val FRAME_DELAY_MS = 16

  private val CROSSHAIR_STROKE = new BasicStroke(2.0f)
  private val INSTRUCTIONS_FONT = new Font("Arial", Font.BOLD, 14)
  private val INSTRUCTIONS_BACKGROUND = new Color(0, 0, 0, 180)
  private val SKY_RGB = 0x87ceeb // (135, 206, 235)
}

/** Software-rendered first-person terrain view with mouse look and WASD movement.
  *
  * Movement and collision are delegated to [[Camera]] and [[CollisionDetector]]; visibility culling
  * to [[ViewFrustum]]. This class only owns projection and rasterization.
  *
  * The hot frame path is allocation-free: vertices are projected once into reusable primitive
  * arrays, triangles are packed into primitive arrays and depth-sorted with a primitive
  * `Array[Long]`, fills use scanline spans and wireframes use Bresenham lines, and the final frame
  * is blitted from one reusable pixel buffer.
  */
final class Renderer3D(private val terrain: Terrain) extends JPanel with KeyListener {

  import Renderer3D.*

  private var lastFrameTime = System.currentTimeMillis()
  private var frameCount = 0
  private var currentFPS = 0

  private val camera =
    new Camera(terrain.spawnX, terrain.spawnY, 0.0, 0.0, 0.0)
  private val collision = new CollisionDetector(terrain)
  private val hud = new HeadsUpDisplay()

  private var robot: Robot = null
  private var mouseCaptured = false

  private val keysPressed = mutable.HashSet.empty[Int]

  private val frustum = new ViewFrustum(FOV, 1.0, NEAR_PLANE, FAR_PLANE)

  private val maxRegionWidth = RENDER_DISTANCE * 2 + 1
  private val maxRegionHeight = RENDER_DISTANCE * 2 + 1
  private val maxVertices = maxRegionWidth * maxRegionHeight

  private val vertexScreenX = new Array[Int](maxVertices)
  private val vertexScreenY = new Array[Int](maxVertices)
  private val vertexDepth = new Array[Float](maxVertices)
  private val vertexColor = new Array[Byte](maxVertices)
  private val vertexValid = new Array[Boolean](maxVertices)
  private val columnCos = new Array[Double](maxRegionWidth)
  private val columnSin = new Array[Double](maxRegionWidth)
  private val rowCos = new Array[Double](maxRegionHeight)
  private val rowSin = new Array[Double](maxRegionHeight)

  private val maxTriangles = maxVertices * 2
  private val triangleVertices = new Array[Int](maxTriangles * 6)
  private val triangleColor = new Array[Byte](maxTriangles)
  private val triangleDepth = new Array[Float](maxTriangles)
  private val sortKeys = new Array[Long](maxTriangles)

  private var terrainBuffer: BufferedImage = null
  private var terrainPixels: Array[Int] = null
  private var bufferWidth = 0
  private var bufferHeight = 0

  private val frameTimer = new Timer(
    FRAME_DELAY_MS,
    new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        update()
        repaint()
      }
    }
  )

  locally {
    collision.adjustCameraPosition(camera)

    setPreferredSize(new Dimension(1200, 800))
    setBackground(new Color(135, 206, 235))

    setFocusable(true)
    addKeyListener(this)
    requestFocusInWindow()

    try {
      robot = new Robot()
    } catch {
      case e: Exception =>
        println("Could not create Robot for mouse capture: " + e.getMessage)
    }

    addMouseListener(new MouseAdapter {
      override def mouseClicked(e: MouseEvent): Unit = {
        if (!mouseCaptured) captureMouse()
      }
    })

    addMouseMotionListener(new MouseMotionAdapter {
      override def mouseMoved(e: MouseEvent): Unit = {
        if (mouseCaptured && robot != null) {
          val centerX = getWidth / 2
          val centerY = getHeight / 2

          val dx = e.getX - centerX
          val dy = e.getY - centerY

          if (dx != 0 || dy != 0) {
            // Raw pixel deltas; Camera.rotate applies sensitivity.
            // Negative deltaY keeps the demo's inverted pitch feel.
            camera.rotate(dx, -dy)

            val loc = getLocationOnScreen
            robot.mouseMove(loc.x + centerX, loc.y + centerY)
          }
        }
      }
    })

    frameTimer.setCoalesce(true)
  }

  override def addNotify(): Unit = {
    super.addNotify()
    frameTimer.start()
    requestFocusInWindow()
  }

  override def removeNotify(): Unit = {
    frameTimer.stop()
    super.removeNotify()
  }

  private def captureMouse(): Unit = {
    mouseCaptured = true
    setCursor(
      Toolkit.getDefaultToolkit.createCustomCursor(
        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        new Point(0, 0),
        "blank"
      )
    )
    if (robot != null) {
      val loc = getLocationOnScreen
      robot.mouseMove(loc.x + getWidth / 2, loc.y + getHeight / 2)
    }
  }

  private def releaseMouse(): Unit = {
    mouseCaptured = false
    setCursor(Cursor.getDefaultCursor)
  }

  private def update(): Unit = {
    camera.setInputKey(Camera.InputKey.FORWARD, keysPressed.contains(KeyEvent.VK_W))
    camera.setInputKey(Camera.InputKey.BACKWARD, keysPressed.contains(KeyEvent.VK_S))
    camera.setInputKey(Camera.InputKey.LEFT, keysPressed.contains(KeyEvent.VK_A))
    camera.setInputKey(Camera.InputKey.RIGHT, keysPressed.contains(KeyEvent.VK_D))
    camera.setInputKey(
      Camera.InputKey.UP,
      keysPressed.contains(KeyEvent.VK_Q) || keysPressed.contains(KeyEvent.VK_SPACE)
    )
    camera.setInputKey(
      Camera.InputKey.DOWN,
      keysPressed.contains(KeyEvent.VK_E) || keysPressed.contains(KeyEvent.VK_SHIFT)
    )

    camera.update()
    collision.clampCameraPosition(camera)
    collision.adjustCameraPosition(camera)
  }

  override protected def paintComponent(g: Graphics): Unit = {
    super.paintComponent(g)

    frameCount += 1
    val now = System.currentTimeMillis()
    if (now - lastFrameTime >= 1000) {
      currentFPS = frameCount
      frameCount = 0
      lastFrameTime = now
    }

    val g2d = g.asInstanceOf[Graphics2D]

    // Terrain is rendered without antialiasing, matching the original
    // off-screen buffer path. AA is kept for the HUD only.
    g2d.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_OFF
    )
    renderTerrain(g2d)
    g2d.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON
    )

    val hudData = HeadsUpDisplay.HUDData(
      camera.x,
      camera.y,
      camera.z,
      currentFPS,
      terrain,
      camera.yaw
    )
    hud.draw(g2d, hudData, getWidth, getHeight)

    g2d.setColor(Color.WHITE)
    g2d.setStroke(CROSSHAIR_STROKE)
    val cx = getWidth / 2
    val cy = getHeight / 2
    g2d.drawLine(cx - 10, cy, cx + 10, cy)
    g2d.drawLine(cx, cy - 10, cx, cy + 10)

    if (!mouseCaptured) {
      g2d.setColor(INSTRUCTIONS_BACKGROUND)
      g2d.fillRect(getWidth / 2 - 150, getHeight / 2 + 30, 300, 30)
      g2d.setColor(Color.WHITE)
      g2d.setFont(INSTRUCTIONS_FONT)
      g2d.drawString(
        "Click to capture mouse (ESC to release)",
        getWidth / 2 - 130,
        getHeight / 2 + 50
      )
    }
  }

  private def renderTerrain(g: Graphics2D): Unit = {
    val screenWidth = getWidth
    val screenHeight = getHeight
    if (screenWidth <= 0 || screenHeight <= 0) return

    ensureTerrainBuffer(screenWidth, screenHeight)
    Arrays.fill(terrainPixels, SKY_RGB)

    val yawRad = Math.toRadians(camera.yaw)
    val pitchRad = Math.toRadians(camera.pitch)
    val cosYaw = Math.cos(yawRad)
    val sinYaw = Math.sin(yawRad)
    val cosPitch = Math.cos(pitchRad)
    val sinPitch = Math.sin(pitchRad)

    val fovScale = 1.0 / Math.tan(Math.toRadians(FOV / 2.0))
    val halfWidth = screenWidth / 2.0
    val halfHeight = screenHeight / 2.0
    val scaleX = fovScale * halfWidth
    val scaleY = fovScale * halfHeight

    val camX = camera.x
    val camY = camera.y
    val camZ = camera.z

    val camGridX = Math.floor(camX).toInt
    val camGridY = Math.floor(camY).toInt

    val xStart = camGridX - RENDER_DISTANCE
    val xEnd = camGridX + RENDER_DISTANCE
    val yStart = camGridY - RENDER_DISTANCE
    val yEnd = camGridY + RENDER_DISTANCE

    // Generate any chunks that entered the view since the last frame.
    terrain.ensureRegionLoaded(xStart, yStart, xEnd, yEnd)

    val regionWidth = xEnd - xStart + 1
    val regionHeight = yEnd - yStart + 1
    val stride = regionWidth

    var col = 0
    while (col < regionWidth) {
      val dx = (xStart + col) - camX
      columnCos(col) = dx * cosYaw
      columnSin(col) = dx * sinYaw
      col += 1
    }

    var row = 0
    while (row < regionHeight) {
      val dy = (yStart + row) - camY
      rowCos(row) = dy * cosYaw
      rowSin(row) = dy * sinYaw
      row += 1
    }

    // Project each vertex once (shared cell corners are not re-transformed).
    col = 0
    while (col < regionWidth) {
      val gx = xStart + col
      val cx = columnCos(col)
      val sx = columnSin(col)
      val base = col
      row = 0
      while (row < regionHeight) {
        val vi = base + row * stride
        val gy = yStart + row
        val wz = terrain.heightAt(gx, gy)
        val rx = cx - rowSin(row)
        val ry = sx + rowCos(row)
        val dz = wz - camZ
        val depth = ry * cosPitch - dz * sinPitch

        if (frustum.isDepthInFrustum(depth)) {
          val finalZ = ry * sinPitch + dz * cosPitch
          val invDepth = 1.0 / depth
          vertexScreenX(vi) = (halfWidth + rx * invDepth * scaleX).toInt
          vertexScreenY(vi) = (halfHeight - finalZ * invDepth * scaleY).toInt
          vertexDepth(vi) = depth.toFloat
          vertexColor(vi) = terrain.colorIndexAt(gx, gy).toByte
          vertexValid(vi) = true
        } else {
          vertexValid(vi) = false
        }
        row += 1
      }
      col += 1
    }

    // Build screen-space triangles, rejecting off-screen ones immediately.
    var triangleCount = 0
    col = 0
    while (col < regionWidth - 1) {
      row = 0
      while (row < regionHeight - 1) {
        val i1 = col + row * stride
        val i2 = i1 + stride // (gx + 1, gy)
        val i3 = i1 + 1 // (gx, gy + 1)
        val i4 = i2 + 1 // (gx + 1, gy + 1)

        if (vertexValid(i1) && vertexValid(i2) && vertexValid(i3)) {
          triangleCount = addTriangle(triangleCount, i1, i2, i3, screenWidth, screenHeight)
        }
        if (vertexValid(i2) && vertexValid(i4) && vertexValid(i3)) {
          triangleCount = addTriangle(triangleCount, i2, i4, i3, screenWidth, screenHeight)
        }
        row += 1
      }
      col += 1
    }

    if (triangleCount > 0) {
      var i = 0
      while (i < triangleCount) {
        sortKeys(i) = (java.lang.Float.floatToIntBits(triangleDepth(i)).toLong << 32) |
          i.toLong
        i += 1
      }
      Arrays.sort(sortKeys, 0, triangleCount)

      // Iterate backwards so the farthest triangle is drawn first.
      var k = triangleCount - 1
      while (k >= 0) {
        val tri = sortKeys(k).toInt
        val offset = tri * 6
        val colorIndex = triangleColor(tri).toInt

        val x1 = triangleVertices(offset)
        val y1 = triangleVertices(offset + 1)
        val x2 = triangleVertices(offset + 2)
        val y2 = triangleVertices(offset + 3)
        val x3 = triangleVertices(offset + 4)
        val y3 = triangleVertices(offset + 5)

        fillTriangle(x1, y1, x2, y2, x3, y3, Terrain.rgbForIndex(colorIndex))

        val wire = Terrain.wireRgbForIndex(colorIndex)
        drawLine(x1, y1, x2, y2, wire)
        drawLine(x2, y2, x3, y3, wire)
        drawLine(x3, y3, x1, y1, wire)
        k -= 1
      }
    }

    g.drawImage(terrainBuffer, 0, 0, null)
  }

  private def ensureTerrainBuffer(width: Int, height: Int): Unit = {
    if (
      terrainBuffer != null &&
      bufferWidth == width &&
      bufferHeight == height
    ) return

    terrainBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    terrainPixels = terrainBuffer.getRaster.getDataBuffer
      .asInstanceOf[DataBufferInt]
      .getData
    bufferWidth = width
    bufferHeight = height
  }

  /** Fills a screen-space triangle using scanline spans. Per row only the two edge intersections
    * are computed, then the whole span is filled with one Arrays.fill call.
    */
  private def fillTriangle(
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

    if (y1 == y3 || y3 < 0 || y1 >= bufferHeight) return

    val yStart = Math.max(0, Math.ceil(y1 - 0.5).toInt)
    val yEnd = Math.min(bufferHeight - 1, Math.floor(y3 - 0.5).toInt)
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
      var xb = x1 + (sampleY - y1) * dxLong / dyLong

      var left = Math.ceil(xa - 0.5).toInt
      var right = Math.floor(xb - 0.5).toInt

      if (left > right) {
        val t = left
        left = right
        right = t
      }
      if (left < 0) left = 0
      if (right >= bufferWidth) right = bufferWidth - 1

      if (left <= right) {
        val rowOffset = y * bufferWidth
        Arrays.fill(terrainPixels, rowOffset + left, rowOffset + right + 1, rgb)
      }
      y += 1
    }
  }

  /** Draws a 1px Bresenham line, clipping it to the viewport first. */
  private def drawLine(
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
          y = bufferHeight - 1
          x = x0 + (x1 - x0).toDouble * (bufferHeight - 1 - y0) / (y1 - y0)
        } else if ((code & 1) != 0) {
          x = 0
          y = y0 + (y1 - y0).toDouble * (0 - x0) / (x1 - x0)
        } else {
          x = bufferWidth - 1
          y = y0 + (y1 - y0).toDouble * (bufferWidth - 1 - x0) / (x1 - x0)
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

    var dx = Math.abs(x1 - x0)
    val sx = if (x0 < x1) 1 else -1
    var dy = -Math.abs(y1 - y0)
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy

    var drawing = true
    while (drawing) {
      terrainPixels(y0 * bufferWidth + x0) = rgb
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
    else if (x >= bufferWidth) code |= 2
    if (y < 0) code |= 4
    else if (y >= bufferHeight) code |= 8
    code
  }

  /** Stores one projected triangle, first rejecting it when its screen-space bounding box does not
    * overlap the viewport.
    *
    * @return
    *   the new triangle count
    */
  private def addTriangle(
      count: Int,
      i1: Int,
      i2: Int,
      i3: Int,
      screenWidth: Int,
      screenHeight: Int
  ): Int = {
    val x1 = vertexScreenX(i1)
    val y1 = vertexScreenY(i1)
    val x2 = vertexScreenX(i2)
    val y2 = vertexScreenY(i2)
    val x3 = vertexScreenX(i3)
    val y3 = vertexScreenY(i3)

    val minX = Math.min(x1, Math.min(x2, x3))
    val maxX = Math.max(x1, Math.max(x2, x3))
    val minY = Math.min(y1, Math.min(y2, y3))
    val maxY = Math.max(y1, Math.max(y2, y3))

    if (maxX < 0 || minX >= screenWidth || maxY < 0 || minY >= screenHeight) {
      return count
    }

    // Keep the original margin cull for visual parity. It rejects triangles
    // with a vertex more than 500px outside the viewport.
    if (
      x1 < -500 || x1 > screenWidth + 500 || y1 < -500 || y1 > screenHeight + 500 ||
      x2 < -500 || x2 > screenWidth + 500 || y2 < -500 || y2 > screenHeight + 500 ||
      x3 < -500 || x3 > screenWidth + 500 || y3 < -500 || y3 > screenHeight + 500
    ) {
      return count
    }

    val offset = count * 6
    triangleVertices(offset) = x1
    triangleVertices(offset + 1) = y1
    triangleVertices(offset + 2) = x2
    triangleVertices(offset + 3) = y2
    triangleVertices(offset + 4) = x3
    triangleVertices(offset + 5) = y3

    triangleDepth(count) = (vertexDepth(i1) + vertexDepth(i2) + vertexDepth(i3)) / 3.0f

    // Pick a majority surface color among the corners so biome borders do not
    // create a directional grid pattern.
    val c1 = vertexColor(i1).toInt
    val c2 = vertexColor(i2).toInt
    val c3 = vertexColor(i3).toInt
    val color = if (c1 == c2 || c1 == c3) c1 else if (c2 == c3) c2 else c1
    triangleColor(count) = color.toByte

    count + 1
  }

  override def keyPressed(e: KeyEvent): Unit = {
    keysPressed.add(e.getKeyCode)
    if (e.getKeyCode == KeyEvent.VK_ESCAPE) {
      releaseMouse()
    }
  }

  override def keyReleased(e: KeyEvent): Unit = {
    keysPressed.remove(e.getKeyCode)
  }

  override def keyTyped(e: KeyEvent): Unit = ()
}
