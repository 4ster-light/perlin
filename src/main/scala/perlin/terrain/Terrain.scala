package perlin.terrain

import perlin.noise.PerlinNoise

import java.awt.Color
import scala.collection.mutable

object Terrain {

  // Surface palette indices (order is shared by the renderer and HUD).
  private val SURFACE_COLORS = Array(
    new Color(30, 80, 180), // 0 deep water
    new Color(60, 120, 200), // 1 shallow water
    new Color(214, 190, 145), // 2 beach sand
    new Color(222, 204, 145), // 3 desert sand
    new Color(96, 160, 74), // 4 grassland
    new Color(40, 105, 48), // 5 forest
    new Color(160, 165, 82), // 6 savanna
    new Color(139, 90, 43), // 7 rock
    new Color(245, 247, 255) // 8 snow
  )

  private val WIRE_COLORS = SURFACE_COLORS.map(_.darker)
  private val SURFACE_RGB = SURFACE_COLORS.map(_.getRGB & 0xffffff)
  private val WIRE_RGB = WIRE_COLORS.map(_.getRGB & 0xffffff)

  private val CHUNK_SIZE = 16
  private val CHUNK_SHIFT = 4
  private val CHUNK_MASK = CHUNK_SIZE - 1
  private val MAX_CACHED_CHUNKS = 4096

  private val SEA_LEVEL = 16.0
  private val SPAWN_SEARCH_RADIUS = 512
  private val SPAWN_SEARCH_STEP = 64

  private final class Chunk {
    val heights = new Array[Double](CHUNK_SIZE * CHUNK_SIZE)
    val colors = new Array[Byte](CHUNK_SIZE * CHUNK_SIZE)
  }

  def paletteSize: Int = SURFACE_COLORS.length

  def colorForIndex(index: Int): Color = SURFACE_COLORS(index)

  def rgbForIndex(index: Int): Int = SURFACE_RGB(index)

  def wireRgbForIndex(index: Int): Int = WIRE_RGB(index)

  private def smoothstep(edge0: Double, edge1: Double, value: Double): Double = {
    val t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0)
    t * t * (3.0 - 2.0 * t)
  }
}

/** Infinite, lazily generated height-map terrain.
  *
  * The world is split into chunks of 16x16 height samples. A chunk is generated on first access and
  * kept in a small LRU cache, so the world can extend forever without ever materializing the whole
  * map. Terrain is a pure function of world coordinates and the seed, so the same location always
  * produces the same height and surface color.
  *
  * Generation combines domain-warped continentalness, erosion, rolling hills, ridged multifractal
  * mountains and temperature/moisture-based biomes.
  *
  * Instances are intended to be used from a single thread after construction. Build and preload a
  * new instance on a worker thread, then hand it to the EDT.
  */
final class Terrain(val seed: Long) {

  import Terrain.*

  private val noise = new PerlinNoise(seed)

  // LRU cache. scala.collection.mutable.LinkedHashMap preserves insertion
  // order; cache hits are moved to the end and the oldest entry is evicted.
  private val chunks = mutable.LinkedHashMap.empty[Long, Chunk]

  // Small last-chunk cache: the renderer walks one chunk column at a time, so
  // this turns almost all per-vertex lookups into direct array accesses.
  private var lastChunkX = Int.MinValue
  private var lastChunkY = Int.MinValue
  private var lastChunk: Chunk = null

  private val spawn: (Double, Double) = chooseSpawn()

  def spawnX: Double = spawn._1

  def spawnY: Double = spawn._2

  /** Height at an integer world coordinate, generating its chunk if needed. */
  def heightAt(x: Int, y: Int): Double =
    chunkAt(x, y).heights(index(x, y))

  /** Surface palette index at an integer world coordinate. */
  def colorIndexAt(x: Int, y: Int): Int =
    chunkAt(x, y).colors(index(x, y))

