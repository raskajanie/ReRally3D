package com.nokia.mid.appl.rally3d;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import rally3d.core.RLog;
import rally3d.core.SafeFile;

/**
 * Car Builder data: a car (or a wheel set) is a list of parts (tapered boxes and prisms). Every part has its own 16x16 material.
 * build() turns the parts into the game's own formats: car.syn (mesh) + car.png (texture atlas, one 32x32 tile per part).
 * The game's add-on loader reads exactly these files, so an exported zip is a normal car / wheels add-on.
 * The editable source is stored in the same zip as car.rcm (ignored by the game, read by the builder).
 *
 * Atlas tile of a part = 4 quadrants of 16x16 (the material baked with a fixed light): 0 top, 1 left/right, 2 front/back, 3 bottom.
 * The engine has no lighting, so the baked shade is what makes a box look like a box.
 */
final class CarModel {
    static final int MAX_PARTS = 64, MAT = 16;
    static final String[] TEMPLATES = {"SPORT COUPE", "HATCHBACK", "PICKUP", "BLANK BLOCK", "WHEELS STANDARD", "WHEELS MONSTER", "WHEELS SLIM"};
    static final String[] PATTERNS = {"SOLID", "H-STRIPES", "V-STRIPES", "CHECKER", "GRADIENT", "CARBON", "NOISE", "FRAME", "WINDOW"};
    static final String[] AXIS_NAMES = {"X", "Y", "Z"};
    private static final float[] SHADE = {1.0f, 0.76f, 0.88f, 0.55f};

    /** One part. type 0 = box (top rectangle can be smaller / shifted = wedge, windscreen, taper), 1 = prism (cylinder / cone / n-gon). */
    static final class Part {
        String name = "Part";
        int type;
        float cx, cy = 1f, cz, sx = 1f, sy = 1f, sz = 1f;
        int topW = 100, topL = 100;
        float slideX, slideZ;
        int axis = 2, sides = 8, endR = 100;
        boolean mirror, hidden;
        int[] mat = new int[MAT * MAT];

        Part() { java.util.Arrays.fill(mat, 0xFFB0B0B0); }

        Part copy() { /*T*/ final long $t = rally3d.core.Trace.in(702); try {
            Part p = new Part();
            p.name = name; p.type = type; p.cx = cx; p.cy = cy; p.cz = cz; p.sx = sx; p.sy = sy; p.sz = sz;
            p.topW = topW; p.topL = topL; p.slideX = slideX; p.slideZ = slideZ; p.axis = axis; p.sides = sides; p.endR = endR;
            p.mirror = mirror; p.hidden = hidden; p.mat = mat.clone();
            return p;
         } finally { rally3d.core.Trace.out(702, $t); } }

        void clampAll() { /*T*/ final long $t = rally3d.core.Trace.in(703); try {
            cx = cl(cx, -12, 12); cy = cl(cy, -4, 12); cz = cl(cz, -12, 12);
            sx = cl(sx, 0.05f, 12); sy = cl(sy, 0.05f, 12); sz = cl(sz, 0.05f, 12);
            topW = (int) cl(topW, 2, 400); topL = (int) cl(topL, 2, 400);
            slideX = cl(slideX, -8, 8); slideZ = cl(slideZ, -8, 8);
            axis = (int) cl(axis, 0, 2); sides = (int) cl(sides, 3, 24); endR = (int) cl(endR, 2, 400);
         } finally { rally3d.core.Trace.out(703, $t); } }
    }

    /** Result of build(): files for the game plus data for the editor (picking, statistics). */
    static final class Built {
        byte[] syn, png;
        int atlas, tris, verts;
        float[] pos;          // xyz per vertex
        int[] idx;            // 3 per triangle
        int[] triPart;        // model part index per triangle
        int[] partFirst, partCount;   // per model part: first triangle / triangle count (count 0 = hidden)
    }

    final ArrayList<Part> parts = new ArrayList<Part>();
    boolean wheels;
    String name = "My car", author = "", desc = "";
    int base = 1, speed = 60;
    float accel = 1f, steer = 1f, steerMax = 1f, grip = 1f;
    boolean shade = true;
    int rev;

    static float cl(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }

    CarModel copy() { /*T*/ final long $t = rally3d.core.Trace.in(704); try {
        CarModel m = new CarModel();
        for (Part p : parts) m.parts.add(p.copy());
        m.wheels = wheels; m.name = name; m.author = author; m.desc = desc; m.base = base; m.speed = speed;
        m.accel = accel; m.steer = steer; m.steerMax = steerMax; m.grip = grip; m.shade = shade; m.rev = rev;
        return m;
     } finally { rally3d.core.Trace.out(704, $t); } }

