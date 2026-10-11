package rally3d.android;

import android.opengl.GLES30;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Thin GLES 3.0 helpers (LWJGL-style gen/delete/upload shortcuts) shared by GpuRenderer, GpuUi and Graphics3D.
 * Everything here must be called on the game thread with the EGL context current.
 * Pixel data is Java ARGB (int, straight alpha); GLES has no BGRA upload, so uploads repack it to RGBA bytes.
 */
public final class Gl {
    private Gl() {}

    private static final int[] one = new int[1];

    public static int genTexture() { GLES30.glGenTextures(1, one, 0); return one[0]; }
    public static int genBuffer() { GLES30.glGenBuffers(1, one, 0); return one[0]; }
    public static int genFramebuffer() { GLES30.glGenFramebuffers(1, one, 0); return one[0]; }
    public static int genRenderbuffer() { GLES30.glGenRenderbuffers(1, one, 0); return one[0]; }
    public static int genVertexArray() { GLES30.glGenVertexArrays(1, one, 0); return one[0]; }
    public static void deleteTexture(int id) { one[0] = id; GLES30.glDeleteTextures(1, one, 0); }
    public static void deleteBuffer(int id) { one[0] = id; GLES30.glDeleteBuffers(1, one, 0); }
    public static void deleteFramebuffer(int id) { one[0] = id; GLES30.glDeleteFramebuffers(1, one, 0); }
    public static void deleteRenderbuffer(int id) { one[0] = id; GLES30.glDeleteRenderbuffers(1, one, 0); }
    public static void deleteVertexArray(int id) { one[0] = id; GLES30.glDeleteVertexArrays(1, one, 0); }

    // ---- buffers ----
    public static FloatBuffer allocFloats(int n) { return ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); }

    /** Same content in a buffer with at least minCapacity floats; position is kept, limit = capacity. */
    public static FloatBuffer growFloats(FloatBuffer old, int minCapacity) {
        if (old.capacity() >= minCapacity) return old;
        FloatBuffer n = allocFloats(Math.max(minCapacity, old.capacity() * 2));
        int pos = old.position();
        old.flip();
        n.put(old);
        n.position(pos);
        return n;
      }

    /** Uploads the buffer's remaining floats (position..limit) into the bound GL_ARRAY_BUFFER, orphaning the old store. */
    public static void bufferData(int target, FloatBuffer data, int usage) { GLES30.glBufferData(target, data.remaining() * 4, data, usage); }
    public static void bufferSize(int target, int bytes, int usage) { GLES30.glBufferData(target, bytes, null, usage); }
    public static void bufferSub(int target, int offset, FloatBuffer data) { GLES30.glBufferSubData(target, offset, data.remaining() * 4, data); }

    // ---- textures ----
    private static ByteBuffer scratch = ByteBuffer.allocateDirect(1 << 16).order(ByteOrder.nativeOrder());
    private static int[] swizzle = new int[1 << 14];

    /** glTexImage2D(RGBA8) of w*h Java ARGB ints into the bound texture (straight alpha kept). */
    public static void texImageArgb(int w, int h, int[] argb) {
        int n = w * h;
        if (scratch.capacity() < n * 4) scratch = ByteBuffer.allocateDirect(Math.max(n * 4, scratch.capacity() * 2)).order(ByteOrder.nativeOrder());
        IntBuffer ib = scratch.asIntBuffer();
        ib.clear();
        if (swizzle.length < 1 << 14) swizzle = new int[1 << 14];
        for (int base = 0; base < n; base += swizzle.length) {
            int cnt = Math.min(swizzle.length, n - base);
            for (int i = 0; i < cnt; i++) {
                int c = argb[base + i];
                swizzle[i] = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);   // 0xAARRGGBB -> bytes R,G,B,A (little endian)
            }
            ib.put(swizzle, 0, cnt);
        }
        scratch.position(0).limit(n * 4);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, scratch);
        scratch.clear();
      }

    /** Allocates an uninitialised RGBA8 level 0 for the bound texture. */
    public static void texImageEmpty(int w, int h) {
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
      }

    public static void texParams(int minMag, int wrapS, int wrapT) {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, minMag);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, minMag);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrapS);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrapT);
      }

    // ---- shaders ----
    public static int program(String what, String vertexSrc, String fragmentSrc) {
        int vs = compile(what, GLES30.GL_VERTEX_SHADER, vertexSrc);
        int fs;
        try { fs = compile(what, GLES30.GL_FRAGMENT_SHADER, fragmentSrc); }
        catch (RuntimeException e) { rally3d.core.RLog.caught("Gl.program", e); GLES30.glDeleteShader(vs); throw e; }
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, vs);
        GLES30.glAttachShader(p, fs);
        GLES30.glLinkProgram(p);
        int[] ok = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0);
        String log = ok[0] == 0 ? GLES30.glGetProgramInfoLog(p) : null;
        GLES30.glDeleteShader(vs);
        GLES30.glDeleteShader(fs);
        if (ok[0] == 0) { GLES30.glDeleteProgram(p); throw new IllegalStateException(what + " shader link failed: " + log); }
        return p;
      }

    private static int compile(String what, int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] ok = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            GLES30.glDeleteShader(s);
            throw new IllegalStateException(what + " shader compile failed: " + log);
        }
        return s;
      }

    private static int errorsLogged;
    /** Logs (rate limited) a pending GL error; returns it. */
    public static int checkError(String where) {
        int e = GLES30.glGetError();
        if (e != 0 && errorsLogged++ < 20) rally3d.core.RLog.w("gpu", "GL error 0x" + Integer.toHexString(e) + " after " + where + (errorsLogged == 20 ? " (further GL errors are not logged)" : ""));
        return e;
      }
}
