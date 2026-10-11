package rally3d.desktop;

import com.nokia.mid.appl.rally3d.TheMidlet;
import javax.swing.SwingUtilities;

/** Desktop launcher: java -jar Rally3D.jar */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        LogConsole.install();
        LogConsole.info("startup", "Starting Rally 3D");
        rally3d.core.LogView.showLogButton = false;   // PC: log via F1 only
        Platform.load();
        LogConsole.info("startup", "Settings loaded; output " + Platform.displayWidth() + "x" + Platform.displayHeight()
                + ", internal render buffer " + Platform.renderWidth() + "x" + Platform.renderHeight());
        for (String a : args) {
            if (a.equals("--headless")) Platform.headless = true;
            else if (a.equals("--fullscreen")) Platform.fullscreen = true;
        }
        if (!Platform.headless) {
            System.setProperty("sun.java2d.opengl", "false");
            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { Platform.window = new GameWindow(); }
            });
        }
        LogConsole.info("startup", Platform.headless ? "Graphics window disabled (headless)" : "Window created; press F1 for the in-game log");
        final TheMidlet midlet = new TheMidlet();
        Thread t = new Thread(new Runnable() {
            public void run() { midlet.startApp(); }
        }, "midlet");
        t.start();
        LogConsole.info("startup", "Game thread started");
        Platform.runGamepadSupport();
    }
}
