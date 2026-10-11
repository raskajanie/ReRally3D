package javax.microedition.m3g;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.awt.image.BufferedImage;
import java.util.stream.IntStream;
import javax.microedition.lcdui.Graphics;
import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Software M3G (JSR-184) subset: triangle strips, perspective-correct nearest-neighbour texturing,
 * per-vertex colours (modulate), near/far clipping, back-face culling, optional depth buffer.
 * Triangles are queued in render() and rasterised in parallel row bands in releaseTarget().
 */
public final class Graphics3D {
    private static final Graphics3D INSTANCE = new Graphics3D();
    public static Graphics3D getInstance() { return INSTANCE; }
    // Keep each software fallback flush to a small set of row jobs. The old
    // CPU-count * 4 split created dozens of tiny common-pool tasks per pass.
    // Common pool workers + the calling thread = every logical core. 2 bands per core (not 1): bands cost very different amounts
    // (sky vs road vs sprites), so extra bands let idle workers steal work and keep all cores busy.
    private static int parallelBands() { return Math.max(1, Math.min(48, rally3d.desktop.Platform.workers() * 2)); }

    private interface RowJob { void run(int y0, int y1); }

    /** Splits [0, rows) into contiguous chunks and runs them on all cores (small jobs stay on the calling thread). */
    private static void parallelRows(int rows, RowJob job) {
        int chunks = rows >= 128 ? Math.min(parallelBands(), rows / 32) : 1;
        if (chunks <= 1) { job.run(0, rows); return; }
        final int per = (rows + chunks - 1) / chunks;
        rally3d.desktop.Platform.parallelFor(chunks, c -> job.run(c * per, Math.min(rows, (c + 1) * per)));
    }

    private static final int ATTRS = 7;            // z, 1/w, u/w, v/w, r/w, g/w, b/w
    private static final int REC = 34;             // doubles per queued triangle

    // ---- state ----
    private int vpX, vpY, vpW, vpH;                // logical viewport
    private final float[] proj = new float[16];
    private Graphics target;
    private int[] pix; private int pw, ph; private double scale;
    private float[] depth; private boolean depthBound;
    private int clipX0, clipY0, clipX1, clipY1;   // physical clip (pixels, x1/y1 exclusive)
    private double vx, vy, vw, vh;                 // physical viewport

    // ---- triangle queue ----
    private double[] queue = new double[REC * 2048];
    private int triCount;
    private State[] states = new State[256];
    private int stateCount;
    private final float[] matrix = new float[16];
    // Reused scan-band bins keep workers from rescanning triangles that cannot
    // touch their rows. The backing arrays grow only when a larger scene appears.
    private int[] bandCounts = new int[0];
    private int[] bandOffsets = new int[1];
    private int[] bandCursors = new int[0];
    private int[] bandTriangles = new int[0];
    private int gpuFramebuffer, gpuColor, gpuDepth, gpuProgram, gpuVao, gpuVbo;
    private int gpuWidth, gpuHeight, gpuVertexCapacity;
    private FloatBuffer gpuVertices;
    private volatile boolean depthMapValid;
    // Cached physical pixel spans for sprite rows/columns; avoiding repeated
    // floating-point floor/ceil work saves CPU when a frame contains many trees.
    private int[] spritePx0 = new int[0], spritePx1 = new int[0];
    private final IdentityHashMap<Texture2D, Integer> gpuTextures = new IdentityHashMap<Texture2D, Integer>();
    private int gpuTextureSampler, gpuTextureEnabled, gpuModulate, gpuInvSize;
    private int gpuAlphaTest;
    private boolean billboardBatchActive, gpuBillboardsRendered;
    private int billboardCount;
    private Texture2D[] billboardTextures = new Texture2D[256];
    private float[] billboardUvs = new float[256 * 4];
    private int[][] billboardPixels = new int[256][];
    private int[][][] billboardRuns = new int[256][][];
    private int[] billboardWidths = new int[256], billboardHeights = new int[256];
    private int[] billboardSourceWidths = new int[256], billboardSourceHeights = new int[256];
    private int[] billboardX = new int[256], billboardY = new int[256];
    private int[] billboardGpuWidths = new int[256], billboardGpuHeights = new int[256];
    private int[] billboardGpuX = new int[256], billboardGpuY = new int[256];
    private float[] billboardDepths = new float[256];
    private int[] billboardOrder = new int[256];

    private static final class State {
        Texture2D tex; boolean modulate; boolean depthTest, depthWrite; boolean noColorWrite; boolean softDepthWrite;
    }

    private Graphics3D() { proj[0] = proj[5] = proj[10] = proj[15] = 1; }

    public void setViewport(int x, int y, int w, int h) { vpX = x; vpY = y; vpW = w; vpH = h; }

    public void setCamera(Camera c, Transform t) { if (c != null) System.arraycopy(c.proj.m, 0, proj, 0, 16); }

    public synchronized void bindTarget(Object tgt, boolean depthBuffer, int hints) {
        target = (Graphics) tgt;
        pix = target.pixelBuffer(); pw = target.physicalWidth(); ph = target.physicalHeight(); scale = target.scale();
        depthBound = depthBuffer;
        depthMapValid = false;
        if (depthBuffer && (depth == null || depth.length != pw * ph)) depth = new float[pw * ph];
        triCount = 0; stateCount = 0;
        updateRects();
    }

    private void updateRects() {
        vx = vpX * scale; vy = vpY * scale; vw = vpW * scale; vh = vpH * scale;
        int cx0 = (int) Math.floor(target.getClipX() * scale), cy0 = (int) Math.floor(target.getClipY() * scale);
        int cx1 = (int) Math.ceil((target.getClipX() + target.getClipWidth()) * scale);
        int cy1 = (int) Math.ceil((target.getClipY() + target.getClipHeight()) * scale);
        clipX0 = Math.max(0, Math.max(cx0, (int) Math.floor(vx)));
        clipY0 = Math.max(0, Math.max(cy0, (int) Math.floor(vy)));
        clipX1 = Math.min(pw, Math.min(cx1, (int) Math.ceil(vx + vw)));
        clipY1 = Math.min(ph, Math.min(cy1, (int) Math.ceil(vy + vh)));
    }

    public synchronized void releaseTarget() {
        if (pix != null) flush();
        target = null; pix = null;
    }

    /** Collect tree/checkpoint billboards so the GPU can depth-test them before color readback. */
    public synchronized void beginBillboardBatch() {
        billboardBatchActive = true;
        gpuBillboardsRendered = false;
        billboardCount = 0;
    }

    public synchronized boolean queueBillboard(Graphics graphics, Texture2D texture, float[] uv, int[] pixels, int[][] runs,
                                                 int sourceWidth, int sourceHeight, int width, int height,
                                                 int x, int y, float depthValue) {
        if (!billboardBatchActive || graphics == null || texture == null || uv == null || pixels == null || runs == null) return false;
        int clipX0 = graphics.getClipX(), clipY0 = graphics.getClipY();
        int clipX1 = clipX0 + graphics.getClipWidth(), clipY1 = clipY0 + graphics.getClipHeight();
        int clippedX0 = Math.max(x, clipX0), clippedY0 = Math.max(y, clipY0);
        int clippedX1 = Math.min(x + width, clipX1), clippedY1 = Math.min(y + height, clipY1);
        if (clippedX0 >= clippedX1 || clippedY0 >= clippedY1) return true;
        if (billboardCount == billboardTextures.length) {
            int size = billboardCount * 2;
            billboardTextures = Arrays.copyOf(billboardTextures, size);
            billboardPixels = Arrays.copyOf(billboardPixels, size);
            billboardRuns = Arrays.copyOf(billboardRuns, size);
            billboardWidths = Arrays.copyOf(billboardWidths, size);
            billboardHeights = Arrays.copyOf(billboardHeights, size);
            billboardSourceWidths = Arrays.copyOf(billboardSourceWidths, size);
            billboardSourceHeights = Arrays.copyOf(billboardSourceHeights, size);
            billboardX = Arrays.copyOf(billboardX, size);
            billboardY = Arrays.copyOf(billboardY, size);
            billboardGpuWidths = Arrays.copyOf(billboardGpuWidths, size);
            billboardGpuHeights = Arrays.copyOf(billboardGpuHeights, size);
            billboardGpuX = Arrays.copyOf(billboardGpuX, size);
            billboardGpuY = Arrays.copyOf(billboardGpuY, size);
            billboardUvs = Arrays.copyOf(billboardUvs, size * 4);
            billboardDepths = Arrays.copyOf(billboardDepths, size);
            billboardOrder = Arrays.copyOf(billboardOrder, size);
        }
        int i = billboardCount++;
        billboardTextures[i] = texture;
        billboardPixels[i] = pixels;
        billboardRuns[i] = runs;
        billboardWidths[i] = width;
        billboardHeights[i] = height;
        billboardSourceWidths[i] = sourceWidth;
        billboardSourceHeights[i] = sourceHeight;
        billboardX[i] = x;
        billboardY[i] = y;
        billboardGpuX[i] = clippedX0;
        billboardGpuY[i] = clippedY0;
        billboardGpuWidths[i] = clippedX1 - clippedX0;
        billboardGpuHeights[i] = clippedY1 - clippedY0;
        float left = (clippedX0 - x) / (float)width;
        float top = (clippedY0 - y) / (float)height;
        float right = (clippedX1 - x) / (float)width;
        float bottom = (clippedY1 - y) / (float)height;
        int uvOffset = i * 4;
        billboardUvs[uvOffset] = uv[0] + (uv[2] - uv[0]) * left;
        billboardUvs[uvOffset + 1] = uv[1] + (uv[3] - uv[1]) * top;
        billboardUvs[uvOffset + 2] = uv[0] + (uv[2] - uv[0]) * right;
        billboardUvs[uvOffset + 3] = uv[1] + (uv[3] - uv[1]) * bottom;
        billboardDepths[i] = depthValue;
        return true;
    }

