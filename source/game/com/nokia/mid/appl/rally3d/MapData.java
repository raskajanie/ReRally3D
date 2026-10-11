package com.nokia.mid.appl.rally3d;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A custom map as the in-game editor sees it: one row of attributes per road SEGMENT (the stock tracks store runs of identical
 * segments, see {@link #toTrackBin}). The model knows how to simulate the exact geometry the track loader produces
 * (position, heading, height, slope per segment), how to fit road curvature to a drawn height profile and how to steer a
 * road towards a tapped point, so what the editor shows is what the game builds.
 *
 * Units: one segment = 8 simulation steps. yaw = stock "s4" (turn per step in 1/2048 half-turns, positive = RIGHT turn),
 * curv = stock "s5" (change of slope per step, 1/16384 * 1.3 per unit).
 */
final class MapData {
    static final int MAX_SEGS = 900, MIN_SEGS = 30, START_SEG = 5, CUSTOM_SPRITES = 4;
    /** Degrees of heading change per segment per yaw unit (8 steps * 180 / 2048). */
    static final double DEG_PER_YAW = 8.0 * 180.0 / 2048.0;
    static final double CURV_SCALE = 1.3 / 16384.0;
    static final int MAX_YAW = 55, MAX_CURV = 4500;
    /** Terrain targets the stock loader understands (profile transitions are 2-5 segments long and handled by the game). */
    static final int[] TERRAIN = {0, 3, 6, 7, 11};
    static final String[] TERRAIN_NAMES = {"HILLS", "ROCK WALL", "HIGH WALL", "DROP-OFF", "GUARD WALL"};
    /** Copy of RallyGame.profileBlend (the transition table of the loader), used to keep terrain changes legal. */
    static final short[][] PB = {
        {0, -1, -1, 1, -1, -1, 1, 9, -1, -1, -1, 9, -1, -1, -1}, {2, -1, -1, 3, -1, -1, 4, 2, -1, -1, -1, 2, -1, -1, -1},
        {0, -1, -1, 1, -1, -1, 1, 9, -1, -1, -1, 9, -1, -1, -1}, {2, -1, -1, 3, -1, -1, 4, 2, -1, -1, -1, 2, -1, -1, -1},
        {5, -1, -1, 5, -1, -1, 6, 5, -1, -1, -1, 5, -1, -1, -1}, {2, -1, -1, 3, -1, -1, 4, 2, -1, -1, -1, 2, -1, -1, -1},
        {5, -1, -1, 5, -1, -1, 6, 5, -1, -1, -1, 5, -1, -1, -1}, {8, -1, -1, 8, -1, -1, 8, 7, -1, -1, -1, 12, -1, -1, -1},
        {0, -1, -1, 1, -1, -1, 1, 9, -1, -1, -1, 9, -1, -1, -1}, {8, -1, -1, 8, -1, -1, 8, 7, -1, -1, -1, 12, -1, -1, -1},
        {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, {13, -1, -1, 13, -1, -1, 13, 13, -1, -1, -1, 11, -1, -1, -1},
        {13, -1, -1, 13, -1, -1, 13, 13, -1, -1, -1, 11, -1, -1, -1}, {8, -1, -1, 8, -1, -1, 8, 7, -1, -1, -1, 12, -1, -1, -1}};

    // sky / ground / HUD colour of the four stock themes (desert, winter, summer, night)
    static final int[][] THEME_COLORS = {{-9667900, -4940137, -7770560}, {-12821864, -2828839, -86}, {-10058565, -10198189, -86}, {-9078942, -16514546, -86}};
    static final String[] THEME_NAMES = {"DESERT", "WINTER", "SUMMER", "NIGHT"};
    static final String[] THEME_DIRS = {"/desert", "/winter", "/summer", "/night"};

    // ---- identity / look
    String name = "My map", author = "", desc = "";
    int theme = 2;
    int bgFrom = -1, roadFrom = -1;          // -1 = the map's own theme, 0..3 = borrow that theme's background / road texture
    boolean customColors;
    int sky, ground, hud;                    // ARGB, only used when customColors
    byte[] bgPng, roadPng;                   // painted background (128x128) / road texture (256x32), null = stock
    final int[][] spritePx = new int[CUSTOM_SPRITES][];   // painted sprite frame 0 (ARGB), null = unused slot
    final int[] spriteW = new int[CUSTOM_SPRITES], spriteH = new int[CUSTOM_SPRITES];
    final int[] cp = {-1, -1, -1, -1};       // checkpoint 1..3 and finish segment, -1 = automatic

    // ---- segments
    int n;
    short[] yaw, curv, tgt, cm, lt, ll, rt, rl;

    // ---- simulated geometry (index 0..n, entry n = end of the road); valid while !dirty
    double[] gx, gz, gdx, gdz, gh, gs;
    boolean dirty = true;
    /** Bumped on every change (editor undo/autosave and geometry caches look at it). */
    int revision;

    MapData() {
        allocate(MAX_SEGS + 8);
        n = 0;
        for (int i = 0; i < 40; i++) addBlank(i > 0 ? 0 : 0);
        theme = 2;
        for (int i = 0; i < n; i++) { cm[i] = 1; }
        touch();
    }

    private void allocate(int cap) { /*T*/ final long $t = rally3d.core.Trace.in(356); try {
        yaw = new short[cap]; curv = new short[cap]; tgt = new short[cap]; cm = new short[cap];
        lt = new short[cap]; ll = new short[cap]; rt = new short[cap]; rl = new short[cap];
     } finally { rally3d.core.Trace.out(356, $t); } }

    private void addBlank(int unused) {
        yaw[n] = 0; curv[n] = 0; tgt[n] = n > 0 ? tgt[n - 1] : 0; cm[n] = 1;
        lt[n] = -2; ll[n] = -2; rt[n] = -2; rl[n] = -2;
        n++;
    }

    void touch() { dirty = true; revision++; }

    MapData copy() { /*T*/ final long $t = rally3d.core.Trace.in(357); try {
        MapData m = new MapData();
        m.name = name; m.author = author; m.desc = desc; m.theme = theme; m.bgFrom = bgFrom; m.roadFrom = roadFrom;
        m.customColors = customColors; m.sky = sky; m.ground = ground; m.hud = hud;
        m.bgPng = bgPng; m.roadPng = roadPng;
        for (int i = 0; i < CUSTOM_SPRITES; i++) { m.spritePx[i] = spritePx[i] == null ? null : spritePx[i].clone(); m.spriteW[i] = spriteW[i]; m.spriteH[i] = spriteH[i]; }
        System.arraycopy(cp, 0, m.cp, 0, 4);
        m.n = n;
        int cap = yaw.length;
        m.allocate(cap);
        System.arraycopy(yaw, 0, m.yaw, 0, cap); System.arraycopy(curv, 0, m.curv, 0, cap); System.arraycopy(tgt, 0, m.tgt, 0, cap);
        System.arraycopy(cm, 0, m.cm, 0, cap); System.arraycopy(lt, 0, m.lt, 0, cap); System.arraycopy(ll, 0, m.ll, 0, cap);
        System.arraycopy(rt, 0, m.rt, 0, cap); System.arraycopy(rl, 0, m.rl, 0, cap);
        m.touch();
        return m;
     } finally { rally3d.core.Trace.out(357, $t); } }

    /** Restores everything from a snapshot made with {@link #copy} (undo). */
    void restore(MapData s) { /*T*/ final long $t = rally3d.core.Trace.in(358); try {
        name = s.name; author = s.author; desc = s.desc; theme = s.theme; bgFrom = s.bgFrom; roadFrom = s.roadFrom;
        customColors = s.customColors; sky = s.sky; ground = s.ground; hud = s.hud; bgPng = s.bgPng; roadPng = s.roadPng;
        for (int i = 0; i < CUSTOM_SPRITES; i++) { spritePx[i] = s.spritePx[i] == null ? null : s.spritePx[i].clone(); spriteW[i] = s.spriteW[i]; spriteH[i] = s.spriteH[i]; }
        System.arraycopy(s.cp, 0, cp, 0, 4);
        n = s.n;
        int cap = Math.min(yaw.length, s.yaw.length);
        System.arraycopy(s.yaw, 0, yaw, 0, cap); System.arraycopy(s.curv, 0, curv, 0, cap); System.arraycopy(s.tgt, 0, tgt, 0, cap);
        System.arraycopy(s.cm, 0, cm, 0, cap); System.arraycopy(s.lt, 0, lt, 0, cap); System.arraycopy(s.ll, 0, ll, 0, cap);
        System.arraycopy(s.rt, 0, rt, 0, cap); System.arraycopy(s.rl, 0, rl, 0, cap);
        touch();
     } finally { rally3d.core.Trace.out(358, $t); } }

    // ------------------------------------------------------------------ colours

    int skyColor() { return customColors ? sky : THEME_COLORS[theme][0]; }
    int groundColor() { return customColors ? ground : THEME_COLORS[theme][1]; }
    int hudColor() { return customColors ? hud : THEME_COLORS[theme][2]; }

    // ------------------------------------------------------------------ geometry

    /** Simulates the road exactly like RallyGame.loadTrackData (detail 3: two sections of four steps per segment). */
    void geo() { /*T*/ final long $t = rally3d.core.Trace.in(359); try {
        if (!dirty && gx != null && gx.length >= n + 1) return;
        int cap = n + 1;
        if (gx == null || gx.length < cap) { gx = new double[cap + 64]; gz = new double[cap + 64]; gdx = new double[cap + 64]; gdz = new double[cap + 64]; gh = new double[cap + 64]; gs = new double[cap + 64]; }
        double x = 0, z = 0, dx = 0, dz = 16.0, f = 0, f2 = 0;
        for (int i = 0; i < n; i++) {
            gx[i] = x; gz[i] = z; gdx[i] = dx; gdz[i] = dz; gh[i] = f; gs[i] = f2;
            double f3 = (double) (float) ((double) curv[i] / 16384.0 * 1.3);
            double a = Math.toRadians(-(yaw[i] * 180.0 / 2048.0)), c = Math.cos(a), s = Math.sin(a);
            for (int sec = 0; sec < 2; sec++) {
                x += dx; z += dz;
                for (int k = 0; k < 4; k++) {
                    f += f2; f2 += f3;
                    double ndx = dx * c + dz * s, ndz = -dx * s + dz * c;
                    dx = ndx; dz = ndz;
                }
            }
        }
        gx[n] = x; gz[n] = z; gdx[n] = dx; gdz[n] = dz; gh[n] = f; gs[n] = f2;
        dirty = false;
     } finally { rally3d.core.Trace.out(359, $t); } }

    double heightAt(int seg) { geo(); return gh[Math.max(0, Math.min(n, seg))]; }
    double slopeAt(int seg) { geo(); return gs[Math.max(0, Math.min(n, seg))]; }
    /** Heading of the road at a segment start in degrees, 0 = +Z, positive = towards +X (which is the LEFT of the driver). */
    double headingDeg(int seg) { geo(); int i = Math.max(0, Math.min(n, seg)); return Math.toDegrees(Math.atan2(gdx[i], gdz[i])); }

    /** Largest |slope| (height per simulation step) of the road, and the first segment where it exceeds {@code limit} (or -1). */
    int firstSteep(double limit) { geo(); for (int i = 0; i <= n; i++) if (Math.abs(gs[i]) > limit) return i; return -1; }

    // ------------------------------------------------------------------ editing: structure

    /** Inserts {@code count} blank segments before index {@code at} (they inherit terrain and centre line of the previous segment). */
    void insert(int at, int count) { /*T*/ final long $t = rally3d.core.Trace.in(360); try {
        count = Math.min(count, MAX_SEGS - n);
        if (count <= 0) return;
        at = Math.max(0, Math.min(n, at));
        for (short[] a : new short[][]{yaw, curv, tgt, cm, lt, ll, rt, rl}) System.arraycopy(a, at, a, at + count, n - at);
        short t = at > 0 ? tgt[at - 1] : (n > 0 ? tgt[0] : 0), c = at > 0 ? cm[at - 1] : 1;
        for (int i = at; i < at + count; i++) { yaw[i] = 0; curv[i] = 0; tgt[i] = t; cm[i] = c; lt[i] = -2; ll[i] = -2; rt[i] = -2; rl[i] = -2; }
        n += count;
        for (int k = 0; k < 4; k++) if (cp[k] >= at) cp[k] += count;
        touch();
     } finally { rally3d.core.Trace.out(360, $t); } }

    /** Deletes segments a..b (inclusive). */
    void delete(int a, int b) { /*T*/ final long $t = rally3d.core.Trace.in(361); try {
        a = Math.max(0, a); b = Math.min(n - 1, b);
        if (b < a) return;
        int cnt = b - a + 1;
        if (n - cnt < 1) return;
        for (short[] arr : new short[][]{yaw, curv, tgt, cm, lt, ll, rt, rl}) System.arraycopy(arr, b + 1, arr, a, n - b - 1);
        n -= cnt;
        for (int k = 0; k < 4; k++) { if (cp[k] > b) cp[k] -= cnt; else if (cp[k] >= a) cp[k] = -1; }
        touch();
     } finally { rally3d.core.Trace.out(361, $t); } }

    /** Inserts a bend of {@code degrees} (positive = right) spread over {@code segs} segments with a smooth entry and exit. Returns the index of its first segment. */
    int insertArc(int at, int degrees, int segs) { /*T*/ final long $t = rally3d.core.Trace.in(362); try {
        at = Math.max(0, Math.min(n, at));
        int before = n;
        insert(at, segs);
        int added = n - before;
        if (added <= 0) return at;
        double total = degrees / DEG_PER_YAW;   // yaw units over the whole bend
        double[] w = new double[added];
        double sum = 0;
        for (int i = 0; i < added; i++) { double u = (i + 0.5) / added; w[i] = 0.35 + Math.sin(Math.PI * u); sum += w[i]; }
        double err = 0;
        for (int i = 0; i < added; i++) {
            double v = total * w[i] / sum + err;
            int r = (int) Math.round(v);
            err = v - r;
            yaw[at + i] = (short) Math.max(-MAX_YAW, Math.min(MAX_YAW, r));
        }
        touch();
        return at;
     } finally { rally3d.core.Trace.out(362, $t); } }

    // ------------------------------------------------------------------ clipboard / stretching

    /** Copied segments (all attributes: turn, terrain, centre line, props). */
    static final class Clip { int n; short[][] a; }

    Clip copyRange(int a, int b) { /*T*/ final long $t = rally3d.core.Trace.in(363); try {
        a = Math.max(0, a); b = Math.min(n - 1, b);
        Clip c = new Clip();
        c.n = Math.max(0, b - a + 1);
        short[][] src = {yaw, curv, tgt, cm, lt, ll, rt, rl};
        c.a = new short[8][];
        for (int k = 0; k < 8; k++) { c.a[k] = new short[c.n]; System.arraycopy(src[k], a, c.a[k], 0, c.n); }
        return c;
     } finally { rally3d.core.Trace.out(363, $t); } }

    /** Inserts a copy of the clip before segment {@code at}; returns the number of segments really inserted. Height is left level (see MapEditor). */
    int pasteRange(Clip c, int at) { /*T*/ final long $t = rally3d.core.Trace.in(364); try {
        int before = n;
        insert(at, c.n);
        int got = n - before;
        short[][] dst = {yaw, curv, tgt, cm, lt, ll, rt, rl};
        for (int k = 0; k < 8; k++) System.arraycopy(c.a[k], 0, dst[k], Math.max(0, Math.min(at, n - got)), got);
        touch();
        return got;
     } finally { rally3d.core.Trace.out(364, $t); } }

    /** Inserts {@code count} copies of segment {@code src} (its turn, terrain, centre line and props) before {@code at} - the "stretch the clip" edit. */
    int stretch(int src, int at, int count) { /*T*/ final long $t = rally3d.core.Trace.in(365); try {
        int before = n;
        insert(at, count);
        int got = n - before;
        int s = src >= at ? src + got : src;
        int base = Math.max(0, Math.min(n - got, at));
        for (int i = 0; i < got; i++) {
            yaw[base + i] = yaw[s]; tgt[base + i] = tgt[s]; cm[base + i] = cm[s];
            lt[base + i] = lt[s]; ll[base + i] = ll[s]; rt[base + i] = rt[s]; rl[base + i] = rl[s];
        }
        touch();
        return got;
     } finally { rally3d.core.Trace.out(365, $t); } }

    /**
     * New road segments from..to continue what is before them: terrain / centre line come from insert(), props keep their kind, lane
     * and spacing (a tree every 2nd segment keeps being a tree every 2nd segment).
     */
    void inheritProps(int from, int to) { /*T*/ final long $t = rally3d.core.Trace.in(366); try {
        for (int side = 0; side < 2; side++) {
            short[] t = side == 0 ? lt : rt, l = side == 0 ? ll : rl;
            int j2 = -1, j1 = -1;
            for (int i = from - 1; i >= 0 && i >= from - 40; i--) {
                if (t[i] == -2) continue;
                if (j2 < 0) j2 = i; else if (t[i] == t[j2] && l[i] == l[j2]) { j1 = i; break; } else break;
            }
            if (j2 < 0) continue;
            int period = j1 >= 0 ? Math.max(1, Math.min(10, j2 - j1)) : 1;
            for (int i = from; i <= to && i < n; i++) {
                boolean put = (i - j2) % period == 0;
                t[i] = put ? t[j2] : (short) -2; l[i] = put ? l[j2] : (short) -2;
            }
        }
        touch();
     } finally { rally3d.core.Trace.out(366, $t); } }

    // ------------------------------------------------------------------ editing: bulk attribute setters

    void setYaw(int a, int b, int v) { /*T*/ final long $t = rally3d.core.Trace.in(367); try { a = Math.max(0, a); b = Math.min(n - 1, b); for (int i = a; i <= b; i++) yaw[i] = (short) Math.max(-MAX_YAW, Math.min(MAX_YAW, v)); touch();  } finally { rally3d.core.Trace.out(367, $t); } }
    void addYaw(int a, int b, int d) { /*T*/ final long $t = rally3d.core.Trace.in(368); try { a = Math.max(0, a); b = Math.min(n - 1, b); for (int i = a; i <= b; i++) yaw[i] = (short) Math.max(-MAX_YAW, Math.min(MAX_YAW, yaw[i] + d)); touch();  } finally { rally3d.core.Trace.out(368, $t); } }
    void setTerrain(int a, int b, int target) { a = Math.max(0, a); b = Math.min(n - 1, b); for (int i = a; i <= b; i++) tgt[i] = (short) target; touch(); }
    void setCenter(int a, int b, int on) { a = Math.max(0, a); b = Math.min(n - 1, b); for (int i = a; i <= b; i++) cm[i] = (short) on; touch(); }

    /**
     * Places a prop on one side of segments a..b: every {@code every}-th segment gets {@code type} on {@code lane}, the others
     * are cleared. side 0 = left, 1 = right, 2 = both. type -2 = nothing, -1 = random stock sprite.
     */
    void paintProps(int a, int b, int side, int type, int lane, int every) { /*T*/ final long $t = rally3d.core.Trace.in(369); try {
        a = Math.max(0, a); b = Math.min(n - 1, b);
        every = Math.max(1, every);
        for (int i = a; i <= b; i++) {
            boolean put = type != -2 && (i - a) % every == 0;
            if (side == 0 || side == 2) { lt[i] = (short) (put ? type : -2); ll[i] = (short) (put ? lane : -2); }
            if (side == 1 || side == 2) { rt[i] = (short) (put ? type : -2); rl[i] = (short) (put ? lane : -2); }
        }
        touch();
     } finally { rally3d.core.Trace.out(369, $t); } }

    // ------------------------------------------------------------------ editing: height profile

    static final int CTRL_STEP = 4;   // one height control point every 4 segments

    int ctrlCount() { return (n + CTRL_STEP - 1) / CTRL_STEP + 1; }

    /** Height of the road at every control point (what the side view shows and lets the user drag). */
    float[] sampleCtrl() { /*T*/ final long $t = rally3d.core.Trace.in(370); try {
        geo();
        int c = ctrlCount();
        float[] h = new float[c];
        for (int k = 0; k < c; k++) h[k] = (float) gh[Math.min(n, k * CTRL_STEP)];
        return h;
     } finally { rally3d.core.Trace.out(370, $t); } }

    private static double catmull(float[] p, double t) { /*T*/ final long $t = rally3d.core.Trace.in(371); try {
        int i = (int) Math.floor(t);
        double u = t - i;
        int last = p.length - 1;
        double p0 = p[Math.max(0, Math.min(last, i - 1))], p1 = p[Math.max(0, Math.min(last, i))],
               p2 = p[Math.max(0, Math.min(last, i + 1))], p3 = p[Math.max(0, Math.min(last, i + 2))];
        double m1 = (p2 - p0) * 0.5, m2 = (p3 - p1) * 0.5;
        double u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * p1 + (u3 - 2 * u2 + u) * m1 + (-2 * u3 + 3 * u2) * p2 + (u3 - u2) * m2;
     } finally { rally3d.core.Trace.out(371, $t); } }

    /** Re-derives the road curvature of every segment so the simulated height follows the spline through {@code ctrl}. */
    void fitHeights(float[] ctrl) { /*T*/ final long $t = rally3d.core.Trace.in(372); try {
        double[] target = new double[n + 1];
        for (int j = 0; j <= n; j++) target[j] = catmull(ctrl, j / (double) CTRL_STEP);
        target[0] = 0;
        double f = 0, f2 = 0;
        for (int i = 0; i < n; i++) {
            int look = Math.min(3, n - i);
            double steps = 8.0 * look;
            double c = (target[i + look] - f - steps * f2) / (steps * (steps - 1) / 2.0);
            int s5 = (int) Math.round(c / CURV_SCALE);
            s5 = Math.max(-MAX_CURV, Math.min(MAX_CURV, s5));
            curv[i] = (short) s5;
            double f3 = (double) (float) ((double) s5 / 16384.0 * 1.3);
            for (int k = 0; k < 8; k++) { f += f2; f2 += f3; }
        }
        touch();
     } finally { rally3d.core.Trace.out(372, $t); } }

    /**
     * After segments were inserted ({@code delta > 0}, at index {@code at}) or deleted ({@code delta < 0}, the {@code -delta} segments from {@code at}),
     * keeps the height profile the user drew: {@code old} is sampleCtrl() from before the edit. Inserted stretches stay level.
     */
    void refitAfterResize(float[] old, int at, int delta) { /*T*/ final long $t = rally3d.core.Trace.in(373); try {
        int c = ctrlCount();
        float[] nc = new float[c];
        for (int k = 0; k < c; k++) {
            int j = k * CTRL_STEP;
            double pos;
            if (delta >= 0) pos = j <= at ? j : (j >= at + delta ? j - delta : at);
            else pos = j < at ? j : j - delta;
            nc[k] = (float) catmull(old, pos / CTRL_STEP);
        }
        fitHeights(nc);
     } finally { rally3d.core.Trace.out(373, $t); } }

    void flattenHeights() { for (int i = 0; i < n; i++) curv[i] = 0; touch(); }

    // ------------------------------------------------------------------ editing: steering the road towards a point

    private static double normDeg(double d) { while (d > 180) d -= 360; while (d < -180) d += 360; return d; }

    /**
     * Extends the road from its current end towards the world point (tx, tz) with a smooth pure-pursuit steer.
     * Returns the number of segments added (0 when the point is too close or behind the road end).
     */
    int extendToward(double tx, double tz) { /*T*/ final long $t = rally3d.core.Trace.in(374); try {
        geo();
        double px = gx[n], pz = gz[n], dx = gdx[n], dz = gdz[n];
        int prev = n > 0 ? yaw[n - 1] : 0;
        int base = n, added = 0, bestAdded = 0;
        double best = Math.hypot(tx - px, tz - pz);
        if (best < 24) return 0;
        int worse = 0;
        short[] ys = new short[160];
        while (base + added < MAX_SEGS && added < ys.length) {
            double vx = tx - px, vz = tz - pz, dist = Math.hypot(vx, vz);
            if (dist < 24) { bestAdded = added; break; }
            double err = normDeg(Math.toDegrees(Math.atan2(vx, vz) - Math.atan2(dx, dz)));
            int want = (int) Math.round(-(err * 0.55) / DEG_PER_YAW);
            want = Math.max(-MAX_YAW, Math.min(MAX_YAW, want));
            int s4 = Math.max(prev - 6, Math.min(prev + 6, want));
            s4 = Math.max(-MAX_YAW, Math.min(MAX_YAW, s4));
            double a = Math.toRadians(-(s4 * 180.0 / 2048.0)), c = Math.cos(a), s = Math.sin(a);
            for (int sec = 0; sec < 2; sec++) {
                px += dx; pz += dz;
                for (int k = 0; k < 4; k++) { double ndx = dx * c + dz * s, ndz = -dx * s + dz * c; dx = ndx; dz = ndz; }
            }
            ys[added++] = (short) s4;
            prev = s4;
            double d2 = Math.hypot(tx - px, tz - pz);
            if (d2 < best) { best = d2; bestAdded = added; worse = 0; } else if (++worse > 7) break;
        }
        if (added > 0 && bestAdded == 0) bestAdded = Math.min(added, 3);
        if (bestAdded <= 0) return 0;
        int before = n;
        insert(n, bestAdded);
        int got = n - before;
        for (int i = 0; i < got; i++) yaw[before + i] = ys[i];
        touch();
        return got;
     } finally { rally3d.core.Trace.out(374, $t); } }

    // ------------------------------------------------------------------ checkpoints

    int effCp(int k) {
        if (cp[k] >= 0 && cp[k] < n) return cp[k];
        return k < 3 ? n * (k + 1) / 4 : n - 5;
    }

    /** Spreads the three checkpoints evenly over the road between the start and the finish line. */
    void autoCheckpoints() { /*T*/ final long $t = rally3d.core.Trace.in(375); try {
        int fin = effCp(3);
        cp[3] = fin;
        for (int k = 0; k < 3; k++) cp[k] = START_SEG + 3 + (fin - START_SEG - 3) * (k + 1) / 4;
        touch();
     } finally { rally3d.core.Trace.out(375, $t); } }

    // ------------------------------------------------------------------ validation

    /** Null when the map can be played, else the first problem in plain words. */
    String validate() { /*T*/ final long $t = rally3d.core.Trace.in(376); try {
        if (n < MIN_SEGS) return "Road too short (" + n + "/" + MIN_SEGS + " segments)";
        if (name.trim().length() == 0) return "Give the map a name";
        int a = effCp(0), b = effCp(1), c = effCp(2), f = effCp(3);
        if (a < START_SEG + 3) return "Checkpoint 1 is too close to the start";
        if (!(a < b && b < c && c < f)) return "Checkpoints must be in order: 1 < 2 < 3 < finish";
        if (f > n - 4) return "Finish line needs 4+ segments of road after it";
        return null;
     } finally { rally3d.core.Trace.out(376, $t); } }

    // ------------------------------------------------------------------ terrain legality

    private static short blend(short target, short state) {
        if (state == -1 || state == target) return target;
        return PB[state][target];
    }

    /** Terrain target per segment as the loader can really follow it: a new target is only accepted once the previous one has settled. */
    short[] effectiveTargets() { /*T*/ final long $t = rally3d.core.Trace.in(377); try {
        short[] e = new short[n];
        short cur = n > 0 ? tgt[0] : 0, state = -1;
        for (int i = 0; i < n; i++) {
            short d = tgt[i];
            if (i < 3) d = tgt[0];
            if (d != cur && state == cur) cur = d;
            state = blend(cur, state);
            e[i] = cur;
        }
        return e;
     } finally { rally3d.core.Trace.out(377, $t); } }

    // ------------------------------------------------------------------ track.bin

    /** Stock track format: theme, record count, then per run of identical segments 10 little-endian shorts. */
    byte[] toTrackBin() { /*T*/ final long $t = rally3d.core.Trace.in(378); try {
        short[] e = effectiveTargets();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int records = 0;
        int i = 0;
        while (i < n) {
            short s7 = e[Math.min(n - 1, i + 1)];
            int j = i + 1;
            while (j < n && j - i < 60000 && same(i, j, e)) j++;
            // the run's end profile is the target of the segment after the run's LAST segment
            short end = e[Math.min(n - 1, j)];
            if (j - i == 1) end = s7;
            put(body, j - i); put(body, yaw[i]); put(body, curv[i]); put(body, e[i]); put(body, end);
            put(body, cm[i]); put(body, lt[i]); put(body, ll[i]); put(body, rt[i]); put(body, rl[i]);
            records++;
            i = j;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        put(out, theme);
        put(out, records);
        byte[] b = body.toByteArray();
        out.write(b, 0, b.length);
        return out.toByteArray();
     } finally { rally3d.core.Trace.out(378, $t); } }

    /** Segment j continues the run that started at i: identical attributes and an identical (start, end) terrain pair. */
    private boolean same(int i, int j, short[] e) { /*T*/ final long $t = rally3d.core.Trace.in(379); try {
        return yaw[i] == yaw[j] && curv[i] == curv[j] && cm[i] == cm[j] && lt[i] == lt[j] && ll[i] == ll[j] && rt[i] == rt[j] && rl[i] == rl[j]
                && e[i] == e[j] && e[Math.min(n - 1, i + 1)] == e[Math.min(n - 1, j + 1)];
     } finally { rally3d.core.Trace.out(379, $t); } }

    private static void put(ByteArrayOutputStream o, int v) { o.write(v & 255); o.write((v >> 8) & 255); }

    /** Parses a stock / exported track.bin into per-segment rows. Returns false when the data is not a valid track. */
    boolean loadTrackBin(byte[] d) { /*T*/ final long $t = rally3d.core.Trace.in(380); try {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d));
            int th = (short) rd(in);
            int cnt = rd(in);
            if (cnt <= 0 || cnt > 5000) return false;
            allocate(MAX_SEGS + 8);
            n = 0;
            for (int r = 0; r < cnt; r++) {
                int len = rd(in);
                short s4 = (short) rd(in), s5 = (short) rd(in), s6 = (short) rd(in);
                rd(in);   // s7 (end terrain) is derived again on export
                short s8 = (short) rd(in), s9 = (short) rd(in), s10 = (short) rd(in), s11 = (short) rd(in), s12 = (short) rd(in);
                for (int k = 0; k < len && n < MAX_SEGS; k++) {
                    yaw[n] = s4; curv[n] = s5; tgt[n] = s6; cm[n] = s8; lt[n] = s9; ll[n] = s10; rt[n] = s11; rl[n] = s12;
                    n++;
                }
            }
            theme = Math.max(0, Math.min(3, th));
            // stock data may use terrain codes outside the five editable ones; snap them
            for (int i = 0; i < n; i++) tgt[i] = (short) snapTerrain(tgt[i]);
            touch();
            return n > 0;
        } catch (IOException e) { rally3d.core.RLog.caught("MapData.loadTrackBin", e);
            return false;
        }
     } finally { rally3d.core.Trace.out(380, $t); } }

    private static int rd(DataInputStream in) throws IOException { int a = in.readUnsignedByte(), b = in.readUnsignedByte(); return a | (b << 8); }

    static int snapTerrain(int t) {
        for (int v : TERRAIN) if (v == t) return t;
        return 0;
    }

    /** A copy of a stock track as a starting point. */
    static MapData fromStock(int index) { /*T*/ final long $t = rally3d.core.Trace.in(381); try {
        MapData m = new MapData();
        InputStream in = null;
        try {
            in = MapData.class.getResourceAsStream("/tracks/" + trackFile(index));
            if (in == null) return m;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            if (!m.loadTrackBin(bo.toByteArray())) return new MapData();
            m.name = "Copy of " + Texts.trackNames[index];
            for (int i = 0; i < m.n; i++) { if (m.lt[i] == -1 && m.ll[i] == -1) { } }
            m.touch();
        } catch (IOException e) { rally3d.core.RLog.caught("MapData.fromStock", e);
            return new MapData();
        } finally { try { if (in != null) in.close(); } catch (IOException ignored) { rally3d.core.RLog.caught("MapData.fromStock.close", ignored); } }
        return m;
     } finally { rally3d.core.Trace.out(381, $t); } }

    static String trackFile(int i) { /*T*/ final long $t = rally3d.core.Trace.in(382); try {
        String[] f = {"track12.bin", "track06.bin", "track07.bin", "track09.bin", "track05.bin", "track02.bin", "track04.bin", "track01.bin", "track03.bin", "track10.bin", "track08.bin", "track11.bin"};
        return f[Math.max(0, Math.min(11, i))];
     } finally { rally3d.core.Trace.out(382, $t); } }

    // ------------------------------------------------------------------ zip pack

    static String slug(String s) { /*T*/ final long $t = rally3d.core.Trace.in(383); try {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 32; i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
            else if (c >= 'A' && c <= 'Z') sb.append((char) (c + 32));
            else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '-') sb.append('-');
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') sb.setLength(sb.length() - 1);
        return sb.length() == 0 ? "map" : sb.toString();
     } finally { rally3d.core.Trace.out(383, $t); } }

    private static String hex(int argb) { String h = Integer.toHexString(argb & 0xFFFFFF); while (h.length() < 6) h = "0" + h; return h; }

    /** Builds the add-on zip (type=map) the game loads: addon.properties, track.bin and the optional painted images. */
    byte[] toZip() throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(384); try {
        String bad = validate();
        if (bad != null) throw new IOException(bad);
        StringBuilder p = new StringBuilder();
        p.append("type=map\n");
        p.append("name=").append(clean(name)).append('\n');
        p.append("author=").append(clean(author)).append('\n');
        p.append("description=").append(clean(desc.length() > 0 ? desc : (n + " segments"))).append('\n');
        p.append("format=1\n");
        p.append("theme=").append(theme).append('\n');
        if (bgFrom >= 0) p.append("bgFrom=").append(bgFrom).append('\n');
        if (roadFrom >= 0) p.append("roadFrom=").append(roadFrom).append('\n');
        if (customColors) p.append("sky=").append(hex(sky)).append("\nground=").append(hex(ground)).append("\nhud=").append(hex(hud)).append('\n');
        p.append("checkpoints=").append(effCp(0)).append(',').append(effCp(1)).append(',').append(effCp(2)).append(',').append(effCp(3)).append('\n');
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bo);
        byte[][] sprites = new byte[CUSTOM_SPRITES][];
        for (int i = 0; i < CUSTOM_SPRITES; i++) {
            if (spritePx[i] == null) continue;
            PngCodec.Sheet sh = PngCodec.spriteSheet(spritePx[i], spriteW[i], spriteH[i]);
            entry(z, "sprite" + (i + 1) + ".png", sh.png);
            entry(z, "sprite" + (i + 1) + ".off", sh.off);
            p.append("sprite").append(i + 1).append("=1\n");
        }
        entry(z, "addon.properties", p.toString().getBytes("UTF-8"));
        entry(z, "track.bin", toTrackBin());
        if (bgPng != null) entry(z, "background.png", bgPng);
        if (roadPng != null) entry(z, "squares.png", roadPng);
        z.close();
        byte[] out = bo.toByteArray();
        if (out.length > AddonPack.MAX_ZIP) throw new IOException("Map too big for a pack (" + (out.length / 1024) + " KB, max " + (AddonPack.MAX_ZIP / 1024) + " KB) - use smaller images");
        return out;
     } finally { rally3d.core.Trace.out(384, $t); } }

    private static String clean(String s) { /*T*/ final long $t = rally3d.core.Trace.in(385); try {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); sb.append(c < 32 || c == 127 || c == '\\' ? ' ' : c); }
        return sb.toString().trim();
     } finally { rally3d.core.Trace.out(385, $t); } }

    private static void entry(ZipOutputStream z, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        z.putNextEntry(e);
        z.write(data);
        z.closeEntry();
    }

    /** Re-opens a map pack in the editor. Returns null when the pack is not an editable map. */
    static MapData fromPack(AddonPack p) { /*T*/ final long $t = rally3d.core.Trace.in(386); try {
        if (p == null || !p.mapOnly || p.mapBin == null) return null;
        MapData m = new MapData();
        if (!m.loadTrackBin(p.mapBin)) return null;
        m.name = p.name; m.author = p.author; m.desc = p.desc;
        Properties pr = p.mapProps == null ? new Properties() : p.mapProps;
        m.theme = Math.max(0, Math.min(3, ival(pr, "theme", m.theme)));
        m.bgFrom = ival(pr, "bgFrom", -1);
        m.roadFrom = ival(pr, "roadFrom", -1);
        if (pr.getProperty("sky") != null) {
            m.customColors = true;
            m.sky = 0xFF000000 | ival16(pr, "sky", 0); m.ground = 0xFF000000 | ival16(pr, "ground", 0); m.hud = 0xFF000000 | ival16(pr, "hud", 0xFFFFAA);
        }
        String c = pr.getProperty("checkpoints");
        if (c != null) {
            String[] t = c.split(",");
            for (int k = 0; k < 4 && k < t.length; k++) { try { int v = Integer.parseInt(t[k].trim()); m.cp[k] = v >= 0 && v < m.n ? v : -1; } catch (NumberFormatException e) { rally3d.core.RLog.caught("MapData.fromPack", e); } }
        }
        m.bgPng = p.mapBg;
        m.roadPng = p.mapRoad;
        for (int i = 0; i < CUSTOM_SPRITES; i++) {
            int[] wh = new int[2];
            int[] px = PngCodec.spriteFrame0(p.mapSpritePng[i], p.mapSpriteOff[i], wh);
            if (px != null) { m.spritePx[i] = px; m.spriteW[i] = wh[0]; m.spriteH[i] = wh[1]; }
        }
        m.touch();
        return m;
     } finally { rally3d.core.Trace.out(386, $t); } }

    private static int ival(Properties p, String k, int def) { /*T*/ final long $t = rally3d.core.Trace.in(387); try { try { return Integer.parseInt(p.getProperty(k, "").trim()); } catch (NumberFormatException e) { rally3d.core.RLog.caught("MapData.ival", e); return def; }  } finally { rally3d.core.Trace.out(387, $t); } }
    private static int ival16(Properties p, String k, int def) { /*T*/ final long $t = rally3d.core.Trace.in(388); try { try { return Integer.parseInt(p.getProperty(k, "").trim(), 16); } catch (NumberFormatException e) { rally3d.core.RLog.caught("MapData.ival16", e); return def; }  } finally { rally3d.core.Trace.out(388, $t); } }
}
