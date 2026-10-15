package rally3d.android;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ArrayBlockingQueue;
import rally3d.core.RLog;
import rally3d.core.Trace;

/**
 * Android log sink. Lines go to logcat (tag Rally3D) and are written by a background thread to
 * Android/data/<pkg>/files/rally3d.log (rotated at 2 MB, 3 files kept: .log, .log.1, .log.2).
 * ERROR lines (and anything queued behind them) are flushed immediately; the rest every ~300 ms, so logging never blocks the game.
 * Optional full function trace (Trace.full) goes to rally3d-trace.log (rotated at 8 MB, 1 old file).
 * System.out/err are captured too.
 */
public final class AndroidLog implements RLog.Sink {
    private static final long MAX_BYTES = 2_000_000L;
    private static final long MAX_TRACE_BYTES = 8_000_000L;
    public static File logFile, traceFile;
    private static AndroidLog instance;

    private final Writer main, trace;
    private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<String>(8192);
    private volatile int dropped;
    private final Object flushLock = new Object();
    private volatile boolean flushNow;

    private AndroidLog(File dir) {
        logFile = new File(dir, "rally3d.log");
        traceFile = new File(dir, "rally3d-trace.log");
        main = new Writer(logFile, MAX_BYTES, 2);
        trace = new Writer(traceFile, MAX_TRACE_BYTES, 1);
        Thread t = new Thread(new Runnable() { public void run() { pump(); } }, "log-writer");
        t.setDaemon(true);
        t.start();
    }

    /** File with rotation. Only used from the log-writer thread (and from flushBlocking under the same lock). */
    private static final class Writer {
        final File file; final long max; final int keep;
        FileWriter w; long size;
        Writer(File f, long max, int keep) { file = f; this.max = max; this.keep = keep; open(); }
        void open() {
            try { size = file.length(); w = new FileWriter(file, true); }
            catch (IOException e) { android.util.Log.e("Rally3D", "cannot open log file " + file, e); w = null; }
        }
        void write(String line) {
            if (w == null) return;
            try {
                w.write(line); w.write('\n');
                size += line.length() + 1;
                if (size > max) rotate();
            } catch (IOException ignored) {}
        }
        void flush() { try { if (w != null) w.flush(); } catch (IOException ignored) {} }
        void rotate() throws IOException {
            w.close();
            for (int i = keep; i >= 1; i--) {
                File from = i == 1 ? file : new File(file.getPath() + "." + (i - 1));
                File to = new File(file.getPath() + "." + i);
                to.delete();
                from.renameTo(to);
            }
            open();
        }
    }

    public void write(int level, String line) {
        if (level == RLog.ERROR) android.util.Log.e("Rally3D", line);
        else if (level == RLog.WARN) android.util.Log.w("Rally3D", line);
        else android.util.Log.i("Rally3D", line);
        if (!queue.offer(line)) dropped++;
        if (level == RLog.ERROR) { flushNow = true; synchronized (flushLock) { flushLock.notifyAll(); } }
    }

    private void pump() {
        StringBuilder batch = new StringBuilder();
        while (true) {
            try {
                String first = queue.poll(300, java.util.concurrent.TimeUnit.MILLISECONDS);
                int n = 0;
                String line = first;
                while (line != null && n < 2000) {
                    if (line.startsWith("\u0001T")) trace.write(line.substring(2)); else main.write(line);
                    n++;
                    line = queue.poll();
                }
                int d = dropped;
                if (d > 0) { dropped = 0; main.write("W [log] " + d + " log lines dropped (queue full)"); }
                main.flush(); trace.flush();
                flushNow = false;
            } catch (Throwable t) {
                android.util.Log.e("Rally3D", "log writer error", t);
            }
        }
    }

    /** Blocks until everything queued so far is on disk (used before the process may die: crash, freeze report, onDestroy). */
    public static void flushBlocking() {
        AndroidLog a = instance;
        if (a == null) return;
        long end = System.currentTimeMillis() + 1500;
        while (!a.queue.isEmpty() && System.currentTimeMillis() < end) {
            a.flushNow = true;
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
        try { Thread.sleep(40); } catch (InterruptedException ignored) {}
    }

    public static void setFullTrace(boolean on) {
        Trace.full = on;
        Trace.fullSink = on ? new Trace.FullSink() {
            public void line(String s) {
                AndroidLog a = instance;
                if (a != null && !a.queue.offer("\u0001T" + System.currentTimeMillis() % 100000000L + " {" + Thread.currentThread().getName() + "} " + s)) a.dropped++;
            }
        } : null;
        RLog.i("trace", "full function trace " + (on ? "ON -> " + traceFile : "OFF"));
    }

    public static void install(Context ctx) {
        File dir = ctx.getExternalFilesDir(null);   // Android/data/<pkg>/files - visible in file managers
        if (dir == null) dir = ctx.getFilesDir();
        dir.mkdirs();
        instance = new AndroidLog(dir);
        RLog.setSink(instance);
        rally3d.core.LogView.exportButton = true;   // the log file is in Android/data: offer EXPORT (system file picker) in the log overlay
        System.setOut(new PrintStream(new LineSink(RLog.INFO, "stdout"), true));
        System.setErr(new PrintStream(new LineSink(RLog.WARN, "stderr"), true));
        RLog.i("app", "==== Rally3D start ==== " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + "), log: " + logFile);
    }

    /** Turns anything printed to System.out/err into log lines. */
    private static final class LineSink extends OutputStream {
        private final int level;
        private final String tag;
        private final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        LineSink(int level, String tag) { this.level = level; this.tag = tag; }
        @Override public synchronized void write(int b) {
            if (b == '\n') flushLine();
            else if (b != '\r') buf.write(b);
        }
        private void flushLine() {
            if (buf.size() > 0) {
                String s;
                try { s = buf.toString("UTF-8"); } catch (Exception e) { s = buf.toString(); }
                RLog.log(level, tag, s, null);
            }
            buf.reset();
        }
    }
}
