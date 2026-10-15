package rally3d.android;

import android.content.Context;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import javax.microedition.lcdui.Canvas;
import rally3d.core.RLog;
import rally3d.desktop.Platform;

/**
 * Game controller support, same behaviour as the desktop GamepadSupport: left stick = steering (and menu arrows), triggers = gas / brake,
 * right stick = camera, A / X = confirm (A also "ready"), B / Y / Select = back, bumpers = previous / next, L3 = -11, R3 = scoreboard.
 * All calls come from the UI thread.
 */
final class Gamepad implements InputManager.InputDeviceListener {
    private static final Gamepad INSTANCE = new Gamepad();
    private InputManager im;
    private boolean connected;
    private final int[] dirMask = new int[4];   // up, down, left, right: bit 1 = d-pad key, 2 = hat axis, 4 = left stick
    private static final int[] DIR_KEYS = {-1, -2, -3, -4};

    static void start(Context c) {
        try {
            INSTANCE.im = (InputManager) c.getSystemService(Context.INPUT_SERVICE);
            if (INSTANCE.im != null) INSTANCE.im.registerInputDeviceListener(INSTANCE, null);
        } catch (Throwable t) { RLog.caught("Gamepad.start", t); }
        refresh();
    }

    static void stop() {
        try { if (INSTANCE.im != null) INSTANCE.im.unregisterInputDeviceListener(INSTANCE); } catch (Throwable ignored) { /* shutting down */ }
        INSTANCE.im = null;
    }

    @Override public void onInputDeviceAdded(int id) { refresh(); }
    @Override public void onInputDeviceRemoved(int id) { refresh(); }
    @Override public void onInputDeviceChanged(int id) { refresh(); }

    static boolean isPadSource(int src) {
        return (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    /** Re-scans the attached devices; updates the shared "controller connected" state. */
    static void refresh() {
        boolean any = false;
        try {
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice d = InputDevice.getDevice(id);
                if (d != null && !d.isVirtual() && isPadSource(d.getSources())) { any = true; break; }
            }
        } catch (Throwable t) { RLog.caught("Gamepad.refresh", t); }
        if (any != INSTANCE.connected) {
            INSTANCE.connected = any;
            RLog.i("gamepad", any ? "Controller connected; analog steering and trigger controls enabled" : "Controller disconnected");
            if (!any) { INSTANCE.releaseDirs(); Platform.updateGamepad(false, 0, 0, 0, 0, 0, false, false); }
        }
    }

    private void releaseDirs() { for (int i = 0; i < 4; i++) setDir(i, 7, false); }

    /** One direction can be held by the d-pad key, the hat axis and the stick at once: the menu key goes down / up only on the combined change. */
    private void setDir(int idx, int bit, boolean down) {
        boolean was = dirMask[idx] != 0;
        dirMask[idx] = down ? dirMask[idx] | bit : dirMask[idx] & ~bit;
        boolean now = dirMask[idx] != 0;
        if (now != was) { Canvas c = Platform.currentCanvas(); if (c != null) c.dispatchKey(DIR_KEYS[idx], now); }
    }

    /** D-pad keys (also the arrow keys of a keyboard): returns true when handled. */
    static boolean onDpadKey(int keyCode, boolean down) {
        int idx = keyCode == KeyEvent.KEYCODE_DPAD_UP ? 0 : keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? 2 : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 3 : -1;
        if (idx < 0) return false;
        INSTANCE.setDir(idx, 1, down);
        return true;
    }

    /** Pad buttons that have no key mapping of their own ("ready" state for the multiplayer lobby). */
    static void onButton(int keyCode, boolean down) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A) Platform.setControllerReady(down);
    }

    private static float axis(MotionEvent e, int a) { return e.getAxisValue(a); }

    private static float trigger(float v) { return v < 0.06f ? 0f : Math.min(1f, v); }

    /** Joystick motion: sticks, triggers, hat. Returns true when consumed. */
    static boolean onMotion(MotionEvent e) {
        if (!isPadSource(e.getSource()) || e.getAction() != MotionEvent.ACTION_MOVE) return false;
        if (!INSTANCE.connected) refresh();
        float x = axis(e, MotionEvent.AXIS_X), y = axis(e, MotionEvent.AXIS_Y);
        float steer = Math.abs(x) < 0.14f ? 0f : Math.max(-1f, Math.min(1f, x));
        float throttle = trigger(Math.max(axis(e, MotionEvent.AXIS_RTRIGGER), axis(e, MotionEvent.AXIS_GAS)));
        float brake = trigger(Math.max(axis(e, MotionEvent.AXIS_LTRIGGER), axis(e, MotionEvent.AXIS_BRAKE)));
        InputDevice d = e.getDevice();
        boolean zAxes = d != null && d.getMotionRange(MotionEvent.AXIS_Z, e.getSource()) != null;
        float cx = axis(e, zAxes ? MotionEvent.AXIS_Z : MotionEvent.AXIS_RX), cy = axis(e, zAxes ? MotionEvent.AXIS_RZ : MotionEvent.AXIS_RY);
        if (Math.abs(cx) < 0.18f) cx = 0;
        if (Math.abs(cy) < 0.18f) cy = 0;
        Platform.updateGamepad(true, steer, throttle, brake, cx, cy, true, Platform.controllerReadyDown());
        INSTANCE.setDir(2, 4, x < -0.50f);
        INSTANCE.setDir(3, 4, x > 0.50f);
        INSTANCE.setDir(0, 4, y < -0.50f);
        INSTANCE.setDir(1, 4, y > 0.50f);
        float hx = axis(e, MotionEvent.AXIS_HAT_X), hy = axis(e, MotionEvent.AXIS_HAT_Y);
        INSTANCE.setDir(2, 2, hx < -0.5f);
        INSTANCE.setDir(3, 2, hx > 0.5f);
        INSTANCE.setDir(0, 2, hy < -0.5f);
        INSTANCE.setDir(1, 2, hy > 0.5f);
        return true;
    }
}
