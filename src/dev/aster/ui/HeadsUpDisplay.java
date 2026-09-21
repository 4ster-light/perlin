package dev.aster.ui;

import dev.aster.terrain.Terrain;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Heads-up display showing player stats and mini-map with modern styling.
 */
public final class HeadsUpDisplay {

    private static final int HUD_PADDING = 15;
    private static final int MINI_MAP_SIZE = 200; // Increased from 180
    private static final int MINI_MAP_PADDING = 15;
    private static final float FONT_SIZE = 13f;

    private static final Color TEXT_COLOR = Color.WHITE;
    private static final Color TEXT_BACKGROUND = new Color(0, 0, 0, 210);
    private static final Color MINI_MAP_BACKGROUND = new Color(20, 20, 30, 220);
    private static final Color ACCENT_COLOR = new Color(100, 150, 255);

    /** Snapshot of the state the HUD renders each frame. */
    public record HUDData(
            double cameraX,
            double cameraY,
            double cameraZ,
            int fps,
            int terrainWidth,
            int terrainHeight,
            Terrain terrain,
            double yaw) {}

    /** Draw the complete HUD on the graphics context. */
    public void draw(Graphics2D g, HUDData hudData, int width, int height) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw statistics panel (top-left)
        drawStatsPanel(g, hudData);

        // Draw mini-map (top-right)
        drawMiniMap(g, hudData, width - MINI_MAP_SIZE - MINI_MAP_PADDING);

        // Draw controls hint (bottom-left)
        drawControlsHint(g, height);

