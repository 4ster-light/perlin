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
        jdk = pkgs.jdk21;

        # Fixed-output derivation that pre-populates Coursier's cache with the
        # project's Maven dependencies. This keeps the sandboxed build hermetic
        # while still allowing the CLI to use Decline/Cats.
        perlinDeps = pkgs.stdenvNoCC.mkDerivation {
          pname = "perlin-coursier-cache";
          version = "1";
          src = ./project.scala;
          dontUnpack = true;

          nativeBuildInputs = [
            scala
            jdk
          ];

          outputHashMode = "recursive";
          outputHashAlgo = "sha256";
          outputHash = "sha256-mnwYj1wUUjPu8ZobT0P38r8pparVLnrFeFXcdtA0nFs=";

          buildPhase = ''
            export HOME=$TMPDIR
            export COURSIER_CACHE=$out

            mkdir -p source/src/main/scala/perlin
            cp $src source/project.scala
            cat > source/src/main/scala/perlin/Deps.scala <<'EOF'
            package perlin

            import com.monovore.decline.Opts

            object Deps:
              val unit: Opts[Unit] = Opts.unit
            EOF

            cd source
            scala compile . --server=false --suppress-outdated-dependency-warning
          '';

          installPhase = "true";
        };
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
            export COURSIER_CACHE=$TMPDIR/coursier-cache
            cp -r ${perlinDeps} $COURSIER_CACHE
            chmod -R u+w $COURSIER_CACHE
            export COURSIER_MODE=offline

            scala compile . --server=false
            scalafmt --test .
            scalafix --auto-classpath \
              --scalac-options -Wunused:imports \
              --scala-version 3.9.0 \
              --check \
              src/main/scala
          '';

          installPhase = ''
            mkdir -p $out
            touch $out/checks-passed
          '';
        };

        packages.perlin-deps = perlinDeps;

        packages.default = pkgs.stdenv.mkDerivation {
          pname = "perlin-landscape";
          version = "0.1.0";
          src = self;

          nativeBuildInputs = [
            scala
            jdk
            pkgs.makeWrapper
          ];

          # Let the Scala runner resolve the Scala version and main class from
          # project.scala, compile the sources, and produce a self-contained
          # runnable assembly JAR.
          buildPhase = ''
            export HOME=$TMPDIR
            export COURSIER_CACHE=$TMPDIR/coursier-cache
            cp -r ${perlinDeps} $COURSIER_CACHE
            chmod -R u+w $COURSIER_CACHE
            export COURSIER_MODE=offline

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
