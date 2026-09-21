package dev.aster.render;

import dev.aster.camera.Camera;

/**
 * View frustum for culling triangles outside the visible area.
 */
public final class ViewFrustum {

    public final double fov;
    public final double aspectRatio;
    public final double nearPlane;
    public final double farPlane;

    private double left = 0.0;
    private double right = 0.0;
    private double top = 0.0;
    private double bottom = 0.0;

    public ViewFrustum() {
        this(90.0, 800.0 / 600.0, 0.1, 1000.0);
    }

    public ViewFrustum(double fov, double aspectRatio, double nearPlane, double farPlane) {
        this.fov = fov;
        this.aspectRatio = aspectRatio;
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
        updateFrustumPlanes();
    }

    private void updateFrustumPlanes() {
        double height = Math.tan(Math.toRadians(fov / 2.0)) * nearPlane;
        top = height;
        bottom = -height;
        left = -aspectRatio * height;
        right = aspectRatio * height;
    }

    /**
     * Check if a point is inside the frustum (simplified check for near plane).
     */
    public boolean isPointInFrustum(double x, double y, double z) {
        // Very basic check: if point is behind camera or too far, cull it
        return z > nearPlane && z < farPlane;
    }

    /** Check if a triangle is potentially visible. */
    public boolean isTriangleVisible(Camera.Vector3 p1, Camera.Vector3 p2, Camera.Vector3 p3) {
        return isPointInFrustum(p1.x(), p1.y(), p1.z())
                || isPointInFrustum(p2.x(), p2.y(), p2.z())
                || isPointInFrustum(p3.x(), p3.y(), p3.z());
    }
}
