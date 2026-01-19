package dev.aster

import java.awt.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Heads-up display showing player stats and mini-map with modern styling
 */
class HeadsUpDisplay {
    companion object {
        private const val HUD_PADDING = 15
        private const val MINI_MAP_SIZE = 200  // Increased from 180
        private const val MINI_MAP_PADDING = 15
        private const val FONT_SIZE = 13f

        private val TEXT_COLOR = Color.WHITE
        private val TEXT_BACKGROUND = Color(0, 0, 0, 210)
        private val MINI_MAP_BACKGROUND = Color(20, 20, 30, 220)
        private val ACCENT_COLOR = Color(100, 150, 255)
    }

    data class HUDData(
        val cameraX: Double,
        val cameraY: Double,
        val cameraZ: Double,
        val fps: Int,
        val terrainWidth: Int,
        val terrainHeight: Int,
        val terrain: Terrain,
        val yaw: Double
    )

    /**
     * Draw the complete HUD on the graphics context
     */
    fun draw(g: Graphics2D, hudData: HUDData, width: Int, height: Int) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Draw statistics panel (top-left)
        drawStatsPanel(g, hudData)

        // Draw mini-map (top-right)
        drawMiniMap(g, hudData, width - MINI_MAP_SIZE - MINI_MAP_PADDING)

        // Draw controls hint (bottom-left)
        drawControlsHint(g, height)

