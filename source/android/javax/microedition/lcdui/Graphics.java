package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.util.HashMap;
import java.util.Map;

/**
 * Pure-Java software Graphics on an int[] RGB framebuffer (shared with the software M3G rasteriser).
 * The game draws in logical coordinates; the buffer has physical size and a uniform scale is applied.
 * Only text uses android.graphics (glyph masks are cached).
 */
public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;

    int[] pixels;
    int pw, ph;
    double scale;
    int clipX, clipY, clipW, clipH;
    Font font = Font.getFont(0, 0, 0);
    int rgb;

    private static final class Glyphs { int[] a; int w, h, ascent; }
    private final Map<String, Glyphs> textCache = new HashMap<String, Glyphs>();

    /** Logical pixels of transparent margin around a GPU string texture (antialiasing / italic overhang). */
    public static final int TEXT_PAD = 2;

    /** Non-null = GPU UI mode: draw calls are recorded (device pixels) and replayed by the GL thread (rally3d.android.GpuUi); `pixels` then only
     *  holds software-rasterised 3D (menu car preview) as transparent ARGB until recordPixelBlit hands it to the recorder. */
    UiRecorder rec;
    final boolean isRecording() { return rec != null; }
    /** CPU UI over the GPU world (UI renderer = CPU): ARGB buffer, 0 = transparent (the world shows through), uploaded as a texture each frame. */
    boolean layered;
    final synchronized void setLayered(boolean on) {
        if (on == layered) return;
        layered = on;
        if (rec == null && pixels != null) java.util.Arrays.fill(pixels, on ? 0 : 0xFF000000);
    }
    final synchronized void setRecording(boolean on) {
        if (on == (rec != null)) return;
        rec = on ? new UiRecorder() : null;
        java.util.Arrays.fill(pixels, on || layered ? 0 : 0xFF000000);
        if (on) { rec.clear(); recordClip(); }
    }
    private int dev(int logical) { return (int) Math.round(logical * scale); }
    private void recordClip() {
        int x0 = Math.max(0, dev(clipX)), y0 = Math.max(0, dev(clipY)), x1 = Math.min(pw, dev(clipX + clipW)), y1 = Math.min(ph, dev(clipY + clipH));
        rec.clip(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
    }

    Graphics(int pw, int ph, double scale) { resize(pw, ph, scale); }

    final synchronized void resize(int pw, int ph, double scale) {
        this.pw = pw; this.ph = ph; this.scale = scale;
        pixels = new int[pw * ph];
        if (rec == null && !layered) java.util.Arrays.fill(pixels, 0xFF000000);
        textCache.clear(); charCache.clear();
        int lw = (int) Math.round(pw / scale), lh = (int) Math.round(ph / scale);
        clipX = 0; clipY = 0; clipW = lw; clipH = lh;
        if (rec != null) { rec.reset(); rec.clear(); recordClip(); }
    }

    /** True in GPU UI mode (draw calls are recorded for the GL thread). */
    public final boolean isUiRecording() { return rec != null; }
    /** GPU layered frame: this graphics is a transparent UI layer over the GPU-rendered world. */
    public boolean hasAlpha() { return rec != null || layered; }
    /** Starts a GPU-composited world frame: everything drawn before is erased (recorded as one CLEAR op). */
    public void clearTransparent() {
        if (rec != null) { rec.clear(); return; }
        java.util.Arrays.fill(pixels, 0);
    }
    /**
     * GPU UI mode only: software-rasterised 3D (menu car preview, shadows, fly view) lands in the CPU pixel buffer, which the GPU never uploads by itself.
     * Records that physical-pixel rectangle as an ARGB image op (in draw order) and clears it afterwards so stale pixels never reappear.
     */
    public void recordPixelBlit(int x0, int y0, int x1, int y1) {
        UiRecorder r = rec;
        if (r == null || pixels == null) return;
        x0 = Math.max(0, x0); y0 = Math.max(0, y0); x1 = Math.min(pw, x1); y1 = Math.min(ph, y1);
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) return;
        r.rgb(pixels, y0 * pw + x0, pw, w, h, true, x0, y0, x1, y1);
        for (int y = y0; y < y1; y++) java.util.Arrays.fill(pixels, y * pw + x0, y * pw + x1, 0);
    }

    public int[] pixelBuffer() { return pixels; }
    public int physicalWidth() { return pw; }
    public int physicalHeight() { return ph; }
    public double scale() { return scale; }
    public int getClipX() { return clipX; }
    public int getClipY() { return clipY; }
    public int getClipWidth() { return clipW; }
    public int getClipHeight() { return clipH; }

    public void setColor(int argb) { rgb = argb; }
    public void setColor(int r, int g, int b) { setColor((r << 16) | (g << 8) | b); }
    public int getColor() { return rgb & 0xFFFFFF; }

    public void setClip(int x, int y, int w, int h) { clipX = x; clipY = y; clipW = w; clipH = h; if (rec != null) recordClip(); }

    public void clipRect(int x, int y, int w, int h) {
        int x0 = Math.max(clipX, x), y0 = Math.max(clipY, y);
        int x1 = Math.min(clipX + clipW, x + w), y1 = Math.min(clipY + clipH, y + h);
        setClip(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    public void setFont(Font f) { font = f; }
    public Font getFont() { return font; }

    // physical clip, [x0,x1) [y0,y1)
    private int cx0, cy0, cx1, cy1;
    private void physClip() {
        cx0 = Math.max(0, (int) Math.floor(clipX * scale));
        cy0 = Math.max(0, (int) Math.floor(clipY * scale));
        cx1 = Math.min(pw, (int) Math.ceil((clipX + clipW) * scale));
        cy1 = Math.min(ph, (int) Math.ceil((clipY + clipH) * scale));
    }

    private void fillPhys(int x0, int y0, int x1, int y1, int argb) {
        if (x0 < cx0) x0 = cx0; if (y0 < cy0) y0 = cy0;
        if (x1 > cx1) x1 = cx1; if (y1 > cy1) y1 = cy1;
        if (x0 >= x1 || y0 >= y1) return;
        int a = argb >>> 24;
        if (a == 255) {
            int c = argb | 0xFF000000;
            for (int y = y0; y < y1; y++) java.util.Arrays.fill(pixels, y * pw + x0, y * pw + x1, c);
        } else if (a > 0) {
            int ia = 255 - a;
            int sr = ((argb >> 16) & 255) * a, sg = ((argb >> 8) & 255) * a, sb = (argb & 255) * a;
            for (int y = y0; y < y1; y++) {
                int idx = y * pw + x0;
                for (int x = x0; x < x1; x++, idx++) {
                    int d = pixels[idx];
                    int r = (sr + ((d >> 16) & 255) * ia) / 255;
                    int g = (sg + ((d >> 8) & 255) * ia) / 255;
                    int b = (sb + (d & 255) * ia) / 255;
                    pixels[idx] = layered && (d >>> 24) == 0 ? (a << 24) | (argb & 0xFFFFFF) : 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (rec != null) { rec.fill(dev(x), dev(y), dev(x + w), dev(y + h), 0xFF000000 | (rgb & 0xFFFFFF)); return; }
        physClip();
        fillPhys((int) Math.round(x * scale), (int) Math.round(y * scale),
                (int) Math.round((x + w) * scale), (int) Math.round((y + h) * scale), 0xFF000000 | rgb);
    }

    public void fillRectAlpha(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        if (rec != null) { rec.fill(dev(x), dev(y), dev(x + w), dev(y + h), argb); return; }
        physClip();
        fillPhys((int) Math.round(x * scale), (int) Math.round(y * scale),
                (int) Math.round((x + w) * scale), (int) Math.round((y + h) * scale), argb);
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        if (rec != null) { rec.line((int) Math.round((x1 + 0.5) * scale), (int) Math.round((y1 + 0.5) * scale), (int) Math.round((x2 + 0.5) * scale), (int) Math.round((y2 + 0.5) * scale), 0xFF000000 | (rgb & 0xFFFFFF)); return; }
        physClip();
        int t = Math.max(1, (int) Math.round(scale));
        int c = 0xFF000000 | rgb;
        int px1 = (int) Math.round((x1 + 0.5) * scale), py1 = (int) Math.round((y1 + 0.5) * scale);
        int px2 = (int) Math.round((x2 + 0.5) * scale), py2 = (int) Math.round((y2 + 0.5) * scale);
        int h = t / 2;
        if (y1 == y2 || x1 == x2) {
            fillPhys(Math.min(px1, px2) - h, Math.min(py1, py2) - h, Math.max(px1, px2) - h + t, Math.max(py1, py2) - h + t, c);
            return;
        }
        int dx = Math.abs(px2 - px1), dy = Math.abs(py2 - py1);
        int sx = px1 < px2 ? 1 : -1, sy = py1 < py2 ? 1 : -1;
        int err = dx - dy;
        int x = px1, y = py1;
        for (int guard = 0; guard < 100000; guard++) {
            fillPhys(x - h, y - h, x - h + t, y - h + t, c);
            if (x == px2 && y == py2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }

    private void anchorXY(int[] xy, int w, int h, int anchor) {
        if ((anchor & HCENTER) != 0) xy[0] -= w / 2; else if ((anchor & RIGHT) != 0) xy[0] -= w;
        if ((anchor & VCENTER) != 0) xy[1] -= h / 2; else if ((anchor & BOTTOM) != 0) xy[1] -= h;
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) return;
        int[] xy = {x, y};
        anchorXY(xy, img.w, img.h, anchor);
        blit(img, 0, 0, img.w, img.h, xy[0], xy[1]);
    }

    public void drawRegion(Image img, int sx, int sy, int w, int h, int x, int y) {
        if (img == null || sx < 0 || sy < 0 || w <= 0 || h <= 0 || sx + w > img.w || sy + h > img.h) return;
        blit(img, sx, sy, w, h, x, y);
    }

    private int[] colMap = new int[0];

    private void blit(Image img, int sx, int sy, int w, int h, int x, int y) {
        if (rec != null) { rec.image(img, dev(x), dev(y), dev(x + w), dev(y + h), sx, sy, sx + w, sy + h); return; }
        physClip();
        int dx0 = (int) Math.round(x * scale), dx1 = (int) Math.round((x + w) * scale);
        int dy0 = (int) Math.round(y * scale), dy1 = (int) Math.round((y + h) * scale);
        int x0 = Math.max(dx0, cx0), x1 = Math.min(dx1, cx1);
        int y0 = Math.max(dy0, cy0), y1 = Math.min(dy1, cy1);
        if (x0 >= x1 || y0 >= y1) return;
        int pwid = dx1 - dx0, phei = dy1 - dy0;
        if (pwid <= 0 || phei <= 0) return;
        if (colMap.length < x1 - x0) colMap = new int[Math.max(x1 - x0, 64)];
        for (int dx = x0; dx < x1; dx++) {
            int u = (int) (((long) (dx - dx0) * 2 + 1) * w / (2L * pwid));
            colMap[dx - x0] = sx + (u >= w ? w - 1 : u);
        }
        final int[] src = img.argb, dst = pixels;
        for (int dy = y0; dy < y1; dy++) {
            int v = (int) (((long) (dy - dy0) * 2 + 1) * h / (2L * phei));
            int srow = (sy + (v >= h ? h - 1 : v)) * img.w;
            int didx = dy * pw + x0;
            for (int dx = x0; dx < x1; dx++, didx++) {
                int s = src[srow + colMap[dx - x0]];
                int a = s >>> 24;
                if (a == 255) dst[didx] = s;
                else if (a != 0) {
                    int d = dst[didx], ia = 255 - a;
                    int r = (((s >> 16) & 255) * a + ((d >> 16) & 255) * ia) / 255;
                    int g = (((s >> 8) & 255) * a + ((d >> 8) & 255) * ia) / 255;
                    int b = ((s & 255) * a + (d & 255) * ia) / 255;
                    dst[didx] = layered && (d >>> 24) == 0 ? s : 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    public void drawRGB(int[] data, int off, int scan, int x, int y, int w, int h, boolean alpha) {
        if (w <= 0 || h <= 0) return;
        if (rec != null) { rec.rgb(data, off, scan, w, h, alpha, dev(x), dev(y), dev(x + w), dev(y + h)); return; }
        int[] a = new int[w * h];
        for (int j = 0; j < h; j++) System.arraycopy(data, off + j * scan, a, j * w, w);
        if (!alpha) for (int i = 0; i < a.length; i++) a[i] |= 0xFF000000;
        blit(new Image(a, w, h), 0, 0, w, h, x, y);
    }

    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    private static final class Glyph { int[] a; int w, h, ascent; float adv; }
    private final android.util.SparseArray<Glyph> charCache = new android.util.SparseArray<Glyph>();

    private Glyph glyph(char c) {
        int key = (font.id << 16) | c;
        Glyph g = charCache.get(key);
        if (g != null) return g;
        if (charCache.size() > 3000) charCache.clear();
        Paint p = maskPaint;
        p.setTypeface(font.paint.getTypeface());
        p.setTextSize(font.px * (float) scale);
        p.setColor(0xFFFFFFFF);
        String s = String.valueOf(c);
        Paint.FontMetrics fm = p.getFontMetrics();
        int asc = (int) Math.ceil(-fm.ascent) + 1;
        float adv = p.measureText(s);
        int w = Math.max(1, (int) Math.ceil(adv) + 3);
        int h = asc + (int) Math.ceil(fm.descent) + 2;
        g = new Glyph();
        g.w = w; g.h = h; g.ascent = asc; g.adv = adv;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawText(s, 1, asc, p);
        g.a = new int[w * h];
        b.getPixels(g.a, 0, w, 0, 0, w, h);
        b.recycle();
        charCache.put(key, g);
        return g;
    }

    public synchronized void drawString(String s, int x, int y, int anchor) {
        if (s == null || s.length() == 0) return;
        int width = font.stringWidth(s);
        if ((anchor & HCENTER) != 0) x -= width / 2; else if ((anchor & RIGHT) != 0) x -= width;
        int base;
        if ((anchor & BOTTOM) != 0) base = y - font.descent();
        else if ((anchor & BASELINE) != 0) base = y;
        else base = y + font.ascent();
        if (rec != null) {
            // The string texture is rendered by GraphicsBridge.renderString with a TEXT_PAD logical margin on every side (see GpuUi.stringTexture).
            rec.string(s, font, dev(x - TEXT_PAD), dev(base - font.ascent() - TEXT_PAD), 0xFF000000 | (rgb & 0xFFFFFF));
            return;
        }
        physClip();
        int ox0 = (int) Math.round(x * scale) - 1;
        int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
        float pen = 0f;
        for (int ci = 0; ci < s.length(); ci++) {
            Glyph g = glyph(s.charAt(ci));
            int ox = ox0 + Math.round(pen);
            pen += g.adv;
            int oy = (int) Math.round(base * scale) - g.ascent;
            int x0 = Math.max(ox, cx0), x1 = Math.min(ox + g.w, cx1);
            int y0 = Math.max(oy, cy0), y1 = Math.min(oy + g.h, cy1);
            if (x0 >= x1 || y0 >= y1) continue;
            for (int dy = y0; dy < y1; dy++) {
                int sidx = (dy - oy) * g.w + (x0 - ox);
                int didx = dy * pw + x0;
                for (int dx = x0; dx < x1; dx++, sidx++, didx++) {
                    int a = g.a[sidx] >>> 24;
                    if (a == 0) continue;
                    if (a == 255) { pixels[didx] = 0xFF000000 | (cr << 16) | (cg << 8) | cb; continue; }
                    int d = pixels[didx], ia = 255 - a;
                    int r = (cr * a + ((d >> 16) & 255) * ia) / 255;
                    int gg = (cg * a + ((d >> 8) & 255) * ia) / 255;
                    int b = (cb * a + (d & 255) * ia) / 255;
                    pixels[didx] = layered && (d >>> 24) == 0 ? (a << 24) | (cr << 16) | (cg << 8) | cb : 0xFF000000 | (r << 16) | (gg << 8) | b;
                }
            }
        }
    }
}
