package com.nokia.mid.appl.rally3d;

import java.io.File;
import java.util.ArrayList;
import javax.microedition.lcdui.Graphics;

/**
 * Pixel painter used by the map editor for custom sprites (transparent, 4 slots), the sky background (128x128) and the road
 * texture (256x32). Pencil / eraser / flood fill / eyedropper, brush size, mirror, undo, templates and stock sprites as a start.
 * Mouse, touch (one finger paints, two fingers move + pinch zoom) and Ctrl+Z / Ctrl+Y.
 */
final class Painter {
    static final int K_SPRITE = 1, K_BG = 2, K_ROAD = 3;
    static final int P_UNDO = 1, P_REDO = 2, P_CANCEL = 3, P_DONE = 4, P_TOOL = 10, P_BRUSH = 20, P_MIRROR = 30, P_CLEAR = 31, P_TEMPL = 32, P_STOCK = 33, P_ZIN = 34, P_ZOUT = 35,
            P_FLIPH = 36, P_FLIPV = 37, P_ROLL = 38, P_SHAPEFILL = 39, P_HEX = 40, P_IMPORT = 41, P_BGTPL = 42, P_ROADTPL = 43, P_SEAM = 44, P_COL = 100;
    static final String[] TEMPLATES = {"TREE", "PINE", "BUSH", "ROCK", "POLE + SIGN", "HOUSE"};
    static final String[] STOCK = {"SPRITE 1 (THEME)", "SPRITE 2 (THEME)", "SPRITE 3 (THEME)", "FINISH GATE", "CHECKPOINT POST", "SPECTATOR"};
    private static final int[] STOCK_TYPES = {0, 1, 2, 3, 5, 6};
    private static final String[] TOOL_NAMES = {"PENCIL", "ERASER", "FILL", "PICK", "LINE", "RECT", "OVAL"};
    private static final int T_LINE = 4, T_RECT = 5, T_OVAL = 6;

    private final MapEditor ed;
    final int kind, slot, w, h;
    final int[] px;
    private final boolean alpha;
    private final String title;
    private int tool, brush = 1, color = 0xFFE02020;
    private boolean mirror, painting, panning, dirtyView = true, shapeFill;
    private int[] shapeBase;
    private int shapeX, shapeY;
    private int panelScroll, panelContent;
    private final int[] sliderDrag = {-1, -1, -1};
    private final boolean[] pnl = new boolean[3];
    private final int[][] sl = new int[3][];
    private File[] importFiles;
    private int zoom, panX, panY, lastX = -1, lastY = -1, strokeSlot = -1, viewZoom = -1;
    private final ArrayList<int[]> undo = new ArrayList<int[]>(), redo = new ArrayList<int[]>();
    private int[] view;
    private int vw2, vh2;
    // pinch
    private final int[] fx = new int[3], fy = new int[3];
    private final boolean[] fdown = new boolean[3];
    private double pinch;
    private final int[] pal = {
        0xFF000000, 0xFF404040, 0xFF808080, 0xFFC0C0C0, 0xFFFFFFFF, 0xFF7A4A28, 0xFFB07840, 0xFFE8C898,
        0xFF800000, 0xFFE02020, 0xFFFF8020, 0xFFFFE030, 0xFF206020, 0xFF30B030, 0xFF90E050, 0xFF106060,
        0xFF2050C0, 0xFF50A0FF, 0xFFA0D8FF, 0xFF402080, 0xFF9050E0, 0xFFE070C0, 0xFF603030, 0xFFFFC8C8};

    Painter(MapEditor ed, int kind, int slot, int[] px, int w, int h, boolean alpha, String title) {
        this.ed = ed; this.kind = kind; this.slot = slot; this.px = px; this.w = w; this.h = h; this.alpha = alpha; this.title = title;
        int aw = Math.max(40, ed.W0() - ed.PW), ah = Math.max(40, ed.H0() - ed.TB);
        zoom = Math.max(1, Math.min(12, Math.min(aw / w, ah / h)));
    }

    // ------------------------------------------------------------------ painting primitives

    private void snapshot() {
        undo.add(px.clone());
        if (undo.size() > 25) undo.remove(0);
        redo.clear();
    }

    private void doUndo() { /*T*/ final long $t = rally3d.core.Trace.in(469); try { if (undo.isEmpty()) return; redo.add(px.clone()); System.arraycopy(undo.remove(undo.size() - 1), 0, px, 0, px.length); dirtyView = true;  } finally { rally3d.core.Trace.out(469, $t); } }
    private void doRedo() { /*T*/ final long $t = rally3d.core.Trace.in(470); try { if (redo.isEmpty()) return; undo.add(px.clone()); System.arraycopy(redo.remove(redo.size() - 1), 0, px, 0, px.length); dirtyView = true;  } finally { rally3d.core.Trace.out(470, $t); } }

