package dev.aster

import kotlin.math.*

/**
 * First-person camera system with WASD movement and mouse look
 */
class Camera(
    var x: Double = 100.0,
    var y: Double = 100.0,
    var z: Double = 50.0,
    var yaw: Double = 0.0,
    var pitch: Double = 0.0
) {
    companion object {
        private const val MIN_PITCH = -89.0
        private const val MAX_PITCH = 89.0
        private const val MOVEMENT_SPEED = 1.2      // Increased for responsive movement
        private const val VERTICAL_SPEED = 0.8      // Increased for responsive vertical movement
        private const val MOUSE_SENSITIVITY = 0.15  // Increased for better mouse look
    }

    private val inputState = mutableMapOf<InputKey, Boolean>()

    enum class InputKey {
        FORWARD, BACKWARD, LEFT, RIGHT, UP, DOWN
    }

    /**
     * Update camera position based on input state
     */
    fun update() {
        val radYaw = Math.toRadians(yaw)

        if (inputState[InputKey.FORWARD] == true) {
            x += cos(radYaw) * MOVEMENT_SPEED
            y += sin(radYaw) * MOVEMENT_SPEED
        }
        if (inputState[InputKey.BACKWARD] == true) {
            x -= cos(radYaw) * MOVEMENT_SPEED
            y -= sin(radYaw) * MOVEMENT_SPEED
        }
        if (inputState[InputKey.LEFT] == true) {
            x -= cos(radYaw - PI / 2) * MOVEMENT_SPEED
            y -= sin(radYaw - PI / 2) * MOVEMENT_SPEED
        }
        if (inputState[InputKey.RIGHT] == true) {
            x += cos(radYaw - PI / 2) * MOVEMENT_SPEED
            y += sin(radYaw - PI / 2) * MOVEMENT_SPEED
        }
        if (inputState[InputKey.UP] == true) {
            z += VERTICAL_SPEED
        }
        if (inputState[InputKey.DOWN] == true) {
            z -= VERTICAL_SPEED
        }
    }

    /**
     * Set input key state (true = pressed, false = released)
     */
    fun setInputKey(key: InputKey, pressed: Boolean) {
        inputState[key] = pressed
    }

    /**
     * Update camera rotation based on mouse movement
     */
    fun rotate(deltaX: Double, deltaY: Double) {
        yaw -= deltaX * MOUSE_SENSITIVITY
        pitch -= deltaY * MOUSE_SENSITIVITY
        pitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH)

        // Normalize yaw to [0, 360)
        yaw = ((yaw % 360.0) + 360.0) % 360.0
    }

    /**
     * Get forward vector based on current orientation
     */
    fun getForwardVector(): Triple<Double, Double, Double> {
        val radYaw = Math.toRadians(yaw)
        val radPitch = Math.toRadians(pitch)

        val x = sin(radYaw) * cos(radPitch)
        val y = cos(radYaw) * cos(radPitch)
        val z = sin(radPitch)

        return Triple(x, y, z)
    }

    /**
     * Get right vector based on current orientation
     */
    fun getRightVector(): Triple<Double, Double, Double> {
        val radYaw = Math.toRadians(yaw + 90.0)
        val x = sin(radYaw)
        val y = cos(radYaw)
        return Triple(x, y, 0.0)
    }
}
