{
  description = "Infinite procedural landscape in Scala/Swing";

  inputs = {
    flake-utils.url = "github:numtide/flake-utils";
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  };

  outputs =
    { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        scala = pkgs.scala_3;
        jdk = pkgs.jdk25;
      in
      {
        devShells.default = pkgs.mkShell {
          packages = [
            scala
            jdk
            pkgs.scalafmt
            pkgs.scalafix
          ];
        };

        checks.default = pkgs.stdenv.mkDerivation {
          pname = "perlin-landscape-checks";
          version = "0.1.0";
          src = self;

          nativeBuildInputs = [
            scala
            jdk
            pkgs.scalafmt
            pkgs.scalafix
          ];

          buildPhase = ''
            export HOME=$TMPDIR
            scala compile . --server=false
            scalafmt --test .
            scalafix --auto-classpath \
              --scalac-options -Wunused:imports \
              --scala-version 3.9.0 \
              --check \
              src
          '';

          installPhase = ''
            mkdir -p $out
            touch $out/checks-passed
          '';
        };

        packages.default = pkgs.stdenv.mkDerivation {
          pname = "perlin-landscape";
          version = "0.1.0";
          src = self;

          nativeBuildInputs = [
            scala
            jdk
            pkgs.makeWrapper
          ];

          # Let scala-cli resolve the Scala version and main class from
          # project.scala, compile the sources, and produce a self-contained
          # runnable assembly JAR.
          buildPhase = ''
            scala --power package . \
              --assembly \
              --server=false \
              --offline \
              --force \
              -o perlin-landscape.jar
          '';

          installPhase = ''
            mkdir -p $out/share/perlin-landscape $out/bin
            cp perlin-landscape.jar $out/share/perlin-landscape/
            makeWrapper ${jdk}/bin/java $out/bin/perlin-landscape \
              --add-flags "-jar $out/share/perlin-landscape/perlin-landscape.jar"
          '';

          meta.mainProgram = "perlin-landscape";
        };

        apps.default = {
          type = "app";
          program = "${self.packages.${system}.default}/bin/perlin-landscape";
          meta.description = "Run the infinite procedural landscape";
        };
      }
    );
}
