package rally3d.android;

import static android.opengl.GLES30.*;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.view.Surface;
import java.nio.FloatBuffer;
import javax.microedition.lcdui.UiRecorder;
import rally3d.core.RLog;
import rally3d.desktop.Platform;

/**
 * GLES 3.0 presentation for the GPU renderer: owns the EGL context + window surface of the GameView surface and composes one frame
 * (3D world texture from Graphics3D + GPU-replayed UI layer) -> FXAA / upscale -> screen. Every method runs on the game thread.
 * The surface can come and go (app backgrounded): detach() releases only the window surface, the context and all GL objects survive.
 */
public final class GpuRenderer {
    private EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
    private EGLContext ctx = EGL14.EGL_NO_CONTEXT;
    private EGLConfig cfg;
    private volatile EGLSurface surf = EGL14.EGL_NO_SURFACE;
    private Thread owner;
    private int swapInterval = -1;
    private boolean glReady;
    private final int[] tmp = new int[1];

    // presentation
    private int program, vao, vbo, samplerLoc, aaLoc;
    // layers
    private int compProgram, compVao, compVbo, compUiLoc, compWorldLoc, compUseWorldLoc;
    private int composedTexture, composedFbo, uiTexture, uiFbo, layerW, layerH;
    private GpuUi gpuUi;
    private volatile int surfaceW, surfaceH;
    private long frames;

    public boolean attached() { return surf != EGL14.EGL_NO_SURFACE; }
    public int surfaceWidth() { return surfaceW; }
    public int surfaceHeight() { return surfaceH; }
    public long frames() { return frames; }

    // ---------------- EGL ----------------
    private void createContext() {
        dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (dpy == EGL14.EGL_NO_DISPLAY) throw new IllegalStateException("eglGetDisplay failed");
        int[] ver = new int[2];
        if (!EGL14.eglInitialize(dpy, ver, 0, ver, 1)) throw new IllegalStateException("eglInitialize failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
        int[] attribs = {
                EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT */,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE };
        EGLConfig[] cfgs = new EGLConfig[1];
        int[] n = new int[1];
        if (!EGL14.eglChooseConfig(dpy, attribs, 0, cfgs, 0, 1, n, 0) || n[0] < 1) throw new IllegalStateException("no EGL config with OpenGL ES 3 + RGBA8888 (0x" + Integer.toHexString(EGL14.eglGetError()) + ")");
        cfg = cfgs[0];
        ctx = EGL14.eglCreateContext(dpy, cfg, EGL14.EGL_NO_CONTEXT, new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE }, 0);
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) throw new IllegalStateException("eglCreateContext(ES 3.0) failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
        RLog.i("gpu", "EGL " + ver[0] + "." + ver[1] + " context created (OpenGL ES 3)");
      }

