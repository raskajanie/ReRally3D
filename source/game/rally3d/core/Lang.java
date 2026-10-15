package rally3d.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * UI language. The game text is English in the source code; {@link #t(String)} returns the translation of an English text for the
 * current language (or the English text itself when there is none). Translations live in res/i18n/&lt;code&gt;.txt, one entry per line:
 * {@code English text TAB translation}, with \\ \n \t escapes (see tools/i18n in the project sources for the generator).
 * The 109 original game strings (lang.xx) go through the same tables; zh-CN / zh-TW keep their old binary lang.* files. Java 8 safe.
 */
public final class Lang {
    private Lang() {}

    /** {code, name in its own language}. The first entry is the default / fallback. */
    public static final String[][] LANGS = {
        {"en", "English"}, {"ru", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439"}, {"uk", "\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430"},
        {"de", "Deutsch"}, {"it", "Italiano"}, {"fr", "Fran\u00e7ais"}, {"es", "Espa\u00f1ol"},
        {"zh-CN", "\u4e2d\u6587 (\u7b80\u4f53)"}, {"zh-TW", "\u4e2d\u6587 (\u7e41\u9ad4)"}};

    private static volatile String code = "en";
    private static volatile Map<String, String> table = new HashMap<String, String>();

    public static String code() { return code; }

    public static int indexOf(String c) {
        if (c != null) for (int i = 0; i < LANGS.length; i++) if (LANGS[i][0].equals(c)) return i;
        return -1;
    }

    public static boolean isKnown(String c) { return indexOf(c) >= 0; }

    /** Native name of the current language (for the settings line). */
    public static String name() { int i = indexOf(code); return LANGS[i < 0 ? 0 : i][1]; }

    /** Best language for this device: the system language if it is one of ours, else English. */
    public static String detect() {
        try {
            Locale l = Locale.getDefault();
            String lang = l.getLanguage() == null ? "" : l.getLanguage().toLowerCase(Locale.ROOT);
            String tag = l.toString();
            if (lang.equals("zh")) return tag.contains("TW") || tag.contains("HK") || tag.contains("Hant") ? "zh-TW" : "zh-CN";
            if (lang.equals("be") || lang.equals("kk")) return "ru";   // closest of ours
            if (isKnown(lang)) return lang;
        } catch (Throwable ignored) { /* default below */ }
        return "en";
    }

    /** "zh-CN" / "zh-TW" while the old binary lang.* file is used for the original strings, else null. */
    public static String binaryLocale() { return code.startsWith("zh") ? code : null; }

    /** Switches the language and loads its table. Unknown codes fall back to English. */
    public static synchronized void set(String c) {
        if (!isKnown(c)) c = "en";
        Map<String, String> t = new HashMap<String, String>();
        if (!c.equals("en") && !c.startsWith("zh")) load(c, t);
        table = t;
        code = c;
        RLog.i("lang", "language " + c + " (" + t.size() + " texts)");
    }

    private static void load(String c, Map<String, String> out) {
        InputStream in = null;
        try {
            in = Lang.class.getResourceAsStream("/i18n/" + c + ".txt");
            if (in == null) { RLog.w("lang", "no translation file for " + c); return; }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                out.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
            }
        } catch (Throwable e) {
            RLog.caught("Lang.load", e);
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { /* nothing to do */ }
        }
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else b.append(ch);
        }
        return b.toString();
    }

    /** Translation of an English UI text (the English text itself when there is none). */
    public static String t(String en) {
        if (en == null || code.equals("en")) return en;
        String v = table.get(en);
        return v != null ? v : en;
    }

    /** Translated copy of a whole array of English texts (static tables stay English; they are translated where they are shown). */
    public static String[] tl(String[] en) {
        if (en == null) return null;
        String[] r = new String[en.length];
        for (int i = 0; i < en.length; i++) r[i] = t(en[i]);
        return r;
    }

    /** Translation with {0} {1} ... replaced by the arguments. */
    public static String f(String en, Object... args) {
        String s = t(en);
        if (args == null) return s;
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }
}
