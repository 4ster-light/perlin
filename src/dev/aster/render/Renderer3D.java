package dev.aster.render;

import dev.aster.camera.Camera;
import dev.aster.camera.CollisionDetector;
import dev.aster.terrain.Terrain;
import dev.aster.ui.HeadsUpDisplay;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
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
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Software-rendered first-person terrain view with mouse look and WASD movement.
 *
 * <p>Movement and collision are delegated to {@link Camera} and
 * {@link CollisionDetector}; visibility culling to {@link ViewFrustum}. This
 * class only owns projection and rasterization.
 *
 * <p>To keep the hot frame path allocation-free:
 * <ul>
 *   <li>vertices are projected once each into reusable primitive arrays instead
 *       of once per cell corner into {@code ProjectedPoint} records,</li>
 *   <li>triangles are accumulated into primitive arrays and depth-sorted with a
 *       primitive {@code long[]} (no comparator, no {@code ProjectedTriangle}
 *       objects),</li>
 *   <li>triangles entirely outside the viewport are rejected before sorting and
 *       drawing,</li>
 *   <li>colors come from cached palettes in {@link Terrain},</li>
 *   <li>the terrain is rasterized into a reusable pixel buffer (scanline spans
 *       for fills, Bresenham for wireframes) and blitted once per frame instead
 *       of issuing several Java2D calls per triangle.</li>
 * </ul>
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
    public static final int RENDER_DISTANCE = 120;
    private static final double NEAR_PLANE = 0.5;
    private static final double FAR_PLANE = RENDER_DISTANCE * 2.0;
    private static final int FRAME_DELAY_MS = 16;

    private static final BasicStroke CROSSHAIR_STROKE = new BasicStroke(2f);
    private static final Font INSTRUCTIONS_FONT = new Font("Arial", Font.BOLD, 14);
    private static final Color INSTRUCTIONS_BACKGROUND = new Color(0, 0, 0, 180);
    private static final int SKY_RGB = 0x87CEEB; // (135, 206, 235)

    // Only the near/far planes are used by the rasterizer (the side planes are
    // handled for free by the screen-space bounding-box rejection below).
    private final ViewFrustum frustum = new ViewFrustum(FOV, 1.0, NEAR_PLANE, FAR_PLANE);

    // Reusable per-frame scratch buffers. They are sized for the largest region
    // the render distance can produce, independent of the terrain size.
    private final int maxRegionWidth;
    private final int maxRegionHeight;
    private final int[] vertexScreenX;
    private final int[] vertexScreenY;
    private final float[] vertexDepth;
    private final byte[] vertexColor;
    private final boolean[] vertexValid;
    private final double[] columnCos;
    private final double[] columnSin;
    private final double[] rowCos;
    private final double[] rowSin;

    private final int maxTriangles;
    private final int[] triangleVertices; // 6 screen coordinates per triangle
    private final byte[] triangleColor;
    private final float[] triangleDepth;
    private final long[] sortKeys;

    // Reusable software-rasterizer target. Painting into an int[] and blitting
    // once per frame is dramatically cheaper than 4 Java2D calls per triangle.
    private BufferedImage terrainBuffer;
    private int[] terrainPixels;
    private int bufferWidth;
    private int bufferHeight;

    private final Timer frameTimer;

    public Renderer3D(Terrain terrain) {
        this.terrain = terrain;
        this.camera = new Camera(terrain.spawnX(), terrain.spawnY(), 0.0, 0.0, 0.0);
        this.collision = new CollisionDetector(terrain);

        // Start hovering above the terrain surface
        collision.adjustCameraPosition(camera);

        this.maxRegionWidth = RENDER_DISTANCE * 2 + 1;
        this.maxRegionHeight = RENDER_DISTANCE * 2 + 1;
        int maxVertices = maxRegionWidth * maxRegionHeight;
        this.maxTriangles = maxVertices * 2;

        this.vertexScreenX = new int[maxVertices];
        this.vertexScreenY = new int[maxVertices];
        this.vertexDepth = new float[maxVertices];
        this.vertexColor = new byte[maxVertices];
        this.vertexValid = new boolean[maxVertices];
        this.columnCos = new double[maxRegionWidth];
        this.columnSin = new double[maxRegionWidth];
        this.rowCos = new double[maxRegionHeight];
        this.rowSin = new double[maxRegionHeight];

        this.triangleVertices = new int[maxTriangles * 6];
        this.triangleColor = new byte[maxTriangles];
        this.triangleDepth = new float[maxTriangles];
        this.sortKeys = new long[maxTriangles];

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

        // EDT-driven game loop. Running update + repaint on the same thread keeps
        // camera state single-threaded and lets Swing coalesce frames when the
        // software renderer cannot keep up. The timer is stopped in removeNotify
        // so discarded renderers from "Regenerate" do not keep ticking.
        this.frameTimer = new Timer(FRAME_DELAY_MS, e -> {
            update();
            repaint();
        });
        this.frameTimer.setCoalesce(true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        frameTimer.start();
        requestFocusInWindow();
    }

    @Override
    public void removeNotify() {
        frameTimer.stop();
        super.removeNotify();
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

        // Terrain is rendered without antialiasing, exactly as the original
        // off-screen buffer path did (the buffer never inherited the AA hint).
        // Antialiased software polygon fills are roughly an order of magnitude
        // more expensive, so keep AA for the HUD only.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        renderTerrain(g2d);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw HUD
        var hudData = new HeadsUpDisplay.HUDData(
                camera.x, camera.y, camera.z,
                currentFPS,
                terrain,
                camera.yaw);
        hud.draw(g2d, hudData, getWidth(), getHeight());

        // Draw crosshair
        g2d.setColor(Color.WHITE);
        g2d.setStroke(CROSSHAIR_STROKE);
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        g2d.drawLine(cx - 10, cy, cx + 10, cy);
        g2d.drawLine(cx, cy - 10, cx, cy + 10);

        // Instructions if mouse not captured
        if (!mouseCaptured) {
            g2d.setColor(INSTRUCTIONS_BACKGROUND);
            g2d.fillRect(getWidth() / 2 - 150, getHeight() / 2 + 30, 300, 30);
            g2d.setColor(Color.WHITE);
            g2d.setFont(INSTRUCTIONS_FONT);
            g2d.drawString("Click to capture mouse (ESC to release)", getWidth() / 2 - 130, getHeight() / 2 + 50);
        }
    }

    private void renderTerrain(Graphics2D g) {
        final int screenWidth = getWidth();
        final int screenHeight = getHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        ensureTerrainBuffer(screenWidth, screenHeight);
        Arrays.fill(terrainPixels, SKY_RGB);

        // Precompute camera transform values once per frame
        double yawRad = Math.toRadians(camera.yaw);
        double pitchRad = Math.toRadians(camera.pitch);
        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        double fovScale = 1.0 / Math.tan(Math.toRadians(FOV / 2.0));
        double halfWidth = screenWidth / 2.0;
        double halfHeight = screenHeight / 2.0;
        double scaleX = fovScale * halfWidth;
        double scaleY = fovScale * halfHeight;

        final double camX = camera.x;
        final double camY = camera.y;
        final double camZ = camera.z;

        int camGridX = (int) Math.floor(camX);
        int camGridY = (int) Math.floor(camY);

        int xStart = camGridX - RENDER_DISTANCE;
        int xEnd = camGridX + RENDER_DISTANCE;
        int yStart = camGridY - RENDER_DISTANCE;
        int yEnd = camGridY + RENDER_DISTANCE;

        // Generate any chunks that entered the view since the last frame.
        terrain.ensureRegionLoaded(xStart, yStart, xEnd, yEnd);

        final int regionWidth = xEnd - xStart + 1;
        final int regionHeight = yEnd - yStart + 1;
        final int stride = regionWidth;

        // Precompute the yaw rotation terms for each column and row. This turns
        // the per-vertex rotation into two subtractions/additions.
        for (int col = 0; col < regionWidth; col++) {
            double dx = (xStart + col) - camX;
            columnCos[col] = dx * cosYaw;
            columnSin[col] = dx * sinYaw;
        }
        for (int row = 0; row < regionHeight; row++) {
            double dy = (yStart + row) - camY;
            rowCos[row] = dy * cosYaw;
            rowSin[row] = dy * sinYaw;
        }

        // Project each vertex once (previously each cell re-projected all four
        // of its corners, so shared vertices were transformed up to 4x).
        for (int col = 0; col < regionWidth; col++) {
            int gx = xStart + col;
            double cx = columnCos[col];
            double sx = columnSin[col];
            int base = col;
            for (int row = 0; row < regionHeight; row++) {
                int vi = base + row * stride;
                int gy = yStart + row;
                double wz = terrain.heightAt(gx, gy);
                double rx = cx - rowSin[row];
                double ry = sx + rowCos[row];
                double dz = wz - camZ;
                double depth = ry * cosPitch - dz * sinPitch;

                if (frustum.isDepthInFrustum(depth)) {
                    double finalZ = ry * sinPitch + dz * cosPitch;
                    double invDepth = 1.0 / depth;
                    vertexScreenX[vi] = (int) (halfWidth + rx * invDepth * scaleX);
                    vertexScreenY[vi] = (int) (halfHeight - finalZ * invDepth * scaleY);
                    vertexDepth[vi] = (float) depth;
                    vertexColor[vi] = (byte) terrain.colorIndexAt(gx, gy);
                    vertexValid[vi] = true;
                } else {
                    vertexValid[vi] = false;
                }
            }
        }

        // Build screen-space triangles, rejecting off-screen ones immediately.
        int triangleCount = 0;
        for (int col = 0; col < regionWidth - 1; col++) {
            for (int row = 0; row < regionHeight - 1; row++) {
                int i1 = col + row * stride;
                int i2 = i1 + stride; // (gx + 1, gy)
                int i3 = i1 + 1;      // (gx, gy + 1)
                int i4 = i2 + 1;      // (gx + 1, gy + 1)

                // Triangle 1: p1, p2, p3
                if (vertexValid[i1] && vertexValid[i2] && vertexValid[i3]) {
                    triangleCount = addTriangle(triangleCount, i1, i2, i3, screenWidth, screenHeight);
                }

                // Triangle 2: p2, p4, p3
                if (vertexValid[i2] && vertexValid[i4] && vertexValid[i3]) {
                    triangleCount = addTriangle(triangleCount, i2, i4, i3, screenWidth, screenHeight);
                }
            }
        }

        if (triangleCount > 0) {
            // Sort far-to-near with primitive keys (no comparator / no objects).
            for (int i = 0; i < triangleCount; i++) {
                sortKeys[i] = ((long) Float.floatToIntBits(triangleDepth[i]) << 32) | i;
            }
            Arrays.sort(sortKeys, 0, triangleCount);

            // Iterate the sorted keys backwards so the farthest triangle is
            // drawn first (painter's algorithm).
            for (int k = triangleCount - 1; k >= 0; k--) {
                int tri = (int) sortKeys[k];
                int offset = tri * 6;
                int colorIndex = triangleColor[tri];

                int x1 = triangleVertices[offset];
                int y1 = triangleVertices[offset + 1];
                int x2 = triangleVertices[offset + 2];
                int y2 = triangleVertices[offset + 3];
                int x3 = triangleVertices[offset + 4];
                int y3 = triangleVertices[offset + 5];

                fillTriangle(x1, y1, x2, y2, x3, y3, Terrain.rgbForIndex(colorIndex));

                // Wireframe for definition
                int wire = Terrain.wireRgbForIndex(colorIndex);
                drawLine(x1, y1, x2, y2, wire);
                drawLine(x2, y2, x3, y3, wire);
                drawLine(x3, y3, x1, y1, wire);
            }
        }

        g.drawImage(terrainBuffer, 0, 0, null);
    }

    private void ensureTerrainBuffer(int width, int height) {
        if (terrainBuffer != null && bufferWidth == width && bufferHeight == height) {
            return;
        }
        terrainBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        terrainPixels = ((DataBufferInt) terrainBuffer.getRaster().getDataBuffer()).getData();
        bufferWidth = width;
        bufferHeight = height;
    }

    /**
     * Fills a screen-space triangle using scanline spans. Per row only the two
     * edge intersections are computed, then the whole span is filled with a
     * single {@link Arrays#fill} call.
     */
    private void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3, int rgb) {
        // Sort the vertices by y so the triangle has a top and bottom half.
        if (y1 > y2) {
            int t = x1; x1 = x2; x2 = t;
            t = y1; y1 = y2; y2 = t;
        }
        if (y2 > y3) {
            int t = x2; x2 = x3; x3 = t;
            t = y2; y2 = y3; y3 = t;
        }
        if (y1 > y2) {
            int t = x1; x1 = x2; x2 = t;
            t = y1; y1 = y2; y2 = t;
        }

        if (y1 == y3 || y3 < 0 || y1 >= bufferHeight) {
            return;
        }

        // Rows whose pixel centers fall inside the triangle.
        int yStart = Math.max(0, (int) Math.ceil(y1 - 0.5));
        int yEnd = Math.min(bufferHeight - 1, (int) Math.floor(y3 - 0.5));
        if (yStart > yEnd) {
            return;
        }

        double dyTop = y2 - y1;
        double dyBottom = y3 - y2;
        double dyLong = y3 - y1;
        double dxTop = x2 - x1;
        double dxBottom = x3 - x2;
        double dxLong = x3 - x1;

        for (int y = yStart; y <= yEnd; y++) {
            double sampleY = y + 0.5;
            double xa;
            double xb;
            if (sampleY < y2) {
                xa = x1 + (sampleY - y1) * dxTop / dyTop;
            } else {
                xa = x2 + (sampleY - y2) * dxBottom / dyBottom;
            }
            xb = x1 + (sampleY - y1) * dxLong / dyLong;

            if (xa > xb) {
                double t = xa;
                xa = xb;
                xb = t;
            }

            int left = (int) Math.ceil(xa - 0.5);
            int right = (int) Math.floor(xb - 0.5);
            if (left < 0) left = 0;
            if (right >= bufferWidth) right = bufferWidth - 1;

            if (left <= right) {
                int row = y * bufferWidth;
                Arrays.fill(terrainPixels, row + left, row + right + 1, rgb);
            }
        }
    }

    /** Draws a 1px Bresenham line, clipping it to the viewport first. */
    private void drawLine(int x0, int y0, int x1, int y1, int rgb) {
        int code0 = outCode(x0, y0);
        int code1 = outCode(x1, y1);

        // Cohen-Sutherland clipping
        while (true) {
            if ((code0 | code1) == 0) {
                break;
            }
            if ((code0 & code1) != 0) {
                return;
            }

            int code = code0 != 0 ? code0 : code1;
            double x;
            double y;
            if ((code & 4) != 0) {
                y = 0;
                x = x0 + (double) (x1 - x0) * (0 - y0) / (y1 - y0);
            } else if ((code & 8) != 0) {
                y = bufferHeight - 1;
                x = x0 + (double) (x1 - x0) * (bufferHeight - 1 - y0) / (y1 - y0);
            } else if ((code & 1) != 0) {
                x = 0;
                y = y0 + (double) (y1 - y0) * (0 - x0) / (x1 - x0);
            } else {
                x = bufferWidth - 1;
                y = y0 + (double) (y1 - y0) * (bufferWidth - 1 - x0) / (x1 - x0);
            }

            int ix = (int) Math.round(x);
            int iy = (int) Math.round(y);
            if (code == code0) {
                x0 = ix;
                y0 = iy;
                code0 = outCode(x0, y0);
            } else {
                x1 = ix;
                y1 = iy;
                code1 = outCode(x1, y1);
            }
        }

        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            terrainPixels[y0 * bufferWidth + x0] = rgb;
            if (x0 == x1 && y0 == y1) {
                return;
            }
            int e2 = err << 1;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private int outCode(int x, int y) {
        int code = 0;
        if (x < 0) {
            code |= 1;
        } else if (x >= bufferWidth) {
            code |= 2;
        }
        if (y < 0) {
            code |= 4;
        } else if (y >= bufferHeight) {
            code |= 8;
        }
        return code;
    }

    /**
     * Stores one projected triangle, first rejecting it when its screen-space
     * bounding box does not overlap the viewport.
     *
     * @return the new triangle count
     */
    private int addTriangle(int count, int i1, int i2, int i3, int screenWidth, int screenHeight) {
        int x1 = vertexScreenX[i1];
        int y1 = vertexScreenY[i1];
        int x2 = vertexScreenX[i2];
        int y2 = vertexScreenY[i2];
        int x3 = vertexScreenX[i3];
        int y3 = vertexScreenY[i3];

        int minX = Math.min(x1, Math.min(x2, x3));
        int maxX = Math.max(x1, Math.max(x2, x3));
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));

        if (maxX < 0 || minX >= screenWidth || maxY < 0 || minY >= screenHeight) {
            return count;
        }

        // Keep the original margin cull for visual parity. It rejects triangles
        // with a vertex more than 500px outside the viewport, which can drop
        // extremely close, mostly-off-screen cells; without it the framing
        // changes noticeably around the player's feet.
        if (x1 < -500 || x1 > screenWidth + 500 || y1 < -500 || y1 > screenHeight + 500
                || x2 < -500 || x2 > screenWidth + 500 || y2 < -500 || y2 > screenHeight + 500
                || x3 < -500 || x3 > screenWidth + 500 || y3 < -500 || y3 > screenHeight + 500) {
            return count;
        }

        int offset = count * 6;
        triangleVertices[offset] = x1;
        triangleVertices[offset + 1] = y1;
        triangleVertices[offset + 2] = x2;
        triangleVertices[offset + 3] = y2;
        triangleVertices[offset + 4] = x3;
        triangleVertices[offset + 5] = y3;

        triangleDepth[count] = (vertexDepth[i1] + vertexDepth[i2] + vertexDepth[i3]) / 3.0f;

        // Pick a majority surface color among the corners so biome borders do
        // not create a directional grid pattern.
        int c1 = vertexColor[i1];
        int c2 = vertexColor[i2];
        int c3 = vertexColor[i3];
        int color = (c1 == c2 || c1 == c3) ? c1 : (c2 == c3 ? c2 : c1);
        triangleColor[count] = (byte) color;

        return count + 1;
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
