package perlin.execution

import perlin.core.GridCell
import perlin.world.TerrainSample
import perlin.world.WorldGenerator

import java.util.concurrent.Executors
import java.util.stream.IntStream
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*

/** One interface for the three generation strategies exposed by the CLI. */
trait ExecutionStrategy:

  def name: String

  def map[A, B](input: IndexedSeq[A])(f: A => B): IndexedSeq[B]

  /** Eagerly generate every row using this strategy. */
  def generateRows(
      generator: WorldGenerator,
      width: Int,
      height: Int
  ): IndexedSeq[IndexedSeq[TerrainSample]] =
    map((0 until height).toIndexedSeq) { y =>
      (0 until width).map { x =>
        generator.sample(GridCell(x), GridCell(y), height)
      }.toIndexedSeq
    }

object ExecutionStrategy:

  /** Plain single-threaded generation. */
  case object Sequential extends ExecutionStrategy:
    val name = "sequential"

    def map[A, B](input: IndexedSeq[A])(f: A => B): IndexedSeq[B] =
      input.map(f)

  /** JVM parallel stream fan-out. */
  case object Parallel extends ExecutionStrategy:
    val name = "parallel"

    def map[A, B](input: IndexedSeq[A])(f: A => B): IndexedSeq[B] =
      val results = new Array[Any](input.length)
      IntStream.range(0, input.length).parallel().forEach { index =>
        results(index) = f(input(index))
      }
      results.iterator.map(_.asInstanceOf[B]).toIndexedSeq

  /** Chunked `Future` fan-out sized to the available processors. */
  case object Chunked extends ExecutionStrategy:
    val name = "chunked"

    private val processors = Runtime.getRuntime.availableProcessors
    private val pool = Executors.newFixedThreadPool(
      processors,
      (runnable: Runnable) =>
        val thread = new Thread(runnable, "perlin-worker")
        thread.setDaemon(true)
        thread
    )
    private given executionContext: ExecutionContext =
      ExecutionContext.fromExecutorService(pool)

    def map[A, B](input: IndexedSeq[A])(f: A => B): IndexedSeq[B] =
      if input.isEmpty then IndexedSeq.empty
      else
        val chunkSize = Math.max(1, (input.length + processors - 1) / processors)
        val futures = input.grouped(chunkSize).toIndexedSeq.map(chunk => Future(chunk.map(f)))
        Await.result(Future.sequence(futures), 30.minutes).flatten

  val all: IndexedSeq[ExecutionStrategy] = IndexedSeq(Sequential, Parallel, Chunked)

  def parse(value: String): Either[String, ExecutionStrategy] =
    value.toLowerCase match
      case "sequential" | "seq" => Right(Sequential)
      case "parallel" | "par"   => Right(Parallel)
      case "chunked" | "chunk"  => Right(Chunked)
      case other                => Left(s"unknown strategy '$other' (expected ${names})")

  def names: String = all.map(_.name).mkString(", ")
