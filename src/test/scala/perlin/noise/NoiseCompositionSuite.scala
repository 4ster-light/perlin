package perlin.noise

import perlin.core.WorldCoordinate
import perlin.noise.Noise.*

class NoiseCompositionSuite extends munit.FunSuite:

  private val base = Noise.perlin(7L)
  private val other = Noise.perlin(8L)
  private val x = WorldCoordinate(1.25)
  private val y = WorldCoordinate(-3.75)

  test("fBm is deterministic and bounded") {
    val first = Noise.fBm(base, 5)
    val second = Noise.fBm(Noise.perlin(7L), 5)
    assertEqualsDouble(first(x, y), second(x, y), 0.0)
    assert(first(x, y) >= -1.0 && first(x, y) <= 1.0)
  }

  test("ridged noise is non-negative") {
    val ridge = Noise.ridged(base, 4)
    for
      px <- -5 to 5
      py <- -5 to 5
    do
      val value = ridge(WorldCoordinate(px * 0.3), WorldCoordinate(py * 0.3))
      assert(value >= 0.0 && value <= 1.0, s"ridged value $value at ($px, $py)")
  }

  test("blend uses the mask") {
    val a = Noise.constant(0.0)
    val b = Noise.constant(10.0)
    assertEqualsDouble(Noise.mix(a, b, Noise.constant(0.0))(x, y), 0.0, 0.0)
    assertEqualsDouble(Noise.mix(a, b, Noise.constant(1.0))(x, y), 10.0, 0.0)
    assertEqualsDouble(Noise.mix(a, b, Noise.constant(0.5))(x, y), 5.0, 0.0)
    assertEqualsDouble(a.blend(b)(x, y), 5.0, 0.0)
  }

  test("domain warping is deterministic") {
    val warped = Noise.warp(base, other, Noise.perlin(9L), 0.25)
    assertEqualsDouble(warped(x, y), warped(x, y), 0.0)
    assert(warped(x, y) >= -1.0 && warped(x, y) <= 1.0)
  }

  test("map and pow compose without changing determinism") {
    val composed = Noise.unit(Noise.fBm(base, 4)).pow(1.3)
    assertEqualsDouble(composed(x, y), composed(x, y), 0.0)
    assert(composed(x, y) >= 0.0 && composed(x, y) <= 1.0)
  }