    /** Finish CPU fallback billboards after software depth has been rasterized. */
    public synchronized void finishBillboardBatch(Graphics graphics) {
        if (!billboardBatchActive) return;
        if (!gpuBillboardsRendered && graphics != null) {
            sortBillboardsBackToFront();
            for (int order = 0; order < billboardCount; order++) {
                int i = billboardOrder[order];
                drawDepthTestedImageScaled(graphics, billboardPixels[i], billboardRuns[i],
                        billboardSourceWidths[i], billboardSourceHeights[i], billboardWidths[i], billboardHeights[i],
                        billboardX[i], billboardY[i], billboardDepths[i]);
            }
        }
        billboardBatchActive = false;
        billboardCount = 0;
    }

    public synchronized void clear(Background bg) {
        if (pix == null) return;
        updateRects();
        if (bg == null) return;
        final boolean col = bg.colorClear, dep = bg.depthClear && depthBound;
        if (!col && !dep) return;
        final int c = 0xFF000000 | (bg.color & 0xFFFFFF), cy0 = clipY0, x0 = clipX0, x1 = Math.max(clipX0, clipX1), pitch = pw;
        final int[] p = pix; final float[] d = depth;
        // one parallel pass over colour + depth (full-screen clears are 2 x 2M elements at 1080p)
        parallelRows(clipY1 - clipY0, (a, b) -> {
            for (int y = cy0 + a; y < cy0 + b; y++) {
                if (col) Arrays.fill(p, y * pitch + x0, y * pitch + x1, c);
                if (dep) Arrays.fill(d, y * pitch + x0, y * pitch + x1, 1f);
            }
        });
    }

    // ================= geometry =================
    private float[] cv = new float[0];
    private int[] codes = new int[0];
    private long renderNanosAccum;
    private short[] depthRow = new short[0];
    private final float[][] polyA = new float[12][9], polyB = new float[12][9];
    private final double[] sa0 = new double[ATTRS], sa1 = new double[ATTRS], sa2 = new double[ATTRS];

