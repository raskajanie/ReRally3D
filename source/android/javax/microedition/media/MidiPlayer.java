package javax.microedition.media;

import android.media.MediaPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.media.control.VolumeControl;

/** MIDI via Android MediaPlayer (sonivox). */
final class MidiPlayer implements Player {
    private static final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.List<java.lang.ref.WeakReference<MidiPlayer>> ALL = new java.util.ArrayList<java.lang.ref.WeakReference<MidiPlayer>>();
    private boolean pausedByApp;
    /** True while the app is in the background: players that get started now (music thread) start on resume instead. */
    private static volatile boolean appPaused;
    /** True after closeAll(): nothing may start any more (the music thread keeps running until the process dies). */
    private static volatile boolean shutdown;
    /** New activity in a reused process: allow playback again. */
    static void reopen() { shutdown = false; appPaused = false; }
    static synchronized void closeAll() { /*T*/ final long $t = rally3d.core.Trace.in(238); try {
        shutdown = true;
        for (java.lang.ref.WeakReference<MidiPlayer> r : ALL) { MidiPlayer m = r.get(); if (m != null) m.close(); }
     } finally { rally3d.core.Trace.out(238, $t); } }
    static synchronized void pauseAll() { /*T*/ final long $t = rally3d.core.Trace.in(154); try {
        appPaused = true;
        for (java.lang.ref.WeakReference<MidiPlayer> r : ALL) { MidiPlayer m = r.get(); if (m != null) m.appPause(); }
     } finally { rally3d.core.Trace.out(154, $t); } }
    static synchronized void resumeAll() { /*T*/ final long $t = rally3d.core.Trace.in(155); try {
        appPaused = false;
        for (java.lang.ref.WeakReference<MidiPlayer> r : ALL) { MidiPlayer m = r.get(); if (m != null) m.appResume(); }
     } finally { rally3d.core.Trace.out(155, $t); } }
    private synchronized void appPause() { /*T*/ final long $t = rally3d.core.Trace.in(156); try {
        try { if (mp != null && state == STARTED && mp.isPlaying()) { mp.pause(); pausedByApp = true; } } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.appPause", ignored);}
     } finally { rally3d.core.Trace.out(156, $t); } }
    private synchronized void appResume() { /*T*/ final long $t = rally3d.core.Trace.in(208); try {
        try { if (mp != null && pausedByApp) { mp.start(); } } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.appResume", ignored);}
        pausedByApp = false;
     } finally { rally3d.core.Trace.out(208, $t); } }
    private final File file;
    private MediaPlayer mp;
    private int state = UNREALIZED;
    private volatile int level = 100;
    private volatile boolean muted;
    private final VolumeControl volume = new VolumeControl() {
        public int setLevel(int l) { level = Math.max(0, Math.min(100, l)); apply(); return level; }
        public int getLevel() { return level; }
        public void setMute(boolean m) { muted = m; apply(); }
        public boolean isMuted() { return muted; }
    };

    MidiPlayer(InputStream in) throws IOException, MediaException {
        if (in == null) throw new MediaException("no stream");
        File dir = new File(rally3d.desktop.Platform.cacheDir(), "midi");
        dir.mkdirs();
        file = new File(dir, "p" + counter.getAndIncrement() + ".mid");
        synchronized (MidiPlayer.class) { ALL.add(new java.lang.ref.WeakReference<MidiPlayer>(this)); }
        try (FileOutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally { try { in.close(); } catch (IOException ignored) { rally3d.core.RLog.caught("MidiPlayer.MidiPlayer", ignored);} }
    }

    private void apply() { /*T*/ final long $t = rally3d.core.Trace.in(157); try {
        MediaPlayer m = mp;
        if (m == null) return;
        float v = muted ? 0f : level / 100f;
        try { m.setVolume(v, v); } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.apply", ignored);}
     } finally { rally3d.core.Trace.out(157, $t); } }

    public synchronized void realize() throws MediaException { /*T*/ final long $t = rally3d.core.Trace.in(158); try {
        if (state >= REALIZED) return;
        try {
            MediaPlayer m = new MediaPlayer();
            m.setDataSource(file.getAbsolutePath());
            mp = m;
            state = REALIZED;
        } catch (Exception e) { rally3d.core.RLog.caught("MidiPlayer.realize", e); throw new MediaException(String.valueOf(e)); }
     } finally { rally3d.core.Trace.out(158, $t); } }

    public synchronized void prefetch() throws MediaException { /*T*/ final long $t = rally3d.core.Trace.in(159); try {
        if (state < REALIZED) realize();
        if (state >= PREFETCHED) return;
        try { mp.prepare(); apply(); state = PREFETCHED; }
        catch (Exception e) { rally3d.core.RLog.caught("MidiPlayer.prefetch", e); throw new MediaException(String.valueOf(e)); }
     } finally { rally3d.core.Trace.out(159, $t); } }

    public synchronized void start() throws MediaException { /*T*/ final long $t = rally3d.core.Trace.in(160); try {
        if (state < PREFETCHED) prefetch();
        if (shutdown) return;
        if (appPaused) { state = STARTED; pausedByApp = true; return; }   // start when the app comes back
        try { apply(); mp.start(); state = STARTED; }
        catch (Exception e) { rally3d.core.RLog.caught("MidiPlayer.start", e); throw new MediaException(String.valueOf(e)); }
     } finally { rally3d.core.Trace.out(160, $t); } }

    public synchronized void stop() throws MediaException { /*T*/ final long $t = rally3d.core.Trace.in(161); try {
        if (mp != null && state == STARTED) {
            try { mp.pause(); mp.seekTo(0); } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.stop", ignored);}
            state = PREFETCHED;
        }
     } finally { rally3d.core.Trace.out(161, $t); } }

    public synchronized void close() { /*T*/ final long $t = rally3d.core.Trace.in(162); try {
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.close", ignored);}
            try { mp.release(); } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.close", ignored);}
            mp = null;
        }
        file.delete();
        state = CLOSED;
     } finally { rally3d.core.Trace.out(162, $t); } }

    public synchronized int getState() { /*T*/ final long $t = rally3d.core.Trace.in(163); try {
        if (state == STARTED && !pausedByApp) {
            try { if (mp == null || !mp.isPlaying()) state = PREFETCHED; } catch (Exception e) { rally3d.core.RLog.caught("MidiPlayer.getState", e); state = PREFETCHED; }
        }
        return state;
     } finally { rally3d.core.Trace.out(163, $t); } }

    public Control getControl(String type) {
        return type != null && type.endsWith("VolumeControl") ? volume : null;
    }
}
