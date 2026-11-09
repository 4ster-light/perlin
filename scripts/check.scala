import scala.sys.process.*

@main def check(): Unit =
  def run(command: String*): Unit =
    val exitCode = Process(command).!
    if exitCode != 0 then sys.exit(exitCode)

  run("scala", "compile", ".", "--server=false")
  run("scalafmt", "--test", ".")
  run(
    "scalafix",
    "--auto-classpath",
    "--scalac-options",
    "-Wunused:imports",
    "--scala-version",
    "3.9.0",
    "--check",
    "src/main/scala"
  )
  run("scala", "test", ".", "--server=false")
