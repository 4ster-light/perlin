package perlin.output

import perlin.world.WorldMap

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** ANSI-coloured (or plain) ASCII rendering of a generated map. */
object AsciiExporter:

  def render(map: WorldMap, colored: Boolean = true): String =
    val builder = new StringBuilder
    map.rows.foreach { row =>
      row.foreach { sample =>
        val character = Palette.asciiChar(sample.altitude)
        if colored then
          val (red, green, blue) = Palette.rgbComponents(Palette.rgb(sample.biome))
          builder
            .append("\u001b[38;2;")
            .append(red)
            .append(';')
            .append(green)
            .append(';')
            .append(blue)
            .append('m')
            .append(character)
        else builder.append(character)
      }
      if colored then builder.append("\u001b[0m")
      builder.append('\n')
    }
    builder.toString

  def write(map: WorldMap, path: Path, colored: Boolean = false): Path =
    Option(path.toAbsolutePath.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, render(map, colored), StandardCharsets.UTF_8)
    path
