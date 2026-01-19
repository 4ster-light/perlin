package dev.aster

import javax.swing.JFrame
import javax.swing.SwingUtilities
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JLabel
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font

fun main() = SwingUtilities.invokeLater {
    val frame = JFrame("Perlin Noise Landscape Generator - First Person Experience").apply {
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        layout = BorderLayout()
        iconImage = null // Use default icon
    }

    // Create initial terrain
    var currentSeed = System.currentTimeMillis()
    var perlinNoise = PerlinNoise()
    var terrain = Terrain(
        width = 200,
        height = 200,
        perlinNoise,
        scale = 0.05,
        octaves = 5,
        heightMultiplier = 50.0
    )

    var renderer = Renderer3D(terrain)
    frame.add(renderer, BorderLayout.CENTER)

    val controlPanel = JPanel().apply {
        layout = BorderLayout(10, 5)
        background = Color(35, 35, 45)
        border = javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Color(100, 150, 255))
    }

    val leftInfoPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 8)).apply {
        background = Color(35, 35, 45)
        isOpaque = false
    }

    val infoLabel = JLabel("◆ First Person Experience • WASD Move • Mouse Look • Q/E Up/Down").apply {
        foreground = Color(200, 200, 200)
        font = Font("Monospaced", Font.PLAIN, 12)
    }
    leftInfoPanel.add(infoLabel)

    val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 8)).apply {
        background = Color(35, 35, 45)
        isOpaque = false
    }

    val regenerateButton = JButton("⟳ Regenerate Terrain").apply {
        background = Color(70, 130, 180)
        foreground = Color.WHITE
        font = Font("Arial", Font.BOLD, 12)
        isFocusPainted = false
        isBorderPainted = false
        isOpaque = true
        border = javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12)

        addActionListener {
            isEnabled = false
            text = "⟳ Generating..."

            // Start async terrain generation
            Thread {
                try {
                    currentSeed = System.currentTimeMillis()
                    perlinNoise = PerlinNoise(currentSeed)
                    terrain = Terrain(
                        width = 200,
                        height = 200,
                        perlinNoise,
                        scale = 0.05,
                        octaves = 5,
                        heightMultiplier = 50.0
                    )

                    // Replace renderer on EDT
                    SwingUtilities.invokeLater {
                        frame.remove(renderer)
                        renderer = Renderer3D(terrain)
                        frame.add(renderer, BorderLayout.CENTER)
                        frame.revalidate()
                        frame.repaint()

                        println("✓ New terrain generated with seed: $currentSeed")

                        this@apply.text = "⟳ Regenerate Terrain"
                        this@apply.isEnabled = true
                    }
                } catch (e: Exception) {
                    println("✗ Error generating terrain: ${e.message}")
                    this@apply.text = "⟳ Regenerate Terrain"
                    this@apply.isEnabled = true
                }
            }.apply {
                isDaemon = true
                start()
            }
        }

        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                background = Color(90, 150, 200)
            }

            override fun mouseExited(e: java.awt.event.MouseEvent) {
                background = Color(70, 130, 180)
            }
        })
    }
    rightPanel.add(regenerateButton)

    controlPanel.add(leftInfoPanel, BorderLayout.WEST)
    controlPanel.add(rightPanel, BorderLayout.EAST)

    frame.add(controlPanel, BorderLayout.SOUTH)

    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true

    println("╔════════════════════════════════════════════════════════════╗")
    println("║  Perlin Noise Landscape - First Person Experience Started  ║")
    println("╠════════════════════════════════════════════════════════════╣")
    println("║ Seed: $currentSeed                                        ║")
    println("║ Terrain: 200x200 vertices • Scale: 0.05 • Octaves: 5       ║")
    println("║                                                            ║")
    println("║ Controls:                                                  ║")
    println("║   • WASD      → Movement                                   ║")
    println("║   • Mouse     → Look Around                                ║")
    println("║   • Q/E       → Ascend/Descend                             ║")
    println("║   • Regen     → Generate New Terrain                       ║")
    println("╚════════════════════════════════════════════════════════════╝")
}
