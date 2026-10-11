package rally3d.desktop;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

/** Desktop runtime state: render resolution, FPS limit, window, settings persistence. */
public final class Platform {
    private Platform() {}

    /** Logical (game-visible) canvas height; width follows the window aspect ratio. */
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
    /** Presets of alpha55 and older (saved as an index): migrated by size to the current table. */
    private static final int[][] LEGACY_RESOLUTIONS = {
        {640, 480}, {800, 600}, {960, 540}, {1024, 768}, {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}
    };
    private static int findResolution(int w, int h, int def) {
        for (int i = 0; i < RESOLUTIONS.length; i++) if (RESOLUTIONS[i][0] == w && RESOLUTIONS[i][1] == h) return i;
        return def;
    }
    /** Common desktop frame caps, from 30 through 360 FPS. */
    public static final int[] FPS_VALUES = {30, 60, 75, 90, 120, 144, 165, 180, 200, 240, 300, 360, 0};

    public interface FrameListener { void onFrame(int[] pixels, int w, int h); }

    // Default to 1080p; a previously saved user resolution is loaded below and preserved.
    static volatile int resIndex = 20;   // 1920x1080
    private static long adaptiveSampleStart = System.nanoTime();
    private static long adaptiveSampleFrames;
    private static int adaptiveSlowSamples, adaptiveFastSamples;
    static volatile int fpsIndex = 2;
    /** -1 uses the preset index; 0 means unlimited; positive values are custom FPS caps. */
    static volatile int customFpsLimit = -1;
    static volatile boolean fullscreen = false;
    static volatile boolean showFps = false;
    private static volatile boolean telemetryEnabled = true;
    private static volatile boolean vanillaMode = false;
    /** True once the first-run screen calibration (max resolution + refresh rate) has been applied and saved. */
    private static volatile boolean calibrated = false;
    static volatile boolean uiAnimationsEnabled = true;
    static volatile boolean mouseLookEnabled = true;
    static volatile boolean noSpriteLod = true;
    /** 0=off, 1=FXAA, 2=FXAA quality; applied by the final GPU presentation pass. */
    private static volatile int antiAliasing = 0;
    /** 3D rasterizer: true = OpenGL FBO, layered GPU pipeline (default since alpha56), false = parallel software. */
    private static volatile boolean gpuRaster = true;
    /** Stage 3a: lcdui drawing replayed on the GPU (opt-in; only meaningful together with gpuRaster). gpuUiBroken = it threw once, CPU UI layer until the user toggles it again. */
    private static volatile boolean gpuUiPref = true, gpuUiBroken = false;
    private static final java.util.concurrent.atomic.AtomicInteger mouseLookDeltaX = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger mouseLookDeltaY = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger cameraZoomDelta = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean mouseLookActiveRequested;
    private static volatile boolean controllerConnected;
    private static volatile boolean controllerReadyDown;
    private static volatile float controllerSteer, controllerThrottle, controllerBrake;
    private static volatile float controllerCameraX, controllerCameraY;
    private static volatile boolean controllerThrottleActive;
    public static volatile boolean headless = Boolean.getBoolean("rally3d.headless") || GraphicsEnvironment.isHeadless();
    public static volatile FrameListener frameListener;
    static GameWindow window;
    private static final List<WeakReference<GameCanvas>> canvases = new ArrayList<WeakReference<GameCanvas>>();
    private static final File CONFIG = new File(System.getProperty("user.home"), ".rally3d/settings.properties");
    private static final String[] PERF_STAGES = {"mesh preparation", "GPU world pass", "software raster", "billboard compose", "frame snapshot", "GPU compose+present", "race simulation", "frame pacing", "frame CPU rendering", "GPU time world", "GPU time compose", "GL lock+make-current", "GL caps refresh", "GL unlock+release", "GPU UI replay", "GPU time UI"};
    private static final long[] perfNanos = new long[PERF_STAGES.length];
    private static final long[] perfCalls = new long[PERF_STAGES.length];
    private static long perfWindowNanos = System.nanoTime();

