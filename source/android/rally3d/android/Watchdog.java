package rally3d.android;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.Map;
import rally3d.core.RLog;
import rally3d.core.Trace;

/**
 * Diagnostics thread: freeze detection (game loop + UI thread), frame hitches, periodic perf/memory summary,
 * last-input ring, crash report helper, previous-session exit reason.
 * Everything it finds goes through RLog (WARN/ERROR) so it shows up in the in-game log and rally3d.log.
 */
public final class Watchdog implements Runnable {
    private Watchdog() {}

    private static final long FREEZE_MS = 2500, UI_FREEZE_MS = 3500, REPORT_EVERY_MS = 5000, PERF_EVERY_MS = 30000, HITCH_MS = 200;
    private static volatile long frameBeat;           // nanoTime of the last presented frame
    private static volatile Thread beatThread;
    private static volatile boolean foreground;
    private static volatile long uiBeat = System.nanoTime();
    private static volatile boolean started;
    private static Handler ui;
    private static File marker;

    // frame stats for the perf summary (written by the game thread, read by the watchdog; races are harmless)
    private static volatile long fCount, fSumNs, fMaxNs, fHitches, lastPresent;
    private static final int KEEP = 48;
    private static final String[] inputs = new String[KEEP];
    private static int inputPos;

    public static void start(Context ctx) {
        if (started) return;
        started = true;
        ui = new Handler(Looper.getMainLooper());
        marker = new File(rally3d.desktop.Platform.dataDir(), "session.running");
        Thread t = new Thread(new Watchdog(), "watchdog");
        t.setDaemon(true);
        t.start();
        RLog.i("watchdog", "started (freeze>" + FREEZE_MS + "ms, ui freeze>" + UI_FREEZE_MS + "ms, hitch>" + HITCH_MS + "ms)");
    }

    // ---- hooks ----
    public static void setForeground(boolean fg) {
        foreground = fg;
        lastPresent = 0;
        frameBeat = System.nanoTime();
        uiBeat = System.nanoTime();
        RLog.i("lifecycle", "foreground=" + fg);
    }

    /** Called by GameView.present for every frame. */
    public static void frame() {
        long now = System.nanoTime();
        frameBeat = now;
        beatThread = Thread.currentThread();
        long lp = lastPresent;
        lastPresent = now;
        if (lp != 0) {
            long dt = now - lp;
            fCount++; fSumNs += dt;
            if (dt > fMaxNs) fMaxNs = dt;
            if (dt > HITCH_MS * 1000000L && foreground) {
                fHitches++;
                long ms = dt / 1000000L;
                if (ms < 1000) RLog.w("hitch", "frame took " + ms + " ms");
                else RLog.w("hitch", "frame took " + ms + " ms; last traced calls:" + Trace.recent(Thread.currentThread(), 12));
            }
        }
    }

    public static synchronized void input(String what) {
        inputs[inputPos++ % KEEP] = System.currentTimeMillis() % 100000000L + " " + what;
    }

