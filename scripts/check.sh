#!/usr/bin/env bash
set -euo pipefail

# Compile first: Scalafix needs SemanticDB produced by the compiler.
scala compile . --server=false

# Formatting check.
scalafmt --test .

# Scalafix needs the Scala version explicitly; without it the parser falls back
# to Scala 2 syntax and fails on Scala 3 indentation.
scalafix \
  --auto-classpath \
  --scalac-options -Wunused:imports \
  --scala-version 3.9.0 \
  --check \
  src/main/scala

# Unit tests (Munit is test-scoped).
scala test . --server=false
