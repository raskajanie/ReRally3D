package com.nokia.mid.appl.rally3d;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import rally3d.core.RLog;
import rally3d.core.SafeFile;

/**
 * Add-ons ("Add-ons"): zips switched on/off on the fly. Storage layout (same on every platform, rooted at Platform.addonsDir()):
 *   PC (Windows): %APPDATA%\Rally3D\addons      Android: Android/data/com.rally3d.android/files/addons
 *     maps\    cars\    wheels\    enabled.txt   (enabled.txt holds paths relative to the root, e.g. "cars/red-rocket.zip")
 * The root and the three sub-folders are (re)created on EVERY start (ensureDirs) and on every rescan.
 * A zip dropped/imported anywhere (root, wrong folder, old ~/.rally3d/addons) is sorted by its addon.properties type into the right folder.
 */
final class AddonManager {
    static final int MAX_LOCAL_CARS = 24;
    /** Sub-folder per kind: 0 = maps, 1 = cars (incl. skins), 2 = wheels. */
    static final String[] KIND_DIRS = {"maps", "cars", "wheels"};
    static final ArrayList<AddonPack> packs = new ArrayList<AddonPack>();
    /** Bumped whenever the set of enabled cars changes, so RallyGame knows to rebuild its car list. */
    static volatile int revision = 1;

    private AddonManager() {}

    /** Add-ons root; creates it and maps/cars/wheels when missing (called at startup, on every rescan and on every access). */
    static synchronized File ensureDirs() { /*T*/ final long $t = rally3d.core.Trace.in(328); try {
        File d = rally3d.desktop.Platform.addonsDir();
        if (d == null) return null;
        if (!d.isDirectory() && !d.mkdirs() && !d.isDirectory()) { RLog.e("addon", "cannot create add-ons folder " + d); return d; }
        for (int i = 0; i < KIND_DIRS.length; i++) {
            File s = new File(d, KIND_DIRS[i]);
            if (!s.isDirectory() && !s.mkdirs() && !s.isDirectory()) RLog.e("addon", "cannot create folder " + s);
        }
        return d;
     } finally { rally3d.core.Trace.out(328, $t); } }

    static File dir() { return ensureDirs(); }

    private static Set<String> readEnabled(File d) { /*T*/ final long $t = rally3d.core.Trace.in(259); try {
        Set<String> set = new HashSet<String>();
        File f = new File(d, "enabled.txt");
        File[] cands = SafeFile.candidates(f);
        for (int i = 0; i < cands.length; i++) {
            if (!cands[i].isFile()) continue;
            try {
                String text = new String(SafeFile.read(cands[i]), "UTF-8");
                for (String line : text.split("\n")) { line = line.trim().replace('\\', '/'); if (line.length() > 0) set.add(line); }
                return set;
            } catch (IOException e) { RLog.caught("AddonManager.readEnabled", e); }
        }
        return set;
     } finally { rally3d.core.Trace.out(259, $t); } }

    private static void saveEnabled() { /*T*/ final long $t = rally3d.core.Trace.in(260); try {
        File d = ensureDirs();
        if (d == null) return;
        StringBuilder sb = new StringBuilder();
        for (AddonPack p : packs) if (p.enabled && p.file != null) sb.append(p.file).append('\n');
        try { SafeFile.write(new File(d, "enabled.txt"), sb.toString().getBytes("UTF-8")); }
        catch (IOException e) { RLog.caught("AddonManager.saveEnabled", e); RLog.e("addon", "cannot save enabled.txt: " + e); }
     } finally { rally3d.core.Trace.out(260, $t); } }

