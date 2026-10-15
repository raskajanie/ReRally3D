package com.nokia.mid.appl.rally3d;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

public class Localization {
    private static Localization instance = null;
    private static DataInputStream langStream = null;

    /** Drops the cached stream so the next string is read in the current language (called after a language change). */
    public static synchronized void reset() {
        try { if (langStream != null) langStream.close(); } catch (IOException e) { rally3d.core.RLog.caught("Localization.reset", e); }
        langStream = null;
    }

    private Localization() {
    }

    public static String replaceAll(String string, String string2, String string3) {
        int n;
        do {
            if ((n = string.indexOf(string2)) < 0) continue;
            string = string.substring(0, n) + string3 + string.substring(n + string2.length());
        } while (n >= 0);
        return string;
    }

    public static synchronized String getString(int n) {
        return Localization.getFormatted(n, null);
    }

    public static synchronized String getFormatted(int n, String[] stringArray) {
        try {
            if (instance == null) {
                instance = new Localization();
            }
            if (langStream == null) {
                String bin = rally3d.core.Lang.binaryLocale();   // zh-CN / zh-TW keep their own binary file; the other languages translate the English strings
                InputStream inputStream = bin == null ? null : instance.getClass().getResourceAsStream("/lang." + bin);
                if (inputStream == null) {
                    inputStream = instance.getClass().getResourceAsStream("/lang.xx");
                }
                if (inputStream == null) {
                    return "X";
                }
                langStream = new DataInputStream(inputStream);
                langStream.mark(512);
            }
            langStream.skipBytes(n * 2);
            short s = langStream.readShort();
            langStream.skipBytes(s - n * 2 - 2);
            String string = langStream.readUTF();
            if (rally3d.core.Lang.binaryLocale() == null) string = rally3d.core.Lang.t(string);
            if (!langStream.markSupported()) {
                langStream.close();
                langStream = null;
            } else {
                try {
                    langStream.reset();
                }
                catch (IOException iOException) { rally3d.core.RLog.caught("Localization.getFormatted", iOException);
                    langStream.close();
                    langStream = null;
                }
            }
            if (stringArray != null) {
                if (stringArray.length == 1) {
                    string = Localization.replaceAll(string, "%U", stringArray[0]);
                } else {
                    for (int i = 0; i < stringArray.length; ++i) {
                        string = Localization.replaceAll(string, "%" + i + "U", stringArray[i]);
                    }
                }
            }
            return string;
        }
        catch (IOException iOException) { rally3d.core.RLog.caught("Localization.getFormatted", iOException);
            langStream = null;
            return "E";
        }
    }

    private static boolean matches(String string, String string2) {
        return Localization.wildcardMatch(string, string2, 0, 0);
    }

    private static boolean wildcardMatch(String string, String string2, int n, int n2) {
        block4: while (true) {
            if (n == string.length() && n2 == string2.length()) {
                return true;
            }
            if (n == string.length() || n2 == string2.length()) {
                return false;
            }
            switch (string2.charAt(n2)) {
                case '?': {
                    ++n;
                    ++n2;
                    continue block4;
                }
                case '*': {
                    if (n2 == string2.length() - 1) {
                        return true;
                    }
                    if (Localization.wildcardMatch(string, string2, n, n2 + 1)) {
                        return true;
                    }
                    ++n;
                    continue block4;
                }
            }
            if (string.charAt(n) != string2.charAt(n2)) break;
            ++n;
            ++n2;
        }
        return false;
    }

    /**
     * Original platform check. If the MIDlet manifest contains a "Nokia-Platform:" line
     * (list of allowed platform patterns separated by '@'), the game exits unless the
     * current "microedition.platform" matches one of them. This build's manifest has no
     * such line, so the check passes.
     * DEVIATION from the original: if the manifest cannot be read at all (many emulators do
     * not expose /META-INF/MANIFEST.MF as a resource) the original called System.exit(0);
     * here the check is simply skipped.
     */
    private static void checkPlatform() {
        String platform = System.getProperty("microedition.platform");
        StringBuffer line = new StringBuffer();
        if (instance == null) {
            instance = new Localization();
        }
        InputStream manifest = instance.getClass().getResourceAsStream("/META-INF/MANIFEST.MF");
        if (manifest == null) {
            return;
        }
        boolean found = false;
        while (!found) {
            int c;
            try {
                c = manifest.read();
            } catch (IOException e) { rally3d.core.RLog.caught("Localization.checkPlatform", e);
                continue;
            }
            if (c < 0) {
                return; // no Nokia-Platform line -> no restriction
            }
            if ((char) c == '\n') {
                if (line.toString().trim().startsWith("Nokia-Platform:")) {
                    found = true;
                } else {
                    line.delete(0, line.length());
                }
            } else {
                line.append((char) c);
            }
        }
        String patterns = line.toString().trim().substring(15);
        Vector vector = new Vector();
        int at;
        while ((at = patterns.indexOf("@")) != -1) {
            vector.addElement(patterns.substring(0, at));
            patterns = patterns.substring(at + 1, patterns.length());
        }
        vector.addElement(patterns);
        boolean allowed = false;
        for (int i = 0; i < vector.size(); ++i) {
            if (matches(platform, ((String) vector.elementAt(i)).trim())) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            System.exit(0);
        }
    }

    static {
        Localization.checkPlatform();
    }
}

