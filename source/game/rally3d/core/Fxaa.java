package rally3d.core;


/**
 * CPU FXAA 3.11 (same algorithm as the PC GPU shader) for the Android software renderer.
 * Edge detect on luma, edge orientation, walk to both edge ends, shift/blend towards the neighbour across the edge.
 * Flat areas are copied untouched. Multi-threaded in row bands. mode: 1 = FXAA, 2 = FXAA Quality.
 */
public final class Fxaa {
    private Fxaa() {}

    /** Band split follows the "CPU threads" setting (Platform worker pool), capped at 8. */
    private static int threads() { return Math.max(1, Math.min(8, rally3d.desktop.Platform.workers())); }
    private static float[] luma = new float[0];

    /** Reads src (w*h ARGB), writes the anti-aliased frame to dst (same size, may not alias src). mode 0 = plain copy. */
    public static synchronized void apply(final int[] src, final int[] dst, final int w, final int h, final int mode) {
        final int n = w * h;
        System.arraycopy(src, 0, dst, 0, n);
        if (mode <= 0 || w < 4 || h < 4 || src.length < n || dst.length < n) return;
        if (luma.length < n) luma = new float[n];
        final float[] L = luma;
        final float thr = mode == 2 ? 0.125f : 0.166f, min = mode == 2 ? 0.0625f : 0.0833f, subq = mode == 2 ? 1.0f : 0.75f;
        final int maxSteps = mode == 2 ? 20 : 12;
        run(h, new Band() { public void run(int y0, int y1) {
            for (int y = y0; y < y1; y++) {
                int o = y * w;
                for (int x = 0; x < w; x++) {
                    int c = src[o + x];
                    L[o + x] = (((c >> 16) & 255) * 0.299f + ((c >> 8) & 255) * 0.587f + (c & 255) * 0.114f) * (1f / 255f);
                }
            }
         } });
        run(h - 2, new Band() { public void run(int y0, int y1) {
            for (int y = y0 + 1; y < y1 + 1; y++) for (int x = 1; x < w - 1; x++) pixel(src, dst, L, w, h, x, y, thr, min, subq, maxSteps);
         } });
     }

    private static void pixel(int[] src, int[] dst, float[] L, int w, int h, int x, int y, float thr, float emin, float subq, int maxSteps) {
        final int i = y * w + x;
        final float lM = L[i], lN = L[i - w], lS = L[i + w], lW = L[i - 1], lE = L[i + 1];
        final float rMax = Math.max(lM, Math.max(Math.max(lN, lS), Math.max(lW, lE)));
        final float rMin = Math.min(lM, Math.min(Math.min(lN, lS), Math.min(lW, lE)));
        final float range = rMax - rMin;
        if (range < Math.max(emin, rMax * thr)) return;
        final float lNW = L[i - w - 1], lNE = L[i - w + 1], lSW = L[i + w - 1], lSE = L[i + w + 1];
        final float lNS = lN + lS, lWE = lW + lE;
        final float edgeH = Math.abs(-2f * lW + (lNW + lSW)) + Math.abs(-2f * lM + lNS) * 2f + Math.abs(-2f * lE + (lNE + lSE));
        final float edgeV = Math.abs(-2f * lN + (lNW + lNE)) + Math.abs(-2f * lM + lWE) * 2f + Math.abs(-2f * lS + (lSW + lSE));
        final boolean horz = edgeH >= edgeV;
        final float l1 = horz ? lN : lW, l2 = horz ? lS : lE;
        final float g1 = l1 - lM, g2 = l2 - lM;
        final boolean pairN = Math.abs(g1) >= Math.abs(g2);
        final float gradScaled = Math.max(Math.abs(g1), Math.abs(g2)) * 0.25f;
        final float lAvg = 0.5f * ((pairN ? l1 : l2) + lM);
        // step across the edge (towards the steeper side) and along it
        final int across = horz ? (pairN ? -w : w) : (pairN ? -1 : 1);
        final int along = horz ? 1 : w;
        final int posAlong = horz ? x : y, limAlong = horz ? w : h;
        int dN = 1, dP = 1;
        float endN = 0.5f * (L[i - along] + L[i - along + across]) - lAvg;
        float endP = 0.5f * (L[i + along] + L[i + along + across]) - lAvg;
        boolean doneN = Math.abs(endN) >= gradScaled, doneP = Math.abs(endP) >= gradScaled;
        for (int s = 1; s < maxSteps && !(doneN && doneP); s++) {
            if (!doneN) {
                if (posAlong - dN - 1 < 0) { doneN = true; }
                else { dN++; int p = i - dN * along; endN = 0.5f * (L[p] + L[p + across]) - lAvg; doneN = Math.abs(endN) >= gradScaled; }
            }
            if (!doneP) {
                if (posAlong + dP + 1 >= limAlong) { doneP = true; }
                else { dP++; int p = i + dP * along; endP = 0.5f * (L[p] + L[p + across]) - lAvg; doneP = Math.abs(endP) >= gradScaled; }
            }
        }
        final boolean below = (lM - lAvg) < 0f;
        final boolean goodN = (endN < 0f) != below, goodP = (endP < 0f) != below;
        final boolean towardN = dN < dP;
        final float dst0 = Math.min(dN, dP);
        final boolean good = towardN ? goodN : goodP;
        final float span = Math.max(dN + dP, 1e-6f);
        final float pixelOffset = good ? (0.5f - dst0 / span) : 0f;
        final float subA = 2f * (lNS + lWE) + (lNW + lNE + lSW + lSE);
        float subC = Math.abs(subA / 12f - lM) / range;
        subC = subC < 0f ? 0f : (subC > 1f ? 1f : subC);
        final float subF = (-2f * subC + 3f) * subC * subC;
        final float po = Math.max(pixelOffset, subF * subF * subq);
        if (po <= 0.004f) return;
        final int a = src[i], b = src[i + across];
        final float t = po > 1f ? 1f : po;
        dst[i] = mix(a, b, t, 16) | mix(a, b, t, 8) | mix(a, b, t, 0) | (a & 0xFF000000);
     }

    private static int mix(int a, int b, float t, int shift) {
        int ca = (a >> shift) & 255, cb = (b >> shift) & 255;
        return (int) (ca + (cb - ca) * t + 0.5f) << shift;
    }

    private interface Band { void run(int y0, int y1); }

    private static void run(int rows, final Band job) {
        if (rows <= 0) return;
        final int th = threads();
        final int chunks = th <= 1 || rows < 96 ? 1 : Math.min(th * 2, rows / 32);
        if (chunks <= 1) { job.run(0, rows); return; }
        final int per = (rows + chunks - 1) / chunks, total = rows;
        rally3d.desktop.Platform.parallelFor(chunks, new java.util.function.IntConsumer() {
            public void accept(int c) {
                int a = c * per, z = Math.min(total, a + per);
                if (a < z) job.run(a, z);
            }
        });
     }
}
