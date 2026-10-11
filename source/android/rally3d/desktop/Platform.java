package rally3d.desktop;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.GraphicsBridge;
import javax.microedition.lcdui.game.GameCanvas;
import rally3d.android.GameView;

/** Android runtime state: render size, FPS limit, settings persistence, view bridge. */
public final class Platform {
    private Platform() {}

    public static final int LOGICAL_HEIGHT = 320;
    public static final int[][] RESOLUTIONS = {
        {640, 480}, {800, 600}, {960, 540}, {1024, 768},
        {1280, 720}, {1280, 768}, {1152, 864}, {1280, 800},
        {1360, 768}, {1366, 768}, {1280, 960}, {1506, 847},
        {1440, 900}, {1280, 1024}, {1600, 900}, {1440, 1080},
        {1600, 1024}, {1707, 960}, {1680, 1050}, {1600, 1200},
        {1920, 1080}, {1969, 1108}, {1920, 1200}, {2176, 1224},
        {1920, 1440}, {2304, 1296}, {2560, 1440}
    };
    /** alpha55 and older saved the preset as an index into this table. */
    private static final int[][] LEGACY_RESOLUTIONS = {
        {640, 480}, {800, 600}, {960, 540}, {1280, 720}
    };
    private static int findResolution(int w, int h, int def) { /*T*/ final long $t = rally3d.core.Trace.in(577); try {
        for (int i = 0; i < RESOLUTIONS.length; i++) if (RESOLUTIONS[i][0] == w && RESOLUTIONS[i][1] == h) return i;
        return def;
     } finally { rally3d.core.Trace.out(577, $t); } }
    public static final int[] FPS_VALUES = {30, 60, 75, 90, 120, 144, 165, 180, 200, 240, 300, 360};

    public interface FrameListener { void onFrame(int[] pixels, int w, int h); }

    // Context / paths
    public static Context appContext;
    private static File dataDir, cacheDir;
    public static volatile GameView view;
    public static volatile int screenW = 1920, screenH = 1080;
    /** True once the first-run calibration (max resolution preset + max refresh rate) has run and been saved. */
    private static volatile boolean calibrated = false;

    /** Add-ons root: Android/data/com.rally3d.android/files/addons (sub-folders maps / cars / wheels are created by AddonManager.ensureDirs). */
    public static File addonsDir() { File d = new File(dataDir, "addons"); d.mkdirs(); return d; }

    /** No older location on Android. */
    public static File legacyAddonsDir() { return null; }

    public static volatile android.app.Activity activity;
    private static volatile java.util.concurrent.CountDownLatch importLatch;
    private static volatile int importCount;
    public static final int ADDON_PICK_REQUEST = 7701;

    /** Opens the system file picker (no storage permission needed), blocks the game thread until it closes, returns how many zips were added. */
    public static int importAddons() { /*T*/ final long $t = rally3d.core.Trace.in(290); try {
        final android.app.Activity a = activity;
        if (a == null) return 0;
        importCount = 0;
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        importLatch = latch;
        a.runOnUiThread(new Runnable() { public void run() { /*T*/ final long $t = rally3d.core.Trace.in(289); try {
            try {
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true);
                i.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
                a.startActivityForResult(i, ADDON_PICK_REQUEST);
            } catch (Throwable t) { rally3d.core.RLog.caught("Platform.importAddons", t); rally3d.core.RLog.e("addon", "cannot open file picker: " + t); latch.countDown(); }
         } finally { rally3d.core.Trace.out(289, $t); } } });
        try { latch.await(15, java.util.concurrent.TimeUnit.MINUTES); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return importCount;
     } finally { rally3d.core.Trace.out(290, $t); } }

    /** Called from MainActivity.onActivityResult with the picker result (null = cancelled). */
    public static void onAddonPicked(android.content.Intent data) { /*T*/ final long $t = rally3d.core.Trace.in(291); try {
        try {
            if (data != null && appContext != null) {
                java.util.ArrayList<android.net.Uri> uris = new java.util.ArrayList<android.net.Uri>();
                if (data.getClipData() != null) for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
                else if (data.getData() != null) uris.add(data.getData());
                for (android.net.Uri u : uris) {
                    try {
                        String name = null;
                        android.database.Cursor c = appContext.getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
                        if (c != null) { try { if (c.moveToFirst()) name = c.getString(0); } finally { c.close(); } }
                        if (name == null) name = "addon-" + System.currentTimeMillis() + ".zip";
                        name = name.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
                        if (!name.toLowerCase().endsWith(".zip")) name += ".zip";
                        java.io.File dir = addonsDir(), target = new java.io.File(dir, name);
                        for (int i = 2; target.exists(); i++) target = new java.io.File(dir, name.replaceAll("(?i)\\.zip$", "") + "-" + i + ".zip");
                        java.io.File tmp = new java.io.File(dir, target.getName() + ".part");
                        java.io.InputStream in = appContext.getContentResolver().openInputStream(u);
                        if (in == null) continue;
                        long total = 0;
                        java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                        try {
                            byte[] buf = new byte[16384];
                            int n;
                            while ((n = in.read(buf)) > 0) { total += n; if (total > 700 * 1024) break; out.write(buf, 0, n); }
                        } finally { out.close(); in.close(); }
                        if (total > 700 * 1024) { tmp.delete(); rally3d.core.RLog.w("addon", "picked file too big, skipped: " + name); continue; }
                        if (tmp.renameTo(target)) importCount++;
                    } catch (Throwable t) { rally3d.core.RLog.caught("Platform.onAddonPicked", t); rally3d.core.RLog.e("addon", "import failed: " + t); }
                }
            }
        } finally {
            java.util.concurrent.CountDownLatch l = importLatch;
            if (l != null) l.countDown();
        }
     } finally { rally3d.core.Trace.out(291, $t); } }

