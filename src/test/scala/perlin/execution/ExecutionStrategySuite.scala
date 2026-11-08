package perlin.execution

import perlin.world.TerrainParameters
import perlin.world.WorldGenerator

class ExecutionStrategySuite extends munit.FunSuite:

  test("all strategies produce the same rows") {
    val generator = new WorldGenerator(TerrainParameters(seed = 2024L, octaves = 4))
    val expected = ExecutionStrategy.Sequential.generateRows(generator, 16, 12)
    ExecutionStrategy.all.foreach { strategy =>
      val actual = strategy.generateRows(generator, 16, 12)
      assertEquals(actual, expected, clue = strategy.name)
    }
  }

  test("strategy names parse case-insensitively") {
    assertEquals(ExecutionStrategy.parse("SEQUENTIAL"), Right(ExecutionStrategy.Sequential))
    assertEquals(ExecutionStrategy.parse("par"), Right(ExecutionStrategy.Parallel))
    assertEquals(ExecutionStrategy.parse("chunk"), Right(ExecutionStrategy.Chunked))
    assert(ExecutionStrategy.parse("unknown").isLeft)
  }
