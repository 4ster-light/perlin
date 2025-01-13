package dev.aster

import kotlin.math.*

/**
 * View frustum for culling triangles outside the visible area
 */
class ViewFrustum(
    val fov: Double = 90.0,
    val aspectRatio: Double = 800.0 / 600.0,
    val nearPlane: Double = 0.1,
    val farPlane: Double = 1000.0
) {
    private var left = 0.0
    private var right = 0.0
    private var top = 0.0
    private var bottom = 0.0

    init {
        updateFrustumPlanes()
    }

    private fun updateFrustumPlanes() {
        val height = tan(Math.toRadians(fov / 2.0)) * nearPlane
        top = height
        bottom = -height
        left = -aspectRatio * height
        right = aspectRatio * height
    }

    /**
     * Check if a point is inside the frustum (simplified check for near plane)
     */
    fun isPointInFrustum(x: Double, y: Double, z: Double): Boolean {
        // Very basic check: if point is behind camera or too far, cull it
        return z > nearPlane && z < farPlane
    }

    /**
     * Check if a triangle is potentially visible
     */
    fun isTriangleVisible(p1: Triple<Double, Double, Double>, p2: Triple<Double, Double, Double>, p3: Triple<Double, Double, Double>): Boolean {
        return isPointInFrustum(p1.first, p1.second, p1.third) ||
                isPointInFrustum(p2.first, p2.second, p2.third) ||
                isPointInFrustum(p3.first, p3.second, p3.third)
    }
}
