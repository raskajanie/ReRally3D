package rally3d.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Crash-safe file writing: data goes to name.tmp, is fsync'ed, the previous good file becomes name.bak, then name.tmp is renamed over name.
 * A crash, kill or power loss at any moment leaves either the old or the new complete file (plus .bak), never a half-written save.
 * Readers try the main file first, then .bak (see candidates()).
 */
public final class SafeFile {
    private SafeFile() {}

    public static void write(File file, byte[] data) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(218); try {
        File tmp = new File(file.getPath() + ".tmp"), bak = new File(file.getPath() + ".bak");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(data);
            out.flush();
            try { out.getFD().sync(); } catch (IOException e) { rally3d.core.RLog.caught("SafeFile.write", e); RLog.w("save", "fsync failed for " + tmp + ": " + e); }
        } finally { out.close(); }
        if (file.exists()) {
            bak.delete();
            if (!file.renameTo(bak)) RLog.w("save", "cannot keep backup " + bak);
        }
        if (!tmp.renameTo(file)) {
            file.delete();
            if (!tmp.renameTo(file)) throw new IOException("cannot move " + tmp + " to " + file);
        }
     } finally { rally3d.core.Trace.out(218, $t); } }

    public static byte[] read(File f) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(219); try {
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            if (off != b.length) throw new IOException("short read " + off + "/" + b.length + " of " + f);
            return b;
        } finally { in.close(); }
     } finally { rally3d.core.Trace.out(219, $t); } }

    /** Candidate files in recovery order: main, then backup. */
    public static File[] candidates(File file) {
        return new File[]{file, new File(file.getPath() + ".bak")};
    }
}
