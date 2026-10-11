package com.nokia.mid.appl.rally3d;

import java.io.IOException;
import java.io.InputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.VolumeControl;

class MidiSoundManager
extends SoundManager
implements Runnable {
    int pendingTrack;
    Player player;
    VolumeControl volumeControl;
    int volume = 99;
    String[] trackPaths = new String[6];
    String[] trackMimeTypes = new String[6];

    MidiSoundManager() throws Exception {
        InputStream inputStream = null;
        try {
            Class.forName("javax.microedition.media.Player");
            inputStream = this.getClass().getResourceAsStream("/midi/check.mid");
            this.player = Manager.createPlayer((InputStream)inputStream, (String)"audio/midi");
            this.player.realize();
            this.player.prefetch();
            this.player.close();
            this.supported = true;
        }
        catch (ClassNotFoundException classNotFoundException) { rally3d.core.RLog.caught("MidiSoundManager.MidiSoundManager", classNotFoundException);
            this.supported = false;
            throw new Exception("Player not ready");
        }
        catch (IOException iOException) { rally3d.core.RLog.caught("MidiSoundManager.MidiSoundManager", iOException);
            this.supported = false;
            throw new Exception("Player not ready");
        }
        catch (MediaException mediaException) { rally3d.core.RLog.caught("MidiSoundManager.MidiSoundManager", mediaException);
            this.supported = false;
            throw new Exception("Player not ready");
        }
        finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                }
                catch (Exception exception) { rally3d.core.RLog.caught("MidiSoundManager.MidiSoundManager", exception);}
            }
        }
        this.player = null;
        this.volumeControl = null;
        this.pendingTrack = -1;
        this.trackCount = 0;
        Thread thread = new Thread(this);
        thread.start();
    }

    protected void addTrack(String string, String string2) { /*T*/ final long $t = rally3d.core.Trace.in(25); try {
        if (this.trackCount < 6) {
            this.trackPaths[this.trackCount] = string;
            this.trackMimeTypes[this.trackCount] = string2;
            ++this.trackCount;
        }
     } finally { rally3d.core.Trace.out(25, $t); } }

    private void createPlayer(String string, String string2) { /*T*/ final long $t = rally3d.core.Trace.in(26); try {
        try {
            InputStream inputStream = this.getClass().getResourceAsStream(string);
            this.player = Manager.createPlayer((InputStream)inputStream, (String)string2);
        }
        catch (Exception exception) { rally3d.core.RLog.caught("MidiSoundManager.createPlayer", exception);
            // empty catch block
        }
     } finally { rally3d.core.Trace.out(26, $t); } }

    private void closePlayer() { /*T*/ final long $t = rally3d.core.Trace.in(27); try {
        if (this.player != null) {
            try {
                this.player.stop();
            }
            catch (Exception exception) { rally3d.core.RLog.caught("MidiSoundManager.closePlayer", exception);
                // empty catch block
            }
            try {
                this.player.close();
            }
            catch (Exception exception) { rally3d.core.RLog.caught("MidiSoundManager.closePlayer", exception);
                // empty catch block
            }
            if (this.volumeControl != null) {
                this.volumeControl = null;
            }
            this.player = null;
        }
     } finally { rally3d.core.Trace.out(27, $t); } }

    protected void reset() {
        this.pendingTrack = -1;
        this.trackCount = 0;
        this.closePlayer();
        System.gc();
    }

    void setVolume(int n) { /*T*/ final long $t = rally3d.core.Trace.in(28); try {
        this.volume = n;
        if (this.player != null && this.volumeControl != null) {
            this.volumeControl.setLevel(n);
        }
     } finally { rally3d.core.Trace.out(28, $t); } }

    void play(int n) {
        if (this.enabled && n < this.trackCount) {
            this.pendingTrack = n;
        }
    }

    void stop() {
        this.closePlayer();
    }

    int getPlayState() {
        Player cur = this.player;
        if (cur != null && cur.getState() == 400) {
            return 0;
        }
        return -1;
    }

    public void run() { /*T*/ final long $t = rally3d.core.Trace.in(29); try {
        while (true) {
            if (this.pendingTrack > -1) {
                this.closePlayer();
                this.createPlayer(this.trackPaths[this.pendingTrack], this.trackMimeTypes[this.pendingTrack]);
                try {
                    this.player.realize();
                }
                catch (Exception exception) { rally3d.core.RLog.caught("MidiSoundManager.run", exception);
                    // empty catch block
                }
                try {
                    this.player.prefetch();
                }
                catch (Exception exception) { rally3d.core.RLog.caught("MidiSoundManager.run", exception);
                    // empty catch block
                }
                if (this.player != null) {
                    this.volumeControl = (VolumeControl)this.player.getControl("VolumeControl");
                    if (this.volumeControl != null) {
                        this.volumeControl.setLevel(this.volume);
                    }
                }
                try {
                    this.player.start();
                }
                catch (Exception exception) { rally3d.core.RLog.caught("MidiSoundManager.run", exception);
                    // empty catch block
                }
                this.pendingTrack = -1;
                continue;
            }
            try {
                Thread.sleep(100L);
            }
            catch (InterruptedException interruptedException) {
            }
        }
     } finally { rally3d.core.Trace.out(29, $t); } }
}

