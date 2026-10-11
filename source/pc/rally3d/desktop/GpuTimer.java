package rally3d.desktop;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * GPU time of a block of GL commands (GL_TIME_ELAPSED query ring). Results are read a few frames later without stalling
 * and fed into the [perf] profile. GL context must be current for every call. A timer must not be nested with another GL_TIME_ELAPSED query.
 */
public final class GpuTimer {
    private final int stage;
    private final int[] queries = new int[4];
    private final boolean[] pending = new boolean[4];
    private int next;
    private boolean created, active, broken;
    private int fullFrames;

    public GpuTimer(int perfStage) { stage = perfStage; }

    /** Collects finished queries (oldest first, never blocks). */
    public void poll() {
        if (!created || broken) return;
        int i = (next + queries.length - countPending()) % queries.length;
        for (int n = 0, total = countPending(); n < total; n++, i = (i + 1) % queries.length) {
            if (!pending[i]) continue;
            if (glGetQueryObjecti(queries[i], GL_QUERY_RESULT_AVAILABLE) == 0) break;
            long nanos = glGetQueryObjecti64(queries[i], GL_QUERY_RESULT);
            pending[i] = false;
            Platform.recordPerformance(stage, nanos);
        }
    }

    public void begin() {
        if (broken) return;
        if (!created) { glGenQueries(queries); created = true; }
        if (active) end();   // previous block threw before end(): close its query first
        if (pending[next]) {   // ring full: skip this frame instead of stalling
            active = false;
            if (++fullFrames >= 120) {   // results never arrive (stale queries after a context change): recreate them
                for (int q : queries) if (q != 0) glDeleteQueries(q);
                glGenQueries(queries); java.util.Arrays.fill(pending, false); next = 0; fullFrames = 0;
                rally3d.core.RLog.w("gpu", "GpuTimer(stage " + stage + "): queries never completed, recreated");
            }
            return;
        }
        fullFrames = 0;
        glBeginQuery(GL_TIME_ELAPSED, queries[next]);
        active = true;
    }

    public void end() {
        if (!active) return;
        glEndQuery(GL_TIME_ELAPSED);
        pending[next] = true;
        next = (next + 1) % queries.length;
        active = false;
    }

    /** Forget all queries (GL context was re-created). */
    public void reset() { created = false; active = false; java.util.Arrays.fill(pending, false); next = 0; }

    private int countPending() { int c = 0; for (boolean p : pending) if (p) c++; return c; }
}
