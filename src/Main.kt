import javax.swing.JFrame
import javax.swing.SwingUtilities
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JLabel
import java.awt.FlowLayout

fun main() {
    SwingUtilities.invokeLater {
        val frame = JFrame("Perlin Noise Landscape Generator")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.layout = BorderLayout()

        // Create initial terrain
        var currentSeed = System.currentTimeMillis()
        var perlinNoise = PerlinNoise()
        var terrain = Terrain(
            width = 100,
            height = 100,
            perlinNoise,
            scale = 0.05,
            octaves = 5,
            heightMultiplier = 50.0
        )

        var renderer = Renderer3D(terrain)
        frame.add(renderer, BorderLayout.CENTER)

        // Control panel
        val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT))

        val infoLabel = JLabel("Drag to rotate and tilt | Scroll to zoom")
        controlPanel.add(infoLabel)

        val regenerateButton = JButton("Regenerate Terrain")
        regenerateButton.addActionListener {
            currentSeed = System.currentTimeMillis()
            perlinNoise = PerlinNoise(currentSeed)
            terrain = Terrain(
                width = 100,
                height = 100,
                perlinNoise,
                scale = 0.05,
                octaves = 5,
                heightMultiplier = 50.0
            )

            // Replace renderer
            frame.remove(renderer)
            renderer = Renderer3D(terrain)
            frame.add(renderer, BorderLayout.CENTER)
            frame.revalidate()
            frame.repaint()

            println("New terrain generated with seed: $currentSeed")
        }
        controlPanel.add(regenerateButton)

        frame.add(controlPanel, BorderLayout.SOUTH)

        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        println("Perlin Noise Landscape Generator started")
        println("Initial seed: $currentSeed")
        println("Controls: Drag to rotate, Scroll to zoom, Click 'Regenerate' for new terrain")
    }
}
