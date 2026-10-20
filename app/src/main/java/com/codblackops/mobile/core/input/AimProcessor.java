package com.codblackops.mobile.core.input;

/**
 * Turns finger movement (swipe-to-look) into a right-stick deflection for the Classic Controller.
 *
 * The game turns the camera at a speed proportional to the stick, so the finger's velocity is
 * mapped to stick deflection: fast swipe = fast turn, finger at rest = no turn. Deadzone,
 * acceleration, anti-deadzone (to overcome the game's own stick deadzone), smoothing and
 * invert-Y are applied in that order.
 */
public final class AimProcessor {
    /** Finger speed (dp/s) that produces full deflection at sensitivity 1.0. */
    public static final float FULL_SPEED_DP_PER_S = 1400f;

    private float sensX = 1f, sensY = 1f, acceleration = 0f, deadzone = 0f, antiDeadzone = 0f, smoothing = 0f;
    private boolean invertY;
    private float outX, outY;

    public void configure(float sensX, float sensY, float acceleration, float deadzone,
                          float antiDeadzone, float smoothing, boolean invertY) {
        this.sensX = sensX;
        this.sensY = sensY;
        this.acceleration = Math.max(0f, acceleration);
        this.deadzone = Math.max(0f, Math.min(0.9f, deadzone));
        this.antiDeadzone = Math.max(0f, Math.min(0.9f, antiDeadzone));
        this.smoothing = Math.max(0f, Math.min(0.95f, smoothing));
        this.invertY = invertY;
    }

    public void configure(ControlSettings s) {
        configure(s.lookSensX, s.lookSensY, s.lookAcceleration, s.lookDeadzone, s.lookAntiDeadzone,
                s.lookSmoothing, s.invertY);
    }

    /**
     * @param dxDp finger movement to the right since the previous frame, in dp
     * @param dyDp finger movement downwards since the previous frame, in dp
     * @param dtSec frame time in seconds
     */
    public void update(float dxDp, float dyDp, float dtSec) {
        if (!(dtSec > 0f) || dtSec > 0.1f) dtSec = 1f / 60f;
        float rawX = dxDp / dtSec * sensX / FULL_SPEED_DP_PER_S;
        float rawY = -dyDp / dtSec * sensY / FULL_SPEED_DP_PER_S; // screen down -> stick down
        if (invertY) rawY = -rawY;
        float[] shaped = shape(rawX, rawY);
        outX = outX * smoothing + shaped[0] * (1f - smoothing);
        outY = outY * smoothing + shaped[1] * (1f - smoothing);
        if (Math.abs(outX) < 1e-3f) outX = 0f;
        if (Math.abs(outY) < 1e-3f) outY = 0f;
    }

    float[] shape(float x, float y) {
        float m = (float) Math.hypot(x, y);
        if (m <= deadzone || m == 0f) return new float[]{0f, 0f};
        float v = (Math.min(m, 4f) - deadzone) / (1f - deadzone);
        v = v * (1f + acceleration * Math.min(v, 1f));
        v = Math.min(1f, v);
        v = antiDeadzone + (1f - antiDeadzone) * v;
        v = Math.min(1f, v);
        return new float[]{x / m * v, y / m * v};
    }

    public void reset() {
        outX = 0f;
        outY = 0f;
    }

    public float x() {
        return outX;
    }

    public float y() {
        return outY;
    }
}
