package rally3d.core;

/** Shared state + geometry of the in-game log overlay and its top-right buttons (logical canvas coordinates). Java 8 safe. */
public final class LogView {
    private LogView() {}

    public static volatile boolean visible;
    /** Set by the game each frame: true while a multiplayer race is on screen (enables the TAB button). */
    public static volatile boolean mpRace;
    /** Set by the platform: true when a native on-screen pause button occupies the top-right corner (Android). */
    public static volatile boolean pauseButton;
    /** Visual pressed state of the TAB button. */
    public static volatile boolean tabHeld;
    /** 0 = all, 1 = warnings+errors, 2 = errors only. */
    public static volatile int filter;
    /** Scroll offset in rows from the newest line. */
    public static volatile int scroll;

    /** Set by the game: height of one menu row in logical px (for finger-scroll sensitivity). */
    public static volatile int menuRowLogical = 18;

    /** Set by the platform: false on PC (log opens with F1 only; no on-screen LOG button). */
    public static volatile boolean showLogButton = true;
    /** Set by the game: height of one log row in logical px (finger-drag scroll step). */
    public static volatile int rowLogical = 9;
    private static float dragRemainder;

    public static final int BTN_LOG = 0, BTN_TAB = 1;

    public static void toggle() { visible = !visible; scroll = 0; }

    /** Button rect in logical coords -> out = {x, y, w, h}. Buttons sit left of the pause button (or in the corner when there is none). */
    public static void rect(int button, int w, int h, int[] out) { /*T*/ final long $t = rally3d.core.Trace.in(0); try {
        int m = Math.max(2, (int) (h * 0.05f));
        int size = Math.max(14, (int) (h * 0.12f));
        int right = w - m - (pauseButton ? size + m : 0);
        int slot = button == BTN_LOG || !showLogButton ? 0 : 1;
        out[0] = right - size * (slot + 1) - m * slot;
        out[1] = m;
        out[2] = size;
        out[3] = size;
     } finally { rally3d.core.Trace.out(0, $t); } }

    /** Hit test with a small touch margin. Returns BTN_* or -1. */
    public static int hit(int x, int y, int w, int h) { /*T*/ final long $t = rally3d.core.Trace.in(1); try {
        int[] r = new int[4];
        int pad = Math.max(2, (int) (h * 0.02f));
        for (int b = 0; b < 2; b++) {
            if (b == BTN_TAB && !mpRace) continue;
            if (b == BTN_LOG && !showLogButton) continue;
            rect(b, w, h, r);
            if (x >= r[0] - pad && x < r[0] + r[2] + pad && y >= r[1] - pad && y < r[1] + r[3] + pad) return b;
        }
        return -1;
     } finally { rally3d.core.Trace.out(1, $t); } }

    /** Finger drag inside the panel: content follows the finger (drag down = older lines). dyLogical = finger delta in logical px. */
    public static void drag(float dyLogical) { /*T*/ final long $t = rally3d.core.Trace.in(233); try {
        dragRemainder += dyLogical / Math.max(1, rowLogical);
        int whole = (int) dragRemainder;
        if (whole == 0) return;
        dragRemainder -= whole;
        scroll = Math.max(0, scroll + whole);
     } finally { rally3d.core.Trace.out(233, $t); } }

    /** Panel taps while the overlay is open: top strip cycles filter. Returns true if consumed (any tap inside the panel). */
    public static boolean panelTap(int x, int y, int w, int h) { /*T*/ final long $t = rally3d.core.Trace.in(2); try {
        int left = w / 20, top = (int) (h * 0.2f), right = w - w / 20, bottom = h - h / 40;
        if (x < left || x >= right || y < top || y >= bottom) return false;
        if (y < top + (int) (h * 0.08f)) { filter = (filter + 1) % 3; scroll = 0; }
        dragRemainder = 0f;
        return true;
     } finally { rally3d.core.Trace.out(2, $t); } }
}
