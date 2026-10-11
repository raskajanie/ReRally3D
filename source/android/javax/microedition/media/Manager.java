package javax.microedition.media;

import java.io.IOException;
import java.io.InputStream;

public final class Manager {
    private Manager() {}
    public static void pauseAll() { MidiPlayer.pauseAll(); }
    public static void resumeAll() { MidiPlayer.resumeAll(); }
    /** Stop and release every player; nothing can start afterwards. Call when the app is closing. */
    /** Call from a fresh Activity: undo closeAll()/pauseAll() left over from a previous activity in the same process. */
    public static void reopen() { MidiPlayer.reopen(); }
    public static void closeAll() { MidiPlayer.closeAll(); }

    public static Player createPlayer(InputStream stream, String type) throws IOException, MediaException {
        return new MidiPlayer(stream);
    }
}
