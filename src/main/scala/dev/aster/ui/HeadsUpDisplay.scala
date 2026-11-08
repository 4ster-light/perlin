package dev.aster.ui

import dev.aster.terrain.Terrain

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

object HeadsUpDisplay {

  final case class HUDData(
      cameraX: Double,
      cameraY: Double,
      cameraZ: Double,
      fps: Int,
      terrain: Terrain,
      yaw: Double
  )

  private final case class Direction(label: String, angle: Double, bold: Boolean)

  private val DIRECTIONS = Array(
    Direction("N", 0.0, true),
    Direction("E", 90.0, false),
    Direction("S", 180.0, false),
    Direction("W", 270.0, false)
  )

  private val HUD_PADDING = 15
  private val MINI_MAP_SIZE = 200
  private val MINI_MAP_PADDING = 15
  private val MINI_MAP_BLOCK = 4
  private val MINI_MAP_SPAN = 160.0 // world units across
  private val MINI_MAP_REBUILD_DISTANCE = 8.0
  private val FONT_SIZE = 13.0f

  private val TEXT_COLOR = Color.WHITE
  private val TEXT_BACKGROUND = new Color(0, 0, 0, 210)
  private val MINI_MAP_BACKGROUND = new Color(20, 20, 30, 220)
  private val ACCENT_COLOR = new Color(100, 150, 255)
  private val PLAYER_MARKER_COLOR = new Color(255, 215, 0, 255)
  private val DIRECTION_COLOR = new Color(100, 200, 255)
  private val MINI_MAP_GRID_COLOR = new Color(60, 60, 80, 100)
  private val COMPASS_BACKGROUND = new Color(0, 0, 0, 200)
  private val COMPASS_NORTH_COLOR = new Color(255, 100, 100)

  private val STROKE_2 = new BasicStroke(2.0f)
  private val STROKE_1_5 = new BasicStroke(1.5f)
  private val STROKE_0_5 = new BasicStroke(0.5f)

  private val CONTROLS =
    "WASD: Move | Mouse: Look | Q/E: Up/Down | Regen: Generate New Terrain"
}

/** Heads-up display showing player stats and a local mini-map.
  *
  * The mini-map shows a local window around the player. It is rebuilt only when the player moves
  * far enough to matter, then blitted every frame. Fonts and font metrics are cached for the same
  * reason: the HUD is repainted at the game loop rate and should not allocate or re-rasterize
  * static artwork.
  */
final class HeadsUpDisplay {

  import HeadsUpDisplay.*

  private var cachedTerrain: Terrain = null
  private var cachedMiniMap: BufferedImage = null
  private var cachedCenterX = Double.NaN
  private var cachedCenterY = Double.NaN

  private var statsFont: Font = null
  private var statsBoldFont: Font = null
  private var controlsFont: Font = null
  private var compassFont: Font = null
  private var compassBoldFont: Font = null
  private var statsMetrics: FontMetrics = null
  private var controlsMetrics: FontMetrics = null

  /** Draw the complete HUD on the graphics context. */
  def draw(g: Graphics2D, hudData: HUDData, width: Int, height: Int): Unit = {
    g.setRenderingHint(
      RenderingHints.KEY_ANTIALIASING,
      RenderingHints.VALUE_ANTIALIAS_ON
    )
    ensureFonts(g)

    drawStatsPanel(g, hudData)
    drawMiniMap(g, hudData, width - MINI_MAP_SIZE - MINI_MAP_PADDING)
    drawControlsHint(g, height)
    drawCompass(g, hudData, width)
  }

  private def ensureFonts(g: Graphics2D): Unit = {
    if (statsFont != null) return

    val base = g.getFont
    statsFont = base.deriveFont(FONT_SIZE)
    statsBoldFont = base.deriveFont(Font.BOLD, FONT_SIZE)
    controlsFont = base.deriveFont(FONT_SIZE - 2)
    compassFont = base.deriveFont(9.0f)
    compassBoldFont = compassFont.deriveFont(Font.BOLD)
    statsMetrics = g.getFontMetrics(statsFont)
    controlsMetrics = g.getFontMetrics(controlsFont)
  }

