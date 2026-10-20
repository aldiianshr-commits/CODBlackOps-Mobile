package com.codblackops.mobile.core.input;

/**
 * Converts Android gyroscope samples (rad/s, device natural axes) into screen-relative aim rates.
 *
 * Output: yaw rate (positive = turn right) and pitch rate (positive = aim up) in rad/s, with the
 * display rotation, calibration bias, deadzone, sensitivity and inversion applied.
 */
public final class GyroProcessor {
    /** Stick deflection produced by 1 rad/s at sensitivity 1.0 (≈ 2 rad/s for full turn speed). */
    public static final float STICK_PER_RAD_S = 0.5f;
    /** Pointer units moved per radian at sensitivity 1.0 (1.0 = centre to edge). */
    public static final float POINTER_PER_RAD = 1.6f;

    private float biasX, biasY, biasZ;
    private float sensX = 1f, sensY = 1f, deadzone = 0f;
    private boolean invertX, invertY;
    private ControlSettings.GyroAxis axis = ControlSettings.GyroAxis.YAW;

    // calibration accumulators
    private double sumX, sumY, sumZ;
    private int samples;

    public void configure(ControlSettings s) {
        biasX = s.gyroBiasX;
        biasY = s.gyroBiasY;
        biasZ = s.gyroBiasZ;
        sensX = s.gyroSensX;
        sensY = s.gyroSensY;
        deadzone = s.gyroDeadzone;
        invertX = s.gyroInvertX;
        invertY = s.gyroInvertY;
        axis = s.gyroAxis;
    }

    /**
     * Maps a device-axes vector to screen axes for the given Display rotation
     * (0..3 = ROTATION_0/90/180/270). Returns {screenX, screenY, z}.
     */
    public static float[] toScreenAxes(float x, float y, float z, int rotation) {
        switch (rotation & 3) {
            case 1: return new float[]{-y, x, z};
            case 2: return new float[]{-x, -y, z};
            case 3: return new float[]{y, -x, z};
            default: return new float[]{x, y, z};
        }
    }

    /** @return {yawRate (right +), pitchRate (up +)} in rad/s after all shaping. */
    public float[] process(float wx, float wy, float wz, int rotation) {
        float[] s = toScreenAxes(wx - biasX, wy - biasY, wz - biasZ, rotation);
        float yaw = axis == ControlSettings.GyroAxis.ROLL ? -s[2] : -s[1];
        float pitch = s[0];
        yaw = applyDeadzone(yaw) * sensX * (invertX ? -1f : 1f);
        pitch = applyDeadzone(pitch) * sensY * (invertY ? -1f : 1f);
        return new float[]{yaw, pitch};
    }

    private float applyDeadzone(float v) {
        if (Math.abs(v) <= deadzone) return 0f;
        return v - Math.signum(v) * deadzone;
    }

    // ---- calibration: keep the device still, feed raw samples, then read the bias
    public void beginCalibration() {
        sumX = sumY = sumZ = 0;
        samples = 0;
    }

    public void addCalibrationSample(float wx, float wy, float wz) {
        sumX += wx;
        sumY += wy;
        sumZ += wz;
        samples++;
    }

    public int calibrationSampleCount() {
        return samples;
    }

    /** @return {biasX, biasY, biasZ}, or null if not enough samples were collected. */
    public float[] finishCalibration() {
        if (samples < 20) return null;
        return new float[]{(float) (sumX / samples), (float) (sumY / samples), (float) (sumZ / samples)};
    }
}
