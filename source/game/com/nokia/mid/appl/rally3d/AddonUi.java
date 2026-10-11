package com.nokia.mid.appl.rally3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.microedition.lcdui.Graphics;
import rally3d.core.RLog;

/**
 * Add-ons screen ("Add-ons"): three tabs on top (Maps / Cars / Wheels), a scrollable list under them.
 * Tap a row = expand its details (author, stats, file, size) with an ON/OFF button; the [X] button next to every row
 * deletes the add-on completely (zip file + enabled flag) after a confirmation. Bottom bar: "+ Zip" and "Back".
 * Maps tab: custom maps (type=map, made in the Map Editor or dropped in as zips) are listed like everything else; enabled ones
 * show up first in the track menus (MapLibrary.menuMaps()).
 */
final class AddonUi {
    static final int TAB_MAPS = 0, TAB_CARS = 1, TAB_WHEELS = 2;
    // original game palette only: pale yellow text (-86), grey for disabled (-4737097), white frames, black shadow
    private static final int YEL = 0xFFFFFFAA, DIM = 0xFFB7B7B7, OK = YEL, OFF = DIM, BAD = YEL;
    private static final int PANEL = 0x55FFFFFF, PANEL_SEL = 0x99FFFFFF, LINE = 0xFFFFFFAA, WHITE = 0xFFFFFFFF;
    private static final String[] TAB_NAMES = {"MAPS", "CARS", "WHEELS"};

    private final RallyGame g;
    private int tab = TAB_CARS;
    private int[] scroll = new int[3];        // per tab, pixels
    private int[] sel = new int[3];           // per tab, selected row (keyboard)
    private String[] open = new String[3];    // per tab, file of the expanded row
    private List<AddonPack> rows = new ArrayList<AddonPack>();
    private int contentH;
    private int fh, sh;                       // bold / small font heights

    // hit boxes of the last drawn frame (x, y, w, h), screen coordinates
    private final int[][] tabBox = new int[3][4];
    private final ArrayList<int[]> rowBox = new ArrayList<int[]>();   // {x,y,w,h,index}
    private final ArrayList<int[]> toggleBox = new ArrayList<int[]>();
    private final ArrayList<int[]> deleteBox = new ArrayList<int[]>();
    private final ArrayList<int[]> offBtnBox = new ArrayList<int[]>(); // big ON/OFF button inside an expanded row
    private int[] helpBox = new int[4];
    private int[] addBox = new int[4], backBox = new int[4];
    private int listTop, listBottom;
    private String toast;
    private long toastUntil;

    AddonUi(RallyGame game) { this.g = game; }

    // ------------------------------------------------------------------ loop

    void run() { /*T*/ final long $t = rally3d.core.Trace.in(301); try {
        int screenBefore = g.screenMode;
        g.beginMenu("Add-ons");
        g.menuHeaderStatic = false;
        g.menuSlide = 0;
        g.screenMode = 2;
        g.addonUi = this;
        g.snapshotKeys();
        int[] ptr = new int[4];
        g.pollPointerEvents(ptr);   // drop clicks made before the screen opened
        try {
            reload();
            while (true) {
                g.redrawBackground = true;
                g.arrowBlink = rally3d.desktop.Platform.areUiAnimationsEnabled() ? (g.arrowBlink + 1) % 6 : 0;
                g.renderFrame();
                int keys = g.pollNewKeys();
                int flags = g.pollPointerEvents(ptr);
                int wheel = (flags & 4) != 0 ? g.consumePointerWheel() : 0;
                if (wheel != 0) scrollBy(wheel * (fh + 2));
                if ((flags & 2) != 0) {
                    int r = click(ptr[2], ptr[3]);
                    if (r == -1) return;
                } else if ((flags & 1) != 0) {
                    hover(ptr[0], ptr[1]);
                }
                if (keys != 0) {
                    int r = key(keys);
                    if (r == -1) return;
                }
                if (g.paused) { g.paused = false; g.clearKeys(); }
            }
        } finally {
            g.addonUi = null;
            g.screenMode = screenBefore;
            g.redrawBackground = true;
        }
     } finally { rally3d.core.Trace.out(301, $t); } }