    public static final int ADDON_EXPORT_REQUEST = 7702;
    private static volatile java.util.concurrent.CountDownLatch exportLatch;
    private static volatile byte[] exportData;
    private static volatile String exportResult;

    /** Safe file name for an export (letters, digits, dot, dash, underscore, space). */
    public static String safeFileName(String name, String ext) { /*T*/ final long $t = rally3d.core.Trace.in(554); try {
        String n = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (n.length() == 0) n = "map";
        if (n.length() > 60) n = n.substring(0, 60);
        if (!n.toLowerCase().endsWith(ext)) n += ext;
        return n;
     } finally { rally3d.core.Trace.out(554, $t); } }

    /**
     * Android save dialog (ACTION_CREATE_DOCUMENT, no storage permission needed): blocks the game thread until it closes and writes the zip
     * to the chosen place. Returns the saved file name or null (cancelled / failed). Without an activity the zip goes to files/addons/export.
     */
    public static String exportFile(String suggestedName, byte[] data) { /*T*/ final long $t = rally3d.core.Trace.in(555); try {
        final String fname = safeFileName(suggestedName, ".zip");
        if (data == null) return null;
        final android.app.Activity a = activity;
        if (a == null) {
            try {
                File dir = new File(addonsDir(), "export");
                dir.mkdirs();
                File t = new File(dir, fname);
                FileOutputStream o = new FileOutputStream(t);
                try { o.write(data); } finally { o.close(); }
                return t.getName();
            } catch (Throwable t) { rally3d.core.RLog.caught("Platform.exportFile", t); return null; }
        }
        exportData = data;
        exportResult = null;
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        exportLatch = latch;
        a.runOnUiThread(new Runnable() { public void run() { /*T*/ final long $t = rally3d.core.Trace.in(289); try {
            try {
                android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                i.setType("application/zip");
                i.putExtra(android.content.Intent.EXTRA_TITLE, fname);
                a.startActivityForResult(i, ADDON_EXPORT_REQUEST);
            } catch (Throwable t) { rally3d.core.RLog.caught("Platform.exportFile", t); rally3d.core.RLog.e("addon", "cannot open save dialog: " + t); latch.countDown(); }  } finally { rally3d.core.Trace.out(289, $t); } } });
        try { latch.await(15, java.util.concurrent.TimeUnit.MINUTES); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        exportData = null;
        return exportResult;
     } finally { rally3d.core.Trace.out(555, $t); } }

    /** Called from MainActivity.onActivityResult with the save dialog result (null = cancelled). */
    public static void onAddonExported(android.content.Intent data) { /*T*/ final long $t = rally3d.core.Trace.in(556); try {
        try {
            byte[] bytes = exportData;
            if (data != null && data.getData() != null && bytes != null && appContext != null) {
                android.net.Uri u = data.getData();
                java.io.OutputStream out = appContext.getContentResolver().openOutputStream(u, "wt");
                if (out != null) {
                    try { out.write(bytes); out.flush(); } finally { out.close(); }
                    String name = null;
                    android.database.Cursor c = appContext.getContentResolver().query(u, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
                    if (c != null) { try { if (c.moveToFirst()) name = c.getString(0); } finally { c.close(); } }
                    exportResult = name != null ? name : "map.zip";
                }
            }
        } catch (Throwable t) { rally3d.core.RLog.caught("Platform.onAddonExported", t); rally3d.core.RLog.e("addon", "export failed: " + t); exportResult = null; }
        finally {
            java.util.concurrent.CountDownLatch l = exportLatch;
            if (l != null) l.countDown();
        }
     } finally { rally3d.core.Trace.out(556, $t); } }

    public static File dataDir() { return dataDir; }
    public static File cacheDir() { return cacheDir; }

    static volatile int resIndex = 0;            // internal render height = this preset (no cap since alpha56)
    /** Optional: lower the internal resolution when FPS drops (off by default: the selected resolution is what gets rendered). */
    private static volatile boolean dynamicResolution = false;
    public static boolean dynamicResolution() { return dynamicResolution; }
    public static void setDynamicResolution(boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(578); try { dynamicResolution = on; adaptiveRenderHeight = baseRenderHeight(); adaptiveSlowSamples = adaptiveFastSamples = 0; save();  } finally { rally3d.core.Trace.out(578, $t); } }
    private static volatile int adaptiveRenderHeight = 480;
    private static long adaptiveSampleStart = System.nanoTime();
    private static long adaptiveSampleFrames;
    private static int adaptiveSlowSamples, adaptiveFastSamples, adaptiveFastNeeded = 4;
    private static boolean adaptiveStall, adaptiveUpscaled;
    private static long adaptiveLastFrame = System.nanoTime();
    static volatile int fpsIndex = 1;
    static volatile int customFpsLimit = -1;
    static volatile boolean fullscreen = true;
    static volatile boolean showFps = false;
    private static volatile boolean telemetryEnabled = false;
    private static volatile boolean vanillaMode = false;
    static volatile boolean uiAnimationsEnabled = true;
    static volatile boolean mouseLookEnabled = true;
    private static final java.util.concurrent.atomic.AtomicInteger lookDx = new java.util.concurrent.atomic.AtomicInteger(), lookDy = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean cameraReset;
    static volatile boolean noSpriteLod = true;
    private static volatile int antiAliasing = 0;
    public static volatile boolean smoothScaling = true;
    /** Touch button size multiplier (settings slider, 0.5 .. 1.8). */
    public static volatile float buttonScale = 1.0f;
    /** Custom touch-button centres as fractions of the screen (gas, brake, pause, steer left, steer right); -1 = default layout. */
    public static final float[] btnPosX = {-1f, -1f, -1f, -1f, -1f}, btnPosY = {-1f, -1f, -1f, -1f, -1f};
    public static final float BTN_SCALE_MIN = 0.5f, BTN_SCALE_MAX = 1.8f;
    public static volatile boolean headless = false;
    public static volatile boolean racing = false;
    public static volatile FrameListener frameListener;
    private static final List<WeakReference<GameCanvas>> canvases = new ArrayList<WeakReference<GameCanvas>>();
    private static File config() { return new File(dataDir, "settings.properties"); }

    // ---- configuration ----
    public static void init(Context ctx) { /*T*/ final long $t = rally3d.core.Trace.in(169); try {
        appContext = ctx.getApplicationContext();
        // Android/data/<pkg>/files: app-specific storage, no permissions needed, visible to the user via file managers/USB
        java.io.File ext = appContext.getExternalFilesDir(null);
        dataDir = ext != null ? ext : appContext.getFilesDir();
        cacheDir = appContext.getCacheDir();
        dataDir.mkdirs();
     } finally { rally3d.core.Trace.out(169, $t); } }

    public static void load() { /*T*/ final long $t = rally3d.core.Trace.in(170); try {
        try {
            String lang = java.util.Locale.getDefault().toLanguageTag();
            if (lang.startsWith("zh-TW") || lang.startsWith("zh-HK") || lang.contains("Hant")) System.setProperty("microedition.locale", "zh-TW");
            else if (lang.startsWith("zh")) System.setProperty("microedition.locale", "zh-CN");
        } catch (Throwable ignored) { rally3d.core.RLog.caught("Platform.load", ignored);}
        Properties p = new Properties();
        File cfg = config();
        if (!cfg.exists()) {
            File bak = new File(cfg.getPath() + ".bak");
            if (bak.exists()) { cfg = bak; rally3d.core.RLog.e("save", "settings.properties missing/unreadable, using backup"); }
            else { rally3d.core.RLog.i("app", "no settings file yet (first run), calibrating"); calibrateFirstRun(); adaptiveRenderHeight = baseRenderHeight(); return; }
        }
        try (FileInputStream in = new FileInputStream(cfg)) { p.load(in); }
        catch (Exception e) { rally3d.core.RLog.caught("Platform.load", e); rally3d.core.RLog.e("save", "cannot read settings: " + e); adaptiveRenderHeight = baseRenderHeight(); return; }
        String wh = p.getProperty("resolutionWH");
        if (wh != null && wh.matches("\\d+x\\d+")) { String[] t = wh.split("x"); resIndex = findResolution(Integer.parseInt(t[0]), Integer.parseInt(t[1]), resIndex); }
        else { int old = clamp(parse(p.getProperty("resolution"), 0), 0, LEGACY_RESOLUTIONS.length - 1); resIndex = findResolution(LEGACY_RESOLUTIONS[old][0], LEGACY_RESOLUTIONS[old][1], resIndex); }
        dynamicResolution = Boolean.parseBoolean(p.getProperty("dynamicResolution", "false"));
        antiAliasing = clamp(parse(p.getProperty("antiAliasing"), 0), 0, 2);
        cpuThreads = clamp(parse(p.getProperty("cpuThreads"), 0), 0, 64);
        fpsIndex = clamp(parse(p.getProperty("fps"), fpsIndex), 0, FPS_VALUES.length - 1);
        customFpsLimit = parse(p.getProperty("fpsLimit"), -1);
        if (customFpsLimit > 1000) customFpsLimit = 1000;
        if (customFpsLimit < -1) customFpsLimit = -1;
        showFps = Boolean.parseBoolean(p.getProperty("showFps", "false"));
        telemetryEnabled = Boolean.parseBoolean(p.getProperty("telemetry", "false"));
        vanillaMode = Boolean.parseBoolean(p.getProperty("vanillaMode", "false"));
        uiAnimationsEnabled = Boolean.parseBoolean(p.getProperty("uiAnimations", "true"));
        noSpriteLod = Boolean.parseBoolean(p.getProperty("noSpriteLod", "true"));
        smoothScaling = Boolean.parseBoolean(p.getProperty("smooth", "true"));
        mouseLookEnabled = Boolean.parseBoolean(p.getProperty("mouseLook", "true"));
        try { buttonScale = Math.max(BTN_SCALE_MIN, Math.min(BTN_SCALE_MAX, Float.parseFloat(p.getProperty("btnScale", "1.0")))); } catch (NumberFormatException e) { rally3d.core.RLog.caught("Platform.load", e); }
        for (int i = 0; i < btnPosX.length; i++) {
            try {
                float bx = Float.parseFloat(p.getProperty("btnX" + i, "-1")), by = Float.parseFloat(p.getProperty("btnY" + i, "-1"));
                if (bx >= 0f && bx <= 1f && by >= 0f && by <= 1f) { btnPosX[i] = bx; btnPosY[i] = by; } else { btnPosX[i] = -1f; btnPosY[i] = -1f; }
            } catch (NumberFormatException e) { rally3d.core.RLog.caught("Platform.load", e); }
        }
        if (Boolean.parseBoolean(p.getProperty("traceAll", "false"))) rally3d.android.AndroidLog.setFullTrace(true);
        gpuRaster = Boolean.parseBoolean(p.getProperty("gpuRaster", "true"));
        vsync = Boolean.parseBoolean(p.getProperty("vsync", "true"));
        if (gpuTryFile().exists()) {   // the previous run died (or was killed) while the GPU renderer was starting
            gpuTryEnd();
            if (gpuRaster) { gpuRaster = false; rally3d.core.RLog.w("gpu", "previous start did not get past the first GPU frames -> GPU renderer switched off (Settings -> Display / FPS -> 3D renderer to try again)"); }
        }
        calibrated = Boolean.parseBoolean(p.getProperty("calibrated", "false"));
        if (!calibrated) calibrateFirstRun();
        adaptiveRenderHeight = baseRenderHeight();
     } finally { rally3d.core.Trace.out(170, $t); } }

    // Gamepad: not used on Android (physical pads arrive as key events).
    public static float controllerSteer() { return 0f; }
    public static float controllerThrottle() { return 0f; }
    public static float controllerBrake() { return 0f; }
    public static float controllerCameraX() { return 0f; }
    public static float controllerCameraY() { return 0f; }
    public static boolean controllerThrottleActive() { return false; }
    public static boolean controllerReadyDown() { return false; }
    public static boolean isControllerConnected() { return false; }

    /** Highest refresh rate (Hz) the default display supports (current rate if the mode list is unavailable). */
    public static float maxRefreshRate() { /*T*/ final long $t = rally3d.core.Trace.in(236); try {
        try {
            android.view.Display d = ((android.view.WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            float best = d.getRefreshRate();
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                for (android.view.Display.Mode m : d.getSupportedModes()) best = Math.max(best, m.getRefreshRate());
            }
            return best;
        } catch (Throwable t) { rally3d.core.RLog.caught("Platform.maxRefreshRate", t); return 60f; }
     } finally { rally3d.core.Trace.out(236, $t); } }

    /** First launch only: biggest resolution preset that fits the screen + the screen's max refresh rate as FPS cap. */
    private static void calibrateFirstRun() { /*T*/ final long $t = rally3d.core.Trace.in(237); try {
        try {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            ((android.view.WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
            int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
            int best = 0;
            long bestArea = -1;
            for (int i = 0; i < RESOLUTIONS.length; i++) {
                if (RESOLUTIONS[i][0] > sw || RESOLUTIONS[i][1] > sh || RESOLUTIONS[i][1] > (gpuRaster ? 1080 : 720)) continue;   // first run: <= 1080p (GPU renderer) / 720p (CPU raster); raise it in Settings
                long area = (long) RESOLUTIONS[i][0] * RESOLUTIONS[i][1];
                if (area > bestArea) { bestArea = area; best = i; }
            }
            resIndex = best;
            int hz = Math.round(maxRefreshRate());
            int preset = -1;
            for (int i = 0; i < FPS_VALUES.length; i++) if (Math.abs(FPS_VALUES[i] - hz) <= 1) preset = i;
            if (preset >= 0) { fpsIndex = preset; customFpsLimit = -1; } else if (hz > 0) { customFpsLimit = Math.min(1000, hz); }
            rally3d.core.RLog.i("app", "first-run calibration: screen " + sw + "x" + sh + " @" + hz + "Hz -> render preset "
                    + RESOLUTIONS[resIndex][0] + "x" + RESOLUTIONS[resIndex][1] + ", fps cap " + currentFpsLimit());
        } catch (Throwable t) { rally3d.core.RLog.caught("Platform.calibrateFirstRun", t); }
        calibrated = true;
        save();
     } finally { rally3d.core.Trace.out(237, $t); } }

    public static void save() { /*T*/ final long $t = rally3d.core.Trace.in(171); try {
        if (dataDir == null) return;
        Properties p = new Properties();
        p.setProperty("resolutionWH", RESOLUTIONS[resIndex][0] + "x" + RESOLUTIONS[resIndex][1]);
        p.setProperty("dynamicResolution", String.valueOf(dynamicResolution));
        p.setProperty("antiAliasing", String.valueOf(antiAliasing));
        p.setProperty("cpuThreads", String.valueOf(cpuThreads));
        p.setProperty("gpuRaster", String.valueOf(gpuRaster));
        p.setProperty("vsync", String.valueOf(vsync));
        p.setProperty("fps", String.valueOf(fpsIndex));
        if (customFpsLimit >= 0) p.setProperty("fpsLimit", String.valueOf(customFpsLimit));
        p.setProperty("showFps", String.valueOf(showFps));
        p.setProperty("telemetry", String.valueOf(telemetryEnabled));
        p.setProperty("vanillaMode", String.valueOf(vanillaMode));
        p.setProperty("calibrated", String.valueOf(calibrated));
        p.setProperty("uiAnimations", String.valueOf(uiAnimationsEnabled));
        p.setProperty("noSpriteLod", String.valueOf(noSpriteLod));
        p.setProperty("smooth", String.valueOf(smoothScaling));
        p.setProperty("mouseLook", String.valueOf(mouseLookEnabled));
        p.setProperty("btnScale", String.valueOf(buttonScale));
        for (int i = 0; i < btnPosX.length; i++) { p.setProperty("btnX" + i, String.valueOf(btnPosX[i])); p.setProperty("btnY" + i, String.valueOf(btnPosY[i])); }
        p.setProperty("traceAll", String.valueOf(rally3d.core.Trace.full));
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            p.store(bo, "Rally 3D Android settings");
            rally3d.core.SafeFile.write(config(), bo.toByteArray());
        } catch (Exception ignored) { rally3d.core.RLog.caught("Platform.save", ignored); rally3d.core.RLog.e("save", "FAILED to write settings: " + ignored); }
     } finally { rally3d.core.Trace.out(171, $t); } }

    private static int parse(String s, int def) { if (s == null) return def; try { return Integer.parseInt(s.trim()); } catch (Exception e) { rally3d.core.RLog.caught("Platform.parse", e); return def; } }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public static void configure(int res, int fpsIdx, boolean fs, boolean showFpsOverlay) { /*T*/ final long $t = rally3d.core.Trace.in(172); try {
        resIndex = clamp(res, 0, RESOLUTIONS.length - 1);
        adaptiveRenderHeight = baseRenderHeight();
        adaptiveSampleStart = System.nanoTime();
        adaptiveSampleFrames = 0;
        adaptiveSlowSamples = adaptiveFastSamples = 0;
        int nextFpsIndex = clamp(fpsIdx, 0, FPS_VALUES.length - 1);
        if (nextFpsIndex != fpsIndex) customFpsLimit = -1;
        fpsIndex = nextFpsIndex;
        showFps = showFpsOverlay;
     } finally { rally3d.core.Trace.out(172, $t); } }

    // ---- geometry ----
    private static int baseRenderHeight() { return Math.max(240, RESOLUTIONS[resIndex][1]); }
    public static int renderHeight() { /*T*/ final long $t = rally3d.core.Trace.in(579); try { return dynamicResolution ? Math.max(240, Math.min(baseRenderHeight(), adaptiveRenderHeight)) : baseRenderHeight();  } finally { rally3d.core.Trace.out(579, $t); } }
    private static double aspect() { /*T*/ final long $t = rally3d.core.Trace.in(173); try { return Math.max(1.0, Math.min(2.6, Math.max(screenW, screenH) / (double) Math.max(1, Math.min(screenW, screenH))));  } finally { rally3d.core.Trace.out(173, $t); } }
    public static int logicalHeight() { return LOGICAL_HEIGHT; }
    public static int logicalWidth() { return (int) Math.round(LOGICAL_HEIGHT * aspect()); }
    public static double scale() { return renderHeight() / (double) LOGICAL_HEIGHT; }
    public static int renderWidth() { return Math.max(1, (int) Math.round(logicalWidth() * scale())); }
    public static int displayWidth() { return screenW; }
    public static int displayHeight() { return screenH; }

    // ---- frame pacing ----
    public static long targetFrameNanos() {
        int f = currentFpsLimit();
        return f <= 0 ? 0L : 1000000000L / f;
    }
    public static int currentFpsLimit() { return customFpsLimit >= 0 ? customFpsLimit : FPS_VALUES[fpsIndex]; }
    public static void setFpsLimit(int limit) { /*T*/ final long $t = rally3d.core.Trace.in(174); try {
        limit = clamp(limit, 30, 1000);
        for (int i = 0; i < FPS_VALUES.length; i++) {
            if (FPS_VALUES[i] == limit) { fpsIndex = i; customFpsLimit = -1; save(); return; }
        }
        customFpsLimit = limit;
        save();
     } finally { rally3d.core.Trace.out(174, $t); } }
    /** True while a finished multiplayer racer watches the others (touch UI hides the right pedals). */
    public static volatile boolean spectating = false;
    /** True while a multiplayer race loop runs (Android: the pause button pauses on the first tap instead of asking for a second tap to exit). */
    public static volatile boolean mpRace = false;
    /** Set when the app goes to the background during a multiplayer race: the game pauses for everybody. */
    public static volatile boolean mpPauseRequest = false;
    /** Touch device: hints show the on-screen arrows instead of A / D. */
    public static boolean isTouchDevice() { return true; }
    /** Watching others: orbit camera works even if "Mouse camera" is off (not in Vanilla mode). */
    public static boolean isSpectateLookEnabled() { return spectating && !vanillaMode; }
    public static boolean isShowFps() { return showFps; }
    public static boolean isTelemetryEnabled() { return telemetryEnabled && !vanillaMode; }
    public static void setTelemetryEnabled(boolean enabled) { telemetryEnabled = enabled; save(); }
    public static boolean isVanillaMode() { return vanillaMode; }
    public static void setVanillaMode(boolean enabled) { vanillaMode = enabled; save(); }

    private static File ghostDir() { File d = new File(dataDir, "ghosts"); d.mkdirs(); return d; }
    public static byte[] loadGhost(int trackIndex) { /*T*/ final long $t = rally3d.core.Trace.in(175); try {
        if (trackIndex < 0 || trackIndex >= 12) return null;
        File file = new File(ghostDir(), "track-" + trackIndex + ".rpl");
        if (!file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] b = new byte[(int) file.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return b;
        } catch (Exception e) { rally3d.core.RLog.caught("Platform.loadGhost", e); return null; }
     } finally { rally3d.core.Trace.out(175, $t); } }
    public static boolean saveGhost(int trackIndex, byte[] data) { /*T*/ final long $t = rally3d.core.Trace.in(176); try {
        if (trackIndex < 0 || trackIndex >= 12 || data == null || data.length == 0) return false;
        File tmp = new File(ghostDir(), "track-" + trackIndex + ".rpl.tmp");
        File target = new File(ghostDir(), "track-" + trackIndex + ".rpl");
        try { rally3d.core.SafeFile.write(target, data); return true; }
        catch (Exception e) { rally3d.core.RLog.caught("Platform.saveGhost", e); rally3d.core.RLog.e("save", "FAILED to write ghost " + target + ": " + e); return false; }
     } finally { rally3d.core.Trace.out(176, $t); } }
    /** Ghost of the best run for this exact track+car. Falls back to the old one-per-track file (caller checks its carIndex). */
    public static byte[] loadGhost(int trackIndex, int carIndex) { /*T*/ final long $t = rally3d.core.Trace.in(292); try {
        if (trackIndex < 0 || trackIndex >= 12 || carIndex < 0 || carIndex > 3) return null;
        File file = new File(ghostDir(), "track-" + trackIndex + "-car-" + carIndex + ".rpl");
        if (!file.isFile()) return loadGhost(trackIndex);
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] b = new byte[(int) file.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return b;
        } catch (Exception e) { rally3d.core.RLog.caught("Platform.loadGhost", e); return null; }
     } finally { rally3d.core.Trace.out(292, $t); } }
    public static boolean saveGhost(int trackIndex, int carIndex, byte[] data) { /*T*/ final long $t = rally3d.core.Trace.in(293); try {
        if (trackIndex < 0 || trackIndex >= 12 || carIndex < 0 || carIndex > 3 || data == null || data.length == 0) return false;
        File target = new File(ghostDir(), "track-" + trackIndex + "-car-" + carIndex + ".rpl");
        try { rally3d.core.SafeFile.write(target, data); return true; }
        catch (Exception e) { rally3d.core.RLog.caught("Platform.saveGhost", e); rally3d.core.RLog.e("save", "FAILED to write ghost " + target + ": " + e); return false; }
     } finally { rally3d.core.Trace.out(293, $t); } }
    public static boolean areUiAnimationsEnabled() { return uiAnimationsEnabled; }
    public static void setUiAnimationsEnabled(boolean enabled) { uiAnimationsEnabled = enabled; save(); }
    /** "Mouse camera" on Android = one-finger drag on a free area of the screen orbits the camera around the car. */
    public static boolean fullTraceSupported() { return true; }
    public static boolean supportsTouchLayout() { return true; }
    public static float getButtonScale() { return buttonScale; }
    public static void setButtonScale(float v) { buttonScale = Math.max(BTN_SCALE_MIN, Math.min(BTN_SCALE_MAX, v)); }
    /** Non-blocking: shows a slider dialog (live preview of the touch buttons over the game). */
    public static void showButtonScaleDialog() { GameView v = view; if (v != null) v.showScaleDialog(); }
    /** Non-blocking: full-screen editor, drag any touch button to any position; saved in settings. */
    public static void showButtonLayoutEditor() { GameView v = view; if (v != null) v.startLayoutEditor(); }
    /** Called from Canvas.setTextEntryMode: shows / hides the Android soft keyboard. */
    public static void textEntryChanged(boolean enabled) { GameView v = view; if (v != null) v.setKeyboard(enabled); }
    public static boolean isFullTrace() { return rally3d.core.Trace.full; }
    public static void setFullTrace(boolean on) { rally3d.android.AndroidLog.setFullTrace(on); save(); }
    public static boolean isMouseLookEnabled() { return mouseLookEnabled && !vanillaMode; }
    public static void setMouseLookEnabled(boolean enabled) { mouseLookEnabled = enabled; save(); }
    public static void addMouseLookDelta(int dx, int dy) { if (dx != 0) lookDx.addAndGet(dx); if (dy != 0) lookDy.addAndGet(dy); }
    public static void requestCameraReset() { cameraReset = true; }
    public static boolean consumeCameraReset() { boolean r = cameraReset; cameraReset = false; return r; }
    public static boolean isNoSpriteLod() { return noSpriteLod && !vanillaMode; }
    public static void setNoSpriteLod(boolean enabled) { noSpriteLod = enabled; save(); }
    public static int antiAliasing() { return antiAliasing; }
    /** GPU renderer (GLES 3.0): 3D world in an FBO, UI replayed on the GPU, FXAA shader. Default on; falls back to the CPU renderer by itself. */
    private static volatile boolean gpuRaster = true;
    private static volatile boolean gpuFailed;
    private static volatile boolean gpuFallbackPending;
    /** V-sync (EGL swap interval 1) for the GPU renderer. Default on. */
    private static volatile boolean vsync = true;
    /** Shown in the menu: the renderer actually in use (a failed GPU start reads as Software until re-enabled). */
    public static boolean gpuRaster() { return gpuRaster && !gpuFailed; }
    public static boolean vsync() { return vsync; }
    public static void setVsync(boolean on) { vsync = on; save(); }
    /** True while frames go through the GPU pipeline (3D world FBO + recorded UI composed by GpuRenderer). */
    public static boolean gpuLayered() { return gpuRaster && !gpuFailed && view != null; }
    /** The GPU renderer broke (shader/driver/context error): everything runs on the CPU from the next frame, at <= 720p. Game thread. */
    public static void failGpu(Throwable error) { /*T*/ final long $t = rally3d.core.Trace.in(584); try {
        if (gpuFailed) return;
        gpuFailed = true;
        gpuFallbackPending = true;
        rally3d.core.RLog.e("gpu", "GPU renderer failed, switching to the CPU renderer (re-enable in Settings -> Display / FPS)", error);
        GameView v = view;
        if (v != null) v.releaseGpu();
     } finally { rally3d.core.Trace.out(584, $t); } }
    private static File gpuTryFile() { return new File(dataDir, "gpu.trying"); }
    /** Crash guard: marks "a GPU start is in progress"; removed after 180 presented GPU frames or when the app is paused. */
    public static void gpuTryBegin() { /*T*/ final long $t = rally3d.core.Trace.in(585); try { try { gpuTryFile().createNewFile(); } catch (Exception e) { rally3d.core.RLog.caught("Platform.gpuTryBegin", e); }  } finally { rally3d.core.Trace.out(585, $t); } }
    public static void gpuTryEnd() { /*T*/ final long $t = rally3d.core.Trace.in(586); try { try { gpuTryFile().delete(); } catch (Exception e) { rally3d.core.RLog.caught("Platform.gpuTryEnd", e); }  } finally { rally3d.core.Trace.out(586, $t); } }
    // ---- CPU multithreading (same API and semantics as PC) ----
    /** 0 = auto (all logical cores), otherwise the worker count. A changed value takes effect on the next parallelFor (old idle workers time out). */
    private static volatile int cpuThreads = 0;
    private static volatile java.util.concurrent.ThreadPoolExecutor workerPool;
    public static int cpuThreadsSetting() { return cpuThreads; }
    public static int cpuThreadsEffective() { /*T*/ final long $t = rally3d.core.Trace.in(580); try { int c = Math.max(1, Runtime.getRuntime().availableProcessors()); return cpuThreads <= 0 ? c : Math.min(cpuThreads, 64);  } finally { rally3d.core.Trace.out(580, $t); } }
    public static void setCpuThreads(int n) { cpuThreads = clamp(n, 0, 64); save(); workerPool = null; }
    /** Next value in the settings cycle: Auto -> 1 -> 2 -> 4 -> ... -> all cores -> Auto. */
    public static int nextCpuThreads() { /*T*/ final long $t = rally3d.core.Trace.in(581); try {
        int all = Math.max(1, Runtime.getRuntime().availableProcessors()), cur = cpuThreads;
        if (cur <= 0) return 1;
        int next = cur < 2 ? 2 : cur * 2;
        return next >= all ? 0 : next;
     } finally { rally3d.core.Trace.out(581, $t); } }
    private static java.util.concurrent.ThreadPoolExecutor pool() { /*T*/ final long $t = rally3d.core.Trace.in(582); try {
        java.util.concurrent.ThreadPoolExecutor p = workerPool;
        if (p != null) return p;
        synchronized (Platform.class) {
            if (workerPool == null) {
                final java.util.concurrent.atomic.AtomicInteger id = new java.util.concurrent.atomic.AtomicInteger();
                int n = cpuThreadsEffective();
                java.util.concurrent.ThreadPoolExecutor e = new java.util.concurrent.ThreadPoolExecutor(n, n, 3L, java.util.concurrent.TimeUnit.SECONDS,
                        new java.util.concurrent.LinkedBlockingQueue<Runnable>(), new java.util.concurrent.ThreadFactory() {
                    public Thread newThread(Runnable r) { Thread t = new Thread(r, "rally-worker-" + id.incrementAndGet()); t.setDaemon(true); return t; }
                });
                e.allowCoreThreadTimeOut(true);
                workerPool = e;
                rally3d.core.RLog.i("cpu", "Worker pool: " + n + " threads (" + Runtime.getRuntime().availableProcessors() + " logical cores)");
            }
            return workerPool;
        }
     } finally { rally3d.core.Trace.out(582, $t); } }
    /** Worker count the renderer should split work for. */
    public static int workers() { return cpuThreadsEffective(); }
    /** Runs body(0..n-1) on the worker pool and waits. n<=1, 1 thread or a call from a worker: runs inline. Exceptions propagate. */
    public static void parallelFor(int n, final java.util.function.IntConsumer body) { /*T*/ final long $t = rally3d.core.Trace.in(583); try {
        if (n <= 0) return;
        if (n == 1 || cpuThreadsEffective() <= 1 || Thread.currentThread().getName().startsWith("rally-worker-")) { for (int i = 0; i < n; i++) body.accept(i); return; }
        java.util.ArrayList<java.util.concurrent.Callable<Object>> jobs = new java.util.ArrayList<java.util.concurrent.Callable<Object>>(n);
        for (int ii = 0; ii < n; ii++) {
            final int c = ii;
            jobs.add(new java.util.concurrent.Callable<Object>() { public Object call() { body.accept(c); return null; } });
        }
        try {
            java.util.List<java.util.concurrent.Future<Object>> fs = pool().invokeAll(jobs);
            for (java.util.concurrent.Future<Object> f : fs) f.get();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (java.util.concurrent.ExecutionException e) { rally3d.core.RLog.caught("Platform.parallelFor", e);
            Throwable c = e.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            if (c instanceof Error) throw (Error) c;
            throw new RuntimeException(c);
        }
     } finally { rally3d.core.Trace.out(583, $t); } }
    public static boolean gpuUiSetting() { return false; }
    public static void setGpuUi(boolean on) { }
    public static void setGpuRaster(boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(587); try {
        boolean was = gpuRaster && !gpuFailed;
        gpuRaster = on;
        if (on) gpuFailed = false;   // explicit re-enable = try again
        save();
        if (on != was) rebuildGraphicsBuffers();
     } finally { rally3d.core.Trace.out(587, $t); } }

    /** Re-creates every canvas buffer (same size) so its mode matches the current renderer. Game thread. */
    static void rebuildGraphicsBuffers() { /*T*/ final long $t = rally3d.core.Trace.in(588); try {
        List<GameCanvas> list = new ArrayList<GameCanvas>();
        synchronized (canvases) {
            for (WeakReference<GameCanvas> r : canvases) { GameCanvas c = r.get(); if (c != null) list.add(c); }
        }
        for (GameCanvas c : list) c.rebuildBuffer();
        javax.microedition.m3g.Graphics3D.getInstance().dropHeldWorld();
     } finally { rally3d.core.Trace.out(588, $t); } }
    public static void setAntiAliasing(int mode) { antiAliasing = clamp(mode, 0, 2); save(); }
    public static void setMouseLookActive(boolean active) { racing = active; }
    public static int consumeMouseLookDeltaX() { return lookDx.getAndSet(0); }
    public static int consumeMouseLookDeltaY() { return lookDy.getAndSet(0); }
    private static final java.util.concurrent.atomic.AtomicInteger zoomMilli = new java.util.concurrent.atomic.AtomicInteger();
    /** Pinch zoom: same units as the PC mouse wheel (positive = camera away). */
    public static void addCameraZoomDelta(float d) { zoomMilli.addAndGet(Math.round(d * 1000f)); }
    public static float consumeCameraZoomDelta() { return zoomMilli.getAndSet(0) / 1000f; }
    public static int resolutionIndex() { return resIndex; }
    public static int fpsIndex() { return fpsIndex; }
    public static boolean isFullscreen() { return true; }

    public static boolean supportsVibration() { return false; }
    public static boolean isBackground() { GameView v = view; return v != null && !v.isSurfaceReady(); }
    public static void recordPerformance(int stage, long elapsedNanos) {}

    // ---- canvases / graphics ----
    public static void register(GameCanvas c) { synchronized (canvases) { canvases.add(new WeakReference<GameCanvas>(c)); } }
    public static Graphics newGraphics() { /*T*/ final long $t = rally3d.core.Trace.in(589); try { Graphics g = GraphicsBridge.create(renderWidth(), renderHeight(), scale()); GraphicsBridge.setRecording(g, gpuLayered()); return g;  } finally { rally3d.core.Trace.out(589, $t); } }
    public static void resizeGraphics(Graphics g) { /*T*/ final long $t = rally3d.core.Trace.in(590); try { GraphicsBridge.resize(g, renderWidth(), renderHeight(), scale()); GraphicsBridge.setRecording(g, gpuLayered());  } finally { rally3d.core.Trace.out(590, $t); } }

    private static volatile Canvas current;
    public static Canvas currentCanvas() { return current; }
    public static void showCanvas(Canvas c) { current = c; }

    public static void present(Graphics g) { /*T*/ final long $t = rally3d.core.Trace.in(177); try {
        FrameListener fl = frameListener;
        GameView v = view;
        if (GraphicsBridge.isRecording(g) && gpuLayered()) {
            if (v != null) v.presentGpu(g);
        } else {
            if (fl != null && !GraphicsBridge.isRecording(g)) fl.onFrame(g.pixelBuffer(), g.physicalWidth(), g.physicalHeight());
            if (v != null) v.present(g.pixelBuffer(), g.physicalWidth(), g.physicalHeight());
        }
        if (gpuFallbackPending) {
            // GPU renderer failed: the CPU renderer is much slower, so do not keep a GPU-sized resolution (settings keep it for a later retry).
            gpuFallbackPending = false;
            if (RESOLUTIONS[resIndex][1] > 720) {
                int best = 0; long bestArea = -1;
                for (int i = 0; i < RESOLUTIONS.length; i++) {
                    if (RESOLUTIONS[i][1] > 720 || RESOLUTIONS[i][0] > RESOLUTIONS[resIndex][0]) continue;
                    long area = (long) RESOLUTIONS[i][0] * RESOLUTIONS[i][1];
                    if (area > bestArea) { bestArea = area; best = i; }
                }
                resIndex = best; adaptiveRenderHeight = baseRenderHeight();
                rally3d.core.RLog.w("gpu", "render resolution lowered to " + RESOLUTIONS[best][0] + "x" + RESOLUTIONS[best][1] + " for the CPU renderer");
                save();
            }
            resizeGraphics(g);
        } else if (GraphicsBridge.isRecording(g) != gpuLayered()) {
            GraphicsBridge.setRecording(g, gpuLayered());   // renderer toggled
            javax.microedition.m3g.Graphics3D.getInstance().dropHeldWorld();
        }
        adaptRenderResolution(g);
     } finally { rally3d.core.Trace.out(177, $t); } }

    private static synchronized void adaptRenderResolution(Graphics g) { /*T*/ final long $t = rally3d.core.Trace.in(178); try {
        if (!dynamicResolution) return;
        adaptiveSampleFrames++;
        long now = System.nanoTime();
        if (now - adaptiveLastFrame > 700_000_000L) adaptiveStall = true;
        adaptiveLastFrame = now;
        long elapsed = now - adaptiveSampleStart;
        if (elapsed < 2_500_000_000L) return;
        double actualFps = adaptiveSampleFrames * 1_000_000_000.0 / Math.max(1L, elapsed);
        int configuredCap = currentFpsLimit();
        double targetFps = configuredCap <= 0 ? 60.0 : Math.min(configuredCap, 60.0);
        int oldHeight = renderHeight();
        int newHeight = oldHeight;
        int maximumHeight = baseRenderHeight();
        int minimumHeight = 240;
        boolean visible = view != null && view.isSurfaceReady();
        boolean stalled = adaptiveStall;
        adaptiveStall = false;
        if (!visible || stalled) {
            adaptiveSlowSamples = adaptiveFastSamples = 0;
        } else if (actualFps < targetFps * 0.88) {
            adaptiveSlowSamples++;
            adaptiveFastSamples = 0;
            if (adaptiveSlowSamples >= 2) {
                newHeight = Math.max(minimumHeight, oldHeight - 60);
                adaptiveSlowSamples = 0;
                if (adaptiveUpscaled) { adaptiveFastNeeded = Math.min(adaptiveFastNeeded * 2, 48); adaptiveUpscaled = false; }
            }
        } else if (actualFps >= targetFps * 0.95 && oldHeight < maximumHeight) {
            adaptiveFastSamples++;
            adaptiveSlowSamples = 0;
            if (adaptiveFastSamples >= adaptiveFastNeeded) { newHeight = Math.min(maximumHeight, oldHeight + 60); adaptiveFastSamples = 0; adaptiveUpscaled = true; }
        } else {
            adaptiveSlowSamples = adaptiveFastSamples = 0;
        }
        adaptiveSampleFrames = 0;
        adaptiveSampleStart = now;
        if (newHeight != oldHeight) {
            adaptiveRenderHeight = newHeight;
            resizeGraphics(g);
        }
     } finally { rally3d.core.Trace.out(178, $t); } }

    /** Executes a GL task with the EGL context current; false = no GL surface this frame (software for it) or the GPU renderer is off. */
    public static boolean runGpu(Runnable task) {
        GameView v = view;
        return gpuRaster && !gpuFailed && v != null && v.runGpu(task);
    }

    public static void applyResolution() { /*T*/ final long $t = rally3d.core.Trace.in(179); try {
        List<GameCanvas> list = new ArrayList<GameCanvas>();
        synchronized (canvases) {
            for (WeakReference<GameCanvas> r : canvases) { GameCanvas c = r.get(); if (c != null) list.add(c); }
        }
        for (GameCanvas c : list) c.rebuildBuffer();
     } finally { rally3d.core.Trace.out(179, $t); } }

    /** Non-blocking text input; the callback receives the entered text or null if cancelled. */
    public static void inputDialog(String title, String initial, int maxLen, Consumer<String> callback) { /*T*/ final long $t = rally3d.core.Trace.in(180); try {
        GameView v = view;
        if (v == null) { callback.accept(initial); return; }
        v.showInput(title, initial, maxLen, callback);
     } finally { rally3d.core.Trace.out(180, $t); } }

    public static String manifestProperty(String key) { return null; }

    public static void exit() {
        save();
        GameView v = view;
        if (v != null) v.finishApp();
        else System.exit(0);
    }
}
