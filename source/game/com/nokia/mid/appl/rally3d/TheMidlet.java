package com.nokia.mid.appl.rally3d;

import javax.microedition.midlet.MIDlet;

public class TheMidlet
extends MIDlet {
    RallyGame game = null;
    public boolean splashShowing = true;
    TitleScreen titleScreen = new TitleScreen(this);

    public static volatile TheMidlet instance;
    public static void saveNow() { /*T*/ final long $t = rally3d.core.Trace.in(234); try {
        TheMidlet m = instance; RallyGame g = m == null ? null : m.game;
        if (g != null && g.saveData != null) g.saveData.save();
     } finally { rally3d.core.Trace.out(234, $t); } }

    public TheMidlet() {
        instance = this;
        this.game = new RallyGame(this);
    }

    public void startApp() { /*T*/ final long $t = rally3d.core.Trace.in(147); try {
        if (this.splashShowing) {
            this.titleScreen.start();
            this.game.start();
            while (this.splashShowing) {
                try {
                    Thread.sleep(50L);
                }
                catch (InterruptedException interruptedException) { rally3d.core.RLog.caught("TheMidlet.sleep", interruptedException); }
            }
        } else {
            this.game.start();
        }
     } finally { rally3d.core.Trace.out(147, $t); } }

    public void pauseApp() { /*T*/ final long $t = rally3d.core.Trace.in(148); try {
        if (this.game != null) this.game.paused = true;
        if (this.game != null && this.game.saveData != null) {
            this.game.saveData.save();
        }
     } finally { rally3d.core.Trace.out(148, $t); } }

    public void destroyApp(boolean bl) {
        if (this.game != null && this.game.saveData != null) {
            this.game.saveData.save();
        }
        this.game = null;
    }

    public void exitApp() {
        this.destroyApp(false);
        this.notifyDestroyed();
    }
}

