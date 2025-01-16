package dev.aster

import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.math.*

/**
 * First-person 3D renderer with Minecraft-style camera controls
 */
class Renderer3D(private val terrain: Terrain) : JPanel(), KeyListener {
    
    // Camera position in world space
    private var camX = terrain.width / 2.0
    private var camY = terrain.height / 2.0
    private var camZ = 0.0  // Will be set to terrain height + offset
    
    // Camera angles (in degrees)
    private var yaw = 0.0    // Left-right rotation (0 = looking along +Y axis)
    private var pitch = 0.0  // Up-down rotation (0 = horizontal, negative = looking down)
    
    // Movement state
    private val keysPressed = mutableSetOf<Int>()
    
    // Mouse control
    private var robot: Robot? = null
    private var mouseCaptured = false
    
    // Rendering
    private var lastFrameTime = System.currentTimeMillis()
    private var frameCount = 0
    private var currentFPS = 0
    
    private val hud = HeadsUpDisplay()
    
    companion object {
        private const val MOVE_SPEED = 0.8
        private const val VERTICAL_SPEED = 0.5
        private const val MOUSE_SENSITIVITY = 0.15
        private const val PLAYER_HEIGHT = 3.0  // Height above terrain
        private const val FOV = 70.0
        private const val RENDER_DISTANCE = 120
    }
    
