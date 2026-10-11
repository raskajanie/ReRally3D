package rally3d.desktop;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.stream.IntStream;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.awt.AWTGLCanvas;
import org.lwjgl.opengl.awt.GLData;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

/** OpenGL accelerated presentation surface with a small PBO upload ring. */
final class GpuSurface extends AWTGLCanvas {
    private static final long serialVersionUID = 1L;
    private volatile int[] framePixels;
    private volatile int frameWidth, frameHeight;
    private int program, texture, vao, vbo;
    private final int[] pixelBuffers = new int[3];
    private int nextBuffer, textureWidth, textureHeight;
    private int samplerLocation, antiAliasingLocation;

    // ---- GPU layered pipeline: [3D world FBO from Graphics3D] + [CPU UI layer texture] -> composed texture -> FXAA / upscale -> window ----
    private static final int UI_BAND = 16;
    private int compProgram, compVao, compVbo, uiTexture, composedTexture, composedFbo;
    private int compUiLoc, compWorldLoc, compUseWorldLoc;
    private int layerWidth, layerHeight;
    private boolean[] uiDirtyNow = new boolean[0], uiDirtyPrev = new boolean[0];
    private boolean uiForceFull;
    private IntBuffer uiStaging;
    private final GpuTimer composeTimer = new GpuTimer(10);
    // ---- stage 3a GPU UI: lcdui draw calls replayed into this premultiplied RGBA framebuffer (same size / orientation as uiTexture) ----
    private int uiGpuTexture, uiGpuFbo, compPremultLoc;
    private GpuUi gpuUi;
    private final GpuTimer uiTimer = new GpuTimer(15);

    GpuSurface() {
        super(contextData());
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setIgnoreRepaint(false);
    }

    private static GLData contextData() {
        GLData data = new GLData();
        data.majorVersion = 3;
        data.minorVersion = 3;
        data.profile = GLData.Profile.CORE;
        data.doubleBuffer = true;
        data.depthSize = 0;
        data.swapInterval = Platform.vsync() ? 1 : 0;
        return data;
    }

    void setFrame(int[] pixels, int width, int height) {
        framePixels = pixels;
        frameWidth = width;
        frameHeight = height;
    }

    boolean programReady() { return program != 0; }
    /** GL capabilities are per thread AND per context: remember which context this thread last called GL.createCapabilities() for (GL.getCapabilities() throws when unset). */
    private final ThreadLocal<long[]> capsFor = new ThreadLocal<long[]>();
    /** GL.createCapabilities() reloads every GL function pointer (~0.25 ms): once per context/thread, not once per runGpu() call (twice per frame). */
    void refreshCapabilities() {
        long[] mark = capsFor.get();
        if (mark == null || mark[0] != context) {
            GL.createCapabilities();
            if (mark == null) { mark = new long[1]; capsFor.set(mark); }
            mark[0] = context;
        }
    }

