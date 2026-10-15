package com.nokia.mid.appl.rally3d;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parsed add-on zip. Everything inside is untrusted (it can arrive from another player): size limits, strict .syn validation.
 * Zip layout (files may also sit in one top-level folder):
 *   addon.properties   name, type (car|wheels), base (1-4 stock model), speed, accel, steer, steerMax, grip, author
 *   car.syn            optional own 3D model (same format as res/cars/N.syn); without it the stock model "base" is used
 *   car.png            optional car skin (width/height power of two, max 1024; UV like the stock 128x128 skins)
 *   wheel.png          optional wheel skin;  wheel.syn  optional wheel model
 *   car.rcm            optional Car Builder project (editable source of car.syn / car.png, ignored by the game)
 *   type=map           placeholder for custom maps (name, author, description); listed in the Maps tab, game support comes later
 */
final class AddonPack {
    /** No practical size limit for add-ons; this is only a safety ceiling so a broken / hostile multiplayer offer cannot exhaust memory. */
    static final int MAX_ZIP = 64 * 1024 * 1024;
    static final int MAX_ENTRY = 32 * 1024 * 1024;
    /** All files of one zip together, uncompressed (a 0.5 MB zip can unpack to hundreds of MB). */
    static final int MAX_UNPACKED = 48 * 1024 * 1024;
    static final int MAX_SYN_INDICES = 60000;

    String file;           // zip path relative to the add-ons root, e.g. "cars/x.zip" (null for packs received over the network)
    String name = "?";
    String author = "";
    boolean wheelsOnly;
    /** type=map: placeholder for custom maps (listed in the add-ons menu, not used by the game yet). */
    boolean mapOnly;
    // type=map payload (see MapData.toZip): track.bin, optional painted background / road texture, custom sprites 1..4 (sheet + .off)
    byte[] mapBin, mapBg, mapRoad;
    final byte[][] mapSpritePng = new byte[4][], mapSpriteOff = new byte[4][];
    Properties mapProps;
    String desc = "";      // optional description line shown in the add-ons menu
    int base = 0;          // stock model index 0..3
    int speed = -1;
    float accel = 1.0f, steer = 1.0f, steerMax = 1.0f, grip = 1.0f;
    byte[] zip;            // original archive (sent to other players)
    byte[] sha;            // MD5 of zip
    byte[] syn, skin, wheelPng, wheelSyn;
    /** car.rcm: editable Car Builder project (the game ignores it, the builder reopens the car from it). */
    byte[] rcm;
    String error;          // non-null: unusable, shown in the menu
    boolean enabled;
    boolean wasEnabled;    // enabled flag as of the previous rescan (change detection)
    long mtime;            // last-modified of the zip file when parsed (rescan re-parses a replaced file)

    /**
     * Stable identity of a car add-on for ghost files: derived from name + author (like map ids), so the ghost survives
     * re-exporting / editing the car and is the same on every machine. Always >= 1000 (0..3 are the stock cars).
     */
    int carKey() { /*T*/ final long $t = rally3d.core.Trace.in(722); try {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        try { c.update((name.trim().toLowerCase(Locale.ROOT) + "|" + author.trim().toLowerCase(Locale.ROOT)).getBytes("UTF-8")); }
        catch (java.io.UnsupportedEncodingException e) { rally3d.core.RLog.caught("AddonPack.carKey", e); }
        return 1000 + (int) (c.getValue() & 0x3FFFFFFFL);
     } finally { rally3d.core.Trace.out(722, $t); } }

