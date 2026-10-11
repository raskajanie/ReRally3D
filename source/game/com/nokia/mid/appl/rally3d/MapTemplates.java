package com.nokia.mid.appl.rally3d;

/**
 * Ready-made starting points for the map editor: 7 tracks (MapData), 6 backgrounds (128x128, seamless left-right)
 * and 5 road surfaces (tiles 0 and 5 of the 256x32 road atlas, seamless top-bottom).
 * Road atlas: 8 tiles of 32x32. Tile 0 = plain road (centre lane without marking), tile 5 = road with the centre line.
 * The other tiles are the roadside textures (grass, rock, wall...) and are left alone by the road templates.
 */
final class MapTemplates {
    static final String[] TRACK_NAMES = {"SPRINT", "MOUNTAIN PASS", "CANYON", "NIGHT CITY", "FOREST", "COAST", "ZIGZAG"};
    static final String[] TRACK_TEXT = {
        "Fast and flowing, a few hills", "Climb with hairpins, then descend", "Walls on both sides, S-bends",
        "90 degree corners, guard walls, lamps", "Dense trees, rolling hills", "Sweeping curves above a drop-off", "Eight switchbacks in a row"};
    static final String[] BG_NAMES = {"SKY", "SUNSET", "STARS", "MOUNTAINS", "CITY", "DUNES"};
    static final String[] ROAD_NAMES = {"ASPHALT", "GRAVEL", "DIRT", "SNOW", "COBBLES"};
    static final int HORIZON = 58;

    private MapTemplates() { }

    // ------------------------------------------------------------------ tracks

    private static final class B {
        final MapData m = new MapData();
        B() { m.n = 0; }
        int pos() { return m.n; }
        void straight(int len) { m.insert(m.n, len); }
        void arc(int deg, int len) { m.insertArc(m.n, deg, len); }
        /** Height profile through keypoints {fraction of the road * 1000, height}, smooth between them. */
        void heights(int[][] kp) { /*T*/ final long $t = rally3d.core.Trace.in(527); try {
            int c = m.ctrlCount();
            float[] ctrl = new float[c];
            for (int k = 0; k < c; k++) {
                double f = Math.min(1.0, k * MapData.CTRL_STEP / (double) Math.max(1, m.n)) * 1000.0;
                double h = kp[kp.length - 1][1];
                for (int i = 0; i + 1 < kp.length; i++) {
                    if (f >= kp[i][0] && f <= kp[i + 1][0]) {
                        double u = (f - kp[i][0]) / Math.max(1.0, kp[i + 1][0] - kp[i][0]);
                        u = (1 - Math.cos(Math.PI * u)) / 2;
                        h = kp[i][1] + (kp[i + 1][1] - kp[i][1]) * u;
                        break;
                    }
                }
                ctrl[k] = (float) h;
            }
            ctrl[0] = 0;
            m.fitHeights(ctrl);
         } finally { rally3d.core.Trace.out(527, $t); } }
    }

    private static void seq(B b, int[] arcs, int lenPerArc, int gap) {
        for (int i = 0; i < arcs.length; i += 2) { b.arc(arcs[i], arcs[i + 1]); if (gap > 0) b.straight(gap); }
    }

