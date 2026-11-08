package perlin.world

import perlin.core.Altitude
import perlin.core.GridCell
import perlin.core.GridCell.*
import perlin.core.Latitude
import perlin.core.Moisture
import perlin.core.WorldCoordinate
import perlin.core.WorldCoordinate.*
import perlin.noise.Noise
import perlin.noise.Noise.*

/** Pure, seeded world generator built from composable noise layers. */
final class WorldGenerator(val parameters: TerrainParameters):

  private val seed = parameters.seed

  private val continent =
    Noise.fBm(
      Noise.perlin(seed),
      parameters.octaves,
      parameters.lacunarity,
      parameters.persistence
    )

  private val hills =
    Noise.fBm(
      Noise.perlin(seed + 1L),
      Math.max(2, parameters.octaves - 2),
      parameters.lacunarity,
      parameters.persistence
    )

  private val mountains =
    Noise.ridged(
      Noise.perlin(seed + 2L),
      Math.max(3, parameters.octaves - 2),
      parameters.lacunarity,
      parameters.persistence
    )

  private val warpX = Noise.perlin(seed + 3L)
  private val warpY = Noise.perlin(seed + 4L)

  private val moistureNoise =
    Noise.fBm(
      Noise.perlin(seed + 5L),
      Math.max(2, parameters.octaves - 3),
      parameters.lacunarity,
      parameters.persistence
    )

  private val moistureVariation =
    Noise.fBm(Noise.perlin(seed + 6L), 3, parameters.lacunarity, parameters.persistence)

  /** Continent shape, rolling hills and mountain ridges combined with masks. */
  private val elevation: Noise[Double] =
    val land = Noise.unit(continent)
    val detail = Noise.unit(hills)
    val ridge = mountains.clamp01
    val mountainMask =
      land.map(value => Noise.smoothstep(parameters.mountainStart, parameters.mountainEnd, value))
    val baseLand = Noise.mix(land, detail, Noise.constant(0.35))
    val withMountains = Noise.mix(baseLand, ridge, mountainMask)
    Noise
      .warp(withMountains, warpX, warpY, parameters.warpStrength)
      .clamp01
      .map(value => Noise.smoothstep(0.16, 0.52, value))

  private val moistureField: Noise[Double] =
    val wet = Noise.unit(moistureNoise)
    val variation = Noise.unit(moistureVariation)
    Noise
      .mix(wet, variation, Noise.constant(0.30))
      .clamp01
      .map(value => Noise.smoothstep(0.36, 0.62, value))

  /** Sample one grid cell. `mapHeight` is used only to derive latitude. */
  def sample(x: GridCell, y: GridCell, mapHeight: Int): TerrainSample =
    val worldX = WorldCoordinate(x.toDouble * parameters.scale)
    val worldY = WorldCoordinate(y.toDouble * parameters.scale)
    val altitude = Altitude(elevation(worldX, worldY))
    val moisture = Moisture(
      moistureField(worldX * parameters.moistureScale, worldY * parameters.moistureScale)
    )
    val latitude = latitudeFor(y, mapHeight)
    TerrainSample(altitude, moisture, Biome.classify(altitude, moisture, latitude))

  /** Stream a map row by row. Nothing is generated until a row is demanded. */
  def generate(width: Int, height: Int): WorldMap =
    require(width > 0, "width must be positive")
    require(height > 0, "height must be positive")
    val rows = LazyList.range(0, height).map { y =>
      LazyList.range(0, width).map { x =>
        sample(GridCell(x), GridCell(y), height)
      }
    }
    WorldMap(width, height, rows)

  /** Stream the world as realised chunks, useful for bounded-memory consumers. */
  def generateChunks(width: Int, height: Int, chunkSize: Int = 64): LazyList[WorldChunk] =
    require(width > 0 && height > 0, "dimensions must be positive")
    require(chunkSize > 0, "chunkSize must be positive")
    val chunkColumns = (width + chunkSize - 1) / chunkSize
    val chunkRows = (height + chunkSize - 1) / chunkSize

    LazyList.range(0, chunkRows).flatMap { chunkY =>
      LazyList.range(0, chunkColumns).map { chunkX =>
        val originX = chunkX * chunkSize
        val originY = chunkY * chunkSize
        val actualWidth = Math.min(chunkSize, width - originX)
        val actualHeight = Math.min(chunkSize, height - originY)
        val rows = (0 until actualHeight).map { dy =>
          (0 until actualWidth).map { dx =>
            sample(GridCell(originX + dx), GridCell(originY + dy), height)
          }.toIndexedSeq
        }.toIndexedSeq
        WorldChunk(originX, originY, actualWidth, actualHeight, rows)
      }
    }

  private def latitudeFor(y: GridCell, mapHeight: Int): Latitude =
    if mapHeight <= 1 then Latitude.Equator
    else Latitude((y.toDouble / (mapHeight - 1).toDouble) * 180.0 - 90.0)
