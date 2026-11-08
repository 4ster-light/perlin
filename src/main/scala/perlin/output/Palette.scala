package perlin.output

import perlin.core.Altitude
import perlin.core.Altitude.*
import perlin.world.Biome

/** Shared colours for PNG, SVG and ANSI ASCII output. */
object Palette:

  private val ASCII_RAMP = " .:-=+*#%@"

  def rgb(biome: Biome): Int = biome match
    case Biome.Ocean     => 0x1e50b4
    case Biome.Beach     => 0xd6be91
    case Biome.Grassland => 0x60a04a
    case Biome.Forest    => 0x286930
    case Biome.Desert    => 0xdecc91
    case Biome.Tundra    => 0xa0aaa0
    case Biome.Mountain  => 0x8b5a2b
    case Biome.SnowPeak  => 0xf5f7ff

  def asciiChar(altitude: Altitude): Char =
    val index = Math.clamp(
      (altitude.value * (ASCII_RAMP.length - 1)).toInt,
      0,
      ASCII_RAMP.length - 1
    )
    ASCII_RAMP.charAt(index)

  def rgbComponents(rgb: Int): (Int, Int, Int) =
    ((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff)