  /** Generates every chunk overlapping the inclusive world-space rectangle. Call once per frame
    * before bulk height/color queries.
    */
  def ensureRegionLoaded(x0: Int, y0: Int, x1: Int, y1: Int): Unit = {
    val chunkX0 = x0 >> CHUNK_SHIFT
    val chunkX1 = x1 >> CHUNK_SHIFT
    val chunkY0 = y0 >> CHUNK_SHIFT
    val chunkY1 = y1 >> CHUNK_SHIFT

    var cx = chunkX0
    while (cx <= chunkX1) {
      var cy = chunkY0
      while (cy <= chunkY1) {
        val cacheKey = key(cx, cy)
        chunks.get(cacheKey) match {
          case Some(chunk) => touch(cacheKey, chunk)
          case None        => cacheChunk(cacheKey, generateChunk(cx, cy))
        }
        cy += 1
      }
      cx += 1
    }

    // The LRU may have evicted the last-chunk shortcut during generation.
    lastChunk = null
    lastChunkX = Int.MinValue
    lastChunkY = Int.MinValue
  }

  /** Preloads chunks around a world position. Useful on a worker thread before swapping a freshly
    * generated world onto the EDT.
    */
  def preloadRegion(centerX: Double, centerY: Double, radius: Double): Unit = {
    val x0 = Math.floor(centerX - radius).toInt
    val y0 = Math.floor(centerY - radius).toInt
    val x1 = Math.ceil(centerX + radius).toInt
    val y1 = Math.ceil(centerY + radius).toInt
    ensureRegionLoaded(x0, y0, x1, y1)
  }

  private def chooseSpawn(): (Double, Double) = {
    var bestX = 0.0
    var bestY = 0.0
    var bestScore = -Double.MaxValue

    var x = -SPAWN_SEARCH_RADIUS
    while (x <= SPAWN_SEARCH_RADIUS) {
      var y = -SPAWN_SEARCH_RADIUS
      while (y <= SPAWN_SEARCH_RADIUS) {
        val height = generateHeight(x, y)
        if (height > SEA_LEVEL + 2.0 && height < SEA_LEVEL + 20.0) {
          val n = generateHeight(x, y + 32)
          val s = generateHeight(x, y - 32)
          val e = generateHeight(x + 32, y)
          val w = generateHeight(x - 32, y)
          val max = Math.max(height, Math.max(Math.max(n, s), Math.max(e, w)))
          val min = Math.min(height, Math.min(Math.min(n, s), Math.min(e, w)))
          val relief = max - min

          val distanceSquared = x.toDouble * x + y.toDouble * y
          // Prefer rolling terrain (some relief, but not a cliff face) while
          // keeping the spawn reasonably close to the origin.
          val score = -Math.abs(relief - 7.0) - Math.sqrt(distanceSquared) / 128.0
          if (score > bestScore) {
            bestScore = score
            bestX = x
            bestY = y
          }
        }
        y += SPAWN_SEARCH_STEP
      }
      x += SPAWN_SEARCH_STEP
    }

    (bestX, bestY)
  }

  private def chunkAt(x: Int, y: Int): Chunk = {
    val chunkX = x >> CHUNK_SHIFT
    val chunkY = y >> CHUNK_SHIFT
    if (chunkX == lastChunkX && chunkY == lastChunkY) {
      return lastChunk
    }

    val cacheKey = key(chunkX, chunkY)
    val chunk = chunks.get(cacheKey) match {
      case Some(existing) =>
        touch(cacheKey, existing)
        existing
      case None =>
        val generated = generateChunk(chunkX, chunkY)
        cacheChunk(cacheKey, generated)
        generated
    }

    lastChunkX = chunkX
    lastChunkY = chunkY
    lastChunk = chunk
    chunk
  }

  private def generateChunk(chunkX: Int, chunkY: Int): Chunk = {
    val chunk = new Chunk()
    val baseX = chunkX << CHUNK_SHIFT
    val baseY = chunkY << CHUNK_SHIFT

    var localX = 0
    while (localX < CHUNK_SIZE) {
      val worldX = baseX + localX
      val row = localX * CHUNK_SIZE
      var localY = 0
      while (localY < CHUNK_SIZE) {
        val worldY = baseY + localY
        val rawHeight = generateHeight(worldX, worldY)
        // Keep oceans as a flat water surface, but use the underlying height
        // for deep/shallow water coloring.
        chunk.heights(row + localY) = Math.max(rawHeight, SEA_LEVEL)
        chunk.colors(row + localY) = surfaceColorIndex(worldX, worldY, rawHeight).toByte
        localY += 1
      }
      localX += 1
    }
    chunk
  }