    public synchronized void render(VertexBuffer vb, IndexBuffer ib, Appearance ap, Transform t) {
        if (pix == null || vb == null || vb.pos == null || !(ib instanceof TriangleStripArray)) return;
        long started = System.nanoTime();
        updateRects();
        // state
        if (stateCount == states.length) states = Arrays.copyOf(states, stateCount * 2);
        State st = states[stateCount];
        if (st == null) states[stateCount] = st = new State();
        st.tex = ap == null ? null : ap.texture;
        st.modulate = st.tex != null && st.tex.func == Texture2D.FUNC_MODULATE;
        CompositingMode cm = ap == null ? null : ap.compositing;
        st.depthTest = depthBound && (cm == null || cm.depthTest);
        st.depthWrite = depthBound && (cm == null || cm.depthWrite);
        // Road / terrain meshes set depthWrite=false (a GPU-driver workaround; the GPU path builds its own depth pre-pass). The CPU fallback
        // has no such pass, so without this the sprites (trees, checkpoints, finish) were never occluded by hills and walls.
        st.softDepthWrite = depthBound && (cm == null || cm.depthWrite || cm.depthTest);
        st.noColorWrite = cm != null && !cm.colorWrite;
        PolygonMode pm = ap == null ? null : ap.polygon;
        int culling = pm == null ? PolygonMode.CULL_BACK : pm.culling;
        int winding = pm == null ? PolygonMode.WINDING_CCW : pm.winding;
        int stateIdx = stateCount++;

        // combined matrix M = P * T
        float[] pm4 = proj, tm = t == null ? IDENT : t.m, m = matrix;
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                m[i * 4 + j] = pm4[i * 4] * tm[j] + pm4[i * 4 + 1] * tm[4 + j] + pm4[i * 4 + 2] * tm[8 + j] + pm4[i * 4 + 3] * tm[12 + j];

        VertexArray pa = vb.pos;
        int n = pa.count;
        if (cv.length < n * 9) cv = new float[n * 9];
        if (codes.length < n) codes = new int[n];
        boolean posShort = pa.s != null;
        int andAll = ~0;
        for (int i = 0; i < n; i++) {
            float x, y, z;
            int o = i * pa.comps;
            if (posShort) { x = pa.s[o]; y = pa.s[o + 1]; z = pa.comps > 2 ? pa.s[o + 2] : 0; }
            else { x = pa.b[o]; y = pa.b[o + 1]; z = pa.comps > 2 ? pa.b[o + 2] : 0; }
            x = x * vb.posScale + vb.posBias[0]; y = y * vb.posScale + vb.posBias[1]; z = z * vb.posScale + vb.posBias[2];
            int d = i * 9;
            cv[d] = m[0] * x + m[1] * y + m[2] * z + m[3];
            cv[d + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
            cv[d + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
            cv[d + 3] = m[12] * x + m[13] * y + m[14] * z + m[15];
            VertexArray ta = vb.tex;
            if (ta != null) {
                int to = i * ta.comps; float u, v;
                if (ta.s != null) { u = ta.s[to]; v = ta.s[to + 1]; } else { u = ta.b[to]; v = ta.b[to + 1]; }
                cv[d + 4] = u * vb.texScale + vb.texBias[0]; cv[d + 5] = v * vb.texScale + vb.texBias[1];
            } else { cv[d + 4] = 0; cv[d + 5] = 0; }
            VertexArray ca = vb.col;
            if (ca != null && ca.b != null) {
                int co = i * ca.comps;
                cv[d + 6] = ca.b[co] & 255; cv[d + 7] = ca.b[co + 1] & 255; cv[d + 8] = ca.b[co + 2] & 255;
            } else { cv[d + 6] = 255; cv[d + 7] = 255; cv[d + 8] = 255; }
            int oc = outcode(d);
            codes[i] = oc;
            andAll &= oc;
        }
        if (andAll != 0) { // whole mesh outside one frustum plane: skip every triangle
            renderNanosAccum += System.nanoTime() - started;
            return;
        }

        TriangleStripArray ts = (TriangleStripArray) ib;
        int base = 0;
        for (int sl : ts.stripLengths) {
            for (int k = 0; k + 2 < sl; k++) {
                int i0 = ts.indices[base + k], i1 = ts.indices[base + k + 1], i2 = ts.indices[base + k + 2];
                if ((k & 1) == 0) emit(i0, i1, i2, stateIdx, culling, winding);
                else emit(i1, i0, i2, stateIdx, culling, winding);
            }
            base += sl;
        }
        renderNanosAccum += System.nanoTime() - started;
    }

    private static final float[] IDENT = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    // ---- parallel vertex transform for many meshes sharing one appearance (road ring) ----
    private static final class Slot { float[] cv = new float[0]; int[] codes = new int[0]; }
    private Slot[] slots = new Slot[0];

    /** Vertex stage of one mesh into the slot's own buffers (thread-safe: reads only vb and m). Returns AND of all outcodes (~0 = nothing to draw). */
    private static int transformMesh(VertexBuffer vb, float[] m, Slot s) {
        if (vb == null || vb.pos == null) return ~0;
        VertexArray pa = vb.pos;
        int n = pa.count;
        if (s.cv.length < n * 9) s.cv = new float[n * 9];
        if (s.codes.length < n) s.codes = new int[n];
        final float[] cv = s.cv; final int[] codes = s.codes;
        boolean posShort = pa.s != null;
        int andAll = ~0;
        VertexArray ta = vb.tex, ca = vb.col;
        for (int i = 0; i < n; i++) {
            float x, y, z;
            int o = i * pa.comps;
            if (posShort) { x = pa.s[o]; y = pa.s[o + 1]; z = pa.comps > 2 ? pa.s[o + 2] : 0; }
            else { x = pa.b[o]; y = pa.b[o + 1]; z = pa.comps > 2 ? pa.b[o + 2] : 0; }
            x = x * vb.posScale + vb.posBias[0]; y = y * vb.posScale + vb.posBias[1]; z = z * vb.posScale + vb.posBias[2];
            int d = i * 9;
            float cx = m[0] * x + m[1] * y + m[2] * z + m[3];
            float cy = m[4] * x + m[5] * y + m[6] * z + m[7];
            float cz = m[8] * x + m[9] * y + m[10] * z + m[11];
            float cw = m[12] * x + m[13] * y + m[14] * z + m[15];
            cv[d] = cx; cv[d + 1] = cy; cv[d + 2] = cz; cv[d + 3] = cw;
            if (ta != null) {
                int to = i * ta.comps; float u, v;
                if (ta.s != null) { u = ta.s[to]; v = ta.s[to + 1]; } else { u = ta.b[to]; v = ta.b[to + 1]; }
                cv[d + 4] = u * vb.texScale + vb.texBias[0]; cv[d + 5] = v * vb.texScale + vb.texBias[1];
            } else { cv[d + 4] = 0; cv[d + 5] = 0; }
            if (ca != null && ca.b != null) {
                int co = i * ca.comps;
                cv[d + 6] = ca.b[co] & 255; cv[d + 7] = ca.b[co + 1] & 255; cv[d + 8] = ca.b[co + 2] & 255;
            } else { cv[d + 6] = 255; cv[d + 7] = 255; cv[d + 8] = 255; }
            int f = 0;
            if (cx < -cw) f |= 1; if (cx > cw) f |= 2;
            if (cy < -cw) f |= 4; if (cy > cw) f |= 8;
            if (cz < -cw) f |= 16; if (cz > cw) f |= 32;
            codes[i] = f;
            andAll &= f;
        }
        return andAll;
    }

    /**
     * Same result as calling render(vbs[i], ib, ap, t) for i = 0..count-1 in order, but the per-mesh vertex transform runs on the worker pool;
     * clipping / triangle emission stay serial (queue order = call order).
     */
    public synchronized void renderBatch(VertexBuffer[] vbs, IndexBuffer ib, Appearance ap, Transform t, int count) {
        if (count <= 0) return;
        if (pix == null || !(ib instanceof TriangleStripArray)) return;
        if (count < 6 || rally3d.desktop.Platform.workers() <= 1) {
            for (int i = 0; i < count; i++) render(vbs[i], ib, ap, t);
            return;
        }
        long started = System.nanoTime();
        updateRects();
        if (stateCount == states.length) states = Arrays.copyOf(states, stateCount * 2);
        State st = states[stateCount];
        if (st == null) states[stateCount] = st = new State();
        st.tex = ap == null ? null : ap.texture;
        st.modulate = st.tex != null && st.tex.func == Texture2D.FUNC_MODULATE;
        CompositingMode cm = ap == null ? null : ap.compositing;
        st.depthTest = depthBound && (cm == null || cm.depthTest);
        st.depthWrite = depthBound && (cm == null || cm.depthWrite);
        st.softDepthWrite = depthBound && (cm == null || cm.depthWrite || cm.depthTest);
        st.noColorWrite = cm != null && !cm.colorWrite;
        PolygonMode pm = ap == null ? null : ap.polygon;
        final int culling = pm == null ? PolygonMode.CULL_BACK : pm.culling;
        final int winding = pm == null ? PolygonMode.WINDING_CCW : pm.winding;
        final int stateIdx = stateCount++;

        final float[] pm4 = proj, tm = t == null ? IDENT : t.m;
        final float[] m = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                m[i * 4 + j] = pm4[i * 4] * tm[j] + pm4[i * 4 + 1] * tm[4 + j] + pm4[i * 4 + 2] * tm[8 + j] + pm4[i * 4 + 3] * tm[12 + j];

        if (slots.length < count) {
            Slot[] ns = Arrays.copyOf(slots, count);
            for (int i = 0; i < count; i++) if (ns[i] == null) ns[i] = new Slot();
            slots = ns;
        }
        final Slot[] sl = slots;
        final int[] ands = new int[count];
        final int chunks = Math.min(count, rally3d.desktop.Platform.workers() * 2);
        final int per = (count + chunks - 1) / chunks;
        rally3d.desktop.Platform.parallelFor(chunks, c -> {
            int a = c * per, z = Math.min(count, a + per);
            for (int i = a; i < z; i++) ands[i] = transformMesh(vbs[i], m, sl[i]);
        });

        final float[] keepCv = cv; final int[] keepCodes = codes;
        TriangleStripArray ts = (TriangleStripArray) ib;
        for (int i = 0; i < count; i++) {
            if (ands[i] != 0) continue;
            cv = sl[i].cv; codes = sl[i].codes;
            int base = 0;
            for (int sln : ts.stripLengths) {
                for (int k = 0; k + 2 < sln; k++) {
                    int i0 = ts.indices[base + k], i1 = ts.indices[base + k + 1], i2 = ts.indices[base + k + 2];
                    if ((k & 1) == 0) emit(i0, i1, i2, stateIdx, culling, winding);
                    else emit(i1, i0, i2, stateIdx, culling, winding);
                }
                base += sln;
            }
        }
        cv = keepCv; codes = keepCodes;
        renderNanosAccum += System.nanoTime() - started;
    }


    private void emit(int a, int b, int c, int stateIdx, int culling, int winding) {
        int oa = a * 9, ob = b * 9, oc = c * 9;
        int fa = codes[a], fb = codes[b], fc = codes[c];
        if ((fa & fb & fc) != 0) return;
        int n;
        float[][] poly;
        if (((fa | fb | fc) & 48) == 0) {
            System.arraycopy(cv, oa, polyA[0], 0, 9);
            System.arraycopy(cv, ob, polyA[1], 0, 9);
            System.arraycopy(cv, oc, polyA[2], 0, 9);
            n = 3; poly = polyA;
        } else {
            System.arraycopy(cv, oa, polyA[0], 0, 9);
            System.arraycopy(cv, ob, polyA[1], 0, 9);
            System.arraycopy(cv, oc, polyA[2], 0, 9);
            n = clipPlane(polyA, 3, polyB, 0);
            if (n < 3) return;
            n = clipPlane(polyB, n, polyA, 1);
            if (n < 3) return;
            poly = polyA;
        }
        // project
        double[] sx = tmpX, sy = tmpY; double[][] at = tmpA;
        for (int i = 0; i < n; i++) {
            float[] p = poly[i];
            double iw = 1.0 / p[3];
            sx[i] = vx + (p[0] * iw + 1) * 0.5 * vw;
            sy[i] = vy + (1 - p[1] * iw) * 0.5 * vh;
            double[] q = at[i];
            q[0] = (p[2] * iw) * 0.5 + 0.5; q[1] = iw; q[2] = p[4] * iw; q[3] = p[5] * iw;
            q[4] = p[6] * iw; q[5] = p[7] * iw; q[6] = p[8] * iw;
        }
        for (int i = 1; i + 1 < n; i++) {
            double area = (sx[i] - sx[0]) * (sy[i + 1] - sy[0]) - (sx[i + 1] - sx[0]) * (sy[i] - sy[0]);
            if (area == 0) continue;
            boolean ccw = area < 0;                       // y-down screen: CCW in NDC -> negative area
            if (winding == PolygonMode.WINDING_CW) ccw = !ccw;
            if (culling == PolygonMode.CULL_BACK && !ccw) continue;
            if (culling == PolygonMode.CULL_FRONT && ccw) continue;
            push(sx[0], sy[0], at[0], sx[i], sy[i], at[i], sx[i + 1], sy[i + 1], at[i + 1], stateIdx);
        }
    }

    private final double[] tmpX = new double[12], tmpY = new double[12];
    private final double[][] tmpA = new double[12][ATTRS];

    private int outcode(int o) {
        float x = cv[o], y = cv[o + 1], z = cv[o + 2], w = cv[o + 3];
        int f = 0;
        if (x < -w) f |= 1; if (x > w) f |= 2;
        if (y < -w) f |= 4; if (y > w) f |= 8;
        if (z < -w) f |= 16; if (z > w) f |= 32;
        return f;
    }

    /** Sutherland-Hodgman against near (z >= -w) or far (z <= w). */
    private static int clipPlane(float[][] in, int n, float[][] out, int plane) {
        int m = 0;
        for (int i = 0; i < n; i++) {
            float[] cur = in[i], nxt = in[(i + 1) % n];
            float dc = plane == 0 ? cur[2] + cur[3] : cur[3] - cur[2];
            float dn = plane == 0 ? nxt[2] + nxt[3] : nxt[3] - nxt[2];
            if (dc >= 0) { System.arraycopy(cur, 0, out[m++], 0, 9); }
            if ((dc >= 0) != (dn >= 0)) {
                float t = dc / (dc - dn);
                float[] o = out[m++];
                for (int k = 0; k < 9; k++) o[k] = cur[k] + (nxt[k] - cur[k]) * t;
            }
        }
        return m;
    }

    private void push(double xa, double ya, double[] aa, double xb, double yb, double[] ab, double xc, double yc, double[] ac, int stateIdx) {
        // sort by y: top (0), mid (1), bottom (2)
        double x0 = xa, y0 = ya, x1 = xb, y1 = yb, x2 = xc, y2 = yc;
        double[] a0 = aa, a1 = ab, a2 = ac, t;
        double tx, ty;
        if (y1 < y0) { tx = x0; ty = y0; t = a0; x0 = x1; y0 = y1; a0 = a1; x1 = tx; y1 = ty; a1 = t; }
        if (y2 < y0) { tx = x0; ty = y0; t = a0; x0 = x2; y0 = y2; a0 = a2; x2 = tx; y2 = ty; a2 = t; }
        if (y2 < y1) { tx = x1; ty = y1; t = a1; x1 = x2; y1 = y2; a1 = a2; x2 = tx; y2 = ty; a2 = t; }
        if (y2 <= y0) return;
        int r0 = Math.max(clipY0, (int) Math.ceil(y0 - 0.5));
        int r1 = Math.min(clipY1, (int) Math.ceil(y2 - 0.5));
        if (r0 >= r1) return;
        double den = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (den == 0) return;
        if ((triCount + 1) * REC > queue.length) queue = Arrays.copyOf(queue, queue.length * 2);
        int o = triCount * REC;
        double[] q = queue;
        q[o] = x0; q[o + 1] = y0; q[o + 2] = x1; q[o + 3] = y1; q[o + 4] = x2; q[o + 5] = y2;
        final double invDen = 1.0 / den, dy20 = y2 - y0, dy10 = y1 - y0, dx10 = x1 - x0, dx20 = x2 - x0;
        for (int k = 0; k < ATTRS; k++) {
            double d1 = a1[k] - a0[k], d2 = a2[k] - a0[k];
            q[o + 6 + k] = a0[k];
            q[o + 13 + k] = (d1 * dy20 - d2 * dy10) * invDen;
            q[o + 20 + k] = (d2 * dx10 - d1 * dx20) * invDen;
        }
        q[o + 27] = r0; q[o + 28] = r1; q[o + 29] = stateIdx;
        q[o + 30] = (x2 - x0) / (y2 - y0);
        q[o + 31] = y1 > y0 ? (x1 - x0) / (y1 - y0) : 0;
        q[o + 32] = y2 > y1 ? (x2 - x1) / (y2 - y1) : 0;
        triCount++;
    }

    // ================= rasterisation =================
    private void flush() {
        if (renderNanosAccum != 0L) { rally3d.desktop.Platform.recordPerformance(0, renderNanosAccum); renderNanosAccum = 0L; }
        if (worldPending) {
            // GPU layered world: sky + 3D (+ billboards) are drawn into the world FBO; no CPU pixels are read back or uploaded.
            long started = System.nanoTime();
            worldPending = false;
            if (rally3d.desktop.Platform.runGpu(this::flushGpuWorld)) {
                rally3d.desktop.Platform.recordPerformance(1, System.nanoTime() - started);
                worldDrawn = true;
                worldHeld = true;
                triCount = 0;
                return;
            }
            // GL context unavailable for this frame: paint the sky on the CPU layer and rasterize in software (UI layer becomes opaque here).
            worldDrawn = false;
            worldHeld = false;
            Runnable sky = cpuSky;
            if (sky != null) { try { sky.run(); } catch (RuntimeException e) { rally3d.core.RLog.caught("Graphics3D.cpuSky", e); } }
            flushSoftware();
            rally3d.desktop.Platform.recordPerformance(2, System.nanoTime() - started);
            return;
        }
        if (triCount == 0) { return; }
        long started = System.nanoTime();
        flushSoftware();
        blitToGpuUi();
        rally3d.desktop.Platform.recordPerformance(2, System.nanoTime() - started);
    }
    /** GPU UI mode: the software raster wrote into the CPU buffer, which is never uploaded -> hand the touched rectangle to the recorded UI (menu car preview was invisible in alpha54). */
    private void blitToGpuUi() {
        Graphics t = target;
        if (t != null && t.isUiRecording() && clipX1 > clipX0 && clipY1 > clipY0) t.recordPixelBlit(clipX0, clipY0, clipX1, clipY1);
    }

    // ---- GPU layered world (stage 1) ----
    private boolean worldPending, worldDrawn, worldHeld;
    private Runnable cpuSky;
    private Object skyKey;
    private int skyFirstX, skyY, skyColorRgb, groundColorRgb, skyImgW, skyImgH;
    private final IdentityHashMap<Object, Integer> skyTextures = new IdentityHashMap<Object, Integer>();

    /**
     * Starts a world frame for the GPU layered pipeline. Returns true when the GPU will draw the sky and the 3D scene into its own layer
     * (the caller must then NOT paint the sky on the CPU and may only draw UI afterwards). Returns false -> classic CPU path.
     * firstTileX = x of the first (left-most, usually negative) sky tile, skyTopY = y of the sky image top, both in logical pixels.
     */
    public synchronized boolean beginWorld(Graphics g, javax.microedition.lcdui.Image sky, int firstTileX, int skyTopY,
                                           int skyColor, int groundColor, Runnable cpuSkyPainter) {
        worldPending = false; worldDrawn = false; cpuSky = null;
        if (g == null || sky == null || !g.hasAlpha() || !rally3d.desktop.Platform.gpuLayered()) return false;
        g.clearTransparent();   // the CPU layer holds only UI from now on
        skyKey = sky;
        skyImgW = sky.getWidth(); skyImgH = sky.getHeight();
        skyFirstX = firstTileX; skyY = skyTopY;
        skyColorRgb = skyColor & 0xFFFFFF; groundColorRgb = groundColor & 0xFFFFFF;
        cpuSky = cpuSkyPainter;
        worldPending = true;
        return true;
    }

    /** Texture of the 3D world rendered this frame (consumed once per frame by the presenter), or 0 when there is none (menus, fallback frame). */
    public synchronized int takeGpuWorldTexture(int width, int height) {
        // Screens that draw no world this frame (race intro panel, messages) paint on top of the previous frame in software; the GPU equivalent
        // is the world texture of the last world frame, which stays valid until the next one overwrites it.
        boolean ok = (worldDrawn || worldHeld) && gpuColor != 0 && gpuWidth == width && gpuHeight == height;
        worldDrawn = false;
        return ok ? gpuColor : 0;
    }

    /** Forget the held world texture (renderer switched / buffers rebuilt): menus are composed over black again until the next world frame. */
    public synchronized void dropHeldWorld() { worldHeld = false; worldDrawn = false; }

    private void rasterDepthOnly() {
        if (depth == null) return;
        for (int t = 0; t < triCount; t++) {
            int o = t * REC;
            State st = states[(int) queue[o + 29]];
            if (!st.depthWrite) continue;
            double ax = queue[o], ay = queue[o + 1];
            double bx = queue[o + 2], by = queue[o + 3];
            double cx = queue[o + 4], cy = queue[o + 5];
            double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            if (area == 0) continue;
            int minX = Math.max(clipX0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
            int maxX = Math.min(clipX1 - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
            int minY = Math.max(clipY0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
            int maxY = Math.min(clipY1 - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
            for (int y = minY; y <= maxY; y++) {
                double py = y + 0.5;
                for (int x = minX; x <= maxX; x++) {
                    double px = x + 0.5;
                    double e0 = (bx - ax) * (py - ay) - (by - ay) * (px - ax);
                    double e1 = (cx - bx) * (py - by) - (cy - by) * (px - bx);
                    double e2 = (ax - cx) * (py - cy) - (ay - cy) * (px - cx);
                    if (area > 0 ? (e0 < 0 || e1 < 0 || e2 < 0) : (e0 > 0 || e1 > 0 || e2 > 0)) continue;
                    int idx = y * pw + x;
                    double z = queue[o + 6] + queue[o + 13] * (px - ax) + queue[o + 20] * (py - ay);
                    if (z < depth[idx]) depth[idx] = (float) z;
                }
            }
        }
    }

    /** Draws an alpha sprite while rejecting pixels hidden by the 3D depth map. */
    public synchronized boolean drawDepthTestedImage(Graphics target, int[] source, int[][] opaqueRuns,
                                                       int sw, int sh, int x, int y, float z) {
        if (target == null || source == null || depth == null || pw <= 0 || ph <= 0) return false;
        double s = target.scale();
        int clipX0 = Math.max(0, (int) Math.floor(target.getClipX() * s));
        int clipY0 = Math.max(0, (int) Math.floor(target.getClipY() * s));
        int clipX1 = Math.min(pw, (int) Math.ceil((target.getClipX() + target.getClipWidth()) * s));
        int clipY1 = Math.min(ph, (int) Math.ceil((target.getClipY() + target.getClipHeight()) * s));
        int[] dst = target.pixelBuffer();
        int visiblePixels = 0;
        int logicalClipX1 = target.getClipX() + target.getClipWidth();
        int logicalClipY1 = target.getClipY() + target.getClipHeight();
        int startY = Math.max(0, target.getClipY() - y);
        int endY = Math.min(sh, logicalClipY1 - y);
        int startX = Math.max(0, target.getClipX() - x);
        int endX = Math.min(sw, logicalClipX1 - x);
        if (spritePx0.length < sw) {
            spritePx0 = Arrays.copyOf(spritePx0, sw);
            spritePx1 = Arrays.copyOf(spritePx1, sw);
        }
        for (int sx = startX; sx < endX; sx++) {
            spritePx0[sx] = Math.max(clipX0, (int) Math.floor((x + sx) * s));
            spritePx1[sx] = Math.min(clipX1, (int) Math.floor((x + sx + 1) * s));
        }
        for (int sy = startY; sy < endY; sy++) {
            int py0 = Math.max(clipY0, (int) Math.floor((y + sy) * s));
            int py1 = Math.min(clipY1, (int) Math.floor((y + sy + 1) * s));
            if (py0 >= py1) continue;
            int srcRow = sy * sw;
            int[] rowRuns = opaqueRuns != null && sy < opaqueRuns.length ? opaqueRuns[sy] : null;
            if (rowRuns == null) continue;
            for (int run = 0; run < rowRuns.length; run += 2) {
              int runStart = Math.max(startX, rowRuns[run]);
              int runEnd = Math.min(endX, rowRuns[run + 1]);
              for (int sx = runStart; sx < runEnd; sx++) {
                int c = source[srcRow + sx], a = c >>> 24;
                int px0 = spritePx0[sx];
                int px1 = spritePx1[sx];
                if (px0 >= px1) continue;
                int rgb = c & 0xFFFFFF;
                if (a == 255) {
                    for (int py = py0; py < py1; py++) {
                        int row = py * pw;
                        for (int px = px0; px < px1; px++) {
                            int idx = row + px;
                            if (z <= depth[idx] + 0.00015f) { dst[idx] = 0xFF000000 | (rgb); visiblePixels++; }
                        }
                    }
                } else {
                    int cr = (c >>> 16) & 255, cg = (c >>> 8) & 255, cb = c & 255, ia = 255 - a;
                    for (int py = py0; py < py1; py++) {
                        int row = py * pw;
                        for (int px = px0; px < px1; px++) {
                            int idx = row + px;
                            if (z > depth[idx] + 0.00015f) continue;
                            visiblePixels++;
                            int old = dst[idx];
                            int r = (cr * a + ((old >>> 16) & 255) * ia) / 255;
                            int g = (cg * a + ((old >>> 8) & 255) * ia) / 255;
                            int b = (cb * a + (old & 255) * ia) / 255;
                            dst[idx] = 0xFF000000 | ((r << 16) | (g << 8) | b);
                        }
                    }
                }
              }
            }
        }
        return visiblePixels > 0;
    }

    /** Draws a depth-tested sprite scaled to fixed logical screen dimensions. */
    public synchronized boolean drawDepthTestedImageScaled(Graphics target, int[] source, int[][] opaqueRuns,
                                                            int sw, int sh, int dw, int dh, int x, int y, float z) {
        if (target == null || source == null || depth == null || pw <= 0 || ph <= 0 || sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return false;
        double scale = target.scale();
        int clipX0 = Math.max(0, (int)Math.floor(target.getClipX() * scale));
        int clipY0 = Math.max(0, (int)Math.floor(target.getClipY() * scale));
        int clipX1 = Math.min(pw, (int)Math.ceil((target.getClipX() + target.getClipWidth()) * scale));
        int clipY1 = Math.min(ph, (int)Math.ceil((target.getClipY() + target.getClipHeight()) * scale));
        int logicalX0 = Math.max(x, target.getClipX()), logicalY0 = Math.max(y, target.getClipY());
        int logicalX1 = Math.min(x + dw, target.getClipX() + target.getClipWidth());
        int logicalY1 = Math.min(y + dh, target.getClipY() + target.getClipHeight());
        int[] dst = target.pixelBuffer();
        boolean drew = false;
        for (int ly = logicalY0; ly < logicalY1; ly++) {
            int sy = Math.min(sh - 1, (ly - y) * sh / dh);
            int[] rowRuns = opaqueRuns != null && sy < opaqueRuns.length ? opaqueRuns[sy] : null;
            if (rowRuns == null) continue;
            int py0 = Math.max(clipY0, (int)Math.floor(ly * scale));
            int py1 = Math.min(clipY1, (int)Math.floor((ly + 1) * scale));
            if (py0 >= py1) continue;
            int srcRow = sy * sw;
            // Convert each opaque source run to its destination span. The old
            // loop searched every run for every destination pixel, which was
            // particularly costly for leafy sprites with many transparent gaps.
            for (int run = 0; run + 1 < rowRuns.length; run += 2) {
                int runStart = Math.max(0, Math.min(sw, rowRuns[run]));
                int runEnd = Math.max(runStart, Math.min(sw, rowRuns[run + 1]));
                int runX0 = x + (int)(((long)runStart * dw + sw - 1L) / sw);
                int runX1 = x + (int)(((long)runEnd * dw + sw - 1L) / sw);
                int lx0 = Math.max(logicalX0, runX0), lx1 = Math.min(logicalX1, runX1);
                for (int lx = lx0; lx < lx1; lx++) {
                    int sx = Math.min(sw - 1, (lx - x) * sw / dw);
                    int c = source[srcRow + sx];
                    int a = c >>> 24;
                    if (a == 0) continue;
                    int px0 = Math.max(clipX0, (int)Math.floor(lx * scale));
                    int px1 = Math.min(clipX1, (int)Math.floor((lx + 1) * scale));
                    if (px0 >= px1) continue;
                    for (int py = py0; py < py1; py++) {
                        int row = py * pw;
                        for (int px = px0; px < px1; px++) {
                            int idx = row + px;
                            if (z > depth[idx] + 0.00015f) continue;
                            if (a == 255) dst[idx] = 0xFF000000 | (c & 0xFFFFFF);
                            else {
                                int inv = 255 - a, old = dst[idx];
                                int r = ((((c >>> 16) & 255) * a) + (((old >>> 16) & 255) * inv)) / 255;
                                int g = ((((c >>> 8) & 255) * a) + (((old >>> 8) & 255) * inv)) / 255;
                                int b = (((c & 255) * a) + ((old & 255) * inv)) / 255;
                                dst[idx] = 0xFF000000 | ((r << 16) | (g << 8) | b);
                            }
                            drew = true;
                        }
                    }
                }
            }
        }
        return drew;
    }

    public boolean isDepthMapValid() { return depthMapValid; }

    private void flushSoftware() {
        final int rows = clipY1 - clipY0;
        if (rows <= 0) { triCount = 0; return; }
        final int bandsMax = parallelBands();
        final int band = Math.max(16, (rows + bandsMax - 1) / bandsMax);
        final int chunks = (rows + band - 1) / band;
        final int y0 = clipY0;
        if (chunks == 1 || triCount < 24) {
            rasterRows(y0, clipY1);
        } else {
            buildBandBins(chunks, band, y0);
            final int[] offsets = bandOffsets, selected = bandTriangles;
            rally3d.desktop.Platform.parallelFor(chunks, c ->
                    rasterRows(selected, offsets[c], offsets[c + 1], y0 + c * band,
                            Math.min(clipY1, y0 + (c + 1) * band)));
        }
        depthMapValid = depthBound;
        triCount = 0;
    }

    /** Draws sky + the already clipped/batched geometry (+ billboards) into the world FBO. GL context current. */
    private void flushGpuWorld() {
        ensureGpu(pw, ph);
        worldTimer.poll();
        worldTimer.begin();
        glBindFramebuffer(GL_FRAMEBUFFER, gpuFramebuffer);
        glViewport(0, 0, pw, ph);
        glDisable(GL_SCISSOR_TEST);
        glColorMask(true, true, true, true);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glClearColor(0f, 0f, 0f, 0f);      // alpha 0 = "no world here": the compositor shows the UI layer / black
        glClearDepth(1.0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_SCISSOR_TEST);
        drawGpuSky();
        glScissor(clipX0, ph - clipY1, Math.max(0, clipX1 - clipX0), Math.max(0, clipY1 - clipY0));
        glUseProgram(gpuProgram);
        glUniform2f(gpuInvSize, 1.0f / pw, 1.0f / ph);
        glBindVertexArray(gpuVao);
        glBindBuffer(GL_ARRAY_BUFFER, gpuVbo);

        if (triCount > 0) {
            gpuVertices.clear();
            for (int t = 0; t < triCount; t++) appendGpuTriangle(t);
            gpuVertices.flip();
            glBufferData(GL_ARRAY_BUFFER, gpuVertices, GL_STREAM_DRAW);

            // Accurate depth in a separate pass: keeps coplanar road/terrain strips from punching holes in the textured surface.
            if (depthBound) {
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(GL_LEQUAL);
                glDepthMask(true);
                glColorMask(false, false, false, false);
                glUniform1i(gpuTextureEnabled, 0);
                glDrawArrays(GL_TRIANGLES, 0, triCount * 3);
                glColorMask(true, true, true, true);
                glDepthMask(false);
                glDisable(GL_DEPTH_TEST);
            }

            int batchStart = 0;
            int batchState = (int) queue[29];
            for (int t = 0; t <= triCount; t++) {
                int nextState = t == triCount ? -1 : (int) queue[t * REC + 29];
                if (nextState != batchState) {
                    drawGpuBatch(batchState, batchStart, t - batchStart);
                    batchStart = t;
                    batchState = nextState;
                }
            }
        }

        glBindVertexArray(0);
        glUseProgram(0);
        if (PARITY && triCount > 0 && depthBound && (++parityFrames % PARITY_EVERY) == 0 && parityRuns < PARITY_MAX_RUNS) {
            parityRuns++;
            try { parityCheck(parityRuns); } catch (RuntimeException e) { rally3d.core.RLog.caught("Graphics3D.parityCheck", e); }
            glBindFramebuffer(GL_FRAMEBUFFER, gpuFramebuffer);
        }
        if (billboardBatchActive) {
            renderGpuBillboards();
            gpuBillboardsRendered = true;
        }
        glDisable(GL_SCISSOR_TEST);
        depthMapValid = false;   // the depth buffer stays on the GPU; CPU sprites are never needed in this mode
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        worldTimer.end();
    }

    // ---- GPU timers + GPU vs software parity check (stage 2) ----
    private final rally3d.desktop.GpuTimer worldTimer = new rally3d.desktop.GpuTimer(9);
    /** Debug: run with -Drally3d.parity=true (run-gpu-parity.bat). Every PARITY_EVERY world frames the same triangle queue is also rasterized in software and compared. */
    private static final boolean PARITY = Boolean.getBoolean("rally3d.parity");
    private static final int PARITY_EVERY = 300, PARITY_MAX_RUNS = 3;
    private int parityFrames, parityRuns;
    private int[] parityPix; private float[] parityDepth; private IntBuffer parityRead;

    /** GPU world (sky + 3D, before billboards) vs software raster of the same queue; logs [gpu-parity] and writes rally3d-parity-N-{gpu,sw,diff}.png. */
    private void parityCheck(int run) {
        final int n = pw * ph;
        if (parityPix == null || parityPix.length != n) {
            parityPix = new int[n]; parityDepth = new float[n]; parityRead = org.lwjgl.BufferUtils.createIntBuffer(n);
        }
        glBindFramebuffer(GL_FRAMEBUFFER, gpuFramebuffer);
        glPixelStorei(GL_PACK_ALIGNMENT, 4);
        parityRead.clear();
        glReadPixels(0, 0, pw, ph, GL_BGRA, GL_UNSIGNED_BYTE, parityRead);
        final int[] realPix = pix; final float[] realDepth = depth;
        long t0 = System.nanoTime();
        try {
            pix = parityPix; depth = parityDepth;
            Arrays.fill(parityPix, 0); Arrays.fill(parityDepth, 1f);
            rasterRows(clipY0, clipY1);
        } finally { pix = realPix; depth = realDepth; }
        long swNanos = System.nanoTime() - t0;
        long total = 0, over2 = 0, over16 = 0, sumDiff = 0; int maxDiff = 0, worstX = -1, worstY = -1, worstS = 0, worstG = 0;
        final boolean save = run == 1;
        BufferedImage imgG = null, imgS = null, imgD = null;
        if (save) { imgG = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB); imgS = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB); imgD = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB); }
        for (int y = clipY0; y < clipY1; y++) {
            int grow = (ph - 1 - y) * pw;
            for (int x = clipX0; x < clipX1; x++) {
                int sw = parityPix[y * pw + x];
                int gp = parityRead.get(grow + x);
                if (save) { imgG.setRGB(x, y, gp & 0xFFFFFF); imgS.setRGB(x, y, sw & 0xFFFFFF); }
                if ((sw >>> 24) != 0xFF) continue;   // software did not draw 3D here (sky only)
                total++;
                int dr = Math.abs(((sw >> 16) & 255) - ((gp >> 16) & 255)), dg = Math.abs(((sw >> 8) & 255) - ((gp >> 8) & 255)), db = Math.abs((sw & 255) - (gp & 255));
                int d = Math.max(dr, Math.max(dg, db));
                sumDiff += d;
                if (d > 2) over2++;
                if (d > 16) over16++;
                if (d > maxDiff) { maxDiff = d; worstX = x; worstY = y; worstS = sw; worstG = gp; }
                if (save) { int h = Math.min(255, d * 8); imgD.setRGB(x, y, (h << 16) | (h << 8) | h); }
            }
        }
        rally3d.core.RLog.i("gpu-parity", String.format(java.util.Locale.ROOT,
                "run %d @ %dx%d: %d px compared (software-drawn 3D) | diff>2: %.2f%% | diff>16: %.2f%% | mean %.2f | max %d at (%d,%d) sw=%08X gpu=%08X | tris=%d | software raster 1 thread %.1f ms",
                run, pw, ph, total, total == 0 ? 0.0 : over2 * 100.0 / total, total == 0 ? 0.0 : over16 * 100.0 / total,
                total == 0 ? 0.0 : sumDiff / (double) total, maxDiff, worstX, worstY, worstS, worstG, triCount, swNanos / 1e6));
        if (save) {
            try {
                java.io.File dir = new java.io.File(System.getProperty("rally3d.logDir", System.getProperty("user.dir")));
                javax.imageio.ImageIO.write(imgG, "png", new java.io.File(dir, "rally3d-parity-gpu.png"));
                javax.imageio.ImageIO.write(imgS, "png", new java.io.File(dir, "rally3d-parity-sw.png"));
                javax.imageio.ImageIO.write(imgD, "png", new java.io.File(dir, "rally3d-parity-diff.png"));
                rally3d.core.RLog.i("gpu-parity", "wrote rally3d-parity-gpu.png / -sw.png / -diff.png (diff = |sw-gpu| x8) to " + dir);
            } catch (java.io.IOException e) { rally3d.core.RLog.caught("Graphics3D.parityCheck.png", e); }
        }
    }

    /** Sky strip (tiled horizontally, nearest) + flat sky/ground colour above/below it, inside the clip rectangle. */
    private void drawGpuSky() {
        final double k = scale;
        final int top = Math.max(clipY0, 0), bottom = Math.min(clipY1, ph);
        if (clipX1 <= clipX0 || bottom <= top) return;
        final int imgY0 = (int) Math.round(skyY * k), imgY1 = (int) Math.round((skyY + skyImgH) * k);
        final int w = clipX1 - clipX0;
        // flat colours (alpha 1)
        if (imgY0 > top) fillGpuRect(clipX0, top, w, Math.min(imgY0, bottom) - top, skyColorRgb);
        if (imgY1 < bottom) fillGpuRect(clipX0, Math.max(imgY1, top), w, bottom - Math.max(imgY1, top), groundColorRgb);
        int y0 = Math.max(imgY0, top), y1 = Math.min(imgY1, bottom);
        if (y1 <= y0) return;
        int tex = skyTextureId();
        if (tex == 0) return;
        fillGpuRect(clipX0, y0, w, y1 - y0, skyColorRgb);   // sky PNGs may be translucent: blend over the sky colour, not over "nothing"
        glScissor(clipX0, ph - y1, w, y1 - y0);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        glUseProgram(gpuProgram);
        glUniform2f(gpuInvSize, 1.0f / pw, 1.0f / ph);
        glUniform1i(gpuTextureEnabled, 1);
        glUniform1i(gpuModulate, 0);
        glUniform1i(gpuAlphaTest, 1);
        glUniform1i(gpuTextureSampler, 0);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex);
        glBindVertexArray(gpuVao);
        glBindBuffer(GL_ARRAY_BUFFER, gpuVbo);
        float x0 = (float) Math.round(skyFirstX * k), x1 = (float) pw;
        float qy0 = (float) imgY0, qy1 = (float) imgY1;
        float tileW = (float) (skyImgW * k);
        float u1 = tileW > 0f ? (x1 - x0) / tileW : 1f;
        gpuVertices.clear();
        appendGpuBillboardVertex(x0, qy0, 0.5f, 0f, 0f);
        appendGpuBillboardVertex(x1, qy0, 0.5f, u1, 0f);
        appendGpuBillboardVertex(x0, qy1, 0.5f, 0f, 1f);
        appendGpuBillboardVertex(x0, qy1, 0.5f, 0f, 1f);
        appendGpuBillboardVertex(x1, qy0, 0.5f, u1, 0f);
        appendGpuBillboardVertex(x1, qy1, 0.5f, u1, 1f);
        gpuVertices.flip();
        glBufferData(GL_ARRAY_BUFFER, gpuVertices, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glDisable(GL_BLEND);
        glUniform1i(gpuAlphaTest, 0);
        glBindVertexArray(0);
        glUseProgram(0);
        glDepthMask(true);
    }

    private void fillGpuRect(int x, int y, int w, int h, int rgb) {
        if (w <= 0 || h <= 0) return;
        glScissor(x, ph - (y + h), w, h);
        glClearColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private int skyTextureId() {
        Object key = skyKey;
        Integer cached = skyTextures.get(key);
        if (cached != null) return cached.intValue();
        if (!(key instanceof javax.microedition.lcdui.Image)) return 0;
        javax.microedition.lcdui.Image img = (javax.microedition.lcdui.Image) key;
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0 || w > 16384 || h > 16384) return 0;
        if (skyTextures.size() >= 6) {   // images change per track: keep the cache small
            for (Integer old : skyTextures.values()) glDeleteTextures(old.intValue());
            skyTextures.clear();
        }
        int[] argb = new int[w * h];
        img.getRGB(argb, 0, w, 0, 0, w, h);
        IntBuffer buf = memAllocInt(w * h);
        try {
            buf.put(argb).flip();
            int id = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, id);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_BYTE, buf);
            skyTextures.put(key, Integer.valueOf(id));
            return id;
        } finally { memFree(buf); }
    }

    private void renderGpuBillboards() {
        if (billboardCount == 0) return;
        glBindFramebuffer(GL_FRAMEBUFFER, gpuFramebuffer);
        glDisable(GL_SCISSOR_TEST);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glDepthMask(false);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);   // colour blends, destination alpha stays 1
        glColorMask(true, true, true, true);
        glUseProgram(gpuProgram);
        glUniform2f(gpuInvSize, 1.0f / pw, 1.0f / ph);
        glUniform1i(gpuTextureEnabled, 1);
        glUniform1i(gpuModulate, 0);
        glUniform1i(gpuAlphaTest, 1);
        glUniform1i(gpuTextureSampler, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(gpuVao);
        glBindBuffer(GL_ARRAY_BUFFER, gpuVbo);

        sortBillboardsBackToFront();
        int first = 0;
        while (first < billboardCount) {
            int orderedIndex = billboardOrder[first];
            Texture2D texture = billboardTextures[orderedIndex];
            gpuVertices.clear();
            int end = first;
            while (end < billboardCount && billboardTextures[billboardOrder[end]] == texture) {
                appendGpuBillboard(billboardOrder[end]);
                end++;
            }
            gpuVertices.flip();
            glBufferData(GL_ARRAY_BUFFER, gpuVertices, GL_STREAM_DRAW);
            glBindTexture(GL_TEXTURE_2D, textureId(texture));
            glDrawArrays(GL_TRIANGLES, 0, gpuVertices.limit() / 9);
            first = end;
        }

        glUniform1i(gpuAlphaTest, 0);
        glBindVertexArray(0);
        glUseProgram(0);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(true);
    }

    private void sortBillboardsBackToFront() {
        for (int i = 0; i < billboardCount; i++) billboardOrder[i] = i;
        // The active track view normally has only a few dozen sprites. Insertion
        // sort is allocation-free and preserves stable order for equal depths.
        for (int i = 1; i < billboardCount; i++) {
            int item = billboardOrder[i];
            int j = i - 1;
            while (j >= 0 && billboardDepths[billboardOrder[j]] < billboardDepths[item]) {
                billboardOrder[j + 1] = billboardOrder[j--];
            }
            billboardOrder[j + 1] = item;
        }
    }

    private void appendGpuBillboard(int i) {
        float x0 = billboardGpuX[i] * (float)scale;
        float y0 = billboardGpuY[i] * (float)scale;
        float x1 = (billboardGpuX[i] + billboardGpuWidths[i]) * (float)scale;
        float y1 = (billboardGpuY[i] + billboardGpuHeights[i]) * (float)scale;
        // Match the small equality tolerance used by the software depth test.
        // With LEQUAL alone, quantization differences can hide an entire sprite
        // when its anchor is coplanar with the terrain under its feet.
        float z = Math.max(0.0f, billboardDepths[i] - 0.00015f);
        int uvOffset = i * 4;
        float u0 = billboardUvs[uvOffset], v0 = billboardUvs[uvOffset + 1];
        float u1 = billboardUvs[uvOffset + 2], v1 = billboardUvs[uvOffset + 3];
        appendGpuBillboardVertex(x0, y0, z, u0, v0);
        appendGpuBillboardVertex(x1, y0, z, u1, v0);
        appendGpuBillboardVertex(x0, y1, z, u0, v1);
        appendGpuBillboardVertex(x0, y1, z, u0, v1);
        appendGpuBillboardVertex(x1, y0, z, u1, v0);
        appendGpuBillboardVertex(x1, y1, z, u1, v1);
    }

    private void appendGpuBillboardVertex(float x, float y, float z, float u, float v) {
        if (gpuVertices.remaining() < 9) {
            int oldPosition = gpuVertices.position();
            gpuVertices = memRealloc(gpuVertices, Math.max(gpuVertices.capacity() * 2, oldPosition + 9 * 1024));
            gpuVertices.position(oldPosition);
        }
        gpuVertices.put(x).put(y).put(z).put(1.0f).put(u).put(v).put(255.0f).put(255.0f).put(255.0f);
    }

    private void ensureGpu(int width, int height) {
        if (gpuProgram == 0) {
            gpuProgram = createGpuProgram();
            gpuTextureSampler = glGetUniformLocation(gpuProgram, "meshTexture");
            gpuTextureEnabled = glGetUniformLocation(gpuProgram, "useTexture");
            gpuModulate = glGetUniformLocation(gpuProgram, "modulateTexture");
            gpuInvSize = glGetUniformLocation(gpuProgram, "invSize");
            gpuAlphaTest = glGetUniformLocation(gpuProgram, "alphaTest");
            gpuFramebuffer = glGenFramebuffers();
            gpuColor = glGenTextures();
            gpuDepth = glGenRenderbuffers();
            gpuVao = glGenVertexArrays();
            gpuVbo = glGenBuffers();
            glBindVertexArray(gpuVao);
            glBindBuffer(GL_ARRAY_BUFFER, gpuVbo);
            int stride = 9 * Float.BYTES;
            glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
            glEnableVertexAttribArray(1); glVertexAttribPointer(1, 1, GL_FLOAT, false, stride, 2L * Float.BYTES);
            glEnableVertexAttribArray(2); glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 3L * Float.BYTES);
            glEnableVertexAttribArray(3); glVertexAttribPointer(3, 2, GL_FLOAT, false, stride, 4L * Float.BYTES);
            glEnableVertexAttribArray(4); glVertexAttribPointer(4, 3, GL_FLOAT, false, stride, 6L * Float.BYTES);
            glBindVertexArray(0);
            gpuVertices = memAllocFloat(9 * 3 * 4096);
        }
        if (gpuWidth != width || gpuHeight != height) {
            gpuWidth = width; gpuHeight = height; worldHeld = false;
            glBindTexture(GL_TEXTURE_2D, gpuColor);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            glBindFramebuffer(GL_FRAMEBUFFER, gpuFramebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, gpuColor, 0);
            glBindRenderbuffer(GL_RENDERBUFFER, gpuDepth);
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, gpuDepth);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("OpenGL framebuffer is incomplete");
        }
    }

