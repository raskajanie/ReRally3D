package rally3d.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Shared logger (PC + Android). PC: stdout/stderr go to the F1 console and rally3d-0.log. Android: Platform installs a Sink (logcat + file + on-screen console). */
public final class RLog {
    private RLog() {}

    public static final int INFO = 0, WARN = 1, ERROR = 2;

    public interface Sink { void write(int level, String line); }

    private static volatile Sink sink;
    private static final ArrayDeque<String> RECENT = new ArrayDeque<String>();
    private static final int MAX_RECENT = 600;
    private static int total;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS");

    public static void setSink(Sink s) { sink = s; }

    public static void i(String tag, String msg) { log(INFO, tag, msg, null); }
    public static void w(String tag, String msg) { log(WARN, tag, msg, null); }
    public static void w(String tag, String msg, Throwable t) { log(WARN, tag, msg, t); }
    public static void e(String tag, String msg) { log(ERROR, tag, msg, null); }
    public static void e(String tag, String msg, Throwable t) { log(ERROR, tag, msg, t); }

    public static synchronized void log(int level, String tag, String msg, Throwable t) {
        StringBuilder sb = new StringBuilder(128);
        synchronized (TIME) { sb.append(TIME.format(new Date())); }
        sb.append(level == ERROR ? " E " : level == WARN ? " W " : " I ");
        sb.append('[').append(tag).append("] ").append(msg);
        sb.append("  {").append(Thread.currentThread().getName()).append('}');
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append('\n').append(sw.toString().trim());
        }
        String line = sb.toString();
        RECENT.addLast(line);
        total++;
        while (RECENT.size() > MAX_RECENT) RECENT.removeFirst();
        Sink s = sink;
        if (s != null) {
            try { s.write(level, line); } catch (Throwable ignored) {}
        } else if (level == INFO) {
            System.out.println(line);
        } else {
            System.err.println(line);
        }
    }

    /** Total lines ever logged; lets the overlay cache wrapped rows until something new arrives. */
    public static synchronized int count() { return total; }

    /** Last n lines (oldest first), for the on-screen console. */
    public static synchronized List<String> recent(int n) {
        List<String> all = new ArrayList<String>(RECENT);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    // ---- swallowed exceptions (every catch block calls this; duplicates are rate-limited) ----
    private static final java.util.HashMap<String, int[]> CAUGHT = new java.util.HashMap<String, int[]>();

    /** Logs an exception that the game handles by itself (catch block). First occurrence with stack, then every 50th. */
    public static void caught(String where, Throwable t) {
        if (t == null || t instanceof java.net.SocketTimeoutException) return;   // expected polling timeouts
        String key = where + "|" + t.getClass().getName() + "|" + t.getMessage();
        int n;
        synchronized (CAUGHT) {
            int[] c = CAUGHT.get(key);
            if (c == null) { if (CAUGHT.size() > 500) CAUGHT.clear(); c = new int[1]; CAUGHT.put(key, c); }
            n = ++c[0];
        }
        if (n == 1) log(WARN, "caught", where + ": " + t, t);
        else if (n % 50 == 0) log(WARN, "caught", where + ": " + t + "  (repeated " + n + " times)", null);
    }

    /** Thread dump of one thread, plus the instrumented call stack and last calls of the function tracer. */
    public static String threadReport(Thread th, int lastCalls) {
        StringBuilder sb = new StringBuilder();
        sb.append("thread ").append(th.getName()).append(" state=").append(th.getState());
        StackTraceElement[] st = th.getStackTrace();
        for (int i = 0; i < Math.min(st.length, 25); i++) sb.append("\n    at ").append(st[i]);
        sb.append("\n  traced call stack:").append(Trace.stack(th));
        sb.append("\n  last ").append(lastCalls).append(" traced calls:").append(Trace.recent(th, lastCalls));
        return sb.toString();
    }
}
