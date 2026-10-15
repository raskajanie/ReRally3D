package com.nokia.mid.appl.rally3d;

import java.util.ArrayList;
import java.util.List;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import rally3d.core.RLog;

/**
 * In-game map editor ("Map Editor" in the main menu). One immediate-mode screen: top bar (file / undo / test drive / save),
 * a map viewport (top view, or the height chart), a segment strip at the bottom, and a tool panel on the right with six modes:
 * PATH (draw / extend the road, bends, turn rates), HEIGHT (drag the height profile), LAND (roadside terrain, centre line),
 * PROPS (sprites left / right incl. 4 paintable custom ones), MARKS (checkpoints and finish), WORLD (theme, sky, background,
 * road texture, colours, name). Works with mouse + wheel and with touch (drag = pan, pinch = zoom, tap = select / press).
 * Everything the user builds is a {@link MapData}; Save writes a type=map add-on zip that the game loads
 * (first in the track menus) and any other player can install.
 */
final class MapEditor {
    // palette = the game's own: pale yellow text, white frames, dark panels
    static final int YEL = 0xFFFFFFAA, WHITE = 0xFFFFFFFF, DIM = 0xFFB7B7B7, BG = 0xFF1B2229, PANEL = 0xFF26323C, BTN = 0xFF34475A,
            BTN_ON = 0xFF6A8AA6, RED = 0xFFE05050, GREEN = 0xFF60D060, BLACK = 0xFF000000, GRID = 0xFF2C3A46;
    static final int M_PATH = 0, M_HEIGHT = 1, M_LAND = 2, M_PROPS = 3, M_MARKS = 4, M_WORLD = 5;
    static final String[] MODE_NAMES = {"PATH", "HEIGHT", "LAND", "PROPS", "MARKS", "WORLD"};
    static final int[] TERRAIN_COLORS = {0xFF5A9E4B, 0xFF9A9A9A, 0xFF566892, 0xFFC08040, 0xFF9A6AB8};
    // button ids
    static final int A_FILE = 1, A_UNDO = 2, A_REDO = 3, A_TEST = 4, A_SAVE = 5, A_MODE = 10, A_FIT = 20, A_ZIN = 21, A_ZOUT = 22,
            A_EXT = 100, A_PIECE = 110, A_TURNL = 130, A_TURNR = 131, A_STRAIGHT = 132, A_DELETE = 133, A_TRIM = 134, A_INS = 135,
            A_H = 150, A_TERR = 170, A_CENTER = 180, A_SIDE = 190, A_SPR = 200, A_LANE = 230, A_EVERY = 240, A_APPLY = 250, A_CLEARP = 251, A_PAINTSPR = 260,
            A_CP = 280, A_CPAUTO = 290, A_CPGO = 295, A_THEME = 300, A_BG = 310, A_ROAD = 320, A_COLOR = 330, A_INFO = 340, A_COLAUTO = 345,
            A_DLG = 900, A_LIST = 1000, A_PAL = 1500, A_P = 2000;
    static final int A_FLY = 6, A_COPY = 7, A_CUT = 8, A_PASTE = 9, A_DUP = 16, A_ALL = 17, A_HELP = 18;

    private final RallyGame g;
    MapData md;
    private final ArrayList<MapData> undo = new ArrayList<MapData>(), redo = new ArrayList<MapData>();
    private int savedRev;
    private int mode = M_PATH;
    int selA = MapData.START_SEG + 1, selB = MapData.START_SEG + 8;
    private boolean extendTool = true;
    // top view camera (world units -> px) and height chart camera
    private double camX, camZ, zoom = 0.06;
    private double hOff, hPx = 3, vOff = -20, vPx = 1;
    // layout
    int W, H, TB = 16, SB = 24, PW = 112, vx, vy, vw, vh;
    int fh, sh;
    // frame hit boxes {x,y,w,h,id,arg}
    private final ArrayList<int[]> btns = new ArrayList<int[]>();
    private int panelScroll, panelContent;
    // pointers (up to 3 fingers)
    private final int[] pid = {-1, -1, -1}, px = new int[3], py = new int[3], dx0 = new int[3], dy0 = new int[3], region = new int[3], dragK = new int[3], pbtn = new int[3];
    private final boolean[] pmoved = new boolean[3];
    private final long[] pt0 = new long[3];
    private int hoverX = -1, hoverY = -1, stripAnchor;
    private long hoverT;
    private double pinchDist;
    // overlays
    private String toast;
    private long toastUntil;
    private String dlgTitle;
    private String[] dlgLines, dlgBtns;
    private int dlgKind;
    private String listTitle;
    private String[] listItems;
    private int listKind, listScroll;
    private int[] palette;      // colour picker overlay (kind in palKind)
    private int palKind;
    Painter painter;
    private int propSide = 2, propSprite = -1, propLane = 1, propEvery = 2;
    private float[] dragCtrl;
    // stretch drags (Premiere-style): 1 = ball at the road end, 2 / 3 = left / right edge handle of the selection on the strip
    private int dragKind, dragSlot = -1, trimSelA, trimSelB;
    private MapData dragSnap;
    private float[] dragOldCtrl;
    private static MapData.Clip clip;
    private boolean exit;
    private final ArrayList<MapData> savedRedo = new ArrayList<MapData>();   // redo stack as it was before the last push() (restored by cancelPush)
    private int dlgScroll, dlgMax;
    private int clipY0 = -1, clipY1 = -1;   // while set, button hit boxes are cut to this vertical band (scrolling panel / list)
    private final int[][] thumbMeta = new int[16][];
    private final int[][] thumbCache = new int[16][];
    private int thumbTheme = -9;
    private static final int[] SPRITE_TYPES = {-2, -1, 0, 1, 2, 5, 6, 3, 7, 8, 9, 10};

    private final int[] rawKeyBuf = new int[3];
    int W0() { return g.width; }
    int H0() { return g.height; }

    MapEditor(RallyGame game) { this.g = game; }

    // ------------------------------------------------------------------ main loop

