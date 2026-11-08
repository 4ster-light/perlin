package perlin.noise

class PerlinNoiseSuite extends munit.FunSuite:

  test("permutation table matches reference vectors for seed 42") {
    val expected = IndexedSeq(
      132, 41, 68, 149, 38, 219, 124, 63, 162, 189, 190, 99, 234, 237, 151, 202, 143, 24, 44, 161,
      231, 171, 204, 195, 66, 71, 70, 226, 95, 225, 101, 18
    )
    val actual = new PerlinNoise(42L).permutationTable.take(expected.length)
    assertEquals(actual, expected)
  }

  test("noise matches hardcoded reference vectors") {
    val noise = new PerlinNoise(42L)
    val references = Seq(
      (0.0, 0.0, 0.0),
      (0.5, 0.5, -0.5),
      (1.25, 3.75, -0.571369171143),
      (-2.5, 4.25, -0.3017578125),
      (10.0, -7.5, 0.0),
      (123.456, 789.012, -0.480785355531)
    )
    references.foreach { case (x, y, expected) =>
      assertEqualsDouble(noise.noise(x, y), expected, 1e-11)
    }
  }

  test("octave and ridged reference values") {
    val noise = new PerlinNoise(42L)
    assertEqualsDouble(noise.octaveNoise(0.5, 0.5, 5, 0.5), -0.258064516129, 1e-11)
    assertEqualsDouble(noise.ridgedNoise(0.5, 0.5, 4, 0.5), 0.466666666667, 1e-11)
  }

  test("noise stays inside the documented range") {
    val noise = new PerlinNoise(7L)
    for
      x <- -10 to 10
      y <- -10 to 10
    do
      val value = noise.noise(x * 0.37, y * 0.61)
      assert(value >= -1.0 && value <= 1.0, s"value $value at ($x, $y)")
  }

  test("gradient interpolation is continuous across cell boundaries") {
    val noise = new PerlinNoise(1234L)
    val epsilon = 1e-6
    for
      x <- -3 to 3
      y <- -3 to 3
    do
      val left = noise.noise(x - epsilon, y + 0.37)
      val right = noise.noise(x + epsilon, y + 0.37)
      assert(Math.abs(left - right) < 1e-3, s"discontinuity at ($x, $y): $left vs $right")
  }

  test("the same seed always produces the same field") {
    val first = new PerlinNoise(99L)
    val second = new PerlinNoise(99L)
    for
      x <- 0 until 20
      y <- 0 until 20
    do assertEqualsDouble(first.noise(x * 0.1, y * 0.1), second.noise(x * 0.1, y * 0.1), 0.0)
  }

  test("different seeds produce different fields") {
    val first = new PerlinNoise(1L)
    val second = new PerlinNoise(2L)
    val differences = for
      x <- 0 until 20
      y <- 0 until 20
    yield Math.abs(first.noise(x * 0.1, y * 0.1) - second.noise(x * 0.1, y * 0.1))
    assert(differences.exists(_ > 1e-6))
  }
