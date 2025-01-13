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
    val frame = JFrame("Perlin Noise Landscape Generator - First Person")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.layout = BorderLayout()

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

    // Control panel with improved styling
    val controlPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5)).apply {
        background = Color(40, 40, 40)
    }

    val infoLabel = JLabel("First Person Controls: WASD=Move | Mouse=Look | Q/E=Up/Down").apply {
        foreground = Color.WHITE
        font = Font("Arial", Font.PLAIN, 12)
    }
    controlPanel.add(infoLabel)

    val regenerateButton = JButton("Regenerate Terrain").apply {
        background = Color(70, 130, 180)
        foreground = Color.WHITE
        font = Font("Arial", Font.BOLD, 12)
        isFocusPainted = false
        addActionListener {
            // Start async terrain generation
            Thread {
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

                    println("New terrain generated with seed: $currentSeed")
                }
            }.apply {
                isDaemon = true
                start()
            }
        }
    }
    controlPanel.add(regenerateButton)

    frame.add(controlPanel, BorderLayout.SOUTH)

    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.isVisible = true

    println("Perlin Noise Landscape Generator started")
    println("Initial seed: $currentSeed")
    println("Controls: WASD=Move, Mouse=Look, Q/E=Up/Down, Click 'Regenerate' for new terrain")
}

