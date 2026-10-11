package com.nokia.mid.appl.rally3d;

import java.util.HashMap;

/**
 * Multiplayer transfer of add-ons so every player really sees the modded car AND the custom wheels. Runs on the network thread
 * (TheBluetooth.receiveLoop), never through the 256-slot game packet queue, so chunks cannot be dropped.
 * Three slots: SLOT_CAR (car pack, or null = stock), SLOT_WHEELS (wheels pack type=wheels, or null) and SLOT_MAP (the host's custom map for this round, or null).
 * Packets:  20 OFFER  [flag(0 none/1 pack), size(4 LE), md5(16), slot]   21 REQUEST [slot]   22 CHUNK [slot, offset(4 LE), data...]
 */
final class AddonSync {
    static final int T_OFFER = 20, T_REQUEST = 21, T_CHUNK = 22;
    static final int SLOT_CAR = 0, SLOT_WHEELS = 1, SLOT_MAP = 2, SLOTS = 3;
    static final int CHUNK = 110;
    /** Relay slots: the host re-offers every guest's packs to the other guests as slot 2 + (origin-1)*2 + slot. */
    static final int MAX_PEERS = 32, ALL_SLOTS = SLOTS + SLOTS * MAX_PEERS;
    static int vslot(int origin, int slot) { return SLOTS + (origin - 1) * SLOTS + slot; }
    private static int originOf(int vslot) { return (vslot - SLOTS) / SLOTS + 1; }
    private volatile boolean host;
    private volatile int myId;
    void setRole(boolean isHost, int id) { host = isHost; myId = id; }
    private static Integer key(int sender, int slot) { return Integer.valueOf(sender * 128 + slot); }

    static final class Remote {
        volatile boolean offered, complete, failed, requested;   // requested: REQUEST sent, buffer is allocated lazily by the first chunk
        volatile int size, got;
        byte[] sha;
        byte[] buf;
        boolean[] have;         // per-chunk received flags (duplicate chunks must not be counted twice)
        volatile byte[] pack;   // complete + md5 verified; null when the sender uses nothing in this slot
    }

    /** What AddonSync needs from the network layer (TheBluetooth in the game, a loopback in tests). */
    interface Link {
        void send(byte[] packet, int length);
        void sendTo(int peer, byte[] packet, int length);
    }

    private final Link net;
    private final java.util.HashSet<Integer> streaming = new java.util.HashSet<Integer>();   // (sender, slot) pairs with a running send thread
    private final byte[][] mine = new byte[SLOTS][];   // my zips per slot, null = nothing
    private final HashMap<Integer, Remote> remotes = new HashMap<Integer, Remote>();
    /** md5 hex -> verified zip, kept for the whole session so rematches do not download again. */
    private final HashMap<String, byte[]> cache = new HashMap<String, byte[]>();

    AddonSync(Link net) { this.net = net; }

    synchronized Remote remote(int sender, int slot) { /*T*/ final long $t = rally3d.core.Trace.in(298); try {
        Integer key = key(sender, slot);
        Remote r = remotes.get(key);
        if (r == null) { r = new Remote(); remotes.put(key, r); }
        return r;
     } finally { rally3d.core.Trace.out(298, $t); } }

    synchronized void forgetRemotes() { remotes.clear(); }

    void setMine(int slot, byte[] zip) { synchronized (mine) { mine[slot] = zip; } }
    private byte[] mine(int slot) { synchronized (mine) { return mine[slot]; } }

