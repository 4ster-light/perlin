package dev.aster.camera;

import java.util.EnumMap;
import java.util.Map;

/**
 * First-person camera system with WASD movement and mouse look.
 */
public final class Camera {

    private static final double MIN_PITCH = -89.0;
    private static final double MAX_PITCH = 89.0;
    private static final double MOVEMENT_SPEED = 1.2;   // Units per frame
    private static final double VERTICAL_SPEED = 0.8;   // Units per frame (up/down)
    private static final double MOUSE_SENSITIVITY = 0.15;  // Increased for better mouse look

    /** Directional input keys tracked by the camera. */
    public enum InputKey { FORWARD, BACKWARD, LEFT, RIGHT, UP, DOWN }

    public double x = 100.0;
    public double y = 100.0;
    public double z = 50.0;
    public double yaw = 0.0;
    public double pitch = 0.0;

    public Camera() {}

    public Camera(double x, double y, double z, double yaw, double pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    private final Map<InputKey, Boolean> inputState = new EnumMap<>(InputKey.class);

    /**
     * Update camera position based on input state.
     *
     * <p>Movement follows the forward vector (sin yaw, cos yaw) and the right
     * vector (cos yaw, -sin yaw), so WASD always moves relative to where the
     * camera is looking (yaw only, pitch is ignored for movement).
     */
    public void update() {
        double radYaw = Math.toRadians(yaw);
        double forwardX = Math.sin(radYaw);
        double forwardY = Math.cos(radYaw);
        double rightX = Math.cos(radYaw);
        double rightY = -Math.sin(radYaw);

        if (inputState.getOrDefault(InputKey.FORWARD, false)) {
            x += forwardX * MOVEMENT_SPEED;
            y += forwardY * MOVEMENT_SPEED;
        }
        if (inputState.getOrDefault(InputKey.BACKWARD, false)) {
            x -= forwardX * MOVEMENT_SPEED;
            y -= forwardY * MOVEMENT_SPEED;
        }
        if (inputState.getOrDefault(InputKey.LEFT, false)) {
            x -= rightX * MOVEMENT_SPEED;
            y -= rightY * MOVEMENT_SPEED;
        }
        if (inputState.getOrDefault(InputKey.RIGHT, false)) {
            x += rightX * MOVEMENT_SPEED;
            y += rightY * MOVEMENT_SPEED;
        }
        if (inputState.getOrDefault(InputKey.UP, false)) {
            z += VERTICAL_SPEED;
        }
        if (inputState.getOrDefault(InputKey.DOWN, false)) {
            z -= VERTICAL_SPEED;
        }
    }

    /** Set input key state ({@code true} = pressed, {@code false} = released). */
    public void setInputKey(InputKey key, boolean pressed) {
        inputState.put(key, pressed);
    }

    /**
     * Update camera rotation based on mouse movement.
     *
     * <p>Yaw follows the demo's convention: yaw 0 looks along +Y and moving the
     * mouse right increases yaw (turning toward +X).
     */
    public void rotate(double deltaX, double deltaY) {
        yaw += deltaX * MOUSE_SENSITIVITY;
        pitch -= deltaY * MOUSE_SENSITIVITY;
        pitch = Math.clamp(pitch, MIN_PITCH, MAX_PITCH);

        // Normalize yaw to [0, 360)
        yaw = ((yaw % 360.0) + 360.0) % 360.0;
    }

    /** Record holding the three vector components. */
    public record Vector3(double x, double y, double z) {}

    /** Get forward vector based on current orientation. */
    public Vector3 getForwardVector() {
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);

        return new Vector3(
                Math.sin(radYaw) * Math.cos(radPitch),
                Math.cos(radYaw) * Math.cos(radPitch),
                Math.sin(radPitch));
    }

    /** Get right vector based on current orientation. */
    public Vector3 getRightVector() {
        double radYaw = Math.toRadians(yaw + 90.0);
        return new Vector3(Math.sin(radYaw), Math.cos(radYaw), 0.0);
    }
}
