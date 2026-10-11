package javax.microedition.midlet;

/** Desktop MIDlet base: lifecycle is driven by rally3d.desktop.Main. */
public abstract class MIDlet {
    protected MIDlet() {}
    protected abstract void startApp() throws MIDletStateChangeException;
    protected abstract void pauseApp();
    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;

    public final void notifyDestroyed() { rally3d.desktop.Platform.exit(); }
    public final void notifyPaused() {}
    public final void resumeRequest() {}
    public final String getAppProperty(String key) { return rally3d.desktop.Platform.manifestProperty(key); }
}
