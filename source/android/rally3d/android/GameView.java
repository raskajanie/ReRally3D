package rally3d.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.InputFilter;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;
import com.nokia.mid.appl.rally3d.TheMidlet;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Consumer;
import rally3d.desktop.Platform;

/** Presents the software framebuffer and turns touches into J2ME key / pointer events. */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private final Activity act;
    private volatile boolean surfaceReady;
    private boolean started;
    private Bitmap frame;
    private int[] aaBuf;
    private final Rect dst = new Rect();
    private final Paint smooth = new Paint(Paint.FILTER_BITMAP_FLAG), crisp = new Paint();
    private final Paint btnFill = new Paint(Paint.ANTI_ALIAS_FLAG), btnStroke = new Paint(Paint.ANTI_ALIAS_FLAG), btnText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile boolean crashShown;
    private volatile boolean previewButtons;   // slider dialog open: draw touch buttons over the menu

    // race controls (all on the right): buttons gas, brake, pause (right) + two arrow buttons for steering (bottom left, keys -3 / -4)
    private static final int NB = 5;                       // buttons with rects: gas, brake, pause, steer-left arrow, steer-right arrow
    private static final int[] KEYS = {-5, -2, -7, -3, -4}; // gas, brake, pause, steer left, steer right
    private static final String[] LABELS = {"GAS", "BRAKE", "II", "<", ">"};
    private final RectF[] rects = new RectF[NB];
    private final boolean[] down = new boolean[KEYS.length];
    private boolean lastRacing, lastSpectating;
    // layout editor (settings -> "Touch buttons position"): drag any button, DONE saves, RESET = default layout, CANCEL = discard
    private volatile boolean editing;
    private int editSel = -1, editBtnDown = -1;
    private float editDX, editDY;
    private final float[] backupX = new float[NB], backupY = new float[NB];
    private final RectF[] editBtns = {new RectF(), new RectF(), new RectF()};
    private static final String[] EDIT_LABELS = {"DONE", "RESET", "CANCEL"};
    private long pauseArmedUntil;
    private final android.graphics.Path arrow = new android.graphics.Path();

    // in-game log overlay (drawn by the game itself, rally3d.core.LogView): LOG / TAB buttons left of pause, triple-tap top-left as menu fallback
    private int cornerTaps;
    private long cornerFirstTap;
    private int tabPointer = -1, swallowPointer = -1, logDragPointer = -1;
    private float logDragLastY;
    // camera drag ("Mouse camera"): one free finger orbits the camera, double tap recenters it
    private int camId = -1;
    private int pinchA = -1, pinchB = -1;   // two free fingers = pinch zoom
    private float pinchLast = -1f;
    private boolean pinching;
    private float camX, camY, camDownX, camDownY;
    private long camDownT, camLastTapT;
    private boolean camMoved;

    // menu touch
    private float downX, downY, lastY, dragAccum;
    private boolean dragging;

    public GameView(Activity a) {
        super(a);
        act = a;
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
        Platform.view = this;
        btnStroke.setStyle(Paint.Style.STROKE);
        btnText.setTextAlign(Paint.Align.CENTER);
        btnText.setFakeBoldText(true);
        for (int i = 0; i < rects.length; i++) rects[i] = new RectF();
    }

    public boolean isSurfaceReady() { return surfaceReady; }

    @Override public void surfaceCreated(SurfaceHolder h) { surfaceReady = true; rally3d.core.RLog.i("surface", "created"); }

    @Override public void surfaceChanged(SurfaceHolder h, int fmt, int w, int hh) { /*T*/ final long $t = rally3d.core.Trace.in(186); try {
        if (!started && w >= hh) {
            Platform.screenW = w;
            Platform.screenH = hh;
            started = true;
            startGame();
        }
        layoutButtons(w, hh);
     } finally { rally3d.core.Trace.out(186, $t); } }

    @Override public void surfaceDestroyed(SurfaceHolder h) { /*T*/ final long $t = rally3d.core.Trace.in(209); try { surfaceReady = false; waitGpuDetached(); rally3d.core.RLog.w("surface", "destroyed (app backgrounded or surface lost)");  } finally { rally3d.core.Trace.out(209, $t); } }

    private void startGame() { /*T*/ final long $t = rally3d.core.Trace.in(188); try {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) { /*T*/ final long $t = rally3d.core.Trace.in(235); try {
                crash(t, e);
                if (t == android.os.Looper.getMainLooper().getThread()) { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
             } finally { rally3d.core.Trace.out(235, $t); } }
        });
        Thread t = new Thread(new Runnable() {
            public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try {
                try {
                    rally3d.core.RLog.i("app", "loading platform/settings");
                    Platform.load();
                    TheMidlet midlet = new TheMidlet();
                    rally3d.core.RLog.i("app", "midlet start");
                    midlet.startApp();
                } catch (Throwable e) { rally3d.core.RLog.caught("GameView.run", e);
                    crash(Thread.currentThread(), e);
                }
             } finally { rally3d.core.Trace.out(187, $t); } }
        }, "midlet");
        t.start();
     } finally { rally3d.core.Trace.out(188, $t); } }

    private void crash(Thread t, Throwable e) { /*T*/ final long $t = rally3d.core.Trace.in(189); try {
        try { Watchdog.crashReport(t, e); } catch (Throwable ignored) { rally3d.core.RLog.caught("GameView.crash", ignored);}
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        final String text = "Thread " + t.getName() + "\n" + sw;
        rally3d.core.RLog.e("crash", text);
        try {
            File f = new File(Platform.dataDir(), "crash.txt");
            FileWriter w = new FileWriter(f, true);
            w.write(text + "\n----\n");
            w.close();
        } catch (Exception ignored) { rally3d.core.RLog.caught("GameView.crash", ignored);}
        if (crashShown) return;
        crashShown = true;
        act.runOnUiThread(new Runnable() {
            public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try {
                try {
                    EditText tv = new EditText(act);
                    tv.setText(text);
                    tv.setTextSize(10);
                    tv.setKeyListener(null);
                    new AlertDialog.Builder(act).setTitle("Rally 3D crashed").setView(tv)
                            .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) { finishApp(); }
                            }).setCancelable(false).show();
                } catch (Throwable ignored) { rally3d.core.RLog.caught("GameView.run", ignored);}  } finally { rally3d.core.Trace.out(187, $t); } }
        });
     } finally { rally3d.core.Trace.out(189, $t); } }

    // ---------------- GPU (GLES 3.0) presenting ----------------
    private final GpuRenderer gpu = new GpuRenderer();
    private final Object gpuLock = new Object();
    private int gpuFailRuns;
    private boolean gpuTrying;
    private OverlayView overlay;
    private boolean overlayShown;
    private long lastOverlayInvalidate;

    /** Touch buttons for the GPU renderer: an ordinary View above the SurfaceView (the surface is owned by EGL then and cannot be drawn on with a Canvas). */
    private final class OverlayView extends android.view.View {
        OverlayView(android.content.Context c) { super(c); setWillNotDraw(false); }
        @Override protected void onDraw(Canvas c) {
            if (gpu.attached() && (Platform.racing || previewButtons || editing)) drawOverlay(c);
        }
    }
    public android.view.View overlayView() { if (overlay == null) overlay = new OverlayView(getContext()); return overlay; }

    /** Surface is going away: the game thread must release the EGL window surface first (it is the thread that owns the context). Waits briefly. */
    private void waitGpuDetached() { /*T*/ final long $t = rally3d.core.Trace.in(591); try {
        long end = System.currentTimeMillis() + 600;
        synchronized (gpuLock) {
            while (gpu.attached() && System.currentTimeMillis() < end) {
                try { gpuLock.wait(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
     } finally { rally3d.core.Trace.out(591, $t); } }
    private void gpuDetach() { if (gpu.attached()) { gpu.detach(); synchronized (gpuLock) { gpuLock.notifyAll(); } } }

    /** Game thread: releases the GPU renderer for good (it failed). */
    public void releaseGpu() { gpu.destroy(); synchronized (gpuLock) { gpuLock.notifyAll(); } }
    public boolean gpuAttached() { return gpu.attached(); }

    /** Executes a GL task with the EGL context current on the game thread. False = no surface / not usable now (the caller draws in software). */
    public boolean runGpu(Runnable task) { /*T*/ final long $t = rally3d.core.Trace.in(592); try {
        if (!surfaceReady) { gpuDetach(); return false; }
        try {
            if (!gpu.attached()) {
                android.view.Surface s = getHolder().getSurface();
                if (s == null || !s.isValid()) return false;
                if (!gpuTrying) { gpuTrying = true; Platform.gpuTryBegin(); }
                gpu.attach(s);
            }
            boolean ok = gpu.run(task);
            if (ok) gpuFailRuns = 0;
            return ok;
        } catch (RuntimeException | LinkageError e) { rally3d.core.RLog.caught("GameView.runGpu", e);
            rally3d.core.RLog.w("gpu", "GL task failed (" + (gpuFailRuns + 1) + "/3): " + e);
            if (++gpuFailRuns >= 3 || String.valueOf(e.getMessage()).contains("context lost")) { Platform.failGpu(e); return false; }
            return false;
        }
     } finally { rally3d.core.Trace.out(592, $t); } }

    /** One GPU frame: compose the world texture + replayed UI, FXAA, swap; then refresh the touch-button overlay. */
    public void presentGpu(final javax.microedition.lcdui.Graphics g) { /*T*/ final long $t = rally3d.core.Trace.in(593); try {
        Watchdog.frame();
        if (!surfaceReady) {
            gpuDetach();
            try { Thread.sleep(30); } catch (InterruptedException ignored) {}
            return;
        }
        boolean racing = Platform.racing;
        rally3d.core.LogView.pauseButton = racing;
        flingStep();
        if (racing != lastRacing) { releaseAll(); lastRacing = racing; }
        if (Platform.spectating != lastSpectating) { releaseAll(); lastSpectating = Platform.spectating; }
        final javax.microedition.lcdui.UiRecorder rec = javax.microedition.lcdui.GraphicsBridge.recorder(g);
        final int w = g.physicalWidth(), h = g.physicalHeight();
        final int world = javax.microedition.m3g.Graphics3D.getInstance().takeGpuWorldTexture(w, h);
        final double sc = g.scale();
        if (rec != null) {
            Platform.runGpu(new Runnable() { public void run() { gpu.presentLayers(rec, w, h, world, sc); } });
        }
        if (gpuTrying && gpu.frames() >= 180) { gpuTrying = false; Platform.gpuTryEnd(); }
        OverlayView ov = overlay;
        if (ov != null) {
            boolean want = racing || previewButtons || editing;
            long now = System.nanoTime();
            if ((want && now - lastOverlayInvalidate > 33_000_000L) || (!want && overlayShown)) { lastOverlayInvalidate = now; ov.postInvalidate(); }
            overlayShown = want;
        }
     } finally { rally3d.core.Trace.out(593, $t); } }

    // ---------------- presenting ----------------
    public void present(int[] px, int w, int h) { /*T*/ final long $t = rally3d.core.Trace.in(190); try {
        Watchdog.frame();
        if (!surfaceReady) {
            gpuDetach();
            try { Thread.sleep(30); } catch (InterruptedException ignored) {}
            return;
        }
        gpuDetach();   // the CPU canvas path needs the Surface back from EGL
        if (overlayShown && overlay != null) { overlayShown = false; overlay.postInvalidate(); }
        boolean racing = Platform.racing;
        rally3d.core.LogView.pauseButton = racing;
        flingStep();
        if (racing != lastRacing) { releaseAll(); lastRacing = racing; }
        if (Platform.spectating != lastSpectating) { releaseAll(); lastSpectating = Platform.spectating; }   // gas/brake fingers must not stay "pressed" when the pedals disappear
        Bitmap f = frame;
        if (f == null || f.getWidth() != w || f.getHeight() != h) {
            f = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            frame = f;
        }
        for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000;
        f.setHasAlpha(false);   // opaque: Skia skips premultiply on setPixels (alpha already forced to 0xFF above)
        int[] shown = px;
        int aa = Platform.antiAliasing();
        if (aa > 0) {   // CPU FXAA into a separate buffer (the game buffer is not always fully redrawn)
            if (aaBuf == null || aaBuf.length < w * h) aaBuf = new int[w * h];
            try { rally3d.core.Fxaa.apply(px, aaBuf, w, h, aa); shown = aaBuf; } catch (Throwable t) { rally3d.core.RLog.caught("GameView.fxaa", t); }
        }
        f.setPixels(shown, 0, w, 0, 0, w, h);
        SurfaceHolder holder = getHolder();
        Canvas c;
        try { c = holder.lockHardwareCanvas(); } catch (Throwable t) { rally3d.core.RLog.caught("GameView.present", t); return; }
        if (c == null) return;
        try {
            dst.set(0, 0, c.getWidth(), c.getHeight());
            c.drawBitmap(f, null, dst, Platform.smoothScaling ? smooth : crisp);
            if (racing || previewButtons || editing) drawOverlay(c);
        } finally {
            try { holder.unlockCanvasAndPost(c); } catch (Throwable ignored) { rally3d.core.RLog.caught("GameView.present", ignored);}
        }
     } finally { rally3d.core.Trace.out(190, $t); } }

    private void layoutButtons(int w, int h) { /*T*/ final long $t = rally3d.core.Trace.in(191); try {
        float sc = Platform.getButtonScale();          // settings slider (0.5 .. 1.8)
        float m = h * 0.05f;
        float aw = h * 0.27f * sc, ah = h * 0.30f * sc, gap = h * 0.08f * sc;   // steering arrows: bottom left, side by side
        rects[3].set(m, h - m - ah, m + aw, h - m);
        rects[4].set(m + aw + gap, h - m - ah, m + aw + gap + aw, h - m);
        float gw = h * 0.20f * sc, gh = h * 0.38f * sc;  // GAS pedal: tall and narrow, bottom right
        rects[0].set(w - m - gw, h - m - gh, w - m, h - m);
        float bw = h * 0.32f * sc, bh = h * 0.36f * sc;  // BRAKE pedal (arm + wide pad), left of GAS
        float gap2 = Math.max(m, h * 0.09f * sc);
        rects[1].set(w - m - gw - gap2 - bw, h - m - bh, w - m - gw - gap2, h - m);
        float p = h * 0.12f;                   // pause: top right (not scaled, stays clear of LOG)
        rects[2].set(w - m - p, m, w - m, m + p);
        for (int i = 0; i < NB; i++) {         // user positions from the layout editor override the defaults
            float fx = Platform.btnPosX[i], fy = Platform.btnPosY[i];
            if (fx < 0f || fy < 0f) continue;
            float rw = rects[i].width(), rh = rects[i].height();
            float cx = Math.max(rw / 2f, Math.min(w - rw / 2f, fx * w)), cy = Math.max(rh / 2f, Math.min(h - rh / 2f, fy * h));
            rects[i].set(cx - rw / 2f, cy - rh / 2f, cx + rw / 2f, cy + rh / 2f);
        }
        float ew = h * 0.30f, eh = h * 0.12f, eg = h * 0.03f, ex = w / 2f - (3 * ew + 2 * eg) / 2f;
        for (int i = 0; i < 3; i++) editBtns[i].set(ex + i * (ew + eg), h * 0.10f, ex + i * (ew + eg) + ew, h * 0.10f + eh);
     } finally { rally3d.core.Trace.out(191, $t); } }

    private final Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pr = new RectF(), pr2 = new RectF();
    private static final int[] METAL = {0xFF5A5A5A, 0xFFE2E2E2, 0xFF8E8E8E, 0xFF3E3E3E};
    private static final float[] METAL_POS = {0f, 0.32f, 0.68f, 1f};

    /** Brushed steel part (horizontal gradient = cylinder look). */
    private void steel(Canvas c, float l, float t, float r, float b, float rad, int alpha) { /*T*/ final long $t = rally3d.core.Trace.in(241); try {
        pr.set(l, t, r, b);
        pp.setStyle(Paint.Style.FILL);
        pp.setShader(new android.graphics.LinearGradient(l, 0, r, 0, METAL, METAL_POS, android.graphics.Shader.TileMode.CLAMP));
        pp.setAlpha(alpha);
        c.drawRoundRect(pr, rad, rad, pp);
        pp.setShader(null);
        pp.setStyle(Paint.Style.STROKE);
        pp.setStrokeWidth(Math.max(1.5f, (r - l) * 0.015f));
        pp.setColor(Color.argb(alpha, 25, 25, 25));
        c.drawRoundRect(pr, rad, rad, pp);
     } finally { rally3d.core.Trace.out(241, $t); } }

    /** Rubber pedal pad: steel frame, black rubber inset, raised horizontal ribs, drop shadow. */
    private void rubberPad(Canvas c, float l, float t, float r, float b, float rad, int ribs, boolean on, int alpha) { /*T*/ final long $t = rally3d.core.Trace.in(242); try {
        float w = r - l, h = b - t;
        pp.setStyle(Paint.Style.FILL);
        pp.setColor(Color.argb(110, 0, 0, 0));                          // shadow
        pr.set(l + w * 0.03f, t + h * 0.025f, r + w * 0.03f, b + h * 0.025f);
        c.drawRoundRect(pr, rad, rad, pp);
        pr.set(l, t, r, b);                                              // steel frame (vertical gradient)
        pp.setShader(new android.graphics.LinearGradient(0, t, 0, b, new int[]{0xFFD0D0D0, 0xFF7A7A7A, 0xFF3A3A3A}, new float[]{0f, 0.5f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        pp.setAlpha(alpha);
        c.drawRoundRect(pr, rad, rad, pp);
        float in = Math.max(3f, w * 0.07f);                              // rubber inset
        pr2.set(l + in, t + in, r - in, b - in);
        float rr = Math.max(2f, rad * 0.6f);
        pp.setShader(new android.graphics.LinearGradient(l, 0, r, 0,
                on ? new int[]{0xFF3A3A3A, 0xFF686868, 0xFF343434} : new int[]{0xFF222222, 0xFF444444, 0xFF1C1C1C}, new float[]{0f, 0.5f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        pp.setAlpha(alpha);
        c.drawRoundRect(pr2, rr, rr, pp);
        pp.setShader(null);
        c.save();                                                         // ribs: dark groove + light edge
        c.clipRect(pr2);
        float y0 = pr2.top + pr2.height() * 0.06f, step = pr2.height() * 0.88f / ribs, sw = Math.max(2f, step * 0.34f);
        pp.setStyle(Paint.Style.STROKE);
        for (int k = 0; k < ribs; k++) {
            float y = y0 + step * (k + 0.5f);
            pp.setStrokeWidth(sw);
            pp.setColor(Color.argb(alpha, 8, 8, 8));
            c.drawLine(pr2.left, y + sw * 0.6f, pr2.right, y + sw * 0.6f, pp);
            pp.setStrokeWidth(Math.max(1f, sw * 0.5f));
            pp.setColor(Color.argb((int) (alpha * 0.55f), 190, 190, 190));
            c.drawLine(pr2.left, y - sw * 0.35f, pr2.right, y - sw * 0.35f, pp);
        }
        c.restore();
        pp.setColor(Color.argb(alpha, 15, 15, 15));                      // outline
        pp.setStrokeWidth(Math.max(1.5f, w * 0.015f));
        pr.set(l, t, r, b);
        c.drawRoundRect(pr, rad, rad, pp);
        pp.setStyle(Paint.Style.FILL);
     } finally { rally3d.core.Trace.out(242, $t); } }

    private void bolt(Canvas c, float x, float y, float rad, int alpha) { /*T*/ final long $t = rally3d.core.Trace.in(243); try {
        pp.setStyle(Paint.Style.FILL);
        pp.setColor(Color.argb(alpha, 40, 40, 40));
        c.drawCircle(x, y, rad, pp);
        pp.setColor(Color.argb(alpha, 170, 170, 170));
        c.drawCircle(x - rad * 0.2f, y - rad * 0.2f, rad * 0.55f, pp);
     } finally { rally3d.core.Trace.out(243, $t); } }

    /** Real car pedals. GAS: organ pedal (long ribbed plate hinged at the floor). BRAKE: hanging arm with a wide ribbed pad. Pressed = moves down. */
    private void drawPedal(Canvas c, RectF r, boolean on, boolean gasPedal, float tsz) { /*T*/ final long $t = rally3d.core.Trace.in(244); try {
        float w = r.width(), h = r.height(), cx = r.centerX();
        int al = on ? 255 : 225;
        if (gasPedal) {
            float press = on ? h * 0.07f : 0f;
            float plateB = r.bottom - h * 0.11f;
            steel(c, cx - w * 0.36f, r.bottom - h * 0.12f, cx + w * 0.36f, r.bottom, w * 0.08f, al);    // hinge bracket
            bolt(c, cx - w * 0.22f, r.bottom - h * 0.06f, w * 0.06f, al);
            bolt(c, cx + w * 0.22f, r.bottom - h * 0.06f, w * 0.06f, al);
            rubberPad(c, cx - w * 0.40f, r.top + press, cx + w * 0.40f, plateB, w * 0.14f, 9, on, al);
            btnText.setColor(Color.argb(210, 235, 235, 235));
            btnText.setTextSize(tsz * 0.5f);
            c.drawText("GAS", cx, r.top + press + h * 0.085f - (btnText.descent() + btnText.ascent()) / 2f, btnText);
        } else {
            float press = on ? h * 0.06f : 0f;
            float padT = r.top + h * 0.50f + press;
            steel(c, cx - w * 0.07f, r.top, cx + w * 0.07f, padT + h * 0.05f, w * 0.03f, al);          // arm
            bolt(c, cx, r.top + w * 0.10f, w * 0.09f, al);                                               // pivot bolt
            rubberPad(c, r.left, padT, r.right, r.bottom, w * 0.10f, 6, on, al);
            btnText.setColor(Color.argb(210, 235, 235, 235));
            btnText.setTextSize(tsz * 0.5f);
            c.drawText("BRAKE", cx, padT + (r.bottom - padT) * 0.15f - (btnText.descent() + btnText.ascent()) / 2f, btnText);
        }
     } finally { rally3d.core.Trace.out(244, $t); } }

    private void drawOverlay(Canvas c) { /*T*/ final long $t = rally3d.core.Trace.in(192); try {
        if (rects[0].isEmpty()) layoutButtons(c.getWidth(), c.getHeight());
        float tsz = c.getHeight() * 0.06f;
        if (editing) c.drawColor(Color.argb(120, 0, 0, 0));
        boolean watching = Platform.spectating && !editing;   // finished racer is watching the others: no gas / brake pedals in the way
        for (int i = 0; i < NB; i++) {
            if (watching && i < 2) continue;
            RectF r = rects[i];
            boolean on = down[i] || (editing && editSel == i);
            if (i < 2) { drawPedal(c, r, on, i == 0, tsz); }
            else {
                btnFill.setColor(on ? Color.argb(150, 255, 255, 255) : Color.argb(70, 255, 255, 255));
                btnStroke.setColor(Color.argb(on ? 230 : 140, 255, 255, 255));
                btnStroke.setStrokeWidth(c.getHeight() * 0.006f);
                float rad = r.height() * 0.2f;
                c.drawRoundRect(r, rad, rad, btnFill);
                c.drawRoundRect(r, rad, rad, btnStroke);
                btnText.setColor(Color.argb(on ? 255 : 200, 255, 255, 255));
                if (i >= 3) {                                   // arrow triangle
                    float cx = r.centerX(), cy = r.centerY(), hw = r.width() * 0.22f, hh = r.height() * 0.26f, dir = i == 3 ? -1f : 1f;
                    arrow.rewind();
                    arrow.moveTo(cx + dir * hw, cy);
                    arrow.lineTo(cx - dir * hw, cy - hh);
                    arrow.lineTo(cx - dir * hw, cy + hh);
                    arrow.close();
                    btnFill.setColor(Color.argb(on ? 255 : 210, 255, 255, 255));
                    c.drawPath(arrow, btnFill);
                } else {
                    btnText.setTextSize(tsz * 0.9f);
                    float ty = r.centerY() - (btnText.descent() + btnText.ascent()) / 2f;
                    c.drawText(LABELS[i], r.centerX(), ty, btnText);
                }
            }
            if (editing) {                                      // yellow frame = draggable
                btnStroke.setColor(editSel == i ? Color.argb(255, 255, 230, 0) : Color.argb(200, 255, 230, 0));
                btnStroke.setStrokeWidth(c.getHeight() * (editSel == i ? 0.010f : 0.004f));
                c.drawRect(r, btnStroke);
            }
        }
        if (editing) {
            btnText.setColor(Color.WHITE);
            btnText.setTextSize(tsz * 0.8f);
            c.drawText("Drag buttons to any position", c.getWidth() / 2f, c.getHeight() * 0.07f, btnText);
            for (int i = 0; i < 3; i++) {
                RectF r = editBtns[i];
                btnFill.setColor(editBtnDown == i ? Color.argb(230, 255, 230, 0) : Color.argb(200, 30, 30, 30));
                btnStroke.setColor(Color.argb(255, 255, 255, 255));
                btnStroke.setStrokeWidth(c.getHeight() * 0.004f);
                c.drawRoundRect(r, r.height() * 0.2f, r.height() * 0.2f, btnFill);
                c.drawRoundRect(r, r.height() * 0.2f, r.height() * 0.2f, btnStroke);
                btnText.setColor(editBtnDown == i ? Color.BLACK : Color.WHITE);
                btnText.setTextSize(tsz * 0.7f);
                c.drawText(EDIT_LABELS[i], r.centerX(), r.centerY() - (btnText.descent() + btnText.ascent()) / 2f, btnText);
            }
        }
     } finally { rally3d.core.Trace.out(192, $t); } }

    /** Triple tap in the top-left corner within 1.2 s toggles the console. Returns true if the touch was consumed. */
    private boolean cornerToggle(MotionEvent e) { /*T*/ final long $t = rally3d.core.Trace.in(193); try {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
        if (e.getX() > getWidth() * 0.12f || e.getY() > getHeight() * 0.14f) return false;
        long now = System.currentTimeMillis();
        if (now - cornerFirstTap > 1200) { cornerTaps = 0; cornerFirstTap = now; }
        if (++cornerTaps >= 3) {
            cornerTaps = 0;
            rally3d.core.LogView.toggle();
            rally3d.core.RLog.i("app", "in-game log " + (rally3d.core.LogView.visible ? "opened" : "closed"));
            return true;
        }
        return false;
     } finally { rally3d.core.Trace.out(193, $t); } }

    // ---------------- input ----------------
    private void releaseAll() { /*T*/ final long $t = rally3d.core.Trace.in(194); try {
        camId = -1;
        javax.microedition.lcdui.Canvas cv = Platform.currentCanvas();
        for (int i = 0; i < down.length; i++) {
            if (down[i]) { down[i] = false; if (cv != null) cv.dispatchKey(KEYS[i], false); }
        }
     } finally { rally3d.core.Trace.out(194, $t); } }

    private float lx(float x) { return x * Platform.logicalWidth() / Math.max(1, getWidth()); }
    private float ly(float y) { return y * Platform.logicalHeight() / Math.max(1, getHeight()); }

    /** LOG / TAB buttons and overlay panel. Returns true if the event was fully consumed by the log UI. */
    private boolean logTouch(MotionEvent e, javax.microedition.lcdui.Canvas cv) { /*T*/ final long $t = rally3d.core.Trace.in(195); try {
        int act = e.getActionMasked();
        int ai = e.getActionIndex();
        int lw = Platform.logicalWidth(), lh = Platform.logicalHeight();
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            int x = (int) lx(e.getX(ai)), y = (int) ly(e.getY(ai));
            int b = rally3d.core.LogView.hit(x, y, lw, lh);
            if (b == rally3d.core.LogView.BTN_LOG) { rally3d.core.LogView.toggle(); swallowPointer = e.getPointerId(ai); return true; }
            if (b == rally3d.core.LogView.BTN_TAB) {
                tabPointer = e.getPointerId(ai);
                rally3d.core.LogView.tabHeld = true;
                cv.dispatchKey(-13, true);
                return true;
            }
            if (rally3d.core.LogView.visible && rally3d.core.LogView.panelTap(x, y, lw, lh)) { swallowPointer = e.getPointerId(ai); logDragPointer = swallowPointer; logDragLastY = ly(e.getY(ai)); return true; }
        }
        if (act == MotionEvent.ACTION_MOVE && logDragPointer >= 0 && rally3d.core.LogView.visible) {
            int di = e.findPointerIndex(logDragPointer);
            if (di >= 0) {
                float yy = ly(e.getY(di));
                rally3d.core.LogView.drag(yy - logDragLastY);
                logDragLastY = yy;
            }
        }
        boolean up = act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP;
        boolean cancel = act == MotionEvent.ACTION_CANCEL;
        if (logDragPointer >= 0 && (cancel || (up && e.getPointerId(ai) == logDragPointer))) logDragPointer = -1;
        if (tabPointer >= 0 && (cancel || (up && e.getPointerId(ai) == tabPointer))) {
            tabPointer = -1;
            rally3d.core.LogView.tabHeld = false;
            cv.dispatchKey(-13, false);
            if (up) return true;
        }
        if (swallowPointer >= 0 && (cancel || (up && e.getPointerId(ai) == swallowPointer))) {
            swallowPointer = -1;
            if (up) return true;
        }
        // single-finger gesture that started on a log button / panel: don't let it reach menus
        return swallowPointer >= 0 && !Platform.racing;
     } finally { rally3d.core.Trace.out(195, $t); } }

    @Override public boolean onTouchEvent(MotionEvent e) { /*T*/ final long $t = rally3d.core.Trace.in(196); try {
        { int am = e.getActionMasked(); if (am != MotionEvent.ACTION_MOVE) Watchdog.input("touch " + (am == 0 ? "down" : am == 1 ? "up" : am == 3 ? "cancel" : am == 5 ? "pdown" : am == 6 ? "pup" : "act" + am) + " x=" + (int) e.getX() + " y=" + (int) e.getY() + " n=" + e.getPointerCount() + (Platform.racing ? " race" : " menu")); }
        if (editing) { editTouch(e); return true; }
        if (cornerToggle(e)) return true;
        javax.microedition.lcdui.Canvas cv = Platform.currentCanvas();
        if (cv == null) return true;
        if (logTouch(e, cv)) return true;
        if (javax.microedition.lcdui.Canvas.rawPointer) { rawTouch(e, cv); return true; }
        if (Platform.racing) { raceTouch(e, cv); return true; }
        menuTouch(e, cv);
        return true;
     } finally { rally3d.core.Trace.out(196, $t); } }

    /** Map editor: every finger goes to the canvas raw queue (down / move / up with pointer ids) so drags and pinch zoom work. */
    private void rawTouch(MotionEvent e, javax.microedition.lcdui.Canvas cv) { /*T*/ final long $t = rally3d.core.Trace.in(503); try {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_POINTER_DOWN) {
            int i = e.getActionIndex();
            cv.dispatchRaw(0, (int) lx(e.getX(i)), (int) ly(e.getY(i)), e.getPointerId(i), 1);
        } else if (act == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) cv.dispatchRaw(1, (int) lx(e.getX(i)), (int) ly(e.getY(i)), e.getPointerId(i), 1);
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_POINTER_UP) {
            int i = e.getActionIndex();
            cv.dispatchRaw(2, (int) lx(e.getX(i)), (int) ly(e.getY(i)), e.getPointerId(i), 1);
        } else if (act == MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < e.getPointerCount(); i++) cv.dispatchRaw(2, (int) lx(e.getX(i)), (int) ly(e.getY(i)), e.getPointerId(i), 1);
        }
     } finally { rally3d.core.Trace.out(503, $t); } }

    private static final float LOOK_GAIN = 0.6f;   // screen px -> same units as PC mouse delta

    private void raceTouch(MotionEvent e, javax.microedition.lcdui.Canvas cv) { /*T*/ final long $t = rally3d.core.Trace.in(197); try {
        if (rects[0].isEmpty()) layoutButtons(getWidth(), getHeight());
        int act = e.getActionMasked();
        int skip = (act == MotionEvent.ACTION_POINTER_UP || act == MotionEvent.ACTION_UP) ? e.getActionIndex() : -1;
        boolean cancel = act == MotionEvent.ACTION_CANCEL;
        boolean[] now = new boolean[down.length];
        boolean camSeen = false;
        boolean look = Platform.isMouseLookEnabled() || Platform.isSpectateLookEnabled();
        int freeN = 0; int[] freeId = new int[2]; float[] freeX = new float[2], freeY = new float[2];
        if (!cancel) {
            for (int p = 0; p < e.getPointerCount(); p++) {
                if (p == skip) continue;
                int id = e.getPointerId(p);
                if (id == tabPointer || id == swallowPointer) continue;
                float x = e.getX(p), y = e.getY(p);
                if (id == camId) {
                    if (freeN < 2) { freeId[freeN] = id; freeX[freeN] = x; freeY[freeN] = y; freeN++; }
                    if (!pinching) Platform.addMouseLookDelta(Math.round((x - camX) * LOOK_GAIN), Math.round((y - camY) * LOOK_GAIN));
                    camX = x; camY = y; camSeen = true;
                    if (Math.abs(x - camDownX) + Math.abs(y - camDownY) > getHeight() * 0.03f) camMoved = true;
                    continue;
                }
                boolean hit = false;
                for (int i = 0; i < NB; i++) {
                    if (i < 2 && Platform.spectating) continue;   // pedals are hidden while watching
                    float pad = rects[i].height() * (i >= 3 ? 0.12f : 0.18f);
                    if (x >= rects[i].left - pad && x <= rects[i].right + pad && y >= rects[i].top - pad && y <= rects[i].bottom + pad) { now[i] = true; hit = true; }
                }
                if (!hit && freeN < 2) { freeId[freeN] = id; freeX[freeN] = x; freeY[freeN] = y; freeN++; }
                if (!hit && look && camId < 0 && !camSeen) {
                    camId = id; camX = camDownX = x; camY = camDownY = y; camSeen = true; camMoved = false;
                    camDownT = System.currentTimeMillis();
                }
            }
        }
        if (freeN >= 2 && !cancel) {   // pinch: spread = zoom in, pinch = zoom out
            if (pinchA != freeId[0] || pinchB != freeId[1]) { pinchA = freeId[0]; pinchB = freeId[1]; pinchLast = -1f; }
            float dist = (float)Math.hypot(freeX[0] - freeX[1], freeY[0] - freeY[1]);
            if (pinchLast > 0f) Platform.addCameraZoomDelta(-(dist - pinchLast) * 40f / Math.max(1, getHeight()));
            pinchLast = dist; pinching = true; camMoved = true;
        } else { pinchA = pinchB = -1; pinchLast = -1f; pinching = false; }
        if (camId >= 0 && !camSeen) {
            long t = System.currentTimeMillis();
            if (!cancel && !camMoved && t - camDownT < 250) {          // short tap; two in a row = recenter camera
                if (t - camLastTapT < 400) { Platform.requestCameraReset(); camLastTapT = 0; } else camLastTapT = t;
            }
            camId = -1;
        }
        if (now[2] && !down[2]) {
            long tn = System.currentTimeMillis();
            if (!Platform.mpRace && tn > pauseArmedUntil) {
                pauseArmedUntil = tn + 2000;
                now[2] = false;
                GameView.this.act.runOnUiThread(new Runnable() { public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try { android.widget.Toast.makeText(GameView.this.act, "Tap II again to exit the race", android.widget.Toast.LENGTH_SHORT).show();  } finally { rally3d.core.Trace.out(187, $t); } } });
            } else pauseArmedUntil = 0;
        }
        for (int i = 0; i < now.length; i++) {
            if (now[i] != down[i]) { down[i] = now[i]; cv.dispatchKey(KEYS[i], now[i]); }
        }
     } finally { rally3d.core.Trace.out(197, $t); } }

    private android.view.VelocityTracker vt;
    private float flingVel;                // px/s, finger-direction (positive = finger moving down)
    private long flingLast;

    private float rowPx() { /*T*/ final long $t = rally3d.core.Trace.in(198); try { return Math.max(8f, rally3d.core.LogView.menuRowLogical * getHeight() / (float) Platform.logicalHeight());  } finally { rally3d.core.Trace.out(198, $t); } }

    /** Momentum scrolling after a swipe; called every presented frame. */
    private void flingStep() { /*T*/ final long $t = rally3d.core.Trace.in(199); try {
        if (Math.abs(flingVel) < 40f || Platform.racing) { flingVel = 0; return; }
        javax.microedition.lcdui.Canvas cv = Platform.currentCanvas();
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - flingLast) / 1e9f);
        flingLast = now;
        if (cv == null) return;
        dragAccum -= flingVel * dt / rowPx();
        int rows = (int) dragAccum;
        if (rows != 0) { dragAccum -= rows; cv.dispatchPointerWheel(rows); }
        flingVel *= (float) Math.pow(0.04, dt);   // ~0.7 s to stop
     } finally { rally3d.core.Trace.out(199, $t); } }

    private void menuTouch(MotionEvent e, javax.microedition.lcdui.Canvas cv) { /*T*/ final long $t = rally3d.core.Trace.in(200); try {
        float x = e.getX(), y = e.getY();
        if (vt == null) vt = android.view.VelocityTracker.obtain();
        vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                flingVel = 0;
                vt.clear(); vt.addMovement(e);
                downX = x; downY = y; lastY = y; dragAccum = 0; dragging = false;
                cv.dispatchPointerMoved((int) lx(x), (int) ly(y));
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(y - downY) > getHeight() * 0.025f) dragging = true;
                if (dragging) {
                    dragAccum += (lastY - y) / rowPx();
                    int rows = (int) dragAccum;
                    if (rows != 0) { dragAccum -= rows; cv.dispatchPointerWheel(rows); }
                } else {
                    cv.dispatchPointerMoved((int) lx(x), (int) ly(y));
                }
                lastY = y;
                break;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    cv.dispatchPointerMoved((int) lx(x), (int) ly(y));
                    cv.dispatchPointerPressed((int) lx(x), (int) ly(y));
                } else {
                    vt.computeCurrentVelocity(1000);
                    flingVel = vt.getYVelocity();
                    flingLast = System.nanoTime();
                }
                dragging = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
            default:
                break;
        }
     } finally { rally3d.core.Trace.out(200, $t); } }

    // ---------------- lifecycle / dialogs ----------------
    public void onAppPause() { /*T*/ final long $t = rally3d.core.Trace.in(201); try {
        javax.microedition.lcdui.Canvas cv = Platform.currentCanvas();
        if (Platform.mpRace) Platform.mpPauseRequest = true;
        if (Platform.racing && cv != null) { releaseAll(); cv.dispatchKey(-7, true); cv.dispatchKey(-7, false); }
        javax.microedition.media.Manager.pauseAll();
        Platform.save();
        try { TheMidlet.saveNow(); } catch (Throwable ignored) { rally3d.core.RLog.caught("GameView.onAppPause", ignored);}
     } finally { rally3d.core.Trace.out(201, $t); } }

    public void onAppResume() { javax.microedition.media.Manager.resumeAll(); }

    // ---------------- touch button position editor ----------------
    public void startLayoutEditor() { /*T*/ final long $t = rally3d.core.Trace.in(245); try {
        for (int i = 0; i < NB; i++) { backupX[i] = Platform.btnPosX[i]; backupY[i] = Platform.btnPosY[i]; }
        layoutButtons(getWidth(), getHeight());
        editSel = -1; editBtnDown = -1;
        editing = true;
        rally3d.core.RLog.i("app", "touch layout editor opened");
     } finally { rally3d.core.Trace.out(245, $t); } }

    private int hitEditBtn(float x, float y) {
        for (int i = 0; i < 3; i++) if (editBtns[i].contains(x, y)) return i;
        return -1;
    }

    private void editTouch(MotionEvent e) { /*T*/ final long $t = rally3d.core.Trace.in(246); try {
        float x = e.getX(), y = e.getY();
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                editBtnDown = hitEditBtn(x, y);
                editSel = -1;
                if (editBtnDown >= 0) break;
                for (int i = NB - 1; i >= 0; i--) {              // top-most (last drawn) first
                    if (rects[i].contains(x, y)) { editSel = i; editDX = x - rects[i].centerX(); editDY = y - rects[i].centerY(); break; }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (editSel >= 0) {
                    float rw = rects[editSel].width(), rh = rects[editSel].height();
                    float cx = Math.max(rw / 2f, Math.min(w - rw / 2f, x - editDX)), cy = Math.max(rh / 2f, Math.min(h - rh / 2f, y - editDY));
                    Platform.btnPosX[editSel] = cx / w;
                    Platform.btnPosY[editSel] = cy / h;
                    layoutButtons(w, h);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int b = e.getActionMasked() == MotionEvent.ACTION_UP ? hitEditBtn(x, y) : -1;
                if (b >= 0 && b == editBtnDown) {
                    if (b == 0) { editing = false; Platform.save(); rally3d.core.RLog.i("app", "touch layout saved"); }
                    else if (b == 1) { for (int i = 0; i < NB; i++) { Platform.btnPosX[i] = -1f; Platform.btnPosY[i] = -1f; } layoutButtons(w, h); }
                    else { for (int i = 0; i < NB; i++) { Platform.btnPosX[i] = backupX[i]; Platform.btnPosY[i] = backupY[i]; } editing = false; layoutButtons(w, h); }
                }
                editSel = -1; editBtnDown = -1;
                break;
            }
            default: break;
        }
     } finally { rally3d.core.Trace.out(246, $t); } }

    // ---------------- touch button size slider ----------------
    public void showScaleDialog() { /*T*/ final long $t = rally3d.core.Trace.in(223); try {
        act.runOnUiThread(new Runnable() {
            public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try {
                try {
                    final float old = Platform.getButtonScale();
                    final int min = Math.round(Platform.BTN_SCALE_MIN * 100), max = Math.round(Platform.BTN_SCALE_MAX * 100);
                    final TextView label = new TextView(act);
                    label.setTextSize(20);
                    label.setText(Math.round(old * 100) + "%");
                    label.setPadding(40, 30, 40, 10);
                    final SeekBar bar = new SeekBar(act);
                    bar.setMax(max - min);
                    bar.setProgress(Math.round(old * 100) - min);
                    bar.setPadding(60, 20, 60, 30);
                    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) { /*T*/ final long $t = rally3d.core.Trace.in(222); try {
                            int pct = progress + min;
                            label.setText(pct + "%");
                            Platform.setButtonScale(pct / 100f);
                            layoutButtons(getWidth(), getHeight());
                         } finally { rally3d.core.Trace.out(222, $t); } }
                        public void onStartTrackingTouch(SeekBar sb) {}
                        public void onStopTrackingTouch(SeekBar sb) {}
                    });
                    LinearLayout box = new LinearLayout(act);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.addView(label);
                    box.addView(bar);
                    previewButtons = true;
                    layoutButtons(getWidth(), getHeight());
                    AlertDialog dlg = new AlertDialog.Builder(act).setTitle("Touch buttons size").setView(box).setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) { previewButtons = false; Platform.save(); }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) { previewButtons = false; Platform.setButtonScale(old); layoutButtons(getWidth(), getHeight()); }
                            }).create();
                    android.view.Window win = dlg.getWindow();
                    if (win != null) {   // top of the screen, no dimming: the buttons stay visible below
                        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                        win.setGravity(android.view.Gravity.TOP);
                    }
                    dlg.show();
                } catch (Throwable t) { previewButtons = false; rally3d.core.RLog.caught("GameView.showScaleDialog", t); }  } finally { rally3d.core.Trace.out(187, $t); } }
        });
     } finally { rally3d.core.Trace.out(223, $t); } }

    // ---------------- soft keyboard (in-game text fields) ----------------
    private volatile boolean keyboardWanted;
    private boolean imeSubmitted;
    private final StringBuilder composing = new StringBuilder();

    public void setKeyboard(final boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(224); try {
        keyboardWanted = on;
        act.runOnUiThread(new Runnable() {
            public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try {
                try {
                    InputMethodManager imm = (InputMethodManager) act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm == null) return;
                    if (on) {
                        composing.setLength(0);
                        requestFocus();
                        imm.restartInput(GameView.this);
                        imm.showSoftInput(GameView.this, InputMethodManager.SHOW_FORCED);
                        postDelayed(new Runnable() { public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try {
                            if (!keyboardWanted) return;
                            InputMethodManager m = (InputMethodManager) act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                            if (m != null) m.showSoftInput(GameView.this, InputMethodManager.SHOW_FORCED);  } finally { rally3d.core.Trace.out(187, $t); } } }, 250);
                    } else {
                        imm.hideSoftInputFromWindow(getWindowToken(), 0);
                    }
                } catch (Throwable t) { rally3d.core.RLog.caught("GameView.setKeyboard", t); }  } finally { rally3d.core.Trace.out(187, $t); } }
        });
     } finally { rally3d.core.Trace.out(224, $t); } }

    private void textChar(char ch) { /*T*/ final long $t = rally3d.core.Trace.in(225); try {
        javax.microedition.lcdui.Canvas cv = Platform.currentCanvas();
        if (cv != null) cv.dispatchTextCharacter(ch);
     } finally { rally3d.core.Trace.out(225, $t); } }
    private void textBackspace() { /*T*/ final long $t = rally3d.core.Trace.in(226); try {
        javax.microedition.lcdui.Canvas cv = Platform.currentCanvas();
        if (cv != null) cv.dispatchTextCommand(javax.microedition.lcdui.Canvas.TEXT_BACKSPACE);
     } finally { rally3d.core.Trace.out(226, $t); } }
    private void textEnter() { /*T*/ final long $t = rally3d.core.Trace.in(227); try {
        javax.microedition.lcdui.Canvas cv = Platform.currentCanvas();
        if (cv == null) return;
        // Down+up in one go is invisible to the per-frame key polling; the controller path latches a menu event
        // (confirm) so the game really sees Enter and leaves text mode (-> keyboard hides).
        cv.dispatchControllerKey(-5, true);
        cv.dispatchControllerKey(-5, false);
     } finally { rally3d.core.Trace.out(227, $t); } }
    private void textSetComposing(CharSequence t) { /*T*/ final long $t = rally3d.core.Trace.in(228); try {          // replace the composing region: backspace old, type new
        for (int i = 0; i < composing.length(); i++) textBackspace();
        composing.setLength(0);
        if (t != null) for (int i = 0; i < t.length(); i++) { char ch = t.charAt(i); textChar(ch); composing.append(ch); }
     } finally { rally3d.core.Trace.out(228, $t); } }

    @Override public boolean onCheckIsTextEditor() { return keyboardWanted; }

    @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) { /*T*/ final long $t = rally3d.core.Trace.in(230); try {
        if (!keyboardWanted) return null;
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;   // plain characters, no predictive composing
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new BaseInputConnection(this, false) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) { textSetComposing(text); composing.setLength(0); return true; }
            @Override public boolean setComposingText(CharSequence text, int newCursorPosition) { textSetComposing(text); return true; }
            @Override public boolean finishComposingText() { composing.setLength(0); return true; }
            @Override public boolean deleteSurroundingText(int before, int after) { for (int i = 0; i < Math.max(1, before); i++) textBackspace(); composing.setLength(0); return true; }
            @Override public boolean sendKeyEvent(KeyEvent ev) { /*T*/ final long $t = rally3d.core.Trace.in(229); try {
                if (ev.getAction() == KeyEvent.ACTION_DOWN) {
                    int kc = ev.getKeyCode();
                    if (kc == KeyEvent.KEYCODE_DEL) { textBackspace(); composing.setLength(0); }
                    else if (kc == KeyEvent.KEYCODE_ENTER) textEnter();
                    else { int u = ev.getUnicodeChar(); if (u > 31) textChar((char) u); }
                }
                return true;
             } finally { rally3d.core.Trace.out(229, $t); } }
            @Override public boolean performEditorAction(int actionCode) { textEnter(); return true; }
        };
     } finally { rally3d.core.Trace.out(230, $t); } }

    /** Back while the keyboard is up = cancel the edit (the IME would otherwise swallow it silently). */
    @Override public boolean onKeyPreIme(int keyCode, KeyEvent ev) { /*T*/ final long $t = rally3d.core.Trace.in(231); try {
        if (keyboardWanted && keyCode == KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() == KeyEvent.ACTION_UP) {
                javax.microedition.lcdui.Canvas cv = Platform.currentCanvas();
                if (cv != null) { cv.dispatchControllerKey(-7, true); cv.dispatchControllerKey(-7, false); }   // latched, see textEnter
            }
            return true;
        }
        return super.onKeyPreIme(keyCode, ev);
     } finally { rally3d.core.Trace.out(231, $t); } }

    public void showInput(final String title, final String initial, final int maxLen, final Consumer<String> cb) { /*T*/ final long $t = rally3d.core.Trace.in(202); try {
        act.runOnUiThread(new Runnable() {
            public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try {
                try {
                    final EditText et = new EditText(act);
                    et.setText(initial == null ? "" : initial);
                    et.setSingleLine(true);
                    if (maxLen > 0) et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLen)});
                    et.setSelection(et.getText().length());
                    et.setImeOptions(EditorInfo.IME_ACTION_DONE);
                    et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                    AlertDialog dlg = new AlertDialog.Builder(act).setTitle(title).setView(et).setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) { cb.accept(et.getText().toString()); }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) { cb.accept(null); }
                            }).create();
                    android.view.Window win = dlg.getWindow();
                    if (win != null) {
                        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                        win.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    }
                    dlg.show();
                    et.requestFocus();
                } catch (Throwable t) { rally3d.core.RLog.caught("GameView.run", t); cb.accept(initial); }  } finally { rally3d.core.Trace.out(187, $t); } }
        });
     } finally { rally3d.core.Trace.out(202, $t); } }

    public void finishApp() { /*T*/ final long $t = rally3d.core.Trace.in(203); try {
        javax.microedition.media.Manager.closeAll();   // silence the music immediately, before the activity/process teardown
        act.runOnUiThread(new Runnable() {
            public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try {
                try { act.finishAndRemoveTask(); } catch (Throwable t) { rally3d.core.RLog.caught("GameView.run", t); act.finish(); }  } finally { rally3d.core.Trace.out(187, $t); } }
        });
        new Thread(new Runnable() {
            public void run() { /*T*/ final long $t = rally3d.core.Trace.in(187); try {
                try { Thread.sleep(600); } catch (InterruptedException ignored) {}
                android.os.Process.killProcess(android.os.Process.myPid());  } finally { rally3d.core.Trace.out(187, $t); } }
        }).start();
     } finally { rally3d.core.Trace.out(203, $t); } }
}