    static MapData track(int k) { /*T*/ final long $t = rally3d.core.Trace.in(528); try {
        B b = new B();
        MapData m = b.m;
        int theme = 2;
        switch (k) {
            case 0: {   // SPRINT
                b.straight(14); b.arc(45, 8); b.straight(12); b.arc(-90, 12); b.straight(10); b.arc(45, 8); b.arc(-45, 8); b.straight(18);
                b.arc(90, 12); b.straight(8); b.arc(-90, 12); b.straight(14); b.arc(45, 8); b.arc(-45, 8); b.straight(18); b.arc(-60, 10);
                b.straight(10); b.arc(60, 10); b.straight(22);
                b.heights(new int[][]{{0, 0}, {100, 0}, {250, 22}, {400, -8}, {550, 30}, {720, 5}, {880, 20}, {1000, 0}});
                m.paintProps(10, m.n - 8, 2, -1, 2, 3);
                break;
            }
            case 1: {   // MOUNTAIN PASS
                theme = 1;
                b.straight(12); b.arc(90, 12); b.straight(8); b.arc(-180, 14); b.straight(6); b.arc(180, 14); b.straight(8); b.arc(-90, 10);
                b.straight(10); b.arc(-180, 14); b.straight(6); b.arc(120, 12); b.straight(10); b.arc(-120, 12); b.straight(14); b.arc(60, 8); b.straight(16);
                b.heights(new int[][]{{0, 0}, {60, 0}, {450, 170}, {550, 205}, {700, 140}, {880, 60}, {1000, 0}});
                int n = m.n;
                m.setTerrain(0, n * 40 / 100, 3);
                m.setTerrain(n * 40 / 100 + 1, n * 62 / 100, 7);
                m.setTerrain(n * 62 / 100 + 1, n * 80 / 100, 3);
                m.setTerrain(n * 80 / 100 + 1, n - 1, 0);
                m.paintProps(14, n - 10, 1, 2, 3, 5);
                break;
            }
            case 2: {   // CANYON
                theme = 0;
                b.straight(12);
                int[] a = {70, 11, -70, 11, 90, 13, -50, 9, -60, 10, 100, 14, -90, 13, 40, 8, -40, 8, 80, 12, -80, 12};
                seq(b, a, 0, 4);
                b.straight(20);
                b.heights(new int[][]{{0, 0}, {80, 0}, {250, 20}, {420, -15}, {600, 22}, {780, -10}, {920, 6}, {1000, 0}});
                m.setTerrain(0, 12, 3);
                m.setTerrain(13, m.n - 1, 6);
                m.paintProps(16, m.n - 10, 0, 0, 3, 6);
                break;
            }
            case 3: {   // NIGHT CITY
                theme = 3;
                b.straight(14); b.arc(90, 7); b.straight(10); b.arc(-90, 7); b.straight(12); b.arc(-90, 7); b.straight(9); b.arc(90, 7);
                b.straight(14); b.arc(90, 7); b.straight(10); b.arc(90, 7); b.straight(8); b.arc(-90, 7); b.straight(14); b.arc(-90, 7);
                b.straight(10); b.arc(90, 7); b.straight(18);
                b.heights(new int[][]{{0, 0}, {400, 0}, {470, 26}, {530, 26}, {600, 0}, {1000, 0}});
                m.setTerrain(8, m.n - 1, 11);
                m.paintProps(8, m.n - 8, 2, 0, 0, 3);
                break;
            }
            case 4: {   // FOREST
                b.straight(14);
                int[] a = {35, 12, -50, 14, 45, 12, -30, 10, 60, 14, -60, 14, 25, 9, -45, 12};
                seq(b, a, 0, 6);
                b.straight(18);
                b.heights(new int[][]{{0, 0}, {120, 0}, {280, 38}, {450, 12}, {620, 45}, {800, 10}, {920, 0}, {1000, 0}});
                m.paintProps(8, m.n - 8, 2, -1, 1, 1);
                break;
            }
            case 5: {   // COAST
                b.straight(14);
                int[] a = {40, 16, -40, 16, -55, 20, 55, 20, 30, 14, -70, 22, 25, 10};
                seq(b, a, 0, 8);
                b.straight(16);
                b.heights(new int[][]{{0, 0}, {100, 0}, {200, 30}, {350, -10}, {500, 40}, {650, 5}, {800, 30}, {920, 0}, {1000, 0}});
                m.setTerrain(14, m.n - 14, 7);
                m.paintProps(14, m.n - 14, 0, 2, 3, 4);
                break;
            }
            default: {   // ZIGZAG
                theme = 0;
                b.straight(12);
                for (int i = 0; i < 8; i++) { b.arc(i % 2 == 0 ? 75 : -75, 8); b.straight(3); }
                b.straight(20);
                b.heights(new int[][]{{0, 0}, {120, 0}, {500, 60}, {800, 20}, {1000, 0}});
                m.setTerrain(0, m.n - 1, 3);
                break;
            }
        }
        k = Math.max(0, Math.min(TRACK_NAMES.length - 1, k));
        m.theme = theme;
        m.name = TRACK_NAMES[k].charAt(0) + TRACK_NAMES[k].substring(1).toLowerCase();
        m.desc = TRACK_TEXT[k];
        m.autoCheckpoints();
        m.touch();
        return m;
     } finally { rally3d.core.Trace.out(528, $t); } }

    // ------------------------------------------------------------------ noise

    private static int hash(int x, int y, int seed) { /*T*/ final long $t = rally3d.core.Trace.in(529); try {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
     } finally { rally3d.core.Trace.out(529, $t); } }

