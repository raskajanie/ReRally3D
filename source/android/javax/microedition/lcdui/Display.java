package javax.microedition.lcdui;

import javax.microedition.midlet.MIDlet;

public class Display {
    static final Display INSTANCE = new Display();
    static volatile Displayable current;

    public static Display getDisplay(MIDlet m) { return INSTANCE; }
    public Displayable getCurrent() { return current; }

    public void setCurrent(Displayable d) {
        Displayable old = current;
        if (old == d) return;
        if (old instanceof Canvas) ((Canvas) old).hideNotify();
        current = d;
        if (d instanceof Canvas) {
            rally3d.desktop.Platform.showCanvas((Canvas) d);
            ((Canvas) d).showNotify();
        } else if (d instanceof TextBox) {
            ((TextBox) d).runDialog();
        }
    }

    public boolean vibrate(int duration) { rally3d.desktop.Platform.vibrate(Math.min(duration, 150), 90); return true; }
}