    private void appendGpuTriangle(int triangle) {
        int o = triangle * REC;
        double x0 = queue[o], y0 = queue[o + 1];
        appendGpuVertex(o, x0, y0);
        appendGpuVertex(o, queue[o + 2], queue[o + 3]);
        appendGpuVertex(o, queue[o + 4], queue[o + 5]);
    }

    private void appendGpuVertex(int o, double x, double y) {
        double dx = x - queue[o], dy = y - queue[o + 1];
        if (gpuVertices.remaining() < 9) {
            int oldPosition = gpuVertices.position();
            int newCapacity = Math.max(gpuVertices.capacity() * 2, oldPosition + 9 * 3 * 1024);
            gpuVertices = memRealloc(gpuVertices, newCapacity);
            gpuVertices.position(oldPosition);
        }
        gpuVertices.put((float) x).put((float) y);
        for (int a = 0; a < 7; a++)
            gpuVertices.put((float) (queue[o + 6 + a] + queue[o + 13 + a] * dx + queue[o + 20 + a] * dy));
    }

    private void drawGpuBatch(int stateIndex, int firstTriangle, int triangleCount) {
        if (triangleCount <= 0) return;
        State state = states[stateIndex];
        Texture2D texture = state.tex;
        glUniform1i(gpuTextureEnabled, texture == null ? 0 : 1);
        glUniform1i(gpuModulate, state.modulate ? 1 : 0);
        glUniform1i(gpuAlphaTest, 0);
        glActiveTexture(GL_TEXTURE0);
        int textureId = texture == null ? 0 : textureId(texture);
        glBindTexture(GL_TEXTURE_2D, textureId);
        glUniform1i(gpuTextureSampler, 0);
        if (state.depthTest) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        glDepthMask(state.depthWrite);
        glDepthFunc(GL_LEQUAL);
        glColorMask(!state.noColorWrite, !state.noColorWrite, !state.noColorWrite, true);
        glDrawArrays(GL_TRIANGLES, firstTriangle * 3, triangleCount * 3);
    }

