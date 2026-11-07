package dev.aster.ui;

import dev.aster.terrain.Terrain;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Heads-up display showing player stats and mini-map with modern styling.
 *
 * <p>The mini-map shows a local window around the player. It is rebuilt only
 * when the player moves far enough to matter, then blitted every frame. Fonts
 * and font metrics are cached for the same reason: the HUD is repainted at the
 * game loop rate and should not allocate or re-rasterize static artwork.
 */
public final class HeadsUpDisplay {

    private static final int HUD_PADDING = 15;
    private static final int MINI_MAP_SIZE = 200; // Increased from 180
    private static final int MINI_MAP_PADDING = 15;
    private static final int MINI_MAP_BLOCK = 4;
    private static final double MINI_MAP_SPAN = 160.0; // world units across
    private static final double MINI_MAP_REBUILD_DISTANCE = 8.0;
    private static final float FONT_SIZE = 13f;

    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TEXT_BACKGROUND = new Color(0, 0, 0, 210);
    private static final Color MINI_MAP_BACKGROUND = new Color(20, 20, 30, 220);
    private static final Color ACCENT_COLOR = new Color(100, 150, 255);
    private static final Color PLAYER_MARKER_COLOR = new Color(255, 215, 0, 255);
    private static final Color DIRECTION_COLOR = new Color(100, 200, 255);
    private static final Color MINI_MAP_GRID_COLOR = new Color(60, 60, 80, 100);
    private static final Color COMPASS_BACKGROUND = new Color(0, 0, 0, 200);
    private static final Color COMPASS_NORTH_COLOR = new Color(255, 100, 100);

    private static final BasicStroke STROKE_2 = new BasicStroke(2f);
    private static final BasicStroke STROKE_1_5 = new BasicStroke(1.5f);
    private static final BasicStroke STROKE_0_5 = new BasicStroke(0.5f);

    private static final String CONTROLS =
            "WASD: Move | Mouse: Look | Q/E: Up/Down | Regen: Generate New Terrain";

    /** Snapshot of the state the HUD renders each frame. */
    public record HUDData(
            double cameraX,
            double cameraY,
            double cameraZ,
            int fps,
            Terrain terrain,
            double yaw) {}

    private record Direction(String label, double angle, boolean bold) {}

    private static final Direction[] DIRECTIONS = {
            new Direction("N", 0.0, true),
            new Direction("E", 90.0, false),
            new Direction("S", 180.0, false),
            new Direction("W", 270.0, false)
    };

    // Cached static artwork / typography
    private Terrain cachedTerrain;
    private BufferedImage cachedMiniMap;
    private double cachedCenterX = Double.NaN;
    private double cachedCenterY = Double.NaN;

    private Font statsFont;
    private Font statsBoldFont;
    private Font controlsFont;
    private Font compassFont;
    private Font compassBoldFont;
    private FontMetrics statsMetrics;
    private FontMetrics controlsMetrics;

    /** Draw the complete HUD on the graphics context. */
    public void draw(Graphics2D g, HUDData hudData, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ensureFonts(g);

        // Draw statistics panel (top-left)
        drawStatsPanel(g, hudData);

        // Draw mini-map (top-right)
        drawMiniMap(g, hudData, width - MINI_MAP_SIZE - MINI_MAP_PADDING);

        // Draw controls hint (bottom-left)
        drawControlsHint(g, height);

        // Draw compass indicator (top-center)
        drawCompass(g, hudData, width);
    }

    private void ensureFonts(Graphics2D g) {
        if (statsFont != null) {
            return;
        }
        Font base = g.getFont();
        statsFont = base.deriveFont(FONT_SIZE);
        statsBoldFont = base.deriveFont(Font.BOLD, FONT_SIZE);
        controlsFont = base.deriveFont(FONT_SIZE - 2);
        compassFont = base.deriveFont(9f);
        compassBoldFont = compassFont.deriveFont(Font.BOLD);
        statsMetrics = g.getFontMetrics(statsFont);
        controlsMetrics = g.getFontMetrics(controlsFont);
    }

