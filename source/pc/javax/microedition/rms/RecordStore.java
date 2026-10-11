package javax.microedition.rms;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Record store persisted as ~/.rally3d/rms/<n>.rms. Crash-safe (SafeFile: tmp -> fsync -> .bak -> rename), bounded reads, broken file kept as .corrupt. */
public class RecordStore {
    private final File file;
    private final List<byte[]> records = new ArrayList<byte[]>();

    private RecordStore(File f) { file = f; }

    private static File dir() {
        File d = new File(System.getProperty("user.home"), ".rally3d/rms");
        d.mkdirs();
        return d;
    }

    public static synchronized RecordStore openRecordStore(String name, boolean create) throws RecordStoreException {
        File f = new File(dir(), name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".rms");
        RecordStore rs = new RecordStore(f);
        boolean loaded = false;
        for (File cand : rally3d.core.SafeFile.candidates(f)) {
            if (!cand.exists()) continue;
            try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(rally3d.core.SafeFile.read(cand)))) {
                int n = in.readInt();
                if (n < 0 || n > 100000) throw new java.io.IOException("bad record count " + n);
                rs.records.clear();
                for (int i = 0; i < n; i++) {
                    int len = in.readInt();
                    if (len < 0 || len > 4_000_000) throw new java.io.IOException("bad record length " + len);
                    byte[] bt = new byte[len]; in.readFully(bt); rs.records.add(bt);
                }
                loaded = true;
                if (cand != f) rally3d.core.RLog.e("save", "record store " + f.getName() + " was unreadable, RESTORED from backup " + cand.getName());
                break;
            } catch (Exception e) {
                rally3d.core.RLog.caught("RecordStore.openRecordStore", e);
                rally3d.core.RLog.e("save", "cannot read " + cand + ": " + e + (cand == f ? " (trying backup)" : ""));
                rs.records.clear();
                if (cand == f) { File bad = new File(f.getPath() + ".corrupt"); bad.delete(); f.renameTo(bad); }   // keep for inspection, never silently overwrite
            }
        }
        if (!loaded && !f.exists() && !new File(f.getPath() + ".bak").exists() && !create) throw new RecordStoreNotFoundException(name);
        return rs; }

    public synchronized int getNumRecords() { return records.size(); }

    public synchronized byte[] getRecord(int id) throws RecordStoreException {
        if (id < 1 || id > records.size()) throw new RecordStoreException("invalid record " + id);
        return records.get(id - 1).clone(); }

    public synchronized int addRecord(byte[] d, int off, int len) throws RecordStoreException {
        byte[] b = new byte[len];
        System.arraycopy(d, off, b, 0, len);
        records.add(b);
        flush();
        return records.size(); }

    public synchronized void setRecord(int id, byte[] d, int off, int len) throws RecordStoreException {
        if (id < 1 || id > records.size()) throw new RecordStoreException("invalid record " + id);
        byte[] b = new byte[len];
        System.arraycopy(d, off, b, 0, len);
        records.set(id - 1, b);
        flush(); }

    public synchronized void closeRecordStore() throws RecordStoreException {  }

    private void flush() {
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bo);
            out.writeInt(records.size());
            for (byte[] b : records) { out.writeInt(b.length); out.write(b); }
            out.flush();
            rally3d.core.SafeFile.write(file, bo.toByteArray());
        } catch (Exception ignored) { rally3d.core.RLog.caught("RecordStore.flush", ignored); rally3d.core.RLog.e("save", "FAILED to write " + file + ": " + ignored); } }
}
