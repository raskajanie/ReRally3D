package javax.microedition.lcdui;

import java.util.ArrayDeque;

public abstract class Canvas extends Displayable {
    public static final int UP = 1, LEFT = 2, RIGHT = 5, DOWN = 6, FIRE = 8, GAME_A = 9, GAME_B = 10, GAME_C = 11, GAME_D = 12;
    public static final int KEY_NUM0 = 48, KEY_NUM1 = 49, KEY_NUM2 = 50, KEY_NUM3 = 51, KEY_NUM4 = 52,
            KEY_NUM5 = 53, KEY_NUM6 = 54, KEY_NUM7 = 55, KEY_NUM8 = 56, KEY_NUM9 = 57, KEY_STAR = 42, KEY_POUND = 35;

    protected Canvas() {}
    public static final int TEXT_BACKSPACE = -1;
    private volatile boolean textEntryMode;
    private int pointerX, pointerY, clickX, clickY, pointerFlags, pointerWheel;
    private final ArrayDeque<Integer> textInput = new ArrayDeque<Integer>();
    protected abstract void paint(Graphics g);
    public final void repaint() {}
    public final void serviceRepaints() {}
    public void setFullScreenMode(boolean mode) {}
    public boolean hasPointerEvents() { return true; }

    public int getGameAction(int keyCode) {
        switch (keyCode) {
            case -1: case 50: return UP;
            case -2: case 56: return DOWN;
            case -3: case 52: return LEFT;
            case -4: case 54: return RIGHT;
            case -5: case 53: return FIRE;
            case 55: return GAME_A;
            case 57: return GAME_B;
            case 42: return GAME_C;
            case 35: return GAME_D;
            default: return 0;
        }
    }

    public int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP: return -1;
            case DOWN: return -2;
            case LEFT: return -3;
            case RIGHT: return -4;
            case FIRE: return -5;
            default: return 0;
        }
    }

    public String getKeyName(int keyCode) {
        switch (keyCode) {
            case -1: return "Up";
            case -2: return "Down";
            case -3: return "Left";
            case -4: return "Right";
            case -5: return "Enter";
            case -6: return "Enter";
            case -7: return "Esc";
            default: return String.valueOf((char) keyCode);
        }
    }

    protected void keyPressed(int keyCode) {}
    protected void keyReleased(int keyCode) {}
    protected void controllerKeyChanged(int keyCode, boolean pressed) {
        if (pressed) keyPressed(keyCode); else keyReleased(keyCode);
    }
    protected void showNotify() {}
    protected void hideNotify() {}

    /** Entry point used by the desktop window (keyPressed is protected in MIDP). */
    public final void dispatchKey(int keyCode, boolean pressed) {
        if (textEntryMode && keyCode != -5 && keyCode != -7) return;
        if (pressed) keyPressed(keyCode); else keyReleased(keyCode);
    }

    /** Separate gamepad key source so releasing a pad never releases a held keyboard key. */
    public final void dispatchControllerKey(int keyCode, boolean pressed) {
        if (textEntryMode && keyCode != -5 && keyCode != -7) return;
        controllerKeyChanged(keyCode, pressed);
    }

    public final void setTextEntryMode(boolean enabled) { textEntryMode = enabled; }

    /** Desktop pointer bridge; coordinates are in the game's logical canvas space. */
    public final synchronized void dispatchPointerMoved(int x, int y) {
        pointerX = x; pointerY = y; pointerFlags |= 1;
    }

    public final synchronized void dispatchPointerPressed(int x, int y) {
        pointerX = clickX = x; pointerY = clickY = y; pointerFlags |= 3;
    }

    public final synchronized void dispatchPointerWheel(int rotation) {
        pointerWheel += rotation;
        pointerFlags |= 4;
    }

    public final synchronized int consumePointerWheel() {
        int rotation = pointerWheel;
        pointerWheel = 0;
        return rotation;
    }

    /** Copies latest pointer coordinates into xy: [hoverX, hoverY, clickX, clickY]. */
    public final synchronized int consumePointerEvents(int[] xy) {
        int flags = pointerFlags;
        xy[0] = pointerX; xy[1] = pointerY; xy[2] = clickX; xy[3] = clickY;
        pointerFlags = 0;
        return flags;
    }

    /** Queues actual desktop text input for in-game editable menu rows. */
    public final synchronized void dispatchTextCharacter(char value) {
        if (Character.isISOControl(value)) return;
        if (textInput.size() >= 256) textInput.removeFirst();
        textInput.addLast((int)value);
    }

    public final synchronized void dispatchTextCommand(int command) {
        if (textInput.size() >= 256) textInput.removeFirst();
        textInput.addLast(command);
    }

    /** Drains pending text/control events. Returns the number copied. */
    public final synchronized int consumeTextInput(int[] target) {
        int count = 0;
        while (!textInput.isEmpty() && count < target.length) target[count++] = textInput.removeFirst();
        if (target.length == 0) textInput.clear();
        return count;
    }

    // ---- raw pointer stream (map editor): press / move / release with pointer ids, so drags and pinches work ----
    public static volatile boolean rawPointer;
    private final java.util.ArrayDeque<int[]> rawQueue = new java.util.ArrayDeque<int[]>();
    /** action 0 = down, 1 = move, 2 = up; id = pointer id (0 on desktop); button 1 = left, 2 = middle, 3 = right. */
    public final synchronized void dispatchRaw(int action, int x, int y, int id, int button) {
        if (rawQueue.size() >= 512) rawQueue.removeFirst();
        rawQueue.addLast(new int[]{action, x, y, id, button});
    }
    /** Pops one raw pointer event into out[0..4] = action, x, y, id, button. Returns false when the queue is empty. */
    public final synchronized boolean pollRaw(int[] out) {
        int[] e = rawQueue.pollFirst();
        if (e == null) return false;
        System.arraycopy(e, 0, out, 0, 5);
        return true;
    }
    public final synchronized void clearRaw() { rawQueue.clear(); }

    // ---- raw key stream (map editor shortcuts, fly view): code = ASCII upper-case letter / 8 backspace / 10 enter / 27 esc / 32 space / 16 shift / 17 ctrl / 37..40 arrows / 127 delete,
    // mods: 1 ctrl, 2 shift, 4 alt, 8 = this is a key RELEASE (code -1 + release = every key let go, e.g. the window lost focus)
    private final java.util.ArrayDeque<int[]> rawKeyQueue = new java.util.ArrayDeque<int[]>();
    public final synchronized void dispatchRawKey(int code, int mods, int ch) {
        if (rawKeyQueue.size() >= 160) rawKeyQueue.removeFirst();
        rawKeyQueue.addLast(new int[]{code, mods, ch});
    }
    public final synchronized void dispatchRawKeyRelease(int code, int mods) { dispatchRawKey(code, mods | 8, 0); }
    public final synchronized void dispatchRawKeyReset() { dispatchRawKey(-1, 8, 0); }
    public final synchronized void clearRawKeys() { rawKeyQueue.clear(); }
    public final synchronized boolean pollRawKey(int[] out) {
        int[] e = rawKeyQueue.pollFirst();
        if (e == null) return false;
        System.arraycopy(e, 0, out, 0, 3);
        return true;
    }
}
