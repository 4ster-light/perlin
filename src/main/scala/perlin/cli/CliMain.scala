package perlin.cli

import cats.syntax.all.*
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Opts
import perlin.execution.ExecutionStrategy
import perlin.output.AsciiExporter
import perlin.output.PngExporter
import perlin.output.SvgExporter
import perlin.world.Biome
import perlin.world.TerrainParameters
import perlin.world.WorldGenerator
import perlin.world.WorldMap

import java.nio.file.Path

/** Command-line entry point built with Decline.
  *
  * `perlin.app.Main` launches the interactive viewer when called without arguments; with arguments
  * it delegates here.
  */
object CliMain:

  private val DefaultSeed = 1337L

  private enum OutputFormat:
    case Png, Ascii, Svg

  private given Argument[OutputFormat] =
    Argument.fromMap(
      "format",
      Map(
        "png" -> OutputFormat.Png,
        "ascii" -> OutputFormat.Ascii,
        "svg" -> OutputFormat.Svg
      )
    )

  private given Argument[ExecutionStrategy] =
    Argument.fromMap(
      "strategy",
      ExecutionStrategy.all.map(strategy => strategy.name -> strategy).toMap
    )

  private final case class MapOptions(
      seed: Long,
      width: Int,
      height: Int,
      octaves: Int,
      scale: Double,
      lacunarity: Double,
      persistence: Double,
      strategy: ExecutionStrategy
  ):
    def parameters: TerrainParameters =
      TerrainParameters(
        seed = seed,
        octaves = octaves,
        lacunarity = lacunarity,
        persistence = persistence,
        scale = scale
      )

  private final case class OutputOptions(
      format: OutputFormat,
      output: Option[Path],
      levels: Int,
      colored: Boolean
  )

  private final case class BenchmarkOptions(
      repetitions: Int,
      warmupRuns: Int
  )

  private sealed trait CliCommand
  private object CliCommand:
    final case class Generate(map: MapOptions, output: OutputOptions) extends CliCommand
    final case class Render(map: MapOptions, output: OutputOptions) extends CliCommand
    final case class Benchmark(map: MapOptions, options: BenchmarkOptions) extends CliCommand
    case object Gui extends CliCommand

  private val seedOption =
    Opts
      .option[Long]("seed", "World seed.", metavar = "long")
      .withDefault(DefaultSeed)

  private val widthOption =
    Opts.option[Int]("width", "Map width in cells.", metavar = "int").withDefault(512)

  private val heightOption =
    Opts.option[Int]("height", "Map height in cells.", metavar = "int").withDefault(512)

  private val octavesOption =
    Opts.option[Int]("octaves", "fBm octave count.", metavar = "int").withDefault(6)

  private val scaleOption =
    Opts
      .option[Double]("scale", "World units per grid cell.", metavar = "double")
      .withDefault(0.0025)

  private val lacunarityOption =
    Opts
      .option[Double]("lacunarity", "Octave frequency multiplier.", metavar = "double")
      .withDefault(2.0)

  private val persistenceOption =
    Opts
      .option[Double]("persistence", "Octave amplitude multiplier.", metavar = "double")
      .withDefault(0.5)

  private val strategyOption =
    Opts
      .option[ExecutionStrategy](
        "strategy",
        s"Generation strategy: ${ExecutionStrategy.names}.",
        metavar = "name"
      )
      .withDefault(ExecutionStrategy.Parallel)

  private val mapOptions =
    (
      seedOption,
      widthOption,
      heightOption,
      octavesOption,
      scaleOption,
      lacunarityOption,
      persistenceOption,
      strategyOption
    ).mapN(MapOptions.apply)

  private val formatOption =
    Opts
      .option[OutputFormat]("format", "Output format: png, ascii or svg.", metavar = "name")
      .withDefault(OutputFormat.Png)

  private val outputOption =
    Opts
      .option[Path]("output", "Output file path.", metavar = "path")
      .orNone

  private val levelsOption =
    Opts
      .option[Int]("levels", "Number of SVG contour levels.", metavar = "int")
      .withDefault(8)

  private val coloredOption =
    Opts.flag("no-color", "Disable ANSI colour in ASCII output.").orTrue

  private val outputOptions =
    (formatOption, outputOption, levelsOption, coloredOption).mapN(OutputOptions.apply)

  private val repetitionsOption =
    Opts
      .option[Int]("reps", "Benchmark repetitions.", metavar = "int")
      .withDefault(3)

  private val warmupOption =
    Opts
      .option[Int]("warmup", "Benchmark warm-up runs.", metavar = "int")
      .withDefault(1)

  private val benchmarkOptions =
    (repetitionsOption, warmupOption).mapN(BenchmarkOptions.apply)

  private val generateCommand =
    Opts.subcommand("generate", "Generate a map and print a biome summary.") {
      (mapOptions, outputOptions).mapN(CliCommand.Generate.apply)
    }

  private val renderCommand =
    Opts.subcommand("render", "Generate and export a map.") {
      (mapOptions, outputOptions).mapN(CliCommand.Render.apply)
    }

  private val benchmarkCommand =
    Opts.subcommand("benchmark", "Benchmark sequential, parallel and chunked generation.") {
      (mapOptions, benchmarkOptions).mapN(CliCommand.Benchmark.apply)
    }

  private val guiCommand =
    Opts.subcommand("gui", "Launch the interactive Swing viewer.") {
      Opts.unit.map(_ => CliCommand.Gui)
    }

  private val command =
    Command(
      "perlin",
      "Seeded Perlin-noise landscape generator with PNG, ASCII and SVG export."
    )(
      generateCommand
        .orElse(renderCommand)
        .orElse(benchmarkCommand)
        .orElse(guiCommand)
    )

  def main(args: Array[String]): Unit =
    val exitCode = run(args)
    if exitCode != 0 then System.exit(exitCode)

  def run(args: Array[String]): Int =
    command.parse(args.toIndexedSeq) match
      case Left(help) =>
        System.err.println(help)
        if help.errors.nonEmpty then 2 else 0
      case Right(cliCommand) =>
        execute(cliCommand)

  private def execute(cliCommand: CliCommand): Int = cliCommand match
    case CliCommand.Generate(map, output)   => generate(map, output)
    case CliCommand.Render(map, output)     => render(map, output)
    case CliCommand.Benchmark(map, options) =>
      Benchmark.run(
        map.width,
        map.height,
        map.parameters,
        options.repetitions,
        options.warmupRuns
      )
      0
    case CliCommand.Gui =>
      perlin.app.Main.main(Array.empty)
      0

  private def generate(mapOptions: MapOptions, outputOptions: OutputOptions): Int =
    val map = generateMap(mapOptions)
    val counts = map.biomeCounts
    println(
      s"Generated ${mapOptions.width}x${mapOptions.height} map " +
        s"(seed ${mapOptions.seed}, strategy ${mapOptions.strategy.name}, " +
        s"${mapOptions.octaves} octaves)"
    )
    counts.toSeq
      .sortBy { case (biome, _) => Biome.values.indexOf(biome) }
      .foreach { case (biome, count) =>
        println(f"  ${biome.toString}%-10s $count%8d")
      }

    outputOptions.output match
      case Some(path) =>
        writeMap(map, path, outputOptions)
        println(s"Wrote ${outputOptions.format.toString.toLowerCase} to $path")
      case None if outputOptions.format == OutputFormat.Ascii =>
        println(AsciiExporter.render(map, outputOptions.colored))
      case None => ()
    0

  private def render(mapOptions: MapOptions, outputOptions: OutputOptions): Int =
    val map = generateMap(mapOptions)
    val output = outputOptions.output.getOrElse(defaultOutput(outputOptions.format))
    writeMap(map, output, outputOptions)
    println(
      s"Wrote ${outputOptions.format.toString.toLowerCase} render to $output " +
        s"(${mapOptions.width}x${mapOptions.height}, seed ${mapOptions.seed}, " +
        s"${mapOptions.strategy.name})"
    )
    0

  private def generateMap(options: MapOptions): WorldMap =
    val generator = new WorldGenerator(options.parameters)
    val rows = options.strategy.generateRows(generator, options.width, options.height)
    WorldMap(
      options.width,
      options.height,
      LazyList.from(rows).map(row => LazyList.from(row))
    )

  private def writeMap(map: WorldMap, path: Path, options: OutputOptions): Unit =
    options.format match
      case OutputFormat.Png   => PngExporter.write(map, path)
      case OutputFormat.Ascii => AsciiExporter.write(map, path, options.colored)
      case OutputFormat.Svg   => SvgExporter.write(map, path, options.levels)

  private def defaultOutput(format: OutputFormat): Path =
    format match
      case OutputFormat.Png   => Path.of("map.png")
      case OutputFormat.Ascii => Path.of("map.txt")
      case OutputFormat.Svg   => Path.of("map.svg")
