package com.nokia.mid.appl.rally3d;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.microedition.lcdui.Image;

/**
 * Tiny PNG writer (RGBA, 8 bit) and image helpers for the map editor: the game has no image encoder of its own,
 * custom sprites / backgrounds / road textures painted in the editor are written into the map zip with this.
 * Pure java.util.zip, so it works the same on PC and Android.
 */
final class PngCodec {
    private PngCodec() {}

    static byte[] encode(int[] argb, int w, int h) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(488); try {
        if (w <= 0 || h <= 0 || argb.length < w * h) throw new IOException("bad image size");
        byte[] raw = new byte[(w * 4 + 1) * h];
        int o = 0;
        for (int y = 0; y < h; y++) {
            raw[o++] = 0;   // filter: none
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                raw[o++] = (byte) (p >> 16);
                raw[o++] = (byte) (p >> 8);
                raw[o++] = (byte) p;
                raw[o++] = (byte) (p >>> 24);
            }
        }
        Deflater df = new Deflater(9);
        df.setInput(raw);
        df.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!df.finished()) { int n = df.deflate(buf); z.write(buf, 0, n); }
        df.end();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, w); putInt(ihdr, 4, h);
        ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
        chunk(out, "IHDR", ihdr);
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
     } finally { rally3d.core.Trace.out(488, $t); } }

    private static void putInt(byte[] b, int o, int v) { b[o] = (byte) (v >>> 24); b[o + 1] = (byte) (v >>> 16); b[o + 2] = (byte) (v >>> 8); b[o + 3] = (byte) v; }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(489); try {
        byte[] len = new byte[4];
        putInt(len, 0, data.length);
        out.write(len);
        byte[] t = type.getBytes("US-ASCII");
        out.write(t);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        byte[] c = new byte[4];
        putInt(c, 0, (int) crc.getValue());
        out.write(c);
     } finally { rally3d.core.Trace.out(489, $t); } }

    /** Decodes any PNG the game can load. Returns ARGB pixels, wh[0] = width, wh[1] = height. Null when it cannot be read. */
    static int[] decode(byte[] png, int[] wh) { /*T*/ final long $t = rally3d.core.Trace.in(490); try {
        try {
            Image img = Image.createImage(png, 0, png.length);
            int w = img.getWidth(), h = img.getHeight();
            if (w <= 0 || h <= 0 || w > 1024 || h > 1024) return null;
            int[] px = new int[w * h];
            img.getRGB(px, 0, w, 0, 0, w, h);
            if (wh != null) { wh[0] = w; wh[1] = h; }
            return px;
        } catch (Exception e) { rally3d.core.RLog.caught("PngCodec.decode", e);
            return null;
        }
     } finally { rally3d.core.Trace.out(490, $t); } }

    /** Box-filter resize with alpha-weighted colour (no dark fringes around sprite edges). */
    static int[] scale(int[] src, int sw, int sh, int dw, int dh) { /*T*/ final long $t = rally3d.core.Trace.in(491); try {
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int y0 = y * sh / dh, y1 = Math.max(y0 + 1, (y + 1) * sh / dh);
            for (int x = 0; x < dw; x++) {
                int x0 = x * sw / dw, x1 = Math.max(x0 + 1, (x + 1) * sw / dw);
                long a = 0, r = 0, g = 0, b = 0;
                int cnt = 0;
                for (int yy = y0; yy < y1 && yy < sh; yy++) for (int xx = x0; xx < x1 && xx < sw; xx++) {
                    int p = src[yy * sw + xx];
                    int pa = p >>> 24;
                    a += pa; r += ((p >> 16) & 255) * pa; g += ((p >> 8) & 255) * pa; b += (p & 255) * pa;
                    cnt++;
                }
                if (cnt == 0 || a == 0) { out[y * dw + x] = 0; continue; }
                int oa = (int) (a / cnt);
                out[y * dw + x] = (oa << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
            }
        }
        return out;
     } finally { rally3d.core.Trace.out(491, $t); } }

    /** LOD scale factors of the 6 frames of a sprite sheet (frame 0 = full size), like the stock billboards. */
    static final float[] LOD = {1.0f, 0.78f, 0.58f, 0.42f, 0.30f, 0.22f};

    /** Result of {@link #spriteSheet}: png = the sheet, off = the 48-byte .off table (frame x[6], y[6], w[6], h[6], little-endian shorts). */
    static final class Sheet { byte[] png; byte[] off; }

    /** Builds the 6-frame LOD sheet + .off table the game's Billboard loader expects from one painted frame. */
    static Sheet spriteSheet(int[] frame0, int w, int h) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(492); try {
        int[] fw = new int[6], fh = new int[6], fx = new int[6];
        int total = 0;
        for (int i = 0; i < 6; i++) {
            fw[i] = Math.max(1, Math.round(w * LOD[i]));
            fh[i] = Math.max(1, Math.round(h * LOD[i]));
            fx[i] = total;
            total += fw[i];
        }
        int[] sheet = new int[total * h];
        for (int i = 0; i < 6; i++) {
            int[] f = i == 0 ? frame0 : scale(frame0, w, h, fw[i], fh[i]);
            for (int y = 0; y < fh[i]; y++) System.arraycopy(f, y * fw[i], sheet, y * total + fx[i], fw[i]);
        }
        Sheet s = new Sheet();
        s.png = encode(sheet, total, h);
        byte[] off = new byte[48];
        for (int i = 0; i < 6; i++) {
            put16(off, i * 2, fx[i]); put16(off, 12 + i * 2, 0); put16(off, 24 + i * 2, fw[i]); put16(off, 36 + i * 2, fh[i]);
        }
        s.off = off;
        return s;
     } finally { rally3d.core.Trace.out(492, $t); } }

    private static void put16(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); }

    /** Crops frame 0 back out of a sheet (so a map can be re-opened in the sprite painter). Returns null when the data does not fit. */
    static int[] spriteFrame0(byte[] sheetPng, byte[] off, int[] wh) { /*T*/ final long $t = rally3d.core.Trace.in(493); try {
        if (sheetPng == null || off == null || off.length < 48) return null;
        int fx = (off[0] & 255) | ((off[1] & 255) << 8), fy = (off[12] & 255) | ((off[13] & 255) << 8);
        int fw = (off[24] & 255) | ((off[25] & 255) << 8), fh = (off[36] & 255) | ((off[37] & 255) << 8);
        int[] sz = new int[2];
        int[] px = decode(sheetPng, sz);
        if (px == null || fw <= 0 || fh <= 0 || fx + fw > sz[0] || fy + fh > sz[1]) return null;
        int[] out = new int[fw * fh];
        for (int y = 0; y < fh; y++) System.arraycopy(px, (fy + y) * sz[0] + fx, out, y * fw, fw);
        wh[0] = fw; wh[1] = fh;
        return out;
     } finally { rally3d.core.Trace.out(493, $t); } }
}
