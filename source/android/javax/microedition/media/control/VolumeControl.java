package javax.microedition.media.control;

public interface VolumeControl extends javax.microedition.media.Control {
    int setLevel(int level);
    int getLevel();
    void setMute(boolean mute);
    boolean isMuted();
}