    String shaHex() { /*T*/ final long $t = rally3d.core.Trace.in(266); try {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sha.length; i++) sb.append(Character.forDigit((sha[i] >> 4) & 15, 16)).append(Character.forDigit(sha[i] & 15, 16));
        return sb.toString();
     } finally { rally3d.core.Trace.out(266, $t); } }

    static byte[] md5(byte[] data) { /*T*/ final long $t = rally3d.core.Trace.in(267); try {
        try { return MessageDigest.getInstance("MD5").digest(data); }
        catch (Exception e) { rally3d.core.RLog.caught("AddonPack.md5", e); return new byte[16]; }
     } finally { rally3d.core.Trace.out(267, $t); } }

    static AddonPack parse(byte[] zipBytes, String fileName) {
        AddonPack p = new AddonPack();
        p.file = fileName;
        p.zip = zipBytes;
        p.sha = md5(zipBytes);
        if (fileName != null) p.name = fileName.substring(fileName.lastIndexOf('/') + 1).replaceAll("(?i)\\.zip$", "");
        try {
            if (zipBytes.length > MAX_ZIP) throw new IOException(rally3d.core.Lang.t("zip too big (max ") + (MAX_ZIP / 1024) + rally3d.core.Lang.t(" KB)"));
            Properties props = new Properties();
            boolean haveProps = false;
            ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes));
            try {
                ZipEntry e;
                int entries = 0;
                int[] budget = {MAX_UNPACKED};
                while ((e = zin.getNextEntry()) != null) {
                    if (++entries > 64) throw new IOException(rally3d.core.Lang.t("too many files in zip"));
                    if (e.isDirectory()) continue;
                    String n = e.getName().replace('\\', '/');
                    n = n.substring(n.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                    boolean mapFile = n.equals("track.bin") || n.equals("background.png") || n.equals("squares.png") || n.matches("sprite[1-4]\\.(png|off)");
                    if (!(mapFile || n.equals("addon.properties") || n.equals("car.syn") || n.equals("car.png") || n.equals("wheel.png") || n.equals("wheel.syn") || n.equals("car.rcm"))) continue;
                    byte[] data = readLimited(zin, budget);
                    if (mapFile) {
                        if (n.equals("track.bin")) p.mapBin = data;
                        else if (n.equals("background.png")) p.mapBg = data;
                        else if (n.equals("squares.png")) p.mapRoad = data;
                        else { int si = n.charAt(6) - '1'; if (n.endsWith(".png")) p.mapSpritePng[si] = data; else p.mapSpriteOff[si] = data; }
                        continue;
                    }
                    if (n.equals("addon.properties")) { props.load(new java.io.InputStreamReader(new ByteArrayInputStream(data), "UTF-8")); haveProps = true; }
                    else if (n.equals("car.syn")) p.syn = data;
                    else if (n.equals("car.png")) p.skin = data;
                    else if (n.equals("car.rcm")) p.rcm = data;
                    else if (n.equals("wheel.png")) p.wheelPng = data;
                    else p.wheelSyn = data;
                }
            } finally { zin.close(); }
            if (haveProps) {
                String nm = props.getProperty("name");
                if (nm != null && nm.trim().length() > 0) p.name = clip(nm.trim(), 24);
                String au = props.getProperty("author");
                if (au != null) p.author = clip(au.trim(), 24);
                String type = props.getProperty("type", "car").trim();
                p.wheelsOnly = "wheels".equalsIgnoreCase(type);
                p.mapOnly = "map".equalsIgnoreCase(type) || "track".equalsIgnoreCase(type);
                String ds = props.getProperty("description");
                if (ds != null) p.desc = clip(ds.trim(), 90);
                p.base = (int) clampF(num(props, "base", 1) - 1, 0, 3);
                if (props.getProperty("speed") != null) p.speed = (int) clampF(num(props, "speed", 60), 20, 200);
                p.accel = clampF(num(props, "accel", 1), 0.2f, 4f);
                p.steer = clampF(num(props, "steer", 1), 0.3f, 3f);
                p.steerMax = clampF(num(props, "steerMax", 1), 0.3f, 3f);
                p.grip = clampF(num(props, "grip", 1), 0.3f, 3f);
            }
            if (p.syn != null) checkSyn(p.syn, "car.syn");
            if (p.wheelSyn != null) checkSyn(p.wheelSyn, "wheel.syn");
            if (p.skin != null) checkPng(p.skin, "car.png");
            if (p.wheelPng != null) checkPng(p.wheelPng, "wheel.png");
            if (p.mapOnly) {
                if (!haveProps) throw new IOException("type=map needs addon.properties");
                p.mapProps = props;
                if (p.mapBin == null) throw new IOException("type=map needs track.bin");
                if (p.mapBin.length < 24 || (p.mapBin.length - 4) % 20 != 0) throw new IOException(rally3d.core.Lang.t("track.bin is damaged"));
                // background / road texture / sprites come from another player too: size-check them like car skins (a huge PNG or a wild .off table used to reach the loader unchecked)
                if (p.mapBg != null) checkImage(p.mapBg, "background.png", 1024);
                if (p.mapRoad != null) checkImage(p.mapRoad, "squares.png", 1024);
                for (int si = 0; si < 4; si++) checkSheet(p.mapSpritePng[si], p.mapSpriteOff[si], "sprite" + (si + 1));
            } else if (p.wheelsOnly) {
                if (p.wheelPng == null && p.wheelSyn == null) throw new IOException("type=wheels needs wheel.png or wheel.syn");
            } else if (p.syn == null && p.skin == null && p.wheelPng == null && p.wheelSyn == null && !haveProps) {
                throw new IOException(rally3d.core.Lang.t("no addon.properties / car.syn / car.png / wheel.png inside"));
            }
        } catch (Exception | OutOfMemoryError ex) { rally3d.core.RLog.caught("AddonPack.parse", ex);
            p.error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            rally3d.core.RLog.w("addon", "pack " + (fileName == null ? "(network)" : fileName) + " rejected: " + p.error);
        }
        return p;
    }

    private static byte[] readLimited(ZipInputStream zin, int[] budget) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(268); try {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zin.read(buf)) > 0) {
            bo.write(buf, 0, n);
            budget[0] -= n;
            if (bo.size() > MAX_ENTRY) throw new IOException(rally3d.core.Lang.t("file inside zip too big (max 32 MB)"));
            if (budget[0] < 0) throw new IOException(rally3d.core.Lang.t("zip unpacks to more than ") + (MAX_UNPACKED >> 20) + rally3d.core.Lang.t(" MB"));
        }
        return bo.toByteArray();
     } finally { rally3d.core.Trace.out(268, $t); } }

    private static String clip(String s, int max) { /*T*/ final long $t = rally3d.core.Trace.in(297); try {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < max; i++) { char c = s.charAt(i); sb.append(c < 32 || c == 127 ? ' ' : c); }   // no control chars / line breaks in menus and lobby
        return sb.toString();
     } finally { rally3d.core.Trace.out(297, $t); } }
    private static float clampF(float v, float lo, float hi) { return v < lo ? lo : v > hi ? hi : v; }
    private static float num(Properties pr, String key, float def) { /*T*/ final long $t = rally3d.core.Trace.in(269); try {
        String v = pr.getProperty(key);
        if (v == null) return def;
        try { float f = Float.parseFloat(v.trim().replace(',', '.')); return Float.isNaN(f) || Float.isInfinite(f) ? def : f; }
        catch (NumberFormatException e) { rally3d.core.RLog.caught("AddonPack.num", e); return def; }
     } finally { rally3d.core.Trace.out(269, $t); } }

    private static int u16(byte[] d, int o) { return (d[o] & 255) | (d[o + 1] & 255) << 8; }
    private static int i32(byte[] d, int o) { return (d[o] & 255) | (d[o + 1] & 255) << 8 | (d[o + 2] & 255) << 16 | (d[o + 3] & 255) << 24; }

    /** Strict .syn check: header 24 bytes, n verts (uint16): 12n pos + 4n uv [+ 12n normals], int32 index count, uint16 indices. */
    static void checkSyn(byte[] d, String what) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(270); try {
        if (d.length < 28) throw new IOException(what + rally3d.core.Lang.t(": too short"));
        int hasNormals = i32(d, 16);
        int n = u16(d, 20);
        if (n < 3) throw new IOException(what + rally3d.core.Lang.t(": no vertices"));
        int off = 24 + 12 * n + 4 * n + (hasNormals != 0 ? 12 * n : 0);
        if (off + 4 > d.length) throw new IOException(what + rally3d.core.Lang.t(": truncated (vertices)"));
        int ic = i32(d, off);
        if (ic < 3 || ic % 3 != 0 || ic > MAX_SYN_INDICES) throw new IOException(what + rally3d.core.Lang.t(": bad index count ") + ic);
        if (off + 4 + 2 * ic > d.length) throw new IOException(what + rally3d.core.Lang.t(": truncated (indices)"));
        for (int i = 0; i < ic; i++) if (u16(d, off + 4 + 2 * i) >= n) throw new IOException(what + rally3d.core.Lang.t(": index out of range"));
        for (int i = 0; i < 3 * n; i++) { int v = i32(d, 24 + 4 * i); if (v < -(1 << 19) || v > (1 << 19)) throw new IOException(what + rally3d.core.Lang.t(": vertex out of range")); }
     } finally { rally3d.core.Trace.out(270, $t); } }

    /** PNG header check without the power-of-two rule (map background / road texture): signature + 1..max pixels each way. Returns {w, h}. */
    static int[] checkImage(byte[] d, String what, int max) throws IOException {
        if (d.length < 24 || (d[0] & 255) != 0x89 || d[1] != 'P' || d[2] != 'N' || d[3] != 'G') throw new IOException(what + rally3d.core.Lang.t(": not a PNG"));
        int w = (d[16] & 255) << 24 | (d[17] & 255) << 16 | (d[18] & 255) << 8 | (d[19] & 255);
        int h = (d[20] & 255) << 24 | (d[21] & 255) << 16 | (d[22] & 255) << 8 | (d[23] & 255);
        if (w < 1 || h < 1 || w > max || h > max) throw new IOException(what + rally3d.core.Lang.t(": size ") + w + "x" + h + " (max " + max + "x" + max + ")");
        return new int[]{w, h};
    }

    /** Map sprite: LOD sheet PNG + 48-byte .off table (x[6], y[6], w[6], h[6], uint16 LE). Every frame must lie inside the sheet. Ignored when one of the two files is missing (the loader skips such a sprite). */
    static void checkSheet(byte[] png, byte[] off, String what) throws IOException {
        if (png == null || off == null) return;
        int[] sz = checkImage(png, what + ".png", 4096);
        if (off.length < 48) throw new IOException(what + ".off: too short");
        for (int i = 0; i < 6; i++) {
            int x = u16(off, i * 2), y = u16(off, 12 + i * 2), w = u16(off, 24 + i * 2), h = u16(off, 36 + i * 2);
            if (w == 0 || h == 0) continue;
            if (x + w > sz[0] || y + h > sz[1]) throw new IOException(what + ".off: frame " + i + " lies outside the image");
        }
    }

    static void checkPng(byte[] d, String what) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(271); try {
        if (d.length < 24 || (d[0] & 255) != 0x89 || d[1] != 'P' || d[2] != 'N' || d[3] != 'G') throw new IOException(what + rally3d.core.Lang.t(": not a PNG"));
        int w = (d[16] & 255) << 24 | (d[17] & 255) << 16 | (d[18] & 255) << 8 | (d[19] & 255);
        int h = (d[20] & 255) << 24 | (d[21] & 255) << 16 | (d[22] & 255) << 8 | (d[23] & 255);
        if (w < 1 || h < 1 || w > 1024 || h > 1024) throw new IOException(what + rally3d.core.Lang.t(": size ") + w + "x" + h + rally3d.core.Lang.t(" (max 1024x1024)"));
        if (Integer.bitCount(w) != 1 || Integer.bitCount(h) != 1) throw new IOException(what + rally3d.core.Lang.t(": size ") + w + "x" + h + rally3d.core.Lang.t(" must be a power of two (16, 32, 64, 128, 256...)"));
     } finally { rally3d.core.Trace.out(271, $t); } }
}