    void sendOffer() { /*T*/ final long $t = rally3d.core.Trace.in(272); try {
        for (int slot = 0; slot < SLOTS; slot++) {
            byte[] z = mine(slot);
            byte[] p = new byte[2 + 22];
            p[0] = T_OFFER; p[1] = 22; p[23] = (byte) slot;
            if (z != null) {
                p[2] = 1;
                int n = z.length;
                p[3] = (byte) n; p[4] = (byte) (n >> 8); p[5] = (byte) (n >> 16); p[6] = (byte) (n >> 24);
                System.arraycopy(AddonPack.md5(z), 0, p, 7, 16);
            }
            net.send(p, p.length);
        }
        if (host) {   // relay: what the guests offered to me is offered on to everybody (the origin ignores its own)
            java.util.ArrayList<Integer> keys;
            synchronized (this) { keys = new java.util.ArrayList<Integer>(remotes.keySet()); }
            for (Integer k : keys) {
                int sender = k / 128, slot = k % 128;
                if (sender < 1 || slot >= SLOTS) continue;
                Remote r = remote(sender, slot);
                byte[] pk = r.pack;
                byte[] p = new byte[2 + 22];
                p[0] = T_OFFER; p[1] = 22; p[23] = (byte) vslot(sender, slot);
                if (r.complete && pk != null) {
                    p[2] = 1; int n = pk.length;
                    p[3] = (byte) n; p[4] = (byte) (n >> 8); p[5] = (byte) (n >> 16); p[6] = (byte) (n >> 24);
                    System.arraycopy(AddonPack.md5(pk), 0, p, 7, 16);
                } else if (!(r.complete || r.failed)) continue;
                net.send(p, p.length);
            }
        }
     } finally { rally3d.core.Trace.out(272, $t); } }

    static boolean handles(int type) { return type == T_OFFER || type == T_REQUEST || type == T_CHUNK; }

    /** Never throws: this runs on the network thread, a hostile or corrupt packet must not kill the receive loop. */
    void onPacket(int sender, byte[] p) { /*T*/ final long $t = rally3d.core.Trace.in(273); try {
        try { handle(sender, p); }
        catch (RuntimeException e) { rally3d.core.RLog.caught("AddonSync.onPacket", e); markFailed(sender, p); }
     } finally { rally3d.core.Trace.out(273, $t); } }

    private void markFailed(int sender, byte[] p) { /*T*/ final long $t = rally3d.core.Trace.in(299); try {
        try { int slot = slotOf(p); if (slot >= 0) remote(sender, slot).failed = true; } catch (RuntimeException e) { rally3d.core.RLog.caught("AddonSync.markFailed", e); }
     } finally { rally3d.core.Trace.out(299, $t); } }

    private static int slotOf(byte[] p) { /*T*/ final long $t = rally3d.core.Trace.in(300); try {
        int type = p[0] & 255;
        int idx = type == T_OFFER ? 23 : 2;
        if (p.length <= idx) return -1;
        int s = p[idx] & 255;
        return s < ALL_SLOTS ? s : -1;
     } finally { rally3d.core.Trace.out(300, $t); } }

