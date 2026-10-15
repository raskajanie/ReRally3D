package com.nokia.mid.appl.rally3d;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import rally3d.core.RLog;

/**
 * In-game Car Builder (main menu "Car Builder"), the car twin of the Map Editor. One immediate-mode screen:
 * top bar (file / undo / redo / save), a live 3D view drawn by the game's own renderer (what you see is what the race shows),
 * a part strip at the bottom and a tool panel with four pages: PARTS (add / copy / delete / mirror), SHAPE (position, size,
 * taper, prism sides), PAINT (a 16x16 texture for the selected part only: pencil, fill, pick, patterns, PNG import) and CAR (name, stats).
 * Everything is a {@link CarModel}; SAVE installs a normal car / wheels add-on zip, EXPORT writes the same zip anywhere.
 */
final class CarEditor {
    static final int YEL = MapEditor.YEL, WHITE = MapEditor.WHITE, DIM = MapEditor.DIM, BG = MapEditor.BG, PANEL = MapEditor.PANEL,
            BTN = MapEditor.BTN, BTN_ON = MapEditor.BTN_ON, RED = MapEditor.RED, GREEN = MapEditor.GREEN, BLACK = MapEditor.BLACK;
    static final String[] MODES = {"PARTS", "SHAPE", "PAINT", "CAR"};
    static final int M_PARTS = 0, M_SHAPE = 1, M_PAINT = 2, M_CAR = 3;
    // button ids
    static final int A_FILE = 1, A_UNDO = 2, A_REDO = 3, A_SAVE = 4, A_HELP = 5, A_MODE = 10, A_ZIN = 20, A_ZOUT = 21, A_FIT = 22,
            A_SPIN = 23, A_GRID = 24, A_GUIDE = 25, A_LOCK = 26, A_ADD = 30, A_DUP = 34, A_DEL = 35, A_MIRROR = 36, A_HIDE = 37, A_RENAME = 38,
            A_STRIP = 300, A_FIELD = 400, A_STEP = 450, A_DRAG = 460, A_CENTERX = 470, A_ONFLOOR = 471,
            A_TOOL = 500, A_SWATCH = 510, A_HEX = 540, A_LIGHT = 541, A_DARK = 542, A_C2 = 543, A_SWAP = 544, A_PAT = 550,
            A_IMPORT = 570, A_COPYM = 571, A_PASTEM = 572, A_ALLSOLID = 573,
            A_NAME = 600, A_AUTHOR = 601, A_DESC = 602, A_KIND = 603, A_BASE = 610, A_STAT = 620, A_SHADE = 640,
            A_LIST = 1000, A_TEXTOK = 999;
    static final int LK_FILE = 1, LK_TEMPLATE = 2, LK_OPEN = 3, LK_IMPORT = 4, LK_CONFIRM = 5;
    // fields of the SHAPE page
    static final int F_X = 0, F_Y = 1, F_Z = 2, F_W = 3, F_H = 4, F_L = 5, F_TOPW = 6, F_TOPL = 7, F_SLX = 8, F_SLZ = 9, F_AXIS = 10, F_SIDES = 11, F_ENDR = 12;
    static final String[] FIELD_NAMES = {"X", "Y (UP)", "Z (FRONT)", "WIDTH", "HEIGHT", "LENGTH", "TOP WIDTH %", "TOP LENGTH %", "TOP SHIFT X", "TOP SHIFT Z", "AXIS", "SIDES", "END SIZE %"};
    static final float[] STEPS = {0.05f, 0.1f, 0.25f, 0.5f};
    static final int[] PALETTE = {
        0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF, 0xFF7A4A28, 0xFFB07840, 0xFFE8C898,
        0xFF800000, 0xFFE02020, 0xFFFF8020, 0xFFFFE030, 0xFF206020, 0xFF30B030, 0xFF90E050, 0xFF106060,
        0xFF2050C0, 0xFF50A0FF, 0xFFA0D8FF, 0xFF402080, 0xFF9050E0, 0xFFE070C0, 0xFF603030, 0xFFFFC8C8};
    static final String[] HELP = {
        "CAR BUILDER. A car is a list of PARTS (boxes and prisms). Each part has its own 16x16 texture. SAVE puts a normal car add-on into the game; EXPORT writes the same zip to any folder.",
        "VIEW: drag = turn the camera, two fingers (or right mouse button) = move + zoom, wheel = zoom. Tap a part to select it, or tap its name on the bottom strip. The yellow frame is the selected part.",
        "STRETCH: the selected part has 6 square handles (red = X, green = Y, blue = Z). Drag a handle to pull that face outwards or inwards; the opposite face stays where it is. Works in every tab, sizes snap to 0.05.",
        "LOCK (button above the view, or key L): the camera is frozen, nothing happens when you touch empty space - no turning, no panning, no pinch. Touches count only on car parts and on the handles, so you can stretch parts without spinning the screen. Use + / - to zoom, tap LOCK again to unfreeze.",
        "PARTS: + BOX, + WEDGE (a box whose top is smaller: bonnet, windscreen), + PRISM (wheel, pipe, cone). MIRROR makes the same part on the other side of the car (lights, mirrors). HIDE removes a part from the car.",
        "SHAPE: - / + change a value by the chosen step, tap the number to type it. DRAG X/Y/Z lets you push the selected part along an axis by dragging in the 3D view. Front of the car = +Z. The ground is Y=0, stock bodies start at Y=0.5.",
        "PAINT: pick a colour, tap the 16x16 grid. Patterns use COLOR 1 and COLOR 2. IMPORT PNG reads pictures from the add-ons/import folder and shrinks them to 16x16. Every part has its own texture, so paint them one by one.",
        "Light is baked into the texture (top bright, bottom dark) because the game has no lighting. CAR page: SHADE off = flat colours.",
        "Wheels: File > New > WHEELS... or CAR page > TYPE. The wheel is built around Y=0.54 with its axle along X, like the stock wheel. GUIDE shows it on a stock car.",
        "Limits: 64 parts, 20000 triangles. With more than 16 parts the texture grows to 256x256. Cars made here work in multiplayer like any add-on car."
    };

    /** Orbit camera. Shared maths for the 3D draw, the overlay and picking (package-private: also used by the offline checks). */
    static final class Cam {
        float yaw = -35f, pitch = 20f, dist = 9f, panX, panY, tgtY = 1f;
        float vx, vy, vw = 100, vh = 100;
        static final float F = 1.7320508f;   // 1 / tan(30 deg): the game camera is 60 degrees

        void toView(float x, float y, float z, float[] o) { /*T*/ final long $t = rally3d.core.Trace.in(631); try {
            y -= tgtY;
            double a = Math.toRadians(yaw), c = Math.cos(a), s = Math.sin(a);
            double x1 = x * c + z * s, z1 = -x * s + z * c;
            double b = Math.toRadians(pitch), cb = Math.cos(b), sb = Math.sin(b);
            double y2 = y * cb - z1 * sb, z2 = y * sb + z1 * cb;
            o[0] = (float) x1 + panX; o[1] = (float) y2 + panY; o[2] = (float) z2 - dist;
         } finally { rally3d.core.Trace.out(631, $t); } }

        /** World point to screen pixels; false when it is behind the camera. o = {x, y, depth}. */
        boolean project(float x, float y, float z, float[] o) { /*T*/ final long $t = rally3d.core.Trace.in(632); try {
            toView(x, y, z, o);
            if (o[2] > -0.2f) return false;
            float asp = vw / vh, d = -o[2];
            float nx = o[0] * F / asp / d, ny = o[1] * F / d;
            o[0] = vx + vw / 2f * (1 + nx); o[1] = vy + vh / 2f * (1 - ny); o[2] = d;
            return true;
         } finally { rally3d.core.Trace.out(632, $t); } }

        void ray(float sx, float sy, float[] org, float[] dir) { /*T*/ final long $t = rally3d.core.Trace.in(633); try {
            float asp = vw / vh;
            float nx = (sx - vx) / (vw / 2f) - 1, ny = 1 - (sy - vy) / (vh / 2f);
            float[] v = {nx * asp / F, ny / F, -1f};
            float[] p0 = {0, 0, 0};
            fromView(p0, org, true);
            float[] dd = new float[3];
            fromView(v, dd, false);
            float l = (float) Math.sqrt(dd[0] * dd[0] + dd[1] * dd[1] + dd[2] * dd[2]);
            dir[0] = dd[0] / l; dir[1] = dd[1] / l; dir[2] = dd[2] / l;
         } finally { rally3d.core.Trace.out(633, $t); } }

        private void fromView(float[] v, float[] o, boolean point) { /*T*/ final long $t = rally3d.core.Trace.in(634); try {
            double x = v[0] - (point ? panX : 0), y = v[1] - (point ? panY : 0), z = v[2] - (point ? -dist : 0);
            double b = Math.toRadians(pitch), cb = Math.cos(b), sb = Math.sin(b);
            double y1 = y * cb + z * sb, z1 = -y * sb + z * cb;
            double a = Math.toRadians(yaw), c = Math.cos(a), s = Math.sin(a);
            o[0] = (float) (x * c - z1 * s); o[2] = (float) (x * s + z1 * c); o[1] = (float) y1 + (point ? tgtY : 0);
         } finally { rally3d.core.Trace.out(634, $t); } }
    }

    private final RallyGame g;
    CarModel md = CarModel.template(0);
    private final ArrayList<CarModel> undo = new ArrayList<CarModel>(), redo = new ArrayList<CarModel>();
    private int savedRev;
    private int sel;
    private int mode = M_PARTS;
    final Cam cam = new Cam();
    private boolean spin, showGrid = true, showGuide = true, exit;
    private int dragAxis = -1, stepIdx = 1;
    // built mesh for the live view
    private CarModel.Built built;
    private int builtRev = -1;
    private String buildError;
    private Mesh3D mesh;
    private Texture2D tex;
    private final Transform tmp = new Transform(), base = new Transform();
    // paint state
    private int tool, color1 = 0xFFD42828, color2 = 0xFF202020, pattern;
    private int gridX, gridY, gridCell;
    private static int[] matClip;
    private int nextColor;
    // layout
    int W, H, TB = 16, SB = 24, PW = 112, vx, vy, vw, vh, fh, sh;
    private final ArrayList<int[]> btns = new ArrayList<int[]>();
    private int panelScroll, panelContent, stripScroll, stripContent;
    private boolean stripToSel;
    private int clipY0 = -1, clipY1 = -1;
    // pointers
    private final int[] pid = {-1, -1, -1}, px = new int[3], py = new int[3], dx0 = new int[3], dy0 = new int[3], region = new int[3], pbtn = new int[3];
    private final boolean[] pmoved = new boolean[3], paintDrag = new boolean[3];
    private final long[] pt0 = new long[3];
    private int hoverX = -1, hoverY = -1;
    private double pinchDist;
    private final int[] rawKeyBuf = new int[3];
    private long lastFrame;
    // overlays
    private String toast;
    private long toastUntil;
    private String listTitle;
    private String[] listItems;
    private int listKind, listScroll, pendKind, pendArg;
    private String[] textLines;
    private String textTitle;
    private int textScroll;
    private File[] importFiles;
    private int[] installedIdx;

    CarEditor(RallyGame game) { this.g = game; }

    // ------------------------------------------------------------------ main loop

