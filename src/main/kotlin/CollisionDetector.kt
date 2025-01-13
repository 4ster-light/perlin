package dev.aster

/**
 * Handles collision detection between camera and terrain
 */
class CollisionDetector(private val terrain: Terrain) {
    companion object {
        private const val CAMERA_HEIGHT = 2.0
        private const val COLLISION_BUFFER = 1.5
    }

    /**
     * Check and adjust camera position to prevent clipping through terrain
     */
    fun adjustCameraPosition(camera: Camera): Boolean {
        val terrainHeight = getTerrainHeightAtPosition(camera.x, camera.y)
        val minCameraZ = terrainHeight + CAMERA_HEIGHT

        return if (camera.z < minCameraZ) {
            camera.z = minCameraZ
            true // Collision occurred
        } else {
            false // No collision
        }
    }

    /**
     * Check if camera is within terrain bounds
     */
    fun isPositionInBounds(x: Double, y: Double): Boolean {
        return x >= 0 && x < terrain.width && y >= 0 && y < terrain.height
    }

    /**
     * Get terrain height at a specific position using bilinear interpolation
     */
    fun getTerrainHeightAtPosition(x: Double, y: Double): Double {
        if (!isPositionInBounds(x, y)) return 0.0

        val xi = x.toInt()
        val yi = y.toInt()

        // Bilinear interpolation for smooth height calculation
        val xFrac = x - xi
        val yFrac = y - yi

        val h00 = terrain.getHeight(xi, yi)
        val h10 = terrain.getHeight(xi + 1, yi)
        val h01 = terrain.getHeight(xi, yi + 1)
        val h11 = terrain.getHeight(xi + 1, yi + 1)

        val h0 = h00 * (1 - xFrac) + h10 * xFrac
        val h1 = h01 * (1 - xFrac) + h11 * xFrac

        return h0 * (1 - yFrac) + h1 * yFrac
    }

    /**
     * Clamp camera position to stay within terrain bounds
     */
    fun clampCameraPosition(camera: Camera) {
        val margin = 5.0
        camera.x = camera.x.coerceIn(margin, terrain.width - margin)
        camera.y = camera.y.coerceIn(margin, terrain.height - margin)
    }
}