    void restore(CarModel o) { /*T*/ final long $t = rally3d.core.Trace.in(705); try {
        parts.clear();
        for (Part p : o.parts) parts.add(p.copy());
        wheels = o.wheels; name = o.name; author = o.author; desc = o.desc; base = o.base; speed = o.speed;
        accel = o.accel; steer = o.steer; steerMax = o.steerMax; grip = o.grip; shade = o.shade;
        rev++;
     } finally { rally3d.core.Trace.out(705, $t); } }

    void touch() { rev++; }

    int visibleCount() { int n = 0; for (Part p : parts) if (!p.hidden) n++; return n; }

    // ------------------------------------------------------------------ materials

    static int[] solid(int argb) { int[] m = new int[MAT * MAT]; java.util.Arrays.fill(m, argb | 0xFF000000); return m; }

    static int mix(int a, int b, int t256) { /*T*/ final long $t = rally3d.core.Trace.in(706); try {
        int r = 0;
        for (int s = 0; s <= 16; s += 8) { int ca = (a >> s) & 255, cb = (b >> s) & 255; r |= ((ca * (256 - t256) + cb * t256) >> 8) << s; }
        return r | 0xFF000000;
     } finally { rally3d.core.Trace.out(706, $t); } }

    static int lighten(int c, int d) { /*T*/ final long $t = rally3d.core.Trace.in(707); try {
        int r = 0;
        for (int s = 0; s <= 16; s += 8) { int v = ((c >> s) & 255) + d; r |= (v < 0 ? 0 : v > 255 ? 255 : v) << s; }
        return r | 0xFF000000;
     } finally { rally3d.core.Trace.out(707, $t); } }

    /** Fills a 16x16 material with a pattern made of the two colours. */
    static void pattern(int[] m, int kind, int c1, int c2) { /*T*/ final long $t = rally3d.core.Trace.in(708); try {
        long seed = 12345;
        for (int y = 0; y < MAT; y++) for (int x = 0; x < MAT; x++) {
            int c;
            switch (kind) {
                case 1: c = (y / 2 & 1) == 0 ? c1 : c2; break;
                case 2: c = (x / 2 & 1) == 0 ? c1 : c2; break;
                case 3: c = ((x / 4 + y / 4) & 1) == 0 ? c1 : c2; break;
                case 4: c = mix(c1, c2, y * 256 / (MAT - 1)); break;
                case 5: c = ((x / 2 + y / 2) & 1) == 0 ? c1 : lighten(c1, 22); break;
                case 6: { seed = seed * 6364136223846793005L + 1442695040888963407L; int d = (int) ((seed >>> 33) % 41) - 20; c = lighten(c1, d); break; }
                case 7: c = (x < 2 || y < 2 || x >= MAT - 2 || y >= MAT - 2) ? c2 : c1; break;
                case 8: { int base = mix(c1, c2, y * 256 / (MAT - 1)); c = (x + y >= 9 && x + y <= 11) ? lighten(base, 60) : base; break; }
                default: c = c1; break;
            }
            m[y * MAT + x] = c | 0xFF000000;
        }
     } finally { rally3d.core.Trace.out(708, $t); } }

    // ------------------------------------------------------------------ geometry

    private static float[][] box(Part p) { /*T*/ final long $t = rally3d.core.Trace.in(709); try {
        float x0 = p.cx, y0 = p.cy - p.sy / 2f, y1 = p.cy + p.sy / 2f, z0 = p.cz;
        float bx = p.sx / 2f, bz = p.sz / 2f;
        float tx = Math.max(0.005f, p.sx * p.topW / 200f), tz = Math.max(0.005f, p.sz * p.topL / 200f);
        float ux = x0 + p.slideX, uz = z0 + p.slideZ;
        float[][] v = {
            {x0 - bx, y0, z0 - bz}, {x0 + bx, y0, z0 - bz}, {x0 + bx, y0, z0 + bz}, {x0 - bx, y0, z0 + bz},
            {ux - tx, y1, uz - tz}, {ux + tx, y1, uz - tz}, {ux + tx, y1, uz + tz}, {ux - tx, y1, uz + tz}};
        return v;
     } finally { rally3d.core.Trace.out(709, $t); } }

    /** Faces as vertex-index loops into the corner array (box) - order is fixed up later by the outward test. */
    private static final int[][] BOX_FACES = {{0, 1, 2, 3}, {4, 5, 6, 7}, {3, 2, 6, 7}, {0, 1, 5, 4}, {1, 2, 6, 5}, {0, 3, 7, 4}};

