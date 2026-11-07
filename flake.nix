{
  description = "Infinite procedural landscape in Scala/Swing";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs =
    { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forAllSystems =
        f:
        nixpkgs.lib.genAttrs systems (
          system: f nixpkgs.legacyPackages.${system}
        );
    in
    {
      devShells = forAllSystems (pkgs: {
        default = pkgs.mkShell {
          packages = [
            pkgs.scala_3
            pkgs.jdk
          ];
        };
      });

      packages = forAllSystems (
        pkgs:
        let
          scala = pkgs.scala_3;
          scalaBare = scala.bare;
          scalaVersion = scala.version;
          scala3Library = "${scalaBare}/maven2/org/scala-lang/scala3-library_3/${scalaVersion}/scala3-library_3-${scalaVersion}.jar";
          scalaLibrary = "${scalaBare}/maven2/org/scala-lang/scala-library/${scalaVersion}/scala-library-${scalaVersion}.jar";
        in
        {
          default = pkgs.stdenv.mkDerivation {
            pname = "perlin-landscape";
            version = "0.1.0";
            src = self;

            nativeBuildInputs = [
              scala
              pkgs.jdk
              pkgs.makeWrapper
            ];

            buildPhase = ''
              mkdir -p build
              scalac -deprecation -feature -d build $(find src -name '*.scala' -print)
            '';

            installPhase = ''
              mkdir -p $out/share/perlin-landscape $out/bin
              cp -r build/* $out/share/perlin-landscape/
              makeWrapper ${pkgs.jdk}/bin/java $out/bin/perlin-landscape \
                --add-flags "-cp $out/share/perlin-landscape:${scala3Library}:${scalaLibrary} dev.aster.app.Main"
            '';

            meta.mainProgram = "perlin-landscape";
          };
        }
      );

      apps = forAllSystems (pkgs: {
        default = {
          type = "app";
          program = "${self.packages.${pkgs.stdenv.hostPlatform.system}.default}/bin/perlin-landscape";
          meta.description = "Run the infinite procedural landscape";
        };
      });
    };
}
