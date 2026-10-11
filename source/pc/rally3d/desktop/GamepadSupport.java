package rally3d.desktop;

import java.util.HashSet;
import java.util.Set;
import java.nio.ByteBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/** GLFW's standardized gamepad layout covers common Xbox and PlayStation pads. */
final class GamepadSupport implements Runnable {
    private static final int[] BUTTON_CODES = {
        -5, // South: Xbox A / PlayStation Cross (confirm and lobby ready)
        -7, // East: Xbox B / PlayStation Circle (back)
        -5, // West: Xbox X / PlayStation Square (alternate confirm)
        -7, // North: Xbox Y / PlayStation Triangle (alternate back)
        -3, // left bumper: previous menu item
        -4, // right bumper: next menu item
        -7, // Back / Share
        -5, // Start / Options
        0, // Guide / PS button: reserved by the operating system
        -11, // left stick click
        -13, // right stick click: scoreboard (Tab)
        -1, -2, -3, -4 // D-pad up/down/left/right
    };

    private final Set<Integer> downButtons = new HashSet<Integer>();
    private final java.util.Map<Integer, Integer> activeKeySources = new java.util.HashMap<Integer, Integer>();
    private final Set<Integer> warnedUnmapped = new HashSet<Integer>();
    private int activeJoystick = -1;
    private String activeName = "";
    private boolean loggedFirstInput;

    static void runOnCurrentThread() {
        if (Platform.headless) return;
        new GamepadSupport().run();
    }

