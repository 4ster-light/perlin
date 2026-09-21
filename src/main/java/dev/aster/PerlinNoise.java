package dev.aster;

import java.util.Random;

/**
 * Classic 2D Perlin noise with seeded permutation table.
 */
public final class PerlinNoise {

    private final int[] permutation = new int[512];

    public PerlinNoise() {
        this(System.currentTimeMillis());
    }

    public PerlinNoise(long seed) {
        // Initialize permutation table with Fisher-Yates shuffle
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }

        Random random = new Random(seed);
        for (int i = 255; i >= 1; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }

        // Duplicate the permutation table
        for (int i = 0; i <= 255; i++) {
            permutation[i] = p[i];
            permutation[i + 256] = p[i];
        }
    }

    /**
     * Generate 2D Perlin noise value at coordinates (x, y).
     *
     * @return value between -1 and 1
     */
    public double noise(double x, double y) {
        // Find unit grid cell containing point
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;

        // Get relative xy coordinates of point within cell
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);

        // Compute fade curves
        double u = fade(xf);
        double v = fade(yf);

        // Hash coordinates of the 4 corners
        int aa = permutation[permutation[xi] + yi];
        int ab = permutation[permutation[xi] + yi + 1];
        int ba = permutation[permutation[xi + 1] + yi];
        int bb = permutation[permutation[xi + 1] + yi + 1];

        // Blend results from 4 corners
        double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);

        return lerp(x1, x2, v);
    }

    /**
     * Generate octave Perlin noise (fractal noise with multiple frequencies).
     */
    public double octaveNoise(double x, double y, int octaves, double persistence) {
        double total = 0.0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxValue = 0.0;

        for (int i = 0; i < octaves; i++) {
            total += noise(x * frequency, y * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }

        return total / maxValue;
    }

    public double octaveNoise(double x, double y, int octaves) {
        return octaveNoise(x, y, octaves, 0.5);
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y) {
        // Convert low 2 bits of hash code into 4 gradient directions
        int h = hash & 3;
        double u = h < 2 ? x : y;
        double v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
