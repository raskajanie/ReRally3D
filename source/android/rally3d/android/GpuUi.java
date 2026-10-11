package rally3d.android;

import static android.opengl.GLES30.*;

import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.GraphicsBridge;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.UiRecorder;

/**
 * GPU 2D (GLES 3.0): replays the UiRecorder list of lcdui.Graphics into the UI framebuffer (RGBA8, PREMULTIPLIED alpha, row 0 = top of the picture,
 * internal render resolution) with one textured/coloured quad batcher. Rects, translucent rects, lines, images/regions, strings and drawRGB.
 * Strings are rasterised once by android.graphics (GraphicsBridge.renderString) into a cached white-on-transparent texture and tinted on the GPU.
 * EGL context must be current; called from GpuRenderer.presentLayers on the game thread.
 */
final class GpuUi {
    private static final int FLOATS = 8;                 // x y u v r g b a
    private static final int MAX_QUADS = 4096;
    private static final int MAX_IMAGE_TEXTURES = 320;
    private static final long MAX_IMAGE_BYTES = 160L << 20;
    private static final int MAX_STRING_TEXTURES = 900;

    private static final class Tex { int id, w, h; }
    private static final class StrKey {
        final Font font; final String text; final int scaleBits; final int hash;
        StrKey(Font f, String s, int sb) { font = f; text = s; scaleBits = sb; hash = System.identityHashCode(f) * 31 * 31 + s.hashCode() * 31 + sb; }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof StrKey)) return false;
            StrKey k = (StrKey) o;
            return k.font == font && k.scaleBits == scaleBits && k.text.equals(text);
          }
    }

    private int program, vao, vbo, whiteTexture, viewportLoc, texLoc;
    private FloatBuffer verts;
    private int quads, boundTex = -1;
    private int clipX0, clipY0, clipX1, clipY1, surfaceW, surfaceH;
    private final IdentityHashMap<Image, Tex> imageTextures = new IdentityHashMap<Image, Tex>();
    private long imageBytes;
    private final LinkedHashMap<StrKey, Tex> stringTextures = new LinkedHashMap<StrKey, Tex>(512, 0.75f, true);

    void init() {
        if (program != 0) return;
        program = Gl.program("GPU UI",
                "#version 300 es\n" +
                "layout(location=0) in vec2 pos; layout(location=1) in vec2 uvIn; layout(location=2) in vec4 colIn;\n" +
                "uniform vec2 viewport; out vec2 uv; out vec4 col;\n" +
                "void main(){ uv=uvIn; col=colIn; gl_Position=vec4(pos.x/viewport.x*2.0-1.0, pos.y/viewport.y*2.0-1.0, 0.0, 1.0); }\n",
                "#version 300 es\n" +
                "precision highp float;\n" +
                "in vec2 uv; in vec4 col; out vec4 outColor; uniform sampler2D tex;\n" +
                "void main(){ vec4 t=texture(tex,uv); float a=t.a*col.a; outColor=vec4(t.rgb*col.rgb*a, a); }\n");
        viewportLoc = glGetUniformLocation(program, "viewport");
        texLoc = glGetUniformLocation(program, "tex");
        vao = Gl.genVertexArray();
        vbo = Gl.genBuffer();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        Gl.bufferSize(GL_ARRAY_BUFFER, MAX_QUADS * 6 * FLOATS * 4, GL_STREAM_DRAW);
        int stride = FLOATS * 4;
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 8);
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 16);
        glBindVertexArray(0);
        verts = Gl.allocFloats(MAX_QUADS * 6 * FLOATS);
        whiteTexture = upload(new int[] { 0xFFFFFFFF }, 1, 1);
        rally3d.core.RLog.i("gpu", "GPU UI renderer ready (GLES3 quad batcher, string/image texture caches)");
      }

    void dispose() {
        deleteAllImages();
        for (Tex t : stringTextures.values()) Gl.deleteTexture(t.id);
        stringTextures.clear();
        if (whiteTexture != 0) Gl.deleteTexture(whiteTexture);
        if (vbo != 0) Gl.deleteBuffer(vbo);
        if (vao != 0) Gl.deleteVertexArray(vao);
        if (program != 0) glDeleteProgram(program);
        verts = null;
        program = vao = vbo = whiteTexture = 0;
      }

    /** Replays the list into the currently bound framebuffer (viewport already w x h). The list is NOT reset here. */
    void execute(UiRecorder r, int w, int h, double scale) {
        if (program == 0) init();
        if (w != surfaceW || h != surfaceH) { surfaceW = w; surfaceH = h; clipX0 = 0; clipY0 = 0; clipX1 = w; clipY1 = h; }
        if (r.overflow > 0) { rally3d.core.RLog.w("gpu", "GPU UI: recorded op list overflowed " + r.overflow + " time(s) in a frame, ops dropped"); r.overflow = 0; }
        quads = 0; boundTex = -1;
        verts.clear();
        glUseProgram(program);
        glUniform2f(viewportLoc, w, h);
        glUniform1i(texLoc, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);   // premultiplied
        glEnable(GL_SCISSOR_TEST);
        applyScissor();
        final int[] ops = r.ops;
        final Object[] refs = r.refs;
        final int n = r.n;
        int i = 0;
        while (i < n) {
            switch (ops[i]) {
                case UiRecorder.CLEAR: {
                    flush();
                    glDisable(GL_SCISSOR_TEST);
                    glClearColor(0f, 0f, 0f, 0f);
                    glClear(GL_COLOR_BUFFER_BIT);
                    glEnable(GL_SCISSOR_TEST);
                    i += 1;
                    break;
                }
                case UiRecorder.CLIP: {
                    flush();
                    clipX0 = clamp(ops[i + 1], 0, w); clipY0 = clamp(ops[i + 2], 0, h);
                    clipX1 = clamp(ops[i + 3], clipX0, w); clipY1 = clamp(ops[i + 4], clipY0, h);
                    applyScissor();
                    i += 5;
                    break;
                }
                case UiRecorder.FILL: {
                    bind(whiteTexture);
                    quad(ops[i + 1], ops[i + 2], ops[i + 3], ops[i + 4], 0f, 0f, 1f, 1f, ops[i + 5]);
                    i += 6;
                    break;
                }
                case UiRecorder.LINE: {
                    bind(whiteTexture);
                    line(ops[i + 1], ops[i + 2], ops[i + 3], ops[i + 4], ops[i + 5], (float) Math.max(1.0, scale));
                    i += 6;
                    break;
                }
                case UiRecorder.IMAGE: {
                    Image img = (Image) refs[ops[i + 1]];
                    Tex t = imageTexture(img);
                    if (t != null) {
                        bind(t.id);
                        quad(ops[i + 2], ops[i + 3], ops[i + 4], ops[i + 5],
                                ops[i + 6] / (float) t.w, ops[i + 7] / (float) t.h, ops[i + 8] / (float) t.w, ops[i + 9] / (float) t.h, 0xFFFFFFFF);
                    }
                    i += 10;
                    break;
                }
                case UiRecorder.STRING: {
                    Tex t = stringTexture((String) refs[ops[i + 1]], (Font) refs[ops[i + 2]], scale);
                    if (t != null) {
                        bind(t.id);
                        int left = ops[i + 3], top = ops[i + 4];
                        quad(left, top, left + t.w, top + t.h, 0f, 0f, 1f, 1f, ops[i + 5]);
                    }
                    i += 6;
                    break;
                }
                case UiRecorder.RGB: {
                    flush();
                    int[] data = (int[]) refs[ops[i + 1]];
                    int rw = ops[i + 2], rh = ops[i + 3];
                    if (ops[i + 4] == 0) { for (int k = 0; k < data.length; k++) data[k] |= 0xFF000000; }
                    int id = upload(data, rw, rh);
                    bind(id);
                    quad(ops[i + 5], ops[i + 6], ops[i + 7], ops[i + 8], 0f, 0f, 1f, 1f, 0xFFFFFFFF);
                    flush();
                    Gl.deleteTexture(id);
                    boundTex = -1;
                    i += 9;
                    break;
                }
                default:
                    throw new IllegalStateException("GPU UI: unknown recorded op " + ops[i] + " at " + i);
            }
        }
        flush();
        glDisable(GL_BLEND);
        glDisable(GL_SCISSOR_TEST);
        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glUseProgram(0);
        trimCaches();
      }

    private static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private void applyScissor() { glScissor(clipX0, clipY0, Math.max(0, clipX1 - clipX0), Math.max(0, clipY1 - clipY0)); }

    private void bind(int tex) {
        if (tex == boundTex) return;
        flush();
        glBindTexture(GL_TEXTURE_2D, tex);
        boundTex = tex;
    }

    private void flush() {
        if (quads == 0) return;
        verts.flip();
        Gl.bufferSize(GL_ARRAY_BUFFER, MAX_QUADS * 6 * FLOATS * 4, GL_STREAM_DRAW);   // orphan: no stall on the previous batch
        Gl.bufferSub(GL_ARRAY_BUFFER, 0, verts);
        glDrawArrays(GL_TRIANGLES, 0, quads * 6);
        verts.clear();
        quads = 0;
      }

    private void vertex(float x, float y, float u, float v, float r, float g, float b, float a) {
        verts.put(x).put(y).put(u).put(v).put(r).put(g).put(b).put(a);
    }

    private void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        if (x1 <= x0 || y1 <= y0) return;
        if (quads >= MAX_QUADS) flush();
        float a = ((argb >>> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        vertex(x0, y0, u0, v0, r, g, b, a); vertex(x1, y0, u1, v0, r, g, b, a); vertex(x0, y1, u0, v1, r, g, b, a);
        vertex(x1, y0, u1, v0, r, g, b, a); vertex(x1, y1, u1, v1, r, g, b, a); vertex(x0, y1, u0, v1, r, g, b, a);
        quads++;
      }

    /** Line from pixel centre to pixel centre, thickness px wide, square caps (covers both end pixels like the CPU drawLine). */
    private void line(float x1, float y1, float x2, float y2, int argb, float thickness) {
        if (quads >= MAX_QUADS) flush();
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        float ux, uy;
        if (len < 1e-4f) { ux = 1f; uy = 0f; } else { ux = dx / len; uy = dy / len; }
        float h = thickness * 0.5f, ex = ux * h, ey = uy * h, px = -uy * h, py = ux * h;
        float ax = x1 - ex + px, ay = y1 - ey + py, bx = x1 - ex - px, by = y1 - ey - py;
        float cx = x2 + ex + px, cy = y2 + ey + py, ddx = x2 + ex - px, ddy = y2 + ey - py;
        float a = ((argb >>> 24) & 255) / 255f, r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        vertex(ax, ay, 0.5f, 0.5f, r, g, b, a); vertex(bx, by, 0.5f, 0.5f, r, g, b, a); vertex(cx, cy, 0.5f, 0.5f, r, g, b, a);
        vertex(bx, by, 0.5f, 0.5f, r, g, b, a); vertex(ddx, ddy, 0.5f, 0.5f, r, g, b, a); vertex(cx, cy, 0.5f, 0.5f, r, g, b, a);
        quads++;
      }

    // ---------------- textures ----------------
    private static int upload(int[] argb, int w, int h) {
        int id = Gl.genTexture();
        glBindTexture(GL_TEXTURE_2D, id);
        Gl.texParams(GL_NEAREST, GL_CLAMP_TO_EDGE, GL_CLAMP_TO_EDGE);
        Gl.texImageArgb(w, h, argb);
        return id;
      }

    private Tex imageTexture(Image img) {
        if (img == null) return null;
        Tex t = imageTextures.get(img);
        if (t != null) return t;
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0) return null;
        boundTex = -1;   // upload() rebinds GL_TEXTURE_2D
        flush();
        t = new Tex(); t.w = w; t.h = h; t.id = upload(GraphicsBridge.imageArgb(img), w, h);
        imageTextures.put(img, t);
        imageBytes += (long) w * h * 4;
        return t;
      }

    private Tex stringTexture(String s, Font f, double scale) {
        if (s == null || s.isEmpty() || f == null) return null;
        StrKey key = new StrKey(f, s, Float.floatToIntBits((float) scale));
        Tex t = stringTextures.get(key);
        if (t != null) return t;
        int[] wh = new int[2];
        int[] px = GraphicsBridge.renderString(f, s, scale, wh);
        if (px == null) return null;
        boundTex = -1;
        flush();
        t = new Tex(); t.w = wh[0]; t.h = wh[1]; t.id = upload(px, wh[0], wh[1]);
        stringTextures.put(key, t);
        return t;
      }

    private void deleteAllImages() {
        for (Tex t : imageTextures.values()) Gl.deleteTexture(t.id);
        imageTextures.clear();
        imageBytes = 0;
      }

    /** Between frames only: no queued vertex can reference a texture deleted here. */
    private void trimCaches() {
        if (imageTextures.size() > MAX_IMAGE_TEXTURES || imageBytes > MAX_IMAGE_BYTES) {
            rally3d.core.RLog.i("gpu", "GPU UI: image texture cache reset (" + imageTextures.size() + " images, " + (imageBytes >> 20) + " MB)");
            deleteAllImages();
        }
        if (stringTextures.size() > MAX_STRING_TEXTURES) {
            Iterator<Map.Entry<StrKey, Tex>> it = stringTextures.entrySet().iterator();
            int drop = stringTextures.size() - MAX_STRING_TEXTURES * 3 / 4;
            while (drop-- > 0 && it.hasNext()) { Gl.deleteTexture(it.next().getValue().id); it.remove(); }
        }
      }
}
