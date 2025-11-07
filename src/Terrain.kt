import java.awt.Color

class Terrain(
    val width: Int,
    val height: Int,
    perlinNoise: PerlinNoise,
    scale: Double = 0.05,
    octaves: Int = 4,
    private val heightMultiplier: Double = 50.0
) {
    val heightMap: Array<DoubleArray> = Array(width) { DoubleArray(height) }

    init {
        for (x in 0 until width) {
            for (y in 0 until height) {
                // Generate noise value with multiple octaves for more detail
                val noiseValue = perlinNoise.octaveNoise(x * scale, y * scale, octaves)

                // Map noise value from [-1, 1] to height
                heightMap[x][y] = (noiseValue + 1.0) * 0.5 * heightMultiplier
            }
        }
    }

    fun getHeight(x: Int, y: Int): Double {
        if (x !in 0..<width || y < 0 || y >= height) return 0.0
        return heightMap[x][y]
    }

    fun getColorForHeight(height: Double): Color {
        return when {
            height < heightMultiplier * 0.3 -> Color(30, 80, 180)  // Water - deep blue
            height < heightMultiplier * 0.35 -> Color(60, 120, 200) // Shallow water
            height < heightMultiplier * 0.4 -> Color(210, 180, 140) // Sand - beach
            height < heightMultiplier * 0.6 -> Color(34, 139, 34)   // Grass - green
            height < heightMultiplier * 0.75 -> Color(139, 90, 43)  // Rock - brown
            else -> Color(255, 255, 255)                             // Snow - white
        }
    }
}
