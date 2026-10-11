import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Minimal APK packer (replaces aapt add + zipalign). Usage: ApkPack out.apk dir1 [dir2 ...] ; later dirs do not override earlier files.
 *  resources.arsc is stored uncompressed and 4-byte aligned (required for targetSdk 30+). */
public final class ApkPack {
    public static void main(String[] a) throws Exception {
        Map<String, File> files = new LinkedHashMap<String, File>();
        for (int i = 1; i < a.length; i++) {
            Path root = Paths.get(a[i]).toAbsolutePath();
            try (java.util.stream.Stream<Path> s = Files.walk(root)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    if (!Files.isRegularFile(p)) continue;
                    String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
                    if (!files.containsKey(rel)) files.put(rel, p.toFile());
                }
            }
        }
        List<String> names = new ArrayList<String>(files.keySet());
        // manifest first, like aapt
        Collections.sort(names, (x, y) -> x.equals("AndroidManifest.xml") ? -1 : y.equals("AndroidManifest.xml") ? 1 : x.compareTo(y));
        try (CountingOut out = new CountingOut(new BufferedOutputStream(new FileOutputStream(a[0])));
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String n : names) {
                byte[] data = Files.readAllBytes(files.get(n).toPath());
                ZipEntry e = new ZipEntry(n);
                e.setTime(315532800000L);   // fixed timestamp -> reproducible
                if (n.equals("resources.arsc")) {
                    CRC32 crc = new CRC32(); crc.update(data);
                    e.setMethod(ZipEntry.STORED); e.setSize(data.length); e.setCompressedSize(data.length); e.setCrc(crc.getValue());
                    // local header = 30 + name + extra; pad extra (>= 4 bytes, valid zero-id block) so data starts on a 4-byte boundary
                    long base = out.count + 30 + n.getBytes("UTF-8").length;
                    int len = 0;
                    if (base % 4 != 0) { len = 4; while ((base + len) % 4 != 0) len++; }
                    if (len > 0) { byte[] ex = new byte[len]; ex[2] = (byte) (len - 4); e.setExtra(ex); }
                } else e.setMethod(ZipEntry.DEFLATED);
                zip.putNextEntry(e);
                zip.write(data);
                zip.closeEntry();
            }
        }
        System.out.println("packed " + names.size() + " files -> " + a[0]);
    }

    private static final class CountingOut extends FilterOutputStream {
        long count;
        CountingOut(OutputStream o) { super(o); }
        @Override public void write(int b) throws IOException { out.write(b); count++; }
        @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); count += len; }
    }
}
