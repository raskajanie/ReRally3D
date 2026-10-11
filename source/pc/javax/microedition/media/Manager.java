package javax.microedition.media;

import java.io.IOException;
import java.io.InputStream;

public final class Manager {
    private Manager() {}

    public static Player createPlayer(InputStream stream, String type) throws IOException, MediaException {
        return new MidiPlayer(stream);
    }
}
