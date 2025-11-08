# Perlin Noise Procedural Landscape Generator

A 3D procedural terrain generator using Perlin noise algorithm, implemented in
Kotlin with real-time 3D visualization.

![Showcase Image](https://github.com/4ster-light/perlin/blob/main/Showcase.png)

## Features

- **Perlin Noise Algorithm**: Pure implementation of Ken Perlin's noise
  algorithm for natural-looking terrain
- **Octave Noise**: Multiple noise frequencies combined for realistic fractal
  terrain details
- **3D Visualization**: Real-time 3D rendering with perspective projection
- **Interactive Camera**:
  - Drag with mouse to rotate the view
  - Scroll wheel to zoom in/out
- **Color-coded Elevation**:
  - Deep blue: Deep water
  - Light blue: Shallow water
  - Tan: Sandy beaches
  - Green: Grasslands
  - Brown: Rocky mountains
  - White: Snow-capped peaks
- **Procedural Generation**: Click "Regenerate Terrain" to create new landscapes
  with different seeds

## Project Structure

```plaintext
perlin/
├── src/
│   ├── Main.kt         - Application entry point and GUI setup
│   ├── PerlinNoise.kt  - Perlin noise algorithm implementation
│   ├── Terrain.kt      - Terrain generation and height mapping
│   └── Renderer3D.kt   - 3D rendering engine with camera controls
└── perlin.sh           - Manager script
```

> [!NOTE]
> See [QUICKSTART.md](QUICKSTART.md) for more information on the project
> structure and manager script

## Building

The project uses IntelliJ IDEA's build system.

### Easy way (Recommended)

```bash
./perlin.sh
```

The script automatically detects Kotlin compiler from:

- System installation
- IntelliJ IDEA's bundled compiler
- Common installation paths

### Manual compilation

```bash
kotlinc src/*.kt -include-runtime -d perlin.jar
```

Or use IntelliJ IDEA to build the project directly.

## Running

### Unified script (Recommended)

```bash
./perlin.sh              # Build (if needed) and run
./perlin.sh --run-only   # Skip build, just run
./perlin.sh --help       # Show all options
```

### From JAR

```bash
java -jar perlin.jar
```

### From IntelliJ IDEA

Open the project and run the `Main.kt` file.

## Controls

- **Mouse Drag**: Rotate the camera sideways (horizontal rotation only)
- **Mouse Scroll**: Zoom in/out
- **Regenerate Button**: Create a new terrain with a different random seed

## Technical Details

### Perlin Noise Algorithm

The implementation uses:

- Permutation table with Fisher-Yates shuffle for pseudo-randomness
- Gradient interpolation with smooth fade curves
- Octave layering for fractal detail (default: 5 octaves)

### 3D Rendering

- Triangular mesh generation from height map
- Painter's algorithm for depth sorting
- Perspective projection with adjustable zoom
- Rotation matrices for camera control

### Terrain Parameters

- Grid size: 100x100 vertices
- Scale: 0.05 (controls feature size)
- Octaves: 5 (detail levels)
- Height multiplier: 50.0 (vertical exaggeration)

## Customization

You can modify terrain parameters in `Main.kt`:

```kotlin
Terrain(
    width = 100,            // Grid width
    height = 100,           // Grid height
    perlinNoise,
    scale = 0.05,           // Smaller = larger features
    octaves = 5,            // More = more detail
    heightMultiplier = 50.0 // Vertical scale
)
```

## Requirements

- Java 8 or higher
- Kotlin compiler

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file
for details.

## Sponsor

If you like this project, consider supporting me by buying me a coffee.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/B0B41HVJUR)
