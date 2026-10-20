package com.codblackops.mobile.core.input;

/** Helpers for analog stick values in the unit circle (x right, y up). */
public final class StickMath {
    private StickMath() {
    }

    /**
     * Radial deadzone with rescaling so the output still reaches 1.0 at full deflection.
     *
     * @return {x, y}
     */
    public static float[] radialDeadzone(float x, float y, float deadzone) {
        float m = (float) Math.hypot(x, y);
        if (m <= deadzone || m == 0f) return new float[]{0f, 0f};
        float clamped = Math.min(1f, m);
        float scaled = (clamped - deadzone) / (1f - deadzone);
        return new float[]{x / m * scaled, y / m * scaled};
    }

    /** Clamps a vector to the unit circle. */
    public static float[] clampUnit(float x, float y) {
        float m = (float) Math.hypot(x, y);
        if (m <= 1f) return new float[]{x, y};
        return new float[]{x / m, y / m};
    }

    /**
     * Converts a touch offset from the stick centre (screen pixels, y down) to a stick vector
     * (y up) normalised by the stick radius.
     */
    public static float[] touchToStick(float dxPx, float dyPx, float radiusPx) {
        if (radiusPx <= 0f) return new float[]{0f, 0f};
        return clampUnit(dxPx / radiusPx, -dyPx / radiusPx);
    }

    /** Angle in [0, 2π) as expected by Dolphin's InputOverrider.getGateRadiusAtAngle(). */
    public static double gateAngle(float x, float y) {
        double a = Math.atan2(y, x);
        if (a < 0) a += 2 * Math.PI;
        return a;
    }
}
