package com.nokia.mid.appl.rally3d;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.zip.CRC32;
import rally3d.core.RLog;
import rally3d.core.SafeFile;

/**
 * Custom maps as the race code sees them. A stock track is an index 0..11; a custom map is an id &gt;= CUSTOM_BASE derived from
 * its name + author (so records and ghosts survive re-exporting the map, and every machine computes the same id).
 * Maps received from the host in multiplayer live in the session list for the current round only.
 */
final class MapLibrary {
    static final int CUSTOM_BASE = 1000000;
    /** Wire value of the multiplayer "track" packet meaning: custom map, take it from the map slot of AddonSync. */
    static final int MP_MARK = 100;
    static final int MAX_MENU_MAPS = 40;
    static final int TEST_ID = CUSTOM_BASE + 0x3FFFFF;   // the editor's test drive

    private MapLibrary() {}

    private static final HashMap<Integer, AddonPack> session = new HashMap<Integer, AddonPack>();

    static boolean isCustom(int track) { return track >= CUSTOM_BASE; }

    static int idOf(AddonPack p) { return idOf(p.name, p.author); }

    static int idOf(String name, String author) { /*T*/ final long $t = rally3d.core.Trace.in(459); try {
        CRC32 c = new CRC32();
        try { c.update((name.trim().toLowerCase() + "|" + author.trim().toLowerCase()).getBytes("UTF-8")); } catch (java.io.UnsupportedEncodingException e) { RLog.caught("MapLibrary.idOf", e); }
        int id = CUSTOM_BASE + (int) (c.getValue() & 0x3FFFFE);   // never TEST_ID
        return id;
     } finally { rally3d.core.Trace.out(459, $t); } }

    static synchronized void putSession(int id, AddonPack p) { session.put(Integer.valueOf(id), p); }
    static synchronized void removeSession(int id) { session.remove(Integer.valueOf(id)); }
    static synchronized void clearSession() { session.clear(); }

    /** The pack to race on: a session map (received from the host / test drive) wins over an installed one with the same id. */
    static synchronized AddonPack byId(int id) { /*T*/ final long $t = rally3d.core.Trace.in(460); try {
        AddonPack s = session.get(Integer.valueOf(id));
        if (s != null) return s;
        for (AddonPack p : AddonManager.enabledMaps()) if (idOf(p) == id) return p;
        return null;
     } finally { rally3d.core.Trace.out(460, $t); } }

    /** Enabled maps for the track menus, sorted by name; they are listed before the stock tracks. */
    static List<AddonPack> menuMaps() { /*T*/ final long $t = rally3d.core.Trace.in(461); try {
        ArrayList<AddonPack> l = new ArrayList<AddonPack>();
        for (AddonPack p : AddonManager.enabledMaps()) if (p.mapBin != null) l.add(p);
        Collections.sort(l, new Comparator<AddonPack>() {
            public int compare(AddonPack a, AddonPack b) { return a.name.compareToIgnoreCase(b.name); }
        });
        while (l.size() > MAX_MENU_MAPS) l.remove(l.size() - 1);
        return l;
     } finally { rally3d.core.Trace.out(461, $t); } }

    static String nameOf(int track) { /*T*/ final long $t = rally3d.core.Trace.in(462); try {
        if (!isCustom(track)) return track >= 0 && track < Texts.trackNames.length ? Texts.trackNames[track] : "?";
        AddonPack p = byId(track);
        return p == null ? "Custom map" : p.name;
     } finally { rally3d.core.Trace.out(462, $t); } }

    // ------------------------------------------------------------------ records (top 5 per custom map)

    private static File recordFile() { /*T*/ final long $t = rally3d.core.Trace.in(463); try {
        File d = rally3d.desktop.Platform.addonsDir();
        File parent = d == null ? null : d.getParentFile();
        return parent == null ? null : new File(parent, "maprecords.txt");
     } finally { rally3d.core.Trace.out(463, $t); } }

    private static HashMap<Integer, String[]> recNames = new HashMap<Integer, String[]>();
    private static HashMap<Integer, int[]> recTimes = new HashMap<Integer, int[]>();
    private static boolean recLoaded;