        // Draw compass indicator (top-center)
        drawCompass(g, hudData, width)
    }

    private fun drawStatsPanel(g: Graphics2D, hudData: HUDData) {
        val font = g.font.deriveFont(FONT_SIZE)
        val boldFont = font.deriveFont(Font.BOLD)
        val metrics = g.getFontMetrics(font)

        val stats = listOf(
            "═ POSITION ═",
            "X: ${String.format("%.1f", hudData.cameraX)}",
            "Y: ${String.format("%.1f", hudData.cameraY)}",
            "Z: ${String.format("%.1f", hudData.cameraZ)}",
            "",
            "═ PERFORMANCE ═",
            "FPS: ${hudData.fps}",
            "Dir: ${(hudData.yaw.toInt() + 360) % 360}°"
        )

        val lineHeight = metrics.height + 3
        val panelWidth = stats.maxOf { metrics.stringWidth(it) } + 20
        val panelHeight = stats.size * lineHeight + 15

        // Draw semi-transparent background with border
        g.color = TEXT_BACKGROUND
        g.fillRect(HUD_PADDING, HUD_PADDING, panelWidth, panelHeight)

        g.color = ACCENT_COLOR
        g.stroke = BasicStroke(2f)
        g.drawRect(HUD_PADDING, HUD_PADDING, panelWidth, panelHeight)

        // Draw corner decorations
        g.drawLine(HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 15, HUD_PADDING + 5)
        g.drawLine(HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 15)
        g.drawLine(HUD_PADDING + panelWidth - 15, HUD_PADDING + 5, HUD_PADDING + panelWidth - 5, HUD_PADDING + 5)
        g.drawLine(HUD_PADDING + panelWidth - 5, HUD_PADDING + 5, HUD_PADDING + panelWidth - 5, HUD_PADDING + 15)

        // Draw text
        g.font = font
        g.color = TEXT_COLOR
        stats.forEachIndexed { index, stat ->
            if (stat.startsWith("═")) {
                g.font = boldFont
                g.color = ACCENT_COLOR
            } else if (stat.isEmpty()) {
                g.font = font
            } else {
                g.font = font
                g.color = TEXT_COLOR
            }
            g.drawString(stat, HUD_PADDING + 10, HUD_PADDING + 15 + index * lineHeight)
        }
    }

    private fun drawMiniMap(g: Graphics2D, hudData: HUDData, x: Int) {
        val scale = MINI_MAP_SIZE.toDouble() / maxOf(hudData.terrainWidth, hudData.terrainHeight)

        // Draw background with border
        g.color = MINI_MAP_BACKGROUND
        g.fillRect(x, MINI_MAP_PADDING, MINI_MAP_SIZE, MINI_MAP_SIZE)

        g.color = ACCENT_COLOR
        g.stroke = BasicStroke(2f)
        g.drawRect(x, MINI_MAP_PADDING, MINI_MAP_SIZE, MINI_MAP_SIZE)

        // Draw corner decorations
        g.drawLine(x + 5, MINI_MAP_PADDING + 5, x + 15, MINI_MAP_PADDING + 5)
        g.drawLine(x + 5, MINI_MAP_PADDING + 5, x + 5, MINI_MAP_PADDING + 15)
        g.drawLine(x + MINI_MAP_SIZE - 15, MINI_MAP_PADDING + 5, x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 5)
        g.drawLine(x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 5, x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 15)

        // Draw terrain height map with adaptive sampling
        val sampleRate = maxOf(1, hudData.terrainWidth / (MINI_MAP_SIZE / 3))
        for (ix in 0 until hudData.terrainWidth step sampleRate) {
            for (iy in 0 until hudData.terrainHeight step sampleRate) {
                val height = hudData.terrain.getHeight(ix, iy)
                val color = hudData.terrain.getColorForHeight(height)
                g.color = color

                val screenX = x + (ix * scale).toInt()
                val screenY = MINI_MAP_PADDING + (iy * scale).toInt()
                val pixelSize = maxOf(1, (scale.toInt()))

                g.fillRect(screenX, screenY, pixelSize, pixelSize)
            }
        }

        // Draw player position indicator
        val playerScreenX = x + (hudData.cameraX * scale).toInt()
        val playerScreenY = MINI_MAP_PADDING + (hudData.cameraY * scale).toInt()

        // Draw position crosshair
        g.color = Color(255, 215, 0, 255)
        g.stroke = BasicStroke(2f)
        g.drawOval(playerScreenX - 5, playerScreenY - 5, 10, 10)
        g.drawLine(playerScreenX - 8, playerScreenY, playerScreenX + 8, playerScreenY)
        g.drawLine(playerScreenX, playerScreenY - 8, playerScreenX, playerScreenY + 8)

        // Draw direction indicator (viewing direction)
        // yaw=0 looks along +Y axis, so use sin for X and cos for Y
        val radYaw = Math.toRadians(hudData.yaw)
        val dirLength = 20.0
        val dirX = playerScreenX + (sin(radYaw) * dirLength).toInt()
        val dirY = playerScreenY + (cos(radYaw) * dirLength).toInt()
        g.color = Color(100, 200, 255)
        g.drawLine(playerScreenX, playerScreenY, dirX, dirY)

        // Draw grid lines for reference
        g.color = Color(60, 60, 80, 100)
        g.stroke = BasicStroke(0.5f)
        val gridSpacing = (MINI_MAP_SIZE / 4).coerceAtLeast(10)
        for (i in 0..4) {
            g.drawLine(x + i * gridSpacing, MINI_MAP_PADDING, x + i * gridSpacing, MINI_MAP_PADDING + MINI_MAP_SIZE)
            g.drawLine(x, MINI_MAP_PADDING + i * gridSpacing, x + MINI_MAP_SIZE, MINI_MAP_PADDING + i * gridSpacing)
        }
    }

    private fun drawCompass(g: Graphics2D, hudData: HUDData, width: Int) {
        val centerX = width / 2
        val centerY = 20
        val radius = 15

        val font = g.font.deriveFont(9f)
        g.font = font

        // Draw compass rose background
        g.color = Color(0, 0, 0, 200)
        g.fillOval(centerX - radius - 2, centerY - radius - 2, (radius + 2) * 2, (radius + 2) * 2)

        g.color = ACCENT_COLOR
        g.stroke = BasicStroke(1.5f)
        g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2)

        // Draw cardinal directions
        val directions = listOf(
            Triple("N", 0.0, true),
            Triple("E", 90.0, false),
            Triple("S", 180.0, false),
            Triple("W", 270.0, false)
        )

        directions.forEach { (dir, angle, bold) ->
            val radians = Math.toRadians(angle)
            val x = centerX + (cos(radians) * (radius - 5)).toInt()
            val y = centerY + (sin(radians) * (radius - 5)).toInt()

            g.color = if (bold) Color(255, 100, 100) else TEXT_COLOR
            if (bold) g.font = font.deriveFont(Font.BOLD)
            g.drawString(dir, x - 3, y + 3)
            g.font = font
        }

        // Draw player direction indicator
        val radYaw = Math.toRadians(hudData.yaw)
        val dirX = centerX + (cos(radYaw) * radius * 0.7).toInt()
        val dirY = centerY + (sin(radYaw) * radius * 0.7).toInt()
        g.color = Color(100, 200, 255)
        g.fillOval(dirX - 2, dirY - 2, 4, 4)
        g.drawOval(dirX - 2, dirY - 2, 4, 4)
    }

    private fun drawControlsHint(g: Graphics2D, height: Int) {
        val font = g.font.deriveFont(FONT_SIZE - 2)
        val metrics = g.getFontMetrics(font)

        val controls = listOf(
            "WASD: Move | Mouse: Look | Q/E: Up/Down | Regen: Generate New Terrain"
        )

        val lineHeight = metrics.height + 3
        val panelWidth = metrics.stringWidth(controls[0]) + 20
        val panelHeight = lineHeight + 10

        val x = HUD_PADDING
        val y = height - panelHeight - HUD_PADDING

        // Draw semi-transparent background with border
        g.color = TEXT_BACKGROUND
        g.fillRect(x, y, panelWidth, panelHeight)

        g.color = ACCENT_COLOR
        g.stroke = BasicStroke(1.5f)
        g.drawRect(x, y, panelWidth, panelHeight)

        // Draw corner decorations
        g.drawLine(x + 3, y + 3, x + 10, y + 3)
        g.drawLine(x + 3, y + 3, x + 3, y + 10)
        g.drawLine(x + panelWidth - 10, y + 3, x + panelWidth - 3, y + 3)
        g.drawLine(x + panelWidth - 3, y + 3, x + panelWidth - 3, y + 10)

        // Draw text
        g.font = font
        g.color = TEXT_COLOR
        g.drawString(controls[0], x + 10, y + 18)
    }
}
