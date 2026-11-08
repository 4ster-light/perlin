package perlin.output

import perlin.world.WorldMap

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** PNG export using only `javax.imageio` from the JDK. */
object PngExporter:

  def render(map: WorldMap): BufferedImage =
    val image = new BufferedImage(map.width, map.height, BufferedImage.TYPE_INT_RGB)
    var y = 0
    map.rows.foreach { row =>
      var x = 0
      row.foreach { sample =>
        image.setRGB(x, y, Palette.rgb(sample.biome))
        x += 1
      }
      y += 1
    }
    image

  def write(map: WorldMap, path: Path): Path =
    Option(path.toAbsolutePath.getParent).foreach(Files.createDirectories(_))
    val image = render(map)
    if !ImageIO.write(image, "png", path.toFile) then
      throw new IllegalStateException(s"no PNG writer available for $path")
    path
