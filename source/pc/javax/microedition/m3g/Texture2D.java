package javax.microedition.m3g;

public class Texture2D extends Object3D {
    public static final int FUNC_ADD = 224, FUNC_BLEND = 225, FUNC_DECAL = 226, FUNC_MODULATE = 227, FUNC_REPLACE = 228;
    final int w, h, wMask, hMask, wShift;
    final int[] rgb;
    int func = FUNC_MODULATE;

    public Texture2D(Image2D image) {
        w = image.width; h = image.height;
        if (Integer.bitCount(w) != 1 || Integer.bitCount(h) != 1) throw new IllegalArgumentException("texture size must be a power of two");
        wMask = w - 1; hMask = h - 1; wShift = Integer.numberOfTrailingZeros(w);
        rgb = image.argb;
    }

    public void setBlending(int f) { func = f; }
}
