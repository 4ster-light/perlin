package dev.aster.app

import dev.aster.render.Renderer3D
import dev.aster.terrain.Terrain

import java.awt.{BorderLayout, Color, FlowLayout, Font}
import java.awt.event.{ActionListener, MouseAdapter, MouseEvent}
import javax.swing.{BorderFactory, JButton, JFrame, JLabel, JPanel, SwingUtilities}

/** Entry point: builds the window, the initial world and the control bar. */
object Main {

  private val PRELOAD_RADIUS = Renderer3D.RENDER_DISTANCE + 24.0

  def main(args: Array[String]): Unit = {
    SwingUtilities.invokeLater(() => createAndShowGui())
  }

  private def createAndShowGui(): Unit = {
    val frame = new JFrame(
      "Infinite Procedural Landscape - First Person Experience"
    )
    frame.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE)
    frame.setLayout(new BorderLayout())

    // Create the initial world and preload the chunks around the spawn so the
    // first painted frame does not have to generate them.
    val currentSeed = System.currentTimeMillis()
    val terrain = new Terrain(currentSeed)
    terrain.preloadRegion(terrain.spawnX, terrain.spawnY, PRELOAD_RADIUS)

    // Mutable holder so the regenerate button can swap the renderer on the EDT.
    val rendererHolder = Array[Renderer3D](new Renderer3D(terrain))
    frame.add(rendererHolder(0), BorderLayout.CENTER)

    val controlPanel = new JPanel(new BorderLayout(10, 5))
    controlPanel.setBackground(new Color(35, 35, 45))
    controlPanel.setBorder(
      BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(100, 150, 255))
    )

    val leftInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8))
    leftInfoPanel.setBackground(new Color(35, 35, 45))
    leftInfoPanel.setOpaque(false)

    val infoLabel = new JLabel(
      "◆ First Person Experience • WASD Move • Mouse Look • Q/E Up/Down"
    )
    infoLabel.setForeground(new Color(200, 200, 200))
    infoLabel.setFont(new Font("Monospaced", Font.PLAIN, 12))
    leftInfoPanel.add(infoLabel)

    val rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8))
    rightPanel.setBackground(new Color(35, 35, 45))
    rightPanel.setOpaque(false)

    val regenerateButton = new JButton("⟳ Regenerate Terrain")
    regenerateButton.setBackground(new Color(70, 130, 180))
    regenerateButton.setForeground(Color.WHITE)
    regenerateButton.setFont(new Font("Arial", Font.BOLD, 12))
    regenerateButton.setFocusPainted(false)
    regenerateButton.setBorderPainted(false)
    regenerateButton.setOpaque(true)
    regenerateButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12))

    regenerateButton.addActionListener(new ActionListener {
      override def actionPerformed(e: java.awt.event.ActionEvent): Unit = {
        regenerateButton.setEnabled(false)
        regenerateButton.setText("⟳ Generating...")

        // Build and preload the next world off the EDT.
        val worker = new Thread(new Runnable {
          override def run(): Unit = {
            try {
              val newSeed = System.currentTimeMillis()
              val newTerrain = new Terrain(newSeed)
              newTerrain.preloadRegion(
                newTerrain.spawnX,
                newTerrain.spawnY,
                PRELOAD_RADIUS
              )

              SwingUtilities.invokeLater(() => {
                frame.remove(rendererHolder(0))
                rendererHolder(0) = new Renderer3D(newTerrain)
                frame.add(rendererHolder(0), BorderLayout.CENTER)
                frame.revalidate()
                frame.repaint()
                rendererHolder(0).requestFocusInWindow()

                println("✓ New terrain generated with seed: " + newSeed)

                regenerateButton.setText("⟳ Regenerate Terrain")
                regenerateButton.setEnabled(true)
              })
            } catch {
              case ex: Exception =>
                println("✗ Error generating terrain: " + ex.getMessage)
                regenerateButton.setText("⟳ Regenerate Terrain")
                regenerateButton.setEnabled(true)
            }
          }
        })
        worker.setDaemon(true)
        worker.start()
      }
    })

    regenerateButton.addMouseListener(new MouseAdapter {
      override def mouseEntered(e: MouseEvent): Unit = {
        regenerateButton.setBackground(new Color(90, 150, 200))
      }

      override def mouseExited(e: MouseEvent): Unit = {
        regenerateButton.setBackground(new Color(70, 130, 180))
      }
    })
    rightPanel.add(regenerateButton)

    controlPanel.add(leftInfoPanel, BorderLayout.WEST)
    controlPanel.add(rightPanel, BorderLayout.EAST)

    frame.add(controlPanel, BorderLayout.SOUTH)

    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.setVisible(true)

    println("╔════════════════════════════════════════════════════════════╗")
    println("║ Infinite Procedural Landscape - First Person Started       ║")
    println("╠════════════════════════════════════════════════════════════╣")
    println("║ Seed: " + currentSeed + "                                        ║")
    println("║ Terrain: infinite • chunks generated as you explore        ║")
    println("║                                                            ║")
    println("║ Controls:                                                  ║")
    println("║   • WASD      → Movement                                   ║")
    println("║   • Mouse     → Look Around                                ║")
    println("║   • Q/E       → Ascend/Descend                             ║")
    println("║   • Regen     → Generate New Terrain                       ║")
    println("╚════════════════════════════════════════════════════════════╝")
  }
}
