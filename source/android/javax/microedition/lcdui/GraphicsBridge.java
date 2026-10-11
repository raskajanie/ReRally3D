package javax.microedition.lcdui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Public bridge so the runtime can create/resize Graphics objects (constructors are package-private) and the GPU UI can reach lcdui internals. */
public final class GraphicsBridge {
    private GraphicsBridge() {}
    public static Graphics create(int pw, int ph, double scale) { return new Graphics(pw, ph, scale); }
    public static void resize(Graphics g, int pw, int ph, double scale) { g.resize(pw, ph, scale); }
    /** GPU UI mode on/off for this Graphics (draw calls recorded for the GL thread instead of rasterised on the CPU). */
    public static void setRecording(Graphics g, boolean on) { g.setRecording(on); }
    public static boolean isRecording(Graphics g) { return g.isRecording(); }
    public static UiRecorder recorder(Graphics g) { return g.rec; }
    /** Straight-alpha ARGB pixels of an image (do not modify). */
    public static int[] imageArgb(Image img) { return img.argb; }

    /**
     * White-on-transparent ARGB bitmap of a string, TEXT_PAD logical pixels of margin on every side, rasterised at the device scale.
     * wh receives {width, height} in device pixels. Returns null for empty / absurdly large strings.
     */
    public static int[] renderString(Font f, String s, double scale, int[] wh) {
        if (s == null || s.isEmpty() || f == null) return null;
        int pad = Graphics.TEXT_PAD;
        int lw = f.stringWidth(s) + 2 * pad, lh = f.ascent() + f.descent() + 2 * pad;
        int dw = (int) Math.ceil(lw * scale), dh = (int) Math.ceil(lh * scale);
        if (dw <= 0 || dh <= 0 || dw > 8192 || dh > 2048) return null;
        Bitmap bmp = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888);
        try {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            p.setTypeface(f.paint.getTypeface());
            p.setTextSize(f.px * (float) scale);
            p.setColor(0xFFFFFFFF);
            new Canvas(bmp).drawText(s, (float) (pad * scale), (float) ((pad + f.ascent()) * scale), p);
            int[] px = new int[dw * dh];
            bmp.getPixels(px, 0, dw, 0, 0, dw, dh);
            wh[0] = dw; wh[1] = dh;
            return px;
        } finally { bmp.recycle(); }
    }
}
