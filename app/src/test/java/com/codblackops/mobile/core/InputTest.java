package com.codblackops.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codblackops.mobile.core.input.ActionStateMachine;
import com.codblackops.mobile.core.input.AimProcessor;
import com.codblackops.mobile.core.input.Bindings;
import com.codblackops.mobile.core.input.ControlLayout;
import com.codblackops.mobile.core.input.ControlScheme;
import com.codblackops.mobile.core.input.ControlSettings;
import com.codblackops.mobile.core.input.GameAction;
import com.codblackops.mobile.core.input.GamepadMapping;
import com.codblackops.mobile.core.input.GyroProcessor;
import com.codblackops.mobile.core.input.HudElementId;
import com.codblackops.mobile.core.input.PointerState;
import com.codblackops.mobile.core.input.StickMath;
import com.codblackops.mobile.core.input.WiiControl;
import com.codblackops.mobile.core.input.WiiInput;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class InputTest {
    static final class RecordingSink implements ActionStateMachine.ControlSink {
        final double[] state = new double[WiiControl.CONTROL_COUNT];
        int writes;

        @Override
        public void setControl(int control, double value) {
            state[control] = value;
            writes++;
        }
    }

    static ActionStateMachine machine(RecordingSink sink, ControlScheme scheme, ControlSettings s) {
        ActionStateMachine m = new ActionStateMachine(sink);
        m.configure(scheme, Bindings.defaults(), s, 0);
        return m;
    }

    @Test
    public void holdButtonFollowsFingerWithMinimumPress() {
        RecordingSink sink = new RecordingSink();
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, ControlSettings.defaults());
        m.press(GameAction.FIRE, 1, true, 1000);
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_ZR_BUTTON], 0);
        m.press(GameAction.FIRE, 1, false, 1010); // very quick tap
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_ZR_BUTTON], 0);
        m.tick(1000 + ActionStateMachine.MIN_PRESS_MS);
        assertEquals(0.0, sink.state[WiiControl.CLASSIC_ZR_BUTTON], 0);
    }

    @Test
    public void tapFireIsAPulse() {
        RecordingSink sink = new RecordingSink();
        ControlSettings s = ControlSettings.defaults();
        s.fireMode = ControlSettings.FireMode.TAP;
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, s);
        m.press(GameAction.FIRE, 1, true, 0);
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_ZR_BUTTON], 0);
        m.tick(ActionStateMachine.PULSE_MS + 1); // still holding finger, pulse ends anyway
        assertEquals(0.0, sink.state[WiiControl.CLASSIC_ZR_BUTTON], 0);
    }

    @Test
    public void sprintToggleLatchesAndUnlatches() {
        RecordingSink sink = new RecordingSink();
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, ControlSettings.defaults());
        m.press(GameAction.SPRINT, 1, true, 0);
        m.press(GameAction.SPRINT, 1, false, 50);
        m.tick(500);
        assertTrue(m.isLatched(GameAction.SPRINT));
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_DPAD_UP], 0);
        m.unlatch(GameAction.SPRINT, 600);
        assertEquals(0.0, sink.state[WiiControl.CLASSIC_DPAD_UP], 0);
        // second tap toggles again
        m.press(GameAction.SPRINT, 2, true, 700);
        assertTrue(m.isLatched(GameAction.SPRINT));
        m.press(GameAction.SPRINT, 2, false, 720);
        m.press(GameAction.SPRINT, 2, true, 800);
        assertFalse(m.isLatched(GameAction.SPRINT));
    }

    @Test
    public void interactIsHeldLongEnough() {
        RecordingSink sink = new RecordingSink();
        ControlSettings s = ControlSettings.defaults();
        s.interactHoldMs = 650;
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, s);
        m.press(GameAction.INTERACT, 1, true, 0);
        m.press(GameAction.INTERACT, 1, false, 30);
        m.tick(600);
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_Y_BUTTON], 0);
        m.tick(651);
        assertEquals(0.0, sink.state[WiiControl.CLASSIC_Y_BUTTON], 0);
    }

    @Test
    public void sharedControlStaysPressedWhileAnyActionActive() {
        RecordingSink sink = new RecordingSink();
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, ControlSettings.defaults());
        m.press(GameAction.RELOAD, 1, true, 0);
        m.press(GameAction.INTERACT, 2, true, 10);
        m.press(GameAction.RELOAD, 1, false, 100);
        m.tick(200);
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_Y_BUTTON], 0); // interact still held
        m.press(GameAction.INTERACT, 2, false, 1000);
        m.tick(1000);
        assertEquals(0.0, sink.state[WiiControl.CLASSIC_Y_BUTTON], 0);
    }

    @Test
    public void classicShoulderSetsDigitalAndAnalog() {
        RecordingSink sink = new RecordingSink();
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, ControlSettings.defaults());
        m.press(GameAction.LETHAL, 1, true, 0);
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_R_DIGITAL], 0);
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_R_ANALOG], 0);
    }

    @Test
    public void wiimoteReloadShakesNunchukForAFixedTime() {
        RecordingSink sink = new RecordingSink();
        ActionStateMachine m = machine(sink, ControlScheme.WIIMOTE_NUNCHUK, ControlSettings.defaults());
        m.press(GameAction.RELOAD, 1, true, 0);
        assertEquals(1.0, sink.state[WiiControl.NUNCHUK_SHAKE_X], 0);
        assertEquals(1.0, sink.state[WiiControl.NUNCHUK_SHAKE_Y], 0);
        assertEquals(1.0, sink.state[WiiControl.NUNCHUK_SHAKE_Z], 0);
        m.tick(ActionStateMachine.SHAKE_MS + 1);
        assertEquals(0.0, sink.state[WiiControl.NUNCHUK_SHAKE_X], 0);
        // classic controls are never touched in Wii Remote mode
        assertEquals(0.0, sink.state[WiiControl.CLASSIC_ZR_BUTTON], 0);
    }

    @Test
    public void multipleSourcesAndRelease() {
        RecordingSink sink = new RecordingSink();
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, ControlSettings.defaults());
        m.press(GameAction.ADS, 1, true, 0);
        m.press(GameAction.ADS, 99, true, 0);
        m.releaseSource(1, 500);
        assertEquals(1.0, sink.state[WiiControl.CLASSIC_ZL_BUTTON], 0);
        m.releaseSource(99, 600);
        assertEquals(0.0, sink.state[WiiControl.CLASSIC_ZL_BUTTON], 0);
    }

    @Test
    public void launcherActionsGoToListenerOnly() {
        RecordingSink sink = new RecordingSink();
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, ControlSettings.defaults());
        List<GameAction> got = new ArrayList<>();
        m.setLauncherActionListener(got::add);
        int before = sink.writes;
        m.press(GameAction.EMULATOR_MENU, 1, true, 0);
        m.press(GameAction.EMULATOR_MENU, 1, false, 10);
        assertEquals(1, got.size());
        assertEquals(before, sink.writes);
    }

    @Test
    public void onlyChangesAreWritten() {
        RecordingSink sink = new RecordingSink();
        ActionStateMachine m = machine(sink, ControlScheme.CLASSIC, ControlSettings.defaults());
        int before = sink.writes;
        m.tick(10);
        m.tick(20);
        assertEquals(before, sink.writes);
    }

    @Test
    public void bindingsDefaultsAreValidAndRoundTrip() {
        Bindings b = Bindings.defaults();
        for (GameAction a : GameAction.values()) {
            if (a.isLauncherAction()) {
                assertEquals(WiiInput.NONE, b.get(ControlScheme.CLASSIC, a));
                continue;
            }
            assertTrue(a + " classic", b.get(ControlScheme.CLASSIC, a).availableIn(ControlScheme.CLASSIC));
            assertTrue(a + " wiimote", b.get(ControlScheme.WIIMOTE_NUNCHUK, a).availableIn(ControlScheme.WIIMOTE_NUNCHUK));
            assertTrue(a + " bound", b.get(ControlScheme.CLASSIC, a) != WiiInput.NONE);
        }
        b.set(ControlScheme.CLASSIC, GameAction.JUMP, WiiInput.CLASSIC_X);
        assertEquals(WiiInput.CLASSIC_X, Bindings.decode(b.encode()).get(ControlScheme.CLASSIC, GameAction.JUMP));
        try {
            b.set(ControlScheme.CLASSIC, GameAction.JUMP, WiiInput.NUNCHUK_C);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        b.resetScheme(ControlScheme.CLASSIC);
        assertEquals(WiiInput.CLASSIC_B, b.get(ControlScheme.CLASSIC, GameAction.JUMP));
    }

    @Test
    public void aimProcessor() {
        AimProcessor a = new AimProcessor();
        a.configure(1f, 1f, 0f, 0.05f, 0.1f, 0f, false);
        a.update(0, 0, 1 / 60f);
        assertEquals(0f, a.x(), 0f);
        // 30 dp to the right in one frame = 1800 dp/s -> saturates
        a.update(30, 0, 1 / 60f);
        assertEquals(1f, a.x(), 1e-4);
        assertEquals(0f, a.y(), 1e-4);
        // finger moving up -> stick up (positive)
        a.update(0, -10, 1 / 60f);
        assertTrue(a.y() > 0);
        a.configure(1f, 1f, 0f, 0.05f, 0.1f, 0f, true);
        a.update(0, -10, 1 / 60f);
        assertTrue(a.y() < 0);
        // tiny jitter below the deadzone is ignored
        a.configure(1f, 1f, 0f, 0.05f, 0.1f, 0f, false);
        a.update(0.5f, 0, 1 / 60f);
        assertEquals(0f, a.x(), 0f);
        // just above the deadzone, anti-deadzone gives a usable minimum
        a.update(1.5f, 0, 1 / 60f);
        assertTrue(a.x() >= 0.1f);
        // nonsense frame time does not explode
        a.update(5, 0, -1f);
        assertTrue(a.x() <= 1f);
    }

    @Test
    public void stickMath() {
        float[] v = StickMath.radialDeadzone(0.05f, 0f, 0.1f);
        assertEquals(0f, v[0], 0f);
        v = StickMath.radialDeadzone(1f, 0f, 0.1f);
        assertEquals(1f, v[0], 1e-5);
        v = StickMath.touchToStick(0, -200, 100); // finger above centre, beyond radius
        assertEquals(1f, v[1], 1e-5);
        assertEquals(0f, v[0], 1e-5);
        assertEquals(Math.PI / 2, StickMath.gateAngle(0, 1), 1e-9);
        assertEquals(3 * Math.PI / 2, StickMath.gateAngle(0, -1), 1e-9);
    }

    @Test
    public void pointer() {
        PointerState p = new PointerState();
        p.setAbsolute(0, 0, 1000, 500);
        assertEquals(-1f, p.x(), 1e-6);
        assertEquals(1f, p.y(), 1e-6);
        p.setAbsolute(500, 250, 1000, 500);
        assertEquals(0f, p.x(), 1e-6);
        assertEquals(0f, p.y(), 1e-6);
        p.addRelative(PointerState.RELATIVE_DP_TO_EDGE_X * 3, 0, 1f);
        assertEquals(1f, p.x(), 1e-6);
        p.recenter();
        p.addVelocity(0.5f, -0.5f, 1f);
        assertEquals(0.5f, p.x(), 1e-6);
        assertEquals(-0.5f, p.y(), 1e-6);
    }

    @Test
    public void gyroAxesAndCalibration() {
        // landscape (ROTATION_90): device +x maps to screen +y
        float[] s = GyroProcessor.toScreenAxes(1, 2, 3, 1);
        assertEquals(-2f, s[0], 0f);
        assertEquals(1f, s[1], 0f);
        assertEquals(3f, s[2], 0f);
        s = GyroProcessor.toScreenAxes(1, 2, 3, 3);
        assertEquals(2f, s[0], 0f);
        assertEquals(-1f, s[1], 0f);

        ControlSettings cs = ControlSettings.defaults();
        cs.gyroDeadzone = 0f;
        GyroProcessor g = new GyroProcessor();
        g.configure(cs);
        // rotation about screen-up axis (negative = turning right) in portrait
        float[] r = g.process(0, -1f, 0, 0);
        assertEquals(1f, r[0], 1e-6);
        // tilting the top towards the user = aim up
        r = g.process(1f, 0, 0, 0);
        assertEquals(1f, r[1], 1e-6);

        g.beginCalibration();
        for (int i = 0; i < 50; i++) g.addCalibrationSample(0.01f, -0.02f, 0.005f);
        float[] bias = g.finishCalibration();
        assertNotNull(bias);
        assertEquals(0.01f, bias[0], 1e-6);
        cs.gyroBiasX = bias[0];
        cs.gyroBiasY = bias[1];
        cs.gyroBiasZ = bias[2];
        cs.gyroDeadzone = 0.001f;
        g.configure(cs);
        r = g.process(0.01f, -0.02f, 0.005f, 0);
        assertEquals(0f, r[0], 1e-6);
        assertEquals(0f, r[1], 1e-6);

        g.beginCalibration();
        assertNull(g.finishCalibration());
    }

    /** Visible elements of the presets must not overlap on the target screen sizes. */
    @Test
    public void presetLayoutsDoNotOverlap() {
        checkNoOverlap(ControlLayout.Preset.PHONE, 800, 360);
        checkNoOverlap(ControlLayout.Preset.XIAOMI_PAD_6, 1440, 900);
    }

    private void checkNoOverlap(ControlLayout.Preset preset, float wDp, float hDp) {
        for (ControlSettings.LayoutSet set : ControlSettings.LayoutSet.values()) {
            ControlLayout l = ControlLayout.preset(preset, set);
            HudElementId[] ids = HudElementId.values();
            for (int i = 0; i < ids.length; i++) {
                ControlLayout.Element a = l.get(ids[i]);
                if (!a.visible) continue;
                // fully on screen
                assertTrue(preset + " " + ids[i] + " left", a.cx * wDp - a.sizeDp / 2 >= -1);
                assertTrue(preset + " " + ids[i] + " right", a.cx * wDp + a.sizeDp / 2 <= wDp + 1);
                assertTrue(preset + " " + ids[i] + " top", a.cy * hDp - a.sizeDp / 2 >= -1);
                assertTrue(preset + " " + ids[i] + " bottom", a.cy * hDp + a.sizeDp / 2 <= hDp + 1);
                for (int j = i + 1; j < ids.length; j++) {
                    ControlLayout.Element b = l.get(ids[j]);
                    if (!b.visible) continue;
                    double d = Math.hypot((a.cx - b.cx) * wDp, (a.cy - b.cy) * hDp);
                    assertTrue(preset + " " + set + ": " + ids[i] + " overlaps " + ids[j] + " (" + d + ")",
                            d >= (a.sizeDp + b.sizeDp) / 2 - 2);
                }
            }
        }
    }

    @Test
    public void layoutRoundTrip() {
        ControlLayout l = ControlLayout.preset(ControlLayout.Preset.PHONE, ControlSettings.LayoutSet.CAMPAIGN);
        l.set(HudElementId.FIRE, 0.5f, 0.5f, 500f, 2f, false); // clamped
        ControlLayout d = ControlLayout.decode(l.encode(), ControlLayout.preset(ControlLayout.Preset.XIAOMI_PAD_6,
                ControlSettings.LayoutSet.CAMPAIGN));
        assertEquals(ControlLayout.MAX_SIZE_DP, d.get(HudElementId.FIRE).sizeDp, 0.01);
        assertEquals(1f, d.get(HudElementId.FIRE).opacity, 0.001);
        assertFalse(d.get(HudElementId.FIRE).visible);
        assertEquals(l.encode(), d.encode());
        // garbage falls back
        ControlLayout g = ControlLayout.decode("FIRE=abc\nNOPE=1,2,3,4,true", l);
        assertEquals(l.encode(), g.encode());
    }

    @Test
    public void controlSettingsRoundTripAndScheme() {
        ControlSettings s = ControlSettings.defaults();
        assertEquals(ControlScheme.CLASSIC, s.activeScheme());
        s.mode = ControlSettings.Mode.WII_POINTER;
        assertEquals(ControlScheme.WIIMOTE_NUNCHUK, s.activeScheme());
        s.mode = ControlSettings.Mode.GYROSCOPE;
        s.gyroScheme = ControlScheme.CLASSIC;
        assertEquals(ControlScheme.CLASSIC, s.activeScheme());
        assertTrue(s.gyroActive());
        s.lookSensX = 99f; // clamped on decode
        ControlSettings d = ControlSettings.decode(s.encode());
        assertEquals(4f, d.lookSensX, 0f);
        assertEquals(ControlSettings.Mode.GYROSCOPE, d.mode);
    }

    @Test
    public void gamepadMapping() {
        GamepadMapping g = GamepadMapping.defaults();
        assertEquals(GameAction.FIRE, g.actionFor(GamepadMapping.KEYCODE_BUTTON_R2));
        assertNull(g.actionFor(12345));
        g.unbindAction(GameAction.JUMP);
        g.bind(GamepadMapping.KEYCODE_BUTTON_Y, GameAction.JUMP);
        assertEquals(GamepadMapping.KEYCODE_BUTTON_Y, g.keyFor(GameAction.JUMP));
        assertEquals(GameAction.JUMP, GamepadMapping.decode(g.encode()).actionFor(GamepadMapping.KEYCODE_BUTTON_Y));
        assertEquals(-1, g.keyFor(GameAction.RECENTER_AIM));
    }
}
