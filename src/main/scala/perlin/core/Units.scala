package perlin.core

/** A coordinate in world space. */
opaque type WorldCoordinate = Double

object WorldCoordinate:
  def apply(value: Double): WorldCoordinate = value
  val Zero: WorldCoordinate = 0.0

  extension (coordinate: WorldCoordinate)
    def value: Double = coordinate
    def +(other: WorldCoordinate): WorldCoordinate = coordinate + other
    def -(other: WorldCoordinate): WorldCoordinate = coordinate - other
    def *(factor: Double): WorldCoordinate = coordinate * factor
    def /(divisor: Double): WorldCoordinate = coordinate / divisor
    def floor: GridCell = GridCell(Math.floor(coordinate).toInt)
    def toDouble: Double = coordinate

/** An integer cell in the generation grid. */
opaque type GridCell = Int

object GridCell:
  def apply(value: Int): GridCell = value
  val Zero: GridCell = 0

  extension (cell: GridCell)
    def value: Int = cell
    def +(offset: Int): GridCell = cell + offset
    def toWorldCoordinate: WorldCoordinate = cell.toDouble
    def toDouble: Double = cell.toDouble

/** Normalised height in `[0, 1]`. */
opaque type Altitude = Double

object Altitude:
  def apply(value: Double): Altitude = clamp01(value)
  def unsafe(value: Double): Altitude = value
  val Min: Altitude = 0.0
  val Max: Altitude = 1.0
  val SeaLevel: Altitude = 0.30

  extension (altitude: Altitude)
    def value: Double = altitude
    def isAbove(other: Altitude): Boolean = altitude > other
    def isBelow(other: Altitude): Boolean = altitude < other
    def toUnit: Double = altitude

/** Normalised moisture in `[0, 1]`. */
opaque type Moisture = Double

object Moisture:
  def apply(value: Double): Moisture = clamp01(value)
  def unsafe(value: Double): Moisture = value
  val Min: Moisture = 0.0
  val Max: Moisture = 1.0

  extension (moisture: Moisture)
    def value: Double = moisture
    def isAbove(other: Moisture): Boolean = moisture > other
    def isBelow(other: Moisture): Boolean = moisture < other
    def toUnit: Double = moisture

/** Latitude in degrees, clamped to `[-90, 90]`. */
opaque type Latitude = Double

object Latitude:
  def apply(degrees: Double): Latitude = Math.clamp(degrees, -90.0, 90.0)
  val Equator: Latitude = 0.0

  extension (latitude: Latitude)
    def value: Double = latitude
    def absolute: Double = Math.abs(latitude)

private def clamp01(value: Double): Double =
  if value < 0.0 then 0.0
  else if value > 1.0 then 1.0
  else value

/** Small numeric conveniences shared by the noise and world packages. */
object NumericOps:
  extension (value: Double) def pow(exponent: Double): Double = Math.pow(value, exponent)