    private void handle(final int sender, byte[] p) { /*T*/ final long $t = rally3d.core.Trace.in(274); try {
        int type = p[0] & 255, len = p[1] & 255;
        final int slot = slotOf(p);
        if (slot < 0) return;
        if (type == T_OFFER && slot >= SLOTS && originOf(slot) == myId) return;   // my own pack, relayed back
        Remote r = remote(sender, slot);
        if (type == T_OFFER && len >= 22) {
            if (r.offered && !r.failed && (r.complete || r.requested)) return;   // repeated offer, already handled
            if (p[2] == 0) { r.failed = false; r.pack = null; r.buf = null; r.have = null; r.complete = true; r.requested = false; r.offered = true; return; }
            int size = (p[3] & 255) | (p[4] & 255) << 8 | (p[5] & 255) << 16 | (p[6] & 255) << 24;
            byte[] sha = new byte[16];
            System.arraycopy(p, 7, sha, 0, 16);
            r.sha = sha;
            r.failed = false; r.complete = false; r.requested = false; r.pack = null; r.buf = null; r.have = null;   // a fresh offer restarts after an earlier failure
            if (size <= 0 || size > AddonPack.MAX_ZIP) {
                rally3d.core.RLog.w("addon", "peer " + sender + " offered a pack of " + size + " bytes (slot " + slot + "): refused");
                r.failed = true; r.offered = true; return;
            }
            byte[] have;
            synchronized (this) { have = cache.get(hex(sha)); }
            if (have != null) { r.pack = have; r.complete = true; r.offered = true; return; }
            r.size = size; r.got = 0; r.requested = true; r.offered = true;   // buffer (<= MAX_ZIP) is allocated when the first chunk arrives, not for every offer
            rally3d.core.RLog.i("addon", "peer " + sender + " offers pack (slot " + slot + "), " + size + " bytes: requesting");
            net.sendTo(sender, new byte[]{T_REQUEST, 1, (byte) slot}, 3);
        } else if (type == T_REQUEST) {
            final byte[] z = slot < SLOTS ? mine(slot) : relayPack(slot);
            if (z == null) return;
            final Integer sk = key(sender, slot);
            synchronized (streaming) { if (!streaming.add(sk)) return; }   // a stream to this peer+slot is already running: a REQUEST flood must not spawn threads
            Thread t = new Thread(new Runnable() { public void run() { try { streamTo(sender, slot, z); } finally { synchronized (streaming) { streaming.remove(sk); } } } }, "rally-addon-send");
            t.setDaemon(true);
            t.start();
        } else if (type == T_CHUNK && len > 5) {
            if (!r.requested || r.complete || r.failed) return;
            byte[] buf = r.buf;
            if (buf == null) { if (r.size <= 0 || r.size > AddonPack.MAX_ZIP) return; buf = new byte[r.size]; r.have = new boolean[(r.size + CHUNK - 1) / CHUNK]; r.buf = buf; }
            int off = (p[3] & 255) | (p[4] & 255) << 8 | (p[5] & 255) << 16 | (p[6] & 255) << 24;
            int n = len - 5;
            if (off < 0 || (long) off + n > buf.length || off % CHUNK != 0 || (n != CHUNK && off + n != buf.length) || 7 + n > p.length) { r.failed = true; return; }
            boolean[] have = r.have;
            if (have == null || have[off / CHUNK]) return;   // duplicate chunk
            have[off / CHUNK] = true;
            System.arraycopy(p, 7, buf, off, n);
            r.got += n;
            if (r.got >= r.size) {
                if (java.util.Arrays.equals(AddonPack.md5(buf), r.sha)) {
                    synchronized (this) { cache.put(hex(r.sha), buf); }
                    r.pack = buf; r.complete = true;
                    rally3d.core.RLog.i("addon", "pack (slot " + slot + ") from peer " + sender + " received and verified");
                } else {
                    r.failed = true;
                    rally3d.core.RLog.w("addon", "pack (slot " + slot + ") from peer " + sender + " failed md5 check");
                }
            }
        }
     } finally { rally3d.core.Trace.out(274, $t); } }

    /** Host: the pack a guest gave me, for another guest (null = not (yet) available). */
    private byte[] relayPack(int vslot) { /*T*/ final long $t = rally3d.core.Trace.in(342); try {
        if (!host) return null;
        int origin = originOf(vslot), s = (vslot - SLOTS) % SLOTS;
        Remote rr;
        synchronized (this) { rr = remotes.get(key(origin, s)); }
        return rr == null ? null : rr.pack;
     } finally { rally3d.core.Trace.out(342, $t); } }

    private void streamTo(int sender, int slot, byte[] z) { /*T*/ final long $t = rally3d.core.Trace.in(275); try {
        try {
            byte[] p = new byte[2 + 5 + CHUNK];
            for (int off = 0; off < z.length; off += CHUNK) {
                int n = Math.min(CHUNK, z.length - off);
                p[0] = T_CHUNK; p[1] = (byte) (5 + n); p[2] = (byte) slot;
                p[3] = (byte) off; p[4] = (byte) (off >> 8); p[5] = (byte) (off >> 16); p[6] = (byte) (off >> 24);
                System.arraycopy(z, off, p, 7, n);
                net.sendTo(sender, p, 7 + n);
                if ((off / CHUNK) % 32 == 31) Thread.sleep(2);   // do not starve the 20 Hz car state packets
            }
        } catch (InterruptedException e) { rally3d.core.RLog.caught("AddonSync.streamTo", e); Thread.currentThread().interrupt(); }
     } finally { rally3d.core.Trace.out(275, $t); } }

    static String hex(byte[] b) { /*T*/ final long $t = rally3d.core.Trace.in(276); try {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) sb.append(Character.forDigit((b[i] >> 4) & 15, 16)).append(Character.forDigit(b[i] & 15, 16));
        return sb.toString();
     } finally { rally3d.core.Trace.out(276, $t); } }
}
