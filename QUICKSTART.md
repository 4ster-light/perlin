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

Once the application starts, you'll see a 3D landscape displayed from a natural
viewing angle.

### Controls

- **Rotate View**: Click and drag with your mouse to rotate sideways
  (horizontally)
- **Zoom In/Out**: Use your mouse scroll wheel
- **New Terrain**: Click the "Regenerate Terrain" button at the bottom

### Camera View

The camera is positioned at the most horizontal angle (5° from horizontal) and
starts at maximum zoom to give you the best view of the landscape. You can
rotate sideways to see the terrain from different horizontal angles, providing a
natural ground-level perspective similar to walking around the landscape.

### What You're Seeing

The colors represent different terrain types based on elevation:

- **Deep Blue**: Deep water (lowest elevation)
- **Light Blue**: Shallow water
- **Tan/Beige**: Sandy beaches
- **Green**: Grasslands and forests
- **Brown**: Rocky mountains
- **White**: Snow-capped peaks (highest elevation)

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

## Customizing the Terrain

Edit `src/Main.kt` and modify these parameters in the `createAndShowGUI()`
function:

```kotlin
Terrain(
    width = 100,            // Change grid size (larger = more detail, slower)
    height = 100,           // Change grid size
    scale = 0.05,           // Change feature size (0.01-0.1 recommended)
    octaves = 5,            // Change detail level (1-8 recommended)
    heightMultiplier = 50.0 // Change vertical scale
)
```

### Camera Settings

Edit `src/Renderer3D.kt` to adjust the initial camera position:

```kotlin
private var rotationX = 5.0  // Horizontal viewing angle (fixed, most horizontal)
private var rotationZ = 45.0 // Initial rotation around terrain (sideways)
private var zoom = 6.0       // Initial zoom level (0.8-8.0)
private var offsetY = 50.0   // Vertical offset
```

## Tips

1. **Performance**: Larger grid sizes (width/height > 150) may slow down
   rendering
2. **Interesting Terrains**: Try different scale values (0.03-0.07 work well)
3. **More Detail**: Increase octaves to 6-7 for more realistic terrain
4. **Flatter Terrain**: Reduce heightMultiplier to 30.0
5. **Mountainous**: Increase heightMultiplier to 80.0
6. **Better View**: The camera starts at the most horizontal angle (5°) and
   maximum zoom
7. **Prevent Clipping**: Camera automatically prevents terrain from overlapping
   the view
8. **Sideways Rotation**: Drag to rotate horizontally for natural ground-level
   exploration

Enjoy exploring procedurally generated landscapes!
