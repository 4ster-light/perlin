package perlin.world

import perlin.core.Altitude.*
import perlin.core.Moisture.*

class WorldGeneratorSuite extends munit.FunSuite:

  private val parameters = TerrainParameters(seed = 12345L, octaves = 4)

  test("the same seed always generates the same map") {
    val first = new WorldGenerator(parameters).generate(24, 18).toIndexedSeq
    val second = new WorldGenerator(parameters).generate(24, 18).toIndexedSeq
    assertEquals(first, second)
  }

  test("different seeds generate different maps") {
    val first = new WorldGenerator(parameters).generate(24, 18).toIndexedSeq
    val second = new WorldGenerator(parameters.copy(seed = 54321L)).generate(24, 18).toIndexedSeq
    assertNotEquals(first, second)
  }

  test("altitude and moisture stay normalised") {
    val map = new WorldGenerator(parameters).generate(32, 32)
    map.rows.foreach { row =>
      row.foreach { sample =>
        assert(sample.altitude.value >= 0.0 && sample.altitude.value <= 1.0)
        assert(sample.moisture.value >= 0.0 && sample.moisture.value <= 1.0)
      }
    }
  }

  test("streamed chunks match the row-oriented map") {
    val generator = new WorldGenerator(parameters)
    val map = generator.generate(37, 29).toIndexedSeq
    val chunks = generator.generateChunks(37, 29, chunkSize = 8).toIndexedSeq

    chunks.foreach { chunk =>
      for
        y <- 0 until chunk.height
        x <- 0 until chunk.width
      do assertEquals(chunk.rows(y)(x), map(chunk.originY + y)(chunk.originX + x))
    }
  }

  test("biome counts cover every sample") {
    val map = new WorldGenerator(parameters).generate(20, 15)
    assertEquals(map.biomeCounts.values.sum, 20 * 15)
  }

  test("ocean samples sit below the beach threshold") {
    val map = new WorldGenerator(parameters).generate(64, 64)
    map.rows.foreach { row =>
      row.foreach { sample =>
        if sample.biome == Biome.Ocean then assert(sample.altitude.value < 0.30)
      }
    }
  }