  private def drawStatsPanel(g: Graphics2D, hudData: HUDData): Unit = {
    val metrics = statsMetrics

    val stats = Array(
      "═ POSITION ═",
      "X: " + format1(hudData.cameraX),
      "Y: " + format1(hudData.cameraY),
      "Z: " + format1(hudData.cameraZ),
      "",
      "═ PERFORMANCE ═",
      "FPS: " + hudData.fps,
      s"Dir: ${((hudData.yaw.toInt + 360) % 360)}°"
    )

    val lineHeight = metrics.getHeight + 3
    var panelWidth = 0
    var i = 0
    while (i < stats.length) {
      panelWidth = Math.max(panelWidth, metrics.stringWidth(stats(i)))
      i += 1
    }
    panelWidth += 20
    val panelHeight = stats.length * lineHeight + 15

    g.setColor(TEXT_BACKGROUND)
    g.fillRect(HUD_PADDING, HUD_PADDING, panelWidth, panelHeight)

    g.setColor(ACCENT_COLOR)
    g.setStroke(STROKE_2)
    g.drawRect(HUD_PADDING, HUD_PADDING, panelWidth, panelHeight)

    g.drawLine(HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 15, HUD_PADDING + 5)
    g.drawLine(HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 15)
    g.drawLine(
      HUD_PADDING + panelWidth - 15,
      HUD_PADDING + 5,
      HUD_PADDING + panelWidth - 5,
      HUD_PADDING + 5
    )
    g.drawLine(
      HUD_PADDING + panelWidth - 5,
      HUD_PADDING + 5,
      HUD_PADDING + panelWidth - 5,
      HUD_PADDING + 15
    )

    i = 0
    while (i < stats.length) {
      val stat = stats(i)
      if (stat.startsWith("═")) {
        g.setFont(statsBoldFont)
        g.setColor(ACCENT_COLOR)
      } else {
        g.setFont(statsFont)
        g.setColor(TEXT_COLOR)
      }
      g.drawString(stat, HUD_PADDING + 10, HUD_PADDING + 15 + i * lineHeight)
      i += 1
    }
  }

  private def format1(value: Double): String =
    "%.1f".format(value)

  private def ensureMiniMap(terrain: Terrain, centerX: Double, centerY: Double): Unit = {
    if (
      terrain == cachedTerrain &&
      cachedMiniMap != null &&
      Math.abs(centerX - cachedCenterX) < MINI_MAP_REBUILD_DISTANCE &&
      Math.abs(centerY - cachedCenterY) < MINI_MAP_REBUILD_DISTANCE
    ) {
      return
    }

    val image = new BufferedImage(
      MINI_MAP_SIZE,
      MINI_MAP_SIZE,
      BufferedImage.TYPE_INT_ARGB
    )
    val g = image.createGraphics()
    g.setColor(MINI_MAP_BACKGROUND)
    g.fillRect(0, 0, MINI_MAP_SIZE, MINI_MAP_SIZE)

    val blocks = MINI_MAP_SIZE / MINI_MAP_BLOCK
    val worldStep = MINI_MAP_SPAN / blocks
    val halfSpan = MINI_MAP_SPAN / 2.0

    var by = 0
    while (by < blocks) {
      val worldY = centerY - halfSpan + (by + 0.5) * worldStep
      val screenY = by * MINI_MAP_BLOCK
      var bx = 0
      while (bx < blocks) {
        val worldX = centerX - halfSpan + (bx + 0.5) * worldStep
        val color = terrain.colorIndexAt(
          Math.floor(worldX).toInt,
          Math.floor(worldY).toInt
        )
        g.setColor(Terrain.colorForIndex(color))
        g.fillRect(bx * MINI_MAP_BLOCK, screenY, MINI_MAP_BLOCK, MINI_MAP_BLOCK)
        bx += 1
      }
      by += 1
    }

    g.dispose()
    cachedTerrain = terrain
    cachedMiniMap = image
    cachedCenterX = centerX
    cachedCenterY = centerY
  }

