package perlin.output

import perlin.world.TerrainParameters
import perlin.world.WorldGenerator

class ExporterSuite extends munit.FunSuite:

  private val map =
    new WorldGenerator(TerrainParameters(seed = 2024L, octaves = 3)).generate(64, 48)

  test("PNG renderer preserves map dimensions") {
    val image = PngExporter.render(map)
    assertEquals(image.getWidth, 64)
    assertEquals(image.getHeight, 48)
  }

  test("ASCII renderer emits one line per row") {
    val text = AsciiExporter.render(map, colored = false)
    assertEquals(text.linesIterator.length, 48)
    assert(text.nonEmpty)
  }

  test("SVG renderer emits valid-looking contour output") {
    val svg = SvgExporter.render(map, levels = 6)
    assert(svg.startsWith("<?xml"))
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
    assert(svg.contains("</svg>"))
  }
