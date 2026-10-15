package com.nokia.mid.appl.rally3d;

import java.io.IOException;
import java.util.Random;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.TextBox;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.midlet.MIDlet;

abstract class BaseCanvas
extends GameCanvas
implements CommandListener {
    TheMidlet midlet;
    Display display;
    Font fontBold;
    int fontBoldHeight;
    Font fontSmall;
    int fontSmallHeight;
    int width;
    int height;
    static Random random;
    long now;
    long lastFrameTime;
    boolean[] keys = new boolean[20];
    private final java.util.concurrent.atomic.AtomicInteger controllerKeyMask = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger controllerMenuEvents = new java.util.concurrent.atomic.AtomicInteger();
    private volatile boolean spaceDown;
    volatile boolean tabDown;
    private volatile boolean controllerTabDown;
    int prevKeyMask;
    long fpsWindowStart;
    long fps;
    int fpsFrameCount;
    TextBox textBox;
    Command okCommand;
    Command cancelCommand;
    volatile int textBoxResult;

    public BaseCanvas(TheMidlet theMidlet) {
        super(false);
        this.setFullScreenMode(true);
        this.midlet = theMidlet;
    }

    void start() { /*T*/ final long $t = rally3d.core.Trace.in(3); try {
        this.width = this.getWidth();
        this.height = this.getHeight();
        random = new Random();
        this.setTargetFps(10);
        this.display = Display.getDisplay((MIDlet)this.midlet);
     } finally { rally3d.core.Trace.out(3, $t); } }

    void sleep(int n) {
        try {
            Thread.sleep(n);
        }
        catch (InterruptedException interruptedException) {
            // empty catch block
        }
    }

    long currentTime() {
        return System.nanoTime() / 1000000L;
    }

    void initFonts() { /*T*/ final long $t = rally3d.core.Trace.in(4); try {
        this.fontBold = Font.getFont((int)0, (int)1, (int)0);
        this.fontSmall = Font.getFont((int)0, (int)0, (int)8);
        this.fontBoldHeight = this.fontBold.getHeight();
        this.fontSmallHeight = this.fontSmall.getHeight();
     } finally { rally3d.core.Trace.out(4, $t); } }

    void resetTimer() {
        this.lastFrameTime = System.currentTimeMillis();
        this.now = System.currentTimeMillis();
    }

    /** When true (during a race) the frame rate follows the user's FPS setting; menus target 60 FPS. */
    boolean highFps;
    private long frameTargetNanos;
    private final java.util.IdentityHashMap<Image, Image> stretched = new java.util.IdentityHashMap<Image, Image>();
    private final java.util.IdentityHashMap<Image, Image> mirrors = new java.util.IdentityHashMap<Image, Image>();

    /** Frame limiter. Desktop port: nanosecond pacing, 0 = unlimited. */
    void waitFrame() { /*T*/ final long $t = rally3d.core.Trace.in(5); try {
        long pacingStarted = System.nanoTime();
        long interval = this.highFps ? rally3d.desktop.Platform.targetFrameNanos() : 16666667L;
        long t = System.nanoTime();
        if (interval > 0L) {
            long target = this.frameTargetNanos + interval;
            if (t - target > interval) {
                target = t;   // we are running late: do not try to catch up
            }
            while ((t = System.nanoTime()) < target) {
                if (target - t > 2000000L) {
                    try {
                        Thread.sleep(1L);
                    }
                    catch (InterruptedException interruptedException) {
                        // ignore
                    }
                } else {
                    Thread.yield();
                }
            }
            this.frameTargetNanos = target;
        } else {
            this.frameTargetNanos = t;
        }
        this.now = System.currentTimeMillis();
        this.lastFrameTime = this.now;
        rally3d.desktop.Platform.recordPerformance(7, System.nanoTime() - pacingStarted);
     } finally { rally3d.core.Trace.out(5, $t); } }

    /** FPS assumed until the first real measurement is available. */
    int initialFps() {
        long ns = rally3d.desktop.Platform.targetFrameNanos();
        return ns > 0L ? (int)(1000000000L / ns) : 240;
    }

    /** Frame-rate independent smoothing: same lag in real time as a per-frame factor 'a' at 15 FPS. */
    float smooth(float a) {
        double f = this.fps < 1L ? 1.0 : (double)this.fps;
        return (float)(1.0 - Math.pow(1.0 - (double)a, 15.0 / f));
    }

    // ---- seamless checker fill: replaces the old black / mirrored side bars. Pixel-exact integer grid, pre-rendered once per size (no tile seams, 1 drawImage per frame) ----
    private static final int CK = 16;
    private Image checkerBg;

    private static int shade(int rgb, int f) {
        return 0xFF000000 | ((((rgb >> 16) & 255) * f >> 8) << 16) | ((((rgb >> 8) & 255) * f >> 8) << 8) | ((rgb & 255) * f >> 8);
    }

    /** 256 in the centre, smooth fall-off to ~41 at the edge; d = 0..1. */
    private static int vignette(float d) {
        float t = Math.max(0f, Math.min(1f, (d - 0.35f) / 0.65f));
        t = t * t * (3f - 2f * t);
        return (int)(256f - 215f * t);
    }

    private Image checkerBackground() { /*T*/ final long $t = rally3d.core.Trace.in(569); try {
        int W = Math.max(1, this.width), H = Math.max(1, this.height);
        if (this.checkerBg != null && this.checkerBg.getWidth() == W && this.checkerBg.getHeight() == H) return this.checkerBg;
        final int light = 0xFF1E80F8, dark = 0xFF0354BA;
        int[] px = new int[W * H];
        float hw = W * 0.5f, hh = H * 0.5f, norm = (float)Math.sqrt(hw * hw + hh * hh);
        for (int y = 0; y < H; y++) {
            int cy = Math.floorDiv(y - H / 2, CK);
            float dy = y - hh;
            for (int x = 0; x < W; x++) {
                int cx = Math.floorDiv(x - W / 2, CK);
                float dx = x - hw;
                float d = (float)Math.sqrt(dx * dx + dy * dy) / norm;
                px[y * W + x] = shade(((cx + cy) & 1) == 0 ? light : dark, vignette(d));
            }
        }
        this.checkerBg = Image.createRGBImage(px, W, H, false);
        return this.checkerBg;
     } finally { rally3d.core.Trace.out(569, $t); } }

    /** Fills a rectangle with the checker background (clip is saved and restored). */
    void fillChecker(Graphics graphics, int x, int y, int w, int h) { /*T*/ final long $t = rally3d.core.Trace.in(570); try {
        if (w <= 0 || h <= 0) return;
        int cx = graphics.getClipX(), cy = graphics.getClipY(), cw = graphics.getClipWidth(), ch = graphics.getClipHeight();
        graphics.setClip(x, y, w, h);
        graphics.drawImage(this.checkerBackground(), 0, 0, 0);
        graphics.setClip(cx, cy, cw, ch);
     } finally { rally3d.core.Trace.out(570, $t); } }

    /** Header/footer bar stretched to the screen width: same rows/colours as the source bar, exact 16 px cells, centre-bright vignette. */
    private Image wideBar(Image src) { /*T*/ final long $t = rally3d.core.Trace.in(571); try {
        int W = this.width, w = src.getWidth(), h = src.getHeight();
        Image cached = this.mirrors.get(src);
        if (cached != null && cached.getWidth() == W) return cached;
        int[] col = new int[w * h];
        src.getRGB(col, 0, w, 0, 0, w, h);
        int xa = w / 2 - 3, xb = w / 2 + 12;
        int[] px = new int[W * h];
        for (int y = 0; y < h; y++) {
            int ca = col[y * w + xa], cb = col[y * w + xb];
            for (int x = 0; x < W; x++) {
                int cx = Math.floorDiv(x - W / 2, CK);
                float d = Math.abs(x - W * 0.5f) / (W * 0.5f);
                px[y * W + x] = shade((cx & 1) == 0 ? ca : cb, vignette(d));
            }
        }
        Image bar = Image.createRGBImage(px, W, h, false);
        this.mirrors.put(src, bar);
        return bar;
     } finally { rally3d.core.Trace.out(571, $t); } }

    /** Draws an image centred horizontally at its native size (never stretched). Bars (<=100 px high) are widened with a seamless checker; the sides of the big title picture get the checker background. */
    void drawWide(Graphics graphics, Image image, int y) { /*T*/ final long $t = rally3d.core.Trace.in(6); try {
        if (image == null) return;
        int w = image.getWidth();
        int x0 = (this.width - w) / 2;
        if (x0 > 0 && image.getHeight() <= 100) {
            graphics.drawImage(this.wideBar(image), 0, y, 0);
            return;
        }
        graphics.drawImage(image, x0, y, 0);
        if (x0 > 0) {
            this.fillChecker(graphics, 0, y, x0, image.getHeight());
            this.fillChecker(graphics, x0 + w, y, this.width - x0 - w, image.getHeight());
        }
     } finally { rally3d.core.Trace.out(6, $t); } }

    int readKeys() { /*T*/ final long $t = rally3d.core.Trace.in(7); try {
        int n = 0;
        int padMask = this.controllerKeyMask.get();
        if (this.key(1, padMask) || this.key(2, padMask) || this.key(3, padMask) || this.key(16, padMask)) {
            n |= 2;
        }
        if (this.key(1, padMask) || this.key(4, padMask) || this.key(7, padMask) || this.key(14, padMask)) {
            n |= 4;
        }
        if (this.key(3, padMask) || this.key(6, padMask) || this.key(9, padMask) || this.key(15, padMask)) {
            n |= 0x20;
        }
        if (this.key(7, padMask) || this.key(8, padMask) || this.key(9, padMask) || this.key(17, padMask)) {
            n |= 0x40;
        }
        if (this.key(5, padMask) || this.key(10, padMask) || this.key(12, padMask) || this.key(18, padMask)) {
            n |= 0x100;
        }
        if (this.key(0, padMask)) {
            n |= 0x400;
        }
        if (this.key(11, padMask) || this.key(13, padMask) || this.key(19, padMask)) {
            n |= 0x200;
        }
        return n;
     } finally { rally3d.core.Trace.out(7, $t); } }

    private boolean key(int index, int padMask) { return this.keys[index] || (padMask & (1 << index)) != 0; }

    @Override
    protected void controllerKeyChanged(int keyCode, boolean pressed) { /*T*/ final long $t = rally3d.core.Trace.in(8); try {
        if (keyCode == -13) { this.controllerTabDown = pressed; return; }
        if (pressed) {
            int menuEvent = controllerMenuEvent(keyCode);
            if (menuEvent != 0) { int old; do { old = this.controllerMenuEvents.get(); } while (!this.controllerMenuEvents.compareAndSet(old, old | menuEvent)); }
        }
        int index = controllerKeyIndex(keyCode);
        if (index >= 0 && index < this.keys.length) {
            int bit = 1 << index;
            int old; int upd;
            do { old = this.controllerKeyMask.get(); upd = pressed ? old | bit : old & ~bit; } while (!this.controllerKeyMask.compareAndSet(old, upd));
        }
     } finally { rally3d.core.Trace.out(8, $t); } }

    private int controllerMenuEvent(int keyCode) { /*T*/ final long $t = rally3d.core.Trace.in(9); try {
        switch (keyCode) {
            case -1: return 0x2;   // up
            case -2: return 0x40;  // down
            case -3: return 0x4;   // left
            case -4: return 0x20;  // right
            case -5: case -6: return 0x100; // confirm
            case -7: return 0x200; // back
            default: return 0;
        }
     } finally { rally3d.core.Trace.out(9, $t); } }

    private int controllerKeyIndex(int n) { /*T*/ final long $t = rally3d.core.Trace.in(10); try {
        switch (n) {
            case -1: return 16; // up
            case -2: return 17; // down
            case -3: return 14; // left
            case -4: return 15; // right
            case -5: case -6: return 12; // confirm
            case -7: return 13; // back
            case -11: return 19; // menu / start
            default: return n >= 48 && n <= 57 ? n - 48 : -1;
        }
     } finally { rally3d.core.Trace.out(10, $t); } }

    void setKey(int n, boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(11); try {
        boolean bl2 = false;
        switch (n) {
            case -13: {
                this.tabDown = bl;
                return;
            }
            case 53: {
                bl2 = true;
            }
            case 48: 
            case 49: 
            case 50: 
            case 51: 
            case 52: 
            case 54: 
            case 55: 
            case 56: 
            case 57: {
                this.keys[n - 48] = bl;
                break;
            }
            case -6: 
            case 6: {
                this.keys[12] = bl;
                bl2 = true;
                break;
            }
            case -7: 
            case 7: {
                this.keys[13] = bl;
                return;
            }
            case 42: {
                this.keys[10] = bl;
                bl2 = true;
                break;
            }
            case 35: {
                this.keys[11] = bl;
                break;
            }
            case -11: {
                this.keys[19] = bl;
                break;
            }
            case -12: {
                this.keys[17] = bl;
                this.spaceDown = bl;
                bl2 = true;
                break;
            }
        }
        if (!bl2) {
            switch (this.getGameAction(n)) {
                case 2: {
                    this.keys[14] = bl;
                    break;
                }
                case 5: {
                    this.keys[15] = bl;
                    break;
                }
                case 1: {
                    this.keys[16] = bl;
                    break;
                }
                case 6: {
                    this.keys[17] = bl;
                    break;
                }
                case 8: {
                    this.keys[18] = bl;
                    bl2 = true;
                }
            }
        }
     } finally { rally3d.core.Trace.out(11, $t); } }

    boolean isTabDown() { return this.tabDown || this.controllerTabDown; }
    boolean isSpaceDown() { return this.spaceDown; }

    void clearKeys() { /*T*/ final long $t = rally3d.core.Trace.in(12); try {
        for (int i = 0; i < this.keys.length; ++i) {
            this.keys[i] = false;
        }
        this.controllerKeyMask.set(0);
        this.controllerMenuEvents.set(0);
        this.spaceDown = false;
     } finally { rally3d.core.Trace.out(12, $t); } }

    public void keyPressed(int n) {
        this.setKey(n, true);
    }

    public void keyReleased(int n) {
        this.setKey(n, false);
    }

    void snapshotKeys() {
        this.prevKeyMask = this.readKeys();
    }

    int pollNewKeys() { /*T*/ final long $t = rally3d.core.Trace.in(13); try {
        int n = 0;
        int n2 = this.readKeys();
        if (this.prevKeyMask == 0 && n2 != 0) {
            n = n2;
        }
        this.prevKeyMask = n2;
        return n | this.controllerMenuEvents.getAndSet(0);
     } finally { rally3d.core.Trace.out(13, $t); } }

    int pollPointerEvents(int[] coordinates) { return this.consumePointerEvents(coordinates); }

    void setTargetFps(int n) {
        this.fps = n;
        this.fpsFrameCount = 0;
        this.fpsWindowStart = System.nanoTime();
    }

    void updateFps() { /*T*/ final long $t = rally3d.core.Trace.in(14); try {
        ++this.fpsFrameCount;
        int window = this.fps > 200L ? (int)(this.fps / 20L) : 10;
        if (this.fpsFrameCount >= window) {
            long l = System.nanoTime();
            long dt = l - this.fpsWindowStart;
            if (dt < 1L) {
                dt = 1L;
            }
            long f = (long)this.fpsFrameCount * 1000000000L / dt;
            this.fps = f < 1L ? 1L : f;
            this.fpsFrameCount = 0;
            this.fpsWindowStart = l;
        }
     } finally { rally3d.core.Trace.out(14, $t); } }

    static int randomPositive() {
        int n = random.nextInt();
        if (n >= 0) {
            return n;
        }
        return -n;
    }

    static Image loadImage(String string) { /*T*/ final long $t = rally3d.core.Trace.in(15); try {
        Image image;
        try {
            image = Image.createImage((String)string);
        }
        catch (IOException iOException) { rally3d.core.RLog.caught("BaseCanvas.loadImage", iOException);
            System.out.println("ErrorLoading: " + string + " reason: " + iOException);
            image = Image.createImage((int)1, (int)1);
        }
        return image;
     } finally { rally3d.core.Trace.out(15, $t); } }

    int drawText(Graphics graphics, int n, int n2, String string, int n3, int n4) { /*T*/ final long $t = rally3d.core.Trace.in(16); try {
        if ((n4 & 4) != 0) {
            n = (this.width - graphics.getFont().stringWidth(string)) / 2 + n;
        }
        if ((n4 & 8) != 0) {
            n = this.width - graphics.getFont().stringWidth(string) - n - 1;
        }
        if ((n4 & 3) != 0) {
            graphics.setColor(0);
            if ((n4 & 2) != 0) {
                graphics.drawString(string, n - 1, n2 - 1, 0x10 | 4);
                graphics.drawString(string, n, n2 - 1, 0x10 | 4);
                graphics.drawString(string, n + 1, n2 - 1, 0x10 | 4);
                graphics.drawString(string, n - 1, n2, 0x10 | 4);
                graphics.drawString(string, n + 1, n2, 0x10 | 4);
                graphics.drawString(string, n - 1, n2 + 1, 0x10 | 4);
                graphics.drawString(string, n, n2 + 1, 0x10 | 4);
            }
            graphics.drawString(string, n + 1, n2 + 1, 0x10 | 4);
        }
        graphics.setColor(n3);
        graphics.drawString(string, n, n2, 0x10 | 4);
        return 1;
     } finally { rally3d.core.Trace.out(16, $t); } }

    String showTextBox(String string, String string2, int n) { /*T*/ final long $t = rally3d.core.Trace.in(17); try {
        final java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<String> value = new java.util.concurrent.atomic.AtomicReference<String>(string2);
        rally3d.desktop.Platform.inputDialog(string, string2, n, new java.util.function.Consumer<String>() {
            public void accept(String entered) {
                if (entered != null) value.set(entered);
                completed.countDown();
            }
        });
        try {
            completed.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        this.clearKeys();
        return value.get();
     } finally { rally3d.core.Trace.out(17, $t); } }

    public void commandAction(Command command, Displayable displayable) { /*T*/ final long $t = rally3d.core.Trace.in(18); try {
        if (command == this.okCommand) {
            this.textBoxResult = 1;
        }
        if (command == this.cancelCommand) {
            this.textBoxResult = -1;
        }
     } finally { rally3d.core.Trace.out(18, $t); } }

    void vibrate(int n) {
        this.display.vibrate(n);
    }

    /** Short pulse with an amplitude 1..255 (impact strength); no-op where unsupported. */
    void vibrate(int ms, int amplitude) {
        rally3d.desktop.Platform.vibrate(ms, amplitude);
    }
}
