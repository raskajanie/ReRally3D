package rally3d.core;

/**
 * Function tracer / flight recorder (shared PC + Android).
 * Every instrumented function calls Trace.in(id) on entry and Trace.out(id, t) on exit (try/finally, see tools/instrument.py).
 * Cost is a few array writes; nothing is written to disk unless "full trace" is enabled, an error/freeze/crash happens, or the periodic
 * perf summary runs. The last RING calls of every thread are kept in memory and dumped on crash / freeze / error.
 * Functions called more than HOT_PER_SEC times per second are auto-muted (counted only) so tracing never slows the game down.
 */
public final class Trace {
    private Trace() {}

    public interface FullSink { void line(String s); }

    private static final int RING = 256;
    private static final int HOT_PER_SEC = 1500;
    private static final int MAX_STACK = 64;

    /** Master switch. Off by default: instrumentation on every call costs FPS and allocates TS per thread. Enable: -Drally3d.trace=true */
    public static final boolean ON = Boolean.getBoolean("rally3d.trace");
    public static volatile boolean full;                    // write every call to the trace file (opt-in, heavy)
    public static volatile FullSink fullSink;

    private static final int N = FnTable.NAMES.length;
    private static final int[] calls = new int[N + 1];
    private static final long[] totalNs = new long[N + 1];
    private static final long[] maxNs = new long[N + 1];
    private static final boolean[] muted = new boolean[N + 1];
    private static final int[] windowCalls = new int[N + 1];
    private static long windowStart = System.nanoTime();

    /** Per-thread recorder. */
    public static final class TS {
        public final Thread thread = Thread.currentThread();
        final int[] ring = new int[RING];                   // id+1 for enter, -(id+1) for exit
        final long[] ringT = new long[RING];
        volatile int pos;
        final int[] stack = new int[MAX_STACK];
        final long[] stackT = new long[MAX_STACK];
        volatile int depth;
    }

    private static final ThreadLocal<TS> LOCAL = new ThreadLocal<TS>() {
        @Override protected TS initialValue() { TS t = new TS(); synchronized (ALL) { ALL.add(t); } return t; }
    };
    private static final java.util.ArrayList<TS> ALL = new java.util.ArrayList<TS>();

    public static long in(int id) {
        if (!ON) return 0L;
        calls[id]++;
        windowCalls[id]++;
        if (muted[id] && !full) return 0L;
        TS t = LOCAL.get();
        long now = System.nanoTime();
        int p = t.pos;
        t.ring[p & (RING - 1)] = id + 1;
        t.ringT[p & (RING - 1)] = now;
        t.pos = p + 1;
        int d = t.depth;
        if (d < MAX_STACK) { t.stack[d] = id; t.stackT[d] = now; }
        t.depth = d + 1;
        FullSink fs = fullSink;
        if (full && fs != null) fs.line(indent(d) + "> " + FnTable.NAMES[id]);
        return now == 0L ? 1L : now;
    }

    public static void out(int id, long t0) {
        if (!ON) return;
        if (t0 == 0L) return;
        TS t = LOCAL.get();
        long now = System.nanoTime();
        int p = t.pos;
        t.ring[p & (RING - 1)] = -(id + 1);
        t.ringT[p & (RING - 1)] = now;
        t.pos = p + 1;
        int d = t.depth - 1;
        t.depth = d < 0 ? 0 : d;
        long dt = now - t0;
        totalNs[id] += dt;
        if (dt > maxNs[id]) maxNs[id] = dt;
        FullSink fs = fullSink;
        if (full && fs != null) fs.line(indent(d) + "< " + FnTable.NAMES[id] + " " + (dt / 1000) + "us");
        if (dt > FnTable.SLOW_NS[id]) {
            RLog.w("slow", FnTable.NAMES[id] + " took " + (dt / 1000000) + " ms");
        }
    }

    private static String indent(int d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(d, 20); i++) sb.append("  ");
        return sb.toString();
    }

    /** Called from the watchdog about once a second: mutes functions that are called too often to time. */
    public static void tick() {
        long now = System.nanoTime();
        long el = now - windowStart;
        if (el < 900_000_000L) return;
        for (int i = 0; i < N; i++) {
            int c = windowCalls[i];
            if (!muted[i] && c * 1_000_000_000L / el > HOT_PER_SEC) {
                muted[i] = true;
                RLog.i("trace", "auto-muted hot function (timing off, counting only): " + FnTable.NAMES[i] + " ~" + (c * 1000L / (el / 1000000L)) + "/s");
            }
            windowCalls[i] = 0;
        }
        windowStart = now;
    }

    public static void resetStats() {
        for (int i = 0; i < N; i++) { calls[i] = 0; totalNs[i] = 0; maxNs[i] = 0; }
    }

    /** Top functions by total time since the last reset. */
    public static String topSummary(int n) {
        boolean[] used = new boolean[N];
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < n; k++) {
            int best = -1;
            for (int i = 0; i < N; i++) if (!used[i] && totalNs[i] > 0 && (best < 0 || totalNs[i] > totalNs[best])) best = i;
            if (best < 0) break;
            used[best] = true;
            sb.append("\n    ").append(FnTable.NAMES[best]).append("  calls=").append(calls[best])
              .append(" total=").append(totalNs[best] / 1000000).append("ms max=").append(maxNs[best] / 1000).append("us");
        }
        return sb.toString();
    }

    /** Last `n` recorded calls of thread th (oldest first) as text. */
    public static String recent(Thread th, int n) {
        TS ts = null;
        synchronized (ALL) { for (TS x : ALL) if (x.thread == th) { ts = x; break; } }
        if (ts == null) return "    (no trace for thread " + (th == null ? "?" : th.getName()) + ")";
        int p = ts.pos;
        int cnt = p < 0 || p >= RING ? RING : p;   // p can wrap negative after 2^31 calls
        int from = p - Math.min(n, cnt);
        long base = System.nanoTime();
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < p; i++) {
            int v = ts.ring[i & (RING - 1)];
            long age = (base - ts.ringT[i & (RING - 1)]) / 1000000;
            sb.append("\n    -").append(age).append("ms ").append(v > 0 ? "> " : "< ").append(FnTable.NAMES[Math.abs(v) - 1]);
        }
        return sb.toString();
    }

    /** Instrumented call stack of thread th (innermost last) with elapsed time per frame. */
    public static String stack(Thread th) {
        TS ts = null;
        synchronized (ALL) { for (TS x : ALL) if (x.thread == th) { ts = x; break; } }
        if (ts == null) return "    (none)";
        int d = Math.min(ts.depth, MAX_STACK);
        long now = System.nanoTime();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d; i++) sb.append("\n    ").append(FnTable.NAMES[ts.stack[i]]).append("  (running ").append((now - ts.stackT[i]) / 1000000).append(" ms)");
        return d == 0 ? "    (empty)" : sb.toString();
    }
}
