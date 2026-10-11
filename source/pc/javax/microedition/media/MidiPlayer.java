package javax.microedition.media;

import java.io.IOException;
import java.io.InputStream;
import javax.microedition.media.control.VolumeControl;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;

/** MIDI Player: one shared software synthesizer, one lightweight Sequencer per Player. */
final class MidiPlayer implements Player {
    private static Synthesizer synth;
    private static synchronized Synthesizer synth() throws Exception {
        if (synth == null) { Synthesizer s = MidiSystem.getSynthesizer(); s.open(); synth = s; }
        return synth;
    }

    private final Sequence sequence;
    private Sequencer sequencer;
    private int state = UNREALIZED;
    private volatile int level = 100;
    private volatile boolean muted;
    private final VolumeControl volume = new VolumeControl() {
        public int setLevel(int l) { level = Math.max(0, Math.min(100, l)); return level; }
        public int getLevel() { return level; }
        public void setMute(boolean m) { muted = m; }
        public boolean isMuted() { return muted; }
    };

    MidiPlayer(InputStream in) throws IOException, MediaException {
        try { sequence = MidiSystem.getSequence(in); }
        catch (InvalidMidiDataException e) { throw new MediaException(e.getMessage()); }
    }

    public synchronized void realize() throws MediaException {
        if (state >= REALIZED) return;
        try {
            Sequencer s = MidiSystem.getSequencer(false);
            s.open();
            s.setSequence(sequence);
            final Receiver target = synth().getReceiver();
            s.getTransmitter().setReceiver(new Receiver() {
                public void send(MidiMessage m, long ts) {
                    if (muted) { if (m instanceof ShortMessage && ((ShortMessage) m).getCommand() == ShortMessage.NOTE_ON) return; }
                    if (m instanceof ShortMessage) {
                        ShortMessage sm = (ShortMessage) m;
                        if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0 && level < 100) {
                            try {
                                ShortMessage c = new ShortMessage();
                                c.setMessage(ShortMessage.NOTE_ON, sm.getChannel(), sm.getData1(), Math.max(1, sm.getData2() * level / 100));
                                target.send(c, ts);
                                return;
                            } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.send", ignored); }
                        }
                    }
                    target.send(m, ts);
                }
                public void close() {}
            });
            sequencer = s;
            state = REALIZED;
        } catch (Exception e) {
            throw new MediaException(String.valueOf(e));
        }
    }

    public synchronized void prefetch() throws MediaException {
        if (state < REALIZED) realize();
        state = PREFETCHED;
    }

    public synchronized void start() throws MediaException {
        if (state < PREFETCHED) prefetch();
        sequencer.start();
        state = STARTED;
    }

    public synchronized void stop() throws MediaException {
        if (sequencer != null && sequencer.isRunning()) sequencer.stop();
        if (state == STARTED) state = PREFETCHED;
    }

    public synchronized void close() {
        if (sequencer != null) {
            try { sequencer.stop(); } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.stop", ignored); }
            try { sequencer.close(); } catch (Exception ignored) { rally3d.core.RLog.caught("MidiPlayer.close", ignored); }
            sequencer = null;
        }
        state = CLOSED;
    }

    public synchronized int getState() {
        if (state == STARTED && (sequencer == null || !sequencer.isRunning())) state = PREFETCHED;
        return state;
    }

    public Control getControl(String type) {
        return type != null && type.endsWith("VolumeControl") ? volume : null;
    }
}
