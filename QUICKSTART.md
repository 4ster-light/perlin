# Quick Start Guide

## Running the Application

### Option 1: Use Gradle (Recommended)

```bash
./gradlew run
```

This will automatically build (if needed) and run the application.

### Option 2: Build then run the JAR

```bash
./gradlew build
java -jar build/libs/perlin-1.0.0.jar
```

### Option 3: From IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. Open `src/Main.kt`
3. Click the green play button next to the `main()` function
4. Or press Shift+F10 (Windows/Linux) or Control+R (Mac)

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

- **Terrain visualization**: Birds-eye view of the landscape colored by
  elevation
- **Your position**: Yellow crosshair showing where you are
- **Your direction**: Blue line showing which way you're looking
- **Reference grid**: Gray lines for orientation

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

The colors represent different terrain types based on elevation:

- **Deep Blue** (#1E50B4): Deep water (lowest elevation)
- **Light Blue** (#3C78C8): Shallow water
- **Tan/Beige** (#D2B48C): Sandy beaches
- **Green** (#228B22): Grasslands and forests
- **Brown** (#8B5A2B): Rocky mountains
- **White** (#FFFFFF): Snow-capped peaks (highest elevation)

## Customizing the Terrain

Edit `src/Main.kt` and modify these parameters in the `main()` function:

```kotlin
Terrain(
    width = 200,              // Grid width (200x200 is default)
    height = 200,             // Grid height
    perlinNoise,
    scale = 0.05,             // 0.01-0.1: Smaller = larger features
    octaves = 5,              // 1-8: Bigger = more detail
    heightMultiplier = 50.0   // Vertical scale
)
```

### Terrain Scale Guide

- **scale = 0.01**: Very large features, zoomed-out feel
- **scale = 0.03**: Large rolling terrain
- **scale = 0.05**: Balanced terrain (default)
- **scale = 0.07**: More detailed, craggy terrain
- **scale = 0.1**: Very fine, intricate features

### Detail Level

- **octaves = 3**: Smooth, simple terrain
- **octaves = 5**: Balanced detail (default)
- **octaves = 7**: Very detailed, complex terrain (may impact performance)

### Height Exaggeration

- **heightMultiplier = 30.0**: Flatter terrain
- **heightMultiplier = 50.0**: Balanced (default)
- **heightMultiplier = 80.0**: Very mountainous

## Camera Settings

Edit `src/Camera.kt` to adjust player movement:

```kotlin
companion object {
    private const val MOVEMENT_SPEED = 0.3      // Units per frame
    private const val VERTICAL_SPEED = 0.2      // Up/down speed
    private const val MOUSE_SENSITIVITY = 0.1   // Degrees per pixel
}
```

Edit `src/Renderer3D.kt` for rendering performance:

```kotlin
companion object {
    private const val RENDER_DISTANCE = 100.0   // How far to render (grid units)
    private const val FOV = 90.0                 // Field of view in degrees
}
```

## Performance Tuning

### For Slow Systems

- Reduce `width` and `height` to 150x150 or 100x100
- Reduce `RENDER_DISTANCE` to 60-80
- Reduce `octaves` to 3-4
- Increase `scale` to reduce detail (0.07+)

### For Fast Systems

- Increase `width` and `height` to 250x250 or 300x300
- Increase `RENDER_DISTANCE` to 120+
- Increase `octaves` to 6-7
- Decrease `scale` for more detail (0.03-0.04)

## Rebuilding

If you make changes to the source code, Gradle will detect them automatically on
the next build:

```bash
./gradlew build
```

To force a clean rebuild:

```bash
./gradlew clean build
```

## Tips for Exploration

1. **Start High**: Generate terrain and immediately press Q to ascend for a
   better overview
2. **Follow Water**: Look for water features and explore surrounding terrain
3. **Find Mountains**: Navigate toward brown areas on the mini-map for dramatic
   peaks
4. **Use Compass**: The compass helps orient yourself, especially in complex
   terrain
5. **Monitor FPS**: Keep an eye on the FPS counter; if it drops too low, reduce
   terrain size or render distance
6. **Try Different Seeds**: Each regeneration creates a completely unique
   landscape

## Common Issues

### Performance is Slow

- Reduce terrain size (width/height)
- Reduce `RENDER_DISTANCE`
- Reduce `octaves` for less detail
- Lower movement and mouse sensitivity if it's laggy

### Can't Move

- Make sure the window is focused and you're not hovering over the button
- Check that WASD keys aren't bound to other applications

### Camera Controls Feel Wrong

- Adjust `MOUSE_SENSITIVITY` - lower value for less sensitive mouse
- Adjust `MOVEMENT_SPEED` if navigation feels too slow or fast

### Terrain Looks Flat

- Increase `heightMultiplier` for more dramatic elevation changes
- Reduce `scale` for more varied terrain features
- Increase `octaves` for more detail

## Troubleshooting

### Window won't open

```bash
# Try with explicit Java options
java -Xmx1024M -jar build/libs/perlin-1.0.0.jar
```

### Build fails

```bash
# Clean and rebuild
./gradlew clean build
```

### No terrain visible

- Ensure the window is large enough to display properly
- Try moving with WASD or adjusting camera with mouse
- Check that you haven't zoomed too far away

## Next Steps

Once you're comfortable with the controls:

1. Experiment with different scale and octave values
2. Generate several terrains and find your favorite
3. Explore the codebase - it's well-commented for learning
4. Consider modifying the terrain colors in `Terrain.kt`
5. Try adjusting performance parameters for your system

Enjoy exploring procedurally generated landscapes!