    /** Creates the window surface for the (valid) Surface and makes the context current on this thread. */
    public void attach(Surface s) {
        if (attached()) return;
        if (ctx == null || ctx == EGL14.EGL_NO_CONTEXT) createContext();
        surf = EGL14.eglCreateWindowSurface(dpy, cfg, s, new int[] { EGL14.EGL_NONE }, 0);
        if (surf == null || surf == EGL14.EGL_NO_SURFACE) { surf = EGL14.EGL_NO_SURFACE; throw new IllegalStateException("eglCreateWindowSurface failed: 0x" + Integer.toHexString(EGL14.eglGetError())); }
        if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx)) {
            int err = EGL14.eglGetError();
            EGL14.eglDestroySurface(dpy, surf); surf = EGL14.EGL_NO_SURFACE;
            throw new IllegalStateException("eglMakeCurrent failed: 0x" + Integer.toHexString(err));
        }
        owner = Thread.currentThread();
        swapInterval = -1;
        applySwapInterval();
        if (!glReady) initGl();
        EGL14.eglQuerySurface(dpy, surf, EGL14.EGL_WIDTH, tmp, 0); surfaceW = tmp[0];
        EGL14.eglQuerySurface(dpy, surf, EGL14.EGL_HEIGHT, tmp, 0); surfaceH = tmp[0];
        RLog.i("gpu", "EGL window surface " + surfaceW + "x" + surfaceH + ", renderer: " + glGetString(GL_RENDERER) + "; " + glGetString(GL_VERSION));
      }

    /** Releases the window surface (surface lost, switch to the CPU canvas, app paused). The context stays. */
    public void detach() {
        if (!attached()) return;
        try {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(dpy, surf);
        } catch (Throwable t) { RLog.caught("GpuRenderer.detach", t); }
        surf = EGL14.EGL_NO_SURFACE;
      }

    /** Frees everything (GPU renderer given up). */
    public void destroy() {
        try {
            if (attached() && Thread.currentThread() == owner && glReady) {
                if (gpuUi != null) gpuUi.dispose();
            }
        } catch (Throwable t) { RLog.caught("GpuRenderer.destroy", t); }
        gpuUi = null;
        detach();
        try {
            if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx);
            if (dpy != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(dpy);
        } catch (Throwable t) { RLog.caught("GpuRenderer.destroy", t); }
        ctx = EGL14.EGL_NO_CONTEXT; dpy = EGL14.EGL_NO_DISPLAY; glReady = false;
      }

    /** Runs a GL task with the context current. False = no surface / wrong thread (caller draws this frame in software). Exceptions propagate. */
    public boolean run(Runnable task) {
        if (!attached()) return false;
        if (Thread.currentThread() != owner) return false;
        task.run();
        return true;
      }

    private void applySwapInterval() {
        int want = Platform.vsync() ? 1 : 0;
        if (want == swapInterval) return;
        swapInterval = want;
        if (!EGL14.eglSwapInterval(dpy, want)) RLog.w("gpu", "eglSwapInterval(" + want + ") failed: 0x" + Integer.toHexString(EGL14.eglGetError()));
        else RLog.i("gpu", "swap interval " + want + " (V-sync " + (want == 1 ? "on" : "off") + ")");
      }

    // ---------------- GL objects ----------------
    private void initGl() {
        program = Gl.program("present",
                "#version 300 es\n" +
                "layout(location=0) in vec2 position;\n" +
                "layout(location=1) in vec2 texCoord;\n" +
                "out vec2 uv;\n" +
                "void main(){ uv=texCoord; gl_Position=vec4(position,0.0,1.0); }\n",
                FXAA_FRAGMENT);
        samplerLoc = glGetUniformLocation(program, "frameTexture");
        aaLoc = glGetUniformLocation(program, "antiAliasingMode");
        vao = Gl.genVertexArray();
        vbo = Gl.genBuffer();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        // Composed texture is stored in "Java orientation" (row 0 = top of the picture): flip V when drawing it to the screen.
        FloatBuffer quad = Gl.allocFloats(16);
        quad.put(new float[] { -1f, -1f, 0f, 1f,  1f, -1f, 1f, 1f,  -1f, 1f, 0f, 0f,  1f, 1f, 1f, 0f }).flip();
        Gl.bufferData(GL_ARRAY_BUFFER, quad, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8);
        glBindVertexArray(0);

        compProgram = Gl.program("composite",
                "#version 300 es\n" +
                "layout(location=0) in vec2 position;\n" +
                "layout(location=1) in vec2 uiUv;\n" +
                "layout(location=2) in vec2 worldUv;\n" +
                "out vec2 vUi; out vec2 vWorld;\n" +
                "void main(){ vUi=uiUv; vWorld=worldUv; gl_Position=vec4(position,0.0,1.0); }\n",
                "#version 300 es\n" +
                "precision highp float; precision highp sampler2D;\n" +
                "in vec2 vUi; in vec2 vWorld; out vec4 color;\n" +
                "uniform sampler2D uiTexture; uniform sampler2D worldTexture; uniform int useWorld;\n" +
                "void main(){\n" +
                " vec4 u=texture(uiTexture,vUi);\n" +
                " vec3 base=vec3(0.0);\n" +
                " if(useWorld!=0){ vec4 w=texture(worldTexture,vWorld); base=w.rgb*w.a; }\n" +
                " color=vec4(u.rgb+base*(1.0-u.a),1.0);\n" +   // the UI layer is premultiplied
                "}\n");
        compUiLoc = glGetUniformLocation(compProgram, "uiTexture");
        compWorldLoc = glGetUniformLocation(compProgram, "worldTexture");
        compUseWorldLoc = glGetUniformLocation(compProgram, "useWorld");
        compVao = Gl.genVertexArray();
        compVbo = Gl.genBuffer();
        glBindVertexArray(compVao);
        glBindBuffer(GL_ARRAY_BUFFER, compVbo);
        // pos.xy, uiUv.xy, worldUv.xy: UI layer row 0 = top (v 0 = top), the world FBO is in GL orientation (v 1 = top of the picture).
        FloatBuffer cq = Gl.allocFloats(24);
        cq.put(new float[] {
                -1f, -1f, 0f, 0f, 0f, 1f,   1f, -1f, 1f, 0f, 1f, 1f,
                -1f,  1f, 0f, 1f, 0f, 0f,   1f,  1f, 1f, 1f, 1f, 0f }).flip();
        Gl.bufferData(GL_ARRAY_BUFFER, cq, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 2, GL_FLOAT, false, 24, 0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 2, GL_FLOAT, false, 24, 8);
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 2, GL_FLOAT, false, 24, 16);
        glBindVertexArray(0);
        composedTexture = Gl.genTexture(); composedFbo = Gl.genFramebuffer();
        uiTexture = Gl.genTexture(); uiFbo = Gl.genFramebuffer();
        glReady = true;
      }

    private void ensureLayerSize(int w, int h) {
        if (layerW == w && layerH == h) return;
        layerW = w; layerH = h;
        glBindTexture(GL_TEXTURE_2D, composedTexture);
        Gl.texParams(GL_LINEAR, GL_CLAMP_TO_EDGE, GL_CLAMP_TO_EDGE);
        Gl.texImageEmpty(w, h);
        glBindFramebuffer(GL_FRAMEBUFFER, composedFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, composedTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("OpenGL compose framebuffer is incomplete");
        glBindTexture(GL_TEXTURE_2D, uiTexture);
        Gl.texParams(GL_NEAREST, GL_CLAMP_TO_EDGE, GL_CLAMP_TO_EDGE);
        Gl.texImageEmpty(w, h);
        glBindFramebuffer(GL_FRAMEBUFFER, uiFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, uiTexture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("OpenGL UI framebuffer is incomplete");
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0f, 0f, 0f, 0f);
        glClear(GL_COLOR_BUFFER_BIT);   // GPU UI starts transparent (Graphics also records a CLEAR on every resize)
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        RLog.i("gpu", "GPU layers " + w + "x" + h);
      }

    /**
     * One GPU frame, no CPU round trip: replay the UI ops, compose world + UI, FXAA / upscale to the whole surface, swap.
     * worldTex = 3D world FBO texture of this frame or 0 (menus). Call inside run().
     */
    public void presentLayers(UiRecorder rec, int w, int h, int worldTex, double uiScale) {
        if (rec == null || w <= 0 || h <= 0) { glBindFramebuffer(GL_FRAMEBUFFER, 0); glClearColor(0f, 0f, 0f, 1f); glClear(GL_COLOR_BUFFER_BIT); swap(); return; }
        applySwapInterval();
        ensureLayerSize(w, h);

        // GPU UI: replay the recorded lcdui calls into the UI layer. The list is consumed even when the replay throws.
        glBindFramebuffer(GL_FRAMEBUFFER, uiFbo);
        glViewport(0, 0, w, h);
        glColorMask(true, true, true, true);
        try {
            if (gpuUi == null) gpuUi = new GpuUi();
            gpuUi.execute(rec, w, h, uiScale);
        } finally {
            rec.reset();
            glDisable(GL_BLEND); glDisable(GL_SCISSOR_TEST);
        }

        // pass 1: world + UI -> composed texture (internal resolution)
        glBindFramebuffer(GL_FRAMEBUFFER, composedFbo);
        glViewport(0, 0, w, h);
        glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND); glDisable(GL_SCISSOR_TEST); glDisable(GL_CULL_FACE);
        glColorMask(true, true, true, true);
        glUseProgram(compProgram);
        glUniform1i(compUiLoc, 0);
        glUniform1i(compWorldLoc, 1);
        glUniform1i(compUseWorldLoc, worldTex != 0 ? 1 : 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, worldTex);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, uiTexture);
        glBindVertexArray(compVao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

        // pass 2: FXAA / upscale over the whole surface (like the CPU canvas path, no letterbox)
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, Math.max(1, surfaceW), Math.max(1, surfaceH));
        glUseProgram(program);
        glUniform1i(samplerLoc, 0);
        glUniform1i(aaLoc, Platform.antiAliasing());
        glBindTexture(GL_TEXTURE_2D, composedTexture);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glBindVertexArray(0);
        glUseProgram(0);
        Gl.checkError("presentLayers");
        swap();
      }

    private void swap() {
        if (EGL14.eglSwapBuffers(dpy, surf)) { frames++; return; }
        int err = EGL14.eglGetError();
        if (err == 0x300E /* EGL_CONTEXT_LOST */) throw new IllegalStateException("EGL context lost");
        // EGL_BAD_SURFACE / EGL_BAD_NATIVE_WINDOW: the Surface is gone. Release it, the next frame re-attaches when a valid one exists.
        RLog.w("gpu", "eglSwapBuffers failed: 0x" + Integer.toHexString(err) + " -> surface released");
        detach();
      }

    // FXAA 3.11 (quality), identical algorithm to the PC shader. 1 = FXAA, 2 = FXAA Quality.
    private static final String FXAA_FRAGMENT =
            "#version 300 es\n" +
            "precision highp float; precision highp int; precision highp sampler2D;\n" +
            "in vec2 uv; out vec4 color; uniform sampler2D frameTexture; uniform int antiAliasingMode;\n" +
            "float luma(vec3 c){ return dot(c,vec3(0.299,0.587,0.114)); }\n" +
            "vec3 fetch(vec2 p){ return textureLod(frameTexture,p,0.0).rgb; }\n" +
            "float lumaAt(vec2 p){ return luma(fetch(p)); }\n" +
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
            "}\n";
}
