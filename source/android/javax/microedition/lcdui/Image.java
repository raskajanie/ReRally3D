package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;

/** Image stored as straight-alpha ARGB int[] so the pure-Java blitter can use it directly. */
public class Image {
    public static final int TRANS_NONE = 0, TRANS_MIRROR = 2;
    final int[] argb;
    final int w, h;

    Image(int[] a, int w, int h) { argb = a; this.w = w; this.h = h; }

    /** Bilinear-scaled copy (non-MIDP helper: stretches full-screen backgrounds). */
    public static Image createScaled(Image src, int nw, int nh) {
        nw = Math.max(1, nw); nh = Math.max(1, nh);
        int[] out = new int[nw * nh];
        float fx = nw > 1 ? (src.w - 1f) / (nw - 1) : 0f, fy = nh > 1 ? (src.h - 1f) / (nh - 1) : 0f;
        for (int y = 0; y < nh; y++) {
            float sy = y * fy; int y0 = (int) sy, y1 = Math.min(src.h - 1, y0 + 1); float wy = sy - y0;
            for (int x = 0; x < nw; x++) {
                float sx = x * fx; int x0 = (int) sx, x1 = Math.min(src.w - 1, x0 + 1); float wx = sx - x0;
                int p00 = src.argb[y0 * src.w + x0], p10 = src.argb[y0 * src.w + x1], p01 = src.argb[y1 * src.w + x0], p11 = src.argb[y1 * src.w + x1];
                int r = 0;
                for (int sh = 24; sh >= 0; sh -= 8) {
                    float top = ((p00 >>> sh) & 255) * (1 - wx) + ((p10 >>> sh) & 255) * wx;
                    float bot = ((p01 >>> sh) & 255) * (1 - wx) + ((p11 >>> sh) & 255) * wx;
                    r |= ((int) (top * (1 - wy) + bot * wy + 0.5f) & 255) << sh;
                }
                out[y * nw + x] = r;
            }
        }
        return new Image(out, nw, nh);
    }

    public static Image createImage(String name) throws IOException {
        InputStream in = Image.class.getResourceAsStream(name);
        if (in == null) throw new IOException("Resource not found: " + name);
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            o.inPremultiplied = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            if (b == null) throw new IOException("Unreadable image: " + name);
            int w = b.getWidth(), h = b.getHeight();
            int[] px = new int[w * h];
            b.getPixels(px, 0, w, 0, 0, w, h);
            b.recycle();
            return new Image(px, w, h);
        } finally {
            in.close();
        }
    }

    /** Decode an image from memory (PNG/JPEG...), used for add-on skins that live inside a zip, not in the resources. */
    public static Image createImage(byte[] data, int off, int len) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        o.inPremultiplied = false;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeByteArray(data, off, len, o);
        if (b == null) throw new IOException("Unreadable image data");
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        b.recycle();
        return new Image(px, w, h);
    }

    public static Image createImage(int w, int h) {
        w = Math.max(1, w); h = Math.max(1, h);
        return new Image(new int[w * h], w, h);
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        if (rgb == null || width <= 0 || height <= 0 || (long) width * height > rgb.length)
            throw new IllegalArgumentException("Invalid RGB image data");
        int[] a = new int[width * height];
        if (processAlpha) System.arraycopy(rgb, 0, a, 0, a.length);
        else for (int i = 0; i < a.length; i++) a[i] = rgb[i] | 0xFF000000;
        return new Image(a, width, height);
    }

    /** Only TRANS_NONE and TRANS_MIRROR are supported. */
    public static Image createImage(Image src, int x, int y, int w, int h, int transform) {
        int[] a = new int[w * h];
        for (int j = 0; j < h; j++) {
            int sy = y + j;
            for (int i = 0; i < w; i++) {
                int sx = transform == TRANS_MIRROR ? x + (w - 1 - i) : x + i;
                a[j * w + i] = (sx >= 0 && sy >= 0 && sx < src.w && sy < src.h) ? src.argb[sy * src.w + sx] : 0;
            }
        }
        return new Image(a, w, h);
    }

    public int getWidth() { return w; }
    public int getHeight() { return h; }
    public void getRGB(int[] out, int off, int scan, int x, int y, int ww, int hh) {
        for (int j = 0; j < hh; j++) System.arraycopy(argb, (y + j) * w + x, out, off + j * scan, ww);
    }
}
