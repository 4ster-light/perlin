package dev.aster

import java.awt.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Heads-up display showing player stats and mini-map
 */
class HeadsUpDisplay {
    companion object {
        private const val HUD_PADDING = 10
        private const val MINI_MAP_SIZE = 150
        private const val MINI_MAP_PADDING = 10
        private const val FONT_SIZE = 12f

        private fun textColor() = Color.WHITE
        private fun textBackground() = Color(0, 0, 0, 200)
        private fun miniMapBackground() = Color(30, 30, 30, 200)
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
        drawStatsPanel(g, hudData, HUD_PADDING, HUD_PADDING)

        // Draw mini-map (top-right)
        drawMiniMap(g, hudData, width - MINI_MAP_SIZE - MINI_MAP_PADDING, MINI_MAP_PADDING)

        // Draw controls hint (bottom-left)
        drawControlsHint(g, height)
    }

    private fun drawStatsPanel(g: Graphics2D, hudData: HUDData, x: Int, y: Int) {
        val font = g.font.deriveFont(FONT_SIZE)
        val metrics = g.getFontMetrics(font)

        val stats = listOf(
            "FPS: ${hudData.fps}",
            "X: ${String.format("%.1f", hudData.cameraX)}",
            "Y: ${String.format("%.1f", hudData.cameraY)}",
            "Z: ${String.format("%.1f", hudData.cameraZ)}",
            "Altitude: ${String.format("%.1f", hudData.cameraZ - hudData.terrain.getColorForHeight(hudData.cameraZ).let { 0.0 })}"
        )

        val lineHeight = metrics.height + 2
        val panelWidth = stats.maxOf { metrics.stringWidth(it) } + 20
        val panelHeight = stats.size * lineHeight + 10

        // Draw semi-transparent background
        g.color = textBackground()
        g.fillRect(x, y, panelWidth, panelHeight)

        // Draw border
        g.color = textColor()
        g.drawRect(x, y, panelWidth, panelHeight)

        // Draw text
        g.font = font
        g.color = textColor()
        stats.forEachIndexed { index, stat ->
            g.drawString(stat, x + 10, y + 15 + index * lineHeight)
        }
    }

    private fun drawMiniMap(g: Graphics2D, hudData: HUDData, x: Int, y: Int) {
        val scale = MINI_MAP_SIZE.toDouble() / maxOf(hudData.terrainWidth, hudData.terrainHeight)

        // Draw background
        g.color = miniMapBackground()
        g.fillRect(x, y, MINI_MAP_SIZE, MINI_MAP_SIZE)

        // Draw border
        g.color = textColor()
        g.stroke = BasicStroke(1.5f)
        g.drawRect(x, y, MINI_MAP_SIZE, MINI_MAP_SIZE)

        // Draw terrain height map (simplified)
        val sampleRate = maxOf(1, hudData.terrainWidth / (MINI_MAP_SIZE / 2))
        for (ix in 0 until hudData.terrainWidth step sampleRate) {
            for (iy in 0 until hudData.terrainHeight step sampleRate) {
                val height = hudData.terrain.getHeight(ix, iy)
                val color = hudData.terrain.getColorForHeight(height)
                g.color = color

                val screenX = x + (ix * scale).toInt()
                val screenY = y + (iy * scale).toInt()
                val pixelSize = maxOf(1, (scale.toInt()))

                g.fillRect(screenX, screenY, pixelSize, pixelSize)
            }
        }

        // Draw player position indicator
        val playerScreenX = x + (hudData.cameraX * scale).toInt()
        val playerScreenY = y + (hudData.cameraY * scale).toInt()

        g.color = Color.YELLOW
        g.fillOval(playerScreenX - 3, playerScreenY - 3, 6, 6)

        // Draw direction indicator
        val radYaw = Math.toRadians(hudData.yaw)
        val dirLength = 15.0
        val dirX = playerScreenX + (cos(radYaw) * dirLength).toInt()
        val dirY = playerScreenY + (sin(radYaw) * dirLength).toInt()
        g.drawLine(playerScreenX, playerScreenY, dirX, dirY)
    }

    private fun drawControlsHint(g: Graphics2D, height: Int) {
        val font = g.font.deriveFont(FONT_SIZE - 2)
        val metrics = g.getFontMetrics(font)

        val controls = listOf(
            "Controls: WASD=Move | Mouse=Look | Q/E=Up/Down | Click=Regenerate"
        )

        val lineHeight = metrics.height + 2
        val panelWidth = metrics.stringWidth(controls[0]) + 20
        val panelHeight = lineHeight + 10

        val x = HUD_PADDING
        val y = height - panelHeight - HUD_PADDING

        // Draw semi-transparent background
        g.color = textBackground()
        g.fillRect(x, y, panelWidth, panelHeight)

        // Draw border
        g.color = textColor()
        g.drawRect(x, y, panelWidth, panelHeight)

        // Draw text
        g.font = font
        g.color = textColor()
        g.drawString(controls[0], x + 10, y + 18)
    }
}