    /** Smooth value noise 0..1; the lattice wraps every wrapW / wrapH pixels (so the result tiles seamlessly). */
    private static double noise(double x, double y, int cell, int wrapW, int wrapH, int seed) { /*T*/ final long $t = rally3d.core.Trace.in(530); try {
        int cw = Math.max(1, wrapW / cell), ch = Math.max(1, wrapH / cell);
        double fx = x / cell, fy = y / cell;
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        double u = fx - ix, v = fy - iy;
        u = u * u * (3 - 2 * u); v = v * v * (3 - 2 * v);
        int x0 = Math.floorMod(ix, cw), x1 = Math.floorMod(ix + 1, cw), y0 = Math.floorMod(iy, ch), y1 = Math.floorMod(iy + 1, ch);
        double a = hash(x0, y0, seed) / 2147483647.0, b = hash(x1, y0, seed) / 2147483647.0, c = hash(x0, y1, seed) / 2147483647.0, d = hash(x1, y1, seed) / 2147483647.0;
        return (a + (b - a) * u) * (1 - v) + (c + (d - c) * u) * v;
     } finally { rally3d.core.Trace.out(530, $t); } }

    private static int mix(int c0, int c1, double t) { /*T*/ final long $t = rally3d.core.Trace.in(531); try {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((c0 >> 16) & 255) * (1 - t) + ((c1 >> 16) & 255) * t);
        int g = (int) Math.round(((c0 >> 8) & 255) * (1 - t) + ((c1 >> 8) & 255) * t);
        int b = (int) Math.round((c0 & 255) * (1 - t) + (c1 & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
     } finally { rally3d.core.Trace.out(531, $t); } }

    private static int shade(int c, double f) { /*T*/ final long $t = rally3d.core.Trace.in(532); try {
        int r = (int) Math.max(0, Math.min(255, ((c >> 16) & 255) * f)), g = (int) Math.max(0, Math.min(255, ((c >> 8) & 255) * f)), b = (int) Math.max(0, Math.min(255, (c & 255) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
     } finally { rally3d.core.Trace.out(532, $t); } }

    // ------------------------------------------------------------------ backgrounds (128 x 128, tiles horizontally)

    /** Periodic ridge height 0..1 built from sines with whole numbers of periods over the width, so x = 0 and x = w continue each other. */
    private static double ridge(int x, int w, int[] freq, double[] amp, double[] phase) { /*T*/ final long $t = rally3d.core.Trace.in(533); try {
        double s = 0, tot = 0;
        for (int i = 0; i < freq.length; i++) { s += amp[i] * Math.sin(2 * Math.PI * freq[i] * x / w + phase[i]); tot += amp[i]; }
        return (s / tot + 1) / 2;
     } finally { rally3d.core.Trace.out(533, $t); } }

    static int[] background(int kind) { /*T*/ final long $t = rally3d.core.Trace.in(534); try {
        final int w = 128, h = 128, hz = HORIZON;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int c;
            double t = y < hz ? y / (double) hz : 0;
            switch (kind) {
                case 0: {   // SKY
                    if (y < hz) c = mix(0x3C64B4, 0xCFE4FF, Math.pow(t, 1.4));
                    else c = mix(0x6E8C54, 0x4A6A38, (y - hz) / (double) (h - hz));
                    double cloud = noise(x, y * 1.6, 16, w, 4096, 3) * 0.6 + noise(x, y * 1.6, 8, w, 4096, 4) * 0.4;
                    if (y < hz - 6 && cloud > 0.62) c = mix(c, 0xFFFFFF, (cloud - 0.62) * 2.2 * (1 - t * 0.5));
                    if (y >= hz) c = shade(c, 0.9 + noise(x, y, 4, w, 4096, 9) * 0.2);
                    break;
                }
                case 1: {   // SUNSET
                    if (y < hz) c = t < 0.55 ? mix(0x1A2050, 0x7A3C78, t / 0.55) : mix(0x7A3C78, 0xFF9030, (t - 0.55) / 0.45);
                    else c = mix(0x2A1A28, 0x120C14, (y - hz) / (double) (h - hz));
                    double dune = 2 + 3 * Math.sin(2 * Math.PI * 3 * x / w) + 2 * Math.sin(2 * Math.PI * 5 * x / w + 1);
                    if (y >= hz && y < hz + 4 + dune) c = 0xFF1A1018;
                    break;
                }
                case 2: {   // STARS
                    if (y < hz) c = mix(0x04061A, 0x1C2858, Math.pow(t, 2));
                    else c = mix(0x0A0E1A, 0x04060C, (y - hz) / (double) (h - hz));
                    if (y < hz - 2) {
                        int hh = hash(x, y, 77);
                        if (hh % 97 == 0) c = mix(c, 0xFFFFFF, 0.55 + (hh >> 8) % 45 / 100.0);
                        else if (hh % 331 == 1) c = 0xFFFFF0C0;
                    }
                    break;
                }
                case 3: {   // MOUNTAINS
                    if (y < hz + 6) c = mix(0x5078B8, 0xD2E6FF, Math.pow(Math.min(1, y / (double) hz), 1.5));
                    else c = mix(0x587A40, 0x3E5A2C, (y - hz) / (double) (h - hz));
                    int far = hz - 4 - (int) (22 * ridge(x, w, new int[]{2, 5, 9}, new double[]{1, 0.6, 0.25}, new double[]{0.4, 1.7, 3.1}));
                    int near = hz + 2 - (int) (14 * ridge(x, w, new int[]{3, 7, 12}, new double[]{1, 0.5, 0.2}, new double[]{2.2, 0.5, 4.0}));
                    if (y >= far && y < hz + 8) { c = mix(0x6C80A8, 0x8CA0C0, (y - far) / 22.0); if (y < far + 5) c = mix(0xFFFFFF, c, (y - far) / 5.0 * 0.7); }
                    if (y >= near && y < hz + 12) { c = mix(0x44603C, 0x2E4A2C, (y - near) / 14.0); if (y < near + 3) c = mix(0xE8F0F8, c, (y - near) / 3.0 * 0.6); }
                    break;
                }
                case 4: {   // CITY
                    if (y < hz) c = t < 0.6 ? mix(0x101838, 0x5A3C70, t / 0.6) : mix(0x5A3C70, 0xE08040, (t - 0.6) / 0.4);
                    else c = mix(0x14141C, 0x08080C, (y - hz) / (double) (h - hz));
                    int bx = 0, bi = 0;
                    // buildings: deterministic widths; the last one is cut by the border (a building edge is as good as any other seam)
                    int cx = x, found = -1, bw0 = 0, top0 = 0;
                    while (bx < w) {
                        int hh = hash(bi, 5, 31);
                        int bw = 7 + hh % 11, top = hz - 8 - (hh >> 6) % 34;
                        if (cx >= bx && cx < bx + bw) { found = bi; bw0 = bx; top0 = top; break; }
                        bx += bw; bi++;
                    }
                    if (found >= 0 && y >= top0 && y < hz + 6) {
                        c = 0xFF0E0E1A;
                        int lx = x - bw0;
                        if (lx % 3 == 1 && (y - top0) % 4 == 2 && y < hz - 2 && hash(x, y, 5) % 5 > 0) c = hash(x / 3, y / 4, 8) % 3 == 0 ? 0xFF706040 : 0xFFF0C050;
                    }
                    break;
                }
                default: {   // DUNES
                    if (y < hz) c = mix(0x7098D8, 0xF4E8D0, Math.pow(t, 1.6));
                    else c = 0xFFD8B070;
                    double d1 = hz + 6 - 10 * ridge(x, w, new int[]{2, 3, 6}, new double[]{1, 0.6, 0.2}, new double[]{0.5, 2.0, 4.1});
                    double d2 = hz + 18 - 14 * ridge(x, w, new int[]{3, 5, 8}, new double[]{1, 0.5, 0.25}, new double[]{3.3, 1.2, 0.2});
                    if (y >= d1) c = mix(0xE8C890, 0xC89C58, Math.min(1, (y - d1) / 16.0));
                    if (y >= d2) c = mix(0xF0D8A0, 0xB88A48, Math.min(1, (y - d2) / 30.0));
                    if (y >= hz) c = shade(c, 0.95 + noise(x, y, 3, w, 4096, 12) * 0.1);
                    break;
                }
            }
            px[y * w + x] = c | 0xFF000000;
        }
        return px;
     } finally { rally3d.core.Trace.out(534, $t); } }

    // ------------------------------------------------------------------ road surfaces (tiles 0 and 5 of a 256 x 32 atlas)

    /** Paints road surface {@code kind} into tile 0 (plain) and tile 5 (centre line) of the atlas, leaving the other tiles alone. */
    static void road(int kind, int[] atlas, int aw) { /*T*/ final long $t = rally3d.core.Trace.in(535); try {
        for (int tile = 0; tile < 2; tile++) {
            int ox = tile == 0 ? 0 : 5 * 32;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int c = surface(kind, x, y);
                if (x == 0 || x == 31) c = shade(c, 0.82);
                atlas[y * aw + ox + x] = c;
            }
        }
        centreLine(atlas, aw, kind == 3 ? 0xFF5A6C88 : kind == 2 ? 0xFFE0D2A8 : kind == 4 ? 0xFFE8D060 : 0xFFF2F2EC);
     } finally { rally3d.core.Trace.out(535, $t); } }

    /** Draws the dashed centre line (2 px wide, 12 px on / 4 px off, two dashes per tile) into tile 5 of the atlas. */
    static void centreLine(int[] atlas, int aw, int line) { /*T*/ final long $t = rally3d.core.Trace.in(536); try {
        int ox = 5 * 32;
        for (int y = 0; y < 32; y++) {
            int yy = y % 16;
            if (yy < 2 || yy > 11) continue;
            for (int x = 15; x <= 16; x++) atlas[y * aw + ox + x] = mix(atlas[y * aw + ox + x], line, 0.92);
        }
     } finally { rally3d.core.Trace.out(536, $t); } }

    /** One road pixel; y wraps every 32 px. */
    private static int surface(int kind, int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(537); try {
        switch (kind) {
            case 0: {   // ASPHALT
                double n = noise(x, y, 4, 32, 32, 21) * 0.5 + noise(x, y, 2, 32, 32, 22) * 0.5;
                int c = shade(0xFF58585E, 0.84 + n * 0.32);
                if (hash(x, y, 23) % 29 == 0) c = shade(c, 1.45);
                if (hash(x, y, 24) % 41 == 0) c = shade(c, 0.6);
                if (x >= 7 && x <= 10 || x >= 21 && x <= 24) c = shade(c, 0.93);   // faint tyre polish
                return c;
            }
            case 1: {   // GRAVEL
                int c = shade(0xFF8C8678, 0.82 + noise(x, y, 4, 32, 32, 31) * 0.3);
                int h = hash(x / 2, y / 2, 32);
                if (h % 5 == 0) c = shade(c, 1.22); else if (h % 7 == 1) c = shade(c, 0.7); else if (h % 11 == 2) c = mix(c, 0xFFB09870, 0.5);
                if (hash(x, y, 33) % 13 == 0) c = shade(c, 0.78);
                return c;
            }
            case 2: {   // DIRT
                int c = shade(0xFF7C5A3A, 0.8 + noise(x, y, 4, 32, 32, 41) * 0.4);
                double rut = Math.exp(-Math.pow((x - 9.5) / 2.2, 2)) + Math.exp(-Math.pow((x - 22.5) / 2.2, 2));
                c = shade(c, 1 - rut * 0.28);
                if (hash(x, y, 42) % 17 == 0) c = shade(c, 0.7);
                if (hash(x, y, 43) % 23 == 0) c = shade(c, 1.25);
                return c;
            }
            case 3: {   // SNOW
                int c = mix(0xFFE4ECF4, 0xFFC4D2E6, noise(x, y, 4, 32, 32, 51));
                double trk = Math.exp(-Math.pow((x - 9.5) / 2.0, 2)) + Math.exp(-Math.pow((x - 22.5) / 2.0, 2));
                c = mix(c, 0xFF9AAABE, trk * 0.45);
                if (hash(x, y, 52) % 19 == 0) c = 0xFFFFFFFF;
                return c;
            }
            default: {   // COBBLES: 4 rows of 8 px, every other row shifted by half a stone
                int row = y / 8, ry = y % 8, off = (row & 1) * 4, sx = (x + off) % 8;
                int stone = hash((x + off) / 8 + (row & 1) * 7, row, 61);
                int c = shade(0xFF8A847C, 0.78 + (stone % 100) / 100.0 * 0.4);
                if (ry == 0 || sx == 0) c = shade(0xFF4A4640, 0.9 + (hash(x, y, 62) % 10) / 50.0);
                else if (ry == 1 || sx == 1) c = shade(c, 1.12);
                else if (ry == 7 || sx == 7) c = shade(c, 0.88);
                else c = shade(c, 0.94 + noise(x, y, 2, 32, 32, 63) * 0.12);
                return c;
            }
        }
     } finally { rally3d.core.Trace.out(537, $t); } }
}
