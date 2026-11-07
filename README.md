# Perlin Noise Procedural Landscape Generator

An infinite 3D procedural landscape generated with Perlin and ridged noise,
now featuring a **first-person immersive experience**, implemented in **Scala 3**
(Swing/AWT) with real-time software 3D visualization.

![Showcase Image](./Showcase.png)

## Features

### Language & Tooling

- **Scala 3**: the entire renderer, world generator, camera and UI are Scala
- **No heavyweight build tool**: `scala-cli` compiles and runs the sources
- **Nix flake**: `nix develop`, `nix build` and `nix run` are supported
- **JDK Swing/AWT only** at runtime - no third-party libraries

### First-Person Experience

- **WASD Movement**: Navigate freely through the terrain (forward, backward,
  strafe left/right)
- **Mouse Look**: Control camera orientation with mouse movement for immersive
  exploration
- **Vertical Control**: Q/E keys to ascend and descend
- **Collision Detection**: Prevents clipping through terrain with smooth
  collision response
- **Bilinear Height Interpolation**: Smooth terrain surface traversal

### Terrain Generation

- **Truly Procedural World**: Infinite terrain generated on demand; chunks are
  created as you explore and evicted from an LRU cache when far away
- **Layered Noise**: Domain-warped continentalness, erosion, rolling hills and
  ridged multifractal mountains combine for natural terrain
- **Biomes**: Temperature and moisture fields drive beaches, deserts,
  grasslands, forests, savanna, rock and snow
- **Flat Oceans**: Water is rendered at sea level while the underlying height
  still controls deep vs. shallow coloring
- **Deterministic Seeds**: Every location is a pure function of its coordinates
  and seed, so revisiting an area regenerates it identically
- **Procedural Customization**: Click "Regenerate Terrain" to create a new
  infinite world with a different seed

### Advanced Rendering

- **3D Visualization**: Real-time 3D rendering with perspective projection
- **Frustum Culling**: Intelligent triangle culling to render only visible
  geometry
- **Render Distance**: Configurable rendering radius around player position
- **Color-coded Terrain**:
  - Deep blue: Deep water
  - Light blue: Shallow water
  - Tan: Sandy beaches
  - Pale yellow: Deserts
  - Light green: Grasslands
  - Dark green: Forests
  - Olive: Savanna
  - Brown: Rocky mountains
  - White: Snow-capped peaks

### Enhanced UI

- **Heads-Up Display (HUD)**:
  - Real-time position coordinates (X, Y, Z)
  - Performance metrics (FPS counter)
  - Current viewing direction/bearing
  - Local mini-map that streams around the player as you explore
  - Compass rose for cardinal direction reference
- **Modern Styling**: Dark theme with blue accent colors and decorative borders
- **Responsive Controls**: Visual feedback on button interactions
- **Status Indicators**: Real-time updates on terrain generation status

## Project Structure

```plaintext
.
├── project.scala              - scala-cli directives (Scala 3.9, main class)
├── run.sh                     - compile and launch helper
├── flake.nix                  - Nix dev shell, package and app
└── src/dev/aster/
    ├── app/
    │   └── Main.scala         - Application entry point and GUI setup
    ├── noise/
    │   └── PerlinNoise.scala  - Perlin and ridged noise implementation
    ├── terrain/
    │   └── Terrain.scala      - Infinite chunked terrain generation and biomes
    ├── camera/
    │   ├── Camera.scala       - First-person camera with WASD + mouse look
    │   └── CollisionDetector.scala - Terrain collision and camera constraints
    ├── render/
    │   ├── Renderer3D.scala   - Projection, software rasterizer and frame loop
    │   └── ViewFrustum.scala  - Camera frustum helpers
    └── ui/
        └── HeadsUpDisplay.scala - HUD rendering with stats and local mini-map
```

Each concern lives in its own package (`app`, `noise`, `terrain`, `camera`,
`render`, `ui`). `scala-cli` discovers the sources and compiles them together.

> [!NOTE]
> See [QUICKSTART.md](QUICKSTART.md) for step-by-step instructions on running
> and customizing the project

## Building & Running

### With Nix (recommended)

The flake provides the Scala 3 compiler and a JDK:

```bash
nix develop -c ./run.sh
```

Or without entering a shell:

```bash
nix shell nixpkgs#scala_3 -c ./run.sh
```

Build a standalone launcher:

```bash
nix build
./result/bin/perlin-landscape
```

### With Scala already installed

Requires Scala 3.9+ and a JDK 21+:

```bash
./run.sh
# or:
scala run .
```

`run.sh` uses `scala-cli` with `--server=false`, so no background build server
is left running.

## Controls

