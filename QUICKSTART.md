# Quick Start Guide

## Running the Application

The project is written in Scala 3 and uses `scala-cli`, so there is no Gradle or
Maven build. With Nix, the compiler and JDK are provided for you:

```bash
nix develop -c ./run.sh
```

Without entering a dev shell:

```bash
nix shell nixpkgs#scala_3 -c ./run.sh
```

If Scala 3.9+ and a JDK 21+ are already installed:

```bash
./run.sh
# or
scala run .
```

To build a standalone launcher:

```bash
nix build
./result/bin/perlin-landscape
```

## Using the Application

Once the application starts, you'll be placed in a first-person view of a
procedurally generated 3D landscape.

### Controls

The terrain is controlled entirely through keyboard and mouse:

#### Movement (Keyboard)

- **W** - Move forward (in the direction you're looking)
- **S** - Move backward
- **A** - Strafe left
- **D** - Strafe right
- **Q** - Ascend (move up vertically)
- **E** - Descend (move down vertically)

#### Camera (Mouse)

- **Move Mouse** - Look around in any direction
- The view updates in real-time as you move the mouse
- No clicking required - just move freely

#### Terrain

- **Click "⟳ Regenerate Terrain"** - Generate a completely new landscape with a
  random seed

### Understanding the HUD

The heads-up display provides real-time information about your position and the
landscape:

#### Top-Left Panel (Position & Performance)

- **Position**: Your current X, Y, Z coordinates in the terrain
- **Performance**: FPS counter and current viewing direction

#### Top-Right Panel (Mini-Map)

- **Terrain visualization**: Local birds-eye view, colored by height and biome
- **Your position**: Yellow crosshair, always centred on the map
- **Your direction**: Blue line showing which way you're looking
- **Reference grid**: Gray lines for orientation
- The map scrolls and rebuilds as you explore the infinite world

#### Top-Center (Compass)

- **Cardinal directions**: N, E, S, W indicators
- **Your heading**: Dot showing your viewing direction

#### Bottom-Left (Control Hints)

- Quick reference for all available controls

### Movement Tips

1. **Smooth Exploration**: Move the mouse gently for smooth camera control
2. **Navigate by Mini-Map**: Use the mini-map to find interesting terrain
   features
3. **Follow Elevation**: Watch the elevation colors to find water, beaches,
   mountains, etc.
4. **Vertical Movement**: Use Q/E to fly up to get a better view or descend into
   valleys
5. **Speed Control**: Movement speed is consistent; move deliberately for
   precision navigation

### Terrain Features

The world is infinite and chunked. Colors represent height plus biome:

- **Deep Blue** (#1E50B4): Deep water
- **Light Blue** (#3C78C8): Shallow water
- **Tan/Beige** (#D6BE91): Sandy beaches
- **Pale Yellow** (#DECC91): Deserts
- **Light Green** (#60A04A): Grasslands
- **Dark Green** (#286930): Forests
- **Olive** (#A0A552): Savanna
- **Brown** (#8B5A2B): Rocky mountains
- **White** (#F5F7FF): Snow-capped peaks

Oceans are flat at sea level, while the underlying noise still decides whether
water is deep or shallow.

## Customizing the Terrain

Choose a seed in `src/dev/aster/app/Main.scala`:

```scala
val seed = System.currentTimeMillis()
val terrain = new Terrain(seed)
```

The interesting worldgen is in `src/dev/aster/terrain/Terrain.scala`:

```scala
private val CHUNK_SIZE = 16        // Height samples per chunk edge
private val SEA_LEVEL = 16.0       // Flat ocean height
private val MAX_CACHED_CHUNKS = 4096
```

`generateHeight(...)` combines four noise layers:

- **Continentalness**: large ocean basins and land masses
- **Erosion**: broad flat areas vs. rough terrain
- **Hills**: local rolling relief
- **Ridged noise**: sharp mountain crests

`surfaceColorIndex(...)` adds temperature and moisture fields for biomes. Try
changing the `smoothstep` thresholds or the multipliers on `hills` and `ridge`
to make the world flatter, more mountainous, or more island-like.

## Camera Settings

Edit `src/dev/aster/camera/Camera.scala` to adjust player movement:

```scala
private val MOVEMENT_SPEED = 1.2      // Units per frame
private val VERTICAL_SPEED = 0.8      // Up/down speed
private val MOUSE_SENSITIVITY = 0.15  // Degrees per pixel
```

Edit `src/dev/aster/render/Renderer3D.scala` for rendering performance:

```scala
private val FOV = 70.0             // Field of view in degrees
val RENDER_DISTANCE = 120          // How far to render (world units)
```

## Performance Tuning

### For Slow Systems

- Reduce `RENDER_DISTANCE` to 60-80
- Reduce `CHUNK_SIZE` if you prefer smaller streaming hitches
- Lower `MAX_CACHED_CHUNKS` to cap memory use more aggressively

### For Fast Systems

- Increase `RENDER_DISTANCE` to 160-200
- Increase `MAX_CACHED_CHUNKS` if you explore large areas
- Raise `PRELOAD_RADIUS` in `Main.scala` for a larger initial buffer

## Rebuilding

`scala-cli` recompiles changed sources automatically. Just relaunch:

```bash
nix develop -c ./run.sh
```

The Scala build cache is stored in `.scala-build/` and is ignored by git.

## Tips for Exploration

1. **Start High**: Generate terrain and immediately press Q to ascend for a
   better overview
2. **Follow Water**: Look for coastlines and explore the surrounding terrain
3. **Find Mountains**: Navigate toward brown/white areas on the mini-map for
   dramatic peaks
4. **Use Compass**: The compass helps orient yourself, especially in complex
   terrain
5. **Monitor FPS**: Keep an eye on the FPS counter; if it drops too low, reduce
   render distance
6. **Try Different Seeds**: Each regeneration creates a completely unique
   infinite world

## Common Issues

### Performance is Slow

- Reduce `RENDER_DISTANCE`
- Reduce `PRELOAD_RADIUS` in `Main.scala`
- Lower movement and mouse sensitivity if it's laggy

### Can't Move

- Make sure the window is focused and you're not hovering over the button
- Check that WASD keys aren't bound to other applications

### Camera Controls Feel Wrong

- Adjust `MOUSE_SENSITIVITY` - lower value for less sensitive mouse
- Adjust `MOVEMENT_SPEED` if navigation feels too slow or fast

### Terrain Looks Flat

- Increase the `ridge` multiplier in `generateHeight(...)`
- Lower the mountain `smoothstep` thresholds so mountains appear more often
- Increase the `hills` multiplier or amplitude for rougher ground

## Troubleshooting

### Window won't open

```bash
# Enter the Nix dev shell and launch from there
nix develop -c ./run.sh

# Or build and run the packaged launcher
nix build
./result/bin/perlin-landscape
```

### Compilation fails

Make sure you have Scala 3.9+ and JDK 21+. The easiest path is:

```bash
nix develop
scala compile .
```

On a headless machine, run the GUI under Xvfb:

```bash
nix shell nixpkgs#xvfb-run -c xvfb-run -a ./run.sh
```

### No terrain visible

- Ensure the window is large enough to display properly
- Try moving with WASD or adjusting camera with mouse
- Check that you haven't zoomed too far away

## Next Steps

Once you're comfortable with the controls:

1. Experiment with the noise layers and multipliers in
   `src/dev/aster/terrain/Terrain.scala`
2. Generate several seeds and find your favorite world
3. Explore the codebase - it's well-commented for learning
4. Modify the biome palette in `Terrain.scala`
5. Try adjusting render distance and chunk-cache size for your system

Enjoy exploring the infinite procedural landscape!
