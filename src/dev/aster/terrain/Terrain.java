package dev.aster.terrain;

import dev.aster.noise.PerlinNoise;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Infinite, lazily generated height-map terrain.
 *
 * <p>The world is split into cubic chunks of {@value #CHUNK_SIZE}×{@value #CHUNK_SIZE}
 * height samples. A chunk is generated on first access and kept in a small LRU
 * cache, so the world can extend forever without ever materializing the whole
 * map. Terrain is a pure function of world coordinates and the seed, so the
 * same location always produces the same height and surface color.
 *
 * <p>Generation combines several noise fields similar in spirit to modern
 * Minecraft worldgen:
 * <ul>
 *   <li>domain-warped continentalness (oceans vs. land),</li>
 *   <li>erosion for broad flat areas vs. rough terrain,</li>
 *   <li>rolling hills for local relief,</li>
 *   <li>ridged multifractal noise for mountain crests,</li>
 *   <li>temperature/moisture fields for biomes.</li>
 * </ul>
 *
 * <p>Instances are intended to be used from a single thread after construction.
 * Build and preload a new instance on a worker thread, then hand it to the EDT.
 */
public final class Terrain {

    // Surface palette indices (order is shared by the editor/renderer/HUD).
    private static final Color[] SURFACE_COLORS = {
            new Color(30, 80, 180),    // 0 deep water
            new Color(60, 120, 200),   // 1 shallow water
            new Color(214, 190, 145),  // 2 beach sand
            new Color(222, 204, 145),  // 3 desert sand
            new Color(96, 160, 74),    // 4 grassland
            new Color(40, 105, 48),    // 5 forest
            new Color(160, 165, 82),   // 6 savanna
            new Color(139, 90, 43),    // 7 rock
            new Color(245, 247, 255)   // 8 snow
    };

    private static final Color[] WIRE_COLORS = new Color[SURFACE_COLORS.length];
    private static final int[] SURFACE_RGB = new int[SURFACE_COLORS.length];
    private static final int[] WIRE_RGB = new int[SURFACE_COLORS.length];

    static {
        for (int i = 0; i < SURFACE_COLORS.length; i++) {
            WIRE_COLORS[i] = SURFACE_COLORS[i].darker();
            SURFACE_RGB[i] = SURFACE_COLORS[i].getRGB() & 0xFFFFFF;
            WIRE_RGB[i] = WIRE_COLORS[i].getRGB() & 0xFFFFFF;
        }
    }

    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNK_MASK = CHUNK_SIZE - 1;
    private static final int MAX_CACHED_CHUNKS = 4096;

    private static final double SEA_LEVEL = 16.0;
    private static final int SPAWN_SEARCH_RADIUS = 512;
    private static final int SPAWN_SEARCH_STEP = 64;

    private final long seed;
    private final PerlinNoise noise;
    private final double spawnX;
    private final double spawnY;

    // Access-order LRU cache. Generation is deterministic, so evicted chunks
    // are simply regenerated when the player returns.
    private final Map<Long, Chunk> chunks = new LinkedHashMap<>(512, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Chunk> eldest) {
            return size() > MAX_CACHED_CHUNKS;
        }
    };

    // Small last-chunk cache: the renderer walks one chunk column at a time, so
    // this turns almost all per-vertex lookups into direct array accesses.
    private int lastChunkX = Integer.MIN_VALUE;
    private int lastChunkY = Integer.MIN_VALUE;
    private Chunk lastChunk;

    private static final class Chunk {
        final double[] heights = new double[CHUNK_SIZE * CHUNK_SIZE];
        final byte[] colors = new byte[CHUNK_SIZE * CHUNK_SIZE];
    }

    public Terrain(long seed) {
        this.seed = seed;
        this.noise = new PerlinNoise(seed);

        // Pick a nearby, walkable land spawn with some local relief. This only
        // samples the height function; it does not force chunks into the cache.
        double bestX = 0.0;
        double bestY = 0.0;
        double bestScore = -Double.MAX_VALUE;
        for (int x = -SPAWN_SEARCH_RADIUS; x <= SPAWN_SEARCH_RADIUS; x += SPAWN_SEARCH_STEP) {
            for (int y = -SPAWN_SEARCH_RADIUS; y <= SPAWN_SEARCH_RADIUS; y += SPAWN_SEARCH_STEP) {
                double height = generateHeight(x, y);
                if (height <= SEA_LEVEL + 2.0 || height >= SEA_LEVEL + 20.0) {
                    continue;
                }

                double n = generateHeight(x, y + 32);
                double s = generateHeight(x, y - 32);
                double e = generateHeight(x + 32, y);
                double w = generateHeight(x - 32, y);
                double max = Math.max(height, Math.max(Math.max(n, s), Math.max(e, w)));
                double min = Math.min(height, Math.min(Math.min(n, s), Math.min(e, w)));
                double relief = max - min;

                double distanceSquared = (double) x * x + (double) y * y;
                // Prefer rolling terrain (some relief, but not a cliff face)
                // while keeping the spawn reasonably close to the origin.
                double score = -Math.abs(relief - 7.0) - Math.sqrt(distanceSquared) / 128.0;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        this.spawnX = bestX;
        this.spawnY = bestY;
    }

    public long seed() {
        return seed;
    }

    public double spawnX() {
        return spawnX;
    }

    public double spawnY() {
        return spawnY;
    }

    /** Height at an integer world coordinate, generating its chunk if needed. */
    public double heightAt(int x, int y) {
        return chunkAt(x, y).heights[index(x, y)];
    }

    /** Surface palette index at an integer world coordinate. */
    public int colorIndexAt(int x, int y) {
        return chunkAt(x, y).colors[index(x, y)];
    }

    /**
     * Generates every chunk overlapping the inclusive world-space rectangle.
     * Call once per frame before bulk height/color queries.
     */
    public void ensureRegionLoaded(int x0, int y0, int x1, int y1) {
        int chunkX0 = x0 >> CHUNK_SHIFT;
        int chunkX1 = x1 >> CHUNK_SHIFT;
        int chunkY0 = y0 >> CHUNK_SHIFT;
        int chunkY1 = y1 >> CHUNK_SHIFT;

        for (int cx = chunkX0; cx <= chunkX1; cx++) {
            for (int cy = chunkY0; cy <= chunkY1; cy++) {
                long key = key(cx, cy);
                if (chunks.get(key) == null) {
                    chunks.put(key, generateChunk(cx, cy));
                }
            }
        }

        // The LRU may have evicted the last-chunk shortcut during generation.
        lastChunk = null;
        lastChunkX = Integer.MIN_VALUE;
        lastChunkY = Integer.MIN_VALUE;
    }

    /**
     * Preloads chunks around a world position. Useful on a worker thread before
     * swapping a freshly generated world onto the EDT.
     */
    public void preloadRegion(double centerX, double centerY, double radius) {
        int x0 = (int) Math.floor(centerX - radius);
        int y0 = (int) Math.floor(centerY - radius);
        int x1 = (int) Math.ceil(centerX + radius);
        int y1 = (int) Math.ceil(centerY + radius);
        ensureRegionLoaded(x0, y0, x1, y1);
    }

    private Chunk chunkAt(int x, int y) {
        int chunkX = x >> CHUNK_SHIFT;
        int chunkY = y >> CHUNK_SHIFT;
        if (chunkX == lastChunkX && chunkY == lastChunkY) {
            return lastChunk;
        }

        long key = key(chunkX, chunkY);
        Chunk chunk = chunks.get(key);
        if (chunk == null) {
            chunk = generateChunk(chunkX, chunkY);
            chunks.put(key, chunk);
        }

        lastChunkX = chunkX;
        lastChunkY = chunkY;
        lastChunk = chunk;
        return chunk;
    }

    private Chunk generateChunk(int chunkX, int chunkY) {
        Chunk chunk = new Chunk();
        int baseX = chunkX << CHUNK_SHIFT;
        int baseY = chunkY << CHUNK_SHIFT;

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            int worldX = baseX + localX;
            int row = localX * CHUNK_SIZE;
            for (int localY = 0; localY < CHUNK_SIZE; localY++) {
                int worldY = baseY + localY;
                double rawHeight = generateHeight(worldX, worldY);
                // Keep oceans as a flat water surface, but use the underlying
                // height for deep/shallow water coloring.
                chunk.heights[row + localY] = Math.max(rawHeight, SEA_LEVEL);
                chunk.colors[row + localY] = (byte) surfaceColorIndex(worldX, worldY, rawHeight);
            }
        }
        return chunk;
    }

    private static int index(int x, int y) {
        return (x & CHUNK_MASK) * CHUNK_SIZE + (y & CHUNK_MASK);
    }

    private static long key(int chunkX, int chunkY) {
        return ((long) chunkX << 32) ^ (chunkY & 0xFFFFFFFFL);
    }

    /**
     * The world height function. All fields are deterministic functions of
     * world coordinates, which makes chunk generation order-independent.
     */
    private double generateHeight(int worldX, int worldY) {
        double x = worldX;
        double y = worldY;

        // Domain warp breaks up the grid-aligned look of raw Perlin noise and
        // gives coastlines/valleys a more organic shape.
        double warpX = noise.octaveNoise(x * 0.0025 + 101.7, y * 0.0025 + 211.3, 2, 0.5) * 28.0;
        double warpY = noise.octaveNoise(x * 0.0025 + 311.9, y * 0.0025 + 401.1, 2, 0.5) * 28.0;
        double wx = x + warpX;
        double wy = y + warpY;

        // Low-frequency continent mask: ocean basins vs. land masses.
        double continent = noise.octaveNoise(wx * 0.0016, wy * 0.0016, 4, 0.5);

        // Erosion controls how rough/flat the land is.
        double erosion = noise.octaveNoise(wx * 0.004 + 1873.0, wy * 0.004 + 2551.0, 3, 0.5);

        // Rolling hills / local detail.
        double hills = noise.octaveNoise(wx * 0.010, wy * 0.010, 5, 0.5);

        // Ridged multifractal for mountain crests.
        double ridge = noise.ridgedNoise(wx * 0.0055 + 4093.0, wy * 0.0055 + 3187.0, 4, 0.5);

        double land = smoothstep(-0.22, 0.18, continent);
        double mountains = smoothstep(-0.08, 0.32, continent)
                * smoothstep(-0.35, 0.35, erosion);

        double base = SEA_LEVEL + continent * 24.0;
        double height = base
                + hills * (5.0 + 7.0 * land) * (0.70 + 0.35 * erosion)
                + ridge * 42.0 * mountains;

        // Slightly flatten very shallow coastal terrain.
        if (height < SEA_LEVEL + 2.0) {
            height = SEA_LEVEL + (height - SEA_LEVEL) * 0.9;
        }
        return height;
    }

    private int surfaceColorIndex(int worldX, int worldY, double height) {
        if (height < SEA_LEVEL - 3.0) return 0; // deep water
        if (height < SEA_LEVEL) return 1;       // shallow water
        if (height < SEA_LEVEL + 1.2) return 2; // beach

        double temperature = noise.octaveNoise(
                worldX * 0.0018 + 5000.0, worldY * 0.0018 + 5000.0, 3, 0.5);
        double moisture = noise.octaveNoise(
                worldX * 0.0015 + 7000.0, worldY * 0.0015 + 7000.0, 3, 0.5);

        double snowLine = SEA_LEVEL + 32.0 + temperature * 8.0;
        if (height > snowLine) return 8; // snow

        double rockLine = SEA_LEVEL + 24.0 + temperature * 5.0;
        if (height > rockLine) return 7; // rock

        if (temperature > 0.15 && moisture < 0.0) return 3;  // desert
        if (moisture > 0.12) return 5;                       // forest
        if (temperature > 0.05 && moisture < 0.08) return 6; // savanna
        return 4;                                            // grassland
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static int paletteSize() {
        return SURFACE_COLORS.length;
    }

    public static Color colorForIndex(int index) {
        return SURFACE_COLORS[index];
    }

    public static int rgbForIndex(int index) {
        return SURFACE_RGB[index];
    }

    public static int wireRgbForIndex(int index) {
        return WIRE_RGB[index];
    }
}