    void run(AddonPack open) { /*T*/ final long $t = rally3d.core.Trace.in(635); try {
        CarModel first = open != null ? CarModel.fromPack(open) : null;
        if (first != null) md = first; else md = CarModel.template(0);
        resetView();
        savedRev = md.rev;
        int screenBefore = g.screenMode;
        int ovx = g.viewX, ovy = g.viewY, ovw = g.viewW, ovh = g.viewH;
        g.beginMenu(rally3d.core.Lang.t("Car Builder"));
        g.menuHeaderStatic = false;
        g.menuSlide = 0;
        g.screenMode = 2;
        g.carEditor = this;
        g.snapshotKeys();
        javax.microedition.lcdui.Canvas.rawPointer = true;
        g.clearRaw();
        say(rally3d.core.Lang.t("Tap a part to select it. Tap ? for help."));
        int[] ev = new int[5];
        lastFrame = System.nanoTime();
        try {
            while (!exit) {
                g.redrawBackground = false;
                g.renderFrame();
                while (g.pollRaw(ev)) pointer(ev[0], ev[1], ev[2], ev[3], ev[4]);
                int wheel = g.consumePointerWheel();
                if (wheel != 0) wheel(wheel);
                int[] kv = rawKeyBuf;
                while (g.pollRawKey(kv)) rawKey(kv[0], kv[1], kv[2]);
                int keys = g.pollNewKeys();
                if (keys != 0) key(keys);
                if (g.paused) { g.paused = false; g.clearKeys(); }
            }
        } finally {
            javax.microedition.lcdui.Canvas.rawPointer = false;
            g.clearRaw();
            g.carEditor = null;
            g.screenMode = screenBefore;
            g.redrawBackground = true;
            releaseGpu();
            try { g.setViewport(ovx, ovy, ovw, ovh); g.setupCamera(); }
            catch (RuntimeException e) { RLog.caught("CarEditor.run", e); }
        }
     } finally { rally3d.core.Trace.out(635, $t); } }

    private void releaseGpu() { /*T*/ final long $t = rally3d.core.Trace.in(636); try {
        try { if (tex != null) g.g3d.releaseTexture(tex); }
        catch (RuntimeException e) { RLog.caught("CarEditor.releaseGpu", e); }
        tex = null; mesh = null; built = null; builtRev = -1;
     } finally { rally3d.core.Trace.out(636, $t); } }

    void say(String t) { toast = t; toastUntil = System.currentTimeMillis() + 2800L; }

    private void resetView() { /*T*/ final long $t = rally3d.core.Trace.in(637); try {
        cam.yaw = -35f; cam.pitch = 20f; cam.panX = 0; cam.panY = 0;
        cam.tgtY = md.wheels ? 0.54f : 1.0f;
        cam.dist = md.wheels ? 4.2f : 9.5f;
     } finally { rally3d.core.Trace.out(637, $t); } }

    // ------------------------------------------------------------------ undo / selection

    private void push() {
        undo.add(md.copy());
        if (undo.size() > 60) undo.remove(0);
        redo.clear();
    }

    private void cancelPush() { if (!undo.isEmpty()) undo.remove(undo.size() - 1); }

    private void doUndo() { /*T*/ final long $t = rally3d.core.Trace.in(638); try {
        if (undo.isEmpty()) { say(rally3d.core.Lang.t("Nothing to undo")); return; }
        redo.add(md.copy());
        md.restore(undo.remove(undo.size() - 1));
        clampSel();
     } finally { rally3d.core.Trace.out(638, $t); } }

    private void doRedo() { /*T*/ final long $t = rally3d.core.Trace.in(639); try {
        if (redo.isEmpty()) { say(rally3d.core.Lang.t("Nothing to redo")); return; }
        undo.add(md.copy());
        md.restore(redo.remove(redo.size() - 1));
        clampSel();
     } finally { rally3d.core.Trace.out(639, $t); } }

    boolean dirty() { return md.rev != savedRev; }

    private void clampSel() { sel = Math.max(0, Math.min(md.parts.size() - 1, sel)); stripToSel = true; }

    private CarModel.Part cur() { return md.parts.isEmpty() ? null : md.parts.get(Math.max(0, Math.min(md.parts.size() - 1, sel))); }

    private void select(int i) { if (i < 0 || i >= md.parts.size()) return; sel = i; stripToSel = true; }

    // ------------------------------------------------------------------ input

    private int slot(int id) { for (int i = 0; i < 3; i++) if (pid[i] == id) return i; return -1; }
    private int activePointers() { int c = 0; for (int i = 0; i < 3; i++) if (pid[i] >= 0) c++; return c; }

    private int regionAt(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(640); try {
        if (listItems != null || textLines != null) return 9;
        if (y < TB) return 4;
        if (x >= W - PW) return 3;
        if (y >= H - SB) return 2;
        return 1;
     } finally { rally3d.core.Trace.out(640, $t); } }

    private void pointer(int action, int x, int y, int id, int button) { /*T*/ final long $t = rally3d.core.Trace.in(641); try {
        hoverX = x; hoverY = y;
        int s = slot(id);
        if (action == 0) {
            if (s >= 0) return;
            for (int i = 0; i < 3; i++) if (pid[i] < 0) { s = i; break; }
            if (s < 0) return;
            pid[s] = id; px[s] = dx0[s] = x; py[s] = dy0[s] = y; pmoved[s] = false; pt0[s] = System.currentTimeMillis(); pbtn[s] = button; paintDrag[s] = false;
            region[s] = regionAt(x, y);
            if (region[s] == 3 && mode == M_PAINT && gridHit(x, y)) { paintDrag[s] = true; paintBegin(x, y); }
            onPart[s] = false;
            if (region[s] == 1 && hActive < 0 && !btnAt(x, y)) {
                int h = handleAt(x, y);
                if (h >= 0) handleBegin(s, h);
                else if (lockMode) {
                    int hit = pickAt(x, y);
                    if (hit >= 0) { onPart[s] = true; if (hit != sel) select(hit); }
                }
            }
            if (region[s] == 1 && !lockMode && activePointers() == 2) pinchDist = pinchNow();
            return;
        }
        if (s < 0) return;
        if (action == 1) {
            int ox = px[s], oy = py[s];
            px[s] = x; py[s] = y;
            if (!pmoved[s] && Math.abs(x - dx0[s]) + Math.abs(y - dy0[s]) > 5) pmoved[s] = true;
            if (paintDrag[s]) { paintMove(x, y); return; }
            if (hActive >= 0 && hSlot == s) { if (pmoved[s]) handleDrag(x - dx0[s], y - dy0[s]); return; }
            if (!pmoved[s]) return;
            switch (region[s]) {
                case 1: viewDrag(s, x - ox, y - oy, x, y); break;
                case 2: stripScroll = Math.max(0, Math.min(Math.max(0, stripContent - (W - PW - 8)), stripScroll - (x - ox))); break;
                case 3: panelScroll = Math.max(0, Math.min(Math.max(0, panelContent - (H - TB - 4)), panelScroll - (y - oy))); break;
                case 9: listScroll = Math.max(0, listScroll - (y - oy)); textScroll = Math.max(0, textScroll - (y - oy)); break;
                default: break;
            }
            return;
        }
        if (action == 2) {
            boolean tap = !pmoved[s] && System.currentTimeMillis() - pt0[s] < 900;
            int r = region[s];
            boolean wasPaint = paintDrag[s];
            boolean wasHandle = hActive >= 0 && hSlot == s;
            if (wasHandle) { hActive = -1; hSlot = -1; }
            pid[s] = -1; paintDrag[s] = false; onPart[s] = false;
            if (wasHandle && pmoved[s]) return;
            if (activePointers() < 2) pinchDist = 0;
            if (wasPaint) return;
            if (tap) tapAt(x, y, r);
        }
     } finally { rally3d.core.Trace.out(641, $t); } }

    private double pinchNow() { /*T*/ final long $t = rally3d.core.Trace.in(642); try {
        int a = -1, b = -1;
        for (int i = 0; i < 3; i++) if (pid[i] >= 0 && region[i] == 1) { if (a < 0) a = i; else b = i; }
        if (b < 0) return 0;
        return Math.hypot(px[a] - px[b], py[a] - py[b]);
     } finally { rally3d.core.Trace.out(642, $t); } }

    private void viewDrag(int s, int ddx, int ddy, int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(643); try {
        if (lockMode) {
            // frozen camera: only an explicit DRAG X/Y/Z push that started on a part still works
            CarModel.Part lp = cur();
            if (onPart[s] && pbtn[s] == 1 && mode == M_SHAPE && dragAxis >= 0 && lp != null) moveAlong(lp, dragAxis, ddx, ddy);
            return;
        }
        if (activePointers() >= 2) {
            double d = pinchNow();
            if (pinchDist > 8 && d > 8) cam.dist = Math.max(1.5f, Math.min(40f, (float) (cam.dist * pinchDist / d)));
            pinchDist = d;
            pan(ddx / 2, ddy / 2);
            return;
        }
        if (pbtn[s] != 1) { pan(ddx, ddy); return; }
        CarModel.Part p = cur();
        if (mode == M_SHAPE && dragAxis >= 0 && p != null) { moveAlong(p, dragAxis, ddx, ddy); return; }
        cam.yaw += ddx * 0.45f;
        cam.pitch = Math.max(-85f, Math.min(85f, cam.pitch + ddy * 0.4f));
     } finally { rally3d.core.Trace.out(643, $t); } }

    private void pan(int ddx, int ddy) {
        float k = 2f * cam.dist / Cam.F / Math.max(1, vh);
        cam.panX += ddx * k; cam.panY -= ddy * k;
    }

    private void moveAlong(CarModel.Part p, int axis, int ddx, int ddy) { /*T*/ final long $t = rally3d.core.Trace.in(644); try {
        float[] a = new float[3], b = new float[3];
        float x = p.cx, y = p.cy, z = p.cz;
        if (!cam.project(x, y, z, a) || !cam.project(axis == 0 ? x + 1 : x, axis == 1 ? y + 1 : y, axis == 2 ? z + 1 : z, b)) return;
        float sx = b[0] - a[0], sy = b[1] - a[1], l2 = sx * sx + sy * sy;
        if (l2 < 1f) return;
        float amount = (ddx * sx + ddy * sy) / l2;
        if (dragPush) { push(); dragPush = false; }
        if (axis == 0) p.cx += amount; else if (axis == 1) p.cy += amount; else p.cz += amount;
        p.clampAll();
        md.touch();
     } finally { rally3d.core.Trace.out(644, $t); } }

    private boolean dragPush = true;

    // ---- stretch handles + lock mode
    private boolean lockMode;
    private int hActive = -1, hSlot = -1;          // handle being dragged (0..5 = +X -X +Y -Y +Z -Z) and the pointer slot that owns it
    private boolean hPushed;
    private float hStartC, hStartS, hAxX, hAxY;    // part centre / size on that axis at the start, screen vector of 1 world unit
    private final boolean[] onPart = new boolean[3];
    static final String[] HN = {"WIDTH", "HEIGHT", "LENGTH"};

    private float[] partSize(CarModel.Part p) { return new float[]{p.sx, p.sy, p.sz}; }

    /** World position of stretch handle h (face centre of the selected part). */
    private void handleWorld(CarModel.Part p, int h, float[] o) {
        int ax = h >> 1;
        float sg = (h & 1) == 0 ? 1f : -1f;
        o[0] = p.cx; o[1] = p.cy; o[2] = p.cz;
        o[ax] += sg * partSize(p)[ax] / 2f;
    }

    private int handleRadius() { return Math.max(16, sh + 10); }

