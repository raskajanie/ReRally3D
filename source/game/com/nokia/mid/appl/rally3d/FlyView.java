package com.nokia.mid.appl.rally3d;

import javax.microedition.lcdui.Graphics;
import rally3d.core.RLog;

/**
 * Free-fly preview of a map (map editor -> FLY): the real game renderer, but the camera is a free "noclip" camera.
 * PC: W A S D move, Space / E up, Q / C down, Shift fast, Ctrl slow, drag the mouse (or arrow keys) to look, wheel or + / - = speed,
 * Home / End = start / finish, R = back to where you began, Esc = leave.
 * Phone / touch: floating joystick on the left half (move), drag anywhere else to look, UP / DOWN buttons, FAST toggle,
 * a progress bar at the bottom (drag it to jump along the road) and an EXIT button. Both input styles work on both platforms.
 */
final class FlyView {
    private static final int B_EXIT = 1, B_SLOW = 2, B_FASTER = 3, B_START = 4, B_END = 5, B_UP = 6, B_DOWN = 7, B_BOOST = 8, B_HELP = 9, B_BAR = 10;
    private static final float LOOK_DEG_PER_PX = 0.32f;
    private static final float MAX_PITCH = 80f;

    private final RallyGame g;
    private final int startSeg;
    private float px, py, pz, yaw = 180f, pitch = 12f;
    private float speed = 70f;               // world units / second (one road segment is about 32 units)
    private boolean exit, boost, showHelp = true;
    private long helpUntil;
    private int seg;
    private final boolean[] held = new boolean[256];
    private final int[] ids = {-1, -1, -1, -1}, kind = new int[4], lx = new int[4], ly = new int[4], ox = new int[4], oy = new int[4], bt = new int[4];
    private final long[] downAt = new long[4];
    private float joyX, joyY;
    private boolean joyActive;
    private int joyCx, joyCy, joyR, joyKx, joyKy;
    private final int[][] rects = new int[12][];   // {x, y, w, h} per button id (null = not shown)
    private int W, H, sh, bh;
    private String toast;
    private long toastUntil;
    private final int[] ev = new int[5], kv = new int[3];

    FlyView(RallyGame game, int startSegment) { this.g = game; this.startSeg = startSegment; }

    // ------------------------------------------------------------------ loop

    void run() { /*T*/ final long $t = rally3d.core.Trace.in(505); try {
        int screenBefore = g.screenMode;
        g.flyView = this;
        javax.microedition.lcdui.Canvas.rawPointer = true;
        g.clearRaw();
        g.clearRawKeys();
        g.snapshotKeys();
        gotoSegment(Math.max(0, Math.min(g.segmentCount - 2, startSeg)), true);
        helpUntil = System.currentTimeMillis() + 9000L;
        long last = System.nanoTime();
        try {
            while (!exit) {
                long now = System.nanoTime();
                float dt = Math.min(0.1f, (now - last) / 1e9f);
                last = now;
                layout();
                while (g.pollRaw(ev)) pointer(ev[0], ev[1], ev[2], ev[3], ev[4]);
                int wheel = g.consumePointerWheel();
                if (wheel != 0) speed = clampSpeed(speed * (float) Math.pow(1.2, -wheel));
                while (g.pollRawKey(kv)) rawKey(kv[0], kv[1]);
                if ((g.pollNewKeys() & 0x200) != 0) exit = true;   // phone BACK key
                if (g.paused) { g.paused = false; g.clearKeys(); }
                update(dt);
                applyCamera();
                g.renderFrame();
            }
        } finally {
            g.flyView = null;
            javax.microedition.lcdui.Canvas.rawPointer = false;
            g.clearRaw();
            g.clearRawKeys();
            g.screenMode = screenBefore;
        }
     } finally { rally3d.core.Trace.out(505, $t); } }

    private static float clampSpeed(float s) { return Math.max(4f, Math.min(1200f, s)); }

    // ------------------------------------------------------------------ camera

    /** Puts the camera behind and above segment s, looking along the road. */
    void gotoSegment(int s, boolean resetAngles) { /*T*/ final long $t = rally3d.core.Trace.in(506); try {
        s = Math.max(0, Math.min(g.segmentCount - 2, s));
        TrackSegment a = g.segments[s], b = g.segments[Math.min(g.segmentCount - 1, s + 1)];
        float heading = 180.0f - (a.accumYaw + b.accumYaw) / 2.0f;
        double r = Math.toRadians(heading);
        px = a.startPos.v[0] - (float) Math.sin(r) * 36f;
        pz = a.startPos.v[2] + (float) Math.cos(r) * 36f;
        py = a.startPos.v[1] + 20f;
        if (resetAngles) { yaw = heading; pitch = 14f; }
        seg = s;
     } finally { rally3d.core.Trace.out(506, $t); } }