    private final java.util.ArrayList<Integer> pendingTextureDelete = new java.util.ArrayList<Integer>();

    /** Frees the GPU copy of a texture (add-on skins that are no longer used). Safe from any thread: the GL delete happens on the next draw. */
    public void releaseTexture(Texture2D texture) {
        synchronized (pendingTextureDelete) {
            Integer id = gpuTextures.remove(texture);
            if (id != null) pendingTextureDelete.add(id);
        }
    }

    private int textureId(Texture2D texture) {
        synchronized (pendingTextureDelete) {
            for (Integer dead : pendingTextureDelete) glDeleteTextures(dead.intValue());
            pendingTextureDelete.clear();
        }
        Integer cached = gpuTextures.get(texture);
        if (cached != null) return cached.intValue();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        ByteBuffer rgba = memAlloc(texture.w * texture.h * 4);
        try {
            for (int argb : texture.rgb) rgba.put((byte) (argb >>> 16)).put((byte) (argb >>> 8))
                    .put((byte) argb).put((byte) (argb >>> 24));
            rgba.flip();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texture.w, texture.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        } finally {
            memFree(rgba);
        }
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        synchronized (pendingTextureDelete) { gpuTextures.put(texture, Integer.valueOf(id)); }
        return id;
    }

    private static int createGpuProgram() {
        String vertexSource = "#version 330 core\n" +
                "layout(location=0) in vec2 position; layout(location=1) in float depth;\n" +
                "layout(location=2) in float invW; layout(location=3) in vec2 uvOverW;\n" +
                "layout(location=4) in vec3 colorOverW; uniform vec2 invSize;\n" +
                "noperspective out float vInvW; noperspective out vec2 vUvOverW;\n" +
                "noperspective out vec3 vColorOverW;\n" +
                "void main(){vInvW=invW;vUvOverW=uvOverW;vColorOverW=colorOverW;" +
                "gl_Position=vec4(position.x*invSize.x*2.0-1.0,1.0-position.y*invSize.y*2.0,depth*2.0-1.0,1.0);}\n";
        String fragmentSource = "#version 330 core\n" +
                "noperspective in float vInvW; noperspective in vec2 vUvOverW;\n" +
                "noperspective in vec3 vColorOverW; out vec4 outColor;\n" +
                "uniform sampler2D meshTexture; uniform int useTexture; uniform int modulateTexture; uniform int alphaTest;\n" +
                "void main(){vec3 c=clamp(vColorOverW/max(vInvW,0.000001),0.0,255.0)/255.0;float a=1.0;" +
                "if(useTexture!=0){vec4 texel=texture(meshTexture,vUvOverW/max(vInvW,0.000001));" +
                "if(alphaTest!=0&&texel.a<0.003)discard;a=texel.a;vec3 t=texel.rgb;" +
                "if(modulateTexture!=0)c*=t;else c=t;}outColor=vec4(c,alphaTest!=0?a:1.0);}\n";
        int vertex = compileGpuShader(GL_VERTEX_SHADER, vertexSource);
        int fragment = compileGpuShader(GL_FRAGMENT_SHADER, fragmentSource);
        int program = glCreateProgram();
        glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String error = glGetProgramInfoLog(program); glDeleteProgram(program);
            throw new IllegalStateException("OpenGL scene shader link failed: " + error);
        }
        glDeleteShader(vertex); glDeleteShader(fragment);
        return program;
    }

    private static int compileGpuShader(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(shader); glDeleteShader(shader);
            throw new IllegalStateException("OpenGL scene shader compile failed: " + error);
        }
        return shader;
    }

    private void buildBandBins(int chunks, int band, int y0) {
        if (bandCounts.length < chunks) {
            bandCounts = Arrays.copyOf(bandCounts, chunks);
            bandCursors = Arrays.copyOf(bandCursors, chunks);
        }
        if (bandOffsets.length < chunks + 1) bandOffsets = Arrays.copyOf(bandOffsets, chunks + 1);
        Arrays.fill(bandCounts, 0, chunks, 0);

        // Count only the bands touched by each triangle.
        for (int t = 0; t < triCount; t++) {
            int o = t * REC;
            int first = Math.max(0, ((int) queue[o + 27] - y0) / band);
            int last = Math.min(chunks - 1, ((int) queue[o + 28] - 1 - y0) / band);
            for (int c = first; c <= last; c++) bandCounts[c]++;
        }
        bandOffsets[0] = 0;
        for (int c = 0; c < chunks; c++) {
            bandOffsets[c + 1] = bandOffsets[c] + bandCounts[c];
            bandCursors[c] = bandOffsets[c];
        }
        int needed = bandOffsets[chunks];
        if (bandTriangles.length < needed) bandTriangles = Arrays.copyOf(bandTriangles, needed);

        // Fill each band's compact list of relevant triangles.
        for (int t = 0; t < triCount; t++) {
            int o = t * REC;
            int first = Math.max(0, ((int) queue[o + 27] - y0) / band);
            int last = Math.min(chunks - 1, ((int) queue[o + 28] - 1 - y0) / band);
            for (int c = first; c <= last; c++) bandTriangles[bandCursors[c]++] = t;
        }
    }

    private void rasterRows(int by0, int by1) {
        rasterRows(null, 0, triCount, by0, by1);
    }

    private static final double[] INV_LEN = new double[17];
    static { for (int i = 1; i < INV_LEN.length; i++) INV_LEN[i] = 1.0 / i; }

    private void rasterRows(int[] selected, int from, int to, int by0, int by1) {
        final double[] q = queue;
        final int[] dst = pix;
        final float[] zb = depth;
        final int pitch = pw;
        for (int entry = from; entry < to; entry++) {
            final int t = selected == null ? entry : selected[entry];
            final int o = t * REC;
            final int ra = (int) q[o + 27], rb = (int) q[o + 28];
            if (rb <= by0 || ra >= by1) continue;
            final State st = states[(int) q[o + 29]];
            final double x0 = q[o], y0 = q[o + 1], x1 = q[o + 2], y1 = q[o + 3];
            final double s02 = q[o + 30], s01 = q[o + 31], s12 = q[o + 32];
            final Texture2D tex = st.tex;
            final boolean dt = st.depthTest && zb != null, dw = st.softDepthWrite && zb != null;
            final boolean textured = tex != null, modulate = st.modulate;
            final int[] tp = textured ? tex.rgb : null;
            final int wMask = textured ? tex.wMask : 0, hMask = textured ? tex.hMask : 0, wShift = textured ? tex.wShift : 0;
            final double tw65 = textured ? tex.w * 65536.0 : 0, th65 = textured ? tex.h * 65536.0 : 0;
            final double z0 = q[o + 6], zx = q[o + 13], zy = q[o + 20];
            final double w0 = q[o + 7], wx = q[o + 14], wy = q[o + 21];
            final double u0 = q[o + 8], ux = q[o + 15], uy = q[o + 22];
            final double v0 = q[o + 9], vx_ = q[o + 16], vy_ = q[o + 23];
            final double r0 = q[o + 10], rx = q[o + 17], ry = q[o + 24];
            final double g0 = q[o + 11], gx = q[o + 18], gy = q[o + 25];
            final double b0 = q[o + 12], bx = q[o + 19], by = q[o + 26];
            final boolean needColor = !textured || modulate;
            final int yEnd = Math.min(rb, by1), cx0 = clipX0, cx1 = clipX1;
            for (int y = Math.max(ra, by0); y < yEnd; y++) {
                final double yc = y + 0.5, dy = yc - y0;
                final double xa = x0 + dy * s02;
                final double xb = yc < y1 ? x0 + dy * s01 : x1 + (yc - y1) * s12;
                final double lo = Math.min(xa, xb), hi = Math.max(xa, xb);
                final int xs = Math.max(cx0, (int) Math.ceil(lo - 0.5));
                final int xe = Math.min(cx1, (int) Math.ceil(hi - 0.5));
                if (xs >= xe) continue;
                final int row = y * pitch;
                // Row-constant parts of every plane equation (the per-pixel part is just base + gradX * dx).
                final double wB = w0 + wy * dy, zB = z0 + zy * dy;
                final double uB = u0 + uy * dy, vB = v0 + vy_ * dy;
                final double rB = r0 + ry * dy, gB = g0 + gy * dy, bB = b0 + by * dy;
                double dxs = xs + 0.5 - x0;
                // Span ends are shared with the next span's start: one reciprocal per span instead of two.
                double invS = 1.0 / (wB + wx * dxs);
                double uS = 0, vS = 0, rS = 0, gS = 0, bS = 0;
                if (textured) { uS = (uB + ux * dxs) * invS; vS = (vB + vx_ * dxs) * invS; }
                if (needColor) {
                    rS = clamp255((rB + rx * dxs) * invS); gS = clamp255((gB + gx * dxs) * invS); bS = clamp255((bB + bx * dxs) * invS);
                }
                int x = xs;
                while (x < xe) {
                    final int len = Math.min(16, xe - x);
                    final double fl = INV_LEN[len];
                    final double dxe = dxs + len;
                    final double invE = 1.0 / (wB + wx * dxe);
                    int iu = 0, iv = 0, du = 0, dv = 0, ir = 0, ig = 0, ib = 0, dr = 0, dg = 0, db = 0;
                    double uE = 0, vE = 0, rE = 0, gE = 0, bE = 0;
                    if (textured) {
                        uE = (uB + ux * dxe) * invE; vE = (vB + vx_ * dxe) * invE;
                        iu = (int) (uS * tw65); du = (int) ((uE - uS) * tw65 * fl);
                        iv = (int) (vS * th65); dv = (int) ((vE - vS) * th65 * fl);
                    }
                    if (needColor) {
                        rE = clamp255((rB + rx * dxe) * invE); gE = clamp255((gB + gx * dxe) * invE); bE = clamp255((bB + bx * dxe) * invE);
                        ir = (int) (rS * 65536.0); dr = (int) ((rE - rS) * 65536.0 * fl);
                        ig = (int) (gS * 65536.0); dg = (int) ((gE - gS) * 65536.0 * fl);
                        ib = (int) (bS * 65536.0); db = (int) ((bE - bS) * 65536.0 * fl);
                    }
                    double z = zB + zx * dxs;
                    final double dz = zx;
                    int idx = row + x;
                    final int end = idx + len;
                    if (!textured) {
                        if (dt) {
                        for (; idx < end; idx++) {
                            if (z > zb[idx]) { z += dz; ir += dr; ig += dg; ib += db; continue; }
                            dst[idx] = 0xFF000000 | (((ir >> 16) << 16) | ((ig >> 16) << 8) | (ib >> 16));
                            if (dw) zb[idx] = (float) z;
                            z += dz; ir += dr; ig += dg; ib += db;
                        }
                        } else {
                        for (; idx < end; idx++) {
                            dst[idx] = 0xFF000000 | (((ir >> 16) << 16) | ((ig >> 16) << 8) | (ib >> 16));
                            if (dw) zb[idx] = (float) z;
                            z += dz; ir += dr; ig += dg; ib += db;
                        }
                        }
                    } else if (!modulate) {
                        if (dt) {
                        for (; idx < end; idx++) {
                            if (z > zb[idx]) { z += dz; iu += du; iv += dv; continue; }
                            dst[idx] = 0xFF000000 | (tp[(((iv >> 16) & hMask) << wShift) | ((iu >> 16) & wMask)] & 0xFFFFFF);
                            if (dw) zb[idx] = (float) z;
                            z += dz; iu += du; iv += dv;
                        }
                        } else {
                        for (; idx < end; idx++) {
                            dst[idx] = 0xFF000000 | (tp[(((iv >> 16) & hMask) << wShift) | ((iu >> 16) & wMask)] & 0xFFFFFF);
                            if (dw) zb[idx] = (float) z;
                            z += dz; iu += du; iv += dv;
                        }
                        }
                    } else {
                        if (dt && dw) {   // common case (opaque world): no per-pixel depth-write branch
                        for (; idx < end; idx++) {
                            if (z > zb[idx]) { z += dz; iu += du; iv += dv; ir += dr; ig += dg; ib += db; continue; }
                            int texel = tp[(((iv >> 16) & hMask) << wShift) | ((iu >> 16) & wMask)];
                            int rr = ((((texel >> 16) & 255) * (ir >> 16)) + 255) >> 8;
                            int gg = ((((texel >> 8) & 255) * (ig >> 16)) + 255) >> 8;
                            int bb = (((texel & 255) * (ib >> 16)) + 255) >> 8;
                            dst[idx] = 0xFF000000 | ((rr << 16) | (gg << 8) | bb);
                            zb[idx] = (float) z;
                            z += dz; iu += du; iv += dv; ir += dr; ig += dg; ib += db;
                        }
                        } else if (dt) {
                        for (; idx < end; idx++) {
                            if (z > zb[idx]) { z += dz; iu += du; iv += dv; ir += dr; ig += dg; ib += db; continue; }
                            int texel = tp[(((iv >> 16) & hMask) << wShift) | ((iu >> 16) & wMask)];
                            int rr = ((((texel >> 16) & 255) * (ir >> 16)) + 255) >> 8;
                            int gg = ((((texel >> 8) & 255) * (ig >> 16)) + 255) >> 8;
                            int bb = (((texel & 255) * (ib >> 16)) + 255) >> 8;
                            dst[idx] = 0xFF000000 | ((rr << 16) | (gg << 8) | bb);
                            z += dz; iu += du; iv += dv; ir += dr; ig += dg; ib += db;
                        }
                        } else {
                        for (; idx < end; idx++) {
                            int texel = tp[(((iv >> 16) & hMask) << wShift) | ((iu >> 16) & wMask)];
                            int rr = ((((texel >> 16) & 255) * (ir >> 16)) + 255) >> 8;
                            int gg = ((((texel >> 8) & 255) * (ig >> 16)) + 255) >> 8;
                            int bb = (((texel & 255) * (ib >> 16)) + 255) >> 8;
                            dst[idx] = 0xFF000000 | ((rr << 16) | (gg << 8) | bb);
                            if (dw) zb[idx] = (float) z;
                            z += dz; iu += du; iv += dv; ir += dr; ig += dg; ib += db;
                        }
                        }
                    }
                    dxs = dxe; invS = invE; uS = uE; vS = vE; rS = rE; gS = gE; bS = bE;
                    x += len;
                }
            }
        }
    }

    private static double clamp255(double v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
}