    /** Nearest handle of the selected part within touch radius of (x, y), or -1. */
    private int handleAt(int x, int y) {
        CarModel.Part p = cur();
        if (p == null || p.hidden || built == null) return -1;
        float[] w = new float[3], sc = new float[3];
        int best = -1;
        float bd = handleRadius() * (float) handleRadius();
        for (int h = 0; h < 6; h++) {
            handleWorld(p, h, w);
            if (!cam.project(w[0], w[1], w[2], sc)) continue;
            float dx = sc[0] - x, dy = sc[1] - y, d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = h; }
        }
        return best;
    }

    private int pickAt(int x, int y) {
        if (built == null) return -1;
        float[] o = new float[3], d = new float[3];
        cam.ray(x, y, o, d);
        return pick(o, d);
    }

    private void handleBegin(int s, int h) {
        CarModel.Part p = cur();
        int ax = h >> 1;
        float[] c = {p.cx, p.cy, p.cz};
        hActive = h; hSlot = s; hPushed = false;
        hStartC = c[ax]; hStartS = partSize(p)[ax];
        float[] a = new float[3], b = new float[3];
        float x = p.cx, y = p.cy, z = p.cz;
        hAxX = 0; hAxY = 0;
        if (cam.project(x, y, z, a) && cam.project(ax == 0 ? x + 1 : x, ax == 1 ? y + 1 : y, ax == 2 ? z + 1 : z, b)) { hAxX = b[0] - a[0]; hAxY = b[1] - a[1]; }
    }

    private void handleDrag(int tx, int ty) {
        CarModel.Part p = cur();
        if (p == null || hActive < 0) return;
        float l2 = hAxX * hAxX + hAxY * hAxY;
        if (l2 < 1f) { say(rally3d.core.Lang.t("Axis points at the camera - turn the view (unlock) a bit")); return; }
        int ax = hActive >> 1;
        float sg = (hActive & 1) == 0 ? 1f : -1f;
        float amount = (tx * hAxX + ty * hAxY) / l2;          // how far the dragged face moved along +axis, world units
        float opp = hStartC - sg * hStartS / 2f;               // the opposite face stays fixed
        float ns = hStartS + sg * amount;
        ns = Math.round(ns * 20f) / 20f;
        ns = Math.max(0.05f, Math.min(12f, ns));
        float nc = opp + sg * ns / 2f;
        if (!hPushed) { push(); hPushed = true; }
        if (ax == 0) { p.sx = ns; p.cx = nc; } else if (ax == 1) { p.sy = ns; p.cy = nc; } else { p.sz = ns; p.cz = nc; }
        p.cx = Math.round(p.cx * 100f) / 100f; p.cy = Math.round(p.cy * 100f) / 100f; p.cz = Math.round(p.cz * 100f) / 100f;
        p.clampAll();
        md.touch();
    }

    private void wheel(int rot) { /*T*/ final long $t = rally3d.core.Trace.in(645); try {
        int x = hoverX, y = hoverY;
        if (listItems != null) { listScroll = Math.max(0, listScroll + rot * 24); return; }
        if (textLines != null) { textScroll = Math.max(0, textScroll + rot * 24); return; }
        if (x >= W - PW && y > TB) { panelScroll = Math.max(0, Math.min(Math.max(0, panelContent - (H - TB - 4)), panelScroll + rot * 14)); return; }
        if (regionAt(x, y) == 1) cam.dist = Math.max(1.5f, Math.min(40f, cam.dist * (float) Math.pow(1.12, rot)));
     } finally { rally3d.core.Trace.out(645, $t); } }

    private void key(int k) { /*T*/ final long $t = rally3d.core.Trace.in(646); try {
        if (textLines != null) { if ((k & 0x200) != 0) textLines = null; return; }
        if (listItems != null) { if ((k & 0x200) != 0) listClose(); return; }
        if ((k & 0x200) != 0) { fileMenu(); return; }
        if ((k & 4) != 0) select(sel - 1);
        else if ((k & 0x20) != 0) select(sel + 1);
     } finally { rally3d.core.Trace.out(646, $t); } }

    private void rawKey(int code, int mods, int ch) { /*T*/ final long $t = rally3d.core.Trace.in(647); try {
        if ((mods & 8) != 0 || code < 0) return;
        boolean ctrl = (mods & 1) != 0, shift = (mods & 2) != 0;
        if (textLines != null) { if (code == 27 || code == 10) textLines = null; return; }
        if (listItems != null) { if (code == 27) listClose(); return; }
        if (ctrl) {
            switch (code) {
                case 'Z': if (shift) doRedo(); else doUndo(); break;
                case 'Y': doRedo(); break;
                case 'S': save(); break;
                case 'E': exportZip(); break;
                case 'D': dupPart(); break;
                case 'N': pendKind = 1; confirmThen(0); break;
                case 'O': openInstalledList(); break;
                default: break;
            }
            return;
        }
        switch (code) {
            case 27: fileMenu(); break;
            case 127: case 8: delPart(); break;
            case 9: select((sel + (shift ? md.parts.size() - 1 : 1)) % Math.max(1, md.parts.size())); break;
            case 33: case 37: select(sel - 1); break;
            case 34: case 39: select(sel + 1); break;
            case 'F': resetView(); break;
            case 'H': showText(rally3d.core.Lang.t("HELP"), rally3d.core.Lang.tl(HELP)); break;
            case 'M': toggleMirror(); break;
            case 'L': setLock(!lockMode); break;
            case 61: case 107: cam.dist = Math.max(1.5f, cam.dist / 1.2f); break;
            case 45: case 109: cam.dist = Math.min(40f, cam.dist * 1.2f); break;
            default:
                if (code >= '1' && code <= '4') setMode(code - '1');
                break;
        }
     } finally { rally3d.core.Trace.out(647, $t); } }

    private void setMode(int m) { mode = Math.max(0, Math.min(3, m)); panelScroll = 0; }

    private boolean btnAt(int x, int y) {
        for (int i = btns.size() - 1; i >= 0; i--) {
            int[] b = btns.get(i);
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) return true;
        }
        return false;
    }

    private void setLock(boolean on) {
        lockMode = on;
        hActive = -1; hSlot = -1; pinchDist = 0;
        say(on ? rally3d.core.Lang.t("LOCK: camera frozen, only parts + handles react") : rally3d.core.Lang.t("LOCK off: camera is free"));
    }

    private void tapAt(int x, int y, int r) { /*T*/ final long $t = rally3d.core.Trace.in(648); try {
        for (int i = btns.size() - 1; i >= 0; i--) {
            int[] b = btns.get(i);
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) { action(b[4], b[5]); return; }
        }
        if (r == 9) { if (listItems != null) listClose(); return; }
        if (r == 1) viewTap(x, y);
     } finally { rally3d.core.Trace.out(648, $t); } }

    private void viewTap(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(649); try {
        if (built == null) return;
        float[] o = new float[3], d = new float[3];
        cam.ray(x, y, o, d);
        int hit = pick(o, d);
        if (hit >= 0) { select(hit); say(md.parts.get(hit).name); }
     } finally { rally3d.core.Trace.out(649, $t); } }

    /** Nearest part under the ray (two-sided Moeller-Trumbore over the built triangles), or -1. */
    private int pick(float[] o, float[] d) { /*T*/ final long $t = rally3d.core.Trace.in(650); try {
        float best = 1e9f;
        int part = -1;
        float[] p = built.pos;
        int[] ix = built.idx;
        for (int t = 0; t < built.tris; t++) {
            int a = ix[t * 3] * 3, b = ix[t * 3 + 1] * 3, c = ix[t * 3 + 2] * 3;
            float e1x = p[b] - p[a], e1y = p[b + 1] - p[a + 1], e1z = p[b + 2] - p[a + 2];
            float e2x = p[c] - p[a], e2y = p[c + 1] - p[a + 1], e2z = p[c + 2] - p[a + 2];
            float hx = d[1] * e2z - d[2] * e2y, hy = d[2] * e2x - d[0] * e2z, hz = d[0] * e2y - d[1] * e2x;
            float det = e1x * hx + e1y * hy + e1z * hz;
            if (Math.abs(det) < 1e-9f) continue;
            float inv = 1f / det;
            float sx = o[0] - p[a], sy = o[1] - p[a + 1], sz = o[2] - p[a + 2];
            float u = (sx * hx + sy * hy + sz * hz) * inv;
            if (u < 0f || u > 1f) continue;
            float qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
            float v = (d[0] * qx + d[1] * qy + d[2] * qz) * inv;
            if (v < 0f || u + v > 1f) continue;
            float tt = (e2x * qx + e2y * qy + e2z * qz) * inv;
            if (tt > 0.01f && tt < best) { best = tt; part = built.triPart[t]; }
        }
        return part;
     } finally { rally3d.core.Trace.out(650, $t); } }

    // ------------------------------------------------------------------ actions

    private void action(int id, int arg) { /*T*/ final long $t = rally3d.core.Trace.in(651); try {
        if (id >= A_LIST) { listPick(id - A_LIST); return; }
        if (id == A_TEXTOK) { textLines = null; return; }
        if (id >= A_MODE && id < A_MODE + 4) { setMode(id - A_MODE); return; }
        if (id >= A_STRIP && id < A_STRIP + CarModel.MAX_PARTS) { select(id - A_STRIP); return; }
        if (id >= A_FIELD && id < A_FIELD + 13) { fieldAction(id - A_FIELD, arg); return; }
        if (id >= A_STEP && id < A_STEP + 4) { stepIdx = id - A_STEP; return; }
        if (id >= A_DRAG && id < A_DRAG + 4) { dragAxis = id - A_DRAG == 3 ? -1 : id - A_DRAG; dragPush = true; return; }
        if (id >= A_TOOL && id < A_TOOL + 3) { tool = id - A_TOOL; return; }
        if (id >= A_SWATCH && id < A_SWATCH + PALETTE.length) { color1 = PALETTE[id - A_SWATCH]; return; }
        if (id >= A_PAT && id < A_PAT + CarModel.PATTERNS.length) { applyPattern(id - A_PAT); return; }
        if (id >= A_ADD && id < A_ADD + 3) { addPart(id - A_ADD); return; }
        if (id >= A_BASE && id < A_BASE + 1) { push(); md.base = Math.max(1, Math.min(4, md.base + arg)); md.touch(); return; }
        if (id >= A_STAT && id < A_STAT + 5) { statAction(id - A_STAT, arg); return; }
        switch (id) {
            case A_FILE: fileMenu(); return;
            case A_UNDO: doUndo(); return;
            case A_REDO: doRedo(); return;
            case A_SAVE: save(); return;
            case A_HELP: showText(rally3d.core.Lang.t("HELP"), rally3d.core.Lang.tl(HELP)); return;
            case A_ZIN: cam.dist = Math.max(1.5f, cam.dist / 1.25f); return;
            case A_ZOUT: cam.dist = Math.min(40f, cam.dist * 1.25f); return;
            case A_FIT: resetView(); return;
            case A_SPIN: spin = !spin; return;
            case A_GRID: showGrid = !showGrid; return;
            case A_GUIDE: showGuide = !showGuide; return;
            case A_LOCK: setLock(!lockMode); return;
            case A_DUP: dupPart(); return;
            case A_DEL: delPart(); return;
            case A_MIRROR: toggleMirror(); return;
            case A_HIDE: { CarModel.Part p = cur(); if (p != null) { push(); p.hidden = !p.hidden; md.touch(); } return; }
            case A_RENAME: renamePart(); return;
            case A_CENTERX: { CarModel.Part p = cur(); if (p != null) { push(); p.cx = 0; p.slideX = 0; md.touch(); } return; }
            case A_ONFLOOR: { CarModel.Part p = cur(); if (p != null) { push(); p.cy = p.sy / 2f + (md.wheels ? 0.04f : 0.5f); md.touch(); } return; }
            case A_HEX: hexColor(); return;
            case A_LIGHT: color1 = CarModel.lighten(color1, 24); return;
            case A_DARK: color1 = CarModel.lighten(color1, -24); return;
            case A_C2: color2 = color1; return;
            case A_SWAP: { int t = color1; color1 = color2; color2 = t; return; }
            case A_IMPORT: openImport(); return;
            case A_COPYM: { CarModel.Part p = cur(); if (p != null) { matClip = p.mat.clone(); say(rally3d.core.Lang.t("Texture copied")); } return; }
            case A_PASTEM: { CarModel.Part p = cur(); if (p != null && matClip != null) { push(); p.mat = matClip.clone(); md.touch(); } else say(rally3d.core.Lang.t("Copy a texture first")); return; }
            case A_ALLSOLID: { push(); for (CarModel.Part p : md.parts) p.mat = CarModel.solid(color1); md.touch(); say(rally3d.core.Lang.t("All parts painted")); return; }
            case A_NAME: editInfo(0); return;
            case A_AUTHOR: editInfo(1); return;
            case A_DESC: editInfo(2); return;
            case A_KIND: { push(); md.wheels = !md.wheels; md.touch(); resetView(); say(md.wheels ? rally3d.core.Lang.t("Now building WHEELS (axle along X)") : rally3d.core.Lang.t("Now building a CAR")); return; }
            case A_SHADE: { push(); md.shade = !md.shade; md.touch(); return; }
            default: break;
        }
     } finally { rally3d.core.Trace.out(651, $t); } }

    private void statAction(int k, int arg) { /*T*/ final long $t = rally3d.core.Trace.in(652); try {
        push();
        switch (k) {
            case 0: md.speed = (int) CarModel.cl(md.speed + 5 * arg, 20, 200); break;
            case 1: md.accel = CarModel.cl(Math.round((md.accel + 0.1f * arg) * 10f) / 10f, 0.2f, 4f); break;
            case 2: md.steer = CarModel.cl(Math.round((md.steer + 0.1f * arg) * 10f) / 10f, 0.3f, 3f); break;
            case 3: md.steerMax = CarModel.cl(Math.round((md.steerMax + 0.1f * arg) * 10f) / 10f, 0.3f, 3f); break;
            default: md.grip = CarModel.cl(Math.round((md.grip + 0.1f * arg) * 10f) / 10f, 0.3f, 3f); break;
        }
        md.touch();
     } finally { rally3d.core.Trace.out(652, $t); } }

    private String fieldText(CarModel.Part p, int f) { /*T*/ final long $t = rally3d.core.Trace.in(653); try {
        switch (f) {
            case F_X: return fl(p.cx);
            case F_Y: return fl(p.cy);
            case F_Z: return fl(p.cz);
            case F_W: return fl(p.sx);
            case F_H: return fl(p.sy);
            case F_L: return fl(p.sz);
            case F_TOPW: return String.valueOf(p.topW);
            case F_TOPL: return String.valueOf(p.topL);
            case F_SLX: return fl(p.slideX);
            case F_SLZ: return fl(p.slideZ);
            case F_AXIS: return CarModel.AXIS_NAMES[p.axis];
            case F_SIDES: return String.valueOf(p.sides);
            default: return String.valueOf(p.endR);
        }
     } finally { rally3d.core.Trace.out(653, $t); } }

    private static String fl(float v) { return String.format(java.util.Locale.ROOT, "%.2f", v); }

    /** arg -1 / +1 = step, 0 = type a number. */
    private void fieldAction(int f, int arg) { /*T*/ final long $t = rally3d.core.Trace.in(654); try {
        CarModel.Part p = cur();
        if (p == null) return;
        float st = STEPS[stepIdx];
        int pc = Math.max(1, Math.round(st * 100));
        float typed = 0;
        if (arg == 0) {
            if (f == F_AXIS) arg = 1;
            else {
                String v = askText(rally3d.core.Lang.t(FIELD_NAMES[f]), "", fieldText(p, f), 8);
                if (v == null) return;
                try { typed = Float.parseFloat(v.trim().replace(',', '.')); }
                catch (NumberFormatException e) { RLog.caught("CarEditor.fieldAction", e); say("Not a number: " + v); return; }
            }
        }
        push();
        boolean set = arg == 0;
        switch (f) {
            case F_X: p.cx = set ? typed : p.cx + arg * st; break;
            case F_Y: p.cy = set ? typed : p.cy + arg * st; break;
            case F_Z: p.cz = set ? typed : p.cz + arg * st; break;
            case F_W: p.sx = set ? typed : p.sx + arg * st; break;
            case F_H: p.sy = set ? typed : p.sy + arg * st; break;
            case F_L: p.sz = set ? typed : p.sz + arg * st; break;
            case F_TOPW: p.topW = set ? Math.round(typed) : p.topW + arg * pc; break;
            case F_TOPL: p.topL = set ? Math.round(typed) : p.topL + arg * pc; break;
            case F_SLX: p.slideX = set ? typed : p.slideX + arg * st; break;
            case F_SLZ: p.slideZ = set ? typed : p.slideZ + arg * st; break;
            case F_AXIS: p.axis = (p.axis + (arg < 0 ? 2 : 1)) % 3; break;
            case F_SIDES: p.sides = set ? Math.round(typed) : p.sides + arg; break;
            default: p.endR = set ? Math.round(typed) : p.endR + arg * pc; break;
        }
        p.cx = Math.round(p.cx * 100f) / 100f; p.cy = Math.round(p.cy * 100f) / 100f; p.cz = Math.round(p.cz * 100f) / 100f;
        p.clampAll();
        md.touch();
     } finally { rally3d.core.Trace.out(654, $t); } }

    private void addPart(int kind) { /*T*/ final long $t = rally3d.core.Trace.in(655); try {
        if (md.parts.size() >= CarModel.MAX_PARTS) { say(rally3d.core.Lang.t("Limit: ") + CarModel.MAX_PARTS + rally3d.core.Lang.t(" parts")); return; }
        push();
        CarModel.Part p = new CarModel.Part();
        CarModel.Part c = cur();
        p.type = kind == 2 ? 1 : 0;
        p.name = (kind == 0 ? rally3d.core.Lang.t("Box ") : kind == 1 ? rally3d.core.Lang.t("Wedge ") : rally3d.core.Lang.t("Prism ")) + (md.parts.size() + 1);
        p.cx = 0; p.cy = md.wheels ? 0.54f : 1.0f; p.cz = 0;
        if (c != null) { p.cx = c.cx; p.cy = c.cy; p.cz = c.cz; }
        p.sx = 0.8f; p.sy = 0.5f; p.sz = 0.8f;
        if (kind == 1) { p.topW = 80; p.topL = 40; }
        if (kind == 2) { p.axis = md.wheels ? 0 : 1; p.sides = 10; }
        p.mat = CarModel.solid(PALETTE[(9 + nextColor++ * 3) % PALETTE.length]);
        md.parts.add(p);
        sel = md.parts.size() - 1;
        stripToSel = true;
        md.touch();
        mode = M_SHAPE; panelScroll = 0;
     } finally { rally3d.core.Trace.out(655, $t); } }

    private void dupPart() { /*T*/ final long $t = rally3d.core.Trace.in(656); try {
        CarModel.Part c = cur();
        if (c == null) return;
        if (md.parts.size() >= CarModel.MAX_PARTS) { say(rally3d.core.Lang.t("Limit: ") + CarModel.MAX_PARTS + rally3d.core.Lang.t(" parts")); return; }
        push();
        CarModel.Part p = c.copy();
        p.name = c.name + " 2";
        p.cx += 0.3f;
        md.parts.add(sel + 1, p);
        sel++;
        stripToSel = true;
        md.touch();
     } finally { rally3d.core.Trace.out(656, $t); } }

    private void delPart() { /*T*/ final long $t = rally3d.core.Trace.in(657); try {
        if (md.parts.size() <= 1) { say(rally3d.core.Lang.t("A car needs at least one part")); return; }
        push();
        md.parts.remove(sel);
        clampSel();
        md.touch();
     } finally { rally3d.core.Trace.out(657, $t); } }

    private void toggleMirror() { /*T*/ final long $t = rally3d.core.Trace.in(658); try { CarModel.Part p = cur(); if (p != null) { push(); p.mirror = !p.mirror; md.touch(); say(p.mirror ? rally3d.core.Lang.t("Mirrored copy on the other side") : rally3d.core.Lang.t("Mirror off")); }  } finally { rally3d.core.Trace.out(658, $t); } }

    private void renamePart() { /*T*/ final long $t = rally3d.core.Trace.in(659); try {
        CarModel.Part p = cur();
        if (p == null) return;
        String v = askText(rally3d.core.Lang.t("Part name"), "", p.name, 20);
        if (v == null || v.length() == 0) return;
        push();
        p.name = v.replace('|', ' ');
        md.touch();
     } finally { rally3d.core.Trace.out(659, $t); } }

    private void editInfo(int which) { /*T*/ final long $t = rally3d.core.Trace.in(660); try {
        String[] titles = {rally3d.core.Lang.t("Car name"), rally3d.core.Lang.t("Author"), rally3d.core.Lang.t("Description")};
        String cur = which == 0 ? md.name : which == 1 ? md.author : md.desc;
        String v = askText(titles[which], "", cur, which == 2 ? 60 : 24);
        if (v == null) return;
        push();
        if (which == 0) md.name = v.length() == 0 ? "My car" : v; else if (which == 1) md.author = v; else md.desc = v;
        md.touch();
     } finally { rally3d.core.Trace.out(660, $t); } }

    // ------------------------------------------------------------------ painting

    private boolean gridHit(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(661); try {
        return gridCell > 0 && x >= gridX && y >= gridY && x < gridX + gridCell * 16 && y < gridY + gridCell * 16 && y >= TB && cur() != null;
     } finally { rally3d.core.Trace.out(661, $t); } }

    private void paintBegin(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(662); try {
        CarModel.Part p = cur();
        int cx = (x - gridX) / gridCell, cy = (y - gridY) / gridCell;
        int i = cy * 16 + cx;
        if (tool == 2) { color1 = p.mat[i]; return; }
        push();
        if (tool == 1) flood(p.mat, cx, cy, color1); else p.mat[i] = color1;
        md.touch();
     } finally { rally3d.core.Trace.out(662, $t); } }

    private void paintMove(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(663); try {
        if (tool != 0 || !gridHit(x, y)) return;
        CarModel.Part p = cur();
        int i = ((y - gridY) / gridCell) * 16 + (x - gridX) / gridCell;
        if (p.mat[i] != color1) { p.mat[i] = color1; md.touch(); }
     } finally { rally3d.core.Trace.out(663, $t); } }

    private static void flood(int[] m, int x, int y, int c) { /*T*/ final long $t = rally3d.core.Trace.in(664); try {
        int target = m[y * 16 + x];
        if (target == c) return;
        int[] stack = new int[16 * 16 * 4];
        int sp = 0;
        stack[sp++] = y * 16 + x;
        while (sp > 0) {
            int q = stack[--sp];
            if (m[q] != target) continue;
            m[q] = c;
            int qx = q % 16, qy = q / 16;
            if (qx > 0 && sp < stack.length - 4) stack[sp++] = q - 1;
            if (qx < 15 && sp < stack.length - 4) stack[sp++] = q + 1;
            if (qy > 0 && sp < stack.length - 4) stack[sp++] = q - 16;
            if (qy < 15 && sp < stack.length - 4) stack[sp++] = q + 16;
        }
     } finally { rally3d.core.Trace.out(664, $t); } }

    private void applyPattern(int k) { /*T*/ final long $t = rally3d.core.Trace.in(665); try {
        CarModel.Part p = cur();
        if (p == null) return;
        push();
        CarModel.pattern(p.mat, k, color1, color2);
        md.touch();
     } finally { rally3d.core.Trace.out(665, $t); } }

    private void hexColor() { /*T*/ final long $t = rally3d.core.Trace.in(666); try {
        String v = askText(rally3d.core.Lang.t("Colour (hex RRGGBB)"), "#", String.format("%06X", color1 & 0xFFFFFF), 7);
        if (v == null) return;
        v = v.trim();
        if (v.startsWith("#")) v = v.substring(1);
        try {
            if (v.length() != 6) { say(rally3d.core.Lang.t("Use 6 hex digits, e.g. 3A7BD5")); return; }
            color1 = 0xFF000000 | Integer.parseInt(v, 16);
        } catch (NumberFormatException e) { RLog.caught("CarEditor.hexColor", e); say("Not a hex colour: " + v); }
     } finally { rally3d.core.Trace.out(666, $t); } }

    private void openImport() { /*T*/ final long $t = rally3d.core.Trace.in(667); try {
        File dir = Painter.importDir();
        File[] all = dir.listFiles();
        ArrayList<File> pngs = new ArrayList<File>();
        if (all != null) for (File fl : all) if (fl.isFile() && fl.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) pngs.add(fl);
        java.util.Collections.sort(pngs);
        if (pngs.isEmpty()) { say(rally3d.core.Lang.t("No PNG files. Put them into ") + dir.getPath().replace('\\', '/')); return; }
        while (pngs.size() > 60) pngs.remove(pngs.size() - 1);
        importFiles = pngs.toArray(new File[0]);
        String[] names = new String[importFiles.length];
        for (int i = 0; i < names.length; i++) names[i] = importFiles[i].getName();
        showList(rally3d.core.Lang.t("IMPORT PNG INTO THIS PART"), names, LK_IMPORT);
     } finally { rally3d.core.Trace.out(667, $t); } }

    private void importPng(int i) { /*T*/ final long $t = rally3d.core.Trace.in(668); try {
        CarModel.Part p = cur();
        if (p == null || importFiles == null || i < 0 || i >= importFiles.length) return;
        File fl = importFiles[i];
        if (fl.length() > 1500 * 1024L) { say(rally3d.core.Lang.t("PNG too big (max 1.5 MB): ") + fl.getName()); return; }
        try {
            byte[] data = java.nio.file.Files.readAllBytes(fl.toPath());
            int[] wh = new int[2];
            int[] src = PngCodec.decode(data, wh);
            if (src == null) { say(rally3d.core.Lang.t("Not a usable PNG (max 1024x1024): ") + fl.getName()); return; }
            int[] sm = PngCodec.scale(src, wh[0], wh[1], CarModel.MAT, CarModel.MAT);
            push();
            for (int k = 0; k < sm.length; k++) {
                int a = sm[k] >>> 24;
                p.mat[k] = a < 128 ? color2 : 0xFF000000 | (sm[k] & 0xFFFFFF);
            }
            md.touch();
            say(rally3d.core.Lang.t("Imported ") + fl.getName() + " (16x16)");
        } catch (IOException e) { RLog.caught("CarEditor.importPng", e); say("Cannot read " + fl.getName()); }
     } finally { rally3d.core.Trace.out(668, $t); } }

    // ------------------------------------------------------------------ file menu, save / export

    private void fileMenu() { /*T*/ final long $t = rally3d.core.Trace.in(669); try {
        showList(rally3d.core.Lang.t("CAR BUILDER"), new String[]{rally3d.core.Lang.t("SAVE TO GAME"), rally3d.core.Lang.t("EXPORT ZIP..."), rally3d.core.Lang.t("NEW (TEMPLATE)..."), rally3d.core.Lang.t("OPEN INSTALLED..."), rally3d.core.Lang.t("HELP"), rally3d.core.Lang.t("EXIT")}, LK_FILE);
     } finally { rally3d.core.Trace.out(669, $t); } }

    void showList(String title, String[] items, int kind) { listTitle = title; listItems = items; listKind = kind; listScroll = 0; }
    private void listClose() { listItems = null; listKind = 0; }
    private void showText(String title, String[] lines) { textTitle = title; textLines = lines; textScroll = 0; }

    private void listPick(int i) { /*T*/ final long $t = rally3d.core.Trace.in(670); try {
        int kind = listKind;
        listClose();
        switch (kind) {
            case LK_FILE:
                switch (i) {
                    case 0: save(); break;
                    case 1: exportZip(); break;
                    case 2: showList(rally3d.core.Lang.t("NEW FROM TEMPLATE"), rally3d.core.Lang.tl(CarModel.TEMPLATES), LK_TEMPLATE); break;
                    case 3: openInstalledList(); break;
                    case 4: showText(rally3d.core.Lang.t("HELP"), rally3d.core.Lang.tl(HELP)); break;
                    default: pendKind = 3; confirmThen(0); break;
                }
                break;
            case LK_TEMPLATE: pendKind = 1; confirmThen(i); break;
            case LK_OPEN: if (installedIdx != null && i < installedIdx.length) { pendKind = 2; confirmThen(installedIdx[i]); } break;
            case LK_IMPORT: importPng(i); break;
            case LK_CONFIRM:
                if (i == 0) { if (!save()) return; proceed(); }
                else if (i == 1) proceed();
                break;
            default: break;
        }
     } finally { rally3d.core.Trace.out(670, $t); } }

    private void confirmThen(int arg) { /*T*/ final long $t = rally3d.core.Trace.in(671); try {
        pendArg = arg;
        if (!dirty()) { proceed(); return; }
        showList(rally3d.core.Lang.t("UNSAVED CHANGES"), new String[]{rally3d.core.Lang.t("SAVE, THEN CONTINUE"), rally3d.core.Lang.t("DISCARD CHANGES"), rally3d.core.Lang.t("CANCEL")}, LK_CONFIRM);
     } finally { rally3d.core.Trace.out(671, $t); } }

    private void proceed() { /*T*/ final long $t = rally3d.core.Trace.in(672); try {
        switch (pendKind) {
            case 1: md = CarModel.template(pendArg); loaded(rally3d.core.Lang.t("Template: ") + md.name); break;
            case 2: {
                java.util.List<AddonPack> pl = AddonManager.snapshot();
                AddonPack p = pendArg >= 0 && pendArg < pl.size() ? pl.get(pendArg) : null;
                CarModel m = CarModel.fromPack(p);
                if (m == null) { say(rally3d.core.Lang.t("This add-on has no editable project")); return; }
                md = m; loaded(rally3d.core.Lang.t("Opened: ") + md.name);
                break;
            }
            default: exit = true; break;
        }
     } finally { rally3d.core.Trace.out(672, $t); } }

    private void loaded(String msg) { /*T*/ final long $t = rally3d.core.Trace.in(673); try {
        undo.clear(); redo.clear();
        sel = 0; stripScroll = 0; panelScroll = 0; stripToSel = true;
        savedRev = md.rev;
        resetView();
        say(msg);
     } finally { rally3d.core.Trace.out(673, $t); } }

    private void openInstalledList() { /*T*/ final long $t = rally3d.core.Trace.in(674); try {
        AddonManager.rescan();
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<Integer> idx = new ArrayList<Integer>();
        java.util.List<AddonPack> plist = AddonManager.snapshot();
        for (int i = 0; i < plist.size(); i++) {
            AddonPack p = plist.get(i);
            if (p.rcm != null && p.error == null) { names.add((p.wheelsOnly ? "[W] " : "[C] ") + p.name); idx.add(Integer.valueOf(i)); }
        }
        if (names.isEmpty()) { say(rally3d.core.Lang.t("No cars made with the Car Builder yet. SAVE one first.")); return; }
        installedIdx = new int[idx.size()];
        for (int i = 0; i < installedIdx.length; i++) installedIdx[i] = idx.get(i).intValue();
        showList(rally3d.core.Lang.t("OPEN INSTALLED ([C] car, [W] wheels)"), names.toArray(new String[0]), LK_OPEN);
     } finally { rally3d.core.Trace.out(674, $t); } }

    boolean save() { /*T*/ final long $t = rally3d.core.Trace.in(675); try {
        try {
            byte[] zip = md.toZip();
            AddonPack p = CarModel.install(zip);
            g.refreshLocalCars();
            g.evictUnusedAddonCars();
            savedRev = md.rev;
            say(rally3d.core.Lang.t("Saved ") + p.file + " (" + (zip.length / 1024 + 1) + rally3d.core.Lang.t(" KB) - ") + (md.wheels ? rally3d.core.Lang.t("wheels are ON") : rally3d.core.Lang.t("pick it in the car list")));
            return true;
        } catch (IOException e) { rally3d.core.RLog.caught("CarEditor.save", e);
            RLog.caught("CarEditor.save", e);
            say(rally3d.core.Lang.t("Cannot save: ") + e.getMessage());
            return false;
        }
     } finally { rally3d.core.Trace.out(675, $t); } }

    void exportZip() { /*T*/ final long $t = rally3d.core.Trace.in(676); try {
        byte[] zip;
        try { zip = md.toZip(); }
        catch (IOException e) { RLog.caught("CarEditor.exportZip", e); say("Cannot export: " + e.getMessage()); return; }
        javax.microedition.lcdui.Canvas.rawPointer = false;
        g.carEditor = null;
        String saved;
        try { saved = rally3d.desktop.Platform.exportFile(MapData.slug(md.name) + ".zip", zip); }
        finally { reenter(); }
        if (saved == null) say(rally3d.core.Lang.t("Export cancelled"));
        else { say("Exported " + saved + " (" + (zip.length / 1024 + 1) + " KB). Add it in Add-ons > + ZIP"); RLog.i("car", "exported car zip " + saved + " " + zip.length + " bytes"); }
     } finally { rally3d.core.Trace.out(676, $t); } }

    private String askText(String title, String prefix, String initial, int max) { /*T*/ final long $t = rally3d.core.Trace.in(677); try {
        javax.microedition.lcdui.Canvas.rawPointer = false;
        g.carEditor = null;
        String r;
        try { r = g.editTextInGame(title, prefix, initial, max); }
        finally { reenter(); }
        return r == null ? null : r.trim();
     } finally { rally3d.core.Trace.out(677, $t); } }

    private void reenter() { /*T*/ final long $t = rally3d.core.Trace.in(678); try {
        g.beginMenu(rally3d.core.Lang.t("Car Builder"));
        g.menuHeaderStatic = false;
        g.menuSlide = 0;
        g.screenMode = 2;
        g.carEditor = this;
        g.snapshotKeys();
        javax.microedition.lcdui.Canvas.rawPointer = true;
        g.clearRaw();
        for (int i = 0; i < 3; i++) pid[i] = -1;
        hActive = -1; hSlot = -1;
        pinchDist = 0;
        lastFrame = System.nanoTime();
     } finally { rally3d.core.Trace.out(678, $t); } }

    // ------------------------------------------------------------------ live mesh

    private void ensureBuilt() { /*T*/ final long $t = rally3d.core.Trace.in(679); try {
        if (built != null && builtRev == md.rev) return;
        builtRev = md.rev;
        try {
            CarModel.Built b = md.build();
            Mesh3D m = new Mesh3D();
            m.loadBytes(b.syn);
            Texture2D t = g.textureFromBytes(b.png);
            m.setTexture(t);
            m.setDepthEnabled(!g.carsDepthDisabled);
            Texture2D old = tex;
            built = b; mesh = m; tex = t; buildError = null;
            if (old != null) g.g3d.releaseTexture(old);
        } catch (IOException e) { rally3d.core.RLog.caught("CarEditor.ensureBuilt", e);
            RLog.caught("CarEditor.ensureBuilt", e);
            buildError = e.getMessage();
            built = null;
        } catch (RuntimeException e) { rally3d.core.RLog.caught("CarEditor.ensureBuilt", e);
            RLog.caught("CarEditor.ensureBuilt", e);
            buildError = String.valueOf(e);
            built = null;
        }
     } finally { rally3d.core.Trace.out(679, $t); } }

    // ------------------------------------------------------------------ drawing helpers

    private int bh() { return sh + 10; }
    private void fill(Graphics gr, int x, int y, int w, int h, int argb) { /*T*/ final long $t = rally3d.core.Trace.in(680); try {
        if (w <= 0 || h <= 0) return;
        if ((argb >>> 24) == 255) { gr.setColor(argb & 0xFFFFFF); gr.fillRect(x, y, w, h); } else gr.fillRectAlpha(x, y, w, h, argb);
     } finally { rally3d.core.Trace.out(680, $t); } }
    private int textW(String s) { return g.fontSmall.stringWidth(s); }
    private void text(Graphics gr, String s, int x, int y, int color) { /*T*/ final long $t = rally3d.core.Trace.in(681); try {
        gr.setFont(g.fontSmall);
        gr.setColor(0x000000);
        gr.drawString(s, x + 1, y + 1, Graphics.TOP | Graphics.LEFT);
        gr.setColor(color & 0xFFFFFF);
        gr.drawString(s, x, y, Graphics.TOP | Graphics.LEFT);
     } finally { rally3d.core.Trace.out(681, $t); } }
    private void textC(Graphics gr, String s, int cx, int y, int color) { text(gr, s, cx - textW(s) / 2, y, color); }
    private void frame(Graphics gr, int x, int y, int w, int h, int argb) { /*T*/ final long $t = rally3d.core.Trace.in(682); try {
        gr.setColor(argb & 0xFFFFFF);
        gr.drawLine(x, y, x + w - 1, y); gr.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        gr.drawLine(x, y, x, y + h - 1); gr.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
     } finally { rally3d.core.Trace.out(682, $t); } }
    private String clipText(String s, int maxW) {
        while (s.length() > 1 && textW(s) > maxW) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void addHit(int x, int y, int w, int h, int id, int arg) { /*T*/ final long $t = rally3d.core.Trace.in(683); try {
        if (clipY0 >= 0) {
            int y0 = Math.max(y, clipY0), y1 = Math.min(y + h, clipY1);
            if (y1 - y0 < 4) return;
            y = y0; h = y1 - y0;
        }
        btns.add(new int[]{x, y, w, h, id, arg});
     } finally { rally3d.core.Trace.out(683, $t); } }

    private void button(Graphics gr, int x, int y, int w, int h, String label, int id, int arg, boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(684); try {
        fill(gr, x, y, w, h, on ? BTN_ON : BTN);
        frame(gr, x, y, w, h, on ? WHITE : 0xFF5A7088);
        textC(gr, clipText(label, w - 4), x + w / 2, y + (h - sh) / 2, on ? YEL : WHITE);
        addHit(x, y, w, h, id, arg);
     } finally { rally3d.core.Trace.out(684, $t); } }

    private void box(Graphics gr, int x, int y, int w, int h) {
        fill(gr, x - 1, y - 1, w + 2, h + 2, BLACK);
        fill(gr, x, y, w, h, 0xFF2E3E4C);
        frame(gr, x, y, w, h, WHITE);
    }

    // ------------------------------------------------------------------ frame

    void draw(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(685); try {
        W = g.width; H = g.height; fh = g.fontBoldHeight; sh = g.fontSmallHeight;
        TB = Math.max(16, sh + 8);
        SB = Math.max(22, sh + 12);
        PW = Math.max(112, Math.min(150, W * 32 / 100));
        vx = 0; vy = TB; vw = W - PW; vh = H - TB - SB;
        btns.clear();
        long now = System.nanoTime();
        float dt = Math.max(0f, Math.min(0.1f, (now - lastFrame) / 1e9f));
        lastFrame = now;
        if (spin && !lockMode) cam.yaw += dt * 40f;
        gr.setClip(0, 0, W, H);
        fill(gr, 0, 0, W, H, BG);
        ensureBuilt();
        drawView(gr);
        gr.setClip(0, 0, W, H);
        drawViewTools(gr);
        drawStrip(gr);
        drawPanel(gr);
        drawTopBar(gr);
        drawOverlays(gr);
        if (toast != null && System.currentTimeMillis() < toastUntil) {
            int tw = Math.min(W - 8, textW(toast) + 12);
            int tx = (W - tw) / 2, ty = H - SB - sh - 12;
            fill(gr, tx, ty, tw, sh + 6, 0xE0101820);
            frame(gr, tx, ty, tw, sh + 6, YEL);
            textC(gr, clipText(toast, tw - 8), W / 2, ty + 3, YEL);
        }
     } finally { rally3d.core.Trace.out(685, $t); } }

    private void drawTopBar(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(686); try {
        fill(gr, 0, 0, W, TB, PANEL);
        gr.setColor(0xFFFFFF);
        gr.drawLine(0, TB - 1, W, TB - 1);
        String[] labels = {rally3d.core.Lang.t("FILE"), rally3d.core.Lang.t("UNDO"), rally3d.core.Lang.t("REDO"), rally3d.core.Lang.t("SAVE"), "?"};
        int[] ids = {A_FILE, A_UNDO, A_REDO, A_SAVE, A_HELP};
        int bw = Math.max(26, Math.min(60, (W - PW) / 6)), x = 2;
        for (int i = 0; i < 5; i++) {
            int w = i == 4 ? 24 : bw;
            button(gr, x, 1, w, TB - 3, labels[i], ids[i], 0, i == 3 && dirty());
            x += w + 2;
        }
        String t = md.name + (md.wheels ? rally3d.core.Lang.t(" (wheels)") : "") + (dirty() ? " *" : "") + (built != null ? "   " + built.tris + rally3d.core.Lang.t(" tris") : "");
        int tx = x + 6;
        if (tx < W - 20) text(gr, clipText(t, W - tx - 4), tx, (TB - sh) / 2, YEL);
     } finally { rally3d.core.Trace.out(686, $t); } }

    private void drawViewTools(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(687); try {
        String[] labels = {"+", "-", rally3d.core.Lang.t("RESET"), rally3d.core.Lang.t("SPIN"), rally3d.core.Lang.t("GRID"), rally3d.core.Lang.t("GUIDE"), rally3d.core.Lang.t("LOCK")};
        int[] ids = {A_ZIN, A_ZOUT, A_FIT, A_SPIN, A_GRID, A_GUIDE, A_LOCK};
        boolean[] on = {false, false, false, spin, showGrid, showGuide, lockMode};
        int n = labels.length, bw = Math.max(24, Math.min(54, (vw - 6) / n)), x = vx + 3, y = vy + 3, h = Math.max(14, sh + 4);
        for (int i = 0; i < n; i++) { button(gr, x, y, bw, h, labels[i], ids[i], 0, on[i]); x += bw + 2; }
     } finally { rally3d.core.Trace.out(687, $t); } }

    private void drawView(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(688); try {
        cam.vx = vx; cam.vy = vy; cam.vw = vw; cam.vh = vh;
        gr.setClip(vx, vy, vw, vh);
        fill(gr, vx, vy, vw, vh, 0xFF3A4C5E);
        fill(gr, vx, vy + vh / 2, vw, vh - vh / 2, 0xFF2E3B48);
        float[] a = new float[3], b = new float[3];
        if (showGrid) {
            for (int i = -6; i <= 6; i++) {
                int col = i == 0 ? 0xFF607890 : 0xFF3F5266;
                if (cam.project(i, 0, -6, a) && cam.project(i, 0, 6, b)) { gr.setColor(col & 0xFFFFFF); gr.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]); }
                if (cam.project(-6, 0, i, a) && cam.project(6, 0, i, b)) { gr.setColor(col & 0xFFFFFF); gr.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]); }
            }
            if (cam.project(0, 0, 6.6f, a)) textC(gr, rally3d.core.Lang.t("FRONT +Z"), (int) a[0], (int) a[1] - sh / 2, 0xFF8FB0D0);
        }
        if (built == null) {
            textC(gr, buildError == null ? "..." : buildError, vx + vw / 2, vy + vh / 2, RED);
            return;
        }
        try { draw3d(gr); }
        catch (RuntimeException e) { RLog.caught("CarEditor.drawView", e); buildError = String.valueOf(e); }
        gr.setClip(vx, vy, vw, vh);
        drawSelection(gr);
        gr.setClip(vx, vy, vw, vh);
        if (lockMode) {
            frame(gr, vx, vy, vw, vh, 0xFFE030);
            frame(gr, vx + 1, vy + 1, vw - 2, vh - 2, 0xFFE030);
            text(gr, rally3d.core.Lang.t("LOCK"), vx + 6, vy + vh - sh - 4, 0xFFE030);
        }
        if (buildError != null) textC(gr, buildError, vx + vw / 2, vy + vh - sh - 6, RED);
     } finally { rally3d.core.Trace.out(688, $t); } }

    private void camTransform(Transform t) { /*T*/ final long $t = rally3d.core.Trace.in(689); try {
        t.setIdentity();
        t.postTranslate(cam.panX, cam.panY, -cam.dist);
        t.postRotate(cam.pitch, 1f, 0f, 0f);
        t.postRotate(cam.yaw, 0f, 1f, 0f);
        t.postTranslate(0f, -cam.tgtY, 0f);
     } finally { rally3d.core.Trace.out(689, $t); } }

    private void draw3d(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(690); try {
        g.setViewport(vx, vy, vw, vh);
        g.setupCamera();
        gr.setClip(vx, vy, vw, vh);
        g.g3d.bindTarget((Object) gr, true, 0);
        g.g3d.beginBillboardBatch();
        g.g3d.clear(g.background);
        camTransform(base);
        if (!md.wheels) {
            mesh.render(g.g3d, base);
            g.applyWheelLook(g.stockCars[0]);
            float[][] wp = {{1.05f, 1.5f}, {-1.05f, 1.5f}, {1.05f, -1.6f}, {-1.05f, -1.6f}};
            for (int i = 0; showGuide && i < 4; i++) {
                tmp.set(base);
                tmp.postTranslate(wp[i][0], -0.1f, wp[i][1]);
                g.curWheelMesh.render(g.g3d, tmp);
            }
        } else {
            mesh.render(g.g3d, base);
            if (showGuide) {
                // stock car placed so that its front-right wheel sits exactly where the wheel being built is
                tmp.set(base);
                tmp.postTranslate(-1.05f, 0.1f, -1.5f);
                g.stockCars[0].mesh.render(g.g3d, tmp);
                float[][] off = {{-2.1f, 0f}, {0f, -3.1f}, {-2.1f, -3.1f}};
                for (int i = 0; i < 3; i++) {
                    tmp.set(base);
                    tmp.postTranslate(off[i][0], 0f, off[i][1]);
                    mesh.render(g.g3d, tmp);
                }
            }
        }
        g.g3d.releaseTarget();
     } finally { rally3d.core.Trace.out(690, $t); } }

    private void drawSelection(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(691); try {
        CarModel.Part p = cur();
        if (p == null || built == null || sel >= built.partCount.length || built.partCount[sel] == 0) return;
        float[] a = new float[3], b = new float[3];
        float[] pos = built.pos;
        int first = built.partFirst[sel], cnt = built.partCount[sel];
        gr.setColor(0xFFFFAA);
        for (int t = first; t < first + cnt; t++) {
            for (int e = 0; e < 3; e++) {
                int i0 = built.idx[t * 3 + e] * 3, i1 = built.idx[t * 3 + (e + 1) % 3] * 3;
                if (cam.project(pos[i0], pos[i0 + 1], pos[i0 + 2], a) && cam.project(pos[i1], pos[i1 + 1], pos[i1 + 2], b)) gr.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
            }
        }
        // axes at the part centre
        float[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        int[] cols = {0xFF5050, 0x50E050, 0x5090FF};
        if (cam.project(p.cx, p.cy, p.cz, a)) {
            for (int k = 0; k < 3; k++) {
                if (!cam.project(p.cx + axes[k][0] * 0.8f, p.cy + axes[k][1] * 0.8f, p.cz + axes[k][2] * 0.8f, b)) continue;
                gr.setColor(cols[k]);
                gr.drawLine((int) a[0], (int) a[1], (int) b[0], (int) b[1]);
                if (dragAxis == k && mode == M_SHAPE) gr.drawLine((int) a[0] + 1, (int) a[1], (int) b[0] + 1, (int) b[1]);
            }
        }
        // stretch handles on the 6 faces
        float[] hw = new float[3];
        int hs = Math.max(5, sh / 2 + 2);
        for (int h = 0; h < 6; h++) {
            handleWorld(p, h, hw);
            if (!cam.project(hw[0], hw[1], hw[2], a)) continue;
            int hx = (int) a[0], hy = (int) a[1];
            boolean act = h == hActive;
            int sz = act ? hs + 3 : hs;
            gr.setColor(0x000000);
            gr.fillRect(hx - sz - 1, hy - sz - 1, sz * 2 + 3, sz * 2 + 3);
            gr.setColor(act ? 0xFFE030 : cols[h >> 1]);
            gr.fillRect(hx - sz, hy - sz, sz * 2 + 1, sz * 2 + 1);
            if (act) {
                float v = partSize(p)[h >> 1];
                text(gr, rally3d.core.Lang.t(HN[h >> 1]) + " " + fl(v), Math.max(vx + 2, Math.min(vx + vw - 70, hx + sz + 4)), Math.max(vy + 2, hy - sh - 4), 0xFFFFAA);
            }
        }
     } finally { rally3d.core.Trace.out(691, $t); } }

    private void drawStrip(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(692); try {
        int x0 = 0, y0 = H - SB, w = W - PW;
        fill(gr, x0, y0, w, SB, PANEL);
        gr.setColor(0xFFFFFF);
        gr.drawLine(x0, y0, x0 + w, y0);
        int total = 4;
        int[] cw = new int[md.parts.size()];
        for (int i = 0; i < cw.length; i++) { cw[i] = textW(md.parts.get(i).name) + 14; total += cw[i] + 2; }
        stripContent = total;
        if (stripToSel) {
            int xs = 4;
            for (int i = 0; i < sel && i < cw.length; i++) xs += cw[i] + 2;
            int xe = xs + (sel < cw.length ? cw[sel] : 0);
            if (xs - stripScroll < 0) stripScroll = xs;
            if (xe - stripScroll > w - 4) stripScroll = xe - (w - 4);
            stripToSel = false;
        }
        stripScroll = Math.max(0, Math.min(Math.max(0, stripContent - w), stripScroll));
        gr.setClip(x0, y0, w, SB);
        int x = 4 - stripScroll;
        for (int i = 0; i < cw.length; i++) {
            CarModel.Part p = md.parts.get(i);
            if (x + cw[i] > 0 && x < w) {
                fill(gr, x, y0 + 2, cw[i], SB - 4, i == sel ? BTN_ON : BTN);
                frame(gr, x, y0 + 2, cw[i], SB - 4, i == sel ? WHITE : 0xFF5A7088);
                fill(gr, x + 2, y0 + 4, 4, SB - 8, p.mat[(CarModel.MAT / 2) * CarModel.MAT + CarModel.MAT / 2]);
                text(gr, p.name, x + 9, y0 + (SB - sh) / 2, p.hidden ? DIM : i == sel ? YEL : WHITE);
                addHit(Math.max(x, x0), y0, Math.min(cw[i], w), SB, A_STRIP + i, 0);
            }
            x += cw[i] + 2;
        }
        gr.setClip(0, 0, W, H);
     } finally { rally3d.core.Trace.out(692, $t); } }

    // ------------------------------------------------------------------ panel

    private int pyy, pxx, pww;   // layout cursor inside the scrolling panel

    private void drawPanel(Graphics gr) {
        int x0 = W - PW;
        fill(gr, x0, TB, PW, H - TB, PANEL);
        gr.setColor(0xFFFFFF);
        gr.drawLine(x0, TB, x0, H);
        int b = bh();
        int tabW = (PW - 8) / 2;
        for (int i = 0; i < 4; i++) button(gr, x0 + 3 + (i % 2) * (tabW + 2), TB + 3 + (i / 2) * (b + 2), tabW, b, rally3d.core.Lang.t(MODES[i]), A_MODE + i, 0, mode == i);
        int top = TB + 3 + 2 * (b + 2) + 2;
        gr.setClip(x0, top, PW, H - top);
        clipY0 = top; clipY1 = H;
        pxx = x0 + 4; pww = PW - 8; pyy = top - panelScroll;
        int startY = pyy;
        CarModel.Part p = cur();
        if (p != null) {
            label(gr, (sel + 1) + "/" + md.parts.size() + "  " + p.name);
        }
        switch (mode) {
            case M_PARTS: panelParts(gr, p); break;
            case M_SHAPE: panelShape(gr, p); break;
            case M_PAINT: panelPaint(gr, p); break;
            default: panelCar(gr); break;
        }
        panelContent = pyy - startY + 6;
        clipY0 = -1; clipY1 = -1;
        gr.setClip(0, 0, W, H);
        int view = H - top;
        if (panelContent > view) {
            int sbh = Math.max(10, view * view / panelContent), sby = top + (view - sbh) * panelScroll / Math.max(1, panelContent - view);
            fill(gr, W - 3, sby, 2, sbh, 0xB0FFFFAA);
        }
        panelScroll = Math.max(0, Math.min(Math.max(0, panelContent - view), panelScroll));
    }

    private void label(Graphics gr, String s) {
        text(gr, clipText(s, pww), pxx, pyy, YEL);
        pyy += sh + 4;
    }

    private void note(Graphics gr, String s, int color) { /*T*/ final long $t = rally3d.core.Trace.in(693); try {
        ArrayList<String> rows = new ArrayList<String>();
        wrap(s, pww, rows);
        for (String r : rows) { text(gr, r, pxx, pyy, color); pyy += sh + 1; }
        pyy += 3;
     } finally { rally3d.core.Trace.out(693, $t); } }

    private void pbtn(Graphics gr, String s, int id, int arg, boolean on) { button(gr, pxx, pyy, pww, bh(), s, id, arg, on); pyy += bh() + 3; }

    private void pbtn2(Graphics gr, String a, int ida, int arga, boolean ona, String b, int idb, int argb, boolean onb) { /*T*/ final long $t = rally3d.core.Trace.in(694); try {
        int w = (pww - 3) / 2;
        button(gr, pxx, pyy, w, bh(), a, ida, arga, ona);
        button(gr, pxx + w + 3, pyy, pww - w - 3, bh(), b, idb, argb, onb);
        pyy += bh() + 3;
     } finally { rally3d.core.Trace.out(694, $t); } }

    /** "-  LABEL value  +" row: tap the middle to type a value. */
    private void pstep(Graphics gr, String label, String value, int id) { /*T*/ final long $t = rally3d.core.Trace.in(695); try {
        int h = 2 * sh + 6, bw = Math.max(18, sh + 12), mid = pww - 2 * bw - 4;
        button(gr, pxx, pyy, bw, h, "-", id, -1, false);
        fill(gr, pxx + bw + 2, pyy, mid, h, 0xFF1E2A34);
        frame(gr, pxx + bw + 2, pyy, mid, h, 0xFF5A7088);
        text(gr, clipText(label, mid - 4), pxx + bw + 5, pyy + 1, DIM);
        textC(gr, clipText(value, mid - 4), pxx + bw + 2 + mid / 2, pyy + h - sh - 2, WHITE);
        addHit(pxx + bw + 2, pyy, mid, h, id, 0);
        button(gr, pxx + bw + mid + 4, pyy, bw, h, "+", id, 1, false);
        pyy += h + 3;
     } finally { rally3d.core.Trace.out(695, $t); } }

    private void panelParts(Graphics gr, CarModel.Part p) { /*T*/ final long $t = rally3d.core.Trace.in(696); try {
        pbtn(gr, rally3d.core.Lang.t("+ BOX"), A_ADD, 0, false);
        pbtn(gr, rally3d.core.Lang.t("+ WEDGE"), A_ADD + 1, 0, false);
        pbtn(gr, rally3d.core.Lang.t("+ PRISM"), A_ADD + 2, 0, false);
        if (p == null) return;
        pbtn2(gr, rally3d.core.Lang.t("COPY"), A_DUP, 0, false, rally3d.core.Lang.t("DELETE"), A_DEL, 0, false);
        pbtn2(gr, rally3d.core.Lang.t("MIRROR"), A_MIRROR, 0, p.mirror, rally3d.core.Lang.t("HIDE"), A_HIDE, 0, p.hidden);
        pbtn(gr, rally3d.core.Lang.t("RENAME"), A_RENAME, 0, false);
        note(gr, p.mirror ? rally3d.core.Lang.t("Mirror ON: the same part also appears on the other side (x = -x).") : rally3d.core.Lang.t("Tap a part in the 3D view or on the bottom strip."), DIM);
     } finally { rally3d.core.Trace.out(696, $t); } }

    private void panelShape(Graphics gr, CarModel.Part p) { /*T*/ final long $t = rally3d.core.Trace.in(697); try {
        if (p == null) return;
        label(gr, rally3d.core.Lang.t("STEP"));
        int w = (pww - 6) / 4;
        for (int i = 0; i < 4; i++) button(gr, pxx + i * (w + 2), pyy, w, bh(), String.valueOf(STEPS[i]).replace("0.", "."), A_STEP + i, 0, stepIdx == i);
        pyy += bh() + 4;
        label(gr, rally3d.core.Lang.t("POSITION"));
        pstep(gr, "X", fieldText(p, F_X), A_FIELD + F_X);
        pstep(gr, rally3d.core.Lang.t("Y (UP)"), fieldText(p, F_Y), A_FIELD + F_Y);
        pstep(gr, rally3d.core.Lang.t("Z (FRONT)"), fieldText(p, F_Z), A_FIELD + F_Z);
        pbtn2(gr, rally3d.core.Lang.t("CENTER X"), A_CENTERX, 0, false, rally3d.core.Lang.t("ON FLOOR"), A_ONFLOOR, 0, false);
        label(gr, rally3d.core.Lang.t("SIZE"));
        pstep(gr, rally3d.core.Lang.t("WIDTH"), fieldText(p, F_W), A_FIELD + F_W);
        pstep(gr, rally3d.core.Lang.t("HEIGHT"), fieldText(p, F_H), A_FIELD + F_H);
        pstep(gr, rally3d.core.Lang.t("LENGTH"), fieldText(p, F_L), A_FIELD + F_L);
        if (p.type == 0) {
            label(gr, rally3d.core.Lang.t("TAPER (WEDGE)"));
            pstep(gr, rally3d.core.Lang.t("TOP WIDTH %"), fieldText(p, F_TOPW), A_FIELD + F_TOPW);
            pstep(gr, rally3d.core.Lang.t("TOP LENGTH %"), fieldText(p, F_TOPL), A_FIELD + F_TOPL);
            pstep(gr, rally3d.core.Lang.t("TOP SHIFT X"), fieldText(p, F_SLX), A_FIELD + F_SLX);
            pstep(gr, rally3d.core.Lang.t("TOP SHIFT Z"), fieldText(p, F_SLZ), A_FIELD + F_SLZ);
        } else {
            label(gr, rally3d.core.Lang.t("PRISM"));
            pstep(gr, rally3d.core.Lang.t("AXIS"), fieldText(p, F_AXIS), A_FIELD + F_AXIS);
            pstep(gr, rally3d.core.Lang.t("SIDES"), fieldText(p, F_SIDES), A_FIELD + F_SIDES);
            pstep(gr, rally3d.core.Lang.t("END SIZE %"), fieldText(p, F_ENDR), A_FIELD + F_ENDR);
        }
        label(gr, rally3d.core.Lang.t("DRAG IN 3D VIEW"));
        int w2 = (pww - 6) / 4;
        String[] n = {"X", "Y", "Z", rally3d.core.Lang.t("OFF")};
        for (int i = 0; i < 4; i++) button(gr, pxx + i * (w2 + 2), pyy, w2, bh(), n[i], A_DRAG + i, 0, i == 3 ? dragAxis < 0 : dragAxis == i);
        pyy += bh() + 4;
     } finally { rally3d.core.Trace.out(697, $t); } }

    private void panelPaint(Graphics gr, CarModel.Part p) { /*T*/ final long $t = rally3d.core.Trace.in(698); try {
        if (p == null) return;
        gridCell = Math.max(3, (pww - 2) / 16);
        gridX = pxx + (pww - gridCell * 16) / 2;
        gridY = pyy;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int cy = gridY + y * gridCell;
            if (cy + gridCell < clipY0 || cy > clipY1) continue;
            fill(gr, gridX + x * gridCell, cy, gridCell, gridCell, p.mat[y * 16 + x]);
        }
        frame(gr, gridX - 1, gridY - 1, gridCell * 16 + 2, gridCell * 16 + 2, WHITE);
        pyy += gridCell * 16 + 6;
        String[] tn = {rally3d.core.Lang.t("PEN"), rally3d.core.Lang.t("FILL"), rally3d.core.Lang.t("PICK")};
        int w = (pww - 4) / 3;
        for (int i = 0; i < 3; i++) button(gr, pxx + i * (w + 2), pyy, w, bh(), tn[i], A_TOOL + i, 0, tool == i);
        pyy += bh() + 4;
        // current colours
        int sw = bh();
        fill(gr, pxx, pyy, sw, sw, color1); frame(gr, pxx, pyy, sw, sw, WHITE);
        fill(gr, pxx + sw + 3, pyy, sw, sw, color2); frame(gr, pxx + sw + 3, pyy, sw, sw, 0xFF8090A0);
        text(gr, "1   2", pxx + 2 * sw + 8, pyy + (sw - sh) / 2, DIM);
        pyy += sw + 3;
        int cols = 4, cell = Math.max(8, (pww - (cols - 1) * 2) / cols);
        for (int i = 0; i < PALETTE.length; i++) {
            int cx = pxx + (i % cols) * (cell + 2), cy = pyy + (i / cols) * (cell + 2);
            fill(gr, cx, cy, cell, cell, PALETTE[i]);
            frame(gr, cx, cy, cell, cell, PALETTE[i] == color1 ? YEL : 0xFF405060);
            addHit(cx, cy, cell, cell, A_SWATCH + i, 0);
        }
        pyy += ((PALETTE.length + cols - 1) / cols) * (cell + 2) + 3;
        pbtn(gr, rally3d.core.Lang.t("HEX COLOUR..."), A_HEX, 0, false);
        pbtn2(gr, rally3d.core.Lang.t("LIGHTER"), A_LIGHT, 0, false, rally3d.core.Lang.t("DARKER"), A_DARK, 0, false);
        pbtn2(gr, rally3d.core.Lang.t("SET COLOR 2"), A_C2, 0, false, rally3d.core.Lang.t("SWAP 1/2"), A_SWAP, 0, false);
        label(gr, rally3d.core.Lang.t("PATTERN (1 + 2)"));
        for (int i = 0; i < CarModel.PATTERNS.length; i += 2) {
            if (i + 1 < CarModel.PATTERNS.length) pbtn2(gr, rally3d.core.Lang.t(CarModel.PATTERNS[i]), A_PAT + i, 0, false, rally3d.core.Lang.t(CarModel.PATTERNS[i + 1]), A_PAT + i + 1, 0, false);
            else pbtn(gr, rally3d.core.Lang.t(CarModel.PATTERNS[i]), A_PAT + i, 0, false);
        }
        pbtn(gr, rally3d.core.Lang.t("IMPORT PNG..."), A_IMPORT, 0, false);
        pbtn2(gr, rally3d.core.Lang.t("COPY"), A_COPYM, 0, false, rally3d.core.Lang.t("PASTE"), A_PASTEM, 0, matClip != null);
        pbtn(gr, rally3d.core.Lang.t("ALL PARTS = COLOR 1"), A_ALLSOLID, 0, false);
        note(gr, rally3d.core.Lang.t("Texture of the selected part only. The game draws it on all sides of the part (light baked in)."), DIM);
     } finally { rally3d.core.Trace.out(698, $t); } }

    private void panelCar(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(699); try {
        pbtn(gr, rally3d.core.Lang.t("NAME: ") + md.name, A_NAME, 0, false);
        pbtn(gr, rally3d.core.Lang.t("AUTHOR: ") + (md.author.length() == 0 ? "-" : md.author), A_AUTHOR, 0, false);
        pbtn(gr, rally3d.core.Lang.t("NOTE: ") + (md.desc.length() == 0 ? "-" : md.desc), A_DESC, 0, false);
        pbtn(gr, rally3d.core.Lang.t("TYPE: ") + (md.wheels ? rally3d.core.Lang.t("WHEELS") : rally3d.core.Lang.t("CAR")), A_KIND, 0, false);
        pbtn(gr, rally3d.core.Lang.t("LIGHT BAKED: ") + (md.shade ? rally3d.core.Lang.t("ON") : rally3d.core.Lang.t("OFF")), A_SHADE, 0, md.shade);
        if (!md.wheels) {
            label(gr, rally3d.core.Lang.t("STATS"));
            pstep(gr, rally3d.core.Lang.t("BASE STOCK CAR"), String.valueOf(md.base), A_BASE);
            pstep(gr, rally3d.core.Lang.t("TOP SPEED"), String.valueOf(md.speed), A_STAT);
            pstep(gr, rally3d.core.Lang.t("ACCELERATION"), fl(md.accel), A_STAT + 1);
            pstep(gr, rally3d.core.Lang.t("STEERING"), fl(md.steer), A_STAT + 2);
            pstep(gr, rally3d.core.Lang.t("STEER ANGLE"), fl(md.steerMax), A_STAT + 3);
            pstep(gr, rally3d.core.Lang.t("GRIP"), fl(md.grip), A_STAT + 4);
            note(gr, rally3d.core.Lang.t("Speed shown in game = value x 1.5 km/h. 1.0 = stock handling."), DIM);
        }
        if (built != null) note(gr, md.visibleCount() + rally3d.core.Lang.t(" parts, ") + built.tris + rally3d.core.Lang.t(" triangles, texture ") + built.atlas + "x" + built.atlas, DIM);
        else if (buildError != null) note(gr, buildError, RED);
     } finally { rally3d.core.Trace.out(699, $t); } }

    // ------------------------------------------------------------------ overlays

    private void wrap(String s, int maxW, ArrayList<String> out) { /*T*/ final long $t = rally3d.core.Trace.in(700); try {
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (textW(t) > maxW && line.length() > 0) { out.add(line.toString()); line = new StringBuilder(word); }
            else line = new StringBuilder(t);
        }
        if (line.length() > 0) out.add(line.toString());
     } finally { rally3d.core.Trace.out(700, $t); } }

    private void drawOverlays(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(701); try {
        if (textLines != null) {
            int w = Math.min(W - 16, 420), lh = sh + 2;
            ArrayList<String> rows = new ArrayList<String>();
            for (String l : textLines) { wrap(l, w - 20, rows); rows.add(""); }
            int maxText = Math.max(lh, H - 8 - sh - 14 - bh() - 14);
            int textH = rows.size() * lh, viewH = Math.min(textH, maxText);
            int h = sh + 14 + viewH + bh() + 14;
            int x = (W - w) / 2, y = (H - h) / 2;
            fill(gr, 0, 0, W, H, 0xA0000000);
            box(gr, x, y, w, h);
            textC(gr, textTitle, W / 2, y + 5, YEL);
            int top = y + sh + 12, max = Math.max(0, textH - viewH);
            textScroll = Math.max(0, Math.min(max, textScroll));
            gr.setClip(x + 2, top, w - 4, viewH);
            for (int i = 0; i < rows.size(); i++) {
                int yy = top - textScroll + i * lh;
                if (yy + lh > top && yy < top + viewH) text(gr, rows.get(i), x + 8, yy, WHITE);
            }
            gr.setClip(0, 0, W, H);
            if (max > 0) { int bh2 = Math.max(10, viewH * viewH / textH); fill(gr, x + w - 5, top + (viewH - bh2) * textScroll / Math.max(1, max), 3, bh2, 0xB0FFFFAA); }
            button(gr, x + 6, y + h - bh() - 6, w - 12, bh(), rally3d.core.Lang.t("CLOSE"), A_TEXTOK, 0, true);
        } else if (listItems != null) {
            int w = Math.min(W - 16, 300), rh = bh() + 2;
            int h = Math.min(H - 20, listItems.length * rh + sh + 14);
            int x = (W - w) / 2, y = (H - h) / 2;
            fill(gr, 0, 0, W, H, 0xA0000000);
            box(gr, x, y, w, h);
            textC(gr, clipText(listTitle, w - 8), W / 2, y + 4, YEL);
            int top = y + sh + 9, avail = h - (top - y) - 4;
            listScroll = Math.max(0, Math.min(Math.max(0, listItems.length * rh - avail), listScroll));
            gr.setClip(x + 2, top, w - 4, avail);
            clipY0 = top; clipY1 = top + avail;
            for (int i = 0; i < listItems.length; i++) {
                int yy = top + i * rh - listScroll;
                if (yy + rh < top || yy > top + avail) continue;
                button(gr, x + 6, yy, w - 12, bh(), listItems[i], A_LIST + i, 0, false);
            }
            clipY0 = -1; clipY1 = -1;
            gr.setClip(0, 0, W, H);
            if (listItems.length * rh > avail) {
                int total = listItems.length * rh, bh2 = Math.max(10, avail * avail / total);
                fill(gr, x + w - 5, top + (avail - bh2) * listScroll / Math.max(1, total - avail), 3, bh2, 0xB0FFFFAA);
            }
        }
     } finally { rally3d.core.Trace.out(701, $t); } }
}