  private def drawMiniMap(g: Graphics2D, hudData: HUDData, x: Int): Unit = {
    ensureMiniMap(hudData.terrain, hudData.cameraX, hudData.cameraY)

    g.drawImage(cachedMiniMap, x, MINI_MAP_PADDING, null)

    g.setColor(ACCENT_COLOR)
    g.setStroke(STROKE_2)
    g.drawRect(x, MINI_MAP_PADDING, MINI_MAP_SIZE, MINI_MAP_SIZE)

    g.drawLine(x + 5, MINI_MAP_PADDING + 5, x + 15, MINI_MAP_PADDING + 5)
    g.drawLine(x + 5, MINI_MAP_PADDING + 5, x + 5, MINI_MAP_PADDING + 15)
    g.drawLine(
      x + MINI_MAP_SIZE - 15,
      MINI_MAP_PADDING + 5,
      x + MINI_MAP_SIZE - 5,
      MINI_MAP_PADDING + 5
    )
    g.drawLine(
      x + MINI_MAP_SIZE - 5,
      MINI_MAP_PADDING + 5,
      x + MINI_MAP_SIZE - 5,
      MINI_MAP_PADDING + 15
    )

    val playerScreenX = x + MINI_MAP_SIZE / 2
    val playerScreenY = MINI_MAP_PADDING + MINI_MAP_SIZE / 2

    g.setColor(PLAYER_MARKER_COLOR)
    g.setStroke(STROKE_2)
    g.drawOval(playerScreenX - 5, playerScreenY - 5, 10, 10)
    g.drawLine(playerScreenX - 8, playerScreenY, playerScreenX + 8, playerScreenY)
    g.drawLine(playerScreenX, playerScreenY - 8, playerScreenX, playerScreenY + 8)

    // yaw=0 looks along +Y, so use sin for X and cos for Y.
    val radYaw = Math.toRadians(hudData.yaw)
    val dirLength = 20.0
    val dirX = playerScreenX + (Math.sin(radYaw) * dirLength).toInt
    val dirY = playerScreenY + (Math.cos(radYaw) * dirLength).toInt
    g.setColor(DIRECTION_COLOR)
    g.drawLine(playerScreenX, playerScreenY, dirX, dirY)

    g.setColor(MINI_MAP_GRID_COLOR)
    g.setStroke(STROKE_0_5)
    val gridSpacing = Math.max(MINI_MAP_SIZE / 4, 10)
    var i = 0
    while (i <= 4) {
      g.drawLine(
        x + i * gridSpacing,
        MINI_MAP_PADDING,
        x + i * gridSpacing,
        MINI_MAP_PADDING + MINI_MAP_SIZE
      )
      g.drawLine(
        x,
        MINI_MAP_PADDING + i * gridSpacing,
        x + MINI_MAP_SIZE,
        MINI_MAP_PADDING + i * gridSpacing
      )
      i += 1
    }
  }

  private def drawCompass(g: Graphics2D, hudData: HUDData, width: Int): Unit = {
    val centerX = width / 2
    val centerY = 20
    val radius = 15

    g.setFont(compassFont)

    g.setColor(COMPASS_BACKGROUND)
    g.fillOval(
      centerX - radius - 2,
      centerY - radius - 2,
      (radius + 2) * 2,
      (radius + 2) * 2
    )

    g.setColor(ACCENT_COLOR)
    g.setStroke(STROKE_1_5)
    g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2)

    var i = 0
    while (i < DIRECTIONS.length) {
      val direction = DIRECTIONS(i)
      val radians = Math.toRadians(direction.angle)
      val x = centerX + (Math.cos(radians) * (radius - 5)).toInt
      val y = centerY + (Math.sin(radians) * (radius - 5)).toInt

      g.setColor(if direction.bold then COMPASS_NORTH_COLOR else TEXT_COLOR)
      g.setFont(if direction.bold then compassBoldFont else compassFont)
      g.drawString(direction.label, x - 3, y + 3)
      i += 1
    }
    g.setFont(compassFont)

    val radYaw = Math.toRadians(hudData.yaw)
    val dirX = centerX + (Math.cos(radYaw) * radius * 0.7).toInt
    val dirY = centerY + (Math.sin(radYaw) * radius * 0.7).toInt
    g.setColor(DIRECTION_COLOR)
    g.fillOval(dirX - 2, dirY - 2, 4, 4)
    g.drawOval(dirX - 2, dirY - 2, 4, 4)
  }

  private def drawControlsHint(g: Graphics2D, height: Int): Unit = {
    val font = controlsFont
    val metrics = controlsMetrics

    val lineHeight = metrics.getHeight + 3
    val panelWidth = metrics.stringWidth(CONTROLS) + 20
    val panelHeight = lineHeight + 10

    val x = HUD_PADDING
    val y = height - panelHeight - HUD_PADDING

    g.setColor(TEXT_BACKGROUND)
    g.fillRect(x, y, panelWidth, panelHeight)

    g.setColor(ACCENT_COLOR)
    g.setStroke(STROKE_1_5)
    g.drawRect(x, y, panelWidth, panelHeight)

    g.drawLine(x + 3, y + 3, x + 10, y + 3)
    g.drawLine(x + 3, y + 3, x + 3, y + 10)
    g.drawLine(x + panelWidth - 10, y + 3, x + panelWidth - 3, y + 3)
    g.drawLine(x + panelWidth - 3, y + 3, x + panelWidth - 3, y + 10)

    g.setFont(font)
    g.setColor(TEXT_COLOR)
    g.drawString(CONTROLS, x + 10, y + 18)
  }
}