    @Override public void run() {
        try {
            if (!GLFW.glfwInit()) {
                LogConsole.info("gamepad", "GLFW gamepad input could not initialize; keyboard and mouse remain available");
                return;
            }
            LogConsole.info("gamepad", "Automatic Xbox and PlayStation controller detection is active on the GLFW main thread");
            try (GLFWGamepadState state = GLFWGamepadState.malloc()) {
                while (!Thread.currentThread().isInterrupted()) {
                    GLFW.glfwPollEvents();
                    poll(state);
                    try { Thread.sleep(8L); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            }
        } catch (LinkageError | RuntimeException error) {
            Platform.updateGamepad(false, 0, 0, 0, 0, 0, false, false);
            releaseAll();
            LogConsole.warning("gamepad", "Controller input unavailable; keyboard and mouse remain available", error);
        }
    }

    private void poll(GLFWGamepadState state) {
        int selected = -1;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            if (GLFW.glfwJoystickIsGamepad(jid)) { selected = jid; break; }
            if (warnedUnmapped.add(jid)) {
                String name = GLFW.glfwGetJoystickName(jid);
                LogConsole.warning("gamepad", "Connected joystick has no standard GLFW mapping and cannot be auto-bound: " + name, null);
            }
        }
        if (selected < 0 || !GLFW.glfwGetGamepadState(selected, state)) {
            if (activeJoystick >= 0) {
                LogConsole.info("gamepad", "Controller disconnected: " + activeName);
                activeJoystick = -1;
                activeName = "";
                releaseAll();
            }
            Platform.updateGamepad(false, 0, 0, 0, 0, 0, false, false);
            return;
        }
        if (activeJoystick != selected) {
            releaseAll();
            activeJoystick = selected;
            activeName = GLFW.glfwGetGamepadName(selected);
            LogConsole.info("gamepad", "Detected controller: " + activeName + " (standard Xbox-style layout)");
            ByteBuffer rawButtons = GLFW.glfwGetJoystickButtons(selected);
            ByteBuffer rawHats = GLFW.glfwGetJoystickHats(selected);
            LogConsole.info("gamepad", "Raw input available: " + (rawButtons == null ? 0 : rawButtons.remaining())
                    + " buttons, " + (rawHats == null ? 0 : rawHats.remaining()) + " hats");
        }

        for (int button = 0; button < BUTTON_CODES.length; button++) {
            setButton(button, state.buttons(button) == GLFW.GLFW_PRESS);
        }
        pollRawHat(selected);
        if (isPlayStationPad(activeName)) pollPlayStationButtons(selected);
        float x = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        float y = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        setControl(100, -3, x < -0.50f);
        setControl(101, -4, x > 0.50f);
        setControl(102, -1, y < -0.50f);
        setControl(103, -2, y > 0.50f);
        float steer = Math.abs(x) < 0.14f ? 0.0f : Math.max(-1.0f, Math.min(1.0f, x));
        float throttle = trigger(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER));
        float brake = trigger(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER));
        float cameraX = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X);
        float cameraY = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
        if (Math.abs(cameraX) < 0.18f) cameraX = 0;
        if (Math.abs(cameraY) < 0.18f) cameraY = 0;
        Platform.updateGamepad(true, steer, throttle, brake, cameraX, cameraY, true,
                state.buttons(GLFW.GLFW_GAMEPAD_BUTTON_A) == GLFW.GLFW_PRESS);
    }

    /** Some Windows PS4/PS5 Bluetooth drivers expose the D-pad as a POV hat. */
    private void pollRawHat(int jid) {
        ByteBuffer hats = GLFW.glfwGetJoystickHats(jid);
        int hat = hats == null || !hats.hasRemaining() ? GLFW.GLFW_HAT_CENTERED : hats.get(hats.position()) & 0xFF;
        setControl(220, -1, (hat & GLFW.GLFW_HAT_UP) != 0);
        setControl(221, -2, (hat & GLFW.GLFW_HAT_DOWN) != 0);
        setControl(222, -3, (hat & GLFW.GLFW_HAT_LEFT) != 0);
        setControl(223, -4, (hat & GLFW.GLFW_HAT_RIGHT) != 0);
    }

    /** Direct HID-button fallback for DualShock/DualSense drivers with incomplete GLFW maps. */
    private void pollPlayStationButtons(int jid) {
        ByteBuffer buttons = GLFW.glfwGetJoystickButtons(jid);
        if (buttons == null) return;
        int start = buttons.position();
        int limit = buttons.limit();
        for (int button = 0; button < 12; button++) {
            boolean down = start + button < limit && buttons.get(start + button) == GLFW.GLFW_PRESS;
            int keyCode;
            switch (button) {
                case 0: case 1: returnButtonCode[button] = -5; break; // Square / Cross
                case 2: case 3: returnButtonCode[button] = -7; break; // Circle / Triangle
                case 4: returnButtonCode[button] = -3; break; // L1
                case 5: returnButtonCode[button] = -4; break; // R1
                case 8: returnButtonCode[button] = -7; break; // Share
                case 9: returnButtonCode[button] = -5; break; // Options
                case 10: returnButtonCode[button] = -11; break; // L3
                case 11: returnButtonCode[button] = -13; break; // R3
                default: returnButtonCode[button] = 0; break;
            }
            keyCode = returnButtonCode[button];
            setControl(240 + button, keyCode, down);
        }
    }

    private final int[] returnButtonCode = new int[12];

    private static boolean isPlayStationPad(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("ps4") || lower.contains("ps5") || lower.contains("dualshock")
                || lower.contains("dualsense") || lower.contains("wireless controller");
    }

    /** GLFW exposes standardized trigger rest as -1 and full pull as +1. */
    private static float trigger(float axis) {
        float value = (axis + 1.0f) * 0.5f;
        return value < 0.06f ? 0.0f : Math.min(1.0f, value);
    }

    private void setButton(int button, boolean down) {
        setControl(button, BUTTON_CODES[button], down);
    }

    private void setControl(int source, int keyCode, boolean down) {
        boolean wasDown = downButtons.contains(source);
        if (down == wasDown) return;
        if (down) downButtons.add(source); else downButtons.remove(source);
        int count = activeKeySources.containsKey(keyCode) ? activeKeySources.get(keyCode).intValue() : 0;
        if (down) {
            activeKeySources.put(keyCode, Integer.valueOf(count + 1));
            if (count != 0) return;
        } else {
            if (count <= 1) activeKeySources.remove(keyCode);
            else { activeKeySources.put(keyCode, Integer.valueOf(count - 1)); return; }
        }
        GameWindow window = Platform.window;
        if (window != null) window.dispatchControllerKey(keyCode, down);
        if (down && keyCode != 0 && !loggedFirstInput) {
            loggedFirstInput = true;
            LogConsole.info("gamepad", "Controller input event received by game: " + keyName(keyCode));
        }
    }

    private static String keyName(int code) {
        switch (code) {
            case -1: return "D-pad/stick up";
            case -2: return "D-pad/stick down";
            case -3: return "D-pad/stick left";
            case -4: return "D-pad/stick right";
            case -5: return "confirm";
            case -7: return "back";
            case -11: return "menu button";
            case -13: return "scoreboard";
            default: return "button " + code;
        }
    }

    private void releaseAll() {
        Integer[] buttons = downButtons.toArray(new Integer[downButtons.size()]);
        for (Integer button : buttons) {
            int source = button.intValue();
            int keyCode = source >= 0 && source < BUTTON_CODES.length ? BUTTON_CODES[source]
                    : source == 100 ? -3 : source == 101 ? -4 : source == 102 ? -1 : source == 103 ? -2 : 0;
            setControl(source, keyCode, false);
        }
    }
}
