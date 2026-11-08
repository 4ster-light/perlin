package perlin.render

import perlin.camera.Camera
import perlin.camera.CollisionDetector
import perlin.terrain.Terrain
import perlin.ui.HeadsUpDisplay

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.Timer
import scala.collection.mutable

object Renderer3D {
  val RENDER_DISTANCE = 120

  private val FOV = 70.0
  private val NEAR_PLANE = 0.5
  private val FAR_PLANE = RENDER_DISTANCE * 2.0
  private val FRAME_DELAY_MS = 16
  private val DEPTH_BUCKET_COUNT = 65536
  private val DEPTH_BUCKET_SCALE = (DEPTH_BUCKET_COUNT - 1).toDouble / FAR_PLANE

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
  * arrays, triangles are packed into primitive arrays and depth-sorted with a counting sort over
  * depth buckets, fills use scanline spans and wireframes use Bresenham lines, and the final frame
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
  private val triangleBucket = new Array[Int](maxTriangles)
  private val sortedTriangles = new Array[Int](maxTriangles)
  private val bucketCounts = new Array[Int](DEPTH_BUCKET_COUNT)

  private val rasterizer = new SoftwareRasterizer

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

    rasterizer.ensureSize(screenWidth, screenHeight)
    rasterizer.clear(SKY_RGB)

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
      sortTrianglesByDepth(triangleCount)

      // Iterate backwards so the farthest triangle is drawn first.
      var k = triangleCount - 1
      while (k >= 0) {
        val tri = sortedTriangles(k)
        val offset = tri * 6
        val colorIndex = triangleColor(tri).toInt

        val x1 = triangleVertices(offset)
        val y1 = triangleVertices(offset + 1)
        val x2 = triangleVertices(offset + 2)
        val y2 = triangleVertices(offset + 3)
        val x3 = triangleVertices(offset + 4)
        val y3 = triangleVertices(offset + 5)

        rasterizer.fillTriangle(x1, y1, x2, y2, x3, y3, Terrain.rgbForIndex(colorIndex))

        val wire = Terrain.wireRgbForIndex(colorIndex)
        rasterizer.drawLine(x1, y1, x2, y2, wire)
        rasterizer.drawLine(x2, y2, x3, y3, wire)
        rasterizer.drawLine(x3, y3, x1, y1, wire)
        k -= 1
      }
    }

    g.drawImage(rasterizer.image, 0, 0, null)
  }

  /** Counting sort by depth bucket. Buckets are ordered near-to-far, so drawing them backwards
    * yields painter's-algorithm order without comparison sorting.
    */
  private def sortTrianglesByDepth(triangleCount: Int): Unit = {
    var bucket = 0
    while (bucket < DEPTH_BUCKET_COUNT) {
      bucketCounts(bucket) = 0
      bucket += 1
    }

    var i = 0
    while (i < triangleCount) {
      bucketCounts(triangleBucket(i)) += 1
      i += 1
    }

    var offset = 0
    bucket = 0
    while (bucket < DEPTH_BUCKET_COUNT) {
      val count = bucketCounts(bucket)
      bucketCounts(bucket) = offset
      offset += count
      bucket += 1
    }

    i = 0
    while (i < triangleCount) {
      val bucketIndex = triangleBucket(i)
      val position = bucketCounts(bucketIndex)
      sortedTriangles(position) = i
      bucketCounts(bucketIndex) = position + 1
      i += 1
    }
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

    val depth = (vertexDepth(i1) + vertexDepth(i2) + vertexDepth(i3)) / 3.0f
    var bucket = (depth * DEPTH_BUCKET_SCALE).toInt
    if (bucket < 0) bucket = 0
    else if (bucket >= DEPTH_BUCKET_COUNT) bucket = DEPTH_BUCKET_COUNT - 1
    triangleBucket(count) = bucket

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