    private void reload() { /*T*/ final long $t = rally3d.core.Trace.in(302); try {
        AddonManager.rescan();
        rows = AddonManager.ofKind(tab);
        if (sel[tab] >= rows.size()) sel[tab] = Math.max(0, rows.size() - 1);
        clampScroll();
     } finally { rally3d.core.Trace.out(302, $t); } }

    private void switchTab(int t) {
        if (t < 0) t = 2;
        if (t > 2) t = 0;
        tab = t;
        reload();
    }

    // ------------------------------------------------------------------ input

    private void hover(int x, int y) {
        int[] b = hit(rowBox, x, y);
        if (b != null) sel[tab] = b[4];
    }

    private static int[] hit(List<int[]> boxes, int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(303); try {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            int[] b = boxes.get(i);
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) return b;
        }
        return null;
     } finally { rally3d.core.Trace.out(303, $t); } }

    private static boolean in(int[] b, int x, int y) { return x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]; }

    /** @return -1 to leave the screen */
    private int click(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(304); try {
        for (int i = 0; i < 3; i++) if (in(tabBox[i], x, y)) { if (tab != i) switchTab(i); return 0; }
        if (in(backBox, x, y)) return -1;
        if (in(addBox, x, y)) { addZip(); return 0; }
        if (y < listTop || y >= listBottom) return 0;
        int[] b;
        if ((b = hit(deleteBox, x, y)) != null) { sel[tab] = b[4]; deleteRow(b[4]); return 0; }
        if ((b = hit(offBtnBox, x, y)) != null) { sel[tab] = b[4]; toggleRow(b[4]); return 0; }
        if ((b = hit(toggleBox, x, y)) != null) { sel[tab] = b[4]; toggleRow(b[4]); return 0; }
        if ((b = hit(rowBox, x, y)) != null) { sel[tab] = b[4]; expandRow(b[4]); return 0; }
        if (in(helpBox, x, y)) { help(); return 0; }
        return 0;
     } finally { rally3d.core.Trace.out(304, $t); } }

    private int key(int k) { /*T*/ final long $t = rally3d.core.Trace.in(305); try {
        if ((k & 0x200) != 0) return -1;                 // back / Esc
        if ((k & 4) != 0) switchTab(tab - 1);            // left
        else if ((k & 0x20) != 0) switchTab(tab + 1);    // right
        else if ((k & 2) != 0 && sel[tab] > 0) { sel[tab]--; ensureVisible(); }       // up
        else if ((k & 0x40) != 0 && sel[tab] < rows.size() - 1) { sel[tab]++; ensureVisible(); }   // down
        else if ((k & 0x100) != 0 && !rows.isEmpty()) {
            AddonPack p = rows.get(sel[tab]);
            if (p.file != null && p.file.equals(open[tab])) toggleRow(sel[tab]); else expandRow(sel[tab]);
        }
        return 0;
     } finally { rally3d.core.Trace.out(305, $t); } }

    private void expandRow(int i) { /*T*/ final long $t = rally3d.core.Trace.in(306); try {
        if (i < 0 || i >= rows.size()) return;
        String f = rows.get(i).file;
        open[tab] = f != null && f.equals(open[tab]) ? null : f;
     } finally { rally3d.core.Trace.out(306, $t); } }

    private void toggleRow(int i) { /*T*/ final long $t = rally3d.core.Trace.in(307); try {
        if (i < 0 || i >= rows.size()) return;
        AddonPack p = rows.get(i);
        if (p.error != null) { toast("Error: " + p.error); return; }
        AddonManager.setEnabled(p, !p.enabled);
        g.refreshLocalCars();
        if (p.mapOnly) toast(p.enabled ? "Map on: it is first in the track lists" : "Map off");
     } finally { rally3d.core.Trace.out(307, $t); } }

    private void deleteRow(int i) { /*T*/ final long $t = rally3d.core.Trace.in(308); try {
        if (i < 0 || i >= rows.size()) return;
        AddonPack p = rows.get(i);
        g.addonUi = null;   // the confirm dialog is a regular menu: drop our overlay while it is up
        boolean yes;
        try { yes = g.confirm("Delete \"" + p.name + "\" completely?"); }
        finally { g.addonUi = this; g.screenMode = 2; }
        g.redrawBackground = true;
        if (!yes) return;
        boolean ok = AddonManager.delete(p);
        g.refreshLocalCars();
        g.evictUnusedAddonCars();
        RLog.i("addon", "ui delete '" + p.name + "' ok=" + ok);
        if (!ok) toast("Could not delete file " + p.file);
        open[tab] = null;
        reload();
     } finally { rally3d.core.Trace.out(308, $t); } }

    private void addZip() { /*T*/ final long $t = rally3d.core.Trace.in(309); try {
        HashSet<String> known = new HashSet<String>();
        for (AddonPack p : AddonManager.packs) known.add(p.file);
        int added = rally3d.desktop.Platform.importAddons();
        RLog.i("addon", "import: " + added + " zip(s) added");
        AddonManager.rescan();
        for (AddonPack p : new ArrayList<AddonPack>(AddonManager.packs)) {   // a freshly added valid pack goes on right away
            if (p.error == null && !p.enabled && p.file != null && !known.contains(p.file)) {
                AddonManager.setEnabled(p, true);
                tab = AddonManager.kindOf(p);   // jump to the tab where the new pack lives
                open[tab] = p.file;
            }
        }
        g.refreshLocalCars();
        reload();
        g.snapshotKeys();
     } finally { rally3d.core.Trace.out(309, $t); } }

    private void help() { /*T*/ final long $t = rally3d.core.Trace.in(310); try {
        g.addonUi = null;
        try {
            g.showTextScreen("How to make an add-on", "An add-on is a zip archive. Put it into the add-ons folder:|" + shownDir()
                    + "|or tap \"+ ZIP\".||Inside the zip:|addon.properties - name=, author=, description=, type=car|wheels|map, base=1..4, speed=, accel=, steer=, steerMax=, grip=|car.syn - your own 3D model (optional)|car.png - car skin (side 16/32/64/128/256..., max 1024)|wheel.png - wheel skin (same), wheel.syn - wheel model||type=wheels - wheels only: fitted to any of your cars, one wheels pack at a time, visible in multiplayer too.|type=map - custom map (PLACEHOLDER: the pack is listed in the MAPS tab, but the game cannot load it yet).||An enabled car appears in the car select screen. In multiplayer everybody sees it: the zip is sent to the opponent automatically.", 0L, 0);
        } finally { g.addonUi = this; g.screenMode = 2; g.redrawBackground = true; g.snapshotKeys(); }
     } finally { rally3d.core.Trace.out(310, $t); } }

    /** Add-ons folder for display: the user's home directory is shown as "~" so no account name leaks into screenshots. */
    private static String shownDir() { /*T*/ final long $t = rally3d.core.Trace.in(327); try {
        String d = String.valueOf(AddonManager.dir());
        String home = System.getProperty("user.home");
        if (home != null && home.length() > 1 && d.startsWith(home)) d = "~" + d.substring(home.length());
        return d.replace('\\', '/');
     } finally { rally3d.core.Trace.out(327, $t); } }

    private void toast(String t) { toast = t; toastUntil = System.currentTimeMillis() + 3500L; }

    // ------------------------------------------------------------------ scrolling

    private void scrollBy(int px) { scroll[tab] += px; clampScroll(); }

    private void clampScroll() { /*T*/ final long $t = rally3d.core.Trace.in(311); try {
        int view = Math.max(0, listBottom - listTop);
        int max = Math.max(0, contentH - view);
        if (scroll[tab] > max) scroll[tab] = max;
        if (scroll[tab] < 0) scroll[tab] = 0;
     } finally { rally3d.core.Trace.out(311, $t); } }

    private void ensureVisible() { /*T*/ final long $t = rally3d.core.Trace.in(312); try {
        int y = 0, i = 0;
        for (; i < sel[tab] && i < rows.size(); i++) y += rowHeight(rows.get(i));
        int h = sel[tab] < rows.size() ? rowHeight(rows.get(sel[tab])) : 0;
        int view = listBottom - listTop;
        if (y < scroll[tab]) scroll[tab] = y;
        else if (y + h > scroll[tab] + view) scroll[tab] = y + h - view;
        clampScroll();
     } finally { rally3d.core.Trace.out(312, $t); } }

    // ------------------------------------------------------------------ layout helpers

    private boolean isOpen(AddonPack p) { return p.file != null && p.file.equals(open[tab]); }

    private int baseRowH() { return fh + sh + 8; }

    private int speedOf(AddonPack p) { /*T*/ final long $t = rally3d.core.Trace.in(323); try {
        if (p.speed > 0) return p.speed;
        int b = Math.max(0, Math.min(3, p.base));
        return RallyGame.statSpeed[b];
     } finally { rally3d.core.Trace.out(323, $t); } }

    /** Detail text of an expanded row, word-wrapped to the row width. */
    private List<String> detail(AddonPack p) { /*T*/ final long $t = rally3d.core.Trace.in(324); try {
        ArrayList<String> raw = new ArrayList<String>();
        if (p.error != null) raw.add("Error: " + p.error);
        if (p.author.length() > 0) raw.add("Author: " + p.author);
        if (p.desc.length() > 0) raw.add(p.desc);
        if (p.error == null) {
            if (p.mapOnly) raw.add("Custom map" + (p.mapBin != null ? " (" + (p.mapBin.length - 4) / 20 + " pieces)" : "") + ". When on it is listed first in Time Trial, Ghost Race and Multiplayer. Edit it in Map Editor > File > Open.");
            else if (p.wheelsOnly) raw.add("Wheels: " + (p.wheelSyn != null ? "model" : "") + (p.wheelSyn != null && p.wheelPng != null ? " + " : "") + (p.wheelPng != null ? "skin" : "") + ". One wheels pack at a time.");
            else {
                raw.add("Base: car " + (p.base + 1) + (p.syn != null ? ", own model" : "") + (p.skin != null ? ", own skin" : ""));
                raw.add("Speed " + speedOf(p) + "  accel x" + f1(p.accel));
                raw.add("Steer x" + f1(p.steer) + "  grip x" + f1(p.grip));
            }
        }
        raw.add("File: " + p.file + (p.zip != null ? " (" + (p.zip.length / 1024 + 1) + " KB)" : ""));
        ArrayList<String> out = new ArrayList<String>();
        int maxW = g.width - 6 - 12;
        for (String line : raw) wrap(line, maxW, out);
        return out;
     } finally { rally3d.core.Trace.out(324, $t); } }

    private void wrap(String text, int maxW, List<String> out) { /*T*/ final long $t = rally3d.core.Trace.in(325); try {
        javax.microedition.lcdui.Font f = g.fontSmall;
        String cur = "";
        for (String word : text.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (f.stringWidth(t) <= maxW) { cur = t; continue; }
            if (cur.length() > 0) out.add(cur);
            while (f.stringWidth(word) > maxW && word.length() > 1) {   // very long token (file names): hard split
                int k = word.length() - 1;
                while (k > 1 && f.stringWidth(word.substring(0, k)) > maxW) k--;
                out.add(word.substring(0, k));
                word = word.substring(k);
            }
            cur = word;
        }
        if (cur.length() > 0) out.add(cur);
     } finally { rally3d.core.Trace.out(325, $t); } }

    private int rowHeight(AddonPack p) { /*T*/ final long $t = rally3d.core.Trace.in(326); try {
        int h = baseRowH() + 3;
        if (isOpen(p)) h += detail(p).size() * (sh + 1) + (p.error == null ? fh + 10 : 0) + 6;
        return h;
     } finally { rally3d.core.Trace.out(326, $t); } }

    // ------------------------------------------------------------------ drawing

    void draw(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(314); try {
        fh = g.fontBoldHeight;
        sh = g.fontSmallHeight;
        int W = g.width, H = g.height, B = g.barHeight;
        rowBox.clear(); toggleBox.clear(); deleteBox.clear(); offBtnBox.clear();

        // header / footer bars like every other menu of the game
        g.drawWide(gr, g.titleUpImage, 0);
        g.drawWide(gr, g.titleDownImage, H - B);
        gr.setFont(g.fontBold);
        g.drawText(gr, 0, (B - fh) / 2, "Add-ons", -86, 5);

        // tabs: widths follow the label widths so "WHEELS" / "CARS" are never cut
        int tabY = B + 2, tabH = fh + 6, gap = 2;
        int[] lw = new int[3];
        int sum = 0;
        for (int i = 0; i < 3; i++) { lw[i] = g.fontBold.stringWidth(TAB_NAMES[i]); sum += lw[i]; }
        int pad = Math.max(2, (W - 4 - gap * 2 - sum) / 3);
        int x = 2;
        for (int i = 0; i < 3; i++) {
            int tw = lw[i] + pad;
            if (i == 2) tw = W - 2 - x;
            tabBox[i][0] = x; tabBox[i][1] = tabY; tabBox[i][2] = tw; tabBox[i][3] = tabH;
            boolean act = i == tab;
            panel(gr, x, tabY, tw, tabH, act ? PANEL_SEL : PANEL, act ? WHITE : 0xFFB7B7B7);
            gr.setFont(g.fontBold);
            int lx0 = x + (tw - lw[i]) / 2;
            txt(gr, TAB_NAMES[i], lx0, tabY + 3, act ? YEL : DIM);
            // little dot when the tab has something switched on
            for (AddonPack q : AddonManager.ofKind(i)) if (q.enabled) { gr.setColor(YEL); gr.fillRect(x + tw - 5, tabY + 2, 3, 3); break; }
            x += tw + gap;
        }

        listTop = tabY + tabH + 3;
        listBottom = H - B - 2;

        // bottom bar: "+ ZIP" (left) and "Back" (right)
        int fy = H - B + (B - (fh + 2)) / 2;
        addBox[0] = 0; addBox[1] = H - B; addBox[2] = W / 2; addBox[3] = B;
        backBox[0] = W / 2; backBox[1] = H - B; backBox[2] = W - W / 2; backBox[3] = B;
        gr.setFont(g.fontBold);
        g.drawText(gr, 6, fy, "+ ZIP", -86, 1);
        g.drawText(gr, 4, fy, Texts.back, -86, 9);

        // list
        gr.setClip(0, listTop, W, Math.max(0, listBottom - listTop));
        int y = listTop - scroll[tab];
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            AddonPack p = rows.get(i);
            int h = rowHeight(p);
            if (y + h > listTop && y < listBottom) drawRow(gr, p, i, y, h, W);
            y += h; total += h;
        }
        if (tab == TAB_MAPS) {
            int ph = drawMapPlaceholders(gr, y, W);
            y += ph; total += ph;
        } else if (rows.isEmpty()) {
            int ph = drawEmpty(gr, y, W);
            y += ph; total += ph;
        }
        // help link
        int hh = fh + 8;
        helpBox[0] = 2; helpBox[1] = y + 2; helpBox[2] = W - 4; helpBox[3] = hh;
        panel(gr, 2, y + 2, W - 4, hh, PANEL, 0xFFB7B7B7);
        gr.setFont(g.fontBold);
        g.drawText(gr, 0, y + 2 + 4, "? How to make an add-on", YEL, 5);
        y += hh + 4; total += hh + 4;
        contentH = total;
        clampScroll();

        // scroll bar
        int view = listBottom - listTop;
        if (contentH > view) {
            int bh = Math.max(8, view * view / contentH);
            int by = listTop + (view - bh) * scroll[tab] / Math.max(1, contentH - view);
            gr.fillRectAlpha(W - 4, listTop, 4, view, PANEL);
            gr.setColor(0xFF000000);
            gr.fillRect(W - 4, by, 4, bh);
            gr.setColor(YEL);
            gr.fillRect(W - 3, by + 1, 2, Math.max(1, bh - 2));
        }
        gr.setClip(0, 0, W, H);

        // toast
        if (toast != null) {
            if (System.currentTimeMillis() > toastUntil) toast = null;
            else {
                gr.setFont(g.fontSmall);
                int tw2 = Math.min(W - 8, g.fontSmall.stringWidth(toast) + 10);
                int tx = (W - tw2) / 2, ty = listBottom - sh - 10;
                panel(gr, tx, ty, tw2, sh + 6, PANEL, WHITE);
                txt(gr, clipTo(toast, tw2 - 6, g.fontSmall), tx + 3, ty + 3, YEL);
            }
        }
     } finally { rally3d.core.Trace.out(314, $t); } }

    /** Vanilla panel (same look as RallyGame.drawPanel: translucent white, white frame, black outer frame) that keeps the current clip. */
    private static void panel(Graphics gr, int x, int y, int w, int h, int fill, int frame) {
        gr.fillRectAlpha(x, y, w, h, fill);
        box(gr, x, y, w, h, frame);
        box(gr, x - 1, y - 1, w + 2, h + 2, 0xFF000000);
    }

    /** Shadowed text like everywhere else in the game (drawText flag 1 = black shadow). */
    private void txt(Graphics gr, String s, int x, int y, int color) { g.drawText(gr, x, y, s, color, 1); }

    private static void box(Graphics gr, int x, int y, int w, int h, int color) { /*T*/ final long $t = rally3d.core.Trace.in(315); try {
        gr.setColor(color);
        gr.fillRect(x, y, w, 1);
        gr.fillRect(x, y + h - 1, w, 1);
        gr.fillRect(x, y, 1, h);
        gr.fillRect(x + w - 1, y, 1, h);
     } finally { rally3d.core.Trace.out(315, $t); } }

    private static String clipTo(String s, int w, javax.microedition.lcdui.Font f) { /*T*/ final long $t = rally3d.core.Trace.in(316); try {
        if (f.stringWidth(s) <= w) return s;
        while (s.length() > 1 && f.stringWidth(s + "..") > w) s = s.substring(0, s.length() - 1);
        return s + "..";
     } finally { rally3d.core.Trace.out(316, $t); } }

    private void drawRow(Graphics gr, AddonPack p, int idx, int y, int h, int W) { /*T*/ final long $t = rally3d.core.Trace.in(317); try {
        int x = 2, w = W - 6, bh = baseRowH();
        boolean selected = idx == sel[tab];
        panel(gr, x, y, w, h - 3, selected ? PANEL_SEL : PANEL, WHITE);
        rowBox.add(new int[] {x, y, w, h - 3, idx});

        int cb = fh + 2;                         // square size: status box (left) and delete button (right)
        int cy = y + (bh - cb) / 2;
        // keyboard cursor: the blinking arrow of the original menus
        int arrowPad = g.arrowRight != null && g.arrowRight[0] != null ? g.arrowWidth + 2 : 0;
        if (selected && arrowPad > 0) {
            gr.drawImage(g.arrowRight[g.arrowBlink < 3 ? 1 : 0], x + 1, cy + (cb - g.arrowRight[0].getHeight()) / 2, 0);
        }
        // status box: filled yellow = on, empty grey = off, "!" = broken
        int sx = x + 4 + arrowPad;
        int col = p.error != null ? BAD : p.enabled ? OK : OFF;
        gr.setColor(0xFF000000);
        gr.fillRect(sx, cy, cb, cb);
        box(gr, sx, cy, cb, cb, p.enabled || p.error != null ? WHITE : DIM);
        if (p.enabled && p.error == null) { gr.setColor(col); gr.fillRect(sx + 3, cy + 3, cb - 6, cb - 6); }
        else if (p.error != null) {
            gr.setFont(g.fontBold);
            txt(gr, "!", sx + (cb - g.fontBold.stringWidth("!")) / 2, cy + 1, YEL);
        }
        toggleBox.add(new int[] {sx - 2, y, cb + 6, bh, idx});

        // delete button (right)
        int dx = x + w - cb - 4;
        gr.setColor(0xFF000000);
        gr.fillRect(dx, cy, cb, cb);
        box(gr, dx, cy, cb, cb, WHITE);
        gr.setColor(YEL);
        gr.drawLine(dx + 4, cy + 4, dx + cb - 5, cy + cb - 5);
        gr.drawLine(dx + 5, cy + 4, dx + cb - 4, cy + cb - 5);
        gr.drawLine(dx + cb - 5, cy + 4, dx + 4, cy + cb - 5);
        gr.drawLine(dx + cb - 4, cy + 4, dx + 5, cy + cb - 5);
        deleteBox.add(new int[] {dx - 3, y, cb + 7, bh, idx});

        // texts
        int tx = sx + cb + 6, tmax = dx - 4 - tx;
        gr.setFont(g.fontBold);
        String name = clipTo(p.name, tmax, g.fontBold);
        txt(gr, name, tx, y + 3, p.enabled || p.error != null ? YEL : DIM);
        gr.setFont(g.fontSmall);
        String sub = p.error != null ? "! error - tap for details" : subtitle(p);
        txt(gr, clipTo(sub, tmax, g.fontSmall), tx, y + 3 + fh + 1, p.enabled ? WHITE : DIM);

        if (!isOpen(p)) return;
        // details
        int dy = y + bh;
        gr.setColor(0xFF000000);
        gr.fillRect(x + 4, dy - 2, w - 8, 1);
        gr.setColor(WHITE);
        gr.fillRect(x + 4, dy - 1, w - 8, 1);
        gr.setFont(g.fontSmall);
        int lx = x + 6, lw = w - 12;
        List<String> lines = detail(p);
        int ly = dy;
        for (int i = 0; i < lines.size(); i++) {
            txt(gr, lines.get(i), lx, ly + 1, WHITE);
            ly += sh + 1;
        }
        // big ON / OFF button
        int by = y + h - 3 - (fh + 10) - 3, bw = w - 12;
        if (p.error == null) {
            boolean on = p.enabled;
            panel(gr, lx, by, bw, fh + 8, on ? PANEL : PANEL_SEL, WHITE);
            gr.setFont(g.fontBold);
            String lab = on ? "TURN OFF" : "TURN ON";
            txt(gr, lab, lx + (bw - g.fontBold.stringWidth(lab)) / 2, by + 4, YEL);
            offBtnBox.add(new int[] {lx, by, bw, fh + 8, idx});
        }
     } finally { rally3d.core.Trace.out(317, $t); } }

    private static String f1(float v) { return String.valueOf(Math.round(v * 10f) / 10f); }

    private String subtitle(AddonPack p) { /*T*/ final long $t = rally3d.core.Trace.in(318); try {
        String state = p.enabled ? "ON" : "OFF";
        if (p.mapOnly) return state + "  map" + (p.author.length() > 0 ? "  " + p.author : "");
        if (p.wheelsOnly) return state + "  wheels" + (p.author.length() > 0 ? "  " + p.author : "");
        String s = state + "  speed " + speedOf(p);
        if (p.author.length() > 0) s += "  " + p.author;
        return s;
     } finally { rally3d.core.Trace.out(318, $t); } }

    /** Placeholders for future custom maps: empty dashed slots + a short hint. @return height used */
    private int drawMapPlaceholders(Graphics gr, int y, int W) { /*T*/ final long $t = rally3d.core.Trace.in(319); try {
        int x = 2, w = W - 6, used = 0;
        int slots = Math.max(1, 3 - rows.size());
        int sh2 = baseRowH() + 3;
        for (int i = 0; i < slots; i++) {
            int yy = y + used;
            panel(gr, x, yy, w, sh2 - 3, PANEL, WHITE);
            gr.setFont(g.fontBold);
            g.drawText(gr, 0, yy + 3, "[ empty slot: map ]", DIM, 5);
            gr.setFont(g.fontSmall);
            g.drawText(gr, 0, yy + 3 + fh + 1, "custom maps will appear here", DIM, 5);
            used += sh2;
        }
        return used;
     } finally { rally3d.core.Trace.out(319, $t); } }

    private int drawEmpty(Graphics gr, int y, int W) { /*T*/ final long $t = rally3d.core.Trace.in(320); try {
        int x = 2, w = W - 6, h = baseRowH() + 3;
        panel(gr, x, y, w, h - 3, PANEL, WHITE);
        gr.setFont(g.fontBold);
        g.drawText(gr, 0, y + 3, tab == TAB_WHEELS ? "[ no wheels ]" : "[ no cars ]", DIM, 5);
        gr.setFont(g.fontSmall);
        g.drawText(gr, 0, y + 3 + fh + 1, "tap + ZIP at the bottom left", DIM, 5);
        return h;
     } finally { rally3d.core.Trace.out(320, $t); } }
}
