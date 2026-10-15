package rally3d.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Progress backup as one zip, so progress survives an uninstall on Android (app data is deleted with the app) and can be moved
 * between devices. Contents: rms/ (progress, medals, best times), ghosts/, addons/ (zips + enabled.txt), settings.properties and the
 * custom-map records / medals. Entry names are "base/..." (progress folder) or "extra/..." (folder that holds maprecords.txt).
 * Restoring only accepts these known names, never a path with ".." and is size-limited (the file may come from anywhere).
 */
public final class ProgressBackup {
    private ProgressBackup() {}

    public static final long MAX_TOTAL = 256L * 1024 * 1024;   // whole backup, uncompressed
    public static final long MAX_ENTRY = 64L * 1024 * 1024;
    private static final int MAX_ENTRIES = 2000;
    private static final String[] BASE_DIRS = {"rms", "ghosts"};
    /** Entry prefix of the add-ons folder. The folder itself is NOT below the progress folder on Windows (%APPDATA%\\Rally3D\\addons), so it is passed in. */
    private static final String ADDONS_PREFIX = "base/addons/";
    private static final String[] EXTRA_FILES = {"maprecords.txt", "maprecords.txt.bak", "mapmedals.txt", "mapmedals.txt.bak"};

    private static boolean skip(String name) {
        return name.endsWith(".tmp") || name.endsWith(".part") || name.endsWith(".corrupt");
    }

    public static byte[] create(File base, File addons, File extra) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bo);
        long[] total = {0};
        int[] count = {0};
        for (String d : BASE_DIRS) addDir(z, new File(base, d), "base/" + d, 0, total, count);
        if (addons != null) addDir(z, addons, "base/addons", 0, total, count);
        addFile(z, new File(base, "settings.properties"), "base/settings.properties", total, count);
        if (extra != null) for (String n : EXTRA_FILES) addFile(z, new File(extra, n), "extra/" + n, total, count);
        z.close();
        return bo.toByteArray();
    }

    private static void addDir(ZipOutputStream z, File dir, String rel, int depth, long[] total, int[] count) throws IOException {
        File[] list = dir.listFiles();
        if (list == null || depth > 4) return;
        for (File f : list) {
            String r = rel + "/" + f.getName();
            if (f.isDirectory()) { if (!f.getName().equals("export")) addDir(z, f, r, depth + 1, total, count); }
            else addFile(z, f, r, total, count);
        }
    }

    private static void addFile(ZipOutputStream z, File f, String name, long[] total, int[] count) throws IOException {
        if (!f.isFile() || skip(f.getName())) return;
        long len = f.length();
        if (len > MAX_ENTRY) { RLog.w("backup", "skipped (too big): " + name); return; }
        if (total[0] + len > MAX_TOTAL || ++count[0] > MAX_ENTRIES) throw new IOException("progress folder too big for one backup (over " + (MAX_TOTAL >> 20) + " MB)");
        byte[] data = new byte[(int) len];
        FileInputStream in = new FileInputStream(f);
        try {
            int off = 0, n;
            while (off < data.length && (n = in.read(data, off, data.length - off)) > 0) off += n;
            if (off != data.length) throw new IOException("short read: " + f);
        } finally { in.close(); }
        total[0] += len;
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    /** True for an entry name this backup format may contain. */
    static boolean allowed(String n) {
        if (n.indexOf("..") >= 0 || n.indexOf('\\') >= 0 || n.indexOf(':') >= 0 || n.startsWith("/") || n.endsWith("/")) return false;
        if (n.startsWith("extra/")) { String f = n.substring(6); for (String e : EXTRA_FILES) if (e.equals(f)) return true; return false; }
        if (!n.startsWith("base/")) return false;
        String f = n.substring(5);
        if (f.equals("settings.properties")) return true;
        if (n.startsWith(ADDONS_PREFIX) && n.length() > ADDONS_PREFIX.length()) return true;
        for (String d : BASE_DIRS) if (f.startsWith(d + "/") && f.length() > d.length() + 1) return true;
        return false;
    }

    /** Writes every valid entry over the current files (each write is atomic). Returns the number of files restored; throws when the zip is not a progress backup. */
    public static int restore(byte[] zip, File base, File addons, File extra) throws IOException {
        if (zip == null || zip.length < 22) throw new IOException("not a backup file");
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<byte[]> datas = new ArrayList<byte[]>();
        long total = 0;
        ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip));
        try {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (names.size() >= MAX_ENTRIES) throw new IOException("too many files in backup");
                if (e.isDirectory()) continue;
                String n = e.getName();
                if (!allowed(n)) continue;
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int k;
                while ((k = zin.read(buf)) > 0) {
                    total += k;
                    if (total > MAX_TOTAL || bo.size() + k > MAX_ENTRY) throw new IOException("backup is too big");
                    bo.write(buf, 0, k);
                }
                names.add(n);
                datas.add(bo.toByteArray());
            }
        } finally { zin.close(); }
        if (names.isEmpty()) throw new IOException("no progress data inside (is this a Rally3D backup?)");
        int ok = 0;
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            File target;
            if (n.startsWith(ADDONS_PREFIX)) {
                if (addons == null) continue;
                target = new File(addons, n.substring(ADDONS_PREFIX.length()));
            } else {
                File root = n.startsWith("extra/") ? extra : base;
                if (root == null) continue;
                target = new File(root, n.substring(n.indexOf('/') + 1));
            }
            File dir = target.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
            SafeFile.write(target, datas.get(i));
            ok++;
        }
        return ok;
    }
}
