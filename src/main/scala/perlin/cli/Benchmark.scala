package perlin.cli

import perlin.execution.ExecutionStrategy
import perlin.world.TerrainParameters
import perlin.world.WorldGenerator

import java.util.Locale
import scala.collection.mutable.ArrayBuffer

/** Warm-up + repeated measurements for the three execution strategies. */
object Benchmark:

  private final case class Result(
      strategy: String,
      minimumMs: Double,
      medianMs: Double,
      checksum: Double
  )

  def run(
      width: Int,
      height: Int,
      parameters: TerrainParameters,
      repetitions: Int,
      warmupRuns: Int
  ): Unit =
    require(width > 0 && height > 0, "benchmark dimensions must be positive")
    require(repetitions > 0, "repetitions must be positive")
    require(warmupRuns >= 0, "warmup runs cannot be negative")

    val generator = new WorldGenerator(parameters)
    val processors = Runtime.getRuntime.availableProcessors

    println("Perlin landscape benchmark")
    println(s"  workload: ${width}x$height, ${parameters.octaves} octaves")
    println(s"  processors: $processors")
    println(s"  repetitions: $repetitions, warm-up runs: $warmupRuns")
    println()

    if warmupRuns > 0 then
      println("Warming up the JVM...")
      var run = 0
      while run < warmupRuns do
        ExecutionStrategy.all.foreach { strategy =>
          force(strategy.generateRows(generator, width, height))
        }
        run += 1
      println("Warm-up complete.")
      println()

    val results = ExecutionStrategy.all.map { strategy =>
      val timings = ArrayBuffer.empty[Double]
      var checksum = 0.0
      var run = 0
      while run < repetitions do
        val start = System.nanoTime()
        val rows = strategy.generateRows(generator, width, height)
        checksum = force(rows)
        val elapsedMs = (System.nanoTime() - start).toDouble / 1_000_000.0
        timings += elapsedMs
        run += 1
      val sorted = timings.sorted
      val median =
        if sorted.length % 2 == 1 then sorted(sorted.length / 2)
        else (sorted(sorted.length / 2 - 1) + sorted(sorted.length / 2)) / 2.0
      Result(strategy.name, sorted.head, median, checksum)
    }

    val baseline = results.head

    println("strategy      min (ms)   median (ms)   min speedup   median speedup   checksum")
    println("-" * 84)
    results.foreach { result =>
      val minimumSpeedup = baseline.minimumMs / result.minimumMs
      val medianSpeedup = baseline.medianMs / result.medianMs
      println(
        String.format(
          Locale.ROOT,
          "%-12s %10.1f %13.1f %12.2fx %15.2fx %11.1f",
          result.strategy,
          Double.box(result.minimumMs),
          Double.box(result.medianMs),
          Double.box(minimumSpeedup),
          Double.box(medianSpeedup),
          Double.box(result.checksum)
        )
      )
    }

  private def force(rows: IndexedSeq[IndexedSeq[perlin.world.TerrainSample]]): Double =
    rows.iterator.flatten.map(_.altitude.value).sum
