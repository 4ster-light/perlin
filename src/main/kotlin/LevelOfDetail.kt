package dev.aster

/**
 * Level of Detail system for rendering distant terrain at reduced detail
 */
class LevelOfDetail(private val terrain: Terrain) {
    companion object {
        // LOD distances (in grid units squared for faster comparison)
        private const val LOD0_DISTANCE_SQ = 900.0   // 30^2 - Full detail
        private const val LOD1_DISTANCE_SQ = 3600.0  // 60^2 - Half detail
    }

    /**
     * Get LOD skip rate based on squared distance from camera
     * Returns 1 for full detail, 2 for half, 3 for quarter
     */
    fun getLODSkipRate(distanceSquared: Double): Int {
        return when {
            distanceSquared < LOD0_DISTANCE_SQ -> 1  // Full detail
            distanceSquared < LOD1_DISTANCE_SQ -> 2  // Half detail
            else -> 3  // Quarter detail
        }
    }
}
