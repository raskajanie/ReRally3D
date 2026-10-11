package javax.microedition.lcdui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

public class Image {
    public static final int TRANS_NONE = 0, TRANS_MIRROR = 2;
    final BufferedImage buf;

    Image(BufferedImage b) { buf = b; }

    /** Bilinear-scaled copy (non-MIDP helper: stretches full-screen backgrounds). */
    public static Image createScaled(Image src, int w, int h) {
        w = Math.max(1, w); h = Math.max(1, h);
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = b.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src.buf, 0, 0, w, h, null);
        g.dispose();
        return new Image(b);
    }

    public static Image createImage(String name) throws IOException {
        InputStream in = Image.class.getResourceAsStream(name);
        if (in == null) throw new IOException("Resource not found: " + name);
        try {
            BufferedImage src = ImageIO.read(in);
            if (src == null) throw new IOException("Unreadable image: " + name);
            BufferedImage b = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = b.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
            return new Image(b);
        } finally {
            in.close();
        }
    }

    /** Decode an image from memory (PNG/JPEG...), used for add-on skins that live inside a zip, not in the resources. */
    public static Image createImage(byte[] data, int off, int len) throws IOException {
        BufferedImage src = ImageIO.read(new java.io.ByteArrayInputStream(data, off, len));
        if (src == null) throw new IOException("Unreadable image data");
        BufferedImage b = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = b.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return new Image(b);
    }

    public static Image createImage(int w, int h) {
        return new Image(new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB));
    }

    public static Image createRGBImage(int[] rgb, int width, int height, boolean processAlpha) {
        if (rgb == null || width <= 0 || height <= 0 || (long)width * height > rgb.length)
            throw new IllegalArgumentException("Invalid RGB image data");
        BufferedImage image = new BufferedImage(width, height,
                processAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, rgb, 0, width);
        return new Image(image);
    }

    /** Only TRANS_NONE and TRANS_MIRROR are supported (enough for the desktop port). */
    public static Image createImage(Image src, int x, int y, int w, int h, int transform) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = b.createGraphics();
        if (transform == TRANS_MIRROR) g.drawImage(src.buf, w, 0, 0, h, x, y, x + w, y + h, null);
        else g.drawImage(src.buf, 0, 0, w, h, x, y, x + w, y + h, null);
        g.dispose();
        return new Image(b);
    }

    public int getWidth() { return buf.getWidth(); }
    public int getHeight() { return buf.getHeight(); }
    public void getRGB(int[] out, int off, int scan, int x, int y, int w, int h) { buf.getRGB(x, y, w, h, out, off, scan); }
}