    private int nearestSegment() { /*T*/ final long $t = rally3d.core.Trace.in(507); try {
        int best = seg, last = g.segmentCount - 2;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i <= last; i++) {
            float dx = g.segments[i].startPos.v[0] - px, dz = g.segments[i].startPos.v[2] - pz;
            float d = dx * dx + dz * dz;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
     } finally { rally3d.core.Trace.out(507, $t); } }

    private void update(float dt) { /*T*/ final long $t = rally3d.core.Trace.in(508); try {
        float f = 0, r = 0, u = 0;
        if (held['W']) f += 1; if (held['S']) f -= 1;
        if (held['D']) r += 1; if (held['A']) r -= 1;
        if (held[' '] || held['E']) u += 1;
        if (held['Q'] || held['C']) u -= 1;
        if (joyActive) { f += -joyY; r += joyX; }
        if (rects[B_UP] != null && uiHeld(B_UP)) u += 1;
        if (rects[B_DOWN] != null && uiHeld(B_DOWN)) u -= 1;
        float turn = 95f * dt;
        if (held[37]) yaw -= turn; if (held[39]) yaw += turn;
        if (held[38]) pitch -= turn * 0.7f; if (held[40]) pitch += turn * 0.7f;
        pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
        yaw = (yaw % 360f + 360f) % 360f;
        float v = speed * (held[16] || boost || uiHeld(B_BOOST) ? 4f : 1f) * (held[17] ? 0.25f : 1f);
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        float cp = (float) Math.cos(pr), sp = (float) Math.sin(pr);
        float fx = (float) Math.sin(yr) * cp, fy = -sp, fz = -(float) Math.cos(yr) * cp;
        float rx = (float) Math.cos(yr), rz = (float) Math.sin(yr);
        f = Math.max(-1.5f, Math.min(1.5f, f)); r = Math.max(-1.5f, Math.min(1.5f, r)); u = Math.max(-1.5f, Math.min(1.5f, u));
        px += (fx * f + rx * r) * v * dt;
        py += (fy * f + u) * v * dt;
        pz += (fz * f + rz * r) * v * dt;
        py = Math.max(-2000f, Math.min(4000f, py));
        if (held[16] || boost) { /* speed boost is shown in the HUD */ }
     } finally { rally3d.core.Trace.out(508, $t); } }

    private void applyCamera() { /*T*/ final long $t = rally3d.core.Trace.in(509); try {
        seg = nearestSegment();
        CameraState cs = g.cameraState;
        cs.position.v[0] = px; cs.position.v[1] = py; cs.position.v[2] = pz;
        cs.yaw = yaw;
        cs.segmentIndex = seg;
        g.mouseCameraPitch = pitch;
        g.mouseYawOffset = 180f;   // makes drawWorld draw the road on both sides of the camera
        g.buildRoadWindow(seg);
     } finally { rally3d.core.Trace.out(509, $t); } }

    // ------------------------------------------------------------------ input

    private void rawKey(int code, int mods) { /*T*/ final long $t = rally3d.core.Trace.in(510); try {
        boolean release = (mods & 8) != 0;
        if (code < 0) { if (release) java.util.Arrays.fill(held, false); return; }
        if (release) { if (code < 256) held[code] = false; return; }
        if (code < 256) held[code] = true;
        showHelp = false;
        switch (code) {
            case 27: exit = true; break;
            case 'R': gotoSegment(startSeg, true); say("Back to the start view"); break;
            case 'F': boost = !boost; say(boost ? "Fast flight ON" : "Fast flight OFF"); break;
            case 'H': showHelp = true; helpUntil = System.currentTimeMillis() + 12000L; break;
            case 36: gotoSegment(MapData.START_SEG, true); say("Start line"); break;      // Home
            case 35: gotoSegment(Math.max(0, g.checkpoints[3] - 2), true); say("Finish line"); break;   // End
            case 33: gotoSegment(seg - 20, false); break;     // Page up / down: 20 segments along the road
            case 34: gotoSegment(seg + 20, false); break;
            case 61: case 107: case 521: speed = clampSpeed(speed * 1.25f); say("Speed " + (int) speed); break;
            case 45: case 109: speed = clampSpeed(speed / 1.25f); say("Speed " + (int) speed); break;
            default: break;
        }
     } finally { rally3d.core.Trace.out(510, $t); } }

