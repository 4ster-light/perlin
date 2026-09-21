package dev.aster;

import java.awt.Color;

/**
 * Height-map based terrain grid with height-banded coloring.
 */
public final class Terrain {

    private final int width;
    private final int height;
    private final double heightMultiplier;
    private final double[][] heightMap;

    public Terrain(int width, int height, PerlinNoise perlinNoise,
                   double scale, int octaves, double heightMultiplier) {
        this.width = width;
        this.height = height;
        this.heightMultiplier = heightMultiplier;
        this.heightMap = new double[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Generate noise value with multiple octaves for more detail
                double noiseValue = perlinNoise.octaveNoise(x * scale, y * scale, octaves);

                // Map noise value from [-1, 1] to height
                heightMap[x][y] = (noiseValue + 1.0) * 0.5 * heightMultiplier;
            }
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public double getHeight(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return 0.0;
        }
        return heightMap[x][y];
    }

    public Color getColorForHeight(double height) {
        if (height < heightMultiplier * 0.3) return new Color(30, 80, 180);   // Water - deep blue
        if (height < heightMultiplier * 0.35) return new Color(60, 120, 200); // Shallow water
        if (height < heightMultiplier * 0.4) return new Color(210, 180, 140); // Sand - beach
        if (height < heightMultiplier * 0.6) return new Color(34, 139, 34);   // Grass - green
        if (height < heightMultiplier * 0.75) return new Color(139, 90, 43);  // Rock - brown
        return new Color(255, 255, 255);                                      // Snow - white
    }
}