    // ---- configuration ----
    public static void load() {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG)) { p.load(in); }
        catch (java.io.FileNotFoundException e) { calibrateFirstRun(); return; }
        catch (Exception e) { LogConsole.warning("settings", "Could not read settings " + CONFIG, e); return; }
        String wh = p.getProperty("resolutionWH");   // alpha56+: "WxH" (the preset table grew, indices are not stable)
        if (wh != null && wh.matches("\\d+x\\d+")) {
            String[] t = wh.split("x");
            resIndex = findResolution(Integer.parseInt(t[0]), Integer.parseInt(t[1]), resIndex);
        } else {
            int savedResolution = parse(p.getProperty("resolution"), 7);
            if (parse(p.getProperty("resolutionVersion"), 1) < 2 && savedResolution >= 3 && savedResolution <= 7) savedResolution++;
            savedResolution = clamp(savedResolution, 0, LEGACY_RESOLUTIONS.length - 1);
            resIndex = findResolution(LEGACY_RESOLUTIONS[savedResolution][0], LEGACY_RESOLUTIONS[savedResolution][1], resIndex);
        }
        vsync = Boolean.parseBoolean(p.getProperty("vsync", "true"));
        fpsIndex = clamp(parse(p.getProperty("fps"), fpsIndex), 0, FPS_VALUES.length - 1);
        customFpsLimit = parse(p.getProperty("fpsLimit"), -1);
        if (customFpsLimit > 1000) customFpsLimit = 1000;
        if (customFpsLimit < -1) customFpsLimit = -1;
        fullscreen = Boolean.parseBoolean(p.getProperty("fullscreen", "false"));
        showFps = Boolean.parseBoolean(p.getProperty("showFps", "false"));
        telemetryEnabled = Boolean.parseBoolean(p.getProperty("telemetry", "true"));
        vanillaMode = Boolean.parseBoolean(p.getProperty("vanillaMode", "false"));
        uiAnimationsEnabled = Boolean.parseBoolean(p.getProperty("uiAnimations", "true"));
        mouseLookEnabled = Boolean.parseBoolean(p.getProperty("mouseLook", "true"));
        noSpriteLod = Boolean.parseBoolean(p.getProperty("noSpriteLod", "true"));
        antiAliasing = clamp(parse(p.getProperty("antiAliasing"), 0), 0, 2);
        gpuRaster = Boolean.parseBoolean(p.getProperty("gpuRaster", "true"));
        gpuUiPref = Boolean.parseBoolean(p.getProperty("gpuUi", "true"));
        // alpha56: GPU renderer + GPU UI became the default; one-time switch for settings saved by older builds (user can turn them off again).
        boolean migrateGpu = parse(p.getProperty("gpuDefaultsVersion"), 0) < 1;
        if (migrateGpu) { gpuRaster = true; gpuUiPref = true; }
        cpuThreads = clamp(parse(p.getProperty("cpuThreads"), 0), 0, 64);
        calibrated = Boolean.parseBoolean(p.getProperty("calibrated", "false"));
        if (!calibrated) calibrateFirstRun();
        if (migrateGpu) save();
    }

    /**
     * One-time calibration (first launch only; the user's later changes are never overridden):
     * largest resolution preset that fits the screen, and the screen's refresh rate as FPS cap.
     */
    private static void calibrateFirstRun() {
        if (headless) return;   // no screen to measure; try again on the next normal start
        try {
            java.awt.DisplayMode dm = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
            int sw = dm.getWidth(), sh = dm.getHeight();
            int best = -1;
            long bestArea = -1;
            for (int i = 0; i < RESOLUTIONS.length; i++) {
                if (RESOLUTIONS[i][0] > sw || RESOLUTIONS[i][1] > sh) continue;
                long area = (long) RESOLUTIONS[i][0] * RESOLUTIONS[i][1];
                if (area > bestArea) { bestArea = area; best = i; }
            }
            if (best < 0) best = 0;
            resIndex = best;
            int hz = dm.getRefreshRate();
            if (hz != java.awt.DisplayMode.REFRESH_RATE_UNKNOWN && hz > 0) {
                int preset = -1;
                for (int i = 0; i < FPS_VALUES.length; i++) if (Math.abs(FPS_VALUES[i] - hz) <= 1) preset = i;
                if (preset >= 0) { fpsIndex = preset; customFpsLimit = -1; }
                else { customFpsLimit = Math.min(1000, hz); }
            }
            LogConsole.info("settings", "First-run calibration: screen " + sw + "x" + sh + " @" + hz + "Hz -> render "
                    + RESOLUTIONS[resIndex][0] + "x" + RESOLUTIONS[resIndex][1] + ", fps cap " + currentFpsLimit());
        } catch (Throwable t) {
            LogConsole.warning("settings", "First-run calibration failed, keeping defaults", t);
        }
        calibrated = true;
        save();
    }

    static void runGamepadSupport() { GamepadSupport.runOnCurrentThread(); }
    static void updateGamepad(boolean connected, float steer, float throttle, float brake,
                              float cameraX, float cameraY, boolean throttleActive, boolean readyDown) {
        boolean wasConnected = controllerConnected;
        controllerSteer = steer;
        controllerThrottle = throttle;
        controllerBrake = brake;
        controllerCameraX = cameraX;
        controllerCameraY = cameraY;
        controllerThrottleActive = throttleActive;
        controllerReadyDown = readyDown;
        controllerConnected = connected;
        if (connected && !wasConnected) LogConsole.info("gamepad", "Controller connected; analog steering and trigger controls enabled");
        else if (!connected && wasConnected) LogConsole.info("gamepad", "Controller disconnected");
    }
    public static float controllerSteer() { return controllerSteer; }
    public static float controllerThrottle() { return controllerThrottle; }
    public static float controllerBrake() { return controllerBrake; }
    public static float controllerCameraX() { return controllerCameraX; }
    public static float controllerCameraY() { return controllerCameraY; }
    public static boolean controllerThrottleActive() { return controllerThrottleActive; }
    public static boolean controllerReadyDown() { return controllerReadyDown; }
    public static boolean isControllerConnected() { return controllerConnected; }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("resolutionWH", RESOLUTIONS[resIndex][0] + "x" + RESOLUTIONS[resIndex][1]);
        p.setProperty("vsync", String.valueOf(vsync));
        p.setProperty("fps", String.valueOf(fpsIndex));
        if (customFpsLimit >= 0) p.setProperty("fpsLimit", String.valueOf(customFpsLimit));
        p.setProperty("fullscreen", String.valueOf(fullscreen));
        p.setProperty("showFps", String.valueOf(showFps));
        p.setProperty("telemetry", String.valueOf(telemetryEnabled));
        p.setProperty("vanillaMode", String.valueOf(vanillaMode));
        p.setProperty("calibrated", String.valueOf(calibrated));
        p.setProperty("uiAnimations", String.valueOf(uiAnimationsEnabled));
        p.setProperty("mouseLook", String.valueOf(mouseLookEnabled));
        p.setProperty("noSpriteLod", String.valueOf(noSpriteLod));
        p.setProperty("antiAliasing", String.valueOf(antiAliasing));
        p.setProperty("gpuRaster", String.valueOf(gpuRaster));
        p.setProperty("gpuUi", String.valueOf(gpuUiPref));
        p.setProperty("gpuDefaultsVersion", "1");
        p.setProperty("cpuThreads", String.valueOf(cpuThreads));
        try {
            CONFIG.getParentFile().mkdirs();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); p.store(bo, "Rally 3D desktop settings"); rally3d.core.SafeFile.write(CONFIG, bo.toByteArray());   // crash-safe (README: SAFE SAVING)
        } catch (Exception e) { LogConsole.warning("settings", "Could not save settings " + CONFIG, e); }
    }

    private static int parse(String s, int def) { if (s == null) return def; try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }
    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public static void configure(int res, int fpsIdx, boolean fs, boolean showFpsOverlay) {
        resIndex = clamp(res, 0, RESOLUTIONS.length - 1);
        adaptiveSampleStart = System.nanoTime();
        adaptiveSampleFrames = 0;
        adaptiveSlowSamples = adaptiveFastSamples = 0;
        int nextFpsIndex = clamp(fpsIdx, 0, FPS_VALUES.length - 1);
        if (nextFpsIndex != fpsIndex) customFpsLimit = -1;
        fpsIndex = nextFpsIndex;
        fullscreen = fs;
        showFps = showFpsOverlay;
    }

    // ---- geometry ----
    /** The selected display/window mode remains independent from the internal render buffer. */
    public static int displayWidth() { return RESOLUTIONS[resIndex][0]; }
    public static int displayHeight() { return RESOLUTIONS[resIndex][1]; }
    /** alpha56: the internal render buffer is exactly the selected resolution (no 1080p cap, no dynamic downscale). */
    public static int renderHeight() { return displayHeight(); }
    public static int renderWidth() {
        return Math.max(1, (int)Math.round(displayWidth() * renderHeight() / (double)displayHeight()));
    }
    public static double scale() { return renderHeight() / (double) LOGICAL_HEIGHT; }
    public static int logicalHeight() { return LOGICAL_HEIGHT; }
    public static int logicalWidth() { return (int) Math.round(LOGICAL_HEIGHT * (double) renderWidth() / renderHeight()); }

    // ---- frame pacing (read by the game loop) ----
    /** Target frame time in nanoseconds; 0 means "do not wait" (unlimited FPS). */
    public static long targetFrameNanos() {
        int f = currentFpsLimit();
        return f <= 0 ? 0L : 1000000000L / f;
    }
    public static int currentFpsLimit() { return customFpsLimit >= 0 ? customFpsLimit : FPS_VALUES[fpsIndex]; }
    public static void setFpsLimit(int limit) {
        if (limit != 0) limit = clamp(limit, 30, 1000);   // 0 = unlimited (FPS_VALUES last entry), not 30
        for (int i = 0; i < FPS_VALUES.length; i++) {
            if (FPS_VALUES[i] == limit) {
                fpsIndex = i;
                customFpsLimit = -1;
                save();
                return;
            }
        }
        customFpsLimit = limit;
        save();
    }
    /** True while a finished multiplayer racer watches the others. */
    public static volatile boolean spectating = false;
    /** True while a multiplayer race loop runs (Android: the pause button pauses on the first tap instead of asking for a second tap to exit). */
    public static volatile boolean mpRace = false;
    /** Set when the app goes to the background during a multiplayer race: the game pauses for everybody. */
    public static volatile boolean mpPauseRequest = false;
    /** Touch device: hints show the on-screen arrows instead of A / D. */
    public static boolean isTouchDevice() { return false; }
    /** Watching others: orbit camera works even if "Mouse camera" is off (not in Vanilla mode). */
    public static boolean isSpectateLookEnabled() { return spectating && !vanillaMode; }
    public static boolean isShowFps() { return showFps; }
    public static boolean isTelemetryEnabled() { return telemetryEnabled && !vanillaMode; }
    public static void setTelemetryEnabled(boolean enabled) { telemetryEnabled = enabled; save(); }
    public static boolean isVanillaMode() { return vanillaMode; }
    public static void setVanillaMode(boolean enabled) { vanillaMode = enabled; save(); }

    public static byte[] loadGhost(int trackIndex) {
        if (trackIndex < 0 || trackIndex >= 12) return null;
        File file = new File(CONFIG.getParentFile(), "ghosts/track-" + trackIndex + ".rpl");
        if (!file.isFile()) return null;
        try { return Files.readAllBytes(file.toPath()); }
        catch (java.io.FileNotFoundException e) { return null; }
        catch (Exception e) { LogConsole.warning("ghost", "Could not load ghost for track " + trackIndex, e); return null; }
    }

    public static boolean saveGhost(int trackIndex, byte[] data) {
        if (trackIndex < 0 || trackIndex >= 12 || data == null || data.length == 0) return false;
        File directory = new File(CONFIG.getParentFile(), "ghosts");
        File target = new File(directory, "track-" + trackIndex + ".rpl");
        File temporary = new File(directory, "track-" + trackIndex + ".rpl.tmp");
        try {
            if (!directory.mkdirs() && !directory.isDirectory()) return false;
            try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(data); }
            try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception e) {
            LogConsole.warning("ghost", "Could not save ghost for track " + trackIndex, e);
            return false;
        }
    }

    /** Ghost of the best run for this exact track+car. Falls back to the old one-per-track file (caller checks its carIndex). */
    public static byte[] loadGhost(int trackIndex, int carIndex) {
        if (trackIndex < 0 || trackIndex >= 12 || carIndex < 0 || carIndex > 3) return null;
        File file = new File(CONFIG.getParentFile(), "ghosts/track-" + trackIndex + "-car-" + carIndex + ".rpl");
        if (!file.isFile()) return loadGhost(trackIndex);
        try { return Files.readAllBytes(file.toPath()); }
        catch (java.io.FileNotFoundException e) { return null; }
        catch (Exception e) { LogConsole.warning("ghost", "Could not load ghost for track " + trackIndex + " car " + carIndex, e); return null; }
    }

    public static boolean saveGhost(int trackIndex, int carIndex, byte[] data) {
        if (trackIndex < 0 || trackIndex >= 12 || carIndex < 0 || carIndex > 3 || data == null || data.length == 0) return false;
        File directory = new File(CONFIG.getParentFile(), "ghosts");
        File target = new File(directory, "track-" + trackIndex + "-car-" + carIndex + ".rpl");
        File temporary = new File(directory, "track-" + trackIndex + "-car-" + carIndex + ".rpl.tmp");
        try {
            if (!directory.mkdirs() && !directory.isDirectory()) return false;
            try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(data); }
            try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (Exception e) {
            LogConsole.warning("ghost", "Could not save ghost for track " + trackIndex + " car " + carIndex, e);
            return false;
        }
    }
    /**
     * Folder with the add-ons (sub-folders maps / cars / wheels are created by AddonManager.ensureDirs on every start).
     * Windows: %APPDATA%\\Rally3D\\addons (Roaming), so no zips pile up next to the game. Other systems: ~/.rally3d/addons.
     */
    public static File addonsDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            File base = appData != null && appData.trim().length() > 0 ? new File(appData) : new File(System.getProperty("user.home"), "AppData\\Roaming");
            return new File(new File(base, "Rally3D"), "addons");
        }
        return legacyAddonsDir();
    }

    /** Where older builds kept the add-on zips (~/.rally3d/addons). On Windows its content is moved into addonsDir() once. */
    public static File legacyAddonsDir() { return new File(CONFIG.getParentFile(), "addons"); }

    /** Shows a file picker (blocks the game thread until it closes) and copies the chosen zips into the add-ons folder. Returns how many were added. */
    public static int importAddons() {
        if (GraphicsEnvironment.isHeadless()) return 0;
        final int[] count = {0};
        try {
            java.awt.EventQueue.invokeAndWait(new Runnable() { public void run() {
                File[] chosen;
                if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                    // Windows: the native Explorer open dialog (AWT FileDialog), not the Swing one
                    java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame)null, "Rally3D - choose add-on zip file(s)", java.awt.FileDialog.LOAD);
                    fd.setMultipleMode(true);
                    fd.setFile("*.zip");
                    File dl = new File(System.getProperty("user.home"), "Downloads");
                    if (dl.isDirectory()) fd.setDirectory(dl.getAbsolutePath());
                    fd.setAlwaysOnTop(true);
                    fd.setVisible(true);
                    chosen = fd.getFiles();
                    fd.dispose();
                } else {
                    javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
                    fc.setDialogTitle("Rally3D - choose add-on zip file(s)");
                    fc.setMultiSelectionEnabled(true);
                    fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Add-on zip (*.zip)", "zip"));
                    if (fc.showOpenDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return;
                    chosen = fc.getSelectedFiles();
                }
                if (chosen == null || chosen.length == 0) return;
                File dir = addonsDir();
                dir.mkdirs();
                for (File f : chosen) {
                    try {
                        String name = f.getName().replaceAll("[^\\p{L}\\p{N}._ -]", "_");
                        if (!name.toLowerCase().endsWith(".zip")) name += ".zip";
                        File target = new File(dir, name);
                        for (int i = 2; target.exists(); i++) target = new File(dir, name.replaceAll("(?i)\\.zip$", "") + "-" + i + ".zip");
                        Files.copy(f.toPath(), target.toPath());
                        count[0]++;
                    } catch (Exception e) { LogConsole.warning("addon", "Could not import " + f, e); }
                }
            } });
        } catch (Exception e) { LogConsole.warning("addon", "Add-on file picker failed", e); }
        return count[0];
    }

    /** Safe file name for an export (letters, digits, dot, dash, underscore, space). */
    public static String safeFileName(String name, String ext) {
        String n = name == null ? "" : name.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (n.length() == 0) n = "map";
        if (n.length() > 60) n = n.substring(0, 60);
        if (!n.toLowerCase().endsWith(ext)) n += ext;
        return n;
    }

    /**
     * Save dialog (blocks the game thread until it closes) and writes {@code data} to the chosen file.
     * Returns the file name that was written, or null when the player cancelled / the write failed.
     * Without a display (headless) the file goes to the add-ons "export" folder.
     */
    public static String exportFile(final String suggestedName, final byte[] data) {
        final String fname = safeFileName(suggestedName, ".zip");
        final String[] result = {null};
        if (data == null) return null;
        if (GraphicsEnvironment.isHeadless()) {
            try {
                File dir = new File(addonsDir(), "export");
                dir.mkdirs();
                File t = new File(dir, fname);
                Files.write(t.toPath(), data);
                return t.getName();
            } catch (Exception e) { LogConsole.warning("addon", "Export failed", e); return null; }
        }
        try {
            java.awt.EventQueue.invokeAndWait(new Runnable() { public void run() {
                File target = null;
                if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                    java.awt.FileDialog fd = new java.awt.FileDialog((java.awt.Frame) null, "Rally3D - export map zip", java.awt.FileDialog.SAVE);
                    fd.setFile(fname);
                    File dl = new File(System.getProperty("user.home"), "Downloads");
                    if (dl.isDirectory()) fd.setDirectory(dl.getAbsolutePath());
                    fd.setAlwaysOnTop(true);
                    fd.setVisible(true);
                    if (fd.getFile() != null) target = new File(fd.getDirectory(), fd.getFile());
                    fd.dispose();
                } else {
                    javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
                    fc.setDialogTitle("Rally3D - export map zip");
                    fc.setSelectedFile(new File(fname));
                    if (fc.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) target = fc.getSelectedFile();
                }
                if (target == null) return;
                if (!target.getName().toLowerCase().endsWith(".zip")) target = new File(target.getParentFile(), target.getName() + ".zip");
                try { Files.write(target.toPath(), data); result[0] = target.getName(); }
                catch (Exception e) { LogConsole.warning("addon", "Could not write " + target, e); }
            } });
        } catch (Exception e) { LogConsole.warning("addon", "Export dialog failed", e); }
        return result[0];
    }

    public static boolean areUiAnimationsEnabled() { return uiAnimationsEnabled; }
    public static void setUiAnimationsEnabled(boolean enabled) { uiAnimationsEnabled = enabled; save(); }
    public static boolean isMouseLookEnabled() { return mouseLookEnabled && !vanillaMode; }
    public static void setMouseLookEnabled(boolean enabled) { mouseLookEnabled = enabled; save(); }
    public static boolean isNoSpriteLod() { return noSpriteLod && !vanillaMode; }
    public static void setNoSpriteLod(boolean enabled) { noSpriteLod = enabled; save(); }
    /** V-sync (WGL swap interval 1) for the GL presentation. Default on. */
    private static volatile boolean vsync = true;
    public static boolean dynamicResolution() { return false; }
    public static void setDynamicResolution(boolean on) { }
    public static boolean vsync() { return vsync; }
    public static void setVsync(boolean on) { vsync = on; save(); }
    public static int antiAliasing() { return antiAliasing; }
    public static boolean gpuRaster() { return gpuRaster; }
    /** True while lcdui draw calls are recorded and replayed on the GPU (needs the layered GPU pipeline). */
    public static boolean gpuUi() { return gpuUiPref && !gpuUiBroken && gpuLayered(); }
    public static boolean gpuUiSetting() { return gpuUiPref; }
    public static void setGpuUi(boolean on) {
        gpuUiPref = on; gpuUiBroken = false;
        save();
    }
    static void failGpuUi(Throwable error) {
        if (gpuUiBroken) return;
        gpuUiBroken = true;
        rally3d.core.RLog.e("gpu", "GPU UI failed, falling back to the CPU UI layer (re-enable in Settings -> Display / FPS (PC))", error);
    }
    public static void setGpuRaster(boolean on) {
        if (gpuRaster == on) return;
        gpuRaster = on;
        save();
        rebuildGraphicsBuffers();   // ARGB UI layer (GPU) <-> opaque RGB frame (software)
    }

    /** Re-creates every canvas buffer (same size) so its pixel format matches the current renderer. Game thread. */
    static void rebuildGraphicsBuffers() {
        List<GameCanvas> list = new ArrayList<GameCanvas>();
        synchronized (canvases) {
            for (WeakReference<GameCanvas> r : canvases) { GameCanvas c = r.get(); if (c != null) list.add(c); }
        }
        for (GameCanvas c : list) c.rebuildBuffer();
        javax.microedition.m3g.Graphics3D.getInstance().dropHeldWorld();
    }
    // ---- CPU multithreading ----
    /** 0 = auto (all logical cores), otherwise the worker count. Applied on the next start of the pool (restart the game to resize). */
    private static volatile int cpuThreads = 0;
    private static volatile java.util.concurrent.ForkJoinPool workerPool;
    public static int cpuThreadsSetting() { return cpuThreads; }
    public static int cpuThreadsEffective() { int c = Runtime.getRuntime().availableProcessors(); return cpuThreads <= 0 ? c : Math.min(cpuThreads, 64); }
    public static void setCpuThreads(int n) { cpuThreads = clamp(n, 0, 64); save(); rebuildWorkerPool(); }
    /** Next value in the settings cycle: Auto -> 1 -> 2 -> 4 -> ... -> all cores -> Auto. */
    public static int nextCpuThreads() {
        int all = Runtime.getRuntime().availableProcessors(), cur = cpuThreads;
        if (cur <= 0) return 1;
        int next = cur < 2 ? 2 : cur * 2;
        return next >= all ? 0 : next;
    }
    private static synchronized void rebuildWorkerPool() {
        java.util.concurrent.ForkJoinPool old = workerPool;
        workerPool = null;
        if (old != null) old.shutdown();
    }
    private static java.util.concurrent.ForkJoinPool pool() {
        java.util.concurrent.ForkJoinPool p = workerPool;
        if (p != null) return p;
        synchronized (Platform.class) {
            if (workerPool == null) {
                final java.util.concurrent.atomic.AtomicInteger id = new java.util.concurrent.atomic.AtomicInteger();
                int n = cpuThreadsEffective();
                workerPool = new java.util.concurrent.ForkJoinPool(n, pl -> {
                    java.util.concurrent.ForkJoinWorkerThread t = java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pl);
                    t.setName("rally-worker-" + id.incrementAndGet()); t.setDaemon(true); return t;
                }, null, false);
                LogConsole.info("cpu", "Worker pool: " + n + " threads (" + Runtime.getRuntime().availableProcessors() + " logical cores)");
            }
            return workerPool;
        }
    }
    /** Worker count the renderer should split work for (caller thread helps, so +0). */
    public static int workers() { return cpuThreadsEffective(); }
    /** Runs body(0..n-1) on the worker pool and waits. n<=1 or 1 thread: runs inline. Exceptions propagate. */
    public static void parallelFor(int n, java.util.function.IntConsumer body) {
        if (n <= 0) return;
        if (n == 1 || cpuThreadsEffective() <= 1) { for (int i = 0; i < n; i++) body.accept(i); return; }
        java.util.concurrent.ForkJoinPool p = pool();
        if (java.util.concurrent.ForkJoinTask.inForkJoinPool()) { java.util.stream.IntStream.range(0, n).parallel().forEach(body); return; }
        p.invoke(java.util.concurrent.ForkJoinTask.adapt(() -> java.util.stream.IntStream.range(0, n).parallel().forEach(body)));
    }
    public static void setAntiAliasing(int mode) { antiAliasing = clamp(mode, 0, 2); save(); }
    public static void setMouseLookActive(boolean active) {
        boolean desired = active && (mouseLookEnabled || spectating) && !vanillaMode;
        GameWindow w = window;
        if (w != null) w.setGameplayActive(active);
        if (mouseLookActiveRequested == desired) return;
        mouseLookActiveRequested = desired;
        mouseLookDeltaX.set(0);
        if (w != null) w.setMouseLookActive(desired);
    }
    public static void addMouseLookDelta(int dx, int dy) {
        if (dx != 0) mouseLookDeltaX.addAndGet(dx);
        if (dy != 0) mouseLookDeltaY.addAndGet(dy);
    }
    public static int consumeMouseLookDeltaX() { return mouseLookDeltaX.getAndSet(0); }
    public static int consumeMouseLookDeltaY() { return mouseLookDeltaY.getAndSet(0); }
    public static void addCameraZoomDelta(double rotations) {
        int delta = (int)Math.round(rotations * 1000.0);
        if (delta != 0) cameraZoomDelta.addAndGet(delta);
    }
    public static boolean consumeCameraReset() { return false; }
    public static boolean fullTraceSupported() { return false; }
    public static boolean supportsTouchLayout() { return false; }
    public static void showButtonScaleDialog() { }
    public static void showButtonLayoutEditor() { }
    public static boolean isFullTrace() { return false; }
    public static void setFullTrace(boolean on) {}
    public static float consumeCameraZoomDelta() { return cameraZoomDelta.getAndSet(0) / 1000.0f; }
    public static int resolutionIndex() { return resIndex; }
    public static int fpsIndex() { return fpsIndex; }
    public static boolean isFullscreen() { return fullscreen; }

    /** Low-overhead rolling timings, reported to the F1 log once per 10 seconds. */
    public static boolean supportsVibration() { return true; }
    public static boolean isBackground() { return false; }
    public static synchronized void recordPerformance(int stage, long elapsedNanos) {
        if (stage < 0 || stage >= PERF_STAGES.length) return;
        perfNanos[stage] += Math.max(0L, elapsedNanos);
        perfCalls[stage]++;
        long now = System.nanoTime();
        if (now - perfWindowNanos < 10_000_000_000L) return;
        long elapsedWindow = Math.max(1L, now - perfWindowNanos);
        long renderedFrames = Math.max(1L, perfCalls[4]);
        StringBuilder report = new StringBuilder("10s frame profile @ ")
                .append(renderWidth()).append('x').append(renderHeight())
                .append(" internal / ").append(displayWidth()).append('x').append(displayHeight()).append(" output")
                .append(" | cap=").append(currentFpsLimit()).append(" FPS")
                .append(" | actual=").append(String.format(java.util.Locale.ROOT, "%.1f FPS", perfCalls[4] * 1_000_000_000.0 / elapsedWindow));
        for (int i = 0; i < PERF_STAGES.length; i++) {
            if (perfCalls[i] == 0) continue;
            report.append(" | ").append(PERF_STAGES[i]).append('=')
                    .append(String.format(java.util.Locale.ROOT, "%.2fms/frame", perfNanos[i]
                            / (i == 5 || i == 9 || i == 10 ? perfCalls[i] : renderedFrames) / 1_000_000.0));
        }
        LogConsole.info("perf", report.toString());
        java.util.Arrays.fill(perfNanos, 0L);
        java.util.Arrays.fill(perfCalls, 0L);
        perfWindowNanos = now;
    }

    // ---- canvases / graphics ----
    public static void register(GameCanvas c) { synchronized (canvases) { canvases.add(new WeakReference<GameCanvas>(c)); } }
    public static Graphics newGraphics() { Graphics g = GraphicsFactory.create(renderWidth(), renderHeight(), scale(), gpuLayered()); javax.microedition.lcdui.GraphicsBridge.setRecording(g, gpuUi()); return g; }
    public static void resizeGraphics(Graphics g) { GraphicsFactory.resize(g, renderWidth(), renderHeight(), scale(), gpuLayered()); javax.microedition.lcdui.GraphicsBridge.setRecording(g, gpuUi()); }

    /** True while frames go through the GPU layered pipeline (3D world in an FBO + transparent CPU UI layer, composited on the GPU, no readback). */
    public static boolean gpuLayered() {
        GameWindow w = window;
        return gpuRaster && !headless && w != null && w.gpuUsable();
    }

    private static volatile Canvas current;
    public static Canvas currentCanvas() { return current; }

    public static volatile Canvas currentCanvas;

    public static void showCanvas(Canvas c) {
        currentCanvas = c;
        current = c;
        if (window != null) window.setCanvas(c);
    }

    public static void present(Graphics g) {
        long started = System.nanoTime();
        FrameListener fl = frameListener;
        if (fl != null && !javax.microedition.lcdui.GraphicsBridge.isRecording(g)) fl.onFrame(g.pixelBuffer(), g.physicalWidth(), g.physicalHeight());
        if (window != null) window.present(g);
        else recordPerformance(4, System.nanoTime() - started);
        // The GPU renderer may have failed (-> software) or been toggled: keep the buffer format in step (ARGB UI layer vs opaque RGB frame).
        if (g.hasAlpha() != gpuLayered()) resizeGraphics(g);
        else if (javax.microedition.lcdui.GraphicsBridge.isRecording(g) != gpuUi()) javax.microedition.lcdui.GraphicsBridge.setRecording(g, gpuUi());   // GPU UI toggled / failed
        adaptRenderResolution(g);
    }

    private static int refreshLockWindows;
    private static boolean refreshLockLogged;
    private static int displayRefreshHz = -1;
    /** alpha54: FPS pinned to the monitor refresh with "Unlimited" = a driver-side V-sync override (found in the alpha53 log: NVIDIA Control Panel "Vertical sync" was forcing On). Logged once. */
    private static void checkRefreshLock(double actualFps, int configuredCap) {
        if (refreshLockLogged || configuredCap > 0 || actualFps < 30) { refreshLockWindows = 0; return; }
        if (displayRefreshHz < 0) {
            displayRefreshHz = 0;
            try { int hz = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getRefreshRate(); if (hz > 0) displayRefreshHz = hz; } catch (Throwable ignored) { /* headless / no device: no check */ }
        }
        if (displayRefreshHz <= 0) return;
        if (Math.abs(actualFps - displayRefreshHz) <= 1.0) {
            if (++refreshLockWindows >= 4) {
                refreshLockLogged = true;
                rally3d.core.RLog.w("render", "FPS is pinned to the display refresh (" + displayRefreshHz + " Hz) although Frame limit is Unlimited: V-sync is forced outside the game (NVIDIA Control Panel -> Manage 3D settings -> Vertical sync = Off for java.exe, or AMD Radeon 'Wait for vertical refresh' = Off)");
            }
        } else refreshLockWindows = 0;
    }

    private static synchronized void adaptRenderResolution(Graphics g) {
        adaptiveSampleFrames++;
        long now = System.nanoTime();
        long elapsed = now - adaptiveSampleStart;
        if (elapsed < 2_500_000_000L) return;
        double actualFps = adaptiveSampleFrames * 1_000_000_000.0 / Math.max(1L, elapsed);
        if (!vsync) checkRefreshLock(actualFps, currentFpsLimit());   // with V-sync on, FPS == refresh is expected
        adaptiveSampleFrames = 0;
        adaptiveSampleStart = now;
    }

    /** Executes a GL task with the desktop context current; false selects the software renderer. */
    public static boolean runGpu(Runnable task) {
        GameWindow w = window;
        return gpuRaster && !headless && w != null && w.runGpu(task);
    }

    /** Re-allocates all game buffers and re-lays-out the window. Call from the game thread. */
    public static void applyResolution() {
        List<GameCanvas> list = new ArrayList<GameCanvas>();
        synchronized (canvases) {
            for (WeakReference<GameCanvas> r : canvases) { GameCanvas c = r.get(); if (c != null) list.add(c); }
        }
        for (GameCanvas c : list) c.rebuildBuffer();
        if (window != null) window.applyMode();
    }

    /** Shows the settings dialog (blocks the calling game thread). */
    public static void showSettingsDialog() {
        if (window == null) return;
        final boolean[] changed = new boolean[1];
        try {
            javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                public void run() { changed[0] = SettingsDialog.show(window); }
            });
        } catch (Exception e) { LogConsole.warning("settings", "Could not open settings window", e); return; }
        if (changed[0]) { save(); applyResolution(); }
    }

    /** Non-blocking text input; the callback receives the entered text or null if cancelled. */
    public static void inputDialog(final String title, final String initial, final int maxLen, final Consumer<String> callback) {
        if (window == null) { callback.accept(initial); return; }
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    window.showInputOverlay(title, initial, maxLen, callback);
                } catch (RuntimeException error) {
                    LogConsole.error("input", "Could not open the in-game input field", error);
                    callback.accept(initial);
                }
            }
        });
    }

    public static String manifestProperty(String key) { return null; }

    public static void exit() {
        save();
        System.exit(0);
    }
}
