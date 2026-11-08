package perlin.world

import perlin.core.Altitude
import perlin.core.Altitude.*
import perlin.core.Latitude
import perlin.core.Moisture
import perlin.core.Moisture.*

class BiomeSuite extends munit.FunSuite:

  private final case class Row(
      altitude: Double,
      moisture: Double,
      latitude: Double,
      expected: Biome
  )

  private val rows = Seq(
    Row(0.10, 0.50, 0.0, Biome.Ocean),
    Row(0.32, 0.50, 0.0, Biome.Beach),
    Row(0.40, 0.80, 0.0, Biome.Forest),
    Row(0.40, 0.10, 0.0, Biome.Desert),
    Row(0.40, 0.40, 70.0, Biome.Tundra),
    Row(0.50, 0.50, 0.0, Biome.Grassland),
    Row(0.75, 0.50, 0.0, Biome.Mountain),
    Row(0.90, 0.50, 0.0, Biome.SnowPeak),
    Row(0.80, 0.50, 80.0, Biome.SnowPeak)
  )

  test("biome classification table") {
    rows.foreach { row =>
      val actual = Biome.classify(
        Altitude(row.altitude),
        Moisture(row.moisture),
        Latitude(row.latitude)
      )
      assertEquals(actual, row.expected, clue = s"row $row")
    }
  }

  test("only ocean is water") {
    rows.foreach { row =>
      assertEquals(Biome.isWater(row.expected), row.expected == Biome.Ocean)
    }
  }

  test("altitude and moisture constructors clamp their inputs") {
    assertEqualsDouble(Altitude(-1.0).value, 0.0, 0.0)
    assertEqualsDouble(Altitude(2.0).value, 1.0, 0.0)
    assertEqualsDouble(Moisture(-1.0).value, 0.0, 0.0)
    assertEqualsDouble(Moisture(2.0).value, 1.0, 0.0)
  }
