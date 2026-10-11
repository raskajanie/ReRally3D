package javax.microedition.lcdui.game;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import rally3d.desktop.Platform;

public abstract class GameCanvas extends Canvas {
    private Graphics offscreen;

    protected GameCanvas(boolean suppressKeyEvents) { Platform.register(this); }

    protected synchronized Graphics getGraphics() {
        if (offscreen == null) offscreen = Platform.newGraphics();
        return offscreen;
    }

    /** Called by Platform when the render resolution changes. */
    public synchronized void rebuildBuffer() { if (offscreen != null) Platform.resizeGraphics(offscreen); }

    public void flushGraphics() {
        if (isShown() && offscreen != null) Platform.present(offscreen);
    }

    public void flushGraphics(int x, int y, int w, int h) { flushGraphics(); }

    public void paint(Graphics g) {}
}
