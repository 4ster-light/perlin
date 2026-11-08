package perlin.cli

import perlin.execution.ExecutionStrategy
import perlin.output.AsciiExporter
import perlin.output.PngExporter
import perlin.output.SvgExporter
import perlin.world.Biome
import perlin.world.TerrainParameters
import perlin.world.WorldGenerator
import perlin.world.WorldMap

import java.nio.file.Path

/** Dependency-free command-line entry point.
  *
  * The default `perlin.app.Main` launches the interactive viewer when called without arguments;
  * with arguments it delegates here.
  */
object CliMain:

  private val DefaultSeed = 1337L

  private final case class Options(
      seed: Long = DefaultSeed,
      width: Int = 512,
      height: Int = 512,
      octaves: Int = 6,
      scale: Double = 0.0025,
      lacunarity: Double = 2.0,
      persistence: Double = 0.5,
      strategy: ExecutionStrategy = ExecutionStrategy.Parallel,
      format: String = "png",
      output: Option[Path] = None,
      levels: Int = 8,
      colored: Boolean = true,
      repetitions: Int = 3,
      warmupRuns: Int = 1
  )

  def main(args: Array[String]): Unit =
    val exitCode = run(args)
    if exitCode != 0 then System.exit(exitCode)

  def run(args: Array[String]): Int =
    args.toList match
      case Nil                    => printHelp(); 0
      case ("--help" | "-h") :: _ => printHelp(); 0
      case "help" :: _            => printHelp(); 0
      case "generate" :: rest     => withOptions(rest)(generate)
      case "render" :: rest       => withOptions(rest)(render)
      case "benchmark" :: rest    => withOptions(rest)(benchmark)
      case "gui" :: _             => perlin.app.Main.main(Array.empty); 0
      case command :: _           =>
        System.err.println(s"error: unknown command '$command'")
        printHelp()
        2

  private def withOptions(args: List[String])(action: Options => Int): Int =
    parseOptions(args) match
      case Left("help") =>
        printHelp()
        0
      case Left(message) =>
        System.err.println(s"error: $message")
        printHelp()
        2
      case Right(options) =>
        action(options)

  private def generate(options: Options): Int =
    val map = generateMap(options)
    val counts = map.biomeCounts
    println(
      s"Generated ${options.width}x${options.height} map " +
        s"(seed ${options.seed}, strategy ${options.strategy.name}, ${options.octaves} octaves)"
    )
    counts.toSeq
      .sortBy { case (biome, _) => Biome.values.indexOf(biome) }
      .foreach { case (biome, count) =>
        println(f"  ${biome.toString}%-10s $count%8d")
      }

    options.output match
      case Some(path) =>
        writeMap(map, path, options)
        println(s"Wrote ${options.format} to $path")
      case None if options.format == "ascii" =>
        println(AsciiExporter.render(map, options.colored))
      case None => ()
    0

  private def render(options: Options): Int =
    val map = generateMap(options)
    val output = options.output.getOrElse(defaultOutput(options.format))
    writeMap(map, output, options)
    println(
      s"Wrote ${options.format} render to $output " +
        s"(${options.width}x${options.height}, seed ${options.seed}, ${options.strategy.name})"
    )
    0

  private def benchmark(options: Options): Int =
    Benchmark.run(
      options.width,
      options.height,
      parameters(options),
      options.repetitions,
      options.warmupRuns
    )
    0

  private def generateMap(options: Options): WorldMap =
    val generator = new WorldGenerator(parameters(options))
    val rows = options.strategy.generateRows(generator, options.width, options.height)
    WorldMap(
      options.width,
      options.height,
      LazyList.from(rows).map(row => LazyList.from(row))
    )

  private def writeMap(map: WorldMap, path: Path, options: Options): Unit =
    options.format match
      case "png"   => PngExporter.write(map, path)
      case "ascii" => AsciiExporter.write(map, path, options.colored)
      case "svg"   => SvgExporter.write(map, path, options.levels)
      case other   => throw new IllegalArgumentException(s"unsupported format: $other")

  private def defaultOutput(format: String): Path =
    format match
      case "png"   => Path.of("map.png")
      case "ascii" => Path.of("map.txt")
      case "svg"   => Path.of("map.svg")
      case other   => throw new IllegalArgumentException(s"unsupported format: $other")

  private def parameters(options: Options): TerrainParameters =
    TerrainParameters(
      seed = options.seed,
      octaves = options.octaves,
      lacunarity = options.lacunarity,
      persistence = options.persistence,
      scale = options.scale
    )

  private def parseOptions(args: List[String]): Either[String, Options] =
    def parseLong(name: String, value: String): Either[String, Long] =
      value.toLongOption.toRight(s"$name expects an integer, got '$value'")

    def parseInt(name: String, value: String): Either[String, Int] =
      value.toIntOption.toRight(s"$name expects an integer, got '$value'")

    def parseDouble(name: String, value: String): Either[String, Double] =
      value.toDoubleOption.toRight(s"$name expects a number, got '$value'")

    def parseFormat(value: String): Either[String, String] =
      value.toLowerCase match
        case "png" | "ascii" | "svg" => Right(value.toLowerCase)
        case other                   => Left(s"--format must be png, ascii or svg, got '$other'")

    def loop(remaining: List[String], options: Options): Either[String, Options] =
      remaining match
        case Nil                       => Right(options)
        case ("--help" | "-h") :: _    => Left("help")
        case "--seed" :: value :: tail =>
          parseLong("--seed", value).flatMap(parsed => loop(tail, options.copy(seed = parsed)))
        case option :: tail if option.startsWith("--seed=") =>
          parseLong("--seed", option.drop("--seed=".length))
            .flatMap(parsed => loop(tail, options.copy(seed = parsed)))
        case "--width" :: value :: tail =>
          parseInt("--width", value).flatMap(parsed => loop(tail, options.copy(width = parsed)))
        case option :: tail if option.startsWith("--width=") =>
          parseInt("--width", option.drop("--width=".length))
            .flatMap(parsed => loop(tail, options.copy(width = parsed)))
        case "--height" :: value :: tail =>
          parseInt("--height", value).flatMap(parsed => loop(tail, options.copy(height = parsed)))
        case option :: tail if option.startsWith("--height=") =>
          parseInt("--height", option.drop("--height=".length))
            .flatMap(parsed => loop(tail, options.copy(height = parsed)))
        case "--octaves" :: value :: tail =>
          parseInt("--octaves", value).flatMap(parsed => loop(tail, options.copy(octaves = parsed)))
        case option :: tail if option.startsWith("--octaves=") =>
          parseInt("--octaves", option.drop("--octaves=".length))
            .flatMap(parsed => loop(tail, options.copy(octaves = parsed)))
        case "--scale" :: value :: tail =>
          parseDouble("--scale", value).flatMap(parsed => loop(tail, options.copy(scale = parsed)))
        case option :: tail if option.startsWith("--scale=") =>
          parseDouble("--scale", option.drop("--scale=".length))
            .flatMap(parsed => loop(tail, options.copy(scale = parsed)))
        case "--lacunarity" :: value :: tail =>
          parseDouble("--lacunarity", value)
            .flatMap(parsed => loop(tail, options.copy(lacunarity = parsed)))
        case option :: tail if option.startsWith("--lacunarity=") =>
          parseDouble("--lacunarity", option.drop("--lacunarity=".length))
            .flatMap(parsed => loop(tail, options.copy(lacunarity = parsed)))
        case "--persistence" :: value :: tail =>
          parseDouble("--persistence", value)
            .flatMap(parsed => loop(tail, options.copy(persistence = parsed)))
        case option :: tail if option.startsWith("--persistence=") =>
          parseDouble("--persistence", option.drop("--persistence=".length))
            .flatMap(parsed => loop(tail, options.copy(persistence = parsed)))
        case "--strategy" :: value :: tail =>
          ExecutionStrategy
            .parse(value)
            .flatMap(parsed => loop(tail, options.copy(strategy = parsed)))
        case option :: tail if option.startsWith("--strategy=") =>
          ExecutionStrategy
            .parse(option.drop("--strategy=".length))
            .flatMap(parsed => loop(tail, options.copy(strategy = parsed)))
        case "--format" :: value :: tail =>
          parseFormat(value).flatMap(parsed => loop(tail, options.copy(format = parsed)))
        case option :: tail if option.startsWith("--format=") =>
          parseFormat(option.drop("--format=".length))
            .flatMap(parsed => loop(tail, options.copy(format = parsed)))
        case "--output" :: value :: tail =>
          loop(tail, options.copy(output = Some(Path.of(value))))
        case option :: tail if option.startsWith("--output=") =>
          loop(tail, options.copy(output = Some(Path.of(option.drop("--output=".length)))))
        case "--levels" :: value :: tail =>
          parseInt("--levels", value).flatMap(parsed => loop(tail, options.copy(levels = parsed)))
        case option :: tail if option.startsWith("--levels=") =>
          parseInt("--levels", option.drop("--levels=".length))
            .flatMap(parsed => loop(tail, options.copy(levels = parsed)))
        case "--reps" :: value :: tail =>
          parseInt("--reps", value)
            .flatMap(parsed => loop(tail, options.copy(repetitions = parsed)))
        case option :: tail if option.startsWith("--reps=") =>
          parseInt("--reps", option.drop("--reps=".length))
            .flatMap(parsed => loop(tail, options.copy(repetitions = parsed)))
        case "--warmup" :: value :: tail =>
          parseInt("--warmup", value)
            .flatMap(parsed => loop(tail, options.copy(warmupRuns = parsed)))
        case option :: tail if option.startsWith("--warmup=") =>
          parseInt("--warmup", option.drop("--warmup=".length))
            .flatMap(parsed => loop(tail, options.copy(warmupRuns = parsed)))
        case "--no-color" :: tail =>
          loop(tail, options.copy(colored = false))
        case option :: Nil if option.startsWith("--") =>
          Left(s"option '$option' requires a value")
        case option :: _ =>
          Left(s"unknown option '$option'")

    loop(args, Options())

  private def printHelp(): Unit =
    println(
      """perlin - seeded Perlin landscape generator
        |
        |Usage:
        |  perlin                         Launch the interactive viewer
        |  perlin gui                     Launch the interactive viewer
        |  perlin generate [options]      Generate a map and print a biome summary
        |  perlin render [options]        Generate and export a map
        |  perlin benchmark [options]     Benchmark sequential/parallel/chunked generation
        |
        |Commands:
        |  generate     Generate a map. Writes a file when --output is supplied.
        |  render       Generate and write PNG, ASCII or SVG output.
        |  benchmark    JVM warm-up, repeated runs and speedup ratios.
        |  gui          Start the first-person Swing viewer.
        |
        |Options:
        |  --seed <long>          World seed (default 1337)
        |  --width <int>          Map width (default 512)
        |  --height <int>         Map height (default 512)
        |  --octaves <int>        fBm octaves (default 6)
        |  --scale <double>       World units per grid cell (default 0.0025)
        |  --lacunarity <double>  Octave frequency multiplier (default 2.0)
        |  --persistence <double> Octave amplitude multiplier (default 0.5)
        |  --strategy <name>      sequential, parallel or chunked (default parallel)
        |  --format <name>        png, ascii or svg (default png)
        |  --output <path>        Output file (defaults: map.png, map.txt, map.svg)
        |  --levels <int>         SVG contour levels (default 8)
        |  --no-color             Disable ANSI colour in ASCII output
        |  --reps <int>           Benchmark repetitions (default 3)
        |  --warmup <int>         Benchmark warm-up runs (default 1)
        |  --help, -h             Show this help
        |
        |Examples:
        |  perlin render --seed 42 --width 1024 --height 1024 --output map.png
        |  perlin render --format ascii --width 120 --height 60 --no-color
        |  perlin render --format svg --levels 12 --output contours.svg
        |  perlin benchmark --width 1024 --height 1024 --octaves 6 --reps 5
        |""".stripMargin
    )
