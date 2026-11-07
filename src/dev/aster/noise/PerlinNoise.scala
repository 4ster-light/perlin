package dev.aster.noise

import java.util.Random

/** Classic 2D Perlin noise with a seeded permutation table. */
final class PerlinNoise(seed: Long) {

  private val permutation = new Array[Int](512)

  def this() = this(System.currentTimeMillis())

  locally {
    val p = new Array[Int](256)
    var i = 0
    while (i < 256) {
      p(i) = i
      i += 1
    }

    val random = new Random(seed)
    i = 255
    while (i >= 1) {
      val j = random.nextInt(i + 1)
      val temp = p(i)
      p(i) = p(j)
      p(j) = temp
      i -= 1
    }

    i = 0
    while (i <= 255) {
      permutation(i) = p(i)
      permutation(i + 256) = p(i)
      i += 1
    }
  }

  /** Generate a 2D Perlin noise value at coordinates (x, y), between -1 and 1. */
  def noise(x: Double, y: Double): Double = {
    val floorX = Math.floor(x)
    val floorY = Math.floor(y)
    val xi = floorX.toInt & 255
    val yi = floorY.toInt & 255

    val xf = x - floorX
    val yf = y - floorY

    val u = fade(xf)
    val v = fade(yf)

    val aa = permutation(permutation(xi) + yi)
    val ab = permutation(permutation(xi) + yi + 1)
    val ba = permutation(permutation(xi + 1) + yi)
    val bb = permutation(permutation(xi + 1) + yi + 1)

    val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
    val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)

    lerp(x1, x2, v)
  }

  /** Fractal (octave) noise with multiple frequencies. */
  def octaveNoise(x: Double, y: Double, octaves: Int, persistence: Double): Double = {
    var total = 0.0
    var frequency = 1.0
    var amplitude = 1.0
    var maxValue = 0.0
    var i = 0
    while (i < octaves) {
      total += noise(x * frequency, y * frequency) * amplitude
      maxValue += amplitude
      amplitude *= persistence
      frequency *= 2.0
      i += 1
    }
    total / maxValue
  }

  def octaveNoise(x: Double, y: Double, octaves: Int): Double =
    octaveNoise(x, y, octaves, 0.5)

  /**
   * Ridged multifractal noise, useful for mountain crests and sharp valleys.
   *
   * @return normalized value roughly between 0 and 1
   */
  def ridgedNoise(x: Double, y: Double, octaves: Int, persistence: Double): Double = {
    var total = 0.0
    var frequency = 1.0
    var amplitude = 1.0
    var maxValue = 0.0
    var weight = 1.0
    var i = 0
    while (i < octaves) {
      var ridge = 1.0 - Math.abs(noise(x * frequency, y * frequency))
      ridge *= ridge
      ridge *= weight
      weight = Math.clamp(ridge * 2.0, 0.0, 1.0)

      total += ridge * amplitude
      maxValue += amplitude
      amplitude *= persistence
      frequency *= 2.0
      i += 1
    }
    total / maxValue
  }

  private def fade(t: Double): Double =
    t * t * t * (t * (t * 6 - 15) + 10)

  private def lerp(a: Double, b: Double, t: Double): Double =
    a + t * (b - a)

  private def grad(hash: Int, x: Double, y: Double): Double = {
    val h = hash & 3
    val u = if h < 2 then x else y
    val v = if h < 2 then y else x
    (if (h & 1) == 0 then u else -u) + (if (h & 2) == 0 then v else -v)
  }
}