    private int slotOf(int id) { for (int i = 0; i < 4; i++) if (ids[i] == id) return i; return -1; }

    private int buttonAt(int x, int y) { /*T*/ final long $t = rally3d.core.Trace.in(511); try {
        for (int b = 1; b < rects.length; b++) {
            int[] r = rects[b];
            if (r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]) return b;
        }
        return 0;
     } finally { rally3d.core.Trace.out(511, $t); } }

    private boolean uiHeld(int b) { for (int i = 0; i < 4; i++) if (ids[i] >= 0 && kind[i] == 3 && bt[i] == b) return true; return false; }

    private void pointer(int action, int x, int y, int id, int button) { /*T*/ final long $t = rally3d.core.Trace.in(512); try {
        int s = slotOf(id);
        if (action == 0) {
            if (s >= 0) return;
            for (int i = 0; i < 4; i++) if (ids[i] < 0) { s = i; break; }
            if (s < 0) return;
            ids[s] = id; lx[s] = ox[s] = x; ly[s] = oy[s] = y; kind[s] = 0; downAt[s] = System.currentTimeMillis();
            showHelp = false;
            int b = buttonAt(x, y);
            if (b == B_BAR) { kind[s] = 4; barJump(x); return; }
            if (b != 0) {
                kind[s] = 3; bt[s] = b;
                if (b != B_UP && b != B_DOWN && b != B_BOOST) buttonAction(b);
                return;
            }
            boolean touch = rally3d.desktop.Platform.isTouchDevice();
            if (touch && x < W * 45 / 100 && y > H * 25 / 100 && !joyActive) { kind[s] = 2; joyActive = true; joyKx = joyCx = x; joyKy = joyCy = y; joyX = joyY = 0; return; }
            kind[s] = 1;
            return;
        }
        if (s < 0) return;
        if (action == 1) {
            int dx = x - lx[s], dy = y - ly[s];
            lx[s] = x; ly[s] = y;
            if (kind[s] == 1) { yaw += dx * LOOK_DEG_PER_PX; pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + dy * LOOK_DEG_PER_PX)); }
            else if (kind[s] == 2) {
                float ddx = x - joyCx, ddy = y - joyCy;
                float len = (float) Math.hypot(ddx, ddy);
                if (len > joyR) { ddx *= joyR / len; ddy *= joyR / len; len = joyR; }
                joyKx = joyCx + (int) ddx; joyKy = joyCy + (int) ddy;
                joyX = Math.abs(ddx / joyR) < 0.12f ? 0 : ddx / joyR;
                joyY = Math.abs(ddy / joyR) < 0.12f ? 0 : ddy / joyR;
            } else if (kind[s] == 4) barJump(x);
            return;
        }
        if (action == 2) {
            if (kind[s] == 2) { joyActive = false; joyX = joyY = 0; }
            ids[s] = -1; kind[s] = 0;
        }
     } finally { rally3d.core.Trace.out(512, $t); } }

    private void barJump(int x) { /*T*/ final long $t = rally3d.core.Trace.in(513); try {
        int[] r = rects[B_BAR];
        if (r == null) return;
        int s = (int) ((long) (x - r[0]) * (g.segmentCount - 2) / Math.max(1, r[2]));
        gotoSegment(s, false);
     } finally { rally3d.core.Trace.out(513, $t); } }

    private void buttonAction(int b) { /*T*/ final long $t = rally3d.core.Trace.in(514); try {
        switch (b) {
            case B_EXIT: exit = true; break;
            case B_SLOW: speed = clampSpeed(speed / 1.4f); say("Speed " + (int) speed); break;
            case B_FASTER: speed = clampSpeed(speed * 1.4f); say("Speed " + (int) speed); break;
            case B_START: gotoSegment(MapData.START_SEG, true); break;
            case B_END: gotoSegment(Math.max(0, g.checkpoints[3] - 2), true); break;
            case B_HELP: showHelp = !showHelp; helpUntil = System.currentTimeMillis() + 15000L; break;
            default: break;
        }
     } finally { rally3d.core.Trace.out(514, $t); } }

    private void say(String t) { toast = t; toastUntil = System.currentTimeMillis() + 1800L; }

    // ------------------------------------------------------------------ HUD

    private void layout() { /*T*/ final long $t = rally3d.core.Trace.in(515); try {
        W = g.width; H = g.height;
        sh = g.fontSmallHeight; bh = sh + 8;
        joyR = Math.max(24, Math.min(H * 22 / 100, 56));
        int pad = 4, bw = Math.max(34, g.fontSmall.stringWidth("START") + 12);
        java.util.Arrays.fill(rects, null);
        rects[B_EXIT] = new int[]{pad, pad, bw, bh};
        rects[B_START] = new int[]{pad + bw + 3, pad, bw, bh};
        rects[B_END] = new int[]{pad + (bw + 3) * 2, pad, bw, bh};
        rects[B_HELP] = new int[]{pad + (bw + 3) * 3, pad, bh, bh};
        int right = W - pad - bw;
        int by = H / 2 - bh * 2;
        rects[B_FASTER] = new int[]{right, by, bw, bh};
        rects[B_SLOW] = new int[]{right, by + bh + 3, bw, bh};
        rects[B_BOOST] = new int[]{right, by + (bh + 3) * 2, bw, bh};
        rects[B_UP] = new int[]{right, H - pad - bh * 5 - 12 - 10, bw, bh * 2};
        rects[B_DOWN] = new int[]{right, H - pad - bh * 3 - 6 - 10, bw, bh * 2};
        int barW = Math.max(60, W * 45 / 100);
        rects[B_BAR] = new int[]{(W - barW) / 2, H - pad - 12, barW, 12};
     } finally { rally3d.core.Trace.out(515, $t); } }

    private void txt(Graphics gr, String s, int x, int y, int color) { /*T*/ final long $t = rally3d.core.Trace.in(516); try {
        gr.setFont(g.fontSmall);
        gr.setColor(0x000000);
        gr.drawString(s, x + 1, y + 1, Graphics.TOP | Graphics.LEFT);
        gr.setColor(color & 0xFFFFFF);
        gr.drawString(s, x, y, Graphics.TOP | Graphics.LEFT);
     } finally { rally3d.core.Trace.out(516, $t); } }

    private int tw(String s) { return g.fontSmall.stringWidth(s); }

    private void button(Graphics gr, int id, String label, boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(517); try {
        int[] r = rects[id];
        if (r == null) return;
        boolean down = uiHeld(id);
        gr.fillRectAlpha(r[0], r[1], r[2], r[3], down ? 0xC0A0C0E0 : on ? 0xB06A8AA6 : 0x90202A34);
        gr.setColor(0xFFFFFF);
        gr.drawLine(r[0], r[1], r[0] + r[2] - 1, r[1]); gr.drawLine(r[0], r[1] + r[3] - 1, r[0] + r[2] - 1, r[1] + r[3] - 1);
        gr.drawLine(r[0], r[1], r[0], r[1] + r[3] - 1); gr.drawLine(r[0] + r[2] - 1, r[1], r[0] + r[2] - 1, r[1] + r[3] - 1);
        String s = label;
        while (s.length() > 1 && tw(s) > r[2] - 3) s = s.substring(0, s.length() - 1);
        txt(gr, s, r[0] + (r[2] - tw(s)) / 2, r[1] + (r[3] - sh) / 2, on || down ? 0xFFFFAA : 0xFFFFFF);
     } finally { rally3d.core.Trace.out(517, $t); } }

    void drawHud(Graphics gr) { /*T*/ final long $t = rally3d.core.Trace.in(518); try {
        gr.setClip(0, 0, g.width, g.height);
        if (rects[B_EXIT] == null) layout();
        button(gr, B_EXIT, "EXIT", false);
        button(gr, B_START, "START", false);
        button(gr, B_END, "FINISH", false);
        button(gr, B_HELP, "?", showHelp);
        button(gr, B_FASTER, "SPD +", false);
        button(gr, B_SLOW, "SPD -", false);
        button(gr, B_BOOST, "FAST", boost);
        button(gr, B_UP, "UP", false);
        button(gr, B_DOWN, "DOWN", false);
        // info line
        int n = g.segmentCount - 2;
        float alt = py - g.segments[Math.max(0, Math.min(n, seg))].startPos.v[1];
        String info = "SEG " + seg + "/" + n + "   ALT " + Math.round(alt) + "   SPEED " + (int) speed + (held[16] || boost ? " x4" : "");
        int iy = rects[B_EXIT][1] + bh + 4;
        gr.fillRectAlpha(4, iy - 1, tw(info) + 6, sh + 3, 0x90101820);
        txt(gr, info, 7, iy, 0xFFFFFF);
        // progress bar (drag = jump along the road) with the checkpoint ticks
        int[] r = rects[B_BAR];
        gr.fillRectAlpha(r[0], r[1], r[2], r[3], 0xA0101820);
        gr.setColor(0xFFFFFF);
        gr.drawLine(r[0], r[1], r[0] + r[2] - 1, r[1]); gr.drawLine(r[0], r[1] + r[3] - 1, r[0] + r[2] - 1, r[1] + r[3] - 1);
        gr.drawLine(r[0], r[1], r[0], r[1] + r[3] - 1); gr.drawLine(r[0] + r[2] - 1, r[1], r[0] + r[2] - 1, r[1] + r[3] - 1);
        gr.setColor(0x60D060); int sx0 = r[0] + (int) ((long) MapData.START_SEG * r[2] / Math.max(1, n)); gr.drawLine(sx0, r[1] + 1, sx0, r[1] + r[3] - 2);
        for (int k = 0; k < 4; k++) {
            int cx = r[0] + (int) ((long) g.checkpoints[k] * r[2] / Math.max(1, n));
            gr.setColor(k == 3 ? 0xE05050 : 0xFFFFAA);
            gr.drawLine(cx, r[1] + 1, cx, r[1] + r[3] - 2);
        }
        int mx = r[0] + (int) ((long) seg * r[2] / Math.max(1, n));
        gr.fillRectAlpha(mx - 2, r[1] - 3, 5, r[3] + 6, 0xFFFFFFFF);
        // joystick
        if (rally3d.desktop.Platform.isTouchDevice()) {
            int cx = joyActive ? joyCx : joyR + 14, cy = joyActive ? joyCy : H - joyR - 22;
            ring(gr, cx, cy, joyR, joyActive ? 0xFFFFFF : 0x909090);
            int kx = joyActive ? joyKx : cx, ky = joyActive ? joyKy : cy;
            gr.fillRectAlpha(kx - 8, ky - 8, 17, 17, joyActive ? 0xC0FFFFAA : 0x60FFFFFF);
        }
        // toast + help
        long now = System.currentTimeMillis();
        if (toast != null && now < toastUntil) {
            int w = tw(toast) + 12;
            gr.fillRectAlpha((W - w) / 2, H / 2 + 20, w, sh + 6, 0xC0101820);
            txt(gr, toast, (W - w) / 2 + 6, H / 2 + 23, 0xFFFFAA);
        }
        if (showHelp && (now < helpUntil || helpUntil == 0)) {
            String[] lines = rally3d.desktop.Platform.isTouchDevice()
                ? new String[]{"FLY VIEW", "left side: joystick = move", "drag elsewhere = look around", "UP / DOWN = height, FAST = x4", "bottom bar = jump along the road", "EXIT or BACK = return to the editor"}
                : new String[]{"FLY VIEW", "W A S D = move   Space / E = up   Q / C = down", "Shift = fast   Ctrl = slow   wheel or + / - = speed", "drag the mouse or use arrows = look", "Home / End = start / finish   PgUp / PgDn = 20 segments", "R = back to the first view   F = fast toggle   Esc = leave"};
            int w = 0;
            for (String l : lines) w = Math.max(w, tw(l));
            w += 14;
            int h = lines.length * (sh + 2) + 8, x = (W - w) / 2, y = H / 2 - h / 2 - 10;
            gr.fillRectAlpha(x, y, w, h, 0xB0101820);
            for (int i = 0; i < lines.length; i++) txt(gr, lines[i], x + 7, y + 5 + i * (sh + 2), i == 0 ? 0xFFFFAA : 0xFFFFFF);
        }
     } finally { rally3d.core.Trace.out(518, $t); } }

    private void ring(Graphics gr, int cx, int cy, int r, int rgb) { /*T*/ final long $t = rally3d.core.Trace.in(519); try {
        gr.setColor(rgb);
        int px0 = cx + r, py0 = cy;
        for (int i = 1; i <= 28; i++) {
            double a = i * Math.PI * 2 / 28;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            gr.drawLine(px0, py0, x, y);
            px0 = x; py0 = y;
        }
     } finally { rally3d.core.Trace.out(519, $t); } }
}
