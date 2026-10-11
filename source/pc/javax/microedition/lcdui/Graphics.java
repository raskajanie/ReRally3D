package javax.microedition.lcdui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Software-backed Graphics. The game draws in "logical" coordinates (e.g. 427x320);
 * the backing buffer has physical size (e.g. 1280x720) and a uniform scale factor is applied.
 */
public class Graphics {
    public static final int HCENTER = 1, VCENTER = 2, LEFT = 4, RIGHT = 8, TOP = 16, BOTTOM = 32, BASELINE = 64;

    BufferedImage image;
    int[] pixels;
    java.awt.Graphics2D g2;
    int pw, ph;
    double scale;
    int clipX, clipY, clipW, clipH;
    Font font = Font.getFont(0, 0, 0);
    int rgb;

    /** True = ARGB buffer (GPU layered mode: this is the transparent UI layer composited over the GPU-rendered 3D world). */
    boolean alpha;

    /** Non-null = GPU UI mode: draw calls are recorded (device pixels) and replayed by the GL thread; Java2D is not touched and `pixels` stays empty. */
    UiRecorder rec;
    final boolean isRecording() { return rec != null; }
    final synchronized void setRecording(boolean on) {
        if (on == (rec != null)) return;
        rec = on ? new UiRecorder() : null;
        if (on) { rec.clear(); recordClip(); } else if (g2 != null) { g2.setClip(clipX, clipY, clipW, clipH); }
    }
    private int dev(int logical) { return (int) Math.round(logical * scale); }
    private void recordClip() {
        int x0 = Math.max(0, dev(clipX)), y0 = Math.max(0, dev(clipY)), x1 = Math.min(pw, dev(clipX + clipW)), y1 = Math.min(ph, dev(clipY + clipH));
        rec.clip(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
    }

    Graphics(int pw, int ph, double scale, boolean alpha) { resize(pw, ph, scale, alpha); }

    final synchronized void resize(int pw, int ph, double scale) { resize(pw, ph, scale, alpha); }

    final synchronized void resize(int pw, int ph, double scale, boolean alpha) {
        if (g2 != null) g2.dispose();
        prescaled.clear();
        this.pw = pw; this.ph = ph; this.scale = scale; this.alpha = alpha;
        image = new BufferedImage(pw, ph, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        g2 = image.createGraphics();
        g2.scale(scale, scale);
        // The original assets are low-resolution pixel art. Nearest-neighbour
        // scaling avoids filtering work on every sprite while preserving their
        // intended crisp edges at desktop render resolutions.
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        int lw = (int) Math.round(pw / scale), lh = (int) Math.round(ph / scale);
        clipX = 0; clipY = 0; clipW = lw; clipH = lh;
        g2.setClip(0, 0, lw, lh);
        g2.setFont(font.awt);
        setColor(rgb);
        if (rec != null) { rec.reset(); rec.clear(); recordClip(); }
    }

    public int[] pixelBuffer() { return pixels; }
    /** True in GPU UI mode (draw calls are recorded for the GL thread). */
    public final boolean isUiRecording() { return rec != null; }
    /**
     * GPU UI mode only: software-rasterised 3D (car preview in the menus, shadows, fly view) lands in the CPU pixel buffer, which the GPU UI never uploads.
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
    public boolean hasAlpha() { return alpha; }

    /** Sets every pixel to fully transparent (all cores). Used at the start of a GPU-composited world frame: the CPU then draws only the UI on top. */
    public void clearTransparent() {
        if (rec != null) { rec.clear(); return; }
        final int[] p = pixels;
        final int w = pw, h = ph;
        if (p == null || w <= 0 || h <= 0) return;
        if (h < 128) { java.util.Arrays.fill(p, 0, Math.min(p.length, w * h), 0); return; }
        final int band = Math.max(32, h / 32);
        rally3d.desktop.Platform.parallelFor((h + band - 1) / band, c -> {
            int y0 = c * band, y1 = Math.min(h, y0 + band);
            java.util.Arrays.fill(p, y0 * w, Math.min(p.length, y1 * w), 0);
        });
    }
    public int physicalWidth() { return pw; }
    public int physicalHeight() { return ph; }
    public double scale() { return scale; }
    public int getClipX() { return clipX; }
    public int getClipY() { return clipY; }
    public int getClipWidth() { return clipW; }
    public int getClipHeight() { return clipH; }

    private int colorKey = -1; private Color colorObj;
    private Color awtColor(int c) { c &= 0xFFFFFF; if (c != colorKey || colorObj == null) { colorKey = c; colorObj = new Color(c); } return colorObj; }
    public void setColor(int argb) { rgb = argb; g2.setColor(awtColor(argb)); }
    public void setColor(int r, int g, int b) { setColor((r << 16) | (g << 8) | b); }
    public int getColor() { return rgb & 0xFFFFFF; }

    public void setClip(int x, int y, int w, int h) {
        clipX = x; clipY = y; clipW = w; clipH = h;
        if (rec != null) { recordClip(); return; }
        g2.setClip(x, y, w, h);
    }

    public void clipRect(int x, int y, int w, int h) {
        int x0 = Math.max(clipX, x), y0 = Math.max(clipY, y);
        int x1 = Math.min(clipX + clipW, x + w), y1 = Math.min(clipY + clipH, y + h);
        setClip(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    public void setFont(Font f) { font = f; g2.setFont(f.awt); }
    public Font getFont() { return font; }

    public void fillRect(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (rec != null) { rec.fill(dev(x), dev(y), dev(x + w), dev(y + h), 0xFF000000 | (rgb & 0xFFFFFF)); return; }
        g2.fillRect(x, y, w, h);
    }
    public void drawLine(int x1, int y1, int x2, int y2) {
        if (rec != null) { rec.line((int) Math.round((x1 + 0.5) * scale), (int) Math.round((y1 + 0.5) * scale), (int) Math.round((x2 + 0.5) * scale), (int) Math.round((y2 + 0.5) * scale), 0xFF000000 | (rgb & 0xFFFFFF)); return; }
        g2.drawLine(x1, y1, x2, y2);
    }

    public void drawImage(Image img, int x, int y, int anchor) {
        if (img == null) return;
        int w = img.getWidth(), h = img.getHeight();
        if ((anchor & HCENTER) != 0) x -= w / 2; else if ((anchor & RIGHT) != 0) x -= w;
        if ((anchor & VCENTER) != 0) y -= h / 2; else if ((anchor & BOTTOM) != 0) y -= h;
        if (rec != null) { rec.image(img, dev(x), dev(y), dev(x + w), dev(y + h), 0, 0, w, h); return; }
        if (scale != 1.0 && w * h >= FAST_BLIT_MIN_PIXELS) { drawImageFast(img.buf, x, y, w, h); return; }
        g2.drawImage(img.buf, x, y, null);
    }

    // ---- fast path for big images (sky): Java2D's scaled nearest-neighbour blit (DrawImage.renderImageXform) was ~11% of a software frame ----
    private static final int FAST_BLIT_MIN_PIXELS = 16384;
    private final java.util.IdentityHashMap<BufferedImage, BufferedImage> prescaled = new java.util.IdentityHashMap<BufferedImage, BufferedImage>();

    /** Image scaled once to device pixels (nearest neighbour, ceil size so neighbouring tiles overlap instead of leaving a gap), drawn with an identity transform. */
    private void drawImageFast(BufferedImage src, int x, int y, int w, int h) {
        BufferedImage big = prescaled.get(src);
        if (big == null) {
            int sw = (int) Math.ceil(w * scale), sh = (int) Math.ceil(h * scale);
            if (sw <= 0 || sh <= 0 || (long) sw * sh > 16_000_000L) { g2.drawImage(src, x, y, null); return; }
            if (prescaled.size() >= 6) prescaled.clear();   // images change per track: keep the cache small
            boolean alpha = src.getColorModel().hasAlpha();
            big = new BufferedImage(sw, sh, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            int[] in = src.getRGB(0, 0, w, h, null, 0, w), out = ((DataBufferInt) big.getRaster().getDataBuffer()).getData();
            double fx = (double) w / sw, fy = (double) h / sh;
            int[] xs = new int[sw];
            for (int dx = 0; dx < sw; dx++) xs[dx] = Math.min(w - 1, (int) ((dx + 0.5) * fx));
            for (int dy = 0; dy < sh; dy++) {
                int rowIn = Math.min(h - 1, (int) ((dy + 0.5) * fy)) * w, rowOut = dy * sw;
                for (int dx = 0; dx < sw; dx++) out[rowOut + dx] = in[rowIn + xs[dx]];
            }
            prescaled.put(src, big);
        }
        java.awt.geom.AffineTransform saved = g2.getTransform();
        g2.setTransform(IDENTITY);   // the clip is kept in device space, so it still applies
        g2.drawImage(big, (int) Math.round(x * scale), (int) Math.round(y * scale), null);
        g2.setTransform(saved);
    }
    private static final java.awt.geom.AffineTransform IDENTITY = new java.awt.geom.AffineTransform();

    /** Draws only the requested source rectangle, avoiding full-sheet draws for sprite atlases. */
    public void drawRegion(Image img, int sx, int sy, int w, int h, int x, int y) {
        if (img == null || sx < 0 || sy < 0 || w <= 0 || h <= 0 || sx + w > img.buf.getWidth() || sy + h > img.buf.getHeight()) return;
        if (rec != null) { rec.image(img, dev(x), dev(y), dev(x + w), dev(y + h), sx, sy, sx + w, sy + h); return; }
        g2.drawImage(img.buf, x, y, x + w, y + h, sx, sy, sx + w, sy + h, null);
    }

    public void drawString(String s, int x, int y, int anchor) {
        FontMetrics fm = font.fm;
        if ((anchor & HCENTER) != 0) x -= fm.stringWidth(s) / 2; else if ((anchor & RIGHT) != 0) x -= fm.stringWidth(s);
        int base;
        if ((anchor & BOTTOM) != 0) base = y - fm.getDescent();
        else if ((anchor & BASELINE) != 0) base = y;
        else base = y + fm.getAscent();
        if (rec != null) {
            if (s == null || s.isEmpty()) return;
            // The string texture is rendered by Java2D with a TEXT_PAD logical margin on every side (see rally3d.desktop.GpuUi.stringTexture).
            rec.string(s, font, dev(x - TEXT_PAD), dev(base - fm.getAscent() - TEXT_PAD), 0xFF000000 | (rgb & 0xFFFFFF));
            return;
        }
        g2.drawString(s, x, base);
    }
    /** Logical pixels of transparent margin around a GPU string texture (antialiasing / italic overhang). */
    public static final int TEXT_PAD = 2;

    public void drawRGB(int[] data, int off, int scan, int x, int y, int w, int h, boolean alpha) {
        if (w <= 0 || h <= 0) return;
        if (rec != null) { rec.rgb(data, off, scan, w, h, alpha, dev(x), dev(y), dev(x + w), dev(y + h)); return; }
        BufferedImage b = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < h; j++) b.setRGB(0, j, w, 1, data, off + j * scan, w);
        g2.drawImage(b, x, y, null);
    }

    /** Alpha-blended filled rectangle (non-MIDP helper used by the desktop port for translucent panels). */
    public void fillRectAlpha(int x, int y, int w, int h, int argb) {
        if (rec != null) { if (w > 0 && h > 0) rec.fill(dev(x), dev(y), dev(x + w), dev(y + h), argb); return; }
        java.awt.Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ((argb >>> 24) & 255) / 255f));
        g2.setColor(awtColor(argb));
        g2.fillRect(x, y, w, h);
        g2.setComposite(old);
        g2.setColor(awtColor(rgb));
    }
}