    void run(AddonPack open) { /*T*/ final long $t = rally3d.core.Trace.in(389); try {
        md = open != null ? MapData.fromPack(open) : null;
        if (md == null) md = new MapData();
        savedRev = md.revision;
        int screenBefore = g.screenMode;
        g.beginMenu(rally3d.core.Lang.t("Map Editor"));
        g.menuHeaderStatic = false;
        g.menuSlide = 0;
        g.screenMode = 2;
        g.mapEditor = this;
        g.snapshotKeys();
        javax.microedition.lcdui.Canvas.rawPointer = true;
        g.clearRaw();
        fitAll();
        say(rally3d.core.Lang.t("Tap ? for help. Tap the road to select a segment."));
        int[] ev = new int[5];
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
            g.mapEditor = null;
            g.screenMode = screenBefore;
            g.redrawBackground = true;
        }
     } finally { rally3d.core.Trace.out(389, $t); } }

    void say(String t) { toast = t; toastUntil = System.currentTimeMillis() + 2600L; }

    // ------------------------------------------------------------------ undo

    void push() { /*T*/ final long $t = rally3d.core.Trace.in(557); try {
        undo.add(md.copy());
        if (undo.size() > 40) undo.remove(0);
        savedRedo.clear(); savedRedo.addAll(redo);
        redo.clear();
     } finally { rally3d.core.Trace.out(557, $t); } }

    /** Takes back the last push() when the edit turned out to be a no-op (keeps the redo history that push() cleared). */
    void cancelPush() {
        if (!undo.isEmpty()) undo.remove(undo.size() - 1);
        redo.clear(); redo.addAll(savedRedo);
    }

    private void doUndo() { /*T*/ final long $t = rally3d.core.Trace.in(390); try {
        if (undo.isEmpty()) { say(rally3d.core.Lang.t("Nothing to undo")); return; }
        redo.add(md.copy());
        md.restore(undo.remove(undo.size() - 1));
        thumbTheme = -9;
        clampSel();
     } finally { rally3d.core.Trace.out(390, $t); } }

    private void doRedo() { /*T*/ final long $t = rally3d.core.Trace.in(391); try {
        if (redo.isEmpty()) { say(rally3d.core.Lang.t("Nothing to redo")); return; }
        undo.add(md.copy());
        md.restore(redo.remove(redo.size() - 1));
        thumbTheme = -9;
        clampSel();
     } finally { rally3d.core.Trace.out(391, $t); } }

    boolean dirty() { return md.revision != savedRev; }

    void clampSel() {
        selA = Math.max(0, Math.min(md.n - 1, selA));
        selB = Math.max(selA, Math.min(md.n - 1, selB));
    }

    // ------------------------------------------------------------------ input

    private int slot(int id) { for (int i = 0; i < 3; i++) if (pid[i] == id) return i; return -1; }
    private int activePointers() { int c = 0; for (int i = 0; i < 3; i++) if (pid[i] >= 0) c++; return c; }

    private int regionAt(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(392); try {
        if (dlgTitle != null || listItems != null || palette != null) return 9;
        if (painter != null) return 8;
        if (y < TB) return 4;
        if (x >= W - PW) return 3;
        if (y >= H - SB) return 2;
        return 1;
     } finally { rally3d.core.Trace.out(392, $t); } }

    private void pointer(int action, int x, int y, int id, int button) { /*T*/ final long $t = rally3d.core.Trace.in(393); try {
        hoverX = x; hoverY = y; hoverT = System.currentTimeMillis();
        int s = slot(id);
        if (action == 0) {
            if (s >= 0) return;
            for (int i = 0; i < 3; i++) if (pid[i] < 0) { s = i; break; }
            if (s < 0) return;
            pid[s] = id; px[s] = dx0[s] = x; py[s] = dy0[s] = y; pmoved[s] = false; pt0[s] = System.currentTimeMillis(); pbtn[s] = button; dragK[s] = -1;
            region[s] = regionAt(x, y);
            if (region[s] == 8) { painter.down(s, x, y, button, activePointers()); }
            else if (region[s] == 2) {
                int h = stripHandleAt(x);
                if (h != 0) beginDrag(s, h + 1); else { stripAnchor = segAtStrip(x); setSel(stripAnchor, stripAnchor); }
            }
            else if (region[s] == 1 && mode != M_HEIGHT && button == 1 && activePointers() == 1 && endBallHit(x, y)) beginDrag(s, 1);
            else if (region[s] == 1 && mode == M_HEIGHT && button == 1 && activePointers() == 1) {
                int k = ctrlAt(x, y);
                if (k >= 0) { dragK[s] = k; push(); dragCtrl = md.sampleCtrl(); }
            }
            if (region[s] == 1 && activePointers() == 2) pinchDist = pinchNow();
            return;
        }
        if (s < 0) return;
        if (action == 1) {
            int ox = px[s], oy = py[s];
            px[s] = x; py[s] = y;
            if (!pmoved[s] && Math.abs(x - dx0[s]) + Math.abs(y - dy0[s]) > 5) pmoved[s] = true;
            if (!pmoved[s]) return;
            switch (region[s]) {
                case 1: if (dragKind == 1 && s == dragSlot) endDrag(x, y); else viewDrag(s, x - ox, y - oy, x, y); break;
                case 2: {
                    if ((dragKind == 2 || dragKind == 3) && s == dragSlot) { trimDrag(x); break; }
                    int a = segAtStrip(x); setSel(Math.min(stripAnchor, a), Math.max(stripAnchor, a)); break;
                }
                case 3: panelScroll = Math.max(0, Math.min(Math.max(0, panelContent - (H - TB - 4)), panelScroll - (y - oy))); break;
                case 8: painter.move(s, x, y, activePointers()); break;
                case 9: listDrag(y - oy); break;
                default: break;
            }
            return;
        }
        if (action == 2) {
            boolean tap = !pmoved[s] && System.currentTimeMillis() - pt0[s] < 900;
            int r = region[s];
            boolean hadCtrl = dragK[s] >= 0 && dragCtrl != null;
            pid[s] = -1; dragK[s] = -1;
            if (hadCtrl && !pmoved[s]) cancelPush();
            if (s == dragSlot) { if (!pmoved[s] && dragKind != 0) cancelPush(); dragKind = 0; dragSlot = -1; dragSnap = null; dragOldCtrl = null; }
            if (r == 8) painter.up(s, x, y);
            if (r == 1 && dragCtrl != null) dragCtrl = null;
            if (tap) tapAt(x, y, r, pbtn[s]);
            if (activePointers() < 2) pinchDist = 0;
        }
     } finally { rally3d.core.Trace.out(393, $t); } }

    private double pinchNow() { /*T*/ final long $t = rally3d.core.Trace.in(394); try {
        int a = -1, b = -1;
        for (int i = 0; i < 3; i++) if (pid[i] >= 0 && region[i] == 1) { if (a < 0) a = i; else b = i; }
        if (b < 0) return 0;
        return Math.hypot(px[a] - px[b], py[a] - py[b]);
     } finally { rally3d.core.Trace.out(394, $t); } }

    private void viewDrag(int s, int ddx, int ddy, int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(395); try {
        if (activePointers() >= 2) {   // pinch: zoom around the midpoint, pan with it
            double d = pinchNow();
            if (pinchDist > 8 && d > 8) zoomView(d / pinchDist, x, y);
            pinchDist = d;
            panView(ddx / 2, ddy / 2);
            return;
        }
        if (dragK[s] >= 0 && dragCtrl != null) {
            double h = vOff + (vy + vh - y) / vPx;
            dragCtrl[dragK[s]] = (float) Math.max(-1500, Math.min(2500, h));
            dragCtrl[0] = 0;
            md.fitHeights(dragCtrl);
            return;
        }
        panView(ddx, ddy);
     } finally { rally3d.core.Trace.out(395, $t); } }

    private void panView(int ddx, int ddy) {
        if (mode == M_HEIGHT) { hOff -= ddx / hPx; vOff += ddy / vPx; }
        else { camX += ddx / zoom; camZ += ddy / zoom; }
    }

    private void zoomView(double f, int cx, int cy) { /*T*/ final long $t = rally3d.core.Trace.in(396); try {
        if (mode == M_HEIGHT) {
            double segAt = hOff + (cx - vx) / hPx;
            hPx = Math.max(0.4, Math.min(40, hPx * f));
            hOff = segAt - (cx - vx) / hPx;
        } else {
            double wx = camX - (cx - (vx + vw / 2)) / zoom, wz = camZ - (cy - (vy + vh / 2)) / zoom;   // world point under the cursor
            zoom = Math.max(0.01, Math.min(2.5, zoom * f));
            camX = wx + (cx - (vx + vw / 2)) / zoom;
            camZ = wz + (cy - (vy + vh / 2)) / zoom;
        }
     } finally { rally3d.core.Trace.out(396, $t); } }

    private void wheel(int rot) { /*T*/ final long $t = rally3d.core.Trace.in(397); try {
        int x = hoverX, y = hoverY;
        if (painter != null) { painter.wheel(rot, x, y); return; }
        if (listItems != null || dlgTitle != null) { listDrag(-rot * 12); return; }
        if (x >= W - PW && y > TB) { panelScroll = Math.max(0, Math.min(Math.max(0, panelContent - (H - TB - 4)), panelScroll + rot * 14)); return; }
        if (regionAt(x, y) == 1) zoomView(Math.pow(1.18, -rot), x, y);
     } finally { rally3d.core.Trace.out(397, $t); } }

    private void key(int k) { /*T*/ final long $t = rally3d.core.Trace.in(398); try {
        if (painter != null) { if ((k & 0x200) != 0) painter.cancel(); return; }
        if (dlgTitle != null) { if ((k & 0x200) != 0) dlgPick(dlgBtns.length - 1); return; }
        if (listItems != null) { if ((k & 0x200) != 0) listClose(); return; }
        if (palette != null) { palette = null; return; }
        if ((k & 0x200) != 0) { fileMenu(); return; }
        if ((k & 4) != 0) setSel(selA - 1, selA - 1);
        else if ((k & 0x20) != 0) setSel(selB + 1, selB + 1);
        else if ((k & 2) != 0) setSel(selA - 10, selA - 10);
        else if ((k & 0x40) != 0) setSel(selB + 10, selB + 10);
     } finally { rally3d.core.Trace.out(398, $t); } }

    void setSel(int a, int b) { /*T*/ final long $t = rally3d.core.Trace.in(399); try {
        a = Math.max(0, Math.min(md.n - 1, a)); b = Math.max(0, Math.min(md.n - 1, b));
        selA = Math.min(a, b); selB = Math.max(a, b);
     } finally { rally3d.core.Trace.out(399, $t); } }

    private int segAtStrip(int x) {
        int sw = W - PW - 8;
        return Math.max(0, Math.min(md.n - 1, (int) ((long) (x - 4) * md.n / Math.max(1, sw))));
    }

    /** Index of the height control point under the pointer, or -1. */
    private int ctrlAt(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(400); try {
        float[] c = md.sampleCtrl();
        int best = -1; double bd = 15;
        for (int k = 0; k < c.length; k++) {
            double sx = vx + (k * MapData.CTRL_STEP - hOff) * hPx, sy = vy + vh - (c[k] - vOff) * vPx;
            double d = Math.hypot(sx - x, sy - y);
            if (d < bd) { bd = d; best = k; }
        }
        return best <= 0 ? -1 : best;   // point 0 is the start height and cannot move
     } finally { rally3d.core.Trace.out(400, $t); } }

    private void tapAt(int x, int y, int r, int button) { /*T*/ final long $t = rally3d.core.Trace.in(401); try {
        // buttons first (they were registered by the last draw)
        for (int i = btns.size() - 1; i >= 0; i--) {
            int[] b = btns.get(i);
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) { if (r != 9 || (b[4] >= A_DLG && b[4] < A_P)) { action(b[4], b[5]); } return; }
        }
        if (r == 9) { return; }
        if (r == 1) viewTap(x, y);
        else if (r == 2) { int s = segAtStrip(x); setSel(s, s); }
     } finally { rally3d.core.Trace.out(401, $t); } }

    private void viewTap(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(402); try {
        if (mode == M_HEIGHT) {
            int s = (int) Math.round(hOff + (x - vx) / hPx);
            setSel(s, s);
            return;
        }
        double wx = camX - (x - (vx + vw / 2)) / zoom, wz = camZ - (y - (vy + vh / 2)) / zoom;
        int near = nearestSegment(wx, wz, 18.0 / zoom);
        if (near >= 0) { setSel(near, near); return; }
        if (mode == M_PATH && extendTool) {
            push();
            float[] old = md.sampleCtrl();
            int before = md.n;
            int added = md.extendToward(wx, wz);
            if (added <= 0) { cancelPush(); say(rally3d.core.Lang.t("Too close to the road end, or behind it")); return; }
            md.refitAfterResize(old, before, added);
            setSel(before, md.n - 1);
            say("+" + added + rally3d.core.Lang.t(" segments (") + md.n + rally3d.core.Lang.t(" total)"));
        } else {
            say(rally3d.core.Lang.t("Tap the road to select a segment"));
        }
     } finally { rally3d.core.Trace.out(402, $t); } }

    /** Segment whose start / middle / end is closest to the world point, within maxDist (else -1). */
    int nearestSegment(double wx, double wz, double maxDist) { /*T*/ final long $t = rally3d.core.Trace.in(403); try {
        md.geo();
        int best = -1; double bd = maxDist;
        for (int i = 0; i < md.n; i++) {
            double mx = (md.gx[i] + md.gx[i + 1]) / 2, mz = (md.gz[i] + md.gz[i + 1]) / 2;
            double d = Math.min(Math.hypot(md.gx[i] - wx, md.gz[i] - wz), Math.hypot(mx - wx, mz - wz));
            if (d < bd) { bd = d; best = i; }
        }
        return best;
     } finally { rally3d.core.Trace.out(403, $t); } }

    private void fitAll() { fitTop(); fitChart(); }

    /** FIT button: refits only the view that is on screen (switching modes must not throw away the other view's zoom). */
    private void fitView() { if (mode == M_HEIGHT) fitChart(); else fitTop(); }

    private void fitTop() { /*T*/ final long $t = rally3d.core.Trace.in(404); try {
        md.geo();
        double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
        for (int i = 0; i <= md.n; i++) {
            minX = Math.min(minX, md.gx[i]); maxX = Math.max(maxX, md.gx[i]);
            minZ = Math.min(minZ, md.gz[i]); maxZ = Math.max(maxZ, md.gz[i]);
        }
        layout();
        camX = (minX + maxX) / 2; camZ = (minZ + maxZ) / 2;
        double spanX = Math.max(200, maxX - minX + 160), spanZ = Math.max(200, maxZ - minZ + 160);
        zoom = Math.max(0.01, Math.min(1.2, Math.min(vw / spanX, vh / spanZ)));
     } finally { rally3d.core.Trace.out(404, $t); } }

    private void fitChart() { /*T*/ final long $t = rally3d.core.Trace.in(558); try {
        md.geo();
        double minH = 0, maxH = 0;
        for (int i = 0; i <= md.n; i++) { minH = Math.min(minH, md.gh[i]); maxH = Math.max(maxH, md.gh[i]); }
        layout();
        hOff = -2; hPx = Math.max(0.6, (vw - 24) / (double) (md.n + 6));
        vOff = minH - 25; vPx = Math.max(0.05, (vh - 30) / Math.max(60.0, maxH - minH + 50));
     } finally { rally3d.core.Trace.out(558, $t); } }

    private void layout() { /*T*/ final long $t = rally3d.core.Trace.in(405); try {
        W = g.width; H = g.height;
        fh = g.fontBoldHeight; sh = g.fontSmallHeight;
        TB = Math.max(16, sh + 6);
        SB = Math.max(22, sh + 12);
        PW = Math.max(104, Math.min(132, W * 30 / 100));
        vx = 0; vy = TB; vw = W - PW; vh = H - TB - SB;
     } finally { rally3d.core.Trace.out(405, $t); } }

    // ------------------------------------------------------------------ actions

    private static final int[][][] PIECES = {
        {{0, 6}}, {{-45, 8}}, {{45, 8}}, {{-90, 10}}, {{90, 10}}, {{-180, 12}}, {{180, 12}}, {{-40, 7}, {40, 7}}, {{30, 5}, {-60, 8}, {30, 5}}};
    static final String[] PIECE_NAMES = {"STRAIGHT", "LEFT 45", "RIGHT 45", "LEFT 90", "RIGHT 90", "HAIRPIN L", "HAIRPIN R", "S-BEND", "CHICANE"};
    private static final int[] EVERY = {1, 2, 3, 5, 8};
    private static final int[] PAL_COLORS = {
        0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF, 0xFF7A4A28, 0xFFB07840, 0xFFE8C898,
        0xFF800000, 0xFFE02020, 0xFFFF8020, 0xFFFFE030, 0xFF206020, 0xFF30B030, 0xFF90E050, 0xFF106060,
        0xFF2050C0, 0xFF50A0FF, 0xFFA0D8FF, 0xFF402080, 0xFF9050E0, 0xFFE070C0, 0xFF603030, 0xFFFFC8C8};

    private void action(int id, int arg) { /*T*/ final long $t = rally3d.core.Trace.in(406); try {
        if (id >= A_P) { if (painter != null) painter.action(id - A_P, arg); return; }
        if (id >= A_PAL) { colorPicked(id - A_PAL); return; }
        if (id >= A_LIST) { listPick(id - A_LIST); return; }
        if (id >= A_DLG) { dlgPick(id - A_DLG); return; }
        if (id >= A_MODE && id < A_MODE + 6) { setMode(id - A_MODE); return; }
        switch (id) {
            case A_FILE: fileMenu(); return;
            case A_UNDO: doUndo(); return;
            case A_REDO: doRedo(); return;
            case A_TEST: testDrive(); return;
            case A_SAVE: save(); return;
            case A_FLY: flyDrive(); return;
            case A_COPY: copySel(); return;
            case A_CUT: cutSel(); return;
            case A_PASTE: pasteSel(); return;
            case A_DUP: dupSel(); return;
            case A_ALL: setSel(0, md.n - 1); return;
            case A_FIT: fitView(); return;
            case A_HELP: showHelp(0); return;
            case A_ZIN: zoomView(1.4, vx + vw / 2, vy + vh / 2); return;
            case A_ZOUT: zoomView(1 / 1.4, vx + vw / 2, vy + vh / 2); return;
            case A_EXT: extendTool = !extendTool; return;
            default: break;
        }
        if (id >= A_PIECE && id < A_PIECE + PIECES.length) { insertPiece(id - A_PIECE); return; }
        if (id == A_TURNL) { push(); md.addYaw(selA, selB, -3); return; }
        if (id == A_TURNR) { push(); md.addYaw(selA, selB, 3); return; }
        if (id == A_STRAIGHT) { push(); md.setYaw(selA, selB, 0); return; }
        if (id == A_DELETE) { deleteRange(selA, selB); return; }
        if (id == A_TRIM) { if (selB >= md.n - 1) say(rally3d.core.Lang.t("Nothing after the selection")); else deleteRange(selB + 1, md.n - 1); return; }
        if (id >= A_H && id < A_H + 8) { heightOp(id - A_H); return; }
        if (id >= A_TERR && id < A_TERR + 5) { push(); md.setTerrain(selA, selB, MapData.TERRAIN[id - A_TERR]); return; }
        if (id == A_CENTER || id == A_CENTER + 1) { push(); md.setCenter(selA, selB, id - A_CENTER); return; }
        if (id >= A_SIDE && id < A_SIDE + 3) { propSide = id - A_SIDE; return; }
        if (id >= A_SPR && id < A_SPR + SPRITE_TYPES.length) {
            int t = SPRITE_TYPES[id - A_SPR];
            if (t >= 7 && md.spritePx[t - 7] == null) { openSpritePainter(t - 7); return; }
            propSprite = t;
            return;
        }
        if (id >= A_LANE && id < A_LANE + 4) { propLane = id - A_LANE; return; }
        if (id >= A_EVERY && id < A_EVERY + EVERY.length) { propEvery = EVERY[id - A_EVERY]; return; }
        if (id == A_APPLY) {
            if (propSprite == -9) { say(rally3d.core.Lang.t("Pick a sprite first")); return; }
            push(); md.paintProps(selA, selB, propSide, propSprite, propLane, propEvery);
            say(propSprite == -2 ? rally3d.core.Lang.t("Props removed") : rally3d.core.Lang.t("Props placed on ") + (selB - selA + 1) + rally3d.core.Lang.t(" segments"));
            return;
        }
        if (id == A_CLEARP) { push(); md.paintProps(selA, selB, 2, -2, 0, 1); return; }
        if (id >= A_PAINTSPR && id < A_PAINTSPR + 4) { openSpritePainter(id - A_PAINTSPR); return; }
        if (id >= A_CP && id < A_CP + 4) { push(); md.cp[id - A_CP] = selA; md.touch(); String bad = md.validate(); say(bad == null ? rally3d.core.Lang.t("Marker set at segment ") + selA : bad); return; }
        if (id == A_CPAUTO) { push(); md.cp[3] = -1; md.autoCheckpoints(); say(rally3d.core.Lang.t("Checkpoints spread evenly")); return; }
        if (id >= A_CPGO && id < A_CPGO + 5) { int k = id - A_CPGO; int s = k == 0 ? MapData.START_SEG : md.effCp(k - 1); setSel(s, s); centerOn(s); return; }
        if (id >= A_THEME && id < A_THEME + 4) { push(); md.theme = id - A_THEME; md.touch(); return; }
        if (id >= A_BG && id < A_BG + 6) {
            int i = id - A_BG;
            if (i == 5) { openBgPainter(); return; }
            push(); md.bgPng = null; md.bgFrom = i == 4 ? -1 : i; md.touch(); return;
        }
        if (id >= A_ROAD && id < A_ROAD + 6) {
            int i = id - A_ROAD;
            if (i == 5) { openRoadPainter(); return; }
            push(); md.roadPng = null; md.roadFrom = i == 4 ? -1 : i; md.touch(); return;
        }
        if (id >= A_COLOR && id < A_COLOR + 3) { palKind = id - A_COLOR; palette = PAL_COLORS; return; }
        if (id == A_COLAUTO) { push(); md.customColors = false; md.touch(); return; }
        if (id >= A_INFO && id < A_INFO + 3) { editInfo(id - A_INFO); return; }
     } finally { rally3d.core.Trace.out(406, $t); } }

    private void setMode(int m) {
        mode = Math.max(0, Math.min(5, m)); panelScroll = 0;
    }

    private void centerOn(int seg) { /*T*/ final long $t = rally3d.core.Trace.in(407); try {
        md.geo();
        if (mode == M_HEIGHT) { hOff = seg - (vw / 2) / hPx; }
        else { camX = md.gx[Math.min(md.n, seg)]; camZ = md.gz[Math.min(md.n, seg)]; }
     } finally { rally3d.core.Trace.out(407, $t); } }

    private void insertPiece(int k) { /*T*/ final long $t = rally3d.core.Trace.in(408); try {
        push();
        float[] old = md.sampleCtrl();
        int at = selB >= md.n - 1 ? md.n : selB + 1;
        int before = md.n;
        int pos = at;
        for (int[] part : PIECES[k]) { int b = md.n; md.insertArc(pos, part[0], part[1]); pos += md.n - b; }
        int added = md.n - before;
        if (added <= 0) { cancelPush(); say(rally3d.core.Lang.t("Map is at the size limit (") + MapData.MAX_SEGS + ")"); return; }
        md.refitAfterResize(old, at, added);
        setSel(at, at + added - 1);
        say(rally3d.core.Lang.t(PIECE_NAMES[k]) + ": +" + added + rally3d.core.Lang.t(" segments"));
     } finally { rally3d.core.Trace.out(408, $t); } }

    private void deleteRange(int a, int b) { /*T*/ final long $t = rally3d.core.Trace.in(409); try {
        int cnt = b - a + 1;
        if (md.n - cnt < MapData.MIN_SEGS) { say(rally3d.core.Lang.t("A map needs at least ") + MapData.MIN_SEGS + rally3d.core.Lang.t(" segments")); return; }
        push();
        float[] old = md.sampleCtrl();
        md.delete(a, b);
        md.refitAfterResize(old, a, -cnt);
        clampSel();
        setSel(Math.max(0, a - 1), Math.max(0, a - 1));
        say(rally3d.core.Lang.t("Deleted ") + cnt + rally3d.core.Lang.t(" segments"));
     } finally { rally3d.core.Trace.out(409, $t); } }

    private void heightOp(int op) { /*T*/ final long $t = rally3d.core.Trace.in(410); try {
        if (op == 7) { fitChart(); return; }
        push();
        float[] c = md.sampleCtrl();
        int ka = Math.max(1, selA / MapData.CTRL_STEP), kb = Math.min(c.length - 1, (selB + MapData.CTRL_STEP - 1) / MapData.CTRL_STEP);
        if (kb < ka) kb = ka;
        switch (op) {
            case 0: for (int k = ka; k <= kb; k++) c[k] += 10; break;
            case 1: for (int k = ka; k <= kb; k++) c[k] -= 10; break;
            case 2: case 3: {
                int cnt = kb - ka + 1;
                for (int k = ka; k <= kb; k++) { double u = (k - ka + 0.5) / cnt; c[k] += (float) ((op == 2 ? 45 : -45) * Math.sin(Math.PI * u)); }
                break;
            }
            case 4: for (int k = ka; k <= kb; k++) c[k] = c[ka - 1 >= 0 ? ka - 1 : ka]; break;
            case 5: {
                float[] o = c.clone();
                for (int k = ka; k <= kb; k++) c[k] = (o[Math.max(0, k - 1)] + o[k] * 2 + o[Math.min(o.length - 1, k + 1)]) / 4f;
                break;
            }
            case 6: for (int k = 1; k < c.length; k++) c[k] = 0; break;
            default: break;
        }
        c[0] = 0;
        md.fitHeights(c);
     } finally { rally3d.core.Trace.out(410, $t); } }

    // ------------------------------------------------------------------ text / colours

    String askText(String title, String prefix, String initial, int max) { /*T*/ final long $t = rally3d.core.Trace.in(411); try {
        javax.microedition.lcdui.Canvas.rawPointer = false;
        g.mapEditor = null;
        String r;
        try { r = g.editTextInGame(title, prefix, initial, max); }
        finally { reenter(); }
        return r == null ? null : r.trim();
     } finally { rally3d.core.Trace.out(411, $t); } }

    /** Re-establishes the editor screen after something else (text menu, test drive) used the menu machinery. */
    private void reenter() { /*T*/ final long $t = rally3d.core.Trace.in(412); try {
        g.beginMenu(rally3d.core.Lang.t("Map Editor"));
        g.menuHeaderStatic = false;
        g.menuSlide = 0;
        g.screenMode = 2;
        g.mapEditor = this;
        g.snapshotKeys();
        javax.microedition.lcdui.Canvas.rawPointer = true;
        g.clearRaw();
        for (int i = 0; i < 3; i++) pid[i] = -1;
        pinchDist = 0;
     } finally { rally3d.core.Trace.out(412, $t); } }

    private void editInfo(int which) { /*T*/ final long $t = rally3d.core.Trace.in(413); try {
        String[] titles = {rally3d.core.Lang.t("Map name"), rally3d.core.Lang.t("Author"), rally3d.core.Lang.t("Description")};
        String[] pre = {rally3d.core.Lang.t("Name: "), rally3d.core.Lang.t("Author: "), rally3d.core.Lang.t("Text: ")};
        int[] max = {24, 24, 60};
        String cur = which == 0 ? md.name : which == 1 ? md.author : md.desc;
        String v = askText(titles[which], pre[which], cur, max[which]);
        if (v == null) return;
        if (which == 0 && v.length() == 0) { say(rally3d.core.Lang.t("The name cannot be empty")); return; }
        if (v.equals(cur)) return;
        push();
        if (which == 0) md.name = v; else if (which == 1) md.author = v; else md.desc = v;
        md.touch();
     } finally { rally3d.core.Trace.out(413, $t); } }

    private void colorPicked(int i) { /*T*/ final long $t = rally3d.core.Trace.in(414); try {
        int c = PAL_COLORS[i];
        push();
        if (!md.customColors) { md.customColors = true; md.sky = md.skyColor(); md.ground = md.groundColor(); md.hud = md.hudColor(); }
        if (palKind == 0) md.sky = c; else if (palKind == 1) md.ground = c; else md.hud = c;
        md.touch();
        palette = null;
     } finally { rally3d.core.Trace.out(414, $t); } }

    // ------------------------------------------------------------------ painters

    private void openSpritePainter(int slot) { /*T*/ final long $t = rally3d.core.Trace.in(415); try {
        palette = null;
        int[] px = md.spritePx[slot];
        if (px != null) { painter = new Painter(this, Painter.K_SPRITE, slot, px.clone(), md.spriteW[slot], md.spriteH[slot], true, rally3d.core.Lang.t("SPRITE ") + (slot + 1)); return; }
        // new sprite: choose a size first
        listTitle = rally3d.core.Lang.t("NEW SPRITE ") + (slot + 1) + rally3d.core.Lang.t(" - SIZE");
        listItems = new String[]{rally3d.core.Lang.t("BUSH 32x24"), rally3d.core.Lang.t("SHRUB 32x32"), rally3d.core.Lang.t("TREE 40x64"), rally3d.core.Lang.t("TALL TREE 48x96"), rally3d.core.Lang.t("POLE 16x48")};
        listKind = LK_SPRSIZE + slot * 10;
        listScroll = 0;
     } finally { rally3d.core.Trace.out(415, $t); } }

    private void openBgPainter() { /*T*/ final long $t = rally3d.core.Trace.in(416); try {
        int[] px;
        int[] wh = new int[2];
        px = md.bgPng != null ? PngCodec.decode(md.bgPng, wh) : null;
        if (px == null || wh[0] != 128 || wh[1] != 128) {
            String dir = MapData.THEME_DIRS[md.bgFrom >= 0 ? md.bgFrom : md.theme];
            px = stockImage(dir + "/background.png", wh);
            if (px == null || wh[0] != 128 || wh[1] != 128) { px = new int[128 * 128]; java.util.Arrays.fill(px, md.skyColor() | 0xFF000000); }
        }
        painter = new Painter(this, Painter.K_BG, 0, px, 128, 128, false, rally3d.core.Lang.t("BACKGROUND"));
     } finally { rally3d.core.Trace.out(416, $t); } }

    private void openRoadPainter() { /*T*/ final long $t = rally3d.core.Trace.in(417); try {
        int[] wh = new int[2];
        int[] px = md.roadPng != null ? PngCodec.decode(md.roadPng, wh) : null;
        if (px == null || wh[0] != 256 || wh[1] != 32) {
            px = stockImage(MapData.THEME_DIRS[md.roadFrom >= 0 ? md.roadFrom : md.theme] + "/squares.png", wh);
            if (px == null || wh[0] != 256 || wh[1] != 32) { px = new int[256 * 32]; java.util.Arrays.fill(px, 0xFF707070); }
        }
        painter = new Painter(this, Painter.K_ROAD, 0, px, 256, 32, false, rally3d.core.Lang.t("ROAD TEXTURE"));
     } finally { rally3d.core.Trace.out(417, $t); } }

    int[] stockImage(String path, int[] wh) { /*T*/ final long $t = rally3d.core.Trace.in(418); try {
        try {
            javax.microedition.lcdui.Image im = javax.microedition.lcdui.Image.createImage(path);
            int w = im.getWidth(), h = im.getHeight();
            int[] px = new int[w * h];
            im.getRGB(px, 0, w, 0, 0, w, h);
            wh[0] = w; wh[1] = h;
            return px;
        } catch (java.io.IOException e) { RLog.caught("MapEditor.stockImage", e);
            return null;
        }
     } finally { rally3d.core.Trace.out(418, $t); } }

    /** First frame of a stock sprite (type 0..2 of a theme, 3, 5, 6) as ARGB; wh = size. */
    int[] stockSprite(int type, int theme, int[] wh) { /*T*/ final long $t = rally3d.core.Trace.in(419); try {
        String dir = MapData.THEME_DIRS[theme];
        String base = type <= 2 ? dir + "/billboard" + (type + 1) : type == 3 ? "/billboard4" : type == 5 ? "/billboard6" : "/billboard7";
        int[] sheetWh = new int[2];
        int[] sheet = stockImage(base + ".png", sheetWh);
        if (sheet == null) return null;
        try {
            java.io.InputStream in = MapEditor.class.getResourceAsStream(base + ".off");
            byte[] off = new byte[48];
            int got = 0, r;
            while (in != null && got < 48 && (r = in.read(off, got, 48 - got)) > 0) got += r;
            if (in != null) in.close();
            if (got < 48) return null;
            int fw = (off[24] & 255) | ((off[25] & 255) << 8), fh = (off[36] & 255) | ((off[37] & 255) << 8);
            if (fw <= 0 || fh <= 0 || fw > sheetWh[0] || fh > sheetWh[1]) return null;
            int[] out = new int[fw * fh];
            for (int y = 0; y < fh; y++) System.arraycopy(sheet, y * sheetWh[0], out, y * fw, fw);
            wh[0] = fw; wh[1] = fh;
            return out;
        } catch (java.io.IOException e) { RLog.caught("MapEditor.stockSprite", e);
            return null;
        }
     } finally { rally3d.core.Trace.out(419, $t); } }

    /** Called by the painter when the user presses DONE. */
    void painterDone(Painter p) { /*T*/ final long $t = rally3d.core.Trace.in(420); try {
        push();
        try {
            if (p.kind == Painter.K_SPRITE) {
                md.spritePx[p.slot] = p.px; md.spriteW[p.slot] = p.w; md.spriteH[p.slot] = p.h;
                thumbTheme = -9;
                propSprite = 7 + p.slot;
            } else if (p.kind == Painter.K_BG) { md.bgPng = PngCodec.encode(p.px, p.w, p.h); md.bgFrom = -1; }
            else { md.roadPng = PngCodec.encode(p.px, p.w, p.h); md.roadFrom = -1; }
            md.touch();
            say(rally3d.core.Lang.t("Image saved in the map"));
        } catch (java.io.IOException e) { RLog.caught("MapEditor.painterDone", e);
            say(rally3d.core.Lang.t("Cannot store the image: ") + e.getMessage());
        }
        painter = null;
     } finally { rally3d.core.Trace.out(420, $t); } }

    void painterClosed() { painter = null; }

    // ------------------------------------------------------------------ file / dialogs / lists

    static final int LK_FILE = 1, LK_STOCK = 2, LK_OPEN = 3, LK_TRACKTPL = 4, LK_TEMPLATE = 50, LK_STOCKSPR = 51, LK_BGTPL = 52, LK_ROADTPL = 53, LK_IMPORT = 54, LK_SPRSIZE = 100;

    void showList(String title, String[] items, int kind) { listTitle = title; listItems = items; listKind = kind; listScroll = 0; }

    void addBtn(int x, int y, int w, int h, int id, int arg) { btns.add(new int[]{x, y, w, h, id, arg}); }
    static final int DG_DISCARD = 1, DG_INFO = 2;
    private int pendKind, pendArg;   // what to do once unsaved changes are settled: 1 new, 2 stock, 3 open, 4 exit
    private ArrayList<AddonPack> openList;

    private void fileMenu() { /*T*/ final long $t = rally3d.core.Trace.in(421); try {
        listTitle = rally3d.core.Lang.t("FILE");
        listItems = new String[]{rally3d.core.Lang.t("Save + install map"), rally3d.core.Lang.t("Export zip..."), rally3d.core.Lang.t("Test drive"), rally3d.core.Lang.t("Fly view"), rally3d.core.Lang.t("Map info (name, author, text)"), rally3d.core.Lang.t("New blank map"), rally3d.core.Lang.t("New from a template..."), rally3d.core.Lang.t("New from a stock track..."), rally3d.core.Lang.t("Open an installed map..."), rally3d.core.Lang.t("How to use"), rally3d.core.Lang.t("Exit editor")};
        listKind = LK_FILE; listScroll = 0;
     } finally { rally3d.core.Trace.out(421, $t); } }

    private void listClose() { listItems = null; listKind = 0; }

    private void openTemplateList() { /*T*/ final long $t = rally3d.core.Trace.in(520); try {
        listTitle = rally3d.core.Lang.t("NEW FROM A TEMPLATE"); listItems = rally3d.core.Lang.tl(MapTemplates.TRACK_NAMES); listKind = LK_TRACKTPL; listScroll = 0;
     } finally { rally3d.core.Trace.out(520, $t); } }

    private void openStockList() { /*T*/ final long $t = rally3d.core.Trace.in(521); try {
        listTitle = rally3d.core.Lang.t("COPY A STOCK TRACK"); listItems = Texts.trackNames.clone(); listKind = LK_STOCK; listScroll = 0;
     } finally { rally3d.core.Trace.out(521, $t); } }

    private void openInstalledList() { /*T*/ final long $t = rally3d.core.Trace.in(522); try {
        openList = new ArrayList<AddonPack>(); AddonManager.rescan();
        for (AddonPack p : AddonManager.ofKind(0)) if (p.error == null && p.mapBin != null) openList.add(p);
        if (openList.isEmpty()) { say(rally3d.core.Lang.t("No installed maps yet - save one first")); return; }
        listItems = new String[openList.size()]; for (int k = 0; k < listItems.length; k++) listItems[k] = openList.get(k).name;
        listTitle = rally3d.core.Lang.t("OPEN A MAP"); listKind = LK_OPEN; listScroll = 0;
     } finally { rally3d.core.Trace.out(522, $t); } }

    private void listDrag(int dy) { /*T*/ final long $t = rally3d.core.Trace.in(559); try {
        if (dlgTitle != null) dlgScroll = Math.max(0, Math.min(dlgMax, dlgScroll - dy));
        else listScroll = Math.max(0, listScroll - dy);
     } finally { rally3d.core.Trace.out(559, $t); } }

    private void listPick(int i) { /*T*/ final long $t = rally3d.core.Trace.in(422); try {
        int kind = listKind;
        listClose();
        if (kind == LK_FILE) {
            switch (i) {
                case 0: save(); break;
                case 1: exportZip(); break;
                case 2: testDrive(); break;
                case 3: flyDrive(); break;
                case 4: mode = M_WORLD; panelScroll = 0; break;
                case 5: settle(1, 0); break;
                case 6: openTemplateList(); break;
                case 7: openStockList(); break;
                case 8: openInstalledList(); break;
                case 9: showHelp(0); break;
                default: settle(4, 0); break;
            }
        } else if (kind == LK_STOCK) { settle(2, i); }
        else if (kind == LK_OPEN) { settle(3, i); }
        else if (kind == LK_TRACKTPL) { settle(5, i); }
        else if (kind == LK_BGTPL) { if (painter != null) painter.bgTemplate(i); }
        else if (kind == LK_ROADTPL) { if (painter != null) painter.roadTemplate(i); }
        else if (kind == LK_IMPORT) { if (painter != null) painter.importPng(i); }
        else if (kind == LK_TEMPLATE) { if (painter != null) painter.template(i); }
        else if (kind == LK_STOCKSPR) { if (painter != null) painter.copyStock(i); }
        else if (kind >= LK_SPRSIZE) {
            int slot = (kind - LK_SPRSIZE) / 10;
            int[][] sizes = {{32, 24}, {32, 32}, {40, 64}, {48, 96}, {16, 48}};
            painter = new Painter(this, Painter.K_SPRITE, slot, new int[sizes[i][0] * sizes[i][1]], sizes[i][0], sizes[i][1], true, rally3d.core.Lang.t("SPRITE ") + (slot + 1));
        }
     } finally { rally3d.core.Trace.out(422, $t); } }

    static final int DG_HELP = 3;
    private int helpPage;
    private static final String[] HELP_TITLES = {"VIEW + SELECT", "STRETCH LIKE A CLIP", "THE SIX MODES", "SHORTCUTS 1", "SHORTCUTS 2", "FLY + TEST", "PAINTER", "SAVE + SHARE"};
    private static final String[][] HELP = {
        {"Drag empty ground = move the view. Wheel, pinch or + / - = zoom. FIT (or F) shows the whole map.",
         "Tap the road = select a segment. Drag along the bottom strip = select a range. The yellow block on the strip is the selection and every tool works on it.",
         "Two fingers (or the wheel) zoom around the point you touch. FIT only refits the view you are looking at (map or height chart).",
         "Hover a button with the mouse for a one-line hint. The ? button in the top bar opens this help."},
        {"ORANGE BALL at the end of the road: drag it anywhere. The road grows that way and the terrain, props and centre line of its last segment continue. Drag it back over the road to shorten.",
         "YELLOW HANDLES on both ends of the selection on the strip: drag outwards and the edge segment is repeated (everything attached to it stretches), drag inwards and the road is cut - like trimming a clip in a video editor."},
        {"PATH: grow the road, add bends, turn, delete, clipboard. HEIGHT: drag the squares on the chart.",
         "LAND: roadside terrain and centre line. PROPS: sprites left / right, 4 painted sprites of your own.",
         "MARKS: start, 3 checkpoints, finish. WORLD: theme, background, road texture, colours, name, author."},
        {"Ctrl+Z undo   Ctrl+Y or Ctrl+Shift+Z redo",
         "Ctrl+C copy   Ctrl+X cut   Ctrl+V paste (after the selection)   Ctrl+D duplicate",
         "Ctrl+A select all   Del or Backspace delete the selection",
         "Arrows: left / right = previous / next segment (Shift = extend the selection), up / down = 10 segments. PgUp / PgDn also jump 10. Home / End = first / last segment."},
        {"Ctrl+S save + install   Ctrl+E export zip   Ctrl+T test drive   Ctrl+F fly view",
         "Ctrl+N new map   Ctrl+O open an installed map   Esc = file menu",
         "1..6 = mode (PATH, HEIGHT, LAND, PROPS, MARKS, WORLD)   Tab / Shift+Tab = next / previous mode",
         "F = fit the view   + / - = zoom   H = this help"},
        {"FLY: your own camera in the real game renderer, no car. PC: W A S D move, Space or E up, Q or C down, Shift fast, Ctrl slow, drag the mouse to look, Home / End = start / finish, Esc leaves.",
         "Phone: joystick on the left half, drag anywhere else to look, UP / DOWN buttons, FAST, bottom bar jumps along the road.",
         "TEST drives the map with a car (time trial rules, no records)."},
        {"Tools: PENCIL, ERASER (sprites), FILL, PICK, LINE, RECT, OVAL (FILL toggle = solid), brush 1-3, MIRROR, FLIP H / V, ROLL (shifts the picture sideways to check seams).",
         "Colours: palette, RGB sliders or HEX. IMPORT reads PNG files from the folder addons/import (Android: Android/data/com.rally3d.android/files/addons/import).",
         "Backgrounds repeat left-right, so use the seamless templates. Road atlas = 8 tiles of 32 px: tile 0 plain road, tile 5 road with the centre line, the rest is the roadside."},
        {"SAVE installs the map: it is listed FIRST in Time Trial, Ghost Race and Multiplayer. EXPORT ZIP writes a copy anywhere (PC dialog / Android save dialog).",
         "Give the zip to anybody: Add-ons > + ZIP. There is no practical size limit for map zips.",
         "NEW FROM A TEMPLATE has 7 ready tracks to start from."}};

    private void help() { showHelp(0); }

    private void showHelp(int page) { /*T*/ final long $t = rally3d.core.Trace.in(523); try {
        helpPage = (page + HELP.length) % HELP.length;
        dlgTitle = rally3d.core.Lang.t("HELP ") + (helpPage + 1) + "/" + HELP.length + "  " + rally3d.core.Lang.t(HELP_TITLES[helpPage]);
        dlgLines = rally3d.core.Lang.tl(HELP[helpPage]);
        dlgBtns = new String[]{rally3d.core.Lang.t("PREV"), rally3d.core.Lang.t("NEXT"), rally3d.core.Lang.t("CLOSE")};
        dlgKind = DG_HELP;
        dlgScroll = 0;
     } finally { rally3d.core.Trace.out(523, $t); } }

    /** Runs a pending destructive action after asking about unsaved changes. */
    private void settle(int kind, int arg) { /*T*/ final long $t = rally3d.core.Trace.in(424); try {
        pendKind = kind; pendArg = arg;
        if (!dirty()) { proceed(); return; }
        dlgTitle = rally3d.core.Lang.t("UNSAVED CHANGES");
        dlgLines = new String[]{rally3d.core.Lang.t("The current map has changes that are not saved.")};
        dlgBtns = new String[]{rally3d.core.Lang.t("SAVE"), rally3d.core.Lang.t("DISCARD"), rally3d.core.Lang.t("CANCEL")};
        dlgKind = DG_DISCARD;
        dlgScroll = 0;
     } finally { rally3d.core.Trace.out(424, $t); } }

    private void dlgPick(int c) { /*T*/ final long $t = rally3d.core.Trace.in(425); try {
        int kind = dlgKind;
        dlgTitle = null; dlgLines = null; dlgBtns = null;
        if (kind == DG_DISCARD) {
            if (c == 0) { if (!save()) return; proceed(); }
            else if (c == 1) proceed();
        } else if (kind == DG_HELP) {
            if (c == 0) showHelp(helpPage - 1); else if (c == 1) showHelp(helpPage + 1);
        }
     } finally { rally3d.core.Trace.out(425, $t); } }

    private void proceed() { /*T*/ final long $t = rally3d.core.Trace.in(426); try {
        switch (pendKind) {
            case 1: md = new MapData(); break;
            case 2: md = MapData.fromStock(pendArg); break;
            case 5: md = MapTemplates.track(pendArg); break;
            case 3: { MapData m = MapData.fromPack(openList.get(pendArg)); if (m == null) { say(rally3d.core.Lang.t("Cannot open this map")); return; } md = m; break; }
            default: exit = true; return;
        }
        undo.clear(); redo.clear();
        savedRev = md.revision;
        selA = MapData.START_SEG + 1; selB = MapData.START_SEG + 8;
        thumbTheme = -9;
        fitAll();
        say(pendKind == 5 ? rally3d.core.Lang.t("Template: ") + md.name + " - " + md.desc : pendKind == 1 ? rally3d.core.Lang.t("New blank map") : pendKind == 2 ? rally3d.core.Lang.t("Stock track copied: ") + md.name : rally3d.core.Lang.t("Opened: ") + md.name);
     } finally { rally3d.core.Trace.out(426, $t); } }

    boolean save() { /*T*/ final long $t = rally3d.core.Trace.in(427); try {
        String bad = md.validate();
        if (bad != null) { say(bad); return false; }
        try {
            byte[] zip = md.toZip();
            AddonPack p = MapLibrary.install(zip);
            savedRev = md.revision;
            say(rally3d.core.Lang.t("Saved ") + p.file + " (" + (zip.length / 1024 + 1) + rally3d.core.Lang.t(" KB). First in the track lists."));
            return true;
        } catch (java.io.IOException e) { RLog.caught("MapEditor.save", e);
            say(rally3d.core.Lang.t("Cannot save: ") + e.getMessage());
            return false;
        }
     } finally { rally3d.core.Trace.out(427, $t); } }

    private void testDrive() { /*T*/ final long $t = rally3d.core.Trace.in(428); try {
        String bad = md.validate();
        if (bad != null) { say(bad); return; }
        AddonPack p;
        try { p = AddonPack.parse(md.toZip(), null); }
        catch (java.io.IOException e) { RLog.caught("MapEditor.testDrive", e); say(e.getMessage()); return; }
        if (p.error != null) { say(rally3d.core.Lang.t("Map error: ") + p.error); return; }
        javax.microedition.lcdui.Canvas.rawPointer = false;
        g.mapEditor = null;
        try { g.testDriveMap(p); }
        finally { reenter(); }
     } finally { rally3d.core.Trace.out(428, $t); } }

    /** Free-fly preview (FLY): real renderer, free camera; starts at the selected segment. */
    private void flyDrive() { /*T*/ final long $t = rally3d.core.Trace.in(524); try {
        String bad = md.validate();
        if (bad != null) { say(bad); return; }
        AddonPack p;
        try { p = AddonPack.parse(md.toZip(), null); }
        catch (java.io.IOException e) { RLog.caught("MapEditor.flyDrive", e); say(e.getMessage()); return; }
        if (p.error != null) { say(rally3d.core.Lang.t("Map error: ") + p.error); return; }
        javax.microedition.lcdui.Canvas.rawPointer = false;
        g.mapEditor = null;
        try { g.flyMap(p, Math.max(0, Math.min(md.n - 1, selA))); }
        catch (RuntimeException e) { RLog.caught("MapEditor.flyDrive", e); RLog.e("map", "fly failed: " + e); say("Fly view failed: " + e.getMessage()); }
        finally { reenter(); }
     } finally { rally3d.core.Trace.out(524, $t); } }

    /** EXPORT ZIP: the map pack saved wherever the player picks (PC dialog / Android save dialog); import it in Add-ons > + ZIP. */
    void exportZip() { /*T*/ final long $t = rally3d.core.Trace.in(525); try {
        String bad = md.validate();
        if (bad != null) { say(bad); return; }
        byte[] zip;
        try { zip = md.toZip(); }
        catch (java.io.IOException e) { RLog.caught("MapEditor.exportZip", e); say("Cannot export: " + e.getMessage()); return; }
        javax.microedition.lcdui.Canvas.rawPointer = false;
        g.mapEditor = null;
        String saved;
        try { saved = rally3d.desktop.Platform.exportFile(MapData.slug(md.name) + ".zip", zip); }
        finally { reenter(); }
        if (saved == null) say(rally3d.core.Lang.t("Export cancelled"));
        else { say("Exported " + saved + " (" + (zip.length / 1024 + 1) + " KB). Add it in Add-ons > + ZIP"); RLog.i("map", "exported map zip " + saved + " " + zip.length + " bytes"); }
     } finally { rally3d.core.Trace.out(525, $t); } }

    // ------------------------------------------------------------------ stretching (ball at the road end, handles on the strip)

    /** 1 = grab the left edge of the selection on the strip, 2 = the right edge, 0 = neither. */
    private int stripHandleAt(int x) { /*T*/ final long $t = rally3d.core.Trace.in(429); try {
        int sw = W - PW - 8;
        int a = 4 + (int) ((long) selA * sw / md.n), b = Math.max(a + 3, 4 + (int) ((long) (selB + 1) * sw / md.n));
        int da = Math.abs(x - a), db = Math.abs(x - b);
        if (da > 9 && db > 9) return 0;
        return db <= da ? 2 : 1;
     } finally { rally3d.core.Trace.out(429, $t); } }

    private boolean endBallHit(int x, int y) {
        md.geo();
        return Math.hypot(sx(md.gx[md.n]) - x, sy(md.gz[md.n]) - y) <= 15;
    }

    private void beginDrag(int slot, int kind) { /*T*/ final long $t = rally3d.core.Trace.in(430); try {
        push();
        dragKind = kind; dragSlot = slot;
        dragSnap = md.copy();
        dragOldCtrl = md.sampleCtrl();
        trimSelA = selA; trimSelB = selB;
     } finally { rally3d.core.Trace.out(430, $t); } }

    /** Pulling the end ball: the road grows towards the pointer (carrying terrain, centre line and prop pattern of its last segment); pulling it back along the road shortens it. */
    private void endDrag(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(431); try {
        md.restore(dragSnap);
        double wx = camX - (x - (vx + vw / 2)) / zoom, wz = camZ - (y - (vy + vh / 2)) / zoom;
        int base = md.n;
        int near = nearestSegment(wx, wz, 16.0 / zoom);
        if (near >= 0 && near < base - 2) {   // pointer is on the road itself: trim back to there
            int keep = Math.max(MapData.MIN_SEGS - 1, near);
            if (keep < base - 1) {
                md.delete(keep + 1, base - 1);
                md.refitAfterResize(dragOldCtrl, keep + 1, -(base - 1 - keep));
            }
            setSel(md.n - 1, md.n - 1);
            return;
        }
        int added = md.extendToward(wx, wz);
        if (added > 0) {
            md.inheritProps(base, md.n - 1);
            md.refitAfterResize(dragOldCtrl, base, added);
            setSel(base, md.n - 1);
        } else setSel(base - 1, base - 1);
     } finally { rally3d.core.Trace.out(431, $t); } }

    /** Dragging a selection edge on the strip: stretches the edge segment (everything attached to it is repeated) or cuts the selection shorter. */
    private void trimDrag(int x) { /*T*/ final long $t = rally3d.core.Trace.in(432); try {
        int sw = W - PW - 8, n0 = dragSnap.n;
        int t = (int) Math.round((x - 4) * (double) n0 / sw);
        md.restore(dragSnap);
        if (dragKind == 3) {   // right edge
            int end = Math.max(trimSelA + 1, Math.min(n0, t));
            int delta = end - (trimSelB + 1);
            if (delta > 0) {
                int got = md.stretch(trimSelB, trimSelB + 1, delta);
                md.refitAfterResize(dragOldCtrl, trimSelB + 1, got);
                setSel(trimSelA, trimSelB + got);
            } else if (delta < 0) {
                int cnt = Math.min(-delta, Math.max(0, n0 - MapData.MIN_SEGS));
                if (cnt > 0) {
                    md.delete(trimSelB + 1 - cnt, trimSelB);
                    md.refitAfterResize(dragOldCtrl, trimSelB + 1 - cnt, -cnt);
                }
                setSel(trimSelA, trimSelB - cnt);
            } else setSel(trimSelA, trimSelB);
        } else {   // left edge
            int start = Math.max(0, Math.min(trimSelB, t));
            int delta = trimSelA - start;
            if (delta > 0) {
                int got = md.stretch(trimSelA, trimSelA, delta);
                md.refitAfterResize(dragOldCtrl, trimSelA, got);
                setSel(trimSelA, trimSelB + got);
            } else if (delta < 0) {
                int cnt = Math.min(-delta, Math.max(0, n0 - MapData.MIN_SEGS));
                if (cnt > 0) {
                    md.delete(trimSelA, trimSelA + cnt - 1);
                    md.refitAfterResize(dragOldCtrl, trimSelA, -cnt);
                }
                setSel(trimSelA, trimSelB - cnt);
            } else setSel(trimSelA, trimSelB);
        }
     } finally { rally3d.core.Trace.out(432, $t); } }

    // ------------------------------------------------------------------ keyboard shortcuts

    /** Keyboard shortcuts. Key RELEASES (mods bit 8) and the "all keys let go" event (code -1) are for the fly view and are ignored here. */
    private void rawKey(int code, int mods, int ch) { /*T*/ final long $t = rally3d.core.Trace.in(433); try {
        if ((mods & 8) != 0 || code < 0) return;
        boolean ctrl = (mods & 1) != 0, shift = (mods & 2) != 0;
        if (painter != null) { painter.rawKey(code, ctrl, shift); return; }
        if (dlgTitle != null) {
            if (dlgKind == DG_HELP) {
                if (code == 27) dlgPick(2);
                else if (code == 38) listDrag(sh * 2);
                else if (code == 40) listDrag(-sh * 2);
                else if (code == 37 || code == 33) dlgPick(0);
                else if (code == 39 || code == 34 || code == 10 || code == 32) { if (helpPage >= HELP.length - 1 && code == 10) dlgPick(2); else dlgPick(1); }
            } else if (code == 27) dlgPick(dlgBtns.length - 1);
            else if (code == 10) dlgPick(0);
            return;
        }
        if (listItems != null) { if (code == 27) listClose(); return; }
        if (palette != null) { if (code == 27) palette = null; return; }
        if (ctrl) {
            switch (code) {
                case 'Z': if (shift) doRedo(); else doUndo(); break;
                case 'Y': doRedo(); break;
                case 'C': copySel(); break;
                case 'X': cutSel(); break;
                case 'V': pasteSel(); break;
                case 'D': dupSel(); break;
                case 'A': setSel(0, md.n - 1); break;
                case 'S': save(); break;
                case 'E': exportZip(); break;
                case 'T': testDrive(); break;
                case 'F': flyDrive(); break;
                case 'N': settle(1, 0); break;
                case 'O': openInstalledList(); break;
                default: break;
            }
            return;
        }
        switch (code) {
            case 27: fileMenu(); break;
            case 127: case 8: deleteRange(selA, selB); break;
            case 37: if (shift) setSel(selA, Math.max(selA, selB - 1)); else setSel(selA - 1, selA - 1); break;
            case 39: if (shift) setSel(selA, selB + 1); else setSel(selB + 1, selB + 1); break;
            case 38: case 33: setSel(selA - 10, selA - 10); break;
            case 40: case 34: setSel(selB + 10, selB + 10); break;
            case 36: if (shift) setSel(0, selB); else setSel(0, 0); centerOn(0); break;                       // Home
            case 35: if (shift) setSel(selA, md.n - 1); else setSel(md.n - 1, md.n - 1); centerOn(md.n - 1); break;   // End
            case 9: setMode((mode + (shift ? 5 : 1)) % 6); break;
            case 'F': fitView(); break;
            case 'H': help(); break;
            case 61: case 107: case 521: zoomView(1.4, vx + vw / 2, vy + vh / 2); break;
            case 45: case 109: zoomView(1 / 1.4, vx + vw / 2, vy + vh / 2); break;
            default:
                if (code >= '1' && code <= '6') setMode(code - '1');
                else if (code >= 97 && code <= 102) setMode(code - 97);   // numpad 1..6
                break;
        }
     } finally { rally3d.core.Trace.out(433, $t); } }

    private void copySel() {
        clip = md.copyRange(selA, selB);
        say(rally3d.core.Lang.t("Copied ") + clip.n + rally3d.core.Lang.t(" segments"));
    }

    private void cutSel() { /*T*/ final long $t = rally3d.core.Trace.in(526); try {
        if (md.n - (selB - selA + 1) < MapData.MIN_SEGS) { say(rally3d.core.Lang.t("A map needs at least ") + MapData.MIN_SEGS + rally3d.core.Lang.t(" segments")); return; }
        clip = md.copyRange(selA, selB);
        int cnt = clip.n;
        deleteRange(selA, selB);
        say(rally3d.core.Lang.t("Cut ") + cnt + rally3d.core.Lang.t(" segments - Ctrl+V pastes them"));
     } finally { rally3d.core.Trace.out(526, $t); } }

    private void dupSel() { pasteClip(md.copyRange(selA, selB), rally3d.core.Lang.t("Duplicated")); }

    /** Inserts the clipboard after the selection (height of the pasted stretch stays level). */
    private void pasteSel() { /*T*/ final long $t = rally3d.core.Trace.in(434); try {
        if (clip == null || clip.n == 0) { say(rally3d.core.Lang.t("Clipboard is empty - Ctrl+C a selection first")); return; }
        pasteClip(clip, rally3d.core.Lang.t("Pasted"));
     } finally { rally3d.core.Trace.out(434, $t); } }

    private void pasteClip(MapData.Clip clip, String verb) { /*T*/ final long $t = rally3d.core.Trace.in(563); try {
        push();
        float[] old = md.sampleCtrl();
        int at = selB >= md.n - 1 ? md.n : selB + 1;
        int got = md.pasteRange(clip, at);
        if (got <= 0) { cancelPush(); say(rally3d.core.Lang.t("Map is at the size limit (") + MapData.MAX_SEGS + ")"); return; }
        md.refitAfterResize(old, at, got);
        setSel(at, at + got - 1);
        say(verb + " " + got + rally3d.core.Lang.t(" segments"));  } finally { rally3d.core.Trace.out(563, $t); } }

    // ------------------------------------------------------------------ drawing helpers (package-private: the painter shares them)

    void fill(Graphics gr, int x, int y, int w, int h, int argb) { /*T*/ final long $t = rally3d.core.Trace.in(435); try {
        if (w <= 0 || h <= 0) return;
        if ((argb >>> 24) == 255) { gr.setColor(argb & 0xFFFFFF); gr.fillRect(x, y, w, h); } else gr.fillRectAlpha(x, y, w, h, argb);
     } finally { rally3d.core.Trace.out(435, $t); } }

    int textW(String s, boolean small) { return (small ? g.fontSmall : g.fontBold).stringWidth(s); }

    void text(Graphics gr, String s, int x, int y, int color, boolean small) { /*T*/ final long $t = rally3d.core.Trace.in(436); try {
        gr.setFont(small ? g.fontSmall : g.fontBold);
        gr.setColor(0x000000);
        gr.drawString(s, x + 1, y + 1, Graphics.TOP | Graphics.LEFT);
        gr.setColor(color & 0xFFFFFF);
        gr.drawString(s, x, y, Graphics.TOP | Graphics.LEFT);
     } finally { rally3d.core.Trace.out(436, $t); } }

    void textC(Graphics gr, String s, int cx, int y, int color, boolean small) { text(gr, s, cx - textW(s, small) / 2, y, color, small); }

    void frame(Graphics gr, int x, int y, int w, int h, int argb) { /*T*/ final long $t = rally3d.core.Trace.in(437); try {
        gr.setColor(argb & 0xFFFFFF);
        gr.drawLine(x, y, x + w - 1, y); gr.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        gr.drawLine(x, y, x, y + h - 1); gr.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
     } finally { rally3d.core.Trace.out(437, $t); } }

    /** Draws a button and registers its hit box. Label is shortened to fit. */
    void button(Graphics gr, int x, int y, int w, int h, String label, int id, int arg, boolean on) { buttonX(gr, x, y, w, h, label, id, arg, on, true); }

    /** Same, with a greyed-out look when {@code enabled} is false (the hit box stays so a tap can explain why nothing happens). */
    void buttonX(Graphics gr, int x, int y, int w, int h, String label, int id, int arg, boolean on, boolean enabled) { /*T*/ final long $t = rally3d.core.Trace.in(438); try {
        fill(gr, x, y, w, h, !enabled ? 0xFF2A3540 : on ? BTN_ON : BTN);
        frame(gr, x, y, w, h, !enabled ? 0xFF3E4C5A : on ? WHITE : 0xFF5A7088);
        String s = label;
        while (s.length() > 1 && textW(s, true) > w - 4) s = s.substring(0, s.length() - 1);
        textC(gr, s, x + w / 2, y + (h - sh) / 2, !enabled ? DIM : on ? YEL : WHITE, true);
        addHit(x, y, w, h, id, arg);
     } finally { rally3d.core.Trace.out(438, $t); } }

    /** Registers a hit box, cut to the scrolling band (clipY0..clipY1) when one is active, so half-visible buttons work and never cover the fixed ones. */
    private void addHit(int x, int y, int w, int h, int id, int arg) { /*T*/ final long $t = rally3d.core.Trace.in(560); try {
        if (clipY0 >= 0) {
            int y0 = Math.max(y, clipY0), y1 = Math.min(y + h, clipY1);
            if (y1 - y0 < 4) return;
            y = y0; h = y1 - y0;
        }
        btns.add(new int[]{x, y, w, h, id, arg});
     } finally { rally3d.core.Trace.out(560, $t); } }

    private void box(Graphics gr, int x, int y, int w, int h) {
        fill(gr, x - 1, y - 1, w + 2, h + 2, BLACK);
        fill(gr, x, y, w, h, 0xFF2E3E4C);
        frame(gr, x, y, w, h, WHITE);
    }

    // ------------------------------------------------------------------ frame

    void draw(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(439); try {
        layout();
        btns.clear();
        gr.setClip(0, 0, W, H);
        fill(gr, 0, 0, W, H, BG);
        if (painter != null) {
            painter.draw(gr);
        } else {
            gr.setClip(vx, vy, vw, vh);
            if (mode == M_HEIGHT) drawHeight(gr); else drawTop(gr);
            gr.setClip(0, 0, W, H);
            drawViewTools(gr);
            drawStrip(gr);
            drawPanel(gr);
            drawTopBar(gr);
            drawHint(gr);
        }
        drawOverlays(gr);
        if (toast != null && System.currentTimeMillis() < toastUntil) {
            int tw = Math.min(W - 8, textW(toast, true) + 12);
            int tx = (W - tw) / 2, ty = H - SB - 2 * sh - 22;
            fill(gr, tx, ty, tw, sh + 6, 0xE0101820);
            frame(gr, tx, ty, tw, sh + 6, YEL);
            String s = toast;
            while (s.length() > 3 && textW(s, true) > tw - 8) s = s.substring(0, s.length() - 1);
            textC(gr, s, W / 2, ty + 3, YEL, true);
        }
     } finally { rally3d.core.Trace.out(439, $t); } }


    // ------------------------------------------------------------------ hint bar + tooltips

    private static final String[] MODE_HINTS = {
        "PATH: tap road = select. Drag orange ball = grow road.",
        "HEIGHT: drag the yellow squares. Red road = too steep.",
        "LAND: select on the strip, then pick a roadside type.",
        "PROPS: select, pick a sprite, press PLACE.",
        "MARKS: select a segment, then press SET.",
        "WORLD: theme, background, road, colours, name."};

    private String tipFor(int id) { /*T*/ final long $t = rally3d.core.Trace.in(561); try {
        if (id >= A_MODE && id < A_MODE + 6) return rally3d.core.Lang.t(MODE_NAMES[id - A_MODE]) + rally3d.core.Lang.t(" mode  (key ") + (id - A_MODE + 1) + ")";
        if (id >= A_PIECE && id < A_PIECE + PIECES.length) return rally3d.core.Lang.t("Insert ") + rally3d.core.Lang.t(PIECE_NAMES[id - A_PIECE]) + rally3d.core.Lang.t(" after the selection");
        switch (id) {
            case A_FILE: return rally3d.core.Lang.t("File menu: save, export, new, open, help  (Esc)");
            case A_UNDO: return rally3d.core.Lang.t("Undo  (Ctrl+Z)");
            case A_REDO: return rally3d.core.Lang.t("Redo  (Ctrl+Y)");
            case A_TEST: return rally3d.core.Lang.t("Drive the map with a car  (Ctrl+T)");
            case A_FLY: return rally3d.core.Lang.t("Free camera fly-through  (Ctrl+F)");
            case A_SAVE: return rally3d.core.Lang.t("Save and install the map  (Ctrl+S)");
            case A_HELP: return rally3d.core.Lang.t("How to use the editor  (H)");
            case A_FIT: return rally3d.core.Lang.t("Fit the whole map in view  (F)");
            case A_ZIN: return rally3d.core.Lang.t("Zoom in  (+ or wheel)");
            case A_ZOUT: return rally3d.core.Lang.t("Zoom out  (- or wheel)");
            case A_EXT: return rally3d.core.Lang.t("ON: tapping empty ground grows the road towards it");
            case A_TURNL: return rally3d.core.Lang.t("Turn the selected segments a bit to the left");
            case A_TURNR: return rally3d.core.Lang.t("Turn the selected segments a bit to the right");
            case A_STRAIGHT: return rally3d.core.Lang.t("Remove all turning from the selection");
            case A_DELETE: return rally3d.core.Lang.t("Delete the selected segments  (Del)");
            case A_TRIM: return rally3d.core.Lang.t("Delete everything after the selection");
            case A_COPY: return rally3d.core.Lang.t("Copy the selection  (Ctrl+C)");
            case A_CUT: return rally3d.core.Lang.t("Cut the selection  (Ctrl+X)");
            case A_PASTE: return rally3d.core.Lang.t("Paste after the selection  (Ctrl+V)");
            case A_DUP: return rally3d.core.Lang.t("Duplicate the selection  (Ctrl+D)");
            case A_ALL: return rally3d.core.Lang.t("Select the whole road  (Ctrl+A)");
            case A_APPLY: return rally3d.core.Lang.t("Put the chosen sprite on the selected segments");
            case A_CLEARP: return rally3d.core.Lang.t("Remove all props from the selection");
            case A_CPAUTO: return rally3d.core.Lang.t("Spread the 3 checkpoints evenly");
            case A_COLAUTO: return rally3d.core.Lang.t("Use the theme's own sky / ground / HUD colours");
            default: break;
        }
        if (id >= A_H && id < A_H + 8) {
            String[] t = {rally3d.core.Lang.t("Raise the selected part by 10"), rally3d.core.Lang.t("Lower the selected part by 10"), rally3d.core.Lang.t("Make a hill over the selection"), rally3d.core.Lang.t("Make a valley over the selection"), rally3d.core.Lang.t("Level the selection with the point before it"), rally3d.core.Lang.t("Smooth out bumps in the selection"), rally3d.core.Lang.t("Set the whole road to height 0"), rally3d.core.Lang.t("Fit the chart in view")};
            return t[id - A_H];
        }
        if (id >= A_CP && id < A_CP + 4) return rally3d.core.Lang.t("Put this marker on the selected segment");
        if (id >= A_CPGO && id < A_CPGO + 5) return rally3d.core.Lang.t("Jump to this marker");
        return null;
     } finally { rally3d.core.Trace.out(561, $t); } }

    private void drawHint(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(562); try {
        String t = null;
        if (dlgTitle == null && listItems == null && palette == null && painter == null && hoverX >= 0 && System.currentTimeMillis() - hoverT < 2500) {
            for (int i = btns.size() - 1; i >= 0; i--) {
                int[] b = btns.get(i);
                if (hoverX >= b[0] && hoverX < b[0] + b[2] && hoverY >= b[1] && hoverY < b[1] + b[3]) { t = tipFor(b[4]); break; }
            }
        }
        boolean tip = t != null;
        if (t == null) t = rally3d.core.Lang.t(MODE_HINTS[mode]);
        int bw2 = vw - 8, y = vy + vh - sh - 6;
        fill(gr, vx + 4, y - 2, bw2, sh + 4, tip ? 0xE0203040 : 0xA0101820);
        if (textW(t, true) > bw2 - 8) {
            while (t.length() > 3 && textW(t + "...", true) > bw2 - 8) t = t.substring(0, t.length() - 1);
            t = t + "...";
        }
        text(gr, t, vx + 8, y, tip ? WHITE : YEL, true);
     } finally { rally3d.core.Trace.out(562, $t); } }

    private void drawTopBar(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(440); try {
        fill(gr, 0, 0, W, TB, PANEL);
        gr.setColor(0x5A7088); gr.drawLine(0, TB - 1, W, TB - 1);
        int pad = W < 420 ? 8 : 12;
        int x = 2, bh = TB - 3, bw = Math.max(28, textW(rally3d.core.Lang.t("TEST"), true) + pad), qw = Math.max(bh, textW("?", true) + pad);
        // fixed positions: the buttons never jump around when REDO becomes available
        buttonX(gr, x, 1, bw, bh, rally3d.core.Lang.t("FILE"), A_FILE, 0, false, true); x += bw + 2;
        buttonX(gr, x, 1, bw, bh, rally3d.core.Lang.t("UNDO"), A_UNDO, 0, false, !undo.isEmpty()); x += bw + 2;
        buttonX(gr, x, 1, bw, bh, rally3d.core.Lang.t("REDO"), A_REDO, 0, false, !redo.isEmpty()); x += bw + 2;
        int rx = W - 2 - bw;
        button(gr, rx, 1, bw, bh, rally3d.core.Lang.t("SAVE"), A_SAVE, 0, dirty()); rx -= bw + 2;
        button(gr, rx, 1, bw, bh, rally3d.core.Lang.t("TEST"), A_TEST, 0, false); rx -= bw + 2;
        button(gr, rx, 1, bw, bh, rally3d.core.Lang.t("FLY"), A_FLY, 0, false); rx -= qw + 2;
        button(gr, rx, 1, qw, bh, "?", A_HELP, 0, false);
        String t = md.name + (dirty() ? " *" : "");
        int room = rx - x - 8;
        if (room > 24) {
            while (t.length() > 3 && textW(t, true) > room) t = t.substring(0, t.length() - 1);
            text(gr, t, x + 6, (TB - sh) / 2, YEL, true);
        }
     } finally { rally3d.core.Trace.out(440, $t); } }

    private void drawViewTools(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(441); try {
        // zoom / fit buttons in the viewport corner
        int bw = 26, bh = 22, x = vx + vw - bw - 3, y = vy + 3;
        button(gr, x, y, bw, bh, "+", A_ZIN, 0, false);
        button(gr, x, y + bh + 2, bw, bh, "-", A_ZOUT, 0, false);
        button(gr, x, y + (bh + 2) * 2, bw, bh, rally3d.core.Lang.t("FIT"), A_FIT, 0, false);
        // info line
        md.geo();
        int a = Math.max(0, Math.min(md.n - 1, selA)), b = Math.max(a, Math.min(md.n - 1, selB));
        String info = rally3d.core.Lang.t("SEG ") + a + (b > a ? "-" + b : "") + "/" + md.n + "  H " + Math.round(md.heightAt(a)) + rally3d.core.Lang.t("  GRADE ") + Math.round(md.slopeAt(a) / 4 * 100) + rally3d.core.Lang.t("%  TURN ") + Math.round(md.yaw[a] * MapData.DEG_PER_YAW) + "\u00B0";
        fill(gr, vx + 2, vy + 2, Math.min(vw - 30, textW(info, true) + 8), sh + 4, 0xB0101820);
        text(gr, info, vx + 6, vy + 4, WHITE, true);
        int steep = md.firstSteep(2.6);
        if (steep >= 0 && mode != M_HEIGHT) text(gr, rally3d.core.Lang.t("TOO STEEP at segment ") + steep + rally3d.core.Lang.t(" (HEIGHT mode)"), vx + 6, vy + sh + 8, RED, true);
     } finally { rally3d.core.Trace.out(441, $t); } }

    private int sx(double wx) { return (int) Math.round(vx + vw / 2 - (wx - camX) * zoom); }
    private int sy(double wz) { return (int) Math.round(vy + vh / 2 - (wz - camZ) * zoom); }

    private int propColor(int t) { /*T*/ final long $t = rally3d.core.Trace.in(442); try {
        if (t == -1) return 0xFF70D070;
        if (t == 0) return 0xFF30A040;
        if (t == 1) return 0xFF208060;
        if (t == 2) return 0xFFB09060;
        if (t == 3) return WHITE;
        if (t == 5) return 0xFFFF9030;
        if (t == 6) return 0xFFFF80C0;
        return 0xFF40D0F0;
     } finally { rally3d.core.Trace.out(442, $t); } }

    private void drawTop(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(443); try {
        int gc = md.groundColor();
        int r = ((gc >> 16) & 255) / 3 + 20, gg = ((gc >> 8) & 255) / 3 + 20, bb = (gc & 255) / 3 + 20;
        fill(gr, vx, vy, vw, vh, 0xFF000000 | (r << 16) | (gg << 8) | bb);
        md.geo();
        // grid every 500 world units
        double step = zoom < 0.03 ? 2000 : 500;
        double wl = camX + (vw / 2) / zoom, wr = camX - (vw / 2) / zoom, wb = camZ - (vh / 2) / zoom, wt = camZ + (vh / 2) / zoom;
        gr.setColor(GRID & 0xFFFFFF);
        for (double x = Math.floor(wr / step) * step; x <= wl; x += step) { int s = sx(x); gr.drawLine(s, vy, s, vy + vh); }
        for (double z = Math.floor(wb / step) * step; z <= wt; z += step) { int s = sy(z); gr.drawLine(vx, s, vx + vw, s); }
        int rad = (int) Math.max(1, Math.min(3, Math.round(7 * zoom)));
        boolean props = zoom > 0.035;
        for (int i = 0; i < md.n; i++) {
            int x0 = sx(md.gx[i]), y0 = sy(md.gz[i]), x1 = sx(md.gx[i + 1]), y1 = sy(md.gz[i + 1]);
            if (Math.max(x0, x1) < vx - 4 || Math.min(x0, x1) > vx + vw + 4 || Math.max(y0, y1) < vy - 4 || Math.min(y0, y1) > vy + vh + 4) continue;
            boolean sel = i >= selA && i <= selB;
            int col;
            if (sel) col = 0xFFFFE040;
            else if (mode == M_LAND) col = TERRAIN_COLORS[terrainIndex(md.tgt[i])];
            else col = 0xFFD4D4D4;
            gr.setColor(col & 0xFFFFFF);
            double ddx = x1 - x0, ddy = y1 - y0, len = Math.max(1, Math.hypot(ddx, ddy));
            int rr = rad + (sel ? 1 : 0);
            for (int k = -rr; k <= rr; k++) gr.drawLine((int) Math.round(x0 - ddy / len * k), (int) Math.round(y0 + ddx / len * k), (int) Math.round(x1 - ddy / len * k), (int) Math.round(y1 + ddx / len * k));
            if (mode == M_LAND && md.cm[i] != 0 && rr >= 1) { gr.setColor(0xFFFFFF); gr.drawLine(x0, y0, x1, y1); }
            if (props && (md.lt[i] != -2 || md.rt[i] != -2)) {
                double dl = Math.hypot(md.gdx[i], md.gdz[i]);
                double nx = md.gdz[i] / dl, nz = -md.gdx[i] / dl;
                if (md.lt[i] != -2) { double off = 9 + md.ll[i] * 7; dot(gr, sx(md.gx[i] + nx * off), sy(md.gz[i] + nz * off), propColor(md.lt[i])); }
                if (md.rt[i] != -2) { double off = 9 + md.rl[i] * 7; dot(gr, sx(md.gx[i] - nx * off), sy(md.gz[i] - nz * off), propColor(md.rt[i])); }
            }
        }
        // markers
        marker(gr, MapData.START_SEG, "S", GREEN);
        for (int k = 0; k < 3; k++) marker(gr, md.effCp(k), String.valueOf(k + 1), YEL);
        marker(gr, md.effCp(3), "F", RED);
        // road end + scale bar
        int ex = sx(md.gx[md.n]), ey = sy(md.gz[md.n]);
        if (dragKind == 1) { gr.setColor(0xFFFFFF); gr.drawLine(ex - 14, ey, ex + 14, ey); gr.drawLine(ex, ey - 14, ex, ey + 14); }
        // the ball: grab it and pull in any direction - the road grows that way carrying the terrain / props of its last segment
        fill(gr, ex - 6, ey - 6, 13, 13, 0xFFFF8020);
        fill(gr, ex - 4, ey - 4, 9, 9, 0xFFFFC060);
        frame(gr, ex - 6, ey - 6, 13, 13, BLACK);
        int bar = (int) Math.round(200 * zoom), by0 = vy + vh - sh - 18;
        gr.setColor(0xFFFFFF); gr.drawLine(vx + 8, by0, vx + 8 + bar, by0);
        gr.drawLine(vx + 8, by0 - 3, vx + 8, by0 + 3); gr.drawLine(vx + 8 + bar, by0 - 3, vx + 8 + bar, by0 + 3);
        text(gr, "~150 m", vx + 12, by0 - sh - 2, WHITE, true);
     } finally { rally3d.core.Trace.out(443, $t); } }

    private void dot(Graphics gr, int x, int y, int col) { gr.setColor(col & 0xFFFFFF); gr.fillRect(x - 1, y - 1, 3, 3); }

    private void marker(Graphics gr, int seg, String label, int col) { /*T*/ final long $t = rally3d.core.Trace.in(444); try {
        if (seg < 0 || seg > md.n) return;
        int x = sx(md.gx[seg]), y = sy(md.gz[seg]);
        if (x < vx - 8 || x > vx + vw + 8 || y < vy - 8 || y > vy + vh + 8) return;
        fill(gr, x - 5, y - 5, 11, 11, col);
        frame(gr, x - 5, y - 5, 11, 11, BLACK);
        textC(gr, label, x, y - sh / 2, BLACK, true);
     } finally { rally3d.core.Trace.out(444, $t); } }

    static int terrainIndex(int t) { for (int i = 0; i < MapData.TERRAIN.length; i++) if (MapData.TERRAIN[i] == t) return i; return 0; }

    private void drawHeight(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(445); try {
        fill(gr, vx, vy, vw, vh, 0xFF141C22);
        md.geo();
        int bot = vy + vh;
        // horizontal grid every 50 height units
        double top = vOff + vh / vPx;
        gr.setColor(GRID & 0xFFFFFF);
        for (double h = Math.ceil(vOff / 50) * 50; h <= top; h += 50) {
            int y = (int) Math.round(bot - (h - vOff) * vPx);
            gr.drawLine(vx, y, vx + vw, y);
            text(gr, String.valueOf((int) h), vx + 2, y - sh - 1, DIM, true);
        }
        // selection + markers
        int sa = (int) Math.round(vx + (selA - hOff) * hPx), sb = (int) Math.round(vx + (selB + 1 - hOff) * hPx);
        fill(gr, sa, vy, Math.max(2, sb - sa), vh, 0x40FFFFAA);
        int[] ms = {MapData.START_SEG, md.effCp(0), md.effCp(1), md.effCp(2), md.effCp(3)};
        String[] ml = {"S", "1", "2", "3", "F"};
        for (int k = 0; k < 5; k++) {
            int x = (int) Math.round(vx + (ms[k] - hOff) * hPx);
            gr.setColor(k == 0 ? 0x60D060 : k == 4 ? 0xE05050 : 0xFFFFAA);
            gr.drawLine(x, vy, x, bot);
            text(gr, ml[k], x + 2, vy + 2, k == 0 ? GREEN : k == 4 ? RED : YEL, true);
        }
        // the simulated road height
        for (int i = 0; i < md.n; i++) {
            int x0 = (int) Math.round(vx + (i - hOff) * hPx), x1 = (int) Math.round(vx + (i + 1 - hOff) * hPx);
            if (x1 < vx || x0 > vx + vw) continue;
            int y0 = (int) Math.round(bot - (md.gh[i] - vOff) * vPx), y1 = (int) Math.round(bot - (md.gh[i + 1] - vOff) * vPx);
            gr.setColor(Math.abs(md.gs[i]) > 2.6 ? 0xFF4040 : 0xE8E8E8);
            gr.drawLine(x0, y0, x1, y1); gr.drawLine(x0, y0 + 1, x1, y1 + 1);
        }
        float[] c = md.sampleCtrl();
        for (int k = 0; k < c.length; k++) {
            int x = (int) Math.round(vx + (k * MapData.CTRL_STEP - hOff) * hPx), y = (int) Math.round(bot - (c[k] - vOff) * vPx);
            if (x < vx - 6 || x > vx + vw + 6) continue;
            fill(gr, x - 4, y - 4, 9, 9, k == 0 ? 0xFF808080 : YEL);
            frame(gr, x - 4, y - 4, 9, 9, BLACK);
        }
     } finally { rally3d.core.Trace.out(445, $t); } }

    private void drawStrip(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(446); try {
        int y = H - SB, sw = W - PW - 8;
        fill(gr, 0, y, W - PW, SB, PANEL);
        gr.setColor(0x5A7088); gr.drawLine(0, y, W - PW, y);
        int by = y + 3, bh = SB - 6;
        for (int x = 0; x < sw; x++) {
            int seg = Math.min(md.n - 1, (int) ((long) x * md.n / sw));
            int col = TERRAIN_COLORS[terrainIndex(md.tgt[seg])];
            gr.setColor(col & 0xFFFFFF);
            gr.drawLine(4 + x, by, 4 + x, by + bh - 1);
        }
        // mini height silhouette
        md.geo();
        double mn = 0, mx = 1;
        for (int i = 0; i <= md.n; i++) { mn = Math.min(mn, md.gh[i]); mx = Math.max(mx, md.gh[i]); }
        gr.setColor(0xFFFFFF);
        int prevY = -1;
        for (int x = 0; x < sw; x++) {
            int seg = Math.min(md.n, (int) ((long) x * md.n / sw));
            int yy = by + bh - 1 - (int) ((md.gh[seg] - mn) / Math.max(1, mx - mn) * (bh - 1));
            if (prevY >= 0) gr.drawLine(3 + x, prevY, 4 + x, yy);
            prevY = yy;
        }
        int a = 4 + (int) ((long) selA * sw / md.n), b = 4 + (int) ((long) (selB + 1) * sw / md.n);
        fill(gr, a, by - 2, Math.max(3, b - a), bh + 4, 0x70FFFFAA);
        frame(gr, a, by - 2, Math.max(3, b - a), bh + 4, YEL);
        int[] ms = {MapData.START_SEG, md.effCp(0), md.effCp(1), md.effCp(2), md.effCp(3)};
        for (int k = 0; k < 5; k++) {
            int x = 4 + (int) ((long) ms[k] * sw / md.n);
            gr.setColor(k == 0 ? 0x60D060 : k == 4 ? 0xE05050 : 0xFFFFAA);
            gr.drawLine(x, y + 1, x, y + SB - 2);
        }
        // trim handles on both edges of the selection: drag to stretch / shrink it like a clip on a timeline
        int hw = 5;
        fill(gr, a - hw + 1, by - 3, hw, bh + 6, YEL);
        frame(gr, a - hw + 1, by - 3, hw, bh + 6, BLACK);
        fill(gr, Math.max(a + 3, b) - 1, by - 3, hw, bh + 6, YEL);
        frame(gr, Math.max(a + 3, b) - 1, by - 3, hw, bh + 6, BLACK);
     } finally { rally3d.core.Trace.out(446, $t); } }

    // ------------------------------------------------------------------ side panel

    private int pcx, pcy, pcw, ptop, pbottom;

    private void drawPanel(Graphics gr) {
        int x0 = W - PW;
        fill(gr, x0, TB, PW, H - TB, PANEL);
        gr.setColor(0x5A7088); gr.drawLine(x0, TB, x0, H);
        // mode tabs (fixed)
        int tw = (PW - 6) / 2, th = sh + 7;
        for (int m = 0; m < 6; m++) button(gr, x0 + 2 + (m % 2) * (tw + 2), TB + 2 + (m / 2) * (th + 2), tw, th, rally3d.core.Lang.t(MODE_NAMES[m]), A_MODE + m, 0, m == mode);
        ptop = TB + 2 + 3 * (th + 2) + 2;
        pbottom = H;
        gr.setClip(x0, ptop, PW, pbottom - ptop);
        pcx = x0 + 3; pcw = PW - 6; pcy = ptop - panelScroll;
        clipY0 = ptop; clipY1 = pbottom;
        switch (mode) {
            case M_PATH: panelPath(gr); break;
            case M_HEIGHT: panelHeight(gr); break;
            case M_LAND: panelLand(gr); break;
            case M_PROPS: panelProps(gr); break;
            case M_MARKS: panelMarks(gr); break;
            default: panelWorld(gr); break;
        }
        panelContent = pcy + panelScroll - ptop + 4;
        clipY0 = -1; clipY1 = -1;
        panelScroll = Math.max(0, Math.min(Math.max(0, panelContent - (pbottom - ptop)), panelScroll));
        gr.setClip(0, 0, W, H);
        if (panelContent > pbottom - ptop) {   // scroll hint
            int hh = Math.max(10, (pbottom - ptop) * (pbottom - ptop) / panelContent), yy = ptop + (pbottom - ptop - hh) * panelScroll / Math.max(1, panelContent - (pbottom - ptop));
            fill(gr, W - 2, yy, 2, hh, 0xB0FFFFAA);
        }
    }

    private int bh() { return sh + 8; }
    int bh0() { return bh(); }

    private void label(Graphics gr, String s) {
        pcy += 3;
        wrapText(gr, s, YEL, 1);
        pcy += 2;
    }

    private void note(Graphics gr, String s, int color) { wrapText(gr, s, color, 3); }

    /** Word-wrapped small text in the panel (only rows that are on screen are drawn). */
    private void wrapText(Graphics gr, String s, int color, int after) { /*T*/ final long $t = rally3d.core.Trace.in(447); try {
        int maxW = pcw;
        String cur = "";
        for (String w : s.split(" ")) {
            String t = cur.length() == 0 ? w : cur + " " + w;
            if (textW(t, true) <= maxW || cur.length() == 0) { cur = t; continue; }
            if (pcy + sh > ptop && pcy < pbottom) text(gr, cur, pcx, pcy, color, true);
            pcy += sh + 1;
            cur = w;
        }
        if (cur.length() > 0) { if (pcy + sh > ptop && pcy < pbottom) text(gr, cur, pcx, pcy, color, true); pcy += sh + 1; }
        pcy += after;
     } finally { rally3d.core.Trace.out(447, $t); } }

    private void pbtn(Graphics gr, String s, int id, int arg, boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(448); try {
        if (pcy + bh() > ptop && pcy < pbottom) button(gr, pcx, pcy, pcw, bh(), s, id, arg, on);
        pcy += bh() + 2;
     } finally { rally3d.core.Trace.out(448, $t); } }

    private void pbtn2(Graphics gr, String a, int ida, boolean ona, String b, int idb, boolean onb) { /*T*/ final long $t = rally3d.core.Trace.in(449); try {
        int w = (pcw - 2) / 2;
        if (pcy + bh() > ptop && pcy < pbottom) {
            button(gr, pcx, pcy, w, bh(), a, ida, 0, ona);
            button(gr, pcx + w + 2, pcy, w, bh(), b, idb, 0, onb);
        }
        pcy += bh() + 2;
     } finally { rally3d.core.Trace.out(449, $t); } }

    private void panelPath(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(450); try {
        label(gr, rally3d.core.Lang.t("GROW THE ROAD"));
        pbtn(gr, extendTool ? rally3d.core.Lang.t("TAP GROUND: ON") : rally3d.core.Lang.t("TAP GROUND: OFF"), A_EXT, 0, extendTool);
        label(gr, rally3d.core.Lang.t("ADD A BEND AFTER THE SELECTION"));
        pbtn(gr, rally3d.core.Lang.t(PIECE_NAMES[0]), A_PIECE, 0, false);
        for (int k = 1; k + 1 < PIECES.length; k += 2) pbtn2(gr, rally3d.core.Lang.t(PIECE_NAMES[k]), A_PIECE + k, false, rally3d.core.Lang.t(PIECE_NAMES[k + 1]), A_PIECE + k + 1, false);
        label(gr, rally3d.core.Lang.t("SELECTION"));
        pbtn2(gr, rally3d.core.Lang.t("TURN L"), A_TURNL, false, rally3d.core.Lang.t("TURN R"), A_TURNR, false);
        pbtn(gr, rally3d.core.Lang.t("MAKE STRAIGHT"), A_STRAIGHT, 0, false);
        pbtn2(gr, rally3d.core.Lang.t("DELETE"), A_DELETE, false, rally3d.core.Lang.t("DEL AFTER"), A_TRIM, false);
        label(gr, rally3d.core.Lang.t("CLIPBOARD"));
        pbtn2(gr, rally3d.core.Lang.t("COPY"), A_COPY, false, rally3d.core.Lang.t("CUT"), A_CUT, false);
        pbtn2(gr, rally3d.core.Lang.t("PASTE"), A_PASTE, clip != null, rally3d.core.Lang.t("DUP"), A_DUP, false);
        pbtn(gr, rally3d.core.Lang.t("SELECT ALL"), A_ALL, 0, false);
        double km = md.n * 32 * 0.77 / 1000.0;
        note(gr, md.n + rally3d.core.Lang.t(" segments, about ") + (Math.round(km * 10) / 10.0) + rally3d.core.Lang.t(" km. Tap the road or drag the strip below to select."), DIM);
     } finally { rally3d.core.Trace.out(450, $t); } }

    private void panelHeight(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(451); try {
        label(gr, rally3d.core.Lang.t("SELECTION HEIGHT"));
        pbtn2(gr, rally3d.core.Lang.t("RAISE"), A_H, false, rally3d.core.Lang.t("LOWER"), A_H + 1, false);
        pbtn2(gr, rally3d.core.Lang.t("HILL"), A_H + 2, false, rally3d.core.Lang.t("VALLEY"), A_H + 3, false);
        pbtn2(gr, rally3d.core.Lang.t("FLATTEN"), A_H + 4, false, rally3d.core.Lang.t("SMOOTH"), A_H + 5, false);
        pbtn(gr, rally3d.core.Lang.t("WHOLE ROAD FLAT"), A_H + 6, 0, false);
        pbtn(gr, rally3d.core.Lang.t("FIT VIEW"), A_H + 7, 0, false);
        note(gr, rally3d.core.Lang.t("Select a part on the strip, then use the buttons (RAISE / LOWER = 10 units) - or drag the yellow squares on the chart. Red = too steep for a car."), DIM);
     } finally { rally3d.core.Trace.out(451, $t); } }

    private void panelLand(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(452); try {
        label(gr, rally3d.core.Lang.t("ROADSIDE (SELECTION)"));
        for (int k = 0; k < 5; k++) {
            boolean on = true;
            for (int i = selA; i <= selB && i < md.n; i++) if (md.tgt[i] != MapData.TERRAIN[k]) { on = false; break; }
            if (pcy + bh() > ptop && pcy < pbottom) {
                fill(gr, pcx, pcy, 6, bh(), TERRAIN_COLORS[k]);
                button(gr, pcx + 8, pcy, pcw - 8, bh(), rally3d.core.Lang.t(MapData.TERRAIN_NAMES[k]), A_TERR + k, 0, on);
            }
            pcy += bh() + 2;
        }
        label(gr, rally3d.core.Lang.t("CENTRE LINE"));
        pbtn2(gr, rally3d.core.Lang.t("ON"), A_CENTER + 1, md.cm[Math.min(selA, md.n - 1)] != 0, rally3d.core.Lang.t("OFF"), A_CENTER, md.cm[Math.min(selA, md.n - 1)] == 0);
        note(gr, rally3d.core.Lang.t("Changing the roadside blends over 2-5 segments. Colours in the strip show the terrain."), DIM);
     } finally { rally3d.core.Trace.out(452, $t); } }

    private void panelProps(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(453); try {
        label(gr, rally3d.core.Lang.t("SIDE"));
        int w = (pcw - 4) / 3;
        if (pcy + bh() > ptop && pcy < pbottom) {
            String[] sn = {rally3d.core.Lang.t("LEFT"), rally3d.core.Lang.t("RIGHT"), rally3d.core.Lang.t("BOTH")};
            for (int s = 0; s < 3; s++) button(gr, pcx + s * (w + 2), pcy, w, bh(), sn[s], A_SIDE + s, 0, propSide == s);
        }
        pcy += bh() + 2;
        label(gr, rally3d.core.Lang.t("SPRITE"));
        int cell = (pcw - 2) / 3;
        if (thumbTheme != md.theme) { java.util.Arrays.fill(thumbCache, null); java.util.Arrays.fill(thumbMeta, null); thumbTheme = md.theme; }
        for (int i = 0; i < SPRITE_TYPES.length; i += 3) {
            if (pcy + cell > ptop && pcy < pbottom) {
                for (int j = 0; j < 3 && i + j < SPRITE_TYPES.length; j++) {
                    int t = SPRITE_TYPES[i + j], cx = pcx + j * (cell + 1);
                    boolean on = propSprite == t;
                    fill(gr, cx, pcy, cell, cell, on ? BTN_ON : BTN);
                    frame(gr, cx, pcy, cell, cell, on ? WHITE : 0xFF5A7088);
                    drawThumb(gr, i + j, t, cx, pcy, cell);
                    addHit(cx, pcy, cell, cell, A_SPR + i + j, 0);
                }
            }
            pcy += cell + 1;
        }
        pcy += 2;
        label(gr, rally3d.core.Lang.t("LANE (0 = ROAD EDGE)"));
        int lw = (pcw - 6) / 4;
        if (pcy + bh() > ptop && pcy < pbottom) for (int l = 0; l < 4; l++) button(gr, pcx + l * (lw + 2), pcy, lw, bh(), String.valueOf(l), A_LANE + l, 0, propLane == l);
        pcy += bh() + 2;
        label(gr, rally3d.core.Lang.t("EVERY N SEGMENTS"));
        int ew = (pcw - 8) / 5;
        if (pcy + bh() > ptop && pcy < pbottom) for (int e = 0; e < EVERY.length; e++) button(gr, pcx + e * (ew + 2), pcy, ew, bh(), String.valueOf(EVERY[e]), A_EVERY + e, 0, propEvery == EVERY[e]);
        pcy += bh() + 4;
        pbtn(gr, rally3d.core.Lang.t("PLACE ON SELECTION"), A_APPLY, 0, true);
        pbtn(gr, rally3d.core.Lang.t("CLEAR SELECTION"), A_CLEARP, 0, false);
        label(gr, rally3d.core.Lang.t("CUSTOM SPRITES"));
        int pw2 = (pcw - 2) / 2;
        for (int s = 0; s < 4; s += 2) {
            if (pcy + bh() > ptop && pcy < pbottom) {
                button(gr, pcx, pcy, pw2, bh(), (md.spritePx[s] == null ? rally3d.core.Lang.t("NEW ") : rally3d.core.Lang.t("EDIT ")) + (s + 1), A_PAINTSPR + s, 0, false);
                button(gr, pcx + pw2 + 2, pcy, pw2, bh(), (md.spritePx[s + 1] == null ? rally3d.core.Lang.t("NEW ") : rally3d.core.Lang.t("EDIT ")) + (s + 2), A_PAINTSPR + s + 1, 0, false);
            }
            pcy += bh() + 2;
        }
        note(gr, rally3d.core.Lang.t("Pick a sprite, then PLACE it on the selected segments. Gate = finish arch, cone = checkpoint post."), DIM);
     } finally { rally3d.core.Trace.out(453, $t); } }

    private void drawThumb(Graphics gr, int idx, int type, int cx, int cy, int cell) { /*T*/ final long $t = rally3d.core.Trace.in(454); try {
        int box = cell - 4;
        if (type == -2) { textC(gr, rally3d.core.Lang.t("NONE"), cx + cell / 2, cy + (cell - sh) / 2, WHITE, true); return; }
        if (type == -1) { textC(gr, rally3d.core.Lang.t("RAND"), cx + cell / 2, cy + (cell - sh) / 2, WHITE, true); return; }
        int[] pix = thumbCache[idx];
        int[] meta = thumbMeta[idx];
        if (pix == null || meta == null || meta[0] != box) {
            int[] wh = new int[2];
            int[] px;
            if (type >= 7) {
                px = md.spritePx[type - 7];
                wh[0] = md.spriteW[type - 7]; wh[1] = md.spriteH[type - 7];
            } else px = stockSprite(type, md.theme, wh);
            if (px == null) {
                if (type >= 7) { textC(gr, rally3d.core.Lang.t("NEW"), cx + cell / 2, cy + cell / 2 - sh, YEL, true); textC(gr, String.valueOf(type - 6), cx + cell / 2, cy + cell / 2 + 1, DIM, true); }
                else textC(gr, "?", cx + cell / 2, cy + (cell - sh) / 2, DIM, true);
                return;
            }
            double sc = Math.min(box / (double) wh[0], box / (double) wh[1]);
            int tw = Math.max(1, (int) Math.round(wh[0] * sc)), th = Math.max(1, (int) Math.round(wh[1] * sc));
            pix = PngCodec.scale(px, wh[0], wh[1], tw, th);
            meta = new int[]{box, tw, th};
            thumbCache[idx] = pix; thumbMeta[idx] = meta;
        }
        int tw = meta[1], th = meta[2];
        gr.drawRGB(pix, 0, tw, cx + (cell - tw) / 2, cy + (cell - th) / 2, tw, th, true);
     } finally { rally3d.core.Trace.out(454, $t); } }

    private void panelMarks(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(455); try {
        label(gr, rally3d.core.Lang.t("MARKERS (TAP = GO)"));
        String[] names = {rally3d.core.Lang.t("START"), rally3d.core.Lang.t("CHECK 1"), rally3d.core.Lang.t("CHECK 2"), rally3d.core.Lang.t("CHECK 3"), rally3d.core.Lang.t("FINISH")};
        for (int k = 0; k < 5; k++) {
            int seg = k == 0 ? MapData.START_SEG : md.effCp(k - 1);
            if (pcy + bh() > ptop && pcy < pbottom) {
                int bw = k == 0 ? pcw : pcw - 34;
                button(gr, pcx, pcy, bw, bh(), names[k] + "  " + seg, A_CPGO + k, 0, false);
                if (k > 0) button(gr, pcx + bw + 2, pcy, 32, bh(), rally3d.core.Lang.t("SET"), A_CP + k - 1, 0, true);
            }
            pcy += bh() + 2;
        }
        pbtn(gr, rally3d.core.Lang.t("SPREAD EVENLY"), A_CPAUTO, 0, false);
        String bad = md.validate();
        note(gr, rally3d.core.Lang.t("SET puts the marker on the selected segment. The car starts at segment ") + MapData.START_SEG + ".", DIM);
        note(gr, bad == null ? rally3d.core.Lang.t("Map is ready to play.") : bad, bad == null ? GREEN : RED);
     } finally { rally3d.core.Trace.out(455, $t); } }

    private void panelWorld(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(456); try {
        label(gr, rally3d.core.Lang.t("THEME"));
        pbtn2(gr, rally3d.core.Lang.t("DESERT"), A_THEME, md.theme == 0, rally3d.core.Lang.t("WINTER"), A_THEME + 1, md.theme == 1);
        pbtn2(gr, rally3d.core.Lang.t("SUMMER"), A_THEME + 2, md.theme == 2, rally3d.core.Lang.t("NIGHT"), A_THEME + 3, md.theme == 3);
        label(gr, rally3d.core.Lang.t("BACKGROUND"));
        pbtn2(gr, rally3d.core.Lang.t("DESERT"), A_BG, md.bgPng == null && md.bgFrom == 0, rally3d.core.Lang.t("WINTER"), A_BG + 1, md.bgPng == null && md.bgFrom == 1);
        pbtn2(gr, rally3d.core.Lang.t("SUMMER"), A_BG + 2, md.bgPng == null && md.bgFrom == 2, rally3d.core.Lang.t("NIGHT"), A_BG + 3, md.bgPng == null && md.bgFrom == 3);
        pbtn2(gr, rally3d.core.Lang.t("THEME'S"), A_BG + 4, md.bgPng == null && md.bgFrom < 0, rally3d.core.Lang.t("PAINT..."), A_BG + 5, md.bgPng != null);
        label(gr, rally3d.core.Lang.t("ROAD SURFACE"));
        pbtn2(gr, rally3d.core.Lang.t("DESERT"), A_ROAD, md.roadPng == null && md.roadFrom == 0, rally3d.core.Lang.t("WINTER"), A_ROAD + 1, md.roadPng == null && md.roadFrom == 1);
        pbtn2(gr, rally3d.core.Lang.t("SUMMER"), A_ROAD + 2, md.roadPng == null && md.roadFrom == 2, rally3d.core.Lang.t("NIGHT"), A_ROAD + 3, md.roadPng == null && md.roadFrom == 3);
        pbtn2(gr, rally3d.core.Lang.t("THEME'S"), A_ROAD + 4, md.roadPng == null && md.roadFrom < 0, rally3d.core.Lang.t("PAINT..."), A_ROAD + 5, md.roadPng != null);
        label(gr, rally3d.core.Lang.t("COLOURS"));
        String[] cn = {rally3d.core.Lang.t("SKY"), rally3d.core.Lang.t("GROUND"), rally3d.core.Lang.t("HUD")};
        int[] cv = {md.skyColor(), md.groundColor(), md.hudColor()};
        for (int k = 0; k < 3; k++) {
            if (pcy + bh() > ptop && pcy < pbottom) {
                fill(gr, pcx, pcy, 16, bh(), 0xFF000000 | cv[k]);
                frame(gr, pcx, pcy, 16, bh(), WHITE);
                button(gr, pcx + 18, pcy, pcw - 18, bh(), cn[k], A_COLOR + k, 0, md.customColors);
            }
            pcy += bh() + 2;
        }
        pbtn(gr, rally3d.core.Lang.t("THEME COLOURS"), A_COLAUTO, 0, !md.customColors);
        label(gr, rally3d.core.Lang.t("MAP INFO"));
        pbtn(gr, rally3d.core.Lang.t("NAME: ") + md.name, A_INFO, 0, false);
        pbtn(gr, rally3d.core.Lang.t("AUTHOR: ") + (md.author.length() == 0 ? "-" : md.author), A_INFO + 1, 0, false);
        pbtn(gr, rally3d.core.Lang.t("TEXT: ") + (md.desc.length() == 0 ? "-" : md.desc), A_INFO + 2, 0, false);
     } finally { rally3d.core.Trace.out(456, $t); } }

    // ------------------------------------------------------------------ overlays

    private int ovX, ovY, ovW, ovH;

    private void drawOverlays(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(457); try {
        if (dlgTitle != null) {
            int w = Math.min(W - 16, 380);
            ArrayList<String> rows = new ArrayList<String>();
            for (String l : dlgLines) { wrapInto(l, w - 20, rows); }
            int lineH = sh + 2, textH = rows.size() * lineH + (dlgLines.length - 1) * 3;
            int maxText = Math.max(lineH, H - 8 - sh - 14 - bh() - 14);
            boolean scrolls = textH > maxText;
            int viewH = scrolls ? maxText : textH;
            int h = sh + 14 + viewH + bh() + 14;
            int x = (W - w) / 2, y = (H - h) / 2;
            fill(gr, 0, 0, W, H, 0xA0000000);
            box(gr, x, y, w, h);
            textC(gr, dlgTitle, W / 2, y + 5, YEL, true);
            int top = y + sh + 12;
            dlgMax = Math.max(0, textH - viewH);
            dlgScroll = Math.max(0, Math.min(dlgMax, dlgScroll));
            gr.setClip(x + 2, top, w - 4, viewH);
            int yy = top - dlgScroll;
            for (int li = 0; li < dlgLines.length; li++) {
                ArrayList<String> part = new ArrayList<String>();
                wrapInto(dlgLines[li], w - 20, part);
                for (String r : part) { if (yy + lineH > top && yy < top + viewH) text(gr, r, x + 8, yy, WHITE, true); yy += lineH; }
                yy += 3;
            }
            gr.setClip(0, 0, W, H);
            if (scrolls) {   // scroll bar + hint
                int bh2 = Math.max(10, viewH * viewH / textH), by = top + (viewH - bh2) * dlgScroll / Math.max(1, dlgMax);
                fill(gr, x + w - 5, by, 3, bh2, 0xB0FFFFAA);
            }
            int n = dlgBtns.length, bw = (w - 12 - (n - 1) * 4) / n;
            for (int i = 0; i < n; i++) button(gr, x + 6 + i * (bw + 4), y + h - bh() - 6, bw, bh(), dlgBtns[i], A_DLG + i, 0, i == 0);
            ovX = x; ovY = y; ovW = w; ovH = h;
        } else if (listItems != null) {
            int w = Math.min(W - 16, 280), rh = bh() + 2;
            int h = Math.min(H - 20, listItems.length * rh + sh + 14);
            int x = (W - w) / 2, y = (H - h) / 2;
            fill(gr, 0, 0, W, H, 0xA0000000);
            box(gr, x, y, w, h);
            textC(gr, listTitle, W / 2, y + 4, YEL, true);
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
                int total = listItems.length * rh, bh2 = Math.max(10, avail * avail / total), by = top + (avail - bh2) * listScroll / Math.max(1, total - avail);
                fill(gr, x + w - 5, by, 3, bh2, 0xB0FFFFAA);
            }
            ovX = x; ovY = y; ovW = w; ovH = h;
        } else if (palette != null) {
            int cell = 24, cols = 8, w = cols * (cell + 2) + 10, rows = (palette.length + cols - 1) / cols, h = rows * (cell + 2) + sh + 16;
            int x = (W - w) / 2, y = (H - h) / 2;
            fill(gr, 0, 0, W, H, 0xA0000000);
            box(gr, x, y, w, h);
            textC(gr, palKind == 0 ? rally3d.core.Lang.t("SKY COLOUR") : palKind == 1 ? rally3d.core.Lang.t("GROUND COLOUR") : rally3d.core.Lang.t("HUD COLOUR"), W / 2, y + 4, YEL, true);
            for (int i = 0; i < palette.length; i++) {
                int cx = x + 6 + (i % cols) * (cell + 2), cy = y + sh + 10 + (i / cols) * (cell + 2);
                fill(gr, cx, cy, cell, cell, palette[i]);
                frame(gr, cx, cy, cell, cell, WHITE);
                btns.add(new int[]{cx, cy, cell, cell, A_PAL + i, 0});
            }
            ovX = x; ovY = y; ovW = w; ovH = h;
        }
     } finally { rally3d.core.Trace.out(457, $t); } }

    void wrapInto(String s, int maxW, List<String> out) { /*T*/ final long $t = rally3d.core.Trace.in(458); try {
        String cur = "";
        for (String w : s.split(" ")) {
            String t = cur.length() == 0 ? w : cur + " " + w;
            if (textW(t, true) <= maxW) { cur = t; continue; }
            if (cur.length() > 0) out.add(cur);
            cur = w;
        }
        if (cur.length() > 0) out.add(cur);
     } finally { rally3d.core.Trace.out(458, $t); } }
}
