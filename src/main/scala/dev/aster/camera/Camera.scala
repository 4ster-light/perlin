package dev.aster.camera

object Camera {

  enum InputKey:
    case FORWARD, BACKWARD, LEFT, RIGHT, UP, DOWN

  final case class Vector3(x: Double, y: Double, z: Double)
}

/** First-person camera system with WASD movement and mouse look. */
final class Camera(
    var x: Double = 100.0,
    var y: Double = 100.0,
    var z: Double = 50.0,
    var yaw: Double = 0.0,
    var pitch: Double = 0.0
) {

  import Camera.InputKey

  private val MIN_PITCH = -89.0
  private val MAX_PITCH = 89.0
  private val MOVEMENT_SPEED = 1.2
  private val VERTICAL_SPEED = 0.8
  private val MOUSE_SENSITIVITY = 0.15

  private var forwardHeld = false
  private var backwardHeld = false
  private var leftHeld = false
  private var rightHeld = false
  private var upHeld = false
  private var downHeld = false

  /** Update camera position based on input state.
    *
    * Movement follows the forward vector (sin yaw, cos yaw) and the right vector (cos yaw, -sin
    * yaw), so WASD always moves relative to where the camera is looking (yaw only, pitch is ignored
    * for movement).
    */
  def update(): Unit = {
    val radYaw = Math.toRadians(yaw)
    val forwardX = Math.sin(radYaw)
    val forwardY = Math.cos(radYaw)
    val rightX = Math.cos(radYaw)
    val rightY = -Math.sin(radYaw)

    if (forwardHeld) {
      x += forwardX * MOVEMENT_SPEED
      y += forwardY * MOVEMENT_SPEED
    }
    if (backwardHeld) {
      x -= forwardX * MOVEMENT_SPEED
      y -= forwardY * MOVEMENT_SPEED
    }
    if (leftHeld) {
      x -= rightX * MOVEMENT_SPEED
      y -= rightY * MOVEMENT_SPEED
    }
    if (rightHeld) {
      x += rightX * MOVEMENT_SPEED
      y += rightY * MOVEMENT_SPEED
    }
    if (upHeld) {
      z += VERTICAL_SPEED
    }
    if (downHeld) {
      z -= VERTICAL_SPEED
    }
  }

  /** Set input key state (true = pressed, false = released). */
  def setInputKey(key: InputKey, pressed: Boolean): Unit = key match {
    case InputKey.FORWARD  => forwardHeld = pressed
    case InputKey.BACKWARD => backwardHeld = pressed
    case InputKey.LEFT     => leftHeld = pressed
    case InputKey.RIGHT    => rightHeld = pressed
    case InputKey.UP       => upHeld = pressed
    case InputKey.DOWN     => downHeld = pressed
  }

  /** Update camera rotation based on mouse movement.
    *
    * Yaw follows the demo's convention: yaw 0 looks along +Y and moving the mouse right increases
    * yaw (turning toward +X).
    */
  def rotate(deltaX: Double, deltaY: Double): Unit = {
    yaw += deltaX * MOUSE_SENSITIVITY
    pitch -= deltaY * MOUSE_SENSITIVITY
    pitch = Math.clamp(pitch, MIN_PITCH, MAX_PITCH)

    // Normalize yaw to [0, 360)
    yaw = ((yaw % 360.0) + 360.0) % 360.0
  }

  /** Get forward vector based on current orientation. */
  def getForwardVector: Camera.Vector3 = {
    val radYaw = Math.toRadians(yaw)
    val radPitch = Math.toRadians(pitch)

    Camera.Vector3(
      Math.sin(radYaw) * Math.cos(radPitch),
      Math.cos(radYaw) * Math.cos(radPitch),
      Math.sin(radPitch)
    )
  }

  /** Get right vector based on current orientation. */
  def getRightVector: Camera.Vector3 = {
    val radYaw = Math.toRadians(yaw + 90.0)
    Camera.Vector3(Math.sin(radYaw), Math.cos(radYaw), 0.0)
  }
}
