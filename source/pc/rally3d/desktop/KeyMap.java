package rally3d.desktop;

import java.awt.event.KeyEvent;

/** Keyboard -> J2ME key codes (Nokia style: arrows -1..-4, fire -5, soft keys -6/-7, digits as ASCII). */
final class KeyMap {
    private KeyMap() {}

    static int map(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_TAB: return -13;
            case KeyEvent.VK_UP: case KeyEvent.VK_W: return -1;
            case KeyEvent.VK_DOWN: case KeyEvent.VK_S: return -2;
            case KeyEvent.VK_LEFT: case KeyEvent.VK_A: return -3;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: return -4;
            case KeyEvent.VK_ENTER: return -5;
            case KeyEvent.VK_SPACE: return -12;
            case KeyEvent.VK_ESCAPE: return -7;
            case KeyEvent.VK_MULTIPLY: return 42;
            case KeyEvent.VK_0: case KeyEvent.VK_NUMPAD0: return 48;
            case KeyEvent.VK_1: case KeyEvent.VK_NUMPAD1: return 49;
            case KeyEvent.VK_2: case KeyEvent.VK_NUMPAD2: return 50;
            case KeyEvent.VK_3: case KeyEvent.VK_NUMPAD3: return 51;
            case KeyEvent.VK_4: case KeyEvent.VK_NUMPAD4: return 52;
            case KeyEvent.VK_5: case KeyEvent.VK_NUMPAD5: return 53;
            case KeyEvent.VK_6: case KeyEvent.VK_NUMPAD6: return 54;
            case KeyEvent.VK_7: case KeyEvent.VK_NUMPAD7: return 55;
            case KeyEvent.VK_8: case KeyEvent.VK_NUMPAD8: return 56;
            case KeyEvent.VK_9: case KeyEvent.VK_NUMPAD9: return 57;
            default: return 0;
        }
    }
}
