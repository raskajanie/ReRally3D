package rally3d.android;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import rally3d.core.RLog;

/**
 * One-time migration of old saves into the current data dir (Android/data/&lt;pkg&gt;/files).
 * Older builds kept working files in app-private storage (getFilesDir) and mirrored them to /sdcard/Rally3D
 * (which needed "All files access"). The game no longer uses shared storage or any storage permission.
 */
public final class Backup {
    private Backup() {}

    public static void migrateLegacy(Context ctx, File dataDir) { /*T*/ final long $t = rally3d.core.Trace.in(184); try {
        try {
            File old = ctx.getFilesDir();
            if (old == null || dataDir == null || old.equals(dataDir)) return;
            int n = migrate(old, dataDir);
            if (n > 0) RLog.i("storage", "migrated " + n + " files from " + old + " to " + dataDir);
        } catch (Throwable t) { rally3d.core.RLog.caught("Backup.migrateLegacy", t);
            RLog.w("storage", "legacy migration failed", t);
        }
     } finally { rally3d.core.Trace.out(184, $t); } }

    /** Copies rms/, ghosts/ and settings.properties from `old` into `dataDir` where missing. Pure file logic (unit-testable). */
    static int migrate(File old, File dataDir) throws IOException { return copyMissing(old, dataDir, "", 0); }

    private static int copyMissing(File from, File to, String rel, int depth) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(185); try {
        File[] list = from.listFiles();
        if (list == null || depth > 4) return 0;
        int n = 0;
        for (File f : list) {
            String r = rel.isEmpty() ? f.getName() : rel + "/" + f.getName();
            if (f.isDirectory()) { n += copyMissing(f, to, r, depth + 1); continue; }
            if (!(r.startsWith("rms/") || r.startsWith("ghosts/") || r.equals("settings.properties")) || f.getName().endsWith(".tmp")) continue;
            File dst = new File(to, r);
            if (dst.exists() && dst.length() > 0) continue;
            File dir = dst.getParentFile();
            if (dir != null) dir.mkdirs();
            File tmp = new File(dst.getPath() + ".tmp");
            FileInputStream in = new FileInputStream(f);
            try {
                FileOutputStream out = new FileOutputStream(tmp);
                try { byte[] buf = new byte[16384]; int k; while ((k = in.read(buf)) > 0) out.write(buf, 0, k); }
                finally { out.close(); }
            } finally { in.close(); }
            if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst); }
            n++;
        }
        return n;
     } finally { rally3d.core.Trace.out(185, $t); } }
}
