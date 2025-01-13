package dev.aster

import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.math.cos
import kotlin.math.sin

class Renderer3D(private val terrain: Terrain) : JPanel(), KeyListener {
    private val camera = Camera(
        x = terrain.width / 2.0,
        y = terrain.height / 2.0,
        z = terrain.getHeight(terrain.width / 2, terrain.height / 2) + 10.0
    )

    private val collisionDetector = CollisionDetector(terrain)
    private val viewFrustum = ViewFrustum(aspectRatio = 800.0 / 600.0)
    private val hud = HeadsUpDisplay()

    // Rendering stats
    private var lastFrameTime = System.currentTimeMillis()
    private var frameCount = 0
    private var currentFPS = 0

    // FOV and perspective settings
    private companion object {
        private const val FOV = 90.0
        private const val NEAR_PLANE = 0.1
        private const val FAR_PLANE = 1000.0
    }

    private data class Point3D(
        val x: Int,
        val y: Int,
        val z: Double
    )

    private data class Triangle(
        val p1: Point3D,
        val p2: Point3D,
        val p3: Point3D,
        val color: Color,
        val avgZ: Double
    )

    init {
        preferredSize = Dimension(800, 600)
        background = Color(100, 150, 200) // Sky blue

        isFocusable = true
        addKeyListener(this)
        requestFocusInWindow()

        // Add mouse listener for camera look
        addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            private var lastMouseX = 0
            private var lastMouseY = 0

            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                val deltaX = (e.x - lastMouseX).toDouble()
                val deltaY = (e.y - lastMouseY).toDouble()
                camera.rotate(deltaX, deltaY)
                lastMouseX = e.x
                lastMouseY = e.y
                repaint()
            }
        })

        // Game loop
        Thread {
            while (true) {
                updateFrame()
                Thread.sleep(16) // ~60 FPS
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun updateFrame() {
        camera.update()
        collisionDetector.adjustCameraPosition(camera)
        collisionDetector.clampCameraPosition(camera)

        // Update FPS counter
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameTime >= 1000) {
            currentFPS = frameCount
            frameCount = 0
            lastFrameTime = currentTime
        }

        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val buffer = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val bg = buffer.createGraphics()
        bg.color = background
        bg.fillRect(0, 0, width, height)

        // Render terrain
        renderTerrain(bg)

        bg.dispose()
        g2d.drawImage(buffer, 0, 0, null)

        // Draw HUD on top
        val hudData = HeadsUpDisplay.HUDData(
            camera.x, camera.y, camera.z,
            currentFPS,
            terrain.width, terrain.height,
            terrain,
            camera.yaw
        )
        hud.draw(g2d, hudData, width, height)
    }

    private fun renderTerrain(bg: Graphics2D) {
        val triangles = mutableListOf<Triangle>()

        // Render only nearby terrain for better performance
        val renderDistance = 80
        val camGridX = camera.x.toInt()
        val camGridY = camera.y.toInt()

        for (x in maxOf(0, camGridX - renderDistance) until minOf(terrain.width - 1, camGridX + renderDistance)) {
            for (y in maxOf(0, camGridY - renderDistance) until minOf(terrain.height - 1, camGridY + renderDistance)) {
                val p1 = project3D(x.toDouble(), y.toDouble(), terrain.getHeight(x, y))
                val p2 = project3D((x + 1).toDouble(), y.toDouble(), terrain.getHeight(x + 1, y))
                val p3 = project3D(x.toDouble(), (y + 1).toDouble(), terrain.getHeight(x, y + 1))
                val p4 = project3D((x + 1).toDouble(), (y + 1).toDouble(), terrain.getHeight(x + 1, y + 1))

                if (p1 != null && p2 != null && p3 != null && p4 != null) {
                    val h1 = (terrain.getHeight(x, y)
                            + terrain.getHeight(x + 1, y)
                            + terrain.getHeight(x, y + 1)) / 3
                    val h2 = (terrain.getHeight(x + 1, y)
                            + terrain.getHeight(x, y + 1)
                            + terrain.getHeight(x + 1, y + 1)) / 3

                    triangles.add(Triangle(p1, p2, p3, terrain.getColorForHeight(h1), p1.z + p2.z + p3.z))
                    triangles.add(Triangle(p2, p4, p3, terrain.getColorForHeight(h2), p2.z + p4.z + p3.z))
                }
            }
        }

        triangles.sortBy { it.avgZ }

        for (triangle in triangles) {
            bg.color = triangle.color
            val xPoints = intArrayOf(triangle.p1.x, triangle.p2.x, triangle.p3.x)
            val yPoints = intArrayOf(triangle.p1.y, triangle.p2.y, triangle.p3.y)
            bg.fillPolygon(xPoints, yPoints, 3)

            bg.color = triangle.color.darker()
            bg.drawPolygon(xPoints, yPoints, 3)
        }
    }

    private fun project3D(worldX: Double, worldY: Double, worldZ: Double): Point3D? {
        // Translate world coordinates relative to camera
        var px = worldX - camera.x
        var py = worldY - camera.y
        var pz = worldZ - camera.z

        // Rotate based on camera yaw (horizontal rotation)
        val radYaw = Math.toRadians(camera.yaw)
        val rotatedX = px * cos(radYaw) - py * sin(radYaw)
        val rotatedY = px * sin(radYaw) + py * cos(radYaw)
        px = rotatedX
        py = rotatedY

        // Rotate based on camera pitch (vertical rotation)
        val radPitch = Math.toRadians(camera.pitch)
        val rotatedY2 = py * cos(radPitch) - pz * sin(radPitch)
        val rotatedZ = py * sin(radPitch) + pz * cos(radPitch)
        py = rotatedY2
        pz = rotatedZ

        // Perspective projection
        if (pz <= NEAR_PLANE) return null

        val fovRadians = Math.toRadians(FOV / 2.0)
        val scale = 1.0 / kotlin.math.tan(fovRadians)

        val screenX = (width / 2 + px * scale * width / 800).toInt()
        val screenY = (height / 2 - py * scale * height / 600).toInt()

        // Cull points outside screen
        if (screenX < -100 || screenX > width + 100 || screenY < -100 || screenY > height + 100) {
            return null
        }

        return Point3D(screenX, screenY, pz)
    }

    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_W -> camera.setInputKey(Camera.InputKey.FORWARD, true)
            KeyEvent.VK_S -> camera.setInputKey(Camera.InputKey.BACKWARD, true)
            KeyEvent.VK_A -> camera.setInputKey(Camera.InputKey.LEFT, true)
            KeyEvent.VK_D -> camera.setInputKey(Camera.InputKey.RIGHT, true)
            KeyEvent.VK_Q -> camera.setInputKey(Camera.InputKey.UP, true)
            KeyEvent.VK_E -> camera.setInputKey(Camera.InputKey.DOWN, true)
        }
    }

    override fun keyReleased(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_W -> camera.setInputKey(Camera.InputKey.FORWARD, false)
            KeyEvent.VK_S -> camera.setInputKey(Camera.InputKey.BACKWARD, false)
            KeyEvent.VK_A -> camera.setInputKey(Camera.InputKey.LEFT, false)
            KeyEvent.VK_D -> camera.setInputKey(Camera.InputKey.RIGHT, false)
            KeyEvent.VK_Q -> camera.setInputKey(Camera.InputKey.UP, false)
            KeyEvent.VK_E -> camera.setInputKey(Camera.InputKey.DOWN, false)
        }
    }

    override fun keyTyped(e: KeyEvent) {}
}