    private void drawStatsPanel(Graphics2D g, HUDData hudData) {
        FontMetrics metrics = statsMetrics;

        String[] stats = {
                "═ POSITION ═",
                "X: " + String.format("%.1f", hudData.cameraX()),
                "Y: " + String.format("%.1f", hudData.cameraY()),
                "Z: " + String.format("%.1f", hudData.cameraZ()),
                "",
                "═ PERFORMANCE ═",
                "FPS: " + hudData.fps(),
                "Dir: " + ((int) hudData.yaw() + 360) % 360 + "°"
        };

        int lineHeight = metrics.getHeight() + 3;
        int panelWidth = 0;
        for (String stat : stats) {
            panelWidth = Math.max(panelWidth, metrics.stringWidth(stat));
        }
        panelWidth += 20;
        int panelHeight = stats.length * lineHeight + 15;

        // Draw semi-transparent background with border
        g.setColor(TEXT_BACKGROUND);
        g.fillRect(HUD_PADDING, HUD_PADDING, panelWidth, panelHeight);

        g.setColor(ACCENT_COLOR);
        g.setStroke(STROKE_2);
        g.drawRect(HUD_PADDING, HUD_PADDING, panelWidth, panelHeight);

        // Draw corner decorations
        g.drawLine(HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 15, HUD_PADDING + 5);
        g.drawLine(HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 15);
        g.drawLine(HUD_PADDING + panelWidth - 15, HUD_PADDING + 5, HUD_PADDING + panelWidth - 5, HUD_PADDING + 5);
        g.drawLine(HUD_PADDING + panelWidth - 5, HUD_PADDING + 5, HUD_PADDING + panelWidth - 5, HUD_PADDING + 15);

        // Draw text
        for (int index = 0; index < stats.length; index++) {
            String stat = stats[index];
            if (stat.startsWith("═")) {
                g.setFont(statsBoldFont);
                g.setColor(ACCENT_COLOR);
            } else {
                g.setFont(statsFont);
                g.setColor(TEXT_COLOR);
            }
            g.drawString(stat, HUD_PADDING + 10, HUD_PADDING + 15 + index * lineHeight);
        }
    }

