package javax.microedition.lcdui;

import android.graphics.Paint;
import android.graphics.Typeface;

/** Logical-size font backed by android.graphics.Paint. Metrics are in logical pixels. */
public final class Font {
    public static final int FACE_SYSTEM = 0, FACE_MONOSPACE = 32, FACE_PROPORTIONAL = 64;
    public static final int STYLE_PLAIN = 0, STYLE_BOLD = 1, STYLE_ITALIC = 2, STYLE_UNDERLINED = 4;
    public static final int SIZE_SMALL = 8, SIZE_MEDIUM = 0, SIZE_LARGE = 16;

    final Paint paint;
    final int px;
    private final int face, style, size, ascent, descent, height;
    final int id;

    private Font(int face, int style, int size) { this(face, style, size, size == SIZE_SMALL ? 11 : size == SIZE_LARGE ? 17 : 14, face * 1000 + style * 100 + size); }

    private Font(int face, int style, int size, int pxSize, int fontId) {
        this.face = face; this.style = style; this.size = size;
        px = pxSize;
        id = fontId;
        int st = ((style & STYLE_BOLD) != 0 ? Typeface.BOLD : 0) | ((style & STYLE_ITALIC) != 0 ? Typeface.ITALIC : 0);
        Typeface base = face == FACE_MONOSPACE ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create(base, st));
        paint.setTextSize(px);
        Paint.FontMetrics fm = paint.getFontMetrics();
        ascent = (int) Math.ceil(-fm.ascent);
        descent = (int) Math.ceil(fm.descent);
        height = ascent + descent + (int) Math.ceil(fm.leading);
    }

    public static Font getFont(int face, int style, int size) { return new Font(face, style, size); }
    /** Font with an arbitrary pixel size (logical px), e.g. player names that shrink with distance. */
    public static Font getSizedFont(int face, int style, int px) { int p = Math.max(4, Math.min(40, px)); return new Font(face, style, SIZE_MEDIUM, p, 30000 + style * 100 + p); }
    public int getPixelSize() { return px; }
    public static Font getDefaultFont() { return new Font(0, 0, 0); }
    public int getHeight() { return height; }
    public int getBaselinePosition() { return ascent; }
    int ascent() { return ascent; }
    int descent() { return descent; }
    public int stringWidth(String s) { return (int) Math.ceil(paint.measureText(s)); }
    public int charWidth(char c) { return (int) Math.ceil(paint.measureText(String.valueOf(c))); }
    public int getStyle() { return style; }
    public int getSize() { return size; }
    public int getFace() { return face; }
}
