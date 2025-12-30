package dev.aster

import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.math.cos
import kotlin.math.sin

class Renderer3D(private val terrain: Terrain) : JPanel() {
    // Camera settings
    private var rotationX = 80.0
    private var rotationZ = 45.0
    private var zoom = 6.0
    private var offsetX = 0.0
    private var baseOffsetY = 100.0
    private var cameraDistance = 300.0

    // Mouse interaction
    private var lastMouseX = 0
    private var lastMouseY = 0
    private var isDragging = false

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
        background = Color(135, 206, 250)

        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) {
                lastMouseX = e.x
                lastMouseY = e.y
                isDragging = true
            }

            override fun mouseReleased(e: java.awt.event.MouseEvent) {
                isDragging = false
            }
        })

        addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: java.awt.event.MouseEvent) {
                if (isDragging) {
                    val dx = e.x - lastMouseX
                    val dy = e.y - lastMouseY

                    rotationZ -= dx * 0.5
                    rotationX -= dy * 0.3

                    rotationX = rotationX.coerceIn(40.0, 89.0)

                    lastMouseX = e.x
                    lastMouseY = e.y
                    repaint()
                }
            }
        })

        addMouseWheelListener { e ->
            zoom *= if (e.wheelRotation < 0) 1.1 else 0.9
            zoom = zoom.coerceIn(0.8, 15.0)
            repaint()
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val buffer = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val bg = buffer.createGraphics()
        bg.color = background
        bg.fillRect(0, 0, width, height)

        val triangles = mutableListOf<Triangle>()
        for (x in 0 until terrain.width - 1) {
            for (y in 0 until terrain.height - 1) {
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
        bg.dispose()

        g2d.drawImage(buffer, 0, 0, null)

        g2d.color = Color.BLACK
        g2d.drawString("Seed: ${(terrain.hashCode())}", 10, 20)
    }

    private fun project3D(x: Double, y: Double, z: Double): Point3D? {
        val centerX = terrain.width / 2.0
        val centerY = terrain.height / 2.0

        var px = x - centerX
        var py = y - centerY
        var pz = z

        val radZ = Math.toRadians(rotationZ)
        val tx = px * cos(radZ) - py * sin(radZ)
        val ty = px * sin(radZ) + py * cos(radZ)
        px = tx
        py = ty

        val radX = Math.toRadians(rotationX)
        val ty2 = py * cos(radX) - pz * sin(radX)
        val tz2 = py * sin(radX) + pz * cos(radX)
        py = ty2
        pz = tz2

        pz += cameraDistance

        if (pz <= 10) return null

        val perspective = 800.0
        val scale = perspective / (perspective + pz)

        val dynamicOffsetY = baseOffsetY + (zoom - 6.0) * 10.0

        val screenX = (width / 2 + px * scale * zoom + offsetX).toInt()
        val screenY = (height / 2 + py * scale * zoom + dynamicOffsetY).toInt()

        return Point3D(screenX, screenY, pz)
    }
}
