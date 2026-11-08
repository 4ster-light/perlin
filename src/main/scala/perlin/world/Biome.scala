package perlin.world

import perlin.core.Altitude
import perlin.core.Altitude.*
import perlin.core.Latitude
import perlin.core.Latitude.*
import perlin.core.Moisture
import perlin.core.Moisture.*

/** Exhaustive biome ADT for the generated world. */
enum Biome:
  case Ocean, Beach, Grassland, Forest, Desert, Tundra, Mountain, SnowPeak

object Biome:

  /** Classify a point from its altitude, moisture and latitude.
    *
    * The match has a final catch-all so the compiler can prove exhaustiveness even though most
    * branches use guard clauses. Add a new `Biome` case and the compiler will still force us to
    * handle it here.
    */
  def classify(altitude: Altitude, moisture: Moisture, latitude: Latitude): Biome =
    val height = altitude.value
    val wetness = moisture.value
    val absoluteLatitude = latitude.absolute

    (height, wetness, absoluteLatitude) match
      case (height, _, _) if height < 0.30 => Biome.Ocean
      case (height, _, _) if height < 0.36 => Biome.Beach
      case (height, _, latitude) if height > 0.86 || (latitude > 72.0 && height > 0.78) =>
        Biome.SnowPeak
      case (height, _, _) if height > 0.72                       => Biome.Mountain
      case (_, _, latitude) if latitude > 60.0 && wetness < 0.48 => Biome.Tundra
      case (height, _, _) if wetness < 0.30 && height < 0.58     => Biome.Desert
      case (_, _, _) if wetness > 0.62                           => Biome.Forest
      case _                                                     => Biome.Grassland

  def isWater(biome: Biome): Boolean = biome match
    case Biome.Ocean => true
    case Biome.Beach | Biome.Grassland | Biome.Forest | Biome.Desert | Biome.Tundra |
        Biome.Mountain | Biome.SnowPeak =>
      false