  private def index(x: Int, y: Int): Int =
    (x & CHUNK_MASK) * CHUNK_SIZE + (y & CHUNK_MASK)

  private def cacheChunk(cacheKey: Long, chunk: Chunk): Unit = {
    chunks.put(cacheKey, chunk)
    if (chunks.size > MAX_CACHED_CHUNKS) {
      chunks.remove(chunks.head._1)
    }
  }

  private def touch(cacheKey: Long, chunk: Chunk): Unit = {
    chunks.remove(cacheKey)
    chunks.put(cacheKey, chunk)
  }

  private def key(chunkX: Int, chunkY: Int): Long =
    (chunkX.toLong << 32) ^ (chunkY.toLong & 0xffffffffL)

  /** The world height function. All fields are deterministic functions of world coordinates, which
    * makes chunk generation order-independent.
    */
  private def generateHeight(worldX: Int, worldY: Int): Double = {
    val x = worldX.toDouble
    val y = worldY.toDouble

    // Domain warp breaks up the grid-aligned look of raw Perlin noise and
    // gives coastlines/valleys a more organic shape.
    val warpX = noise.octaveNoise(x * 0.0025 + 101.7, y * 0.0025 + 211.3, 2, 0.5) * 28.0
    val warpY = noise.octaveNoise(x * 0.0025 + 311.9, y * 0.0025 + 401.1, 2, 0.5) * 28.0
    val wx = x + warpX
    val wy = y + warpY

    // Low-frequency continent mask: ocean basins vs. land masses.
    val continent = noise.octaveNoise(wx * 0.0016, wy * 0.0016, 4, 0.5)

    // Erosion controls how rough/flat the land is.
    val erosion = noise.octaveNoise(wx * 0.004 + 1873.0, wy * 0.004 + 2551.0, 3, 0.5)

    // Rolling hills / local detail.
    val hills = noise.octaveNoise(wx * 0.010, wy * 0.010, 5, 0.5)

    // Ridged multifractal for mountain crests.
    val ridge = noise.ridgedNoise(wx * 0.0055 + 4093.0, wy * 0.0055 + 3187.0, 4, 0.5)

    val land = smoothstep(-0.22, 0.18, continent)
    val mountains = smoothstep(-0.08, 0.32, continent) *
      smoothstep(-0.35, 0.35, erosion)

    val base = SEA_LEVEL + continent * 24.0
    var height = base +
      hills * (5.0 + 7.0 * land) * (0.70 + 0.35 * erosion) +
      ridge * 42.0 * mountains

    // Slightly flatten very shallow coastal terrain.
    if (height < SEA_LEVEL + 2.0) {
      height = SEA_LEVEL + (height - SEA_LEVEL) * 0.9
    }
    height
  }

  private def surfaceColorIndex(worldX: Int, worldY: Int, height: Double): Int = {
    if (height < SEA_LEVEL - 3.0) return 0 // deep water
    if (height < SEA_LEVEL) return 1 // shallow water
    if (height < SEA_LEVEL + 1.2) return 2 // beach

    val temperature = noise.octaveNoise(
      worldX * 0.0018 + 5000.0,
      worldY * 0.0018 + 5000.0,
      3,
      0.5
    )
    val moisture = noise.octaveNoise(
      worldX * 0.0015 + 7000.0,
      worldY * 0.0015 + 7000.0,
      3,
      0.5
    )

    val snowLine = SEA_LEVEL + 32.0 + temperature * 8.0
    if (height > snowLine) return 8 // snow

    val rockLine = SEA_LEVEL + 24.0 + temperature * 5.0
    if (height > rockLine) return 7 // rock

    if (temperature > 0.15 && moisture < 0.0) return 3 // desert
    if (moisture > 0.12) return 5 // forest
    if (temperature > 0.05 && moisture < 0.08) return 6 // savanna
    4 // grassland
  }
}