    private static byte[] readAll(File f) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(261); try {
        if (f.length() > AddonPack.MAX_ZIP) throw new IOException(rally3d.core.Lang.t("zip too big (max ") + (AddonPack.MAX_ZIP / 1024) + rally3d.core.Lang.t(" KB)"));
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return b;
        } finally { in.close(); }
     } finally { rally3d.core.Trace.out(261, $t); } }

    private static boolean isZip(File f) { return f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"); }

    /** Moves src to dir/name (adds -2, -3... when taken). Returns the new file or null. */
    private static File moveInto(File src, File dir, String name) { /*T*/ final long $t = rally3d.core.Trace.in(329); try {
        try {
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return null;
            File target = new File(dir, name);
            String stem = name.replaceAll("(?i)\\.zip$", "");
            for (int i = 2; target.exists(); i++) target = new File(dir, stem + "-" + i + ".zip");
            try { Files.move(src.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException e) { rally3d.core.RLog.caught("AddonManager.moveInto", e);   // different volume / no atomic move: copy + delete
                RLog.caught("AddonManager.moveInto", e);
                Files.copy(src.toPath(), target.toPath());
                if (!src.delete()) { Files.deleteIfExists(target.toPath()); return null; }
            }
            return target;
        } catch (IOException e) { RLog.caught("AddonManager.moveInto", e); RLog.w("addon", "cannot move " + src + ": " + e); return null; }
     } finally { rally3d.core.Trace.out(329, $t); } }

    /** One-time move of zips (and enabled.txt entries) from the old PC location (~/.rally3d/addons) into the new root. Returns enabled names found there. */
    private static void migrateLegacy(File root, Set<String> on) { /*T*/ final long $t = rally3d.core.Trace.in(330); try {
        File old = rally3d.desktop.Platform.legacyAddonsDir();
        if (old == null || !old.isDirectory() || old.equals(root)) return;
        File[] files = old.listFiles();
        if (files == null) return;
        int moved = 0;
        for (File f : files) {
            if (!isZip(f)) continue;
            if (moveInto(f, root, f.getName()) != null) moved++;
        }
        Set<String> legacyOn = readEnabled(old);
        on.addAll(legacyOn);
        files = old.listFiles();
        boolean left = false;
        if (files != null) for (File f : files) { String n = f.getName(); if (n.equals("enabled.txt") || n.startsWith("enabled.txt.")) { f.delete(); } else left = true; }
        if (!left) old.delete();
        if (moved > 0) RLog.i("addon", "migrated " + moved + " zip(s) from " + old + " to " + root);
     } finally { rally3d.core.Trace.out(330, $t); } }

    private static void deleteStale(File dir) { /*T*/ final long $t = rally3d.core.Trace.in(331); try {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) if (f.isFile() && (f.getName().endsWith(".part") || f.getName().endsWith(".tmp")) && !f.getName().startsWith("enabled.txt")) f.delete();
     } finally { rally3d.core.Trace.out(331, $t); } }

    /** Re-read the folders (zips dropped in by hand show up, removed ones disappear, misplaced ones are sorted). Keeps parsed packs whose file is unchanged. */
    static synchronized void rescan() { /*T*/ final long $t = rally3d.core.Trace.in(262); try {
        File d = ensureDirs();
        if (d == null) return;
        Set<String> on = readEnabled(d);
        migrateLegacy(d, on);
        deleteStale(d);
        for (int i = 0; i < KIND_DIRS.length; i++) deleteStale(new File(d, KIND_DIRS[i]));
        // candidates: root (fresh imports / legacy) + the three sub-folders
        ArrayList<File> cands = new ArrayList<File>();
        File[] top = d.listFiles();
        if (top != null) { Arrays.sort(top); for (File f : top) if (isZip(f)) cands.add(f); }
        for (int k = 0; k < KIND_DIRS.length; k++) {
            File[] files = new File(d, KIND_DIRS[k]).listFiles();
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) if (isZip(f)) cands.add(f);
        }
        boolean enabledDirty = false;
        ArrayList<AddonPack> fresh = new ArrayList<AddonPack>();
        for (File f : cands) {
            File parent = f.getParentFile();
            boolean atRoot = parent != null && parent.equals(d);
            String folder = atRoot ? "" : parent.getName();
            String rel = atRoot ? f.getName() : folder + "/" + f.getName();
            AddonPack p = null;
            for (AddonPack o : packs) if (rel.equals(o.file) && o.zip != null && o.zip.length == f.length() && o.mtime == f.lastModified()) { p = o; break; }
            if (p == null) {
                try { p = AddonPack.parse(readAll(f), rel); p.mtime = f.lastModified(); }
                catch (IOException e) { RLog.caught("AddonManager.rescan", e); p = new AddonPack(); p.file = rel; p.name = f.getName(); p.error = String.valueOf(e.getMessage()); p.sha = new byte[16]; p.mtime = f.lastModified(); }
            }
            // sort into the right folder: valid packs by type; broken ones only leave the root (they stay in the folder the user put them in)
            String want = p.error == null ? KIND_DIRS[kindOf(p)] : (atRoot ? KIND_DIRS[1] : folder);
            if (!want.equals(folder)) {
                File moved = moveInto(f, new File(d, want), f.getName());
                if (moved != null) {
                    String newRel = want + "/" + moved.getName();
                    RLog.i("addon", "sorted " + rel + " -> " + newRel);
                    if (on.contains(rel)) { on.add(newRel); }
                    on.remove(rel);
                    enabledDirty = true;
                    p.file = newRel;
                    p.mtime = moved.lastModified();
                    rel = newRel;
                }
            }
            p.enabled = on.contains(rel) && p.error == null;
            fresh.add(p);
        }
        boolean changed = fresh.size() != packs.size();
        if (!changed) for (int i = 0; i < fresh.size(); i++) if (fresh.get(i) != packs.get(i)) { changed = true; break; }
        if (!changed) {   // same objects: compare against what was enabled before this scan
            for (AddonPack p : fresh) if (p.enabled != p.wasEnabled) { changed = true; break; }
        }
        for (AddonPack p : fresh) p.wasEnabled = p.enabled;
        // one wheels pack at a time (enabled.txt edited by hand)
        boolean seenWheels = false;
        for (AddonPack p : fresh) if (p.enabled && p.wheelsOnly) { if (seenWheels) { p.enabled = false; p.wasEnabled = false; enabledDirty = true; changed = true; } seenWheels = true; }
        packs.clear();
        packs.addAll(fresh);
        if (changed) revision++;
        if (enabledDirty) saveEnabled();
        RLog.i("addon", "scan: " + packs.size() + " zip(s), " + enabledCars().size() + " car(s) enabled, folder " + d);
     } finally { rally3d.core.Trace.out(262, $t); } }

    static synchronized void setEnabled(AddonPack p, boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(263); try {
        if (p == null || p.error != null) return;
        p.enabled = on;
        p.wasEnabled = on;
        if (on && p.wheelsOnly) for (AddonPack o : packs) if (o != p && o.wheelsOnly && o.enabled) { o.enabled = false; o.wasEnabled = false; }   // one wheels pack at a time
        revision++;
        saveEnabled();
        RLog.i("addon", (on ? "ENABLED " : "disabled ") + p.file + " (" + p.name + ")");
     } finally { rally3d.core.Trace.out(263, $t); } }

    /** Deletes the zip from disk (and its enabled entry). Returns false when the file could not be removed (it then stays in the list). */
    static synchronized boolean delete(AddonPack p) { /*T*/ final long $t = rally3d.core.Trace.in(264); try {
        File d = ensureDirs();
        if (d == null || p == null || p.file == null) return false;
        File f = new File(d, p.file);
        boolean ok = true;
        if (f.exists()) {
            ok = f.delete();
            if (!ok) {
                try { Files.deleteIfExists(f.toPath()); ok = !f.exists(); }
                catch (IOException e) { RLog.caught("AddonManager.delete", e); RLog.e("addon", "cannot delete " + f + ": " + e); ok = false; }
            }
        }
        new File(f.getPath() + ".part").delete();
        if (ok) {
            p.enabled = false;
            p.wasEnabled = false;
            packs.remove(p);
            revision++;
            saveEnabled();
        }
        RLog.i("addon", "deleted " + p.file + " ok=" + ok);
        return ok;
     } finally { rally3d.core.Trace.out(264, $t); } }

    /** Cars that appear in the car-select screen, in a stable order. */
    /** Copy of the pack list for code outside this class (the list itself is only safe to touch under this class's lock). */
    static synchronized ArrayList<AddonPack> snapshot() { return new ArrayList<AddonPack>(packs); }

    static synchronized List<AddonPack> enabledCars() { /*T*/ final long $t = rally3d.core.Trace.in(265); try {
        ArrayList<AddonPack> out = new ArrayList<AddonPack>();
        for (AddonPack p : packs) if (p.enabled && p.error == null && !p.wheelsOnly && !p.mapOnly && out.size() < MAX_LOCAL_CARS) out.add(p);
        return out;
     } finally { rally3d.core.Trace.out(265, $t); } }

    /** Wheel pack override (type=wheels), first enabled wins. It is also sent to the other players in multiplayer. */
    static synchronized AddonPack enabledWheels() {
        for (AddonPack p : packs) if (p.enabled && p.error == null && p.wheelsOnly) return p;
        return null;
    }

    /** Enabled custom-map packs (type=map). Placeholder: the track loader will read these later. */
    static synchronized List<AddonPack> enabledMaps() { /*T*/ final long $t = rally3d.core.Trace.in(295); try {
        ArrayList<AddonPack> out = new ArrayList<AddonPack>();
        for (AddonPack p : packs) if (p.enabled && p.error == null && p.mapOnly) out.add(p);
        return out;
     } finally { rally3d.core.Trace.out(295, $t); } }

    /** Packs of one tab: 0 = maps, 1 = cars (incl. skins), 2 = wheels. Broken zips show up in the tab of the folder they sit in. */
    static synchronized List<AddonPack> ofKind(int kind) { /*T*/ final long $t = rally3d.core.Trace.in(296); try {
        ArrayList<AddonPack> out = new ArrayList<AddonPack>();
        for (AddonPack p : packs) if (kindOf(p) == kind) out.add(p);
        return out;
     } finally { rally3d.core.Trace.out(296, $t); } }

    static int kindOf(AddonPack p) { /*T*/ final long $t = rally3d.core.Trace.in(332); try {
        if (p.error != null && p.file != null) {
            if (p.file.startsWith("maps/")) return 0;
            if (p.file.startsWith("wheels/")) return 2;
            return 1;
        }
        return p.mapOnly ? 0 : p.wheelsOnly ? 2 : 1;
     } finally { rally3d.core.Trace.out(332, $t); } }
}