    private static ArrayList<float[][]> faces(Part p) { /*T*/ final long $t = rally3d.core.Trace.in(710); try {
        ArrayList<float[][]> out = new ArrayList<float[][]>();
        if (p.type == 0) {
            float[][] v = box(p);
            for (int[] f : BOX_FACES) { float[][] face = new float[f.length][]; for (int i = 0; i < f.length; i++) face[i] = v[f[i]].clone(); out.add(face); }
            return out;
        }
        int a = p.axis, n = Math.max(3, p.sides);
        int b = a == 0 ? 1 : 0, c = a == 2 ? 1 : 2;   // the two cross-section axes
        float[] size = {p.sx, p.sy, p.sz}, cen = {p.cx, p.cy, p.cz};
        float len = size[a], rb = size[b] / 2f, rc = size[c] / 2f, e = p.endR / 100f;
        float[][][] ring = new float[2][n][];
        for (int r = 0; r < 2; r++) for (int k = 0; k < n; k++) {
            double ang = (k + 0.5) * 2 * Math.PI / n;
            float[] q = new float[3];
            q[a] = cen[a] + (r == 0 ? -len / 2f : len / 2f);
            float sc = r == 0 ? 1f : e;
            q[b] = cen[b] + (float) Math.cos(ang) * rb * sc;
            q[c] = cen[c] + (float) Math.sin(ang) * rc * sc;
            ring[r][k] = q;
        }
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            out.add(new float[][]{ring[0][k].clone(), ring[0][k2].clone(), ring[1][k2].clone(), ring[1][k].clone()});
        }
        float[][] cap0 = new float[n][], cap1 = new float[n][];
        for (int k = 0; k < n; k++) { cap0[k] = ring[0][k].clone(); cap1[k] = ring[1][k].clone(); }
        out.add(cap0); out.add(cap1);
        return out;
     } finally { rally3d.core.Trace.out(710, $t); } }

    /** Builds mesh + atlas. Throws when there is nothing to build or a limit is exceeded. */
    Built build() throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(711); try {
        int vis = visibleCount();
        if (vis == 0) throw new IOException(rally3d.core.Lang.t("No visible parts"));
        Built b = new Built();
        b.atlas = vis <= 16 ? 128 : 256;
        int A = b.atlas, unitPx = A / 128, qUnits = 16 / unitPx, perRow = A / 32;
        int[] atlas = new int[A * A];
        java.util.Arrays.fill(atlas, 0xFF808080);
        ArrayList<float[]> vs = new ArrayList<float[]>();   // x y z u v
        ArrayList<Integer> idx = new ArrayList<Integer>();
        ArrayList<Integer> triPart = new ArrayList<Integer>();
        b.partFirst = new int[parts.size()]; b.partCount = new int[parts.size()];
        int tile = 0;
        for (int pi = 0; pi < parts.size(); pi++) {
            Part p = parts.get(pi);
            if (p.hidden) continue;
            int tx = tile % perRow, ty = tile / perRow;
            tile++;
            bake(atlas, A, tx * 32, ty * 32, p.mat, shade, unitPx);
            b.partFirst[pi] = idx.size() / 3;
            for (int pass = 0; pass < (p.mirror ? 2 : 1); pass++) {
                ArrayList<float[][]> fl = faces(p);
                float cxp = p.cx;
                if (pass == 1) { cxp = -cxp; for (float[][] f : fl) for (float[] q : f) q[0] = -q[0]; }
                float[] mn = {1e9f, 1e9f, 1e9f}, mx = {-1e9f, -1e9f, -1e9f};
                for (float[][] f : fl) for (float[] q : f) for (int k = 0; k < 3; k++) { if (q[k] < mn[k]) mn[k] = q[k]; if (q[k] > mx[k]) mx[k] = q[k]; }
                for (float[][] f : fl) emit(f, new float[]{cxp, p.cy, p.cz}, mn, mx, tx * 32 / unitPx, ty * 32 / unitPx, qUnits, vs, idx, triPart, pi);
            }
            b.partCount[pi] = idx.size() / 3 - b.partFirst[pi];
        }
        int n = vs.size(), ic = idx.size();
        if (n > 65535 || ic > 60000) throw new IOException(rally3d.core.Lang.t("Too many triangles (") + ic / 3 + rally3d.core.Lang.t(", max 20000)"));
        // winding: same convention as the stock cars (positive signed volume); flip everything if the model came out inside-out
        double vol = 0;
        for (int i = 0; i < ic; i += 3) {
            float[] p0 = vs.get(idx.get(i)), p1 = vs.get(idx.get(i + 1)), p2 = vs.get(idx.get(i + 2));
            vol += (p0[0] * (p1[1] * p2[2] - p1[2] * p2[1]) - p0[1] * (p1[0] * p2[2] - p1[2] * p2[0]) + p0[2] * (p1[0] * p2[1] - p1[1] * p2[0])) / 6.0;
        }
        boolean flip = vol < 0;
        b.verts = n; b.tris = ic / 3;
        b.pos = new float[n * 3];
        b.idx = new int[ic];
        b.triPart = new int[ic / 3];
        for (int i = 0; i < ic; i += 3) {
            int i1 = idx.get(i), i2 = idx.get(i + 1), i3 = idx.get(i + 2);
            if (flip) { int t = i2; i2 = i3; i3 = t; }
            b.idx[i] = i1; b.idx[i + 1] = i2; b.idx[i + 2] = i3;
            b.triPart[i / 3] = triPart.get(i / 3);
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        w32(bo, 24); w32(bo, 24 + 12 * n); w32(bo, 0); w32(bo, 0); w32(bo, 0); w16(bo, n); w16(bo, 0);
        for (int i = 0; i < n; i++) {
            float[] q = vs.get(i);
            for (int k = 0; k < 3; k++) {
                if (Math.abs(q[k]) > 31.9f) throw new IOException(rally3d.core.Lang.t("Part is too far from the centre (max 32 units)"));
                b.pos[i * 3 + k] = q[k];
                w32(bo, Math.round(q[k] * 16000f));
            }
        }
        for (int i = 0; i < n; i++) { float[] q = vs.get(i); w16(bo, Math.round(q[3])); w16(bo, Math.round(q[4])); }
        w32(bo, ic);
        for (int i = 0; i < ic; i++) w16(bo, b.idx[i]);
        b.syn = bo.toByteArray();
        AddonPack.checkSyn(b.syn, "mesh");
        b.png = PngCodec.encode(atlas, A, A);
        return b;
     } finally { rally3d.core.Trace.out(711, $t); } }

    private static void w16(ByteArrayOutputStream o, int v) { o.write(v & 255); o.write((v >> 8) & 255); }
    private static void w32(ByteArrayOutputStream o, int v) { o.write(v & 255); o.write((v >> 8) & 255); o.write((v >> 16) & 255); o.write((v >>> 24) & 255); }

    private static void emit(float[][] f, float[] center, float[] mn, float[] mx, int u0, int v0, int qUnits,
                             ArrayList<float[]> vs, ArrayList<Integer> idx, ArrayList<Integer> triPart, int partIndex) { /*T*/ final long $t = rally3d.core.Trace.in(712); try {
        int m = f.length;
        // Newell normal
        double nx = 0, ny = 0, nz = 0, gx = 0, gy = 0, gz = 0;
        for (int i = 0; i < m; i++) {
            float[] a = f[i], b = f[(i + 1) % m];
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
            gx += a[0]; gy += a[1]; gz += a[2];
        }
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6) return;   // collapsed face
        gx /= m; gy /= m; gz /= m;
        boolean rev = nx * (gx - center[0]) + ny * (gy - center[1]) + nz * (gz - center[2]) < 0;
        if (rev) { nx = -nx; ny = -ny; nz = -nz; }
        double ax = Math.abs(nx), ay = Math.abs(ny), az = Math.abs(nz);
        int q, ua, va;
        if (ay >= ax && ay >= az) { q = ny > 0 ? 0 : 3; ua = 0; va = 2; }
        else if (ax >= az) { q = 1; ua = 2; va = 1; }
        else { q = 2; ua = 0; va = 1; }
        // u runs along ua; v runs DOWN the picture: front (+z) / up (+y) is the top of the picture
        int qu = u0 + (q & 1) * qUnits, qv = v0 + (q >> 1) * qUnits;
        int inset = 1, span = qUnits - 2 * inset;
        int base = vs.size();
        for (int i = 0; i < m; i++) {
            float[] p = f[rev ? m - 1 - i : i];
            float eu = mx[ua] - mn[ua], ev = mx[va] - mn[va];
            float tu = eu < 1e-5f ? 0.5f : (p[ua] - mn[ua]) / eu;
            float tv = ev < 1e-5f ? 0.5f : (mx[va] - p[va]) / ev;
            int uu = qu + inset + Math.round(tu * span), vv = qv + inset + Math.round(tv * span);
            vs.add(new float[]{p[0], p[1], p[2], Math.min(127, uu) * 8f, Math.min(127, vv) * 8f});
        }
        for (int i = 1; i + 1 < m; i++) { idx.add(base); idx.add(base + i); idx.add(base + i + 1); triPart.add(partIndex); }
     } finally { rally3d.core.Trace.out(712, $t); } }

    /** Bakes the 16x16 material into the 4 quadrants of its tile (lit by the fixed shade); the picture fills the usable window of a quadrant. */
    private static void bake(int[] atlas, int A, int ox, int oy, int[] mat, boolean shade, int unitPx) { /*T*/ final long $t = rally3d.core.Trace.in(713); try {
        int w0 = unitPx, win = 16 - 2 * unitPx;
        for (int q = 0; q < 4; q++) {
            int qx = ox + (q & 1) * 16, qy = oy + (q >> 1) * 16;
            float sh = shade ? SHADE[q] : 1f;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int sx = Math.max(0, Math.min(win - 1, x - w0)) * 16 / win, sy = Math.max(0, Math.min(win - 1, y - w0)) * 16 / win;
                int c = mat[sy * MAT + sx];
                int r = Math.min(255, Math.round(((c >> 16) & 255) * sh)), g = Math.min(255, Math.round(((c >> 8) & 255) * sh)), bl = Math.min(255, Math.round((c & 255) * sh));
                atlas[(qy + y) * A + qx + x] = 0xFF000000 | r << 16 | g << 8 | bl;
            }
        }
     } finally { rally3d.core.Trace.out(713, $t); } }

    // ------------------------------------------------------------------ project file (car.rcm)

    private static String fmt(float v) { return String.format(Locale.ROOT, "%.3f", v); }
    private static String clean(String s) { /*T*/ final long $t = rally3d.core.Trace.in(714); try {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); sb.append(c < 32 || c == 127 || c == '|' || c == '\\' ? ' ' : c); }
        return sb.toString().trim();
     } finally { rally3d.core.Trace.out(714, $t); } }

    byte[] toRcm() throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(715); try {
        StringBuilder sb = new StringBuilder();
        sb.append("RCM1\n");
        sb.append("kind=").append(wheels ? "wheels" : "car").append('\n');
        sb.append("name=").append(clean(name)).append('\n');
        sb.append("author=").append(clean(author)).append('\n');
        sb.append("desc=").append(clean(desc)).append('\n');
        sb.append("base=").append(base).append("\nspeed=").append(speed).append('\n');
        sb.append("stats=").append(fmt(accel)).append(',').append(fmt(steer)).append(',').append(fmt(steerMax)).append(',').append(fmt(grip)).append('\n');
        sb.append("shade=").append(shade ? 1 : 0).append('\n');
        for (Part p : parts) {
            sb.append("p=").append(clean(p.name)).append('|').append(p.type).append('|').append(fmt(p.cx)).append('|').append(fmt(p.cy)).append('|').append(fmt(p.cz))
              .append('|').append(fmt(p.sx)).append('|').append(fmt(p.sy)).append('|').append(fmt(p.sz)).append('|').append(p.topW).append('|').append(p.topL)
              .append('|').append(fmt(p.slideX)).append('|').append(fmt(p.slideZ)).append('|').append(p.axis).append('|').append(p.sides).append('|').append(p.endR)
              .append('|').append((p.mirror ? 1 : 0) | (p.hidden ? 2 : 0)).append('\n');
            sb.append("m=");
            int run = 1;
            for (int i = 1; i <= p.mat.length; i++) {
                if (i < p.mat.length && p.mat[i] == p.mat[i - 1]) { run++; continue; }
                if (i > run) sb.append(',');
                sb.append(run).append(':').append(String.format(Locale.ROOT, "%06X", p.mat[i - 1] & 0xFFFFFF));
                run = 1;
            }
            sb.append('\n');
        }
        return sb.toString().getBytes("UTF-8");
     } finally { rally3d.core.Trace.out(715, $t); } }

    static CarModel fromRcm(byte[] data) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(716); try {
        if (data == null || data.length > 1024 * 1024) throw new IOException(rally3d.core.Lang.t("bad project file"));
        String[] lines = new String(data, "UTF-8").split("\n");
        if (lines.length == 0 || !lines[0].trim().equals("RCM1")) throw new IOException("not a Car Builder project");
        CarModel m = new CarModel();
        Part cur = null;
        try {
            for (int li = 1; li < lines.length; li++) {
                String l = lines[li].trim();
                int eq = l.indexOf('=');
                if (eq < 0) continue;
                String k = l.substring(0, eq), v = l.substring(eq + 1);
                if (k.equals("kind")) m.wheels = v.equals("wheels");
                else if (k.equals("name")) m.name = v;
                else if (k.equals("author")) m.author = v;
                else if (k.equals("desc")) m.desc = v;
                else if (k.equals("base")) m.base = (int) cl(Integer.parseInt(v), 1, 4);
                else if (k.equals("speed")) m.speed = (int) cl(Integer.parseInt(v), 20, 200);
                else if (k.equals("shade")) m.shade = !v.equals("0");
                else if (k.equals("stats")) {
                    String[] s = v.split(",");
                    m.accel = cl(Float.parseFloat(s[0]), 0.2f, 4f); m.steer = cl(Float.parseFloat(s[1]), 0.3f, 3f);
                    m.steerMax = cl(Float.parseFloat(s[2]), 0.3f, 3f); m.grip = cl(Float.parseFloat(s[3]), 0.3f, 3f);
                } else if (k.equals("p") && m.parts.size() < MAX_PARTS) {
                    String[] s = v.split("\\|", -1);
                    Part p = new Part();
                    p.name = s[0]; p.type = Integer.parseInt(s[1]) == 1 ? 1 : 0;
                    p.cx = Float.parseFloat(s[2]); p.cy = Float.parseFloat(s[3]); p.cz = Float.parseFloat(s[4]);
                    p.sx = Float.parseFloat(s[5]); p.sy = Float.parseFloat(s[6]); p.sz = Float.parseFloat(s[7]);
                    p.topW = Integer.parseInt(s[8]); p.topL = Integer.parseInt(s[9]);
                    p.slideX = Float.parseFloat(s[10]); p.slideZ = Float.parseFloat(s[11]);
                    p.axis = Integer.parseInt(s[12]); p.sides = Integer.parseInt(s[13]); p.endR = Integer.parseInt(s[14]);
                    int fl = Integer.parseInt(s[15]);
                    p.mirror = (fl & 1) != 0; p.hidden = (fl & 2) != 0;
                    p.clampAll();
                    m.parts.add(p);
                    cur = p;
                } else if (k.equals("m") && cur != null) {
                    int pos = 0;
                    for (String tok : v.split(",")) {
                        int c = tok.indexOf(':');
                        int run = Integer.parseInt(tok.substring(0, c));
                        int col = 0xFF000000 | Integer.parseInt(tok.substring(c + 1), 16);
                        for (int i = 0; i < run && pos < cur.mat.length; i++) cur.mat[pos++] = col;
                    }
                }
            }
        } catch (RuntimeException e) { RLog.caught("CarModel.fromRcm", e); throw new IOException("project file is damaged"); }
        if (m.parts.isEmpty()) throw new IOException(rally3d.core.Lang.t("project has no parts"));
        return m;
     } finally { rally3d.core.Trace.out(716, $t); } }

    static CarModel fromPack(AddonPack p) { /*T*/ final long $t = rally3d.core.Trace.in(717); try {
        if (p == null || p.rcm == null) return null;
        try { return fromRcm(p.rcm); }
        catch (IOException e) { RLog.caught("CarModel.fromPack", e); return null; }
     } finally { rally3d.core.Trace.out(717, $t); } }

    // ------------------------------------------------------------------ add-on zip

    /** The add-on zip the game loads: addon.properties + car.syn/car.png (or wheel.syn/wheel.png) + car.rcm (editable source). */
    byte[] toZip() throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(718); try {
        Built b = build();
        StringBuilder p = new StringBuilder();
        p.append("type=").append(wheels ? "wheels" : "car").append('\n');
        p.append("name=").append(clean(name.length() == 0 ? "My car" : name)).append('\n');
        p.append("author=").append(clean(author)).append('\n');
        p.append("description=").append(clean(desc.length() > 0 ? desc : "Car Builder: " + b.tris + " triangles")).append('\n');
        if (!wheels) {
            p.append("base=").append(base).append("\nspeed=").append(speed).append('\n');
            p.append("accel=").append(fmt(accel)).append("\nsteer=").append(fmt(steer)).append("\nsteerMax=").append(fmt(steerMax)).append("\ngrip=").append(fmt(grip)).append('\n');
        }
        p.append("builder=1\n");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bo);
        entry(z, "addon.properties", p.toString().getBytes("UTF-8"));
        entry(z, wheels ? "wheel.syn" : "car.syn", b.syn);
        entry(z, wheels ? "wheel.png" : "car.png", b.png);
        entry(z, "car.rcm", toRcm());
        z.close();
        byte[] out = bo.toByteArray();
        if (out.length > AddonPack.MAX_ZIP) throw new IOException(rally3d.core.Lang.t("Too big for an add-on (") + out.length / 1024 + rally3d.core.Lang.t(" KB, max ") + AddonPack.MAX_ZIP / 1024 + rally3d.core.Lang.t(" KB)"));
        AddonPack chk = AddonPack.parse(out, null);
        if (chk.error != null) throw new IOException(chk.error);
        return out;
     } finally { rally3d.core.Trace.out(718, $t); } }

    private static void entry(ZipOutputStream z, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0L);
        z.putNextEntry(e);
        z.write(data);
        z.closeEntry();
    }

    /** Writes the zip into add-ons/cars (or wheels), replacing the pack with the same name + author, and switches it on. */
    static AddonPack install(byte[] zip) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(719); try {
        AddonPack np = AddonPack.parse(zip, null);
        if (np.error != null) throw new IOException(np.error);
        File root = AddonManager.ensureDirs();
        if (root == null) throw new IOException(rally3d.core.Lang.t("no add-ons folder"));
        File dir = new File(root, np.wheelsOnly ? "wheels" : "cars");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException(rally3d.core.Lang.t("cannot create ") + dir);
        String stem = MapData.slug(np.name);
        File target = null;
        for (int k = 1; k < 100 && target == null; k++) {
            File f = new File(dir, stem + (k == 1 ? "" : "-" + k) + ".zip");
            if (!f.exists()) { target = f; break; }
            try {
                AddonPack old = AddonPack.parse(java.nio.file.Files.readAllBytes(f.toPath()), null);
                if (old.error != null || (old.name.equalsIgnoreCase(np.name) && old.author.equalsIgnoreCase(np.author))) target = f;
            } catch (IOException e) { RLog.caught("CarModel.install", e); target = f; }
        }
        if (target == null) throw new IOException(rally3d.core.Lang.t("too many add-ons with this name"));
        SafeFile.write(target, zip);
        AddonManager.rescan();
        String rel = (np.wheelsOnly ? "wheels/" : "cars/") + target.getName();
        for (AddonPack p : AddonManager.snapshot()) if (rel.equals(p.file)) { if (p.error == null) AddonManager.setEnabled(p, true); RLog.i("addon", "car builder installed '" + np.name + "' as " + rel + " (" + zip.length + " bytes)"); return p; }
        throw new IOException(rally3d.core.Lang.t("saved, but the pack was not found after rescan"));
     } finally { rally3d.core.Trace.out(719, $t); } }

    // ------------------------------------------------------------------ templates

    private Part add(String nm, int type, float cx, float cy, float cz, float sx, float sy, float sz, int color) { /*T*/ final long $t = rally3d.core.Trace.in(720); try {
        Part p = new Part();
        p.name = nm; p.type = type; p.cx = cx; p.cy = cy; p.cz = cz; p.sx = sx; p.sy = sy; p.sz = sz;
        p.mat = solid(color);
        parts.add(p);
        return p;
     } finally { rally3d.core.Trace.out(720, $t); } }

    static CarModel template(int i) { /*T*/ final long $t = rally3d.core.Trace.in(721); try {
        CarModel m = new CarModel();
        Part p;
        int red = 0xFFD42828, dark = 0xFF2C2C30, glass = 0xFF5A7FA8;
        switch (i) {
            case 0: {   // sport coupe
                m.name = "Sport coupe";
                m.add(rally3d.core.Lang.t("Body"), 0, 0, 0.95f, 0, 2.5f, 0.8f, 5.2f, red);
                p = m.add(rally3d.core.Lang.t("Cabin"), 0, 0, 1.65f, -0.35f, 2.0f, 0.8f, 2.5f, glass); p.topW = 78; p.topL = 62; p.slideZ = -0.1f; pattern(p.mat, 8, 0xFF86A9CF, 0xFF2F4F70);
                p = m.add(rally3d.core.Lang.t("Roof"), 0, 0, 2.08f, -0.45f, 1.56f, 0.08f, 1.55f, red);
                m.add(rally3d.core.Lang.t("Bumper front"), 0, 0, 0.7f, 2.7f, 2.5f, 0.4f, 0.25f, dark);
                m.add(rally3d.core.Lang.t("Bumper rear"), 0, 0, 0.7f, -2.7f, 2.5f, 0.4f, 0.25f, dark);
                p = m.add(rally3d.core.Lang.t("Headlight"), 0, 0.85f, 1.05f, 2.62f, 0.5f, 0.22f, 0.1f, 0xFFFFF0A0); p.mirror = true;
                p = m.add(rally3d.core.Lang.t("Taillight"), 0, 0.9f, 1.1f, -2.62f, 0.5f, 0.2f, 0.1f, 0xFFFF2020); p.mirror = true;
                m.add(rally3d.core.Lang.t("Spoiler"), 0, 0, 1.62f, -2.45f, 2.2f, 0.08f, 0.5f, dark);
                p = m.add(rally3d.core.Lang.t("Spoiler post"), 0, 0.8f, 1.45f, -2.4f, 0.1f, 0.3f, 0.15f, dark); p.mirror = true;
                break;
            }
            case 1: {   // hatchback
                m.name = "Hatchback";
                m.add(rally3d.core.Lang.t("Body"), 0, 0, 0.95f, 0, 2.5f, 0.8f, 4.6f, 0xFF2E7BC8);
                p = m.add(rally3d.core.Lang.t("Cabin"), 0, 0, 1.65f, -0.2f, 2.3f, 0.8f, 3.2f, glass); p.topW = 86; p.topL = 80; p.slideZ = -0.25f; pattern(p.mat, 8, 0xFF86A9CF, 0xFF2F4F70);
                m.add(rally3d.core.Lang.t("Roof"), 0, 0, 2.08f, -0.45f, 1.98f, 0.08f, 2.56f, 0xFF2E7BC8);
                m.add(rally3d.core.Lang.t("Bumper front"), 0, 0, 0.7f, 2.4f, 2.5f, 0.4f, 0.25f, dark);
                m.add(rally3d.core.Lang.t("Bumper rear"), 0, 0, 0.7f, -2.4f, 2.5f, 0.4f, 0.25f, dark);
                p = m.add(rally3d.core.Lang.t("Headlight"), 0, 0.85f, 1.05f, 2.32f, 0.5f, 0.22f, 0.1f, 0xFFFFF0A0); p.mirror = true;
                p = m.add(rally3d.core.Lang.t("Taillight"), 0, 0.9f, 1.1f, -2.32f, 0.4f, 0.3f, 0.1f, 0xFFFF2020); p.mirror = true;
                break;
            }
            case 2: {   // pickup
                m.name = "Pickup";
                m.add(rally3d.core.Lang.t("Chassis"), 0, 0, 0.85f, 0, 2.5f, 0.6f, 5.4f, 0xFFE0A020);
                p = m.add(rally3d.core.Lang.t("Cab"), 0, 0, 1.55f, 0.7f, 2.3f, 0.8f, 1.9f, 0xFFE0A020); p.topW = 84; p.topL = 70; p.slideZ = -0.1f;
                p = m.add(rally3d.core.Lang.t("Windows"), 0, 0, 1.58f, 0.72f, 2.34f, 0.5f, 1.6f, glass); p.topW = 80; p.topL = 78; pattern(p.mat, 8, 0xFF86A9CF, 0xFF2F4F70);
                p = m.add(rally3d.core.Lang.t("Bed wall"), 0, 1.2f, 1.3f, -1.5f, 0.1f, 0.5f, 2.3f, 0xFFB07818); p.mirror = true;
                m.add(rally3d.core.Lang.t("Tailgate"), 0, 0, 1.3f, -2.6f, 2.5f, 0.5f, 0.1f, 0xFFB07818);
                m.add(rally3d.core.Lang.t("Bumper front"), 0, 0, 0.7f, 2.75f, 2.5f, 0.4f, 0.25f, dark);
                p = m.add(rally3d.core.Lang.t("Headlight"), 0, 0.85f, 1.05f, 2.72f, 0.5f, 0.22f, 0.1f, 0xFFFFF0A0); p.mirror = true;
                break;
            }
            case 3:
                m.name = "Blank block";
                m.add(rally3d.core.Lang.t("Body"), 0, 0, 1.0f, 0, 2.4f, 1.2f, 5.0f, 0xFF909090);
                break;
            case 4: {   // wheels: tire + rim (centre of the stock wheel mesh: y 0.54, axle along X)
                m.wheels = true; m.name = "My wheels";
                p = m.add(rally3d.core.Lang.t("Tire"), 1, 0, 0.54f, 0, 0.4f, 1.0f, 1.0f, 0xFF202020); p.axis = 0; p.sides = 14;
                p = m.add(rally3d.core.Lang.t("Rim"), 1, 0, 0.54f, 0, 0.44f, 0.62f, 0.62f, 0xFFC8C8D0); p.axis = 0; p.sides = 10;
                break;
            }
            case 5: {
                m.wheels = true; m.name = "Monster wheels";
                p = m.add(rally3d.core.Lang.t("Tire"), 1, 0, 0.66f, 0, 0.6f, 1.24f, 1.24f, 0xFF1C1C1C); p.axis = 0; p.sides = 16;
                p = m.add(rally3d.core.Lang.t("Rim"), 1, 0, 0.66f, 0, 0.64f, 0.7f, 0.7f, 0xFFE0A020); p.axis = 0; p.sides = 8;
                break;
            }
            default: {
                m.wheels = true; m.name = "Slim wheels";
                p = m.add(rally3d.core.Lang.t("Tire"), 1, 0, 0.54f, 0, 0.25f, 1.0f, 1.0f, 0xFF282828); p.axis = 0; p.sides = 14;
                p = m.add(rally3d.core.Lang.t("Rim"), 1, 0, 0.54f, 0, 0.29f, 0.72f, 0.72f, 0xFFD8D8D8); p.axis = 0; p.sides = 6;
                break;
            }
        }
        return m;
     } finally { rally3d.core.Trace.out(721, $t); } }
}
