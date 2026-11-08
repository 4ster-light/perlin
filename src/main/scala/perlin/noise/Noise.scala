package perlin.noise

import perlin.core.WorldCoordinate
import perlin.core.WorldCoordinate.*

/** A pure, composable noise field.
  *
  * `Noise[A]` is just a function from a world coordinate to a value. Keeping it as a value (instead
  * of a class hierarchy) means layers can be built with ordinary function composition and sampled
  * without any hidden state.
  */
final case class Noise[A](sample: (WorldCoordinate, WorldCoordinate) => A):

  def apply(x: WorldCoordinate, y: WorldCoordinate): A = sample(x, y)

  def map[B](f: A => B): Noise[B] =
    Noise((x, y) => f(sample(x, y)))

  def zip[B](other: Noise[B]): Noise[(A, B)] =
    Noise((x, y) => (sample(x, y), other(x, y)))

  def offset(dx: Double, dy: Double): Noise[A] =
    Noise((x, y) => sample(x + WorldCoordinate(dx), y + WorldCoordinate(dy)))

  def scale(factor: Double): Noise[A] =
    Noise((x, y) => sample(x * factor, y * factor))

object Noise:

  export _root_.perlin.core.NumericOps.*

  def constant[A](value: A): Noise[A] =
    Noise((_, _) => value)

  /** Classic 2D Perlin noise in `[-1, 1]`, seeded explicitly. */
  def perlin(seed: Long): Noise[Double] =
    val generator = new PerlinNoise(seed)
    Noise((x, y) => generator.noise(x.value, y.value))

  /** Map a `[-1, 1]` noise field to `[0, 1]`. */
  def unit(noise: Noise[Double]): Noise[Double] =
    noise.map(value => Math.clamp((value + 1.0) * 0.5, 0.0, 1.0))

  def smoothstep(edge0: Double, edge1: Double, value: Double): Double =
    val t = Math.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0)
    t * t * (3.0 - 2.0 * t)

  /** Fractal Brownian motion: several octaves of the same base field. */
  def fBm(
      base: Noise[Double],
      octaves: Int,
      lacunarity: Double = 2.0,
      persistence: Double = 0.5
  ): Noise[Double] =
    require(octaves > 0, "octaves must be positive")
    Noise { (x, y) =>
      var total = 0.0
      var frequency = 1.0
      var amplitude = 1.0
      var maxValue = 0.0
      var octave = 0
      while octave < octaves do
        total += base(x * frequency, y * frequency) * amplitude
        maxValue += amplitude
        amplitude *= persistence
        frequency *= lacunarity
        octave += 1
      total / maxValue
    }

  /** Ridged multifractal noise in `[0, 1]`, useful for mountain crests. */
  def ridged(
      base: Noise[Double],
      octaves: Int,
      lacunarity: Double = 2.0,
      persistence: Double = 0.5
  ): Noise[Double] =
    require(octaves > 0, "octaves must be positive")
    Noise { (x, y) =>
      var total = 0.0
      var frequency = 1.0
      var amplitude = 1.0
      var maxValue = 0.0
      var weight = 1.0
      var octave = 0
      while octave < octaves do
        var ridge = 1.0 - Math.abs(base(x * frequency, y * frequency))
        ridge *= ridge
        ridge *= weight
        weight = Math.clamp(ridge * 2.0, 0.0, 1.0)
        total += ridge * amplitude
        maxValue += amplitude
        amplitude *= persistence
        frequency *= lacunarity
        octave += 1
      total / maxValue
    }

  /** Domain warping: offset the sample point by another noise field. */
  def warp(
      base: Noise[Double],
      warpX: Noise[Double],
      warpY: Noise[Double],
      strength: Double
  ): Noise[Double] =
    Noise { (x, y) =>
      val offsetX = WorldCoordinate(warpX(x, y) * strength)
      val offsetY = WorldCoordinate(warpY(x, y) * strength)
      base(x + offsetX, y + offsetY)
    }

  /** Linear interpolation between two fields. The mask is expected in `[0, 1]`. */
  def mix(a: Noise[Double], b: Noise[Double], mask: Noise[Double]): Noise[Double] =
    Noise { (x, y) =>
      val t = Math.clamp(mask(x, y), 0.0, 1.0)
      a(x, y) * (1.0 - t) + b(x, y) * t
    }

  extension (noise: Noise[Double])
    /** Blend with another layer. `a blend b` uses an even 50/50 mix. */
    def blend(other: Noise[Double], mask: Noise[Double] = constant(0.5)): Noise[Double] =
      Noise.mix(noise, other, mask)

    def clamp01: Noise[Double] =
      noise.map(value => Math.clamp(value, 0.0, 1.0))

    def pow(exponent: Double): Noise[Double] =
      noise.map(value => Math.pow(Math.max(0.0, value), exponent))