    @Override public void initGL() {
        if (program != 0) { refreshCapabilities(); return; }
        GL.createCapabilities();
        long[] mark = capsFor.get();
        if (mark == null) { mark = new long[1]; capsFor.set(mark); }
        mark[0] = context;
        program = createProgram();
        LogConsole.info("gpu", "OpenGL renderer: " + glGetString(GL_RENDERER) + "; " + glGetString(GL_VERSION));
        samplerLocation = glGetUniformLocation(program, "frameTexture");
        antiAliasingLocation = glGetUniformLocation(program, "antiAliasingMode");

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer quad = memAllocFloat(16);
        try {
            // Flip V: Java frame row zero is the top row; OpenGL texture row zero is bottom.
            quad.put(new float[] {
                -1f, -1f, 0f, 1f,  1f, -1f, 1f, 1f,
                -1f,  1f, 0f, 0f,  1f,  1f, 1f, 0f
            }).flip();
            glBufferData(GL_ARRAY_BUFFER, quad, GL_STATIC_DRAW);
        } finally {
            memFree(quad);
        }
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0L);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);
        glBindVertexArray(0);

        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenBuffers(pixelBuffers);
        createLayerResources();
    }

    private void createLayerResources() {
        compProgram = createCompositeProgram();
        glUseProgram(compProgram);
        compUiLoc = glGetUniformLocation(compProgram, "uiTexture");
        compWorldLoc = glGetUniformLocation(compProgram, "worldTexture");
        compUseWorldLoc = glGetUniformLocation(compProgram, "useWorld");
        compPremultLoc = glGetUniformLocation(compProgram, "uiPremult");
        glUseProgram(0);
        compVao = glGenVertexArrays();
        compVbo = glGenBuffers();
        glBindVertexArray(compVao);
        glBindBuffer(GL_ARRAY_BUFFER, compVbo);
        FloatBuffer quad = memAllocFloat(24);
        try {
            // pos.xy, uiUv.xy, worldUv.xy. The composed texture is stored in "Java orientation" (row 0 = top of the picture):
            // the UI texture is uploaded unflipped (v 0 = top), the world FBO is in GL orientation (v 1 = top of the picture).
            quad.put(new float[] {
                -1f, -1f, 0f, 0f, 0f, 1f,   1f, -1f, 1f, 0f, 1f, 1f,
                -1f,  1f, 0f, 1f, 0f, 0f,   1f,  1f, 1f, 1f, 1f, 0f
            }).flip();
            glBufferData(GL_ARRAY_BUFFER, quad, GL_STATIC_DRAW);
        } finally { memFree(quad); }
        int stride = 6 * Float.BYTES;
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 4L * Float.BYTES);
        glBindVertexArray(0);
        uiTexture = glGenTextures();
        composedTexture = glGenTextures();
        composedFbo = glGenFramebuffers();
        uiGpuTexture = glGenTextures();
        uiGpuFbo = glGenFramebuffers();
        layerWidth = layerHeight = 0;
    }

    private static void nearest(int tex) {
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private void ensureLayerSize(int w, int h) {
        if (layerWidth == w && layerHeight == h) return;
        layerWidth = w; layerHeight = h;
        nearest(uiTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, composedTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindFramebuffer(GL_FRAMEBUFFER, composedFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, composedTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("OpenGL compose framebuffer is incomplete");
        // GPU UI framebuffer (stage 3a). Undefined until the first recorded CLEAR op (Graphics records one on every resize / mode switch).
        nearest(uiGpuTexture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindFramebuffer(GL_FRAMEBUFFER, uiGpuFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, uiGpuTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("OpenGL UI framebuffer is incomplete");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        int bands = (h + UI_BAND - 1) / UI_BAND;
        uiDirtyNow = new boolean[bands];
        uiDirtyPrev = new boolean[bands];
        uiForceFull = true;   // glTexImage2D(null) leaves the texture undefined: upload every row once (transparent where nothing is drawn)
        if (uiStaging != null) memFree(uiStaging);
        uiStaging = memAllocInt(w * h);
    }

    /**
     * One GPU frame with no CPU round trip. Game thread, GL context current (called inside GameWindow.runGpu).
     * ui = CPU layer (ARGB, transparent where the world shows through), worldTex = 3D world FBO texture of this frame or 0 (menus).
     */
    void presentLayers(int[] ui, int w, int h, int worldTex, javax.microedition.lcdui.UiRecorder rec, double uiScale) {
        long started = System.nanoTime();
        int windowWidth = getFramebufferWidth(), windowHeight = getFramebufferHeight();
        if (ui == null || w <= 0 || h <= 0 || windowWidth <= 0 || windowHeight <= 0 || ui.length < w * h) {
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            swapBuffers();
            return;
        }
        ensureLayerSize(w, h);
        boolean gpuUiFrame = rec != null;
        if (gpuUiFrame) replayGpuUi(rec, w, h, uiScale); else uploadUiDirtyBands(ui, w, h);
        composeTimer.poll();
        composeTimer.begin();

        // pass 1: world + UI -> composed texture (internal resolution)
        glBindFramebuffer(GL_FRAMEBUFFER, composedFbo);
        glViewport(0, 0, w, h);
        glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND); glDisable(GL_SCISSOR_TEST); glDisable(GL_CULL_FACE);
        glColorMask(true, true, true, true);
        glUseProgram(compProgram);
        glUniform1i(compUiLoc, 0);
        glUniform1i(compWorldLoc, 1);
        glUniform1i(compUseWorldLoc, worldTex != 0 ? 1 : 0);
        glUniform1i(compPremultLoc, gpuUiFrame ? 1 : 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, worldTex);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, gpuUiFrame ? uiGpuTexture : uiTexture);
        glBindVertexArray(compVao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glBindVertexArray(0);

        // pass 2: FXAA / upscale (letterboxed) -> window
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        double scale = Math.min(windowWidth / (double) w, windowHeight / (double) h);
        int drawWidth = (int) Math.round(w * scale), drawHeight = (int) Math.round(h * scale);
        glViewport(0, 0, windowWidth, windowHeight);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glViewport((windowWidth - drawWidth) / 2, (windowHeight - drawHeight) / 2, drawWidth, drawHeight);
        glUseProgram(program);
        glUniform1i(samplerLocation, 0);
        glUniform1i(antiAliasingLocation, Platform.antiAliasing());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, composedTexture);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glBindVertexArray(0);
        glUseProgram(0);
        composeTimer.end();
        enforceSwapInterval();
        swapBuffers();
        Platform.recordPerformance(5, System.nanoTime() - started);
    }

    /** Stage 3a: replay the recorded lcdui draw calls into the UI framebuffer. A failure only disables the GPU UI (CPU UI layer from the next frame), never the whole GPU renderer. */
    private void replayGpuUi(javax.microedition.lcdui.UiRecorder rec, int w, int h, double scale) {
        long started = System.nanoTime();
        uiTimer.poll();
        uiTimer.begin();
        glBindFramebuffer(GL_FRAMEBUFFER, uiGpuFbo);
        glViewport(0, 0, w, h);
        glColorMask(true, true, true, true);
        try {
            if (gpuUi == null) gpuUi = new GpuUi();
            gpuUi.execute(rec, w, h, scale);
            rec.reset();
        } catch (RuntimeException | LinkageError error) {
            rec.reset();
            glDisable(GL_BLEND); glDisable(GL_SCISSOR_TEST);
            Platform.failGpuUi(error);
        } finally {
            uiTimer.end();
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
        }
        Platform.recordPerformance(14, System.nanoTime() - started);
    }

    private boolean swapControlOk = true;
    private int loggedSwapInterval = -99;
    /** Drivers / DWM can ignore or override the interval chosen at context creation: query it every frame, log changes, force the wanted value (V-sync setting). */
    private void enforceSwapInterval() {
        if (!swapControlOk) return;
        final int want = Platform.vsync() ? 1 : 0;
        try {
            int cur = org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT();
            if (cur != loggedSwapInterval) {
                loggedSwapInterval = cur;
                rally3d.core.RLog.i("gpu", "WGL swap interval is " + cur + (cur != want ? " (wanted " + want + ") -> forcing" : " (V-sync " + (want == 1 ? "on" : "off") + ")"));
            }
            if (cur != want) org.lwjgl.opengl.WGLEXTSwapControl.wglSwapIntervalEXT(want);
        } catch (Throwable t) {
            swapControlOk = false;
            rally3d.core.RLog.w("gpu", "WGL swap control unavailable: " + t);
        }
    }

    /** Uploads only the 16-row bands that contain UI now or contained UI last frame (so cleared areas are erased). */
    private void uploadUiDirtyBands(final int[] ui, final int w, final int h) {
        final int bands = uiDirtyNow.length;
        final boolean[] now = uiDirtyNow;
        Platform.parallelFor(bands, b -> {
            int from = b * UI_BAND * w, to = Math.min(h, (b + 1) * UI_BAND) * w;
            boolean any = false;
            for (int i = from; i < to; i++) if (ui[i] != 0) { any = true; break; }
            now[b] = any;
        });
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, uiTexture);
        int b = 0;
        while (b < bands) {
            if (!(uiForceFull || uiDirtyNow[b] || uiDirtyPrev[b])) { b++; continue; }
            int e = b;
            while (e < bands && (uiForceFull || uiDirtyNow[e] || uiDirtyPrev[e])) e++;
            final int r0 = b * UI_BAND, r1 = Math.min(h, e * UI_BAND), rows = r1 - r0;
            final IntBuffer staging = uiStaging;
            int chunks = rows >= 64 ? Math.min(16, rows / 16) : 1;
            final int per = (rows + chunks - 1) / chunks;
            Platform.parallelFor(chunks, c -> {
                int a = r0 + c * per, z = Math.min(r1, a + per);
                if (a >= z) return;
                IntBuffer dst = staging.duplicate();
                dst.position((a - r0) * w);
                dst.put(ui, a * w, (z - a) * w);
            });
            staging.limit(rows * w).position(0);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, r0, w, rows, GL_BGRA, GL_UNSIGNED_BYTE, staging);
            staging.clear();
            b = e;
        }
        uiForceFull = false;
        boolean[] t = uiDirtyPrev; uiDirtyPrev = uiDirtyNow; uiDirtyNow = t;
    }

    @Override public void paintGL() {
        long uploadStarted = System.nanoTime();
        int[] pixels = framePixels;
        int width = frameWidth, height = frameHeight;
        int windowWidth = getFramebufferWidth(), windowHeight = getFramebufferHeight();
        if (pixels == null || width <= 0 || height <= 0 || windowWidth <= 0 || windowHeight <= 0) {
            glClearColor(0f, 0f, 0f, 1f);
            glClear(GL_COLOR_BUFFER_BIT);
            swapBuffers();
            return;
        }

        glBindTexture(GL_TEXTURE_2D, texture);
        if (textureWidth != width || textureHeight != height) {
            textureWidth = width;
            textureHeight = height;
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_BGRA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            long bytes = (long) width * height * Integer.BYTES;
            for (int pbo : pixelBuffers) {
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
                glBufferData(GL_PIXEL_UNPACK_BUFFER, bytes, GL_STREAM_DRAW);
            }
        }

        int pbo = pixelBuffers[nextBuffer++ % pixelBuffers.length];
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
        glBufferData(GL_PIXEL_UNPACK_BUFFER, (long) width * height * Integer.BYTES, GL_STREAM_DRAW);
        ByteBuffer mapped = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY);
        if (mapped != null) {
            mapped.order(ByteOrder.nativeOrder()).asIntBuffer().put(pixels, 0, Math.min(pixels.length, width * height));
            glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_BYTE, 0L);
        } else {
            ByteBuffer fallback = memAlloc(width * height * Integer.BYTES);
            try {
                fallback.order(ByteOrder.nativeOrder()).asIntBuffer().put(pixels, 0, Math.min(pixels.length, width * height));
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_BYTE, fallback);
            } finally { memFree(fallback); }
        }
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

        double scale = Math.min(windowWidth / (double) width, windowHeight / (double) height);
        int drawWidth = (int) Math.round(width * scale), drawHeight = (int) Math.round(height * scale);
        glViewport((windowWidth - drawWidth) / 2, (windowHeight - drawHeight) / 2, drawWidth, drawHeight);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        glUseProgram(program);
        glUniform1i(samplerLocation, 0);
        glUniform1i(antiAliasingLocation, Platform.antiAliasing());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glBindVertexArray(0);
        glUseProgram(0);
        enforceSwapInterval();
        swapBuffers();
        Platform.recordPerformance(5, System.nanoTime() - uploadStarted);
    }

    @Override protected void disposeGL() {
        if (program != 0) glDeleteProgram(program);
        if (texture != 0) glDeleteTextures(texture);
        if (vbo != 0) glDeleteBuffers(vbo);
        if (pixelBuffers[0] != 0 || pixelBuffers[1] != 0 || pixelBuffers[2] != 0) glDeleteBuffers(pixelBuffers);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (compProgram != 0) glDeleteProgram(compProgram);
        if (uiTexture != 0) glDeleteTextures(uiTexture);
        if (composedTexture != 0) glDeleteTextures(composedTexture);
        if (composedFbo != 0) glDeleteFramebuffers(composedFbo);
        if (compVbo != 0) glDeleteBuffers(compVbo);
        if (compVao != 0) glDeleteVertexArrays(compVao);
        if (uiGpuTexture != 0) glDeleteTextures(uiGpuTexture);
        if (uiGpuFbo != 0) glDeleteFramebuffers(uiGpuFbo);
        if (gpuUi != null) { gpuUi.dispose(); gpuUi = null; }
        if (uiStaging != null) { memFree(uiStaging); uiStaging = null; }
        compProgram = uiTexture = composedTexture = composedFbo = compVbo = compVao = layerWidth = layerHeight = uiGpuTexture = uiGpuFbo = 0;
        program = texture = vbo = vao = samplerLocation = antiAliasingLocation = 0;
        java.util.Arrays.fill(pixelBuffers, 0);
        textureWidth = textureHeight = nextBuffer = 0;
    }

    private static int createProgram() {
        int vertex = compileShader(GL_VERTEX_SHADER,
                "#version 330 core\n" +
                "layout(location=0) in vec2 position;\n" +
                "layout(location=1) in vec2 texCoord;\n" +
                "out vec2 uv;\n" +
                "void main(){ uv=texCoord; gl_Position=vec4(position,0.0,1.0); }\n");
        int fragment = compileShader(GL_FRAGMENT_SHADER,
                "#version 330 core\n" +
                "in vec2 uv; out vec4 color; uniform sampler2D frameTexture; uniform int antiAliasingMode;\n" +
                "float luma(vec3 c){ return dot(c,vec3(0.299,0.587,0.114)); }\n" +
                "vec3 fetch(vec2 p){ return textureLod(frameTexture,p,0.0).rgb; }\n" +
                "float lumaAt(vec2 p){ return luma(fetch(p)); }\n" +
                "// FXAA 3.11 (quality): edge detect -> edge orientation -> walk to both edge ends -> sub-pixel shift. 1 = FXAA, 2 = FXAA Quality.\n" +
                "void main(){\n" +
                " vec3 rgbM=fetch(uv);\n" +
                " if(antiAliasingMode==0){ color=vec4(rgbM,1.0); return; }\n" +
                " vec2 px=1.0/vec2(textureSize(frameTexture,0));\n" +
                " float edgeThreshold=antiAliasingMode==2?0.125:0.166;\n" +
                " float edgeMin=antiAliasingMode==2?0.0625:0.0833;\n" +
                " float subpixQuality=antiAliasingMode==2?1.0:0.75;\n" +
                " int maxSteps=antiAliasingMode==2?20:12;\n" +
                " float lM=luma(rgbM);\n" +
                " float lN=lumaAt(uv+vec2(0.0,-px.y)), lS=lumaAt(uv+vec2(0.0,px.y)), lW=lumaAt(uv+vec2(-px.x,0.0)), lE=lumaAt(uv+vec2(px.x,0.0));\n" +
                " float rangeMax=max(lM,max(max(lN,lS),max(lW,lE)));\n" +
                " float rangeMin=min(lM,min(min(lN,lS),min(lW,lE)));\n" +
                " float range=rangeMax-rangeMin;\n" +
                " if(range<max(edgeMin,rangeMax*edgeThreshold)){ color=vec4(rgbM,1.0); return; }\n" +
                " float lNW=lumaAt(uv+vec2(-px.x,-px.y)), lNE=lumaAt(uv+vec2(px.x,-px.y)), lSW=lumaAt(uv+vec2(-px.x,px.y)), lSE=lumaAt(uv+vec2(px.x,px.y));\n" +
                " float lNS=lN+lS, lWE=lW+lE;\n" +
                " float edgeH=abs(-2.0*lW+(lNW+lSW))+abs(-2.0*lM+lNS)*2.0+abs(-2.0*lE+(lNE+lSE));\n" +
                " float edgeV=abs(-2.0*lN+(lNW+lNE))+abs(-2.0*lM+lWE)*2.0+abs(-2.0*lS+(lSW+lSE));\n" +
                " bool horz=edgeH>=edgeV;\n" +
                " float l1=horz?lN:lW, l2=horz?lS:lE;\n" +
                " float lengthSign=horz?px.y:px.x;\n" +
                " float g1=l1-lM, g2=l2-lM;\n" +
                " bool pairN=abs(g1)>=abs(g2);\n" +
                " float gradientScaled=max(abs(g1),abs(g2))*0.25;\n" +
                " if(pairN) lengthSign=-lengthSign;\n" +
                " float lAvg=0.5*((pairN?l1:l2)+lM);\n" +
                " vec2 posB=uv;\n" +
                " vec2 offNP=horz?vec2(px.x,0.0):vec2(0.0,px.y);\n" +
                " if(horz) posB.y+=lengthSign*0.5; else posB.x+=lengthSign*0.5;\n" +
                " vec2 posN=posB-offNP, posP=posB+offNP;\n" +
                " float endN=lumaAt(posN)-lAvg, endP=lumaAt(posP)-lAvg;\n" +
                " bool doneN=abs(endN)>=gradientScaled, doneP=abs(endP)>=gradientScaled;\n" +
                " for(int i=1;i<maxSteps;i++){\n" +
                "  if(doneN&&doneP) break;\n" +
                "  float stepK=i<5?1.0:(i<9?1.5:2.0);\n" +
                "  if(!doneN){ posN-=offNP*stepK; endN=lumaAt(posN)-lAvg; doneN=abs(endN)>=gradientScaled; }\n" +
                "  if(!doneP){ posP+=offNP*stepK; endP=lumaAt(posP)-lAvg; doneP=abs(endP)>=gradientScaled; }\n" +
                " }\n" +
                " float dstN=horz?uv.x-posN.x:uv.y-posN.y;\n" +
                " float dstP=horz?posP.x-uv.x:posP.y-uv.y;\n" +
                " bool centerBelowAvg=(lM-lAvg)<0.0;\n" +
                " bool goodN=((endN<0.0)!=centerBelowAvg), goodP=((endP<0.0)!=centerBelowAvg);\n" +
                " bool towardN=dstN<dstP;\n" +
                " float dst=min(dstN,dstP);\n" +
                " bool good=towardN?goodN:goodP;\n" +
                " float spanLength=max(dstP+dstN,1e-6);\n" +
                " float pixelOffset=good?(0.5-dst/spanLength):0.0;\n" +
                " float subA=2.0*(lNS+lWE)+(lNW+lNE+lSW+lSE);\n" +
                " float subC=clamp(abs(subA/12.0-lM)/range,0.0,1.0);\n" +
                " float subF=(-2.0*subC+3.0)*subC*subC;\n" +
                " float subH=subF*subF*subpixQuality;\n" +
                " float po=max(pixelOffset,subH);\n" +
                " vec2 fp=uv;\n" +
                " if(horz) fp.y+=po*lengthSign; else fp.x+=po*lengthSign;\n" +
                " color=vec4(fetch(fp),1.0);\n" +
                "}\n");
        int result = glCreateProgram();
        glAttachShader(result, vertex);
        glAttachShader(result, fragment);
        glLinkProgram(result);
        if (glGetProgrami(result, GL_LINK_STATUS) == GL_FALSE) {
            String error = glGetProgramInfoLog(result);
            glDeleteProgram(result);
            throw new IllegalStateException("OpenGL presentation shader link failed: " + error);
        }
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return result;
    }

    private static int createCompositeProgram() {
        int vertex = compileShader(GL_VERTEX_SHADER,
                "#version 330 core\n" +
                "layout(location=0) in vec2 position;\n" +
                "layout(location=1) in vec2 uiUv;\n" +
                "layout(location=2) in vec2 worldUv;\n" +
                "out vec2 vUi; out vec2 vWorld;\n" +
                "void main(){ vUi=uiUv; vWorld=worldUv; gl_Position=vec4(position,0.0,1.0); }\n");
        int fragment = compileShader(GL_FRAGMENT_SHADER,
                "#version 330 core\n" +
                "in vec2 vUi; in vec2 vWorld; out vec4 color;\n" +
                "uniform sampler2D uiTexture; uniform sampler2D worldTexture; uniform int useWorld; uniform int uiPremult;\n" +
                "void main(){\n" +
                " vec4 u=texture(uiTexture,vUi);\n" +
                " vec3 base=vec3(0.0);\n" +
                " if(useWorld!=0){ vec4 w=texture(worldTexture,vWorld); base=w.rgb*w.a; }\n" +
                " vec3 over=(uiPremult!=0)?u.rgb:u.rgb*u.a;\n" +
                " color=vec4(over+base*(1.0-u.a),1.0);\n" +
                "}\n");
        int result = glCreateProgram();
        glAttachShader(result, vertex);
        glAttachShader(result, fragment);
        glLinkProgram(result);
        if (glGetProgrami(result, GL_LINK_STATUS) == GL_FALSE) {
            String error = glGetProgramInfoLog(result);
            glDeleteProgram(result);
            throw new IllegalStateException("OpenGL composite shader link failed: " + error);
        }
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        return result;
    }

    private static int compileShader(int kind, String source) {
        int shader = glCreateShader(kind);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String error = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("OpenGL presentation shader compile failed: " + error);
        }
        return shader;
    }
}