        // Draw compass indicator (top-center)
        drawCompass(g, hudData, width);
    }

    private void drawStatsPanel(Graphics2D g, HUDData hudData) {
        Font font = g.getFont().deriveFont(FONT_SIZE);
        Font boldFont = font.deriveFont(Font.BOLD);
        FontMetrics metrics = g.getFontMetrics(font);

        List<String> stats = List.of(
                "═ POSITION ═",
                "X: " + String.format("%.1f", hudData.cameraX()),
                "Y: " + String.format("%.1f", hudData.cameraY()),
                "Z: " + String.format("%.1f", hudData.cameraZ()),
                "",
                "═ PERFORMANCE ═",
                "FPS: " + hudData.fps(),
                "Dir: " + ((int) hudData.yaw() + 360) % 360 + "°");

        int lineHeight = metrics.getHeight() + 3;
        int panelWidth = stats.stream().mapToInt(metrics::stringWidth).max().orElse(0) + 20;
        int panelHeight = stats.size() * lineHeight + 15;

        // Draw semi-transparent background with border
        g.setColor(TEXT_BACKGROUND);
        g.fillRect(HUD_PADDING, HUD_PADDING, panelWidth, panelHeight);

        g.setColor(ACCENT_COLOR);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(HUD_PADDING, HUD_PADDING, panelWidth, panelHeight);

        // Draw corner decorations
        g.drawLine(HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 15, HUD_PADDING + 5);
        g.drawLine(HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 5, HUD_PADDING + 15);
        g.drawLine(HUD_PADDING + panelWidth - 15, HUD_PADDING + 5, HUD_PADDING + panelWidth - 5, HUD_PADDING + 5);
        g.drawLine(HUD_PADDING + panelWidth - 5, HUD_PADDING + 5, HUD_PADDING + panelWidth - 5, HUD_PADDING + 15);

        // Draw text
        for (int index = 0; index < stats.size(); index++) {
            String stat = stats.get(index);
            if (stat.startsWith("═")) {
                g.setFont(boldFont);
                g.setColor(ACCENT_COLOR);
            } else {
                g.setFont(font);
                g.setColor(TEXT_COLOR);
            }
            g.drawString(stat, HUD_PADDING + 10, HUD_PADDING + 15 + index * lineHeight);
        }
    }

    private void drawMiniMap(Graphics2D g, HUDData hudData, int x) {
        double scale = (double) MINI_MAP_SIZE / Math.max(hudData.terrainWidth(), hudData.terrainHeight());

        // Draw background with border
        g.setColor(MINI_MAP_BACKGROUND);
        g.fillRect(x, MINI_MAP_PADDING, MINI_MAP_SIZE, MINI_MAP_SIZE);

        g.setColor(ACCENT_COLOR);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x, MINI_MAP_PADDING, MINI_MAP_SIZE, MINI_MAP_SIZE);

        // Draw corner decorations
        g.drawLine(x + 5, MINI_MAP_PADDING + 5, x + 15, MINI_MAP_PADDING + 5);
        g.drawLine(x + 5, MINI_MAP_PADDING + 5, x + 5, MINI_MAP_PADDING + 15);
        g.drawLine(x + MINI_MAP_SIZE - 15, MINI_MAP_PADDING + 5, x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 5);
        g.drawLine(x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 5, x + MINI_MAP_SIZE - 5, MINI_MAP_PADDING + 15);

        // Draw terrain height map with adaptive sampling
        int sampleRate = Math.max(1, hudData.terrainWidth() / (MINI_MAP_SIZE / 3));
        for (int ix = 0; ix < hudData.terrainWidth(); ix += sampleRate) {
            for (int iy = 0; iy < hudData.terrainHeight(); iy += sampleRate) {
                double height = hudData.terrain().getHeight(ix, iy);
                g.setColor(hudData.terrain().getColorForHeight(height));

                int screenX = x + (int) (ix * scale);
                int screenY = MINI_MAP_PADDING + (int) (iy * scale);
                int pixelSize = Math.max(1, (int) scale);

                g.fillRect(screenX, screenY, pixelSize, pixelSize);
            }
        }

        // Draw player position indicator
        int playerScreenX = x + (int) (hudData.cameraX() * scale);
        int playerScreenY = MINI_MAP_PADDING + (int) (hudData.cameraY() * scale);

        // Draw position crosshair
        g.setColor(new Color(255, 215, 0, 255));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(playerScreenX - 5, playerScreenY - 5, 10, 10);
        g.drawLine(playerScreenX - 8, playerScreenY, playerScreenX + 8, playerScreenY);
        g.drawLine(playerScreenX, playerScreenY - 8, playerScreenX, playerScreenY + 8);

        // Draw direction indicator (viewing direction)
        // yaw=0 looks along +Y axis, so use sin for X and cos for Y
        double radYaw = Math.toRadians(hudData.yaw());
        double dirLength = 20.0;
        int dirX = playerScreenX + (int) (Math.sin(radYaw) * dirLength);
        int dirY = playerScreenY + (int) (Math.cos(radYaw) * dirLength);
        g.setColor(new Color(100, 200, 255));
        g.drawLine(playerScreenX, playerScreenY, dirX, dirY);

        // Draw grid lines for reference
        g.setColor(new Color(60, 60, 80, 100));
        g.setStroke(new BasicStroke(0.5f));
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

        Font font = g.getFont().deriveFont(9f);
        g.setFont(font);

        // Draw compass rose background
        g.setColor(new Color(0, 0, 0, 200));
        g.fillOval(centerX - radius - 2, centerY - radius - 2, (radius + 2) * 2, (radius + 2) * 2);

        g.setColor(ACCENT_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // Draw cardinal directions: label, angle, highlighted
        record Direction(String label, double angle, boolean bold) {}
        List<Direction> directions = List.of(
                new Direction("N", 0.0, true),
                new Direction("E", 90.0, false),
                new Direction("S", 180.0, false),
                new Direction("W", 270.0, false));

        for (Direction direction : directions) {
            double radians = Math.toRadians(direction.angle());
            int x = centerX + (int) (Math.cos(radians) * (radius - 5));
            int y = centerY + (int) (Math.sin(radians) * (radius - 5));

            g.setColor(direction.bold() ? new Color(255, 100, 100) : TEXT_COLOR);
            if (direction.bold()) {
                g.setFont(font.deriveFont(Font.BOLD));
            }
            g.drawString(direction.label(), x - 3, y + 3);
            g.setFont(font);
        }

        // Draw player direction indicator
        double radYaw = Math.toRadians(hudData.yaw());
        int dirX = centerX + (int) (Math.cos(radYaw) * radius * 0.7);
        int dirY = centerY + (int) (Math.sin(radYaw) * radius * 0.7);
        g.setColor(new Color(100, 200, 255));
        g.fillOval(dirX - 2, dirY - 2, 4, 4);
        g.drawOval(dirX - 2, dirY - 2, 4, 4);
    }

    private void drawControlsHint(Graphics2D g, int height) {
        Font font = g.getFont().deriveFont(FONT_SIZE - 2);
        FontMetrics metrics = g.getFontMetrics(font);

        String controls = "WASD: Move | Mouse: Look | Q/E: Up/Down | Regen: Generate New Terrain";

        int lineHeight = metrics.getHeight() + 3;
        int panelWidth = metrics.stringWidth(controls) + 20;
        int panelHeight = lineHeight + 10;

        int x = HUD_PADDING;
        int y = height - panelHeight - HUD_PADDING;

        // Draw semi-transparent background with border
        g.setColor(TEXT_BACKGROUND);
        g.fillRect(x, y, panelWidth, panelHeight);

        g.setColor(ACCENT_COLOR);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(x, y, panelWidth, panelHeight);

        // Draw corner decorations
        g.drawLine(x + 3, y + 3, x + 10, y + 3);
        g.drawLine(x + 3, y + 3, x + 3, y + 10);
        g.drawLine(x + panelWidth - 10, y + 3, x + panelWidth - 3, y + 3);
        g.drawLine(x + panelWidth - 3, y + 3, x + panelWidth - 3, y + 10);

        // Draw text
        g.setFont(font);
        g.setColor(TEXT_COLOR);
        g.drawString(controls, x + 10, y + 18);
    }
}
