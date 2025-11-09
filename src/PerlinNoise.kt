import kotlin.math.floor

class PerlinNoise(seed: Long = System.currentTimeMillis()) {
    private val permutation = IntArray(512)

    init {
        // Initialize permutation table with Fisher-Yates shuffle
        val p = IntArray(256) { it }
        val random = java.util.Random(seed)

        for (i in 255 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = p[i]
            p[i] = p[j]
            p[j] = temp
        }

        // Duplicate the permutation table
        for (i in 0..255) {
            permutation[i] = p[i]
            permutation[i + 256] = p[i]
        }
    }

    /**
     * Generate 2D Perlin noise value at coordinates (x, y)
     * - Returns value between -1 and 1
     */
    fun noise(x: Double, y: Double): Double {
        // Find unit grid cell containing point
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255

        // Get relative xy coordinates of point within cell
        val xf = x - floor(x)
        val yf = y - floor(y)

        // Compute fade curves
        val u = fade(xf)
        val v = fade(yf)

        // Hash coordinates of the 4 corners
        val aa = permutation[permutation[xi] + yi]
        val ab = permutation[permutation[xi] + yi + 1]
        val ba = permutation[permutation[xi + 1] + yi]
        val bb = permutation[permutation[xi + 1] + yi + 1]

        // Blend results from 4 corners
        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)

        return lerp(x1, x2, v)
    }

    /**
     * Generate octave Perlin noise (fractal noise with multiple frequencies)
     */
    fun octaveNoise(x: Double, y: Double, octaves: Int, persistence: Double = 0.5): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxValue = 0.0

        repeat(octaves) {
            total += noise(x * frequency, y * frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= 2.0
        }

        return total / maxValue
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)

    private fun lerp(a: Double, b: Double, t: Double): Double = a + t * (b - a)

    private fun grad(hash: Int, x: Double, y: Double): Double {
        // Convert low 2 bits of hash code into 4 gradient directions
        val h = hash and 3
        val u = if (h < 2) x else y
        val v = if (h < 2) y else x
        return (if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v)
    }
}
