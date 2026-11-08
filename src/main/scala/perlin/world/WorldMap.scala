package perlin.world

import perlin.core.Altitude
import perlin.core.Moisture

/** Parameters that fully determine a generated world. */
final case class TerrainParameters(
    seed: Long = 1337L,
    octaves: Int = 6,
    lacunarity: Double = 2.0,
    persistence: Double = 0.5,
    scale: Double = 0.0025,
    warpStrength: Double = 0.35,
    moistureScale: Double = 1.7,
    mountainStart: Double = 0.55,
    mountainEnd: Double = 0.82
):
  require(octaves > 0, "octaves must be positive")
  require(lacunarity > 0.0, "lacunarity must be positive")
  require(persistence > 0.0, "persistence must be positive")
  require(scale > 0.0, "scale must be positive")

/** One point in the generated world. */
final case class TerrainSample(
    altitude: Altitude,
    moisture: Moisture,
    biome: Biome
)

/** A generated map. Rows are lazy so callers can stream without materialising the whole world;
  * export code can realise only the rows it needs.
  */
final case class WorldMap(
    width: Int,
    height: Int,
    rows: LazyList[LazyList[TerrainSample]]
):
  require(width > 0, "width must be positive")
  require(height > 0, "height must be positive")

  def apply(x: Int, y: Int): TerrainSample = rows(y)(x)

  def row(y: Int): LazyList[TerrainSample] = rows(y)

  def toIndexedSeq: IndexedSeq[IndexedSeq[TerrainSample]] =
    rows.map(_.toIndexedSeq).toIndexedSeq

  def biomeCounts: Map[Biome, Int] =
    rows.iterator.flatten.foldLeft(Map.empty[Biome, Int]) { (counts, sample) =>
      counts.updated(sample.biome, counts.getOrElse(sample.biome, 0) + 1)
    }

/** A realised, rectangular chunk of a streamed world. */
final case class WorldChunk(
    originX: Int,
    originY: Int,
    width: Int,
    height: Int,
    rows: IndexedSeq[IndexedSeq[TerrainSample]]
)
