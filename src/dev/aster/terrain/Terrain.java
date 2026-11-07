package dev.aster.terrain;

import dev.aster.noise.PerlinNoise;

import java.awt.Color;

/**
 * Height-map based terrain grid with height-banded coloring.
 *
 * <p>The height map is stored as a single flat, row-major array
 * ({@code index = x * height + y}) instead of a {@code double[][]}. That keeps
 * the hot rendering loops (which walk columns then rows) cache friendly and
 * removes one pointer indirection per lookup. The height-band colors are
 * cached as static {@link Color} instances, so the renderer never allocates a
 * {@code Color} per triangle.
 */
public final class Terrain {

    private static final Color[] HEIGHT_COLORS = {
            new Color(30, 80, 180),    // Deep water
            new Color(60, 120, 200),   // Shallow water
            new Color(210, 180, 140),  // Sand - beach
            new Color(34, 139, 34),    // Grass - green
            new Color(139, 90, 43),    // Rock - brown
            new Color(255, 255, 255)   // Snow - white
    };

    /** Pre-computed {@code Color.darker()} variants for the wireframe pass. */
    private static final Color[] WIRE_COLORS = new Color[HEIGHT_COLORS.length];

    /** Packed RGB variants used by the software rasterizer. */
    private static final int[] HEIGHT_RGB = new int[HEIGHT_COLORS.length];
    private static final int[] WIRE_RGB = new int[HEIGHT_COLORS.length];

    static {
        for (int i = 0; i < HEIGHT_COLORS.length; i++) {
            WIRE_COLORS[i] = HEIGHT_COLORS[i].darker();
            HEIGHT_RGB[i] = HEIGHT_COLORS[i].getRGB() & 0xFFFFFF;
            WIRE_RGB[i] = WIRE_COLORS[i].getRGB() & 0xFFFFFF;
        }
    }

    private final int width;
    private final int height;
    private final double[] heightMap;

    // Pre-multiplied band thresholds so the per-triangle color lookup is just
    // a handful of comparisons.
    private final double deepWater;
    private final double shallowWater;
    private final double sand;
    private final double grass;
    private final double rock;

    public Terrain(int width, int height, PerlinNoise perlinNoise,
                   double scale, int octaves, double heightMultiplier) {
        this.width = width;
        this.height = height;
        this.heightMap = new double[width * height];

        this.deepWater = heightMultiplier * 0.3;
        this.shallowWater = heightMultiplier * 0.35;
        this.sand = heightMultiplier * 0.4;
        this.grass = heightMultiplier * 0.6;
        this.rock = heightMultiplier * 0.75;

        for (int x = 0; x < width; x++) {
            int row = x * height;
            for (int y = 0; y < height; y++) {
                // Generate noise value with multiple octaves for more detail
                double noiseValue = perlinNoise.octaveNoise(x * scale, y * scale, octaves);

                // Map noise value from [-1, 1] to height
                heightMap[row + y] = (noiseValue + 1.0) * 0.5 * heightMultiplier;
            }
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Safe lookup: returns {@code 0.0} for out-of-bounds coordinates. */
    public double getHeight(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return 0.0;
        }
        return heightMap[x * height + y];
    }

    /**
     * Bounds-free lookup for hot paths where the caller has already clamped the
     * coordinates (terrain generation, rendering inner loops, mini-map).
     */
    public double heightAt(int x, int y) {
        return heightMap[x * height + y];
    }

    /** Height-band index in range {@code [0, paletteSize() - 1]}. */
    public int colorIndexForHeight(double height) {
        if (height < deepWater) return 0;
        if (height < shallowWater) return 1;
        if (height < sand) return 2;
        if (height < grass) return 3;
        if (height < rock) return 4;
        return 5;
    }

    public Color getColorForHeight(double height) {
        return HEIGHT_COLORS[colorIndexForHeight(height)];
    }

    public static int paletteSize() {
        return HEIGHT_COLORS.length;
    }

    public static Color colorForIndex(int index) {
        return HEIGHT_COLORS[index];
    }

    public static Color wireColorForIndex(int index) {
        return WIRE_COLORS[index];
    }

    public static int rgbForIndex(int index) {
        return HEIGHT_RGB[index];
    }

    public static int wireRgbForIndex(int index) {
        return WIRE_RGB[index];
    }
}