    private void put(int x, int y, int c) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = alpha ? c : (c | 0xFF000000);
    }

    private void stamp(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(471); try {
        int c = tool == 1 ? 0 : color;
        int r0 = -(brush - 1) / 2, r1 = brush / 2;
        for (int dy = r0; dy <= r1; dy++) for (int dx = r0; dx <= r1; dx++) {
            put(x + dx, y + dy, c);
            if (mirror) put(w - 1 - (x + dx), y + dy, c);
        }
        dirtyView = true;
     } finally { rally3d.core.Trace.out(471, $t); } }

    private void line(int x0, int y0, int x1, int y1) { /*T*/ final long $t = rally3d.core.Trace.in(472); try {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        for (int guard = 0; guard < 4000; guard++) {
            stamp(x0, y0);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
     } finally { rally3d.core.Trace.out(472, $t); } }

    private void flood(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(473); try {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int target = px[y * w + x], c = alpha ? color : (color | 0xFF000000);
        if (target == c) return;
        int[] stack = new int[w * h + 8];
        int sp = 0;
        stack[sp++] = y * w + x;
        while (sp > 0) {
            int p = stack[--sp];
            if (px[p] != target) continue;
            px[p] = c;
            int cx = p % w, cy = p / w;
            if (cx > 0 && sp < stack.length - 4) stack[sp++] = p - 1;
            if (cx < w - 1 && sp < stack.length - 4) stack[sp++] = p + 1;
            if (cy > 0 && sp < stack.length - 4) stack[sp++] = p - w;
            if (cy < h - 1 && sp < stack.length - 4) stack[sp++] = p + w;
        }
        dirtyView = true;
     } finally { rally3d.core.Trace.out(473, $t); } }

    // ------------------------------------------------------------------ layout / coordinates

    private int areaW() { return ed.W - ed.PW; }
    private int ox() { return (areaW() - w * zoom) / 2 + panX; }
    private int oy() { return ed.TB + (ed.H - ed.TB - h * zoom) / 2 + panY; }
    private int pixX(int x) { return (int) Math.floor((x - ox()) / (double) zoom); }
    private int pixY(int y) { return (int) Math.floor((y - oy()) / (double) zoom); }

    // ------------------------------------------------------------------ input (called by MapEditor)

    void down(int s, int x, int y, int button, int pointers) { /*T*/ final long $t = rally3d.core.Trace.in(474); try {
        fx[s] = x; fy[s] = y;
        sliderDrag[s] = -1; pnl[s] = false;
        if (y < ed.TB) return;
        if (x >= areaW()) {   // right panel: colour sliders, or drag = scroll the panel
            for (int i = 0; i < 3; i++) { int[] r = sl[i]; if (r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) { sliderDrag[s] = i; setChannel(i, x); return; } }
            pnl[s] = true;
            return;
        }
        fdown[s] = true;
        pointers = canvasPointers();
        if (pointers >= 2 || button != 1) { panning = true; painting = false; shapeBase = null; pinch = pinchNow(); return; }
        if (tool == 3) { pickAt(x, y); return; }
        int ix = pixX(x), iy = pixY(y);
        if (tool == 2) { snapshot(); flood(ix, iy); return; }
        snapshot();
        if (tool >= T_LINE) { shapeBase = px.clone(); shapeX = ix; shapeY = iy; painting = true; strokeSlot = s; drawShape(ix, iy, ix, iy); return; }
        painting = true; strokeSlot = s;
        lastX = ix; lastY = iy;
        stamp(ix, iy);
     } finally { rally3d.core.Trace.out(474, $t); } }

    void move(int s, int x, int y, int pointers) { /*T*/ final long $t = rally3d.core.Trace.in(475); try {
        int ox0 = fx[s], oy0 = fy[s];
        fx[s] = x; fy[s] = y;
        if (sliderDrag[s] >= 0) { setChannel(sliderDrag[s], x); return; }
        if (pnl[s]) { panelScroll = Math.max(0, Math.min(Math.max(0, panelContent - (ed.H - ed.TB)), panelScroll - (y - oy0))); return; }
        pointers = canvasPointers();
        if (pointers >= 2 || panning) {
            if (pointers >= 2) {
                double d = pinchNow();
                if (pinch > 8 && d > 8) { int nz = (int) Math.round(zoom * d / pinch); if (nz != zoom) setZoom(nz); }
                pinch = d;
            }
            panX += (x - ox0) / (pointers >= 2 ? 2 : 1); panY += (y - oy0) / (pointers >= 2 ? 2 : 1);
            return;
        }
        if (painting && s == strokeSlot) {
            int ix = pixX(x), iy = pixY(y);
            if (shapeBase != null) { System.arraycopy(shapeBase, 0, px, 0, px.length); drawShape(shapeX, shapeY, ix, iy); dirtyView = true; }
            else { line(lastX, lastY, ix, iy); lastX = ix; lastY = iy; }
        } else if (tool == 3 && x < areaW() && y >= ed.TB) pickAt(x, y);
     } finally { rally3d.core.Trace.out(475, $t); } }

    void up(int s, int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(476); try {
        fdown[s] = false; sliderDrag[s] = -1; pnl[s] = false;
        if (s == strokeSlot) { painting = false; strokeSlot = -1; shapeBase = null; }
        int still = 0;
        for (int i = 0; i < 3; i++) if (fdown[i]) still++;
        if (still == 0) { panning = false; pinch = 0; }
        else if (still < 2) pinch = 0;
     } finally { rally3d.core.Trace.out(476, $t); } }

    private double pinchNow() { /*T*/ final long $t = rally3d.core.Trace.in(477); try {
        int a = -1, b = -1;
        for (int i = 0; i < 3; i++) if (fdown[i]) { if (a < 0) a = i; else b = i; }
        return b < 0 ? 0 : Math.hypot(fx[a] - fx[b], fy[a] - fy[b]);
     } finally { rally3d.core.Trace.out(477, $t); } }

    private void pickAt(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(478); try {
        int ix = pixX(x), iy = pixY(y);
        if (ix < 0 || iy < 0 || ix >= w || iy >= h) return;
        int c = px[iy * w + ix];
        if ((c >>> 24) != 0) { color = c | 0xFF000000; if (tool == 3) tool = 0; }
     } finally { rally3d.core.Trace.out(478, $t); } }

    private void setZoom(int nz) { zoom = Math.max(1, Math.min(16, nz)); dirtyView = true; }

    void wheel(int rot, int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(538); try {
        if (x >= areaW()) { panelScroll = Math.max(0, Math.min(Math.max(0, panelContent - (ed.H - ed.TB)), panelScroll + rot * 14)); return; }
        setZoom(zoom - rot);
     } finally { rally3d.core.Trace.out(538, $t); } }

    private int canvasPointers() { int c = 0; for (int i = 0; i < 3; i++) if (fdown[i]) c++; return c; }

    void cancel() { ed.painterClosed(); }

    void rawKey(int code, boolean ctrl, boolean shift) { /*T*/ final long $t = rally3d.core.Trace.in(479); try {
        if (ctrl) {
            if (code == 'Z') { if (shift) doRedo(); else doUndo(); }
            else if (code == 'Y') doRedo();
            return;
        }
        switch (code) {
            case 27: cancel(); break;
            case 'P': tool = 0; break;
            case 'E': if (alpha) tool = 1; break;
            case 'F': tool = 2; break;
            case 'I': tool = 3; break;
            case 'L': tool = T_LINE; break;
            case 'R': tool = T_RECT; break;
            case 'O': tool = T_OVAL; break;
            case 'M': mirror = !mirror; break;
            case 'X': shapeFill = !shapeFill; break;
            case 61: case 107: case 521: setZoom(zoom + 1); break;
            case 45: case 109: setZoom(zoom - 1); break;
            default: break;
        }
     } finally { rally3d.core.Trace.out(479, $t); } }

    void action(int id, int arg) { /*T*/ final long $t = rally3d.core.Trace.in(480); try {
        if (id >= P_COL && id < P_COL + pal.length) { color = pal[id - P_COL]; if (tool == 1 || tool == 3) tool = 0; return; }
        if (id >= P_TOOL && id < P_TOOL + TOOL_NAMES.length) { tool = id - P_TOOL; return; }
        if (id >= P_BRUSH && id < P_BRUSH + 3) { brush = id - P_BRUSH + 1; return; }
        switch (id) {
            case P_UNDO: doUndo(); break;
            case P_REDO: doRedo(); break;
            case P_CANCEL: cancel(); break;
            case P_DONE: ed.painterDone(this); break;
            case P_MIRROR: mirror = !mirror; break;
            case P_CLEAR: snapshot(); java.util.Arrays.fill(px, alpha ? 0 : 0xFF000000 | ed.md.skyColor()); dirtyView = true; break;
            case P_TEMPL: ed.showList(rally3d.core.Lang.t("TEMPLATE"), rally3d.core.Lang.tl(TEMPLATES), MapEditor.LK_TEMPLATE); break;
            case P_STOCK: ed.showList(rally3d.core.Lang.t("COPY A STOCK SPRITE"), rally3d.core.Lang.tl(STOCK), MapEditor.LK_STOCKSPR); break;
            case P_ZIN: setZoom(zoom + 1); break;
            case P_ZOUT: setZoom(zoom - 1); break;
            case P_FLIPH: flip(true); break;
            case P_FLIPV: flip(false); break;
            case P_ROLL: roll(); break;
            case P_SHAPEFILL: shapeFill = !shapeFill; break;
            case P_HEX: hexColor(); break;
            case P_IMPORT: openImport(); break;
            case P_BGTPL: ed.showList(rally3d.core.Lang.t("BACKGROUND TEMPLATE"), rally3d.core.Lang.tl(MapTemplates.BG_NAMES), MapEditor.LK_BGTPL); break;
            case P_ROADTPL: ed.showList(rally3d.core.Lang.t("ROAD SURFACE (TILES 0 + 5)"), rally3d.core.Lang.tl(MapTemplates.ROAD_NAMES), MapEditor.LK_ROADTPL); break;
            case P_SEAM: seamless(); break;
            default: break;
        }
     } finally { rally3d.core.Trace.out(480, $t); } }

    // ------------------------------------------------------------------ templates / stock

    void template(int t) { /*T*/ final long $t = rally3d.core.Trace.in(481); try {
        snapshot();
        java.util.Arrays.fill(px, 0);
        int cx = w / 2;
        switch (t) {
            case 0: {   // tree
                rect(cx - Math.max(1, w / 12), h * 55 / 100, cx + Math.max(1, w / 12), h - 1, 0xFF6A4020);
                rect(cx - Math.max(1, w / 12), h * 55 / 100, cx - Math.max(1, w / 12) + 1, h - 1, 0xFF8A5A30);
                ellipse(cx, h * 32 / 100, w * 46 / 100, h * 30 / 100, 0xFF1F7A2A);
                ellipse(cx - w / 12, h * 28 / 100, w * 30 / 100, h * 20 / 100, 0xFF35A83C);
                ellipse(cx - w / 6, h * 22 / 100, w * 12 / 100, h * 8 / 100, 0xFF70D060);
                break;
            }
            case 1: {   // pine
                rect(cx - Math.max(1, w / 14), h * 80 / 100, cx + Math.max(1, w / 14), h - 1, 0xFF5A3A1E);
                for (int i = 0; i < 3; i++) tri(cx, h * (6 + i * 22) / 100, w * (22 + i * 12) / 100, h * (36 + i * 22) / 100, i == 1 ? 0xFF1E6A32 : 0xFF175A28, 0xFF2E8A44);
                break;
            }
            case 2: {   // bush
                ellipse(cx, h * 62 / 100, w * 46 / 100, h * 34 / 100, 0xFF1F7A2A);
                ellipse(cx - w / 5, h * 66 / 100, w * 26 / 100, h * 26 / 100, 0xFF2E9A38);
                ellipse(cx + w / 5, h * 64 / 100, w * 26 / 100, h * 28 / 100, 0xFF2E9A38);
                ellipse(cx - w / 8, h * 52 / 100, w * 14 / 100, h * 10 / 100, 0xFF70D060);
                break;
            }
            case 3: {   // rock
                ellipse(cx, h * 66 / 100, w * 44 / 100, h * 30 / 100, 0xFF6E6E74);
                ellipse(cx - w / 10, h * 58 / 100, w * 30 / 100, h * 20 / 100, 0xFF9A9AA2);
                ellipse(cx + w / 3, h * 80 / 100, w * 16 / 100, h * 14 / 100, 0xFF5A5A60);
                ellipse(cx - w / 7, h * 52 / 100, w * 10 / 100, h * 7 / 100, 0xFFCACAD2);
                break;
            }
            case 4: {   // pole + sign
                rect(cx - 1, h * 20 / 100, cx, h - 1, 0xFF808080);
                rect(cx - w * 30 / 100, h * 4 / 100, cx + w * 30 / 100, h * 26 / 100, 0xFFE02020);
                rect(cx - w * 26 / 100, h * 8 / 100, cx + w * 26 / 100, h * 22 / 100, 0xFFFFFFFF);
                rect(cx - w * 18 / 100, h * 12 / 100, cx + w * 18 / 100, h * 18 / 100, 0xFFE02020);
                break;
            }
            default: {   // house
                rect(w * 12 / 100, h * 45 / 100, w * 88 / 100, h - 1, 0xFFD8C8A0);
                tri(cx, h * 8 / 100, w * 52 / 100, h * 40 / 100, 0xFF9A3A2A, 0xFFC05A3A);
                rect(w * 40 / 100, h * 66 / 100, w * 60 / 100, h - 1, 0xFF6A4020);
                rect(w * 18 / 100, h * 54 / 100, w * 32 / 100, h * 66 / 100, 0xFF80C0E0);
                rect(w * 68 / 100, h * 54 / 100, w * 82 / 100, h * 66 / 100, 0xFF80C0E0);
                break;
            }
        }
        dirtyView = true;
     } finally { rally3d.core.Trace.out(481, $t); } }

    void copyStock(int i) { /*T*/ final long $t = rally3d.core.Trace.in(482); try {
        int[] wh = new int[2];
        int[] src = ed.stockSprite(STOCK_TYPES[i], ed.md.theme, wh);
        if (src == null) return;
        snapshot();
        java.util.Arrays.fill(px, 0);
        double sc = Math.min(w / (double) wh[0], h / (double) wh[1]);
        int tw = Math.max(1, (int) Math.round(wh[0] * sc)), th = Math.max(1, (int) Math.round(wh[1] * sc));
        int[] sm = PngCodec.scale(src, wh[0], wh[1], tw, th);
        int offX = (w - tw) / 2, offY = h - th;
        for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) px[(offY + y) * w + offX + x] = sm[y * tw + x];
        dirtyView = true;
     } finally { rally3d.core.Trace.out(482, $t); } }

    private void rect(int x0, int y0, int x1, int y1, int c) { /*T*/ final long $t = rally3d.core.Trace.in(483); try {
        for (int y = Math.max(0, y0); y <= Math.min(h - 1, y1); y++) for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) px[y * w + x] = c;
     } finally { rally3d.core.Trace.out(483, $t); } }

    private void ellipse(int cx, int cy, int rx, int ry, int c) { /*T*/ final long $t = rally3d.core.Trace.in(484); try {
        rx = Math.max(1, rx); ry = Math.max(1, ry);
        for (int y = Math.max(0, cy - ry); y <= Math.min(h - 1, cy + ry); y++) for (int x = Math.max(0, cx - rx); x <= Math.min(w - 1, cx + rx); x++) {
            double dx = (x - cx) / (double) rx, dy = (y - cy) / (double) ry;
            if (dx * dx + dy * dy <= 1.0) px[y * w + x] = c;
        }
     } finally { rally3d.core.Trace.out(484, $t); } }

    private void tri(int cx, int top, int halfW, int height, int dark, int light) { /*T*/ final long $t = rally3d.core.Trace.in(485); try {
        for (int y = top; y < Math.min(h, top + height); y++) {
            int hw = Math.max(0, (int) ((y - top + 1) * (double) halfW / height));
            for (int x = cx - hw; x <= cx + hw; x++) if (x >= 0 && x < w && y >= 0) px[y * w + x] = x < cx ? light : dark;
        }
     } finally { rally3d.core.Trace.out(485, $t); } }

    // ------------------------------------------------------------------ shapes, flip, colours, import, background / road templates

    private void span(int y, int xa, int xb) { /*T*/ final long $t = rally3d.core.Trace.in(539); try {
        for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++) { put(x, y, color); if (mirror) put(w - 1 - x, y, color); }
        dirtyView = true;
     } finally { rally3d.core.Trace.out(539, $t); } }

    /** LINE / RECT / OVAL between two pixels (the rectangle / oval are the bounding box). Brush size and mirror apply; FILL toggle = solid. */
    private void drawShape(int x0, int y0, int x1, int y1) { /*T*/ final long $t = rally3d.core.Trace.in(540); try {
        if (tool == T_LINE) { line(x0, y0, x1, y1); return; }
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
        if (tool == T_RECT) {
            if (shapeFill) { for (int y = ay; y <= by; y++) span(y, ax, bx); }
            else { line(ax, ay, bx, ay); line(ax, by, bx, by); line(ax, ay, ax, by); line(bx, ay, bx, by); }
            return;
        }
        double cx = (ax + bx) / 2.0, cy = (ay + by) / 2.0, rx = (bx - ax) / 2.0, ry = (by - ay) / 2.0;
        if (rx < 0.5 || ry < 0.5) { line(ax, ay, bx, by); return; }
        for (int y = ay; y <= by; y++) {
            double t = (y - cy) / ry, e = rx * Math.sqrt(Math.max(0, 1 - t * t));
            int xa = (int) Math.round(cx - e), xb = (int) Math.round(cx + e);
            if (shapeFill) span(y, xa, xb); else { stamp(xa, y); stamp(xb, y); }
        }
        if (!shapeFill) for (int x = ax; x <= bx; x++) {
            double t = (x - cx) / rx, e = ry * Math.sqrt(Math.max(0, 1 - t * t));
            stamp(x, (int) Math.round(cy - e)); stamp(x, (int) Math.round(cy + e));
        }
     } finally { rally3d.core.Trace.out(540, $t); } }

    private void flip(boolean horizontal) { /*T*/ final long $t = rally3d.core.Trace.in(541); try {
        snapshot();
        if (horizontal) { for (int y = 0; y < h; y++) for (int x = 0; x < w / 2; x++) { int a = y * w + x, b = y * w + w - 1 - x, t = px[a]; px[a] = px[b]; px[b] = t; } }
        else { for (int y = 0; y < h / 2; y++) for (int x = 0; x < w; x++) { int a = y * w + x, b = (h - 1 - y) * w + x, t = px[a]; px[a] = px[b]; px[b] = t; } }
        dirtyView = true;
     } finally { rally3d.core.Trace.out(541, $t); } }

    /** Background: shifts the picture sideways by a quarter (the seam moves to the middle so it can be seen and fixed). Road: shifts it down by 8 px. Both wrap around. */
    private void roll() { /*T*/ final long $t = rally3d.core.Trace.in(542); try {
        snapshot();
        int[] o = px.clone();
        if (kind == K_ROAD) { for (int y = 0; y < h; y++) System.arraycopy(o, ((y + h - 8) % h) * w, px, y * w, w); }
        else { int d = w / 4; for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = o[y * w + (x + w - d) % w]; }
        dirtyView = true;
     } finally { rally3d.core.Trace.out(542, $t); } }

    /** Background: cross-fades the left and right edge so the picture repeats without a visible seam. */
    private void seamless() { /*T*/ final long $t = rally3d.core.Trace.in(543); try {
        snapshot();
        int[] o = px.clone();
        int f = Math.max(4, w / 6);
        for (int y = 0; y < h; y++) for (int i = 0; i < f; i++) {
            double a = 0.5 * (1.0 - i / (double) f);
            px[y * w + i] = mixc(o[y * w + i], o[y * w + w - 1 - i], a);
            px[y * w + w - 1 - i] = mixc(o[y * w + w - 1 - i], o[y * w + i], a);
        }
        dirtyView = true;
        ed.say(rally3d.core.Lang.t("Edges blended - the picture repeats smoothly"));
     } finally { rally3d.core.Trace.out(543, $t); } }

    private static int mixc(int c0, int c1, double t) { /*T*/ final long $t = rally3d.core.Trace.in(544); try {
        int a = (int) Math.round((c0 >>> 24) * (1 - t) + (c1 >>> 24) * t), r = (int) Math.round(((c0 >> 16) & 255) * (1 - t) + ((c1 >> 16) & 255) * t);
        int g2 = (int) Math.round(((c0 >> 8) & 255) * (1 - t) + ((c1 >> 8) & 255) * t), b = (int) Math.round((c0 & 255) * (1 - t) + (c1 & 255) * t);
        return (a << 24) | (r << 16) | (g2 << 8) | b;
     } finally { rally3d.core.Trace.out(544, $t); } }

    private void setChannel(int ch, int x) { /*T*/ final long $t = rally3d.core.Trace.in(545); try {
        int[] r = sl[ch];
        if (r == null) return;
        int v = Math.max(0, Math.min(255, (x - r[0]) * 255 / Math.max(1, r[2] - 1)));
        int shift = 16 - ch * 8;
        color = 0xFF000000 | (color & ~(255 << shift) & 0xFFFFFF) | (v << shift);
        if (tool == 1 || tool == 3) tool = 0;
     } finally { rally3d.core.Trace.out(545, $t); } }

    private void hexColor() { /*T*/ final long $t = rally3d.core.Trace.in(546); try {
        String cur = Integer.toHexString(color & 0xFFFFFF).toUpperCase(java.util.Locale.ROOT);
        while (cur.length() < 6) cur = "0" + cur;
        String v = ed.askText(rally3d.core.Lang.t("Colour (hex RRGGBB)"), "#", cur, 7);
        if (v == null) return;
        v = v.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() == 3) v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
        try {
            color = 0xFF000000 | Integer.parseInt(v, 16);
            if (v.length() != 6) { ed.say(rally3d.core.Lang.t("Use 6 hex digits, e.g. 3A7BD5")); return; }
            if (tool == 1 || tool == 3) tool = 0;
        } catch (NumberFormatException e) { rally3d.core.RLog.caught("Painter.hexColor", e); ed.say("Not a hex colour: " + v); }
     } finally { rally3d.core.Trace.out(546, $t); } }

    /** Folder for display: the home directory is shown as "~" (no account name on screenshots). */
    private static String shown(File d) { /*T*/ final long $t = rally3d.core.Trace.in(547); try {
        String p = String.valueOf(d), home = System.getProperty("user.home");
        if (home != null && home.length() > 1 && p.startsWith(home)) p = "~" + p.substring(home.length());
        return p.replace('\\', '/');
     } finally { rally3d.core.Trace.out(547, $t); } }

    /** Folder the PNG import reads: addons/import (created on demand). */
    static File importDir() { File d = new File(AddonManager.dir(), "import"); d.mkdirs(); return d; }

    private void openImport() { /*T*/ final long $t = rally3d.core.Trace.in(548); try {
        File dir = importDir();
        File[] all = dir.listFiles();
        ArrayList<File> pngs = new ArrayList<File>();
        if (all != null) for (File fl : all) if (fl.isFile() && fl.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) pngs.add(fl);
        java.util.Collections.sort(pngs);
        if (pngs.isEmpty()) { ed.say(rally3d.core.Lang.t("No PNG files. Put them into ") + shown(dir)); return; }
        while (pngs.size() > 60) pngs.remove(pngs.size() - 1);
        importFiles = pngs.toArray(new File[0]);
        String[] names = new String[importFiles.length];
        for (int i = 0; i < names.length; i++) names[i] = importFiles[i].getName();
        ed.showList(rally3d.core.Lang.t("IMPORT PNG (") + shown(dir) + ")", names, MapEditor.LK_IMPORT);
     } finally { rally3d.core.Trace.out(548, $t); } }

    /** Loads import file i into the picture: sprites keep their proportions (small ones are enlarged in whole steps), backgrounds are stretched, the road accepts a whole 256x32 atlas or one square tile for tiles 0 and 5. */
    void importPng(int i) { /*T*/ final long $t = rally3d.core.Trace.in(549); try {
        if (importFiles == null || i < 0 || i >= importFiles.length) return;
        File fl = importFiles[i];
        if (fl.length() > 1500 * 1024L) { ed.say(rally3d.core.Lang.t("PNG too big (max 1.5 MB): ") + fl.getName()); return; }
        byte[] data;
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(fl);
            try { data = new byte[(int) fl.length()]; int got = 0, r; while (got < data.length && (r = in.read(data, got, data.length - got)) > 0) got += r; }
            finally { in.close(); }
        } catch (java.io.IOException e) { rally3d.core.RLog.caught("Painter.importPng", e); ed.say("Cannot read " + fl.getName()); return; }
        int[] wh = new int[2];
        int[] src = PngCodec.decode(data, wh);
        if (src == null) { ed.say(rally3d.core.Lang.t("Not a usable PNG (max 1024x1024): ") + fl.getName()); return; }
        int sw = wh[0], sh2 = wh[1];
        snapshot();
        if (kind == K_SPRITE) {
            java.util.Arrays.fill(px, 0);
            double sc = Math.min(w / (double) sw, h / (double) sh2);
            int tw, th;
            int[] sm;
            if (sc >= 2) { int fct = (int) sc; tw = sw * fct; th = sh2 * fct; sm = new int[tw * th]; for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) sm[y * tw + x] = src[(y / fct) * sw + x / fct]; }
            else if (sc >= 1) { tw = sw; th = sh2; sm = src; }
            else { tw = Math.max(1, (int) Math.round(sw * sc)); th = Math.max(1, (int) Math.round(sh2 * sc)); sm = PngCodec.scale(src, sw, sh2, tw, th); }
            int offX = (w - tw) / 2, offY = h - th;
            for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) px[(offY + y) * w + offX + x] = sm[y * tw + x];
        } else if (kind == K_BG) {
            int[] sm = PngCodec.scale(src, sw, sh2, w, h);
            for (int k = 0; k < px.length; k++) px[k] = sm[k] | 0xFF000000;
        } else {
            if (sw >= sh2 * 6) {   // a whole atlas
                int[] sm = PngCodec.scale(src, sw, sh2, w, h);
                for (int k = 0; k < px.length; k++) px[k] = sm[k] | 0xFF000000;
            } else {   // one tile -> tiles 0 and 5 (tile 5 gets the centre line)
                int[] sm = PngCodec.scale(src, sw, sh2, 32, 32);
                for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) { px[y * w + x] = sm[y * 32 + x] | 0xFF000000; px[y * w + 160 + x] = sm[y * 32 + x] | 0xFF000000; }
                MapTemplates.centreLine(px, w, 0xFFF2F2EC);
            }
        }
        dirtyView = true;
        ed.say(rally3d.core.Lang.t("Imported ") + fl.getName());
     } finally { rally3d.core.Trace.out(549, $t); } }

    void bgTemplate(int i) { /*T*/ final long $t = rally3d.core.Trace.in(550); try {
        if (kind != K_BG) return;
        snapshot();
        int[] t = MapTemplates.background(i);
        if (w == 128 && h == 128) System.arraycopy(t, 0, px, 0, px.length);
        else { int[] sm = PngCodec.scale(t, 128, 128, w, h); System.arraycopy(sm, 0, px, 0, px.length); }
        dirtyView = true;
        ed.say(rally3d.core.Lang.t(MapTemplates.BG_NAMES[i]) + rally3d.core.Lang.t(" background (repeats left-right)"));
     } finally { rally3d.core.Trace.out(550, $t); } }

    void roadTemplate(int i) { /*T*/ final long $t = rally3d.core.Trace.in(551); try {
        if (kind != K_ROAD || w != 256 || h != 32) return;
        snapshot();
        MapTemplates.road(i, px, w);
        dirtyView = true;
        ed.say(rally3d.core.Lang.t(MapTemplates.ROAD_NAMES[i]) + rally3d.core.Lang.t(" in tiles 0 + 5 (roadside tiles untouched)"));
     } finally { rally3d.core.Trace.out(551, $t); } }

    // ------------------------------------------------------------------ drawing

    private void rebuildView() { /*T*/ final long $t = rally3d.core.Trace.in(486); try {
        vw2 = w * zoom; vh2 = h * zoom;
        if (view == null || view.length != vw2 * vh2) view = new int[vw2 * vh2];
        for (int y = 0; y < vh2; y++) {
            int sy = y / zoom;
            for (int x = 0; x < vw2; x++) {
                int c = px[sy * w + x / zoom];
                int a = c >>> 24;
                if (a == 255) { view[y * vw2 + x] = c; continue; }
                int chk = (((x / Math.max(1, zoom * 2 / 3)) + (y / Math.max(1, zoom * 2 / 3))) & 1) == 0 ? 0xFF505860 : 0xFF3A4048;
                if (a == 0) { view[y * vw2 + x] = chk; continue; }
                int r = (((c >> 16) & 255) * a + ((chk >> 16) & 255) * (255 - a)) / 255, g2 = (((c >> 8) & 255) * a + ((chk >> 8) & 255) * (255 - a)) / 255, b = ((c & 255) * a + (chk & 255) * (255 - a)) / 255;
                view[y * vw2 + x] = 0xFF000000 | (r << 16) | (g2 << 8) | b;
            }
        }
        viewZoom = zoom;
        dirtyView = false;
     } finally { rally3d.core.Trace.out(486, $t); } }

    void draw(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(487); try {
        int W = ed.W, H = ed.H, TB = ed.TB, PW = ed.PW, sh = ed.sh;
        if (dirtyView || viewZoom != zoom) rebuildView();
        gr.setClip(0, TB, W - PW, H - TB);
        ed.fill(gr, 0, TB, W - PW, H - TB, 0xFF12181D);
        int x0 = ox(), y0 = oy();
        gr.drawRGB(view, 0, vw2, x0, y0, vw2, vh2, false);
        ed.frame(gr, x0 - 1, y0 - 1, vw2 + 2, vh2 + 2, MapEditor.WHITE);
        if (zoom >= 6) {
            gr.setColor(0x000000);
            for (int x = 1; x < w; x++) if (x % 8 == 0) gr.drawLine(x0 + x * zoom, y0, x0 + x * zoom, y0 + vh2 - 1);
            for (int y = 1; y < h; y++) if (y % 8 == 0) gr.drawLine(x0, y0 + y * zoom, x0 + vw2 - 1, y0 + y * zoom);
        }
        if (mirror) { gr.setColor(0xFF8020); gr.drawLine(x0 + vw2 / 2, y0, x0 + vw2 / 2, y0 + vh2 - 1); }
        gr.setClip(0, 0, W, H);
        // top bar
        ed.fill(gr, 0, 0, W, TB, MapEditor.PANEL);
        gr.setColor(0x5A7088); gr.drawLine(0, TB - 1, W, TB - 1);
        int bw = Math.max(34, ed.textW(rally3d.core.Lang.t("CANCEL"), true) + 10), bh = TB - 3;
        ed.button(gr, W - 2 - bw, 1, bw, bh, rally3d.core.Lang.t("DONE"), A_(P_DONE), 0, true);
        ed.button(gr, W - 4 - bw * 2, 1, bw, bh, rally3d.core.Lang.t("CANCEL"), A_(P_CANCEL), 0, false);
        ed.button(gr, 2, 1, bw, bh, rally3d.core.Lang.t("UNDO"), A_(P_UNDO), 0, false);
        ed.button(gr, 4 + bw, 1, bw, bh, rally3d.core.Lang.t("REDO"), A_(P_REDO), 0, false);
        ed.text(gr, title + " " + w + "x" + h + "  x" + zoom, 8 + bw * 2, (TB - sh) / 2, MapEditor.YEL, true);
        // right panel: scrolls (palette, RGB sliders, tools, brush, effects, templates)
        int px0 = W - PW;
        ed.fill(gr, px0, TB, PW, H - TB, MapEditor.PANEL);
        gr.setColor(0x5A7088); gr.drawLine(px0, TB, px0, H);
        ed.button(gr, W - PW - 26, TB + 3, 22, 18, "+", A_(P_ZIN), 0, false);
        ed.button(gr, W - PW - 26, TB + 23, 22, 18, "-", A_(P_ZOUT), 0, false);
        gr.setClip(px0 + 1, TB, PW - 1, H - TB);
        int pw = PW - 6, bhh = sh + 7, y = TB + 3 - panelScroll, lo = TB, hi = H;
        int cell = (PW - 6) / 6;
        for (int i = 0; i < pal.length; i++) {
            int cx = px0 + 3 + (i % 6) * cell, cy = y + (i / 6) * cell;
            ed.fill(gr, cx, cy, cell - 1, cell - 1, pal[i]);
            if (pal[i] == color) ed.frame(gr, cx, cy, cell - 1, cell - 1, MapEditor.WHITE);
            if (cy >= lo && cy + cell <= hi) ed.addBtn(cx, cy, cell - 1, cell - 1, A_(P_COL + i), 0);
        }
        y += ((pal.length + 5) / 6) * cell + 3;
        ed.fill(gr, px0 + 3, y, 14, 10, 0xFF000000 | color);
        ed.frame(gr, px0 + 3, y, 14, 10, MapEditor.WHITE);
        ed.text(gr, rally3d.core.Lang.t(TOOL_NAMES[tool]) + (tool >= T_RECT ? (shapeFill ? rally3d.core.Lang.t(" SOLID") : rally3d.core.Lang.t(" LINE")) : ""), px0 + 21, y - 1, MapEditor.YEL, true);
        y += Math.max(12, sh + 2);
        // RGB sliders (tap or drag) + hex entry
        String[] cn = {"R", "G", "B"};
        int[] cols = {0xFFE04040, 0xFF40C040, 0xFF4080F0};
        for (int c = 0; c < 3; c++) {
            int bx = px0 + 3 + 9, bw3 = pw - 9, val = (color >> (16 - c * 8)) & 255;
            ed.text(gr, cn[c], px0 + 3, y, MapEditor.YEL, true);
            ed.fill(gr, bx, y, bw3, sh + 2, 0xFF141C22);
            ed.fill(gr, bx, y, Math.max(1, (bw3 - 1) * val / 255), sh + 2, (cols[c] & 0x00FFFFFF) | 0xA0000000);
            ed.frame(gr, bx, y, bw3, sh + 2, 0xFF5A7088);
            ed.textC(gr, String.valueOf(val), bx + bw3 / 2, y + 1, MapEditor.WHITE, true);
            sl[c] = (y >= lo && y + sh + 2 <= hi) ? new int[]{bx, y, bw3, sh + 2} : null;
            y += sh + 4;
        }
        int bw2 = (PW - 8) / 2;
        y = pb(gr, px0 + 3, y, pw, bhh, rally3d.core.Lang.t("HEX COLOUR..."), A_(P_HEX), false);
        y += 2;
        int[] shown = alpha ? new int[]{0, 1, 2, 3, 4, 5, 6} : new int[]{0, 2, 3, 4, 5, 6};
        for (int t = 0; t < shown.length; t += 2) {
            pb(gr, px0 + 3, y, bw2, bhh, rally3d.core.Lang.t(TOOL_NAMES[shown[t]]), A_(P_TOOL + shown[t]), tool == shown[t]);
            if (t + 1 < shown.length) pb(gr, px0 + 5 + bw2, y, bw2, bhh, rally3d.core.Lang.t(TOOL_NAMES[shown[t + 1]]), A_(P_TOOL + shown[t + 1]), tool == shown[t + 1]);
            y += bhh + 2;
        }
        y = pb(gr, px0 + 3, y, pw, bhh, shapeFill ? rally3d.core.Lang.t("RECT / OVAL: SOLID") : rally3d.core.Lang.t("RECT / OVAL: LINE"), A_(P_SHAPEFILL), shapeFill);
        y += 2;
        int b3 = (PW - 10) / 3;
        for (int b = 0; b < 3; b++) pb(gr, px0 + 3 + b * (b3 + 2), y, b3, bhh, String.valueOf(b + 1), A_(P_BRUSH + b), brush == b + 1);
        y += bhh + 2;
        pb(gr, px0 + 3, y, bw2, bhh, rally3d.core.Lang.t("MIRROR"), A_(P_MIRROR), mirror);
        pb(gr, px0 + 5 + bw2, y, bw2, bhh, rally3d.core.Lang.t("CLEAR"), A_(P_CLEAR), false);
        y += bhh + 2;
        pb(gr, px0 + 3, y, bw2, bhh, rally3d.core.Lang.t("FLIP H"), A_(P_FLIPH), false);
        pb(gr, px0 + 5 + bw2, y, bw2, bhh, rally3d.core.Lang.t("FLIP V"), A_(P_FLIPV), false);
        y += bhh + 2;
        if (kind == K_SPRITE) {
            pb(gr, px0 + 3, y, bw2, bhh, rally3d.core.Lang.t("TEMPLATE"), A_(P_TEMPL), false);
            pb(gr, px0 + 5 + bw2, y, bw2, bhh, rally3d.core.Lang.t("STOCK"), A_(P_STOCK), false);
            y += bhh + 2;
        } else if (kind == K_BG) {
            pb(gr, px0 + 3, y, bw2, bhh, rally3d.core.Lang.t("TEMPLATE"), A_(P_BGTPL), false);
            pb(gr, px0 + 5 + bw2, y, bw2, bhh, rally3d.core.Lang.t("ROLL"), A_(P_ROLL), false);
            y += bhh + 2;
            y = pb(gr, px0 + 3, y, pw, bhh, rally3d.core.Lang.t("MAKE SEAMLESS"), A_(P_SEAM), false);
            y += 2;
        } else {
            pb(gr, px0 + 3, y, bw2, bhh, rally3d.core.Lang.t("ROAD SET"), A_(P_ROADTPL), false);
            pb(gr, px0 + 5 + bw2, y, bw2, bhh, rally3d.core.Lang.t("ROLL"), A_(P_ROLL), false);
            y += bhh + 2;
        }
        y = pb(gr, px0 + 3, y, pw, bhh, rally3d.core.Lang.t("IMPORT PNG..."), A_(P_IMPORT), false);
        y += 4;
        if (kind == K_ROAD) { ed.text(gr, rally3d.core.Lang.t("tile 0 = road, 5 = centre line"), px0 + 3, y, MapEditor.DIM, true); y += sh + 2; }
        panelContent = y + panelScroll - TB + 2;
        gr.setClip(0, 0, W, H);
        if (panelContent > H - TB) {
            int tr = H - TB, hh = Math.max(10, tr * tr / panelContent), yy = TB + (tr - hh) * panelScroll / Math.max(1, panelContent - tr);
            ed.fill(gr, W - 2, yy, 2, hh, 0xB0FFFFAA);
        }
     } finally { rally3d.core.Trace.out(487, $t); } }

    /** Panel button at y (scrolls with the panel); returns the y below it. Only buttons fully inside the panel are tappable. */
    private int pb(Graphics gr, int x, int y, int w2, int h2, String label, int id, boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(552); try {
        if (y + h2 > ed.TB && y < ed.H) {
            if (y >= ed.TB && y + h2 <= ed.H) ed.button(gr, x, y, w2, h2, label, id, 0, on);
            else ed.fill(gr, x, y, w2, h2, MapEditor.BTN);
        }
        return y + h2 + 2;
     } finally { rally3d.core.Trace.out(552, $t); } }

    private static int A_(int id) { return MapEditor.A_P + id; }
}
