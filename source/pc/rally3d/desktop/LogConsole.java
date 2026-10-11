package rally3d.desktop;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import rally3d.core.RLog;

/** Desktop logging backend: everything goes through RLog (shown by the in-game overlay, F1) and is mirrored to rotating rally3d-N.log files and the real console. No Swing windows. */
public final class LogConsole {
    private static final Logger FILE = Logger.getLogger("rally3d.file");
    private static volatile File activeLogFile;
    private static boolean installed;

    private LogConsole() {}

    /** Install early so startup messages and uncaught failures are not lost. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        final PrintStream realOut = System.out, realErr = System.err;
        FILE.setUseParentHandlers(false);
        FILE.setLevel(Level.ALL);
        try {
            File dir = logDirectory();
            activeLogFile = new File(dir, "rally3d-0.log");
            FileHandler file = new FileHandler(new File(dir, "rally3d-%g.log").getAbsolutePath(), 5 * 1024 * 1024, 3, true);
            file.setEncoding("UTF-8");
            file.setFormatter(new java.util.logging.Formatter() {
                private final java.time.format.DateTimeFormatter date =
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd ", java.util.Locale.ENGLISH)
                                .withZone(java.time.ZoneId.systemDefault());
                @Override public String format(LogRecord record) {
                    return date.format(java.time.Instant.ofEpochMilli(record.getMillis())) + record.getMessage() + '\n';
                }
            });
            file.setLevel(Level.ALL);
            FILE.addHandler(file);
        } catch (Exception e) {
            activeLogFile = null;
            realErr.println("Could not enable file logging: " + e);
        }
        RLog.setSink((level, line) -> {
            (level == RLog.INFO ? realOut : realErr).println(line);
            FILE.log(level == RLog.ERROR ? Level.SEVERE : level == RLog.WARN ? Level.WARNING : Level.INFO, line);
        });
        System.setOut(new PrintStream(new LineOutputStream(RLog.INFO), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new LineOutputStream(RLog.WARN), true, StandardCharsets.UTF_8));
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                RLog.e("uncaught", "Uncaught exception on thread " + thread.getName(), error));
        info("log", "Logging started; Java " + System.getProperty("java.version") + ", " + System.getProperty("os.name")
                + ", CPU threads=" + Runtime.getRuntime().availableProcessors());
        info("log", activeLogFile == null ? "File logging unavailable" : "Log file: " + activeLogFile.getAbsolutePath());
    }

    private static File logDirectory() {
        String override = System.getProperty("rally3d.logDir");
        File dir;
        if (override != null && !override.trim().isEmpty()) dir = new File(override);
        else {
            try {
                File launched = new File(LogConsole.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                dir = launched.isDirectory() ? launched : launched.getAbsoluteFile().getParentFile();
            } catch (Exception e) { dir = new File(System.getProperty("user.dir")); }
            if (dir == null) dir = new File(System.getProperty("user.dir"));
        }
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create " + dir);
        return dir;
    }

    public static void info(String source, String message) { RLog.i(source, message); }
    public static void warning(String source, String message, Throwable error) { RLog.w(source, message, error); }
    public static void error(String source, String message, Throwable error) { RLog.e(source, message, error); }

    /** F1 / LOG button: show or hide the in-game overlay. */
    public static void toggle() { RLog.i("log", "in-game log " + (rally3d.core.LogView.visible ? "closed" : "opened")); rally3d.core.LogView.toggle(); }

    private static final class LineOutputStream extends OutputStream {
        private final int level;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream(256);
        LineOutputStream(int level) { this.level = level; }
        @Override public synchronized void write(int b) {
            if (b == '\n') emit(); else if (b != '\r') line.write(b);
        }
        @Override public synchronized void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) write(b[i] & 0xff);
        }
        @Override public synchronized void flush() { if (line.size() > 0) emit(); }
        private void emit() {
            String message = line.toString(StandardCharsets.UTF_8);
            line.reset();
            RLog.log(level, "stdout", message, null);
        }
    }
}
