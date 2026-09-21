package dev.aster.render;

import dev.aster.terrain.Terrain;

/**
 * Level of Detail system for rendering distant terrain at reduced detail.
 *
 * <p>Detail levels lower per-triangle rendering cost (e.g. wireframe strokes)
 * with distance. The mesh itself is deliberately kept uniform: merging cells
 * into coarser triangles cracks a heightfield (T-junctions) unless boundary
 * edges are stitched, which is not worth the complexity in this demo.
 */
public final class LevelOfDetail {

    // LOD distances (in grid units squared for faster comparison)
    private static final double LOD0_DISTANCE_SQ = 900.0;   // 30^2 - Full detail
    private static final double LOD1_DISTANCE_SQ = 3600.0;  // 60^2 - Half detail

    private final Terrain terrain;

    public LevelOfDetail(Terrain terrain) {
        this.terrain = terrain;
    }

    /**
     * Get LOD skip rate based on squared distance from camera.
     *
     * @return 1 for full detail, 2 for half, 3 for quarter
     */
    public int getLODSkipRate(double distanceSquared) {
        if (distanceSquared < LOD0_DISTANCE_SQ) return 1; // Full detail
        if (distanceSquared < LOD1_DISTANCE_SQ) return 2; // Half detail
        return 3;                                          // Quarter detail
    }
}
