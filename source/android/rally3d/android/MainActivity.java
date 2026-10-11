package rally3d.android;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import javax.microedition.lcdui.Canvas;
import rally3d.desktop.Platform;

public class MainActivity extends Activity {
    private GameView view;
    private android.net.wifi.WifiManager.MulticastLock multicastLock;

    @Override protected void onCreate(Bundle b) { /*T*/ final long $t = rally3d.core.Trace.in(204); try {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);  // keyboard overlays, surface never resizes
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        javax.microedition.media.Manager.reopen();
        Platform.init(this);
        Platform.activity = this;
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                android.view.Display d = getWindowManager().getDefaultDisplay();
                android.view.Display.Mode cur = d.getMode(), best = cur;
                for (android.view.Display.Mode m : d.getSupportedModes()) {
                    if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight()
                            && m.getRefreshRate() > best.getRefreshRate()) best = m;
                }
                WindowManager.LayoutParams lp2 = getWindow().getAttributes();
                lp2.preferredDisplayModeId = best.getModeId();
                getWindow().setAttributes(lp2);
            } catch (Throwable ignored) { rally3d.core.RLog.caught("MainActivity.refreshMode", ignored); }
        }
        try {   // without this many phones drop UDP broadcast/multicast packets, so LAN host discovery sees nothing
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) { multicastLock = wm.createMulticastLock("rally3d-lan"); multicastLock.setReferenceCounted(false); multicastLock.acquire(); }
        } catch (Throwable ignored) { rally3d.core.RLog.caught("MainActivity.multicastLock", ignored); }
        AndroidLog.install(this);
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) { /*T*/ final long $t = rally3d.core.Trace.in(210); try {
                try { Watchdog.crashReport(t, e); } catch (Throwable ignored) { rally3d.core.RLog.caught("MainActivity.uncaughtException", ignored);}
                if (prev != null) prev.uncaughtException(t, e);
             } finally { rally3d.core.Trace.out(210, $t); } }
        });
        Backup.migrateLegacy(this, Platform.dataDir());   // old private-dir saves -> Android/data/<pkg>/files, before the game reads them
        logEnvironment();
        Watchdog.sessionStart(this);
        Watchdog.start(this);
        startGame();
     } finally { rally3d.core.Trace.out(204, $t); } }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) { /*T*/ final long $t = rally3d.core.Trace.in(294); try {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Platform.ADDON_PICK_REQUEST) Platform.onAddonPicked(resultCode == RESULT_OK ? data : null);
        else if (requestCode == Platform.ADDON_EXPORT_REQUEST) Platform.onAddonExported(resultCode == RESULT_OK ? data : null);
     } finally { rally3d.core.Trace.out(294, $t); } }

    private void startGame() { /*T*/ final long $t = rally3d.core.Trace.in(623); try {
        if (view != null) return;
        view = new GameView(this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(view.overlayView(), new android.widget.FrameLayout.LayoutParams(-1, -1));   // touch buttons while the GPU renderer owns the surface
        setContentView(root);
        immersive();
     } finally { rally3d.core.Trace.out(623, $t); } }

    private void immersive() { /*T*/ final long $t = rally3d.core.Trace.in(205); try {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
     } finally { rally3d.core.Trace.out(205, $t); } }

    private void logEnvironment() { /*T*/ final long $t = rally3d.core.Trace.in(211); try {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            float hz = Build.VERSION.SDK_INT >= 30 && getDisplay() != null ? getDisplay().getRefreshRate() : 0f;
            if (!rally3d.core.Version.NAME.equals(pi.versionName) || rally3d.core.Version.CODE != pi.versionCode)
                rally3d.core.RLog.e("env", "VERSION MISMATCH: Version.java says " + rally3d.core.Version.NAME + " (" + rally3d.core.Version.CODE + "), manifest says " + pi.versionName + " (" + pi.versionCode + ") - fix both together");
            rally3d.core.RLog.i("env", "game version " + pi.versionName + " (code " + pi.versionCode + "), package " + getPackageName()
                    + ", abi " + java.util.Arrays.toString(Build.SUPPORTED_ABIS) + ", cores " + Runtime.getRuntime().availableProcessors());
            rally3d.core.RLog.i("env", "device " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + "), Android " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT
                    + ", screen " + dm.widthPixels + "x" + dm.heightPixels + " @" + dm.densityDpi + "dpi " + hz + "Hz, locale " + java.util.Locale.getDefault());
            rally3d.core.RLog.i("env", "ram total " + (mi.totalMem >> 20) + " MB, free " + (mi.availMem >> 20) + " MB, lowMemory=" + mi.lowMemory
                    + ", heap limit " + am.getMemoryClass() + " MB (large " + am.getLargeMemoryClass() + " MB), data dir " + Platform.dataDir());
        } catch (Throwable t) { rally3d.core.RLog.caught("MainActivity.logEnvironment", t); rally3d.core.RLog.w("env", "environment info failed", t); }
     } finally { rally3d.core.Trace.out(211, $t); } }

    @Override public void onTrimMemory(int level) { /*T*/ final long $t = rally3d.core.Trace.in(212); try {
        super.onTrimMemory(level);
        rally3d.core.RLog.w("memory", "onTrimMemory level=" + level + " (" + Watchdog.memory() + ")");
     } finally { rally3d.core.Trace.out(212, $t); } }

    @Override public void onLowMemory() {
        super.onLowMemory();
        rally3d.core.RLog.e("memory", "onLowMemory! (" + Watchdog.memory() + ")");
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration c) { /*T*/ final long $t = rally3d.core.Trace.in(213); try {
        super.onConfigurationChanged(c);
        rally3d.core.RLog.i("lifecycle", "configuration changed: orientation=" + c.orientation + " screen " + c.screenWidthDp + "x" + c.screenHeightDp + "dp");
     } finally { rally3d.core.Trace.out(213, $t); } }

    @Override protected void onStart() { super.onStart(); rally3d.core.RLog.i("lifecycle", "onStart"); }
    @Override protected void onStop() { /*T*/ final long $t = rally3d.core.Trace.in(239); try { super.onStop(); rally3d.core.RLog.i("lifecycle", "onStop"); javax.microedition.media.Manager.pauseAll(); AndroidLog.flushBlocking();  } finally { rally3d.core.Trace.out(239, $t); } }

    @Override public void onWindowFocusChanged(boolean hasFocus) { /*T*/ final long $t = rally3d.core.Trace.in(214); try {
        rally3d.core.RLog.i("lifecycle", "window focus " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && Canvas.rawPointer) { Canvas rc = Platform.currentCanvas(); if (rc != null) rc.dispatchRawKeyReset(); }
        if (hasFocus) immersive();
     } finally { rally3d.core.Trace.out(214, $t); } }

    @Override protected void onPause() { /*T*/ final long $t = rally3d.core.Trace.in(215); try {
        super.onPause();
        Platform.gpuTryEnd();   // a clean pause = the GPU start did not crash the process
        rally3d.core.RLog.i("app", "onPause");
        Watchdog.setForeground(false);
        if (isFinishing()) javax.microedition.media.Manager.closeAll();
        if (view != null) view.onAppPause();
     } finally { rally3d.core.Trace.out(215, $t); } }

    @Override protected void onResume() { /*T*/ final long $t = rally3d.core.Trace.in(216); try {
        super.onResume();
        rally3d.core.RLog.i("app", "onResume");
        Watchdog.setForeground(true);
        if (view != null) view.onAppResume();
     } finally { rally3d.core.Trace.out(216, $t); } }

    @Override protected void onDestroy() { /*T*/ final long $t = rally3d.core.Trace.in(217); try {
        super.onDestroy();
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); } catch (Throwable ignored) { rally3d.core.RLog.caught("MainActivity.onDestroy", ignored); }
        rally3d.core.RLog.i("app", "onDestroy (finishing=" + isFinishing() + ")");
        if (isFinishing()) { javax.microedition.media.Manager.closeAll(); Watchdog.sessionEnd(); }
        Platform.save();
        AndroidLog.flushBlocking();
     } finally { rally3d.core.Trace.out(217, $t); } }

    static int mapKey(int kc) {
        switch (kc) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_W: return -1;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_S: return -2;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_A: return -3;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_D: return -4;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_BUTTON_A: return -5;
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE: case KeyEvent.KEYCODE_BUTTON_B: return -7;
            case KeyEvent.KEYCODE_SPACE: case KeyEvent.KEYCODE_BUTTON_START: return -12;
            case KeyEvent.KEYCODE_TAB: return -13;
            default:
                if (kc >= KeyEvent.KEYCODE_0 && kc <= KeyEvent.KEYCODE_9) return 48 + (kc - KeyEvent.KEYCODE_0);
                return 0;
        }
    }

    /** Editor key codes (same as the desktop build): letters = ASCII upper case, 8 backspace, 10 enter, 27 esc, 37..40 arrows, 127 delete. */
    private static int rawCode(int kc) { /*T*/ final long $t = rally3d.core.Trace.in(504); try {
        if (kc >= KeyEvent.KEYCODE_A && kc <= KeyEvent.KEYCODE_Z) return 'A' + (kc - KeyEvent.KEYCODE_A);
        switch (kc) {
            case KeyEvent.KEYCODE_DEL: return 8;
            case KeyEvent.KEYCODE_FORWARD_DEL: return 127;
            case KeyEvent.KEYCODE_ENTER: return 10;
            case KeyEvent.KEYCODE_ESCAPE: return 27;
            case KeyEvent.KEYCODE_SPACE: return 32;
            case KeyEvent.KEYCODE_SHIFT_LEFT: case KeyEvent.KEYCODE_SHIFT_RIGHT: return 16;
            case KeyEvent.KEYCODE_CTRL_LEFT: case KeyEvent.KEYCODE_CTRL_RIGHT: return 17;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 37;
            case KeyEvent.KEYCODE_DPAD_UP: return 38;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 39;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 40;
            case KeyEvent.KEYCODE_TAB: return 9;
            case KeyEvent.KEYCODE_MOVE_HOME: return 36;
            case KeyEvent.KEYCODE_MOVE_END: return 35;
            case KeyEvent.KEYCODE_PAGE_UP: return 33;
            case KeyEvent.KEYCODE_PAGE_DOWN: return 34;
            case KeyEvent.KEYCODE_PLUS: return 521;
            case KeyEvent.KEYCODE_EQUALS: return 61;
            case KeyEvent.KEYCODE_NUMPAD_ADD: return 107;
            case KeyEvent.KEYCODE_MINUS: return 45;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return 109;
            default:
                if (kc >= KeyEvent.KEYCODE_0 && kc <= KeyEvent.KEYCODE_9) return 48 + (kc - KeyEvent.KEYCODE_0);
                return 0;
        }
     } finally { rally3d.core.Trace.out(504, $t); } }

    @Override public boolean onKeyDown(int kc, KeyEvent e) { /*T*/ final long $t = rally3d.core.Trace.in(206); try {
        if (e.getRepeatCount() == 0) Watchdog.input("key down " + KeyEvent.keyCodeToString(kc));
        Canvas rc = Platform.currentCanvas();
        if (Canvas.rawPointer && rc != null) {   // map editor with a hardware keyboard
            int code = rawCode(kc);
            if (code != 0) {
                rc.dispatchRawKey(code, (e.isCtrlPressed() ? 1 : 0) | (e.isShiftPressed() ? 2 : 0) | (e.isAltPressed() ? 4 : 0), e.getUnicodeChar());
                return true;
            }
        }
        int k = mapKey(kc);
        Canvas c = Platform.currentCanvas();
        if (k != 0 && c != null) { if (e.getRepeatCount() == 0) c.dispatchKey(k, true); return true; }
        return super.onKeyDown(kc, e);
     } finally { rally3d.core.Trace.out(206, $t); } }

    @Override public boolean onKeyUp(int kc, KeyEvent e) { /*T*/ final long $t = rally3d.core.Trace.in(207); try {
        Watchdog.input("key up " + KeyEvent.keyCodeToString(kc));
        Canvas rc = Platform.currentCanvas();
        if (Canvas.rawPointer && rc != null) {   // map editor / fly view: key release for held keys
            int code = rawCode(kc);
            if (code != 0) { rc.dispatchRawKeyRelease(code, (e.isCtrlPressed() ? 1 : 0) | (e.isShiftPressed() ? 2 : 0) | (e.isAltPressed() ? 4 : 0)); return true; }
        }
        int k = mapKey(kc);
        Canvas c = Platform.currentCanvas();
        if (k != 0 && c != null) { c.dispatchKey(k, false); return true; }
        return super.onKeyUp(kc, e);
     } finally { rally3d.core.Trace.out(207, $t); } }
}