    private static synchronized String lastInputs(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, inputPos - n); i < inputPos; i++) { String s = inputs[i % KEEP]; if (s != null) sb.append("\n    ").append(s); }
        return sb.length() == 0 ? "    (none)" : sb.toString();
    }

    // ---- session marker / previous exit ----
    public static void sessionStart(Context ctx) {
        try {
            File dir = rally3d.desktop.Platform.dataDir();
            marker = new File(dir, "session.running");
            if (marker.exists()) RLog.w("session", "previous session did not exit cleanly (crash, freeze kill, swipe-away, or Android closed it in the background - see exit reason below and the end of the previous log)");
            marker.createNewFile();
        } catch (Throwable t) { RLog.w("session", "marker failed", t); }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                java.util.List<android.app.ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(null, 0, 3);
                for (android.app.ApplicationExitInfo e : list) {
                    RLog.i("session", "previous process exit: " + reasonName(e.getReason()) + " (status " + e.getStatus() + ") at "
                            + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(e.getTimestamp()))
                            + ", importance " + e.getImportance() + ", pss " + e.getPss() + "KB, " + e.getDescription());
                    if (e.getReason() == android.app.ApplicationExitInfo.REASON_ANR || e.getReason() == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE
                            || e.getReason() == android.app.ApplicationExitInfo.REASON_LOW_MEMORY) {
                        RLog.w("session", "that exit was a PROBLEM: " + reasonName(e.getReason()));
                    }
                }
            }
        } catch (Throwable t) { RLog.w("session", "exit reasons unavailable", t); }
    }

    public static void sessionEnd() {
        try { if (marker != null) marker.delete(); } catch (Throwable ignored) {}
        RLog.i("session", "clean exit");
    }

    private static String reasonName(int r) {
        switch (r) {
            case 1: return "EXIT_SELF"; case 2: return "SIGNALED(killed)"; case 3: return "LOW_MEMORY(killed by system)";
            case 4: return "CRASH(java)"; case 5: return "CRASH_NATIVE"; case 6: return "ANR(app not responding)";
            case 7: return "INITIALIZATION_FAILURE"; case 8: return "PERMISSION_CHANGE"; case 9: return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED"; case 11: return "USER_STOPPED"; case 12: return "DEPENDENCY_DIED"; case 13: return "OTHER"; default: return "UNKNOWN(" + r + ")";
        }
    }

    /** Full crash report (java crash). Called from the uncaught-exception handler. */
    public static void crashReport(Thread t, Throwable e) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("CRASH report\n  memory: ").append(memory());
            sb.append("\n  last inputs:").append(lastInputs(16));
            sb.append("\n  crashing ").append(RLog.threadReport(t, 40));
            RLog.e("crash", sb.toString());
        } catch (Throwable ignored) {}
        AndroidLog.flushBlocking();
    }

    static String memory() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        return "java heap " + used + "/" + max + " MB, native " + (Debug.getNativeHeapAllocatedSize() >> 20) + " MB, threads " + Thread.activeCount();
    }

    // ---- watchdog loop ----
    private boolean frozen, uiFrozen;
    private long freezeStartNs, lastFreezeReport, lastUiReport, lastPerf = System.nanoTime(), lastHeapWarn;
    private int reports;

    public void run() {
        long lastPost = 0;
        while (true) {
            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            try { tick(); } catch (Throwable t) { RLog.w("watchdog", "tick failed", t); }
            long now = System.nanoTime();
            if (now - lastPost > 1_000_000_000L) {
                lastPost = now;
                ui.post(new Runnable() { public void run() { uiBeat = System.nanoTime(); } });
            }
        }
    }

    private void tick() {
        Trace.tick();
        long now = System.nanoTime();
        long stale = (now - frameBeat) / 1000000L;
        boolean armed = frameBeat != 0 && beatThread != null;
        if (armed && foreground && stale > FREEZE_MS) {
            if (!frozen) { frozen = true; freezeStartNs = frameBeat; reports = 0; freezeReport(stale); lastFreezeReport = now; }
            else if ((now - lastFreezeReport) / 1000000L > REPORT_EVERY_MS) { freezeReport(stale); lastFreezeReport = now; }
        } else if (frozen && stale < 1000) {
            frozen = false;
            RLog.w("freeze", "game loop recovered, it was stuck for ~" + ((now - freezeStartNs) / 1000000L) + " ms");
        }
        long uiStale = (now - uiBeat) / 1000000L;
        if (foreground && uiStale > UI_FREEZE_MS) {
            if (!uiFrozen || (now - lastUiReport) / 1000000L > REPORT_EVERY_MS) {
                uiFrozen = true; lastUiReport = now;
                Thread main = Looper.getMainLooper().getThread();
                RLog.e("freeze", "UI (main) thread blocked for " + uiStale + " ms - Android may show ANR. " + RLog.threadReport(main, 0));
                AndroidLog.flushBlocking();
            }
        } else if (uiFrozen && uiStale < 1500) {
            uiFrozen = false;
            RLog.w("freeze", "UI thread recovered");
        }
        Runtime rt = Runtime.getRuntime();
        long usedPct = (rt.totalMemory() - rt.freeMemory()) * 100 / Math.max(1, rt.maxMemory());
        if (usedPct > 85 && (now - lastHeapWarn) / 1000000L > 15000) {
            lastHeapWarn = now;
            RLog.w("memory", "java heap almost full (" + usedPct + "%): " + memory());
        }
        if ((now - lastPerf) / 1000000L > PERF_EVERY_MS) {
            long dt = now - lastPerf; lastPerf = now;
            long c = fCount, sum = fSumNs, mx = fMaxNs, hit = fHitches;
            fCount = 0; fSumNs = 0; fMaxNs = 0; fHitches = 0;
            if (foreground && c > 0) {
                RLog.i("perf", String.format(java.util.Locale.US, "%.1f fps avg, worst frame %d ms, %d hitches (>%d ms) in %d s | %s | top functions:%s",
                        c * 1e9 / Math.max(1, sum), mx / 1000000L, hit, HITCH_MS, dt / 1000000000L, memory(), Trace.topSummary(8)));
            }
            Trace.resetStats();
        }
    }

    private void freezeReport(long staleMs) {
        reports++;
        StringBuilder sb = new StringBuilder();
        sb.append("GAME LOOP FROZEN: no frame for ").append(staleMs).append(" ms (report #").append(reports).append(")");
        sb.append("\n  memory: ").append(memory());
        sb.append("\n  last inputs:").append(lastInputs(12));
        Thread bt = beatThread;
        if (bt != null) sb.append("\n  last frame thread: ").append(RLog.threadReport(bt, 25));
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        sb.append("\n  other threads:");
        for (Map.Entry<Thread, StackTraceElement[]> en : all.entrySet()) {
            Thread th = en.getKey();
            if (th == bt || th == Thread.currentThread() || th.getName().startsWith("log-writer")) continue;
            StackTraceElement[] st = en.getValue();
            if (st.length == 0) continue;
            sb.append("\n    [").append(th.getName()).append(" ").append(th.getState()).append("]");
            for (int i = 0; i < Math.min(5, st.length); i++) sb.append("\n        at ").append(st[i]);
        }
        RLog.e("freeze", sb.toString());
        AndroidLog.flushBlocking();
    }
}
