package dev.aster.camera

import dev.aster.terrain.Terrain

/** Handles collision detection between camera and terrain. */
final class CollisionDetector(private val terrain: Terrain) {

  private val CAMERA_HEIGHT = 3.0
  private val WORLD_LIMIT = 1_000_000.0 // numeric safety net only

  /**
   * Check and adjust camera position to prevent clipping through terrain.
   *
   * @return true if a collision occurred and the camera was pushed up
   */
  def adjustCameraPosition(camera: Camera): Boolean = {
    val terrainHeight = getTerrainHeightAtPosition(camera.x, camera.y)
    val minCameraZ = terrainHeight + CAMERA_HEIGHT

    if (camera.z < minCameraZ) {
      camera.z = minCameraZ
      true
    } else {
      false
    }
  }

  /** Get terrain height at a specific position using bilinear interpolation. */
  def getTerrainHeightAtPosition(x: Double, y: Double): Double = {
    // floor() (not a cast) is required for negative world coordinates.
    val xi = Math.floor(x).toInt
    val yi = Math.floor(y).toInt

    val xFrac = x - xi
    val yFrac = y - yi

    val h00 = terrain.heightAt(xi, yi)
    val h10 = terrain.heightAt(xi + 1, yi)
    val h01 = terrain.heightAt(xi, yi + 1)
    val h11 = terrain.heightAt(xi + 1, yi + 1)

    val h0 = h00 * (1 - xFrac) + h10 * xFrac
    val h1 = h01 * (1 - xFrac) + h11 * xFrac

    h0 * (1 - yFrac) + h1 * yFrac
  }

  /**
   * The world is infinite; this only guards against absurd coordinates that
   * could destabilize the projection math.
   */
  def clampCameraPosition(camera: Camera): Unit = {
    camera.x = Math.clamp(camera.x, -WORLD_LIMIT, WORLD_LIMIT)
    camera.y = Math.clamp(camera.y, -WORLD_LIMIT, WORLD_LIMIT)
  }
}
