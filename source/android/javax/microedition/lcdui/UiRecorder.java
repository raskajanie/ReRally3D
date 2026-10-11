package javax.microedition.lcdui;

/**
 * GPU UI mode: Graphics records its draw calls here (device-pixel coordinates, already scaled/rounded) instead of rasterising them with Java2D.
 * The GL thread replays the list into the UI framebuffer (rally3d.desktop.GpuUi). The list is reset after every presented frame; the replayed
 * framebuffer persists between frames exactly like the old CPU layer did (it is cleared only by an explicit CLEAR op).
 */
public final class UiRecorder {
    public static final int CLEAR = 0, CLIP = 1, FILL = 2, LINE = 3, IMAGE = 4, STRING = 5, RGB = 6;
    /** Per op: ops[i] = code, followed by a fixed number of ints (see Graphics): CLIP x0 y0 x1 y1 | FILL x0 y0 x1 y1 argb | LINE x1 y1 x2 y2 argb |
     *  IMAGE ref dx0 dy0 dx1 dy1 sx0 sy0 sx1 sy1 | STRING strRef fontRef left top rgb | RGB ref w h hasAlpha dx0 dy0 dx1 dy1 */
    public int[] ops = new int[8192];
    public int n;
    public Object[] refs = new Object[512];
    public int nr;
    /** Ops dropped because the list hit its hard limit (a runaway frame); logged by the executor. */
    public int overflow;
    private static final int MAX_INTS = 4_000_000;

    public void reset() {
        n = 0;
        java.util.Arrays.fill(refs, 0, nr, null);
        nr = 0;
    }

    private boolean room(int need) {
        if (n + need > ops.length) {
            if (ops.length >= MAX_INTS) { overflow++; return false; }
            ops = java.util.Arrays.copyOf(ops, Math.min(MAX_INTS, ops.length * 2));
            if (n + need > ops.length) { overflow++; return false; }
        }
        return true;
    }

    private int ref(Object o) {
        if (nr >= refs.length) refs = java.util.Arrays.copyOf(refs, refs.length * 2);
        refs[nr] = o;
        return nr++;
    }

    public void clear() { if (room(1)) ops[n++] = CLEAR; }
    public void clip(int x0, int y0, int x1, int y1) { if (room(5)) { ops[n++] = CLIP; ops[n++] = x0; ops[n++] = y0; ops[n++] = x1; ops[n++] = y1; } }
    public void fill(int x0, int y0, int x1, int y1, int argb) { if (room(6)) { ops[n++] = FILL; ops[n++] = x0; ops[n++] = y0; ops[n++] = x1; ops[n++] = y1; ops[n++] = argb; } }
    public void line(int x1, int y1, int x2, int y2, int argb) { if (room(6)) { ops[n++] = LINE; ops[n++] = x1; ops[n++] = y1; ops[n++] = x2; ops[n++] = y2; ops[n++] = argb; } }
    public void image(Image img, int dx0, int dy0, int dx1, int dy1, int sx0, int sy0, int sx1, int sy1) {
        if (!room(10)) return;
        ops[n++] = IMAGE; ops[n++] = ref(img); ops[n++] = dx0; ops[n++] = dy0; ops[n++] = dx1; ops[n++] = dy1; ops[n++] = sx0; ops[n++] = sy0; ops[n++] = sx1; ops[n++] = sy1;
    }
    public void string(String s, Font f, int left, int top, int rgb) {
        if (!room(6)) return;
        ops[n++] = STRING; ops[n++] = ref(s); ops[n++] = ref(f); ops[n++] = left; ops[n++] = top; ops[n++] = rgb;
    }
    /** Copies the pixels now: the caller may reuse its array. */
    public void rgb(int[] data, int off, int scan, int w, int h, boolean alpha, int dx0, int dy0, int dx1, int dy1) {
        if (!room(9)) return;
        int[] copy = new int[w * h];
        for (int j = 0; j < h; j++) System.arraycopy(data, off + j * scan, copy, j * w, w);
        ops[n++] = RGB; ops[n++] = ref(copy); ops[n++] = w; ops[n++] = h; ops[n++] = alpha ? 1 : 0; ops[n++] = dx0; ops[n++] = dy0; ops[n++] = dx1; ops[n++] = dy1;
    }
}
