package com.nokia.mid.appl.rally3d;

import java.util.Timer;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

class TitleScreen
extends BaseCanvas
implements Runnable {
    Thread thread;
    boolean hidden;
    Image titleImage = null;
    Image titleUpImage = null;
    Image titleDownImage = null;
    Image titleTextImage = null;
    int titleX;
    int titleY;
    Graphics screenGraphics = this.getGraphics();
    Timer timer = null;

    protected void hideNotify() {
        this.hidden = true;
    }

    protected void showNotify() { /*T*/ final long $t = rally3d.core.Trace.in(149); try {
        this.hidden = false;
        this.setFullScreenMode(true);
        this.screenGraphics = this.getGraphics();
        this.drawTitle();
     } finally { rally3d.core.Trace.out(149, $t); } }

    public TitleScreen(TheMidlet theMidlet) {
        super(theMidlet);
    }

    void start() { /*T*/ final long $t = rally3d.core.Trace.in(150); try {
        if (this.thread == null) {
            super.start();
            this.display.setCurrent((Displayable)this);
            this.thread = new Thread(this);
            this.thread.start();
        }
     } finally { rally3d.core.Trace.out(150, $t); } }

    void drawTitle() { /*T*/ final long $t = rally3d.core.Trace.in(151); try {
        this.fillChecker(this.screenGraphics, 0, 0, this.width, this.height);
        if (this.titleImage == null || this.titleTextImage == null) {
            this.flushGraphics();
            return;
        }
        this.drawWide(this.screenGraphics, this.titleImage, this.titleY);
        this.drawWide(this.screenGraphics, this.titleUpImage, this.titleY);
        this.screenGraphics.drawImage(this.titleTextImage, (this.width - this.titleTextImage.getWidth()) / 2, (this.titleUpImage.getHeight() - this.titleTextImage.getHeight()) / 2, 0);
        this.flushGraphics();
     } finally { rally3d.core.Trace.out(151, $t); } }

    void loadImages() { /*T*/ final long $t = rally3d.core.Trace.in(152); try {
        this.titleImage = TitleScreen.loadImage("/title/title.png");
        this.titleUpImage = TitleScreen.loadImage("/title/title_up.png");
        this.titleDownImage = TitleScreen.loadImage("/title/title_down.png");
        this.titleTextImage = TitleScreen.loadImage("/title/title_text.png");
        this.titleX = (this.width - this.titleImage.getWidth()) / 2;
        this.titleY = (this.height - this.titleImage.getHeight()) / 2;
        this.drawTitle();
     } finally { rally3d.core.Trace.out(152, $t); } }

    public void run() { /*T*/ final long $t = rally3d.core.Trace.in(153); try {
        boolean bl = false;
        int n = 0;
        int[] pointer = new int[4];
        this.loadImages();
        if (!rally3d.desktop.Platform.areUiAnimationsEnabled()) {
            this.midlet.splashShowing = false;
            this.clearKeys();
            return;
        }
        long l = this.currentTime() + 3000L;
        while (this.currentTime() < l) {
            this.repaint();
            this.sleep(50);
            n = this.pollNewKeys();
            if ((this.pollPointerEvents(pointer) & 2) != 0) n |= 0x100;
            if (n != 0) break;
        }
        this.midlet.splashShowing = false;
        if (0 != (n & 0x200)) {
            this.midlet.exitApp();
            return;
        }
        this.clearKeys();
        this.titleImage = null;
        this.titleUpImage = null;
        this.titleDownImage = null;
        this.titleTextImage = null;
     } finally { rally3d.core.Trace.out(153, $t); } }
}
