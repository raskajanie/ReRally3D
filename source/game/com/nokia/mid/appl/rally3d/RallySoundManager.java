package com.nokia.mid.appl.rally3d;

class RallySoundManager
extends MidiSoundManager {
    RallySoundManager() throws Exception {
        super();
    }

    void loadTracks(int n) { /*T*/ final long $t = rally3d.core.Trace.in(107); try {
        this.reset();
        this.addTrack("/midi/check.mid", "audio/midi");
        this.addTrack("/midi/plink1.mid", "audio/midi");
        this.addTrack("/midi/plink2.mid", "audio/midi");
        this.addTrack("/midi/title.mid", "audio/midi");
        this.addTrack("/midi/goal.mid", "audio/midi");
        ++this.trackCount;
     } finally { rally3d.core.Trace.out(107, $t); } }
}