    private void ensureMiniMap(Terrain terrain, double centerX, double centerY) {
        if (terrain == cachedTerrain && cachedMiniMap != null
                && Math.abs(centerX - cachedCenterX) < MINI_MAP_REBUILD_DISTANCE
                && Math.abs(centerY - cachedCenterY) < MINI_MAP_REBUILD_DISTANCE) {
            return;
        }

        BufferedImage image = new BufferedImage(MINI_MAP_SIZE, MINI_MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(MINI_MAP_BACKGROUND);
        g.fillRect(0, 0, MINI_MAP_SIZE, MINI_MAP_SIZE);

        int blocks = MINI_MAP_SIZE / MINI_MAP_BLOCK;
        double worldStep = MINI_MAP_SPAN / blocks;
        double halfSpan = MINI_MAP_SPAN / 2.0;

        for (int by = 0; by < blocks; by++) {
            double worldY = centerY - halfSpan + (by + 0.5) * worldStep;
            int screenY = by * MINI_MAP_BLOCK;
            for (int bx = 0; bx < blocks; bx++) {
                double worldX = centerX - halfSpan + (bx + 0.5) * worldStep;
                int color = terrain.colorIndexAt((int) Math.floor(worldX), (int) Math.floor(worldY));
                g.setColor(Terrain.colorForIndex(color));
                g.fillRect(bx * MINI_MAP_BLOCK, screenY, MINI_MAP_BLOCK, MINI_MAP_BLOCK);
            }
        }

        g.dispose();
        cachedTerrain = terrain;
        cachedMiniMap = image;
        cachedCenterX = centerX;
        cachedCenterY = centerY;
    }

    private void drawMiniMap(Graphics2D g, HUDData hudData, int x) {
        ensureMiniMap(hudData.terrain(), hudData.cameraX(), hudData.cameraY());

        // Background + local terrain raster
        g.drawImage(cachedMiniMap, x, MINI_MAP_PADDING, null);

        g.setColor(ACCENT_COLOR);
        g.setStroke(STROKE_2);
        g.drawRect(x, MINI_MAP_PADDING, MINI_MAP_SIZE, MINI_MAP_SIZE);

        // Draw corner decorations
        g.drawLine(x + 5, MINI_MAP_PADDING + 5, x + 15, MINI_MAP_PADDING + 5);
        g.drawLine(x + 5, MINI_MAP_PADDING + 5, x + 5, MINI_MAP_PADDING + 15);
        g.drawLine(x + MINI_MAP_SIZE - 15, MINI_MAP_PADDING + 5, x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 5);
        g.drawLine(x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 5, x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 15);

        // Draw player position indicator (always the centre of the local map)
        int playerScreenX = x + MINI_MAP_SIZE / 2;
        int playerScreenY = MINI_MAP_PADDING + MINI_MAP_SIZE / 2;

        // Draw position crosshair
        g.setColor(PLAYER_MARKER_COLOR);
        g.setStroke(STROKE_2);
        g.drawOval(playerScreenX - 5, playerScreenY - 5, 10, 10);
        g.drawLine(playerScreenX - 8, playerScreenY, playerScreenX + 8, playerScreenY);
        g.drawLine(playerScreenX, playerScreenY - 8, playerScreenX, playerScreenY + 8);

        // Draw direction indicator (viewing direction)
        // yaw=0 looks along +Y axis, so use sin for X and cos for Y
        double radYaw = Math.toRadians(hudData.yaw());
        double dirLength = 20.0;
        int dirX = playerScreenX + (int) (Math.sin(radYaw) * dirLength);
        int dirY = playerScreenY + (int) (Math.cos(radYaw) * dirLength);
        g.setColor(DIRECTION_COLOR);
        g.drawLine(playerScreenX, playerScreenY, dirX, dirY);

        // Draw grid lines for reference
        g.setColor(MINI_MAP_GRID_COLOR);
        g.setStroke(STROKE_0_5);
        int gridSpacing = Math.max(MINI_MAP_SIZE / 4, 10);
        for (int i = 0; i <= 4; i++) {
            g.drawLine(x + i * gridSpacing, MINI_MAP_PADDING, x + i * gridSpacing, MINI_MAP_PADDING + MINI_MAP_SIZE);
            g.drawLine(x, MINI_MAP_PADDING + i * gridSpacing, x + MINI_MAP_SIZE, MINI_MAP_PADDING + i * gridSpacing);
        }
    }

    private void drawCompass(Graphics2D g, HUDData hudData, int width) {
        int centerX = width / 2;
        int centerY = 20;
        int radius = 15;

        g.setFont(compassFont);

        // Draw compass rose background
        g.setColor(COMPASS_BACKGROUND);
        g.fillOval(centerX - radius - 2, centerY - radius - 2, (radius + 2) * 2, (radius + 2) * 2);

        g.setColor(ACCENT_COLOR);
        g.setStroke(STROKE_1_5);
        g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        for (Direction direction : DIRECTIONS) {
            double radians = Math.toRadians(direction.angle());
            int x = centerX + (int) (Math.cos(radians) * (radius - 5));
            int y = centerY + (int) (Math.sin(radians) * (radius - 5));

            g.setColor(direction.bold() ? COMPASS_NORTH_COLOR : TEXT_COLOR);
            g.setFont(direction.bold() ? compassBoldFont : compassFont);
            g.drawString(direction.label(), x - 3, y + 3);
        }
        g.setFont(compassFont);

        // Draw player direction indicator
        double radYaw = Math.toRadians(hudData.yaw());
        int dirX = centerX + (int) (Math.cos(radYaw) * radius * 0.7);
        int dirY = centerY + (int) (Math.sin(radYaw) * radius * 0.7);
        g.setColor(DIRECTION_COLOR);
        g.fillOval(dirX - 2, dirY - 2, 4, 4);
        g.drawOval(dirX - 2, dirY - 2, 4, 4);
    }

    private void drawControlsHint(Graphics2D g, int height) {
        Font font = controlsFont;
        FontMetrics metrics = controlsMetrics;

        int lineHeight = metrics.getHeight() + 3;
        int panelWidth = metrics.stringWidth(CONTROLS) + 20;
        int panelHeight = lineHeight + 10;

        int x = HUD_PADDING;
        int y = height - panelHeight - HUD_PADDING;

        // Draw semi-transparent background with border
        g.setColor(TEXT_BACKGROUND);
        g.fillRect(x, y, panelWidth, panelHeight);

        g.setColor(ACCENT_COLOR);
        g.setStroke(STROKE_1_5);
        g.drawRect(x, y, panelWidth, panelHeight);

        // Draw corner decorations
        g.drawLine(x + 3, y + 3, x + 10, y + 3);
        g.drawLine(x + 3, y + 3, x + 3, y + 10);
        g.drawLine(x + panelWidth - 10, y + 3, x + panelWidth - 3, y + 3);
        g.drawLine(x + panelWidth - 3, y + 3, x + panelWidth - 3, y + 10);

        // Draw text
        g.setFont(font);
        g.setColor(TEXT_COLOR);
        g.drawString(CONTROLS, x + 10, y + 18);
    }
}
