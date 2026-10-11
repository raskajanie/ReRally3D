package javax.microedition.m3g;

public class Image2D extends Object3D {
    public static final int ALPHA = 96, LUMINANCE = 97, LUMINANCE_ALPHA = 98, RGB = 99, RGBA = 100;
    final int width, height; final int[] argb;

    public Image2D(int format, Object image) {
        javax.microedition.lcdui.Image img = (javax.microedition.lcdui.Image) image;
        width = img.getWidth(); height = img.getHeight();
        argb = new int[width * height];
        img.getRGB(argb, 0, width, 0, 0, width, height);
        if (format != RGBA) for (int i = 0; i < argb.length; i++) argb[i] |= 0xFF000000;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
