package com.codblackops.mobile.core.input;

/**
 * Emulated Wii Remote IR pointer position in Dolphin's override range: x right, y up, both
 * in [-1, 1] where (0, 0) is the centre of the screen.
 */
public final class PointerState {
    /** dp of finger travel that moves the pointer from centre to edge at sensitivity 1.0. */
    public static final float RELATIVE_DP_TO_EDGE_X = 420f;
    public static final float RELATIVE_DP_TO_EDGE_Y = 300f;

    private float x, y;

    public void setAbsolute(float touchX, float touchY, float viewWidth, float viewHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) return;
        x = clamp(touchX / viewWidth * 2f - 1f);
        y = clamp(-(touchY / viewHeight * 2f - 1f));
    }

    public void addRelative(float dxDp, float dyDp, float sens) {
        x = clamp(x + dxDp * sens / RELATIVE_DP_TO_EDGE_X);
        y = clamp(y - dyDp * sens / RELATIVE_DP_TO_EDGE_Y);
    }

    /** Moves the pointer by a velocity (units/second), e.g. from a gamepad stick or gyro. */
    public void addVelocity(float vx, float vy, float dtSec) {
        x = clamp(x + vx * dtSec);
        y = clamp(y + vy * dtSec);
    }

    public void recenter() {
        x = 0f;
        y = 0f;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }
}
