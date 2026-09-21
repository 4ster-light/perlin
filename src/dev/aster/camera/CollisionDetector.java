package dev.aster.camera;

import dev.aster.terrain.Terrain;

/**
 * Handles collision detection between camera and terrain.
 */
public final class CollisionDetector {

    private static final double CAMERA_HEIGHT = 3.0; // Hover height above terrain

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

    /** Check if camera is within terrain bounds. */
    public boolean isPositionInBounds(double x, double y) {
        return x >= 0 && x < terrain.width() && y >= 0 && y < terrain.height();
    }

    /** Get terrain height at a specific position using bilinear interpolation. */
    public double getTerrainHeightAtPosition(double x, double y) {
        if (!isPositionInBounds(x, y)) {
            return 0.0;
        }

        int xi = (int) x;
        int yi = (int) y;

        // Bilinear interpolation for smooth height calculation
        double xFrac = x - xi;
        double yFrac = y - yi;

        double h00 = terrain.getHeight(xi, yi);
        double h10 = terrain.getHeight(xi + 1, yi);
        double h01 = terrain.getHeight(xi, yi + 1);
        double h11 = terrain.getHeight(xi + 1, yi + 1);

        double h0 = h00 * (1 - xFrac) + h10 * xFrac;
        double h1 = h01 * (1 - xFrac) + h11 * xFrac;

        return h0 * (1 - yFrac) + h1 * yFrac;
    }

    /** Clamp camera position to stay within terrain bounds. */
    public void clampCameraPosition(Camera camera) {
        double margin = 5.0;
        camera.x = Math.clamp(camera.x, margin, terrain.width() - margin);
        camera.y = Math.clamp(camera.y, margin, terrain.height() - margin);
    }
}
