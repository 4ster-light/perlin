package dev.aster.render

import dev.aster.camera.Camera

/** View frustum for culling triangles outside the visible area. */
final class ViewFrustum(
    val fov: Double,
    val aspectRatio: Double,
    val nearPlane: Double,
    val farPlane: Double
) {

  private var left = 0.0
  private var right = 0.0
  private var top = 0.0
  private var bottom = 0.0

  def this() = this(90.0, 800.0 / 600.0, 0.1, 1000.0)

  updateFrustumPlanes()

  private def updateFrustumPlanes(): Unit = {
    val height = Math.tan(Math.toRadians(fov / 2.0)) * nearPlane
    top = height
    bottom = -height
    left = -aspectRatio * height
    right = aspectRatio * height
  }

  /** Check if a point is inside the frustum (simplified near/far check). */
  def isPointInFrustum(x: Double, y: Double, z: Double): Boolean =
    z > nearPlane && z < farPlane

  /** Cheap depth-only near/far plane test used by the software rasterizer. */
  def isDepthInFrustum(depth: Double): Boolean =
    depth > nearPlane && depth < farPlane

  /** Check if a triangle is potentially visible. */
  def isTriangleVisible(
      p1: Camera.Vector3,
      p2: Camera.Vector3,
      p3: Camera.Vector3
  ): Boolean =
    isPointInFrustum(p1.x, p1.y, p1.z) ||
      isPointInFrustum(p2.x, p2.y, p2.z) ||
      isPointInFrustum(p3.x, p3.y, p3.z)
}
