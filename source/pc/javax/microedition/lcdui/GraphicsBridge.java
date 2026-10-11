package javax.microedition.lcdui;

/** Public bridge so the desktop runtime can create/resize Graphics objects (constructors are package-private). */
public final class GraphicsBridge {
    private GraphicsBridge() {}
    public static Graphics create(int pw, int ph, double scale, boolean alpha) { return new Graphics(pw, ph, scale, alpha); }
    public static void resize(Graphics g, int pw, int ph, double scale, boolean alpha) { g.resize(pw, ph, scale, alpha); }
    /** GPU UI mode on/off for this Graphics (draw calls recorded for the GL thread instead of rasterised by Java2D). */
    public static void setRecording(Graphics g, boolean on) { g.setRecording(on); }
    public static boolean isRecording(Graphics g) { return g.isRecording(); }
    public static UiRecorder recorder(Graphics g) { return g.rec; }
    public static java.awt.image.BufferedImage imageBuffer(Image img) { return img.buf; }
    public static java.awt.Font awtFont(Font f) { return f.awt; }
    public static java.awt.FontMetrics fontMetrics(Font f) { return f.fm; }
}
