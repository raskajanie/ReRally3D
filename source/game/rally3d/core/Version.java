package rally3d.core;

/**
 * SINGLE SOURCE OF TRUTH for the game version (PC + Android). Shown in the main menu (bottom-left),
 * logged at start, and sent in LAN multiplayer: players with different NAME cannot join each other.
 * Change NAME (and CODE) here AND android:versionName / android:versionCode in source/android-pkg/AndroidManifest.xml together
 * (see README "VERSIONS"). Android logs a WARN at start if they differ.
 */
public final class Version {
    private Version() {}
    public static final String NAME = "2.0-alpha65";
    public static final int CODE = 60;
}
