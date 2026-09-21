package dev.aster.render;

import dev.aster.camera.Camera;
import dev.aster.camera.CollisionDetector;
import dev.aster.terrain.Terrain;
import dev.aster.ui.HeadsUpDisplay;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;

/**
 * Software-rendered first-person terrain view with mouse look and WASD movement.
 *
 * <p>Movement and collision are delegated to {@link Camera} and
 * {@link CollisionDetector}; visibility culling to {@link ViewFrustum}. This
 * class only owns projection and rasterization.
 */
public final class Renderer3D extends JPanel implements KeyListener {

    // Rendering: real painted frames (counted in paintComponent, because Swing
    // coalesces repaint() calls and the game-loop tick rate overstates FPS)
    private long lastFrameTime = System.currentTimeMillis();
    private int frameCount = 0;
    private int currentFPS = 0;

    private final Terrain terrain;
    private final Camera camera;
    private final CollisionDetector collision;
    private final HeadsUpDisplay hud = new HeadsUpDisplay();

    // Mouse control
    private Robot robot;
    private boolean mouseCaptured = false;

    // Movement state (raw key codes, mapped onto Camera.InputKey in update())
    private final Set<Integer> keysPressed = new HashSet<>();

    private static final double FOV = 70.0;
    private static final int RENDER_DISTANCE = 120;

    /** A projected, screen-space triangle ready for painter's-algorithm drawing. */
    private record ProjectedTriangle(
            int x1, int y1,
            int x2, int y2,
            int x3, int y3,
            double depth,
            Color color) {}

    /** Screen coordinates plus depth, or {@code null} when the point is culled. */
    private record ProjectedPoint(int x, int y, double depth) {}

