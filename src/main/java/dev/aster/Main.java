package dev.aster;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Entry point: builds the window, the initial terrain and the control bar.
 */
public final class Main {

    private static final int TERRAIN_WIDTH = 200;
    private static final int TERRAIN_HEIGHT = 200;
    private static final double TERRAIN_SCALE = 0.05;
    private static final int TERRAIN_OCTAVES = 5;
    private static final double TERRAIN_HEIGHT_MULTIPLIER = 50.0;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createAndShowGui);
    }

    private static void createAndShowGui() {
        JFrame frame = new JFrame("Perlin Noise Landscape Generator - First Person Experience");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Create initial terrain
        long currentSeed = System.currentTimeMillis();
        PerlinNoise perlinNoise = new PerlinNoise();
        Terrain terrain = new Terrain(
                TERRAIN_WIDTH, TERRAIN_HEIGHT, perlinNoise,
                TERRAIN_SCALE, TERRAIN_OCTAVES, TERRAIN_HEIGHT_MULTIPLIER);

        // Mutable holder so the regenerate button can swap the renderer on the EDT
        Renderer3D[] rendererHolder = {new Renderer3D(terrain)};
        frame.add(rendererHolder[0], BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new BorderLayout(10, 5));
        controlPanel.setBackground(new Color(35, 35, 45));
        controlPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(100, 150, 255)));

        JPanel leftInfoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        leftInfoPanel.setBackground(new Color(35, 35, 45));
        leftInfoPanel.setOpaque(false);

        JLabel infoLabel = new JLabel("◆ First Person Experience • WASD Move • Mouse Look • Q/E Up/Down");
        infoLabel.setForeground(new Color(200, 200, 200));
        infoLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        leftInfoPanel.add(infoLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        rightPanel.setBackground(new Color(35, 35, 45));
        rightPanel.setOpaque(false);

        JButton regenerateButton = new JButton("⟳ Regenerate Terrain");
        regenerateButton.setBackground(new Color(70, 130, 180));
        regenerateButton.setForeground(Color.WHITE);
        regenerateButton.setFont(new Font("Arial", Font.BOLD, 12));
        regenerateButton.setFocusPainted(false);
        regenerateButton.setBorderPainted(false);
        regenerateButton.setOpaque(true);
        regenerateButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        regenerateButton.addActionListener(e -> {
            regenerateButton.setEnabled(false);
            regenerateButton.setText("⟳ Generating...");

            // Start async terrain generation
            Thread worker = new Thread(() -> {
                try {
                    long newSeed = System.currentTimeMillis();
                    PerlinNoise newNoise = new PerlinNoise(newSeed);
                    Terrain newTerrain = new Terrain(
                            TERRAIN_WIDTH, TERRAIN_HEIGHT, newNoise,
                            TERRAIN_SCALE, TERRAIN_OCTAVES, TERRAIN_HEIGHT_MULTIPLIER);

                    // Replace renderer on EDT
                    SwingUtilities.invokeLater(() -> {
                        frame.remove(rendererHolder[0]);
                        rendererHolder[0] = new Renderer3D(newTerrain);
                        frame.add(rendererHolder[0], BorderLayout.CENTER);
                        frame.revalidate();
                        frame.repaint();
                        rendererHolder[0].requestFocusInWindow();

                        System.out.println("✓ New terrain generated with seed: " + newSeed);

                        regenerateButton.setText("⟳ Regenerate Terrain");
                        regenerateButton.setEnabled(true);
                    });
                } catch (Exception ex) {
                    System.out.println("✗ Error generating terrain: " + ex.getMessage());
                    regenerateButton.setText("⟳ Regenerate Terrain");
                    regenerateButton.setEnabled(true);
                }
            });
            worker.setDaemon(true);
            worker.start();
        });

        regenerateButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                regenerateButton.setBackground(new Color(90, 150, 200));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                regenerateButton.setBackground(new Color(70, 130, 180));
            }
        });
        rightPanel.add(regenerateButton);

        controlPanel.add(leftInfoPanel, BorderLayout.WEST);
        controlPanel.add(rightPanel, BorderLayout.EAST);

        frame.add(controlPanel, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  Perlin Noise Landscape - First Person Experience Started  ║");
        System.out.println("╠════════════════════════════════════════════════════════════╣");
        System.out.println("║ Seed: " + currentSeed + "                                        ║");
        System.out.println("║ Terrain: 200x200 vertices • Scale: 0.05 • Octaves: 5       ║");
        System.out.println("║                                                            ║");
        System.out.println("║ Controls:                                                  ║");
        System.out.println("║   • WASD      → Movement                                   ║");
        System.out.println("║   • Mouse     → Look Around                                ║");
        System.out.println("║   • Q/E       → Ascend/Descend                             ║");
        System.out.println("║   • Regen     → Generate New Terrain                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
    }
}