| Input              | Action                                   |
| ------------------ | ---------------------------------------- |
| **W**              | Move forward                             |
| **S**              | Move backward                            |
| **A**              | Strafe left                              |
| **D**              | Strafe right                             |
| **Q**              | Ascend (move up)                         |
| **E**              | Descend (move down)                      |
| **Mouse Movement** | Look around / rotate camera              |
| **Button Click**   | Generate new terrain with different seed |

## Technical Details

### Scala 3 Architecture

- Immutable `case class` value types for HUD snapshots and camera vectors
- Scala 3 `enum` for input keys
- Java Swing/AWT interop for the window, timers and graphics
- Hot rendering loops use primitive `Array`s and `while` loops, preserving the
  allocation-free frame path from the Java version

### Perlin Noise Algorithm

The implementation uses:

- Permutation table with Fisher-Yates shuffle for pseudo-randomness
- Gradient interpolation with smooth fade curves
- Octave layering for fractal detail (default: 5 octaves)
- Configurable persistence for detail control

### First-Person Camera System

- Full 6-DOF camera movement (forward/back, left/right, up/down)
- Euler angle rotation (pitch and yaw) with mouse input
- Smooth frame-rate independent movement using delta time
- Camera height offset from terrain surface
- Infinite traversal with only a numeric world-limit safety net
- Timer-driven update/paint on the Swing EDT

### 3D Rendering

- Perspective projection with configurable FOV (70°)
- First-person camera perspective transformation
- Rotation matrices for camera orientation (yaw and pitch)
- Triangular mesh generation from height map
- Painter's algorithm for depth sorting
- View frustum culling to optimize rendering

### Performance Optimization

- **Chunk Streaming**: Only the chunks visible around the player are generated
  and cached; the rest of the infinite world does not exist in memory
- **Render Distance**: Only renders terrain within ~120 units of the player
- **Frustum Culling**: Near/far plane checks plus screen-space bounding-box
  rejection skip off-screen geometry
- **Software Rasterizer**: Scanline triangle fills and Bresenham wireframes
  avoid per-triangle Java2D overhead; one pixel buffer is blitted per frame
- **Reusable Buffers**: Vertex, triangle and frame buffers are retained between
  frames, so the hot path is allocation-free
- **Primitive Depth Sort**: Packed `long` keys replace per-triangle objects and
  comparator sorting
- **Cached HUD Artwork**: The local mini-map is rebuilt only when the player
  moves, and fonts/metrics/palettes are cached
- **Uniform Wireframe Mesh**: Every triangle carries its border, giving the
  demo its signature polygon-y look (geometric LOD was deliberately rejected:
  merging heightfield cells cracks the mesh without edge stitching)

### Terrain Parameters

- **Chunk size**: 16×16 height samples, generated on demand
- **Sea level**: 16.0
- **Surface height range**: roughly 5–65 units
- **Biome fields**: temperature and moisture from low-frequency noise
- **Render distance**: 120 world units
- **Movement speed**: 1.2 units per frame
- **Mouse sensitivity**: 0.15 degrees per pixel

## Customization

The world seed is chosen in `src/dev/aster/app/Main.scala`:

```java
long seed = System.currentTimeMillis();
Terrain terrain = new Terrain(seed);
```

To experiment with worldgen, edit the constants and noise fields in
`src/dev/aster/terrain/Terrain.scala`:

```java
private static final int CHUNK_SIZE = 16;
private static final double SEA_LEVEL = 16.0;
```

The `generateHeight(...)` method combines continentalness, erosion, hills and
ridged mountains; `surfaceColorIndex(...)` maps elevation, temperature and
moisture to biomes.

Camera settings can be adjusted in `Camera.scala`:

```java
private static final double MOVEMENT_SPEED = 1.2;    // Units per frame
private static final double VERTICAL_SPEED = 0.8;    // Units per frame (up/down)
private static final double MOUSE_SENSITIVITY = 0.15; // Degrees per pixel
```

Rendering parameters in `Renderer3D.scala`:

```java
private static final double FOV = 70.0;
public static final int RENDER_DISTANCE = 120;   // World units
```

## Performance Considerations

- **Render Distance**: Reduce for better performance on slower systems
- **Chunk Cache**: `MAX_CACHED_CHUNKS` in `Terrain.scala` bounds memory use
- **Mouse Sensitivity**: Adjust for comfortable control experience
- **FPS Counter**: Shows actual rendering performance in real-time

## Requirements

- [Nix](https://nixos.org/) with flakes enabled (recommended), or
- Scala 3.9+ with a JDK 21+ on `PATH`
- No third-party runtime libraries

## System Requirements

**Minimum:**

- Processor: Dual-core 2.0 GHz
- RAM: 512 MB
- Graphics: Integrated (software rendering)

**Recommended:**

- Processor: Quad-core 2.5 GHz
- RAM: 2 GB
- Graphics: Dedicated graphics card

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file
for details.
