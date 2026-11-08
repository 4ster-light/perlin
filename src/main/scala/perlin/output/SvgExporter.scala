package perlin.output

import perlin.world.WorldMap

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** SVG export with layered marching-squares terrain contours. */
object SvgExporter:

  private type Point = (Double, Double)
  private type Segment = (Point, Point)

  def render(map: WorldMap, levels: Int = 8): String =
    require(levels > 0, "levels must be positive")
    val grid = map.toIndexedSeq
    val builder = new StringBuilder

    builder
      .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
      .append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
      .append(s"width=\"${map.width}\" height=\"${map.height}\" ")
      .append(s"viewBox=\"0 0 ${map.width} ${map.height}\">\n")
      .append("  <rect width=\"100%\" height=\"100%\" fill=\"#0d1117\"/>\n")
      .append("  <g fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n")

    var level = 1
    while level <= levels do
      val threshold = level.toDouble / (levels + 1).toDouble
      val segments = contourSegments(grid, threshold)
      if segments.nonEmpty then
        val colour = contourColour(level, levels)
        builder
          .append(s"    <path stroke=\"$colour\" stroke-width=\"1.1\" opacity=\"0.9\" d=\"")
        segments.foreach { case ((x1, y1), (x2, y2)) =>
          builder
            .append('M')
            .append(format(x1))
            .append(' ')
            .append(format(y1))
            .append('L')
            .append(format(x2))
            .append(' ')
            .append(format(y2))
        }
        builder.append("\"/>\n")
      level += 1

    builder
      .append("  </g>\n")
      .append("</svg>\n")
      .toString

  def write(map: WorldMap, path: Path, levels: Int = 8): Path =
    Option(path.toAbsolutePath.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, render(map, levels), StandardCharsets.UTF_8)
    path

  private def contourSegments(
      grid: IndexedSeq[IndexedSeq[perlin.world.TerrainSample]],
      threshold: Double
  ): IndexedSeq[Segment] =
    val segments = IndexedSeq.newBuilder[Segment]
    var y = 0
    while y < grid.length - 1 do
      val row = grid(y)
      val nextRow = grid(y + 1)
      var x = 0
      while x < row.length - 1 do
        val topLeft = row(x).altitude.value
        val topRight = row(x + 1).altitude.value
        val bottomRight = nextRow(x + 1).altitude.value
        val bottomLeft = nextRow(x).altitude.value
        segments ++=
          marchingSquares(x, y, topLeft, topRight, bottomRight, bottomLeft, threshold)
        x += 1
      y += 1
    segments.result()

  private def marchingSquares(
      x: Int,
      y: Int,
      topLeft: Double,
      topRight: Double,
      bottomRight: Double,
      bottomLeft: Double,
      threshold: Double
  ): List[Segment] =
    val caseIndex =
      (if topLeft >= threshold then 1 else 0) |
        (if topRight >= threshold then 2 else 0) |
        (if bottomRight >= threshold then 4 else 0) |
        (if bottomLeft >= threshold then 8 else 0)

    def top: Point =
      (x + interpolate(topLeft, topRight, threshold), y.toDouble)

    def right: Point =
      (x + 1.0, y + interpolate(topRight, bottomRight, threshold))

    def bottom: Point =
      (x + interpolate(bottomLeft, bottomRight, threshold), y + 1.0)

    def left: Point =
      (x.toDouble, y + interpolate(topLeft, bottomLeft, threshold))

    caseIndex match
      case 0 | 15 => Nil
      case 1 | 14 => List((left, top))
      case 2 | 13 => List((top, right))
      case 3 | 12 => List((left, right))
      case 4 | 11 => List((right, bottom))
      case 5      => List((left, top), (right, bottom))
      case 6 | 9  => List((top, bottom))
      case 7 | 8  => List((left, bottom))
      case 10     => List((left, bottom), (top, right))
      case _      => Nil

  private def interpolate(value0: Double, value1: Double, threshold: Double): Double =
    val delta = value1 - value0
    if Math.abs(delta) < 1e-12 then 0.5 else (threshold - value0) / delta

  private def contourColour(level: Int, levels: Int): String =
    val t = level.toDouble / (levels + 1).toDouble
    val red = (80 + 160 * t).toInt
    val green = (140 + 100 * (1.0 - t)).toInt
    val blue = (220 - 120 * t).toInt
    f"#$red%02x$green%02x$blue%02x"

  private def format(value: Double): String =
    String.format(Locale.ROOT, "%.3f", value)
