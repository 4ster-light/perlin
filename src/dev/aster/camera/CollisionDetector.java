package dev.aster.camera;

import dev.aster.terrain.Terrain;

/**
 * Handles collision detection between camera and terrain.
 */
public final class CollisionDetector {

    private static final double CAMERA_HEIGHT = 3.0; // Hover height above terrain
    private static final double WORLD_LIMIT = 1_000_000.0; // numeric safety net only

    private final Terrain terrain;

    public CollisionDetector(Terrain terrain) {
        this.terrain = terrain;
    }

    /**
     * Check and adjust camera position to prevent clipping through terrain.
     *
     * @return {@code true} if a collision occurred and the camera was pushed up
     */
    public boolean adjustCameraPosition(Camera camera) {
        double terrainHeight = getTerrainHeightAtPosition(camera.x, camera.y);
        double minCameraZ = terrainHeight + CAMERA_HEIGHT;

        if (camera.z < minCameraZ) {
            camera.z = minCameraZ;
            return true; // Collision occurred
        }
        return false; // No collision
    }

    /** Get terrain height at a specific position using bilinear interpolation. */
    public double getTerrainHeightAtPosition(double x, double y) {
        // floor() (not a cast) is required for negative world coordinates.
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);

        double xFrac = x - xi;
        double yFrac = y - yi;

        double h00 = terrain.heightAt(xi, yi);
        double h10 = terrain.heightAt(xi + 1, yi);
        double h01 = terrain.heightAt(xi, yi + 1);
        double h11 = terrain.heightAt(xi + 1, yi + 1);

        double h0 = h00 * (1 - xFrac) + h10 * xFrac;
        double h1 = h01 * (1 - xFrac) + h11 * xFrac;

        return h0 * (1 - yFrac) + h1 * yFrac;
    }

    /**
     * The world is infinite; this only guards against absurd coordinates that
     * could destabilize the projection math.
     */
    public void clampCameraPosition(Camera camera) {
        camera.x = Math.clamp(camera.x, -WORLD_LIMIT, WORLD_LIMIT);
        camera.y = Math.clamp(camera.y, -WORLD_LIMIT, WORLD_LIMIT);
    }
}