    init {
        preferredSize = Dimension(1200, 800)
        background = Color(135, 206, 235)
        
        isFocusable = true
        addKeyListener(this)
        
        // Initialize camera height
        camZ = getTerrainHeight(camX, camY) + PLAYER_HEIGHT
        
        // Try to create robot for mouse capture
        try {
            robot = Robot()
        } catch (e: Exception) {
            println("Could not create Robot for mouse capture: ${e.message}")
        }
        
        // Mouse click to capture
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (!mouseCaptured) {
                    captureMouse()
                }
            }
        })
        
        // Mouse motion for looking around
        addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseMoved(e: java.awt.event.MouseEvent) {
                if (mouseCaptured && robot != null) {
                    val centerX = width / 2
                    val centerY = height / 2
                    
                    val dx = e.x - centerX
                    val dy = e.y - centerY
                    
                    if (dx != 0 || dy != 0) {
                        // Update camera angles
                        yaw += dx * MOUSE_SENSITIVITY
                        pitch += dy * MOUSE_SENSITIVITY  // Inverted: mouse up = look up
                        
                        // Clamp pitch to prevent flipping
                        pitch = pitch.coerceIn(-89.0, 89.0)
                        
                        // Normalize yaw
                        yaw = ((yaw % 360.0) + 360.0) % 360.0
                        
                        // Reset mouse to center
                        val loc = locationOnScreen
                        robot?.mouseMove(loc.x + centerX, loc.y + centerY)
                    }
                }
            }
        })
        
        // Game loop
        Thread {
            while (true) {
                update()
                repaint()
                Thread.sleep(16)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
    
    private fun captureMouse() {
        mouseCaptured = true
        cursor = Toolkit.getDefaultToolkit().createCustomCursor(
            BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
            Point(0, 0),
            "blank"
        )
        // Center mouse
        robot?.let {
            val loc = locationOnScreen
            it.mouseMove(loc.x + width / 2, loc.y + height / 2)
        }
    }
    
    private fun releaseMouse() {
        mouseCaptured = false
        cursor = Cursor.getDefaultCursor()
    }
    
    private fun update() {
        // Calculate forward and right vectors based on yaw only (for movement)
        val yawRad = Math.toRadians(yaw)
        val forwardX = sin(yawRad)
        val forwardY = cos(yawRad)
        val rightX = cos(yawRad)
        val rightY = -sin(yawRad)
        
        // Handle movement
        if (KeyEvent.VK_W in keysPressed) {
            camX += forwardX * MOVE_SPEED
            camY += forwardY * MOVE_SPEED
        }
        if (KeyEvent.VK_S in keysPressed) {
            camX -= forwardX * MOVE_SPEED
            camY -= forwardY * MOVE_SPEED
        }
        if (KeyEvent.VK_A in keysPressed) {
            camX -= rightX * MOVE_SPEED
            camY -= rightY * MOVE_SPEED
        }
        if (KeyEvent.VK_D in keysPressed) {
            camX += rightX * MOVE_SPEED
            camY += rightY * MOVE_SPEED
        }
        if (KeyEvent.VK_Q in keysPressed || KeyEvent.VK_SPACE in keysPressed) {
            camZ += VERTICAL_SPEED
        }
        if (KeyEvent.VK_E in keysPressed || KeyEvent.VK_SHIFT in keysPressed) {
            camZ -= VERTICAL_SPEED
        }
        
        // Clamp position to terrain bounds
        camX = camX.coerceIn(5.0, terrain.width - 5.0)
        camY = camY.coerceIn(5.0, terrain.height - 5.0)
        
        // Keep above terrain
        val terrainHeight = getTerrainHeight(camX, camY)
        if (camZ < terrainHeight + PLAYER_HEIGHT) {
            camZ = terrainHeight + PLAYER_HEIGHT
        }
        
        // FPS counter
        frameCount++
        val now = System.currentTimeMillis()
        if (now - lastFrameTime >= 1000) {
            currentFPS = frameCount
            frameCount = 0
            lastFrameTime = now
        }
    }
    
    private fun getTerrainHeight(x: Double, y: Double): Double {
        val xi = x.toInt().coerceIn(0, terrain.width - 2)
        val yi = y.toInt().coerceIn(0, terrain.height - 2)
        val xf = x - xi
        val yf = y - yi
        
        val h00 = terrain.getHeight(xi, yi)
        val h10 = terrain.getHeight(xi + 1, yi)
        val h01 = terrain.getHeight(xi, yi + 1)
        val h11 = terrain.getHeight(xi + 1, yi + 1)
        
        val h0 = h00 * (1 - xf) + h10 * xf
        val h1 = h01 * (1 - xf) + h11 * xf
        
        return h0 * (1 - yf) + h1 * yf
    }
    
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2d = g as Graphics2D
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        
        // Create buffer
        val buffer = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val bg = buffer.createGraphics()
        bg.color = background
        bg.fillRect(0, 0, width, height)
        
        // Render terrain
        renderTerrain(bg)
        
        bg.dispose()
        g2d.drawImage(buffer, 0, 0, null)
        
        // Draw HUD
        val hudData = HeadsUpDisplay.HUDData(
            camX, camY, camZ,
            currentFPS,
            terrain.width, terrain.height,
            terrain,
            yaw
        )
        hud.draw(g2d, hudData, width, height)
        
        // Draw crosshair
        g2d.color = Color.WHITE
        g2d.stroke = BasicStroke(2f)
        val cx = width / 2
        val cy = height / 2
        g2d.drawLine(cx - 10, cy, cx + 10, cy)
        g2d.drawLine(cx, cy - 10, cx, cy + 10)
        
        // Instructions if mouse not captured
        if (!mouseCaptured) {
            g2d.color = Color(0, 0, 0, 180)
            g2d.fillRect(width / 2 - 150, height / 2 + 30, 300, 30)
            g2d.color = Color.WHITE
            g2d.font = Font("Arial", Font.BOLD, 14)
            g2d.drawString("Click to capture mouse (ESC to release)", width / 2 - 130, height / 2 + 50)
        }
    }
    
    private fun renderTerrain(g: Graphics2D) {
        // Precompute camera transform values
        val yawRad = Math.toRadians(yaw)
        val pitchRad = Math.toRadians(pitch)
        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        
        val fovScale = 1.0 / tan(Math.toRadians(FOV / 2.0))
        val aspectRatio = width.toDouble() / height.toDouble()
        
        data class ProjectedTriangle(
            val x1: Int, val y1: Int,
            val x2: Int, val y2: Int,
            val x3: Int, val y3: Int,
            val depth: Double,
            val color: Color
        )
        
        val triangles = mutableListOf<ProjectedTriangle>()
        
        val camGridX = camX.toInt()
        val camGridY = camY.toInt()
        
        for (gx in maxOf(0, camGridX - RENDER_DISTANCE) until minOf(terrain.width - 1, camGridX + RENDER_DISTANCE)) {
            for (gy in maxOf(0, camGridY - RENDER_DISTANCE) until minOf(terrain.height - 1, camGridY + RENDER_DISTANCE)) {
                
                // Get the 4 corners of this grid cell
                val wx1 = gx.toDouble()
                val wy1 = gy.toDouble()
                val wz1 = terrain.getHeight(gx, gy)
                
                val wx2 = (gx + 1).toDouble()
                val wy2 = gy.toDouble()
                val wz2 = terrain.getHeight(gx + 1, gy)
                
                val wx3 = gx.toDouble()
                val wy3 = (gy + 1).toDouble()
                val wz3 = terrain.getHeight(gx, gy + 1)
                
                val wx4 = (gx + 1).toDouble()
                val wy4 = (gy + 1).toDouble()
                val wz4 = terrain.getHeight(gx + 1, gy + 1)
                
                // Project all 4 points
                val p1 = projectPoint(wx1, wy1, wz1, cosYaw, sinYaw, cosPitch, sinPitch, fovScale, aspectRatio)
                val p2 = projectPoint(wx2, wy2, wz2, cosYaw, sinYaw, cosPitch, sinPitch, fovScale, aspectRatio)
                val p3 = projectPoint(wx3, wy3, wz3, cosYaw, sinYaw, cosPitch, sinPitch, fovScale, aspectRatio)
                val p4 = projectPoint(wx4, wy4, wz4, cosYaw, sinYaw, cosPitch, sinPitch, fovScale, aspectRatio)
                
                // Triangle 1: p1, p2, p3
                if (p1 != null && p2 != null && p3 != null) {
                    val avgHeight = (wz1 + wz2 + wz3) / 3.0
                    val depth = (p1.third + p2.third + p3.third) / 3.0
                    triangles.add(ProjectedTriangle(
                        p1.first, p1.second,
                        p2.first, p2.second,
                        p3.first, p3.second,
                        depth,
                        terrain.getColorForHeight(avgHeight)
                    ))
                }
                
                // Triangle 2: p2, p4, p3
                if (p2 != null && p4 != null && p3 != null) {
                    val avgHeight = (wz2 + wz4 + wz3) / 3.0
                    val depth = (p2.third + p4.third + p3.third) / 3.0
                    triangles.add(ProjectedTriangle(
                        p2.first, p2.second,
                        p4.first, p4.second,
                        p3.first, p3.second,
                        depth,
                        terrain.getColorForHeight(avgHeight)
                    ))
                }
            }
        }
        
        // Sort by depth (far to near - painter's algorithm)
        triangles.sortByDescending { it.depth }
        
        // Draw triangles
        for (tri in triangles) {
            g.color = tri.color
            g.fillPolygon(
                intArrayOf(tri.x1, tri.x2, tri.x3),
                intArrayOf(tri.y1, tri.y2, tri.y3),
                3
            )
            // Wireframe for definition
            g.color = tri.color.darker()
            g.drawPolygon(
                intArrayOf(tri.x1, tri.x2, tri.x3),
                intArrayOf(tri.y1, tri.y2, tri.y3),
                3
            )
        }
    }
    
    /**
     * Projects a world point to screen coordinates using first-person camera transform
     * Returns Triple(screenX, screenY, depth) or null if behind camera
     */
    private fun projectPoint(
        wx: Double, wy: Double, wz: Double,
        cosYaw: Double, sinYaw: Double,
        cosPitch: Double, sinPitch: Double,
        fovScale: Double, aspectRatio: Double
    ): Triple<Int, Int, Double>? {
        
        // Translate to camera-relative coordinates
        val dx = wx - camX
        val dy = wy - camY
        val dz = wz - camZ
        
        // Rotate around Z axis (yaw - left/right)
        // This rotates the world so camera looks along +Y after rotation
        val rx = dx * cosYaw - dy * sinYaw
        val ry = dx * sinYaw + dy * cosYaw
        val rz = dz
        
        // Rotate around X axis (pitch - up/down)
        // ry becomes our "forward" direction (depth)
        val depth = ry * cosPitch - rz * sinPitch
        val finalZ = ry * sinPitch + rz * cosPitch
        
        // Behind camera
        if (depth < 0.5) return null
        
        // Perspective projection
        val screenX = (width / 2.0 + (rx / depth) * fovScale * width / 2.0).toInt()
        val screenY = (height / 2.0 - (finalZ / depth) * fovScale * height / 2.0).toInt()
        
        // Frustum culling
        if (screenX < -500 || screenX > width + 500 || screenY < -500 || screenY > height + 500) {
            return null
        }
        
        return Triple(screenX, screenY, depth)
    }
    
    override fun keyPressed(e: KeyEvent) {
        keysPressed.add(e.keyCode)
        
        // ESC to release mouse
        if (e.keyCode == KeyEvent.VK_ESCAPE) {
            releaseMouse()
        }
    }
    
    override fun keyReleased(e: KeyEvent) {
        keysPressed.remove(e.keyCode)
    }
    
    override fun keyTyped(e: KeyEvent) {}
}