    private static synchronized void loadRecords() { /*T*/ final long $t = rally3d.core.Trace.in(464); try {
        if (recLoaded) return;
        recLoaded = true;
        File f = recordFile();
        if (f == null) return;
        for (File c : SafeFile.candidates(f)) {
            if (!c.isFile()) continue;
            try {
                String text = new String(SafeFile.read(c), "UTF-8");
                for (String line : text.split("\n")) {
                    String[] t = line.split("\\|", 4);
                    if (t.length < 4) continue;
                    try {
                        int id = Integer.parseInt(t[0].trim()), slot = Integer.parseInt(t[1].trim()), time = Integer.parseInt(t[2].trim());
                        if (slot < 0 || slot > 4) continue;
                        ensure(id);
                        recNames.get(Integer.valueOf(id))[slot] = t[3].trim();
                        recTimes.get(Integer.valueOf(id))[slot] = time;
                    } catch (NumberFormatException e) { RLog.caught("MapLibrary.loadRecords", e); }
                }
                return;
            } catch (IOException e) { RLog.caught("MapLibrary.loadRecords", e); }
        }
     } finally { rally3d.core.Trace.out(464, $t); } }

    private static void ensure(int id) { /*T*/ final long $t = rally3d.core.Trace.in(465); try {
        Integer k = Integer.valueOf(id);
        if (!recNames.containsKey(k)) {
            String[] n = new String[5];
            int[] t = new int[5];
            for (int i = 0; i < 5; i++) { n[i] = "-"; t[i] = -1; }
            recNames.put(k, n);
            recTimes.put(k, t);
        }
     } finally { rally3d.core.Trace.out(465, $t); } }

    static synchronized String[] bestNames(int id) { loadRecords(); ensure(id); return recNames.get(Integer.valueOf(id)); }
    static synchronized int[] bestTimes(int id) { loadRecords(); ensure(id); return recTimes.get(Integer.valueOf(id)); }

    /** Stores the five rows the race code computed (names / times as in SaveData.bestNames / bestTimes). */
    static synchronized void setBest(int id, String[] names, int[] times) { /*T*/ final long $t = rally3d.core.Trace.in(466); try {
        if (id == TEST_ID) return;
        loadRecords();
        ensure(id);
        String[] n = recNames.get(Integer.valueOf(id));
        int[] t = recTimes.get(Integer.valueOf(id));
        for (int i = 0; i < 5; i++) { n[i] = names[i] == null ? "-" : names[i].replace('|', ' ').replace('\n', ' '); t[i] = times[i]; }
        saveRecords();
     } finally { rally3d.core.Trace.out(466, $t); } }

    private static void saveRecords() { /*T*/ final long $t = rally3d.core.Trace.in(467); try {
        File f = recordFile();
        if (f == null) return;
        StringBuilder sb = new StringBuilder();
        for (Integer id : recTimes.keySet()) {
            String[] n = recNames.get(id);
            int[] t = recTimes.get(id);
            for (int i = 0; i < 5; i++) if (t[i] >= 0) sb.append(id).append('|').append(i).append('|').append(t[i]).append('|').append(n[i]).append('\n');
        }
        try { SafeFile.write(f, sb.toString().getBytes("UTF-8")); } catch (IOException e) { RLog.caught("MapLibrary.saveRecords", e); RLog.e("map", "cannot save map records: " + e); }
     } finally { rally3d.core.Trace.out(467, $t); } }

    // ------------------------------------------------------------------ install

    /** Writes a map zip into add-ons/maps and enables it (replacing the pack with the same name + author). Returns the installed pack. */
    static AddonPack install(byte[] zip) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(468); try {
        AddonPack np = AddonPack.parse(zip, null);
        if (np.error != null) throw new IOException(np.error);
        File root = AddonManager.ensureDirs();
        if (root == null) throw new IOException("no add-ons folder");
        File dir = new File(root, "maps");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        String base = MapData.slug(np.name);
        File target = null;
        for (int k = 1; k < 100 && target == null; k++) {
            File f = new File(dir, base + (k == 1 ? "" : "-" + k) + ".zip");
            if (!f.exists()) { target = f; break; }
            try {
                AddonPack old = AddonPack.parse(java.nio.file.Files.readAllBytes(f.toPath()), null);
                if (old.error != null || idOf(old) == idOf(np)) target = f;
            } catch (IOException e) { RLog.caught("MapLibrary.install", e); target = f; }
        }
        if (target == null) throw new IOException("too many maps with this name");
        SafeFile.write(target, zip);
        AddonManager.rescan();
        String rel = "maps/" + target.getName();
        for (AddonPack p : AddonManager.packs) if (rel.equals(p.file)) { if (p.error == null) AddonManager.setEnabled(p, true); RLog.i("map", "installed map '" + np.name + "' as " + rel + " (" + zip.length + " bytes)"); return p; }
        throw new IOException("saved, but the pack was not found after rescan");
     } finally { rally3d.core.Trace.out(468, $t); } }
}
