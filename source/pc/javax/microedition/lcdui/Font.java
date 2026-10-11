package javax.microedition.lcdui;

import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Logical-size font: metrics are measured at 1:1 logical size, drawing is scaled by the Graphics transform. */
public final class Font {
    public static final int FACE_SYSTEM = 0, FACE_MONOSPACE = 32, FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0, STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8, SIZE_MEDIUM = 0, SIZE_LARGE = 16;

    final java.awt.Font awt;
    final FontMetrics fm;
    private final int face, style, size;

    private final int pixelSize;

    private Font(int face, int style, int size) { this(face, style, size, size == SIZE_SMALL ? 11 : size == SIZE_LARGE ? 17 : 14); }

    private Font(int face, int style, int size, int px) {
        this.face = face; this.style = style; this.size = size;
        this.pixelSize = px;
        int st = ((style & STYLE_BOLD) != 0 ? java.awt.Font.BOLD : 0) | ((style & STYLE_ITALIC) != 0 ? java.awt.Font.ITALIC : 0);
        String fam = face == FACE_MONOSPACE ? java.awt.Font.MONOSPACED : java.awt.Font.SANS_SERIF;
        awt = new java.awt.Font(fam, st, px);
        BufferedImage bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        fm = g.getFontMetrics(awt);
        g.dispose();
    }

    public static Font getFont(int face, int style, int size) { return new Font(face, style, size); }
    /** Font with an arbitrary pixel size (logical px), e.g. player names that shrink with distance. */
    public static Font getSizedFont(int face, int style, int px) { return new Font(face, style, SIZE_MEDIUM, Math.max(4, Math.min(40, px))); }
    public int getPixelSize() { return pixelSize; }
    public static Font getDefaultFont() { return new Font(0, 0, 0); }
    public int getHeight() { return fm.getHeight(); }
    public int getBaselinePosition() { return fm.getAscent(); }
    public int stringWidth(String s) { return fm.stringWidth(s); }
    public int charWidth(char c) { return fm.charWidth(c); }
    public int getStyle() { return style; }
    public int getSize() { return size; }
    public int getFace() { return face; }
}