    public Renderer3D(Terrain terrain) {
        this.terrain = terrain;
        this.camera = new Camera(terrain.width() / 2.0, terrain.height() / 2.0, 0.0, 0.0, 0.0);
        this.collision = new CollisionDetector(terrain);

        // Start hovering above the terrain surface
        collision.adjustCameraPosition(camera);

        setPreferredSize(new Dimension(1200, 800));
        setBackground(new Color(135, 206, 235));

        setFocusable(true);
        addKeyListener(this);
        requestFocusInWindow();

        // Try to create robot for mouse capture
        try {
            robot = new Robot();
        } catch (Exception e) {
            System.out.println("Could not create Robot for mouse capture: " + e.getMessage());
        }

        // Mouse click to capture
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!mouseCaptured) {
                    captureMouse();
                }
            }
        });

        // Mouse motion for looking around
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (mouseCaptured && robot != null) {
                    int centerX = getWidth() / 2;
                    int centerY = getHeight() / 2;

                    int dx = e.getX() - centerX;
                    int dy = e.getY() - centerY;

                    if (dx != 0 || dy != 0) {
                        // Raw pixel deltas; Camera.rotate applies sensitivity.
                        // Negative deltaY keeps the demo's inverted pitch feel
                        // (mouse up = look up).
                        camera.rotate(dx, -dy);

                        // Reset mouse to center
                        Point loc = getLocationOnScreen();
                        robot.mouseMove(loc.x + centerX, loc.y + centerY);
                    }
                }
            }
        });

        // Game loop
        Thread gameLoop = new Thread(() -> {
            while (true) {
                update();
                repaint();
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        gameLoop.setDaemon(true);
        gameLoop.start();
    }

    private void captureMouse() {
        mouseCaptured = true;
        setCursor(Toolkit.getDefaultToolkit().createCustomCursor(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                new Point(0, 0),
                "blank"));
        // Center mouse
        if (robot != null) {
            Point loc = getLocationOnScreen();
            robot.mouseMove(loc.x + getWidth() / 2, loc.y + getHeight() / 2);
        }
    }

    private void releaseMouse() {
        mouseCaptured = false;
        setCursor(Cursor.getDefaultCursor());
    }

    private void update() {
        // Map raw key codes onto directional camera input
        camera.setInputKey(Camera.InputKey.FORWARD, keysPressed.contains(KeyEvent.VK_W));
        camera.setInputKey(Camera.InputKey.BACKWARD, keysPressed.contains(KeyEvent.VK_S));
        camera.setInputKey(Camera.InputKey.LEFT, keysPressed.contains(KeyEvent.VK_A));
        camera.setInputKey(Camera.InputKey.RIGHT, keysPressed.contains(KeyEvent.VK_D));
        camera.setInputKey(Camera.InputKey.UP,
                keysPressed.contains(KeyEvent.VK_Q) || keysPressed.contains(KeyEvent.VK_SPACE));
        camera.setInputKey(Camera.InputKey.DOWN,
                keysPressed.contains(KeyEvent.VK_E) || keysPressed.contains(KeyEvent.VK_SHIFT));

        // Move, then keep the camera inside the terrain and above its surface
        camera.update();
        collision.clampCameraPosition(camera);
        collision.adjustCameraPosition(camera);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // FPS measurement: count frames actually painted on screen
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFrameTime >= 1000) {
            currentFPS = frameCount;
            frameCount = 0;
            lastFrameTime = now;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Create buffer
        BufferedImage buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D bg = buffer.createGraphics();
        bg.setColor(getBackground());
        bg.fillRect(0, 0, getWidth(), getHeight());

        // Render terrain
        renderTerrain(bg);

        bg.dispose();
        g2d.drawImage(buffer, 0, 0, null);

        // Draw HUD
        var hudData = new HeadsUpDisplay.HUDData(
                camera.x, camera.y, camera.z,
                currentFPS,
                terrain.width(), terrain.height(),
                terrain,
                camera.yaw);
        hud.draw(g2d, hudData, getWidth(), getHeight());

        // Draw crosshair
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2f));
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        g2d.drawLine(cx - 10, cy, cx + 10, cy);
        g2d.drawLine(cx, cy - 10, cx, cy + 10);

        // Instructions if mouse not captured
        if (!mouseCaptured) {
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRect(getWidth() / 2 - 150, getHeight() / 2 + 30, 300, 30);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            g2d.drawString("Click to capture mouse (ESC to release)", getWidth() / 2 - 130, getHeight() / 2 + 50);
        }
    }

    private void renderTerrain(Graphics2D g) {
        // Precompute camera transform values
        double yawRad = Math.toRadians(camera.yaw);
        double pitchRad = Math.toRadians(camera.pitch);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        double fovScale = 1.0 / Math.tan(Math.toRadians(FOV / 2.0));
        ViewFrustum frustum = new ViewFrustum(
                FOV, (double) getWidth() / getHeight(), 0.5, RENDER_DISTANCE * 2.0);

        List<ProjectedTriangle> triangles = new ArrayList<>();

        int camGridX = (int) camera.x;
        int camGridY = (int) camera.y;

        int xStart = Math.max(0, camGridX - RENDER_DISTANCE);
        int xEnd = Math.min(terrain.width() - 1, camGridX + RENDER_DISTANCE);
        int yStart = Math.max(0, camGridY - RENDER_DISTANCE);
        int yEnd = Math.min(terrain.height() - 1, camGridY + RENDER_DISTANCE);

        for (int gx = xStart; gx < xEnd; gx++) {
            for (int gy = yStart; gy < yEnd; gy++) {

                // Get the 4 corners of this grid cell
                double wx1 = gx;
                double wy1 = gy;
                double wz1 = terrain.getHeight(gx, gy);

                double wx2 = gx + 1;
                double wy2 = gy;
                double wz2 = terrain.getHeight(gx + 1, gy);

                double wx3 = gx;
                double wy3 = gy + 1;
                double wz3 = terrain.getHeight(gx, gy + 1);

                double wx4 = gx + 1;
                double wy4 = gy + 1;
                double wz4 = terrain.getHeight(gx + 1, gy + 1);

                // Project all 4 points
                ProjectedPoint p1 = projectPoint(wx1, wy1, wz1, cosYaw, sinYaw, cosPitch, sinPitch, fovScale, frustum);
                ProjectedPoint p2 = projectPoint(wx2, wy2, wz2, cosYaw, sinYaw, cosPitch, sinPitch, fovScale, frustum);
                ProjectedPoint p3 = projectPoint(wx3, wy3, wz3, cosYaw, sinYaw, cosPitch, sinPitch, fovScale, frustum);
                ProjectedPoint p4 = projectPoint(wx4, wy4, wz4, cosYaw, sinYaw, cosPitch, sinPitch, fovScale, frustum);

                // Triangle 1: p1, p2, p3
                if (p1 != null && p2 != null && p3 != null) {
                    double avgHeight = (wz1 + wz2 + wz3) / 3.0;
                    double depth = (p1.depth() + p2.depth() + p3.depth()) / 3.0;
                    triangles.add(new ProjectedTriangle(
                            p1.x(), p1.y(),
                            p2.x(), p2.y(),
                            p3.x(), p3.y(),
                            depth,
                            terrain.getColorForHeight(avgHeight)));
                }

                // Triangle 2: p2, p4, p3
                if (p2 != null && p4 != null && p3 != null) {
                    double avgHeight = (wz2 + wz4 + wz3) / 3.0;
                    double depth = (p2.depth() + p4.depth() + p3.depth()) / 3.0;
                    triangles.add(new ProjectedTriangle(
                            p2.x(), p2.y(),
                            p4.x(), p4.y(),
                            p3.x(), p3.y(),
                            depth,
                            terrain.getColorForHeight(avgHeight)));
                }
            }
        }

        // Sort by depth (far to near - painter's algorithm)
        triangles.sort((a, b) -> Double.compare(b.depth(), a.depth()));

        // Draw triangles
        for (ProjectedTriangle tri : triangles) {
            g.setColor(tri.color());
            g.fillPolygon(
                    new int[]{tri.x1(), tri.x2(), tri.x3()},
                    new int[]{tri.y1(), tri.y2(), tri.y3()},
                    3);
            // Wireframe for definition
            g.setColor(tri.color().darker());
            g.drawPolygon(
                    new int[]{tri.x1(), tri.x2(), tri.x3()},
                    new int[]{tri.y1(), tri.y2(), tri.y3()},
                    3);
        }
    }

    /**
     * Projects a world point to screen coordinates using first-person camera transform.
     *
     * @return screen coordinates plus depth, or {@code null} if outside the frustum
     */
    private ProjectedPoint projectPoint(
            double wx, double wy, double wz,
            double cosYaw, double sinYaw,
            double cosPitch, double sinPitch,
            double fovScale, ViewFrustum frustum) {

        // Translate to camera-relative coordinates
        double dx = wx - camera.x;
        double dy = wy - camera.y;
        double dz = wz - camera.z;

        // Rotate around Z axis (yaw - left/right)
        // This rotates the world so camera looks along +Y after rotation
        double rx = dx * cosYaw - dy * sinYaw;
        double ry = dx * sinYaw + dy * cosYaw;

        // Rotate around X axis (pitch - up/down)
        // ry becomes our "forward" direction (depth)
        double depth = ry * cosPitch - dz * sinPitch;
        double finalZ = ry * sinPitch + dz * cosPitch;

        // Frustum culling (near/far planes)
        if (!frustum.isPointInFrustum(rx, finalZ, depth)) {
            return null;
        }

        // Perspective projection
        int screenX = (int) (getWidth() / 2.0 + (rx / depth) * fovScale * getWidth() / 2.0);
        int screenY = (int) (getHeight() / 2.0 - (finalZ / depth) * fovScale * getHeight() / 2.0);

        // Screen-bounds culling
        if (screenX < -500 || screenX > getWidth() + 500 || screenY < -500 || screenY > getHeight() + 500) {
            return null;
        }

        return new ProjectedPoint(screenX, screenY, depth);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        keysPressed.add(e.getKeyCode());

        // ESC to release mouse
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            releaseMouse();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keysPressed.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {}
}
