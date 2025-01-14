package dev.aster

/**
 * Level of Detail system for rendering distant terrain at reduced detail
 */
class LevelOfDetail(private val terrain: Terrain) {
    companion object {
        // LOD distances (in grid units)
        private const val LOD0_DISTANCE = 30.0  // Full detail
        private const val LOD1_DISTANCE = 60.0  // Skip every 2nd vertex
        private const val LOD2_DISTANCE = 100.0 // Skip every 3rd vertex
    }

    /**
     * Data class representing a LOD level with mesh vertices
     */
    data class LODLevel(
        val distance: Double,
        val skipRate: Int,
        val triangles: List<LODTriangle>
    )

    data class LODTriangle(
        val x1: Int, val y1: Int, val z1: Double,
        val x2: Int, val y2: Int, val z2: Double,
        val x3: Int, val y3: Int, val z3: Double,
        val color: java.awt.Color
    )

    /**
     * Get LOD level based on distance from camera
     */
    fun getLODSkipRate(distanceSquared: Double): Int {
        return when {
            distanceSquared < LOD0_DISTANCE * LOD0_DISTANCE -> 1  // Full detail
            distanceSquared < LOD1_DISTANCE * LOD1_DISTANCE -> 2  // Half detail
            else -> 3  // Quarter detail
        }
    }

    /**
     * Generate triangles with appropriate LOD based on camera position
     */
    fun generateTriangles(
        cameraX: Double,
        cameraY: Double,
        renderDistance: Double
    ): List<Renderer3D.Triangle> {
        val triangles = mutableListOf<Renderer3D.Triangle>()

        val camGridX = cameraX.toInt()
        val camGridY = cameraY.toInt()

        for (x in maxOf(0, (camGridX - renderDistance).toInt()) until minOf(
            terrain.width - 1,
            (camGridX + renderDistance).toInt()
        )) {
            for (y in maxOf(0, (camGridY - renderDistance).toInt()) until minOf(
                terrain.height - 1,
                (camGridY + renderDistance).toInt()
            )) {
                // Calculate distance from camera
                val dx = x - cameraX
                val dy = y - cameraY
                val distanceSquared = dx * dx + dy * dy

                // Determine LOD skip rate
                val skipRate = getLODSkipRate(distanceSquared)

                // Only render if this vertex aligns with current LOD skip rate
                if ((x % skipRate != 0) || (y % skipRate != 0)) continue

                // Create triangles with LOD consideration
                val nextX = x + skipRate
                val nextY = y + skipRate

                if (nextX < terrain.width && nextY < terrain.height) {
                    val h1 = terrain.getHeight(x, y)
                    val h2 = terrain.getHeight(nextX, y)
                    val h3 = terrain.getHeight(x, nextY)
                    val h4 = terrain.getHeight(nextX, nextY)

                    // Calculate average heights for color selection
                    val avgHeight1 = (h1 + h2 + h3) / 3.0
                    val avgHeight2 = (h2 + h4 + h3) / 3.0

                    // Create mesh representation (would need projection in Renderer3D)
                    // For now, store the raw data for later processing
                }
            }
        }

        return triangles
    }
}
