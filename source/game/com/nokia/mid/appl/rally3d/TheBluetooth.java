package com.nokia.mid.appl.rally3d;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/** Desktop local-network transport. Keeps the original game's small packet protocol. */
class TheBluetooth implements Runnable {
    static final int MAX_PLAYERS = 32;
    static final int TCP_PORT = 47832;
    private static final int DISCOVERY_PORT = 47833;
    private static final String BEACON = "RALLY3D|2|";          // v2 beacon carries the build version
    private static final String LEGACY_BEACON = "RALLY3D|1|";    // hosts from builds without version check: shown as incompatible
    private static final String DISCOVER = "RALLY3D|DISCOVER";

    volatile boolean waiting = true;
    volatile boolean awaitingDevice = true;
    volatile boolean inquiryDone;
    volatile boolean hostMode;
    volatile boolean hostDone;
    volatile boolean hostConnected;
    volatile boolean clientDone;
    volatile boolean clientConnected;
    volatile boolean joinRejected;
    volatile int joinRejectReason;
    volatile int assignedPlayerId = -1;
    volatile boolean adapterAvailable;
    /** Human readable reason of the last failed client connection (null when none). */
    volatile String connectError;
    final String[] deviceNames = new String[16];
    private final boolean[] incompatibleServers = new boolean[16];
    private final String[] hostVersions = new String[16];
    /** Version of the host we were rejected by / found incompatible (reject reason 4 = different game version). */
    volatile String remoteVersion;
    private final InetSocketAddress[] servers = new InetSocketAddress[16];
    private final boolean[] protectedServers = new boolean[16];
    volatile int deviceCount;
    volatile int selectedDevice;
    private volatile String directTarget;
    private volatile boolean closed;
    private final String playerName;
    private final String gameVersion;
    private final int maxPlayers;
    private final String hostPassword;
    private volatile String joinPassword = "";
    private static final class ReceivedPacket {
        final int senderId;
        final byte[] data;
        ReceivedPacket(int senderId, byte[] data) { this.senderId = senderId; this.data = data; }
    }
    private final LinkedBlockingQueue<ReceivedPacket> packets = new LinkedBlockingQueue<ReceivedPacket>(1024);
    private final BufferedOutputStream[] outputs = new BufferedOutputStream[MAX_PLAYERS];
    private final Socket[] peerSockets = new Socket[MAX_PLAYERS];
    private final String[] peerNames = new String[MAX_PLAYERS];
    /** Slot is taken: handshake in progress or peer attached. Guarded by "this". */
    private final boolean[] reserved = new boolean[MAX_PLAYERS];
    /** peer id -> nanoTime of the last READY packet; a ready flag expires (guests re-send it every 250 ms) so stale flags from an earlier round never count. */
    private final ConcurrentHashMap<Integer, Long> readyPlayers = new ConcurrentHashMap<Integer, Long>();
    private static final long READY_TTL_NANOS = 3_000_000_000L;
    private boolean isReady(int id) { Long t = readyPlayers.get(id); return t != null && System.nanoTime() - t < READY_TTL_NANOS; }
    volatile int lastSenderId = -1;
    /** Add-on car transfer (packet types 20-22), handled on the network thread. */
    final AddonSync addon = new AddonSync(new AddonSync.Link() {
        public void send(byte[] packet, int length) { sendPacket(packet, length); }
        public void sendTo(int peer, byte[] packet, int length) { sendPacketTo(peer, packet, length); }
    });
    /** Ping (type 17) is answered and pong (type 18) is timestamped on the network thread, so the measured RTT is not inflated by the game's frame time. */
    private final int[] pongSeq = new int[MAX_PLAYERS];
    private final long[] pongAt = new long[MAX_PLAYERS];
    private synchronized void recordPong(int id, int seq, long at) { if (id >= 0 && id < MAX_PLAYERS) { pongSeq[id] = seq; pongAt[id] = at; } }
    /** nanoTime when the pong with this sequence number arrived from peer id (0 = host for clients), or -1 if not (yet) received. */
    synchronized long pongArrival(int id, int seq) { return id >= 0 && id < MAX_PLAYERS && pongAt[id] != 0L && pongSeq[id] == seq ? pongAt[id] : -1L; }
    /** nanoTime of the last byte-level packet from each peer (host: peer id, client: 0), stamped on the network thread - alive even while the game loop is frozen/backgrounded. */
    private final long[] rxAt = new long[MAX_PLAYERS];
    /** Nanoseconds since anything at all arrived from this peer (0 = nothing yet). */
    long silentNanos(int id) { long t = id >= 0 && id < MAX_PLAYERS ? rxAt[id] : 0L; return t == 0L ? 0L : System.nanoTime() - t; }
    private volatile int selectedTrack = -1;
    private ServerSocket listener;
    private DatagramSocket beaconSocket;
    private DatagramSocket discoverySocket;
    private Socket connection;
    private BufferedOutputStream out;
    private final Thread thread;

    TheBluetooth(RallyGame game) { this(game, "Player", false, 2, ""); }

    TheBluetooth(RallyGame game, String name, boolean vanillaMode) { this(game, name, vanillaMode, 2, ""); }

    TheBluetooth(RallyGame game, String name, boolean vanillaMode, int maxPlayers) { this(game, name, vanillaMode, maxPlayers, ""); }

    TheBluetooth(RallyGame game, String name, boolean vanillaMode, int maxPlayers, String password) {
        this.playerName = sanitize(name);
        this.gameVersion = vanillaMode ? "Vanilla" : "Modified";
        this.maxPlayers = Math.max(2, Math.min(MAX_PLAYERS, maxPlayers));
        this.hostPassword = password == null ? "" : password;
        log("session created: name=" + this.playerName + ", version=" + this.gameVersion + ", maxPlayers=" + this.maxPlayers + ", password=" + (this.hostPassword.isEmpty() ? "no" : "yes") + ", ports tcp=" + TCP_PORT + " udp=" + DISCOVERY_PORT);
        this.thread = new Thread(this, "rally-lan");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private static void log(String message) { rally3d.core.RLog.i("lan", message); }
    private static void warn(String message, Throwable t) { rally3d.core.RLog.w("lan", message, t); }

    private static String sanitize(String s) { /*T*/ final long $t = rally3d.core.Trace.in(122); try {
        if (s == null) return "Player";
        String value = s.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (value.length() > 16) value = value.substring(0, 16);
        return value.length() == 0 ? "Player" : value;
     } finally { rally3d.core.Trace.out(122, $t); } }

    void startHost() { hostMode = true; waiting = false; hostDone = false; }
    boolean isHostDone() { return hostDone; }
    boolean isHostConnected() { return hostConnected; }
    void startClient() { waiting = false; inquiryDone = false; }
    /** Join a known address ("host" or "host:port") instead of using LAN discovery. */
    void setDirectTarget(String target) { directTarget = target == null ? null : target.trim(); }
    boolean hasDirectTarget() { return directTarget != null && directTarget.length() > 0; }
    boolean isInquiryDone() { return inquiryDone; }
    boolean hasDevices() { return deviceCount > 0; }
    boolean isClientDone() { return clientDone; }
    boolean isClientConnected() { return clientConnected; }
    int maxPlayers() { return maxPlayers; }

    private synchronized int countPlayers() {
        int count = 1;
        for (int id = 1; id < MAX_PLAYERS; id++) if (reserved[id]) count++;
        return count;
    }

    private synchronized int countAttachedPeers() {
        int count = 0;
        for (int id = 1; id < MAX_PLAYERS; id++) if (peerSockets[id] != null) count++;
        return count;
    }

    int playerCount() { return hostMode ? countPlayers() : -1; }
    int connectedPeerCount() { return hostMode ? countAttachedPeers() : 1; }
    int readyPeerCount() { /*T*/ final long $t = rally3d.core.Trace.in(123); try {
        if (!hostMode) return 0;
        int ready = 0;
        for (int id = 1; id < peerSockets.length; id++)
            if (peerSockets[id] != null && isReady(id)) ready++;
        return ready;
     } finally { rally3d.core.Trace.out(123, $t); } }
    boolean allConnectedPeersReady() { /*T*/ final long $t = rally3d.core.Trace.in(124); try {
        if (!hostMode || countAttachedPeers() < 1) return false;
        for (int id = 1; id < peerSockets.length; id++)
            if (peerSockets[id] != null && !isReady(id)) return false;
        return true;
     } finally { rally3d.core.Trace.out(124, $t); } }
    void markReady(int id) { /*T*/ final long $t = rally3d.core.Trace.in(232); try { if (id > 0 && id < peerSockets.length && peerSockets[id] != null) readyPlayers.put(id, System.nanoTime());  } finally { rally3d.core.Trace.out(232, $t); } }
    /** Lobby entry: forget ready flags AND queued packets (old READY / status packets from the previous round). */
    void resetLobby() { readyPlayers.clear(); packets.clear(); }
    int assignedPlayerId() { return assignedPlayerId; }
    String playerName() { return playerName; }
    boolean selectedServerNeedsPassword() { return selectedDevice >= 0 && selectedDevice < deviceCount && protectedServers[selectedDevice]; }
    void setJoinPassword(String value) { joinPassword = value == null ? "" : value; }

    /** Names of everyone currently in the lobby, host first. */
    synchronized List<String> playerNames() { /*T*/ final long $t = rally3d.core.Trace.in(125); try {
        List<String> names = new ArrayList<String>();
        names.add(playerName);
        for (int id = 1; id < MAX_PLAYERS; id++) if (peerSockets[id] != null && peerNames[id] != null) names.add(peerNames[id]);
        return names;
     } finally { rally3d.core.Trace.out(125, $t); } }

    /** Site-local IPv4 addresses of this PC, for "join by IP". */
    static List<String> localAddresses() { /*T*/ final long $t = rally3d.core.Trace.in(126); try {
        List<String> result = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    InetAddress ip = address.getAddress();
                    if (ip instanceof Inet4Address && !ip.isLinkLocalAddress()) result.add(ip.getHostAddress());
                }
            }
        } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.localAddresses", e); warn("localAddresses failed", e); }
        java.util.Collections.sort(result, new java.util.Comparator<String>() {   // real LAN first, VPN (26.x / 25.x ...) after
            public int compare(String a, String b) { return Integer.compare(lanRank(a), lanRank(b)); }
        });
        log("local addresses: " + result);
        return result;
     } finally { rally3d.core.Trace.out(126, $t); } }

    private static int lanRank(String ip) { /*T*/ final long $t = rally3d.core.Trace.in(247); try {
        if (ip.startsWith("192.168.") || ip.startsWith("10.")) return 0;
        if (ip.startsWith("172.")) { try { int second = Integer.parseInt(ip.split("\\.")[1]); if (second >= 16 && second <= 31) return 0; } catch (RuntimeException e) { rally3d.core.RLog.caught("TheBluetooth.lanRank", e); } }
        return 1;
     } finally { rally3d.core.Trace.out(247, $t); } }

    /** New round: forget the previous track and ready flags so late joiners do not get stale data. */
    void beginRound() {
        selectedTrack = -1;
        readyPlayers.clear();
    }

    /** Packet 24: a guest tells the host it holds the custom map of this round. */
    static final int T_MAPACK = 24;
    final java.util.Set<Integer> mapAcks = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Integer, Boolean>());
    void sendMapAck() { byte[] p = new byte[]{(byte) T_MAPACK, 1, 1}; sendPacket(p, p.length); }
    /** Host: how many guests are connected right now. */
    int guestCount() { return hostMode ? Math.max(0, countAttachedPeers()) : 0; }

    void setTrackChoice(int track) { /*T*/ final long $t = rally3d.core.Trace.in(127); try {
        if (MapLibrary.isCustom(track)) track = MapLibrary.MP_MARK;   // custom map: the guests take it from the map slot of AddonSync
        if (!hostMode || track < 0 || (track >= 12 && track != MapLibrary.MP_MARK)) return;
        selectedTrack = track;
        byte[] packet = new byte[]{2, 1, (byte)track};
        sendPacket(packet, packet.length);
     } finally { rally3d.core.Trace.out(127, $t); } }

    /** Pick a server from the discovered list without connecting yet. */
    void chooseDevice(int index) { selectedDevice = index; }

    /** Start connecting to the chosen server. Set the password before calling this. */
    void connect() { clientDone = false; awaitingDevice = false; }

    void selectDevice(int index) {
        selectedDevice = index;
        connect();
    }

    boolean receivePacket(byte[] target) { /*T*/ final long $t = rally3d.core.Trace.in(128); try {
        ReceivedPacket packet = packets.poll();
        if (packet == null) return false;
        lastSenderId = packet.senderId;
        int n = Math.min(packet.data.length, target.length);
        System.arraycopy(packet.data, 0, target, 0, n);
        java.util.Arrays.fill(target, n, target.length, (byte) 0);   // a short packet must not expose bytes of the previous one
        return true;
     } finally { rally3d.core.Trace.out(128, $t); } }

    void sendPacket(byte[] packet, int length) { /*T*/ final long $t = rally3d.core.Trace.in(129); try {
        if (packet == null || length < 2) return;
        int size = Math.min(length, packet.length);
        if (hostMode) {
            for (int id = 1; id < outputs.length; id++) writePacket(id, outputs[id], packet, size);
        } else writePacket(0, outputs[0], packet, size);
     } finally { rally3d.core.Trace.out(129, $t); } }

    /** True once the TCP link to the other side is gone (add-on transfer stops waiting). */
    boolean linkLost() { return closed || (!hostMode && !clientConnected); }

    /** Send to one peer only (host: that client's slot; client: the host). */
    void sendPacketTo(int id, byte[] packet, int length) { /*T*/ final long $t = rally3d.core.Trace.in(288); try {
        if (packet == null || length < 2) return;
        int size = Math.min(length, packet.length);
        if (hostMode) { if (id > 0 && id < outputs.length) writePacket(id, outputs[id], packet, size); }
        else writePacket(0, outputs[0], packet, size);
     } finally { rally3d.core.Trace.out(288, $t); } }

    private void writePacket(int id, BufferedOutputStream stream, byte[] packet, int length) { /*T*/ final long $t = rally3d.core.Trace.in(130); try {
        if (stream == null) return;
        try {
            synchronized (stream) { stream.write(packet, 0, length); stream.flush(); }
        } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.writePacket", e);
            // Closing the socket makes the reader thread report the loss for exactly this peer.
            log("write to peer " + id + " failed: " + e.getMessage());
            if (hostMode) closeQuietly(peerSockets[id]); else closeQuietly(connection);
        }
     } finally { rally3d.core.Trace.out(130, $t); } }

    private void attach(Socket socket, int senderId, DataInputStream in) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(131); try {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(0);
        rxAt[senderId] = System.nanoTime();
        BufferedOutputStream stream = new BufferedOutputStream(new QueuedOut(socket, senderId));   // socket writes happen on a writer thread: a stalled peer cannot freeze the game thread
        if (hostMode) {
            synchronized (this) {
                outputs[senderId] = stream;
                peerSockets[senderId] = socket;
            }
        } else {
            connection = socket;
            out = stream;
            outputs[0] = stream;
        }
        if (hostMode && selectedTrack >= 0) writePacket(senderId, stream, new byte[]{2, 1, (byte)selectedTrack}, 3);
        // Reuse the handshake stream: it may already have buffered the first game packets.
        Thread reader = new Thread(new Runnable() { public void run() { receiveLoop(in, senderId); } }, "rally-lan-receiver-" + senderId);
        reader.setDaemon(true);
        reader.start();
     } finally { rally3d.core.Trace.out(131, $t); } }

    private synchronized void releaseSlot(int id) { /*T*/ final long $t = rally3d.core.Trace.in(132); try {
        if (id <= 0 || id >= MAX_PLAYERS) return;
        closeQuietly(peerSockets[id]);
        peerSockets[id] = null;
        outputs[id] = null;
        peerNames[id] = null;
        reserved[id] = false;
        readyPlayers.remove(id);
        hostConnected = countAttachedPeers() > 0;
     } finally { rally3d.core.Trace.out(132, $t); } }

    private void receiveLoop(DataInputStream in, int senderId) { /*T*/ final long $t = rally3d.core.Trace.in(133); try {
        try {
            while (!closed) {
                int type = in.read();
                if (type < 0) break;
                int length = in.read();
                if (length < 0 || length > 126) { warn("peer " + senderId + " sent bad packet length " + length + " (type " + type + ")", null); break; }
                byte[] packet = new byte[length + 2];
                packet[0] = (byte)type;
                packet[1] = (byte)length;
                in.readFully(packet, 2, length);
                rxAt[senderId] = System.nanoTime();
                if (type == 16) continue; // The join name is handled before the gameplay reader starts.
                if (AddonSync.handles(type)) { addon.onPacket(hostMode ? senderId : 0, packet); continue; }
                if (type == T_MAPACK) { mapAcks.add(Integer.valueOf(hostMode ? senderId : 0)); continue; }   // a guest has the round's custom map
                if (type == 17 && length == 4) {   // ping: echo straight back as pong from this thread
                    byte[] pong = packet.clone();
                    pong[0] = 18;
                    if (hostMode) writePacket(senderId, outputs[senderId], pong, pong.length); else writePacket(0, outputs[0], pong, pong.length);
                    continue;
                }
                if (type == 18 && length == 4) {   // pong: remember exactly when it arrived
                    int seq = (packet[2] & 0xFF) | (packet[3] & 0xFF) << 8 | (packet[4] & 0xFF) << 16 | (packet[5] & 0xFF) << 24;
                    recordPong(hostMode ? senderId : 0, seq, System.nanoTime());
                    continue;
                }
                if (type == 8 && length >= 3) {   // pause / resume of one player: the host stamps the origin id and relays it to the other guests
                    if (hostMode) {
                        packet[3] = (byte)senderId;
                        for (int id = 1; id < outputs.length; id++) if (id != senderId) writePacket(id, outputs[id], packet, packet.length);
                    }
                    enqueue(senderId, packet);
                    continue;
                }
                // Host relay: every guest's state (4) / player info (6) goes to all OTHER guests as 9 / 11 with the origin id in front.
                if (hostMode && senderId > 0) {
                    if (type == 4 && length >= 20) {
                        // host load is N^2: with more than 8 guests relay every 2nd state, with more than 16 every 4th (the receivers extrapolate)
                        int live = 0;
                        for (int i = 1; i < outputs.length; i++) if (outputs[i] != null) live++;
                        int every = live > 16 ? 4 : live > 8 ? 2 : 1;
                        if (senderId < relayTick.length && (++relayTick[senderId] % every) == 0) relayToGuests(senderId, 9, packet);
                    }
                    else if (type == 6 && length >= 3) relayToGuests(senderId, 11, packet);
                    else if (type == 5 && length >= 4) relayToGuests(senderId, 12, packet);   // finish time: [12, len, origin, int time]
                }
                // Preserve finish/connection status and the latest state packet if the game stalls (state of each origin separately).
                if (type == 4 || type == 9) {
                    final int t = type;
                    synchronized (packets) {
                        for (java.util.Iterator<ReceivedPacket> it = packets.iterator(); it.hasNext();) {
                            ReceivedPacket rp = it.next();
                            if (rp.data.length == 0 || rp.data[0] != t) continue;
                            if (t == 4 ? rp.senderId == senderId : (rp.data.length > 2 && packet.length > 2 && rp.data[2] == packet[2])) it.remove();
                        }
                        if (packets.remainingCapacity() == 0) packets.poll();
                        packets.offer(new ReceivedPacket(senderId, packet));
                    }
                } else {
                    if (packets.remainingCapacity() == 0) packets.poll();
                    packets.offer(new ReceivedPacket(senderId, packet));
                }
            }
        } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.receiveLoop", e);
            // Socket closure is reported to the game through the connection-lost packet.
            if (!closed) warn("receive loop for peer " + senderId + " ended: " + e, null);
        } finally {
            if (!closed) {
                log("peer " + senderId + " disconnected");
                enqueue(senderId, new byte[]{0, 0});
                if (hostMode && senderId > 0) releaseSlot(senderId);
                else if (!hostMode) clientConnected = false;
            }
        }
     } finally { rally3d.core.Trace.out(133, $t); } }

    private final int[] relayTick = new int[64];

    /** Host only: re-send a guest packet to every other guest as newType with the origin id inserted at [2]. */
    private void relayToGuests(int origin, int newType, byte[] packet) { /*T*/ final long $t = rally3d.core.Trace.in(350); try {
        int length = packet[1] & 0xFF;
        if (length + 1 > 126) return;
        byte[] rel = new byte[packet.length + 1];
        rel[0] = (byte)newType; rel[1] = (byte)(length + 1); rel[2] = (byte)origin;
        System.arraycopy(packet, 2, rel, 3, length);
        for (int id = 1; id < outputs.length; id++) if (id != origin) writePacket(id, outputs[id], rel, rel.length);
     } finally { rally3d.core.Trace.out(350, $t); } }

    private void enqueue(int senderId, byte[] packet) { /*T*/ final long $t = rally3d.core.Trace.in(134); try {
        if (packets.remainingCapacity() == 0) packets.poll();
        packets.offer(new ReceivedPacket(senderId, packet));
     } finally { rally3d.core.Trace.out(134, $t); } }

    private void hostSession() { /*T*/ final long $t = rally3d.core.Trace.in(135); try {
        try {
            listener = new ServerSocket();
            listener.setReuseAddress(true);
            log("host: binding TCP " + TCP_PORT);
            listener.bind(new InetSocketAddress(TCP_PORT));
            listener.setSoTimeout(350);
            beaconSocket = new DatagramSocket();
            beaconSocket.setBroadcast(true);
            startDiscoveryResponder();
            adapterAvailable = true;
            log("hosting on TCP " + TCP_PORT + ", max players " + maxPlayers);
            long lastBeacon = 0;
            while (!closed) {
                long now = System.currentTimeMillis();
                if (now - lastBeacon >= 700) {
                    broadcastBeacon();
                    lastBeacon = now;
                }
                try {
                    final Socket socket = listener.accept();
                    log("host: incoming connection from " + socket.getRemoteSocketAddress());
                    // Handshake on its own thread: a slow or silent client must not block other players.
                    Thread join = new Thread(new Runnable() { public void run() { handleJoin(socket); } }, "rally-lan-join");
                    join.setDaemon(true);
                    join.start();
                } catch (SocketTimeoutException ignored) { rally3d.core.RLog.caught("TheBluetooth.hostSession", ignored);}
            }
        } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.hostSession", e);
            if (!closed) warn("host socket error: " + e, e);
            adapterAvailable = false;
        } finally {
            hostDone = true;
        }
     } finally { rally3d.core.Trace.out(135, $t); } }

    private void handleJoin(Socket socket) { /*T*/ final long $t = rally3d.core.Trace.in(136); try {
        int id = -1;
        boolean attached = false;
        try {
            socket.setSoTimeout(3000);
            DataInputStream hello = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            int type = hello.readUnsignedByte();
            int size = hello.readUnsignedByte();
            if (type == 16) {   // client from a build without version check
                warn("join REJECTED: client " + socket.getRemoteSocketAddress() + " runs an old version (no version in hello), we are " + rally3d.core.Version.NAME, null);
                byte[] hv = rally3d.core.Version.NAME.getBytes(StandardCharsets.UTF_8);
                byte[] out = new byte[5 + hv.length]; out[0] = 14; out[1] = (byte)(3 + hv.length); out[2] = 0; out[3] = 4; out[4] = (byte)hv.length;
                System.arraycopy(hv, 0, out, 5, hv.length);
                writeRaw(new BufferedOutputStream(socket.getOutputStream()), out);
                return;
            }
            if (type != 17 || size < 3 || size > 124) { warn("join: bad hello header type=" + type + " size=" + size + " from " + socket.getRemoteSocketAddress() + " (not a Rally3D client?)", null); return; }
            int nameSize = hello.readUnsignedByte();
            if (nameSize < 1 || nameSize > 64 || nameSize + 2 > size) { warn("join: bad nickname size " + nameSize, null); return; }
            int passwordSize = hello.readUnsignedByte();
            int verSize = hello.readUnsignedByte();
            if (nameSize + passwordSize + verSize + 3 != size || passwordSize > 96 || verSize > 64) { warn("join: bad hello sizes name=" + nameSize + " password=" + passwordSize + " version=" + verSize + " (hello size " + size + ")", null); return; }
            byte[] nameBytes = new byte[nameSize];
            hello.readFully(nameBytes);
            byte[] passwordBytes = new byte[passwordSize];
            hello.readFully(passwordBytes);
            byte[] verBytes = new byte[verSize];
            hello.readFully(verBytes);
            String name = sanitize(new String(nameBytes, StandardCharsets.UTF_8));
            String password = new String(passwordBytes, StandardCharsets.UTF_8);
            String clientVersion = new String(verBytes, StandardCharsets.UTF_8);
            BufferedOutputStream response = new BufferedOutputStream(socket.getOutputStream());
            int reason = 0;
            synchronized (this) {
                if (!rally3d.core.Version.NAME.equals(clientVersion)) {
                    reason = 4;
                } else if (!hostPassword.equals(password)) {
                    reason = 3;
                } else {
                    id = reserveSlot();
                    if (id < 1) reason = 2;
                    else {
                        reserved[id] = true;
                        peerNames[id] = uniqueName(name);
                        readyPlayers.remove(id);
                    }
                }
            }
            if (reason != 0) {
                log("join rejected (" + name + " from " + socket.getRemoteSocketAddress() + "): reason " + reason + (reason == 3 ? " = wrong password" : reason == 2 ? " = server full (" + countPlayers() + "/" + maxPlayers + ")" : ""));
                if (reason == 4) {
                    warn("join REJECTED: " + name + " runs version '" + clientVersion + "', we are '" + rally3d.core.Version.NAME + "'", null);
                    byte[] hv = rally3d.core.Version.NAME.getBytes(StandardCharsets.UTF_8);
                    byte[] out = new byte[5 + hv.length]; out[0] = 14; out[1] = (byte)(3 + hv.length); out[2] = 0; out[3] = 4; out[4] = (byte)hv.length;
                    System.arraycopy(hv, 0, out, 5, hv.length);
                    writeRaw(response, out);
                    return;
                }
                writeRaw(response, new byte[]{14, 2, 0, (byte)reason});
                return;
            }
            writeRaw(response, new byte[]{14, 2, 1, (byte)id});
            attach(socket, id, hello);
            attached = true;
            hostConnected = true;
            hostDone = true;
            log("player joined: " + peerNames[id] + " (id " + id + ")");
        } catch (IOException | RuntimeException e) { rally3d.core.RLog.caught("TheBluetooth.handleJoin", e);
            warn("join failed: " + e, e);
        } finally {
            if (!attached) {
                if (id > 0) releaseSlot(id);
                closeQuietly(socket);
            }
        }
     } finally { rally3d.core.Trace.out(136, $t); } }

    /** Duplicate nicknames are made unique instead of refusing the player. */
    private synchronized String uniqueName(String wanted) { /*T*/ final long $t = rally3d.core.Trace.in(137); try {
        String candidate = wanted;
        for (int suffix = 2; suffix < 100; suffix++) {
            boolean taken = playerName.equalsIgnoreCase(candidate);
            for (int id = 1; id < peerNames.length && !taken; id++)
                if (reserved[id] && peerNames[id] != null && peerNames[id].equalsIgnoreCase(candidate)) taken = true;
            if (!taken) return candidate;
            String tail = " " + suffix;
            candidate = (wanted.length() + tail.length() > 16 ? wanted.substring(0, 16 - tail.length()) : wanted) + tail;
        }
        return candidate;
     } finally { rally3d.core.Trace.out(137, $t); } }

    private synchronized int reserveSlot() { /*T*/ final long $t = rally3d.core.Trace.in(138); try {
        if (countPlayers() >= maxPlayers) return -1;
        for (int id = 1; id < MAX_PLAYERS; id++) if (!reserved[id]) return id;
        return -1;
     } finally { rally3d.core.Trace.out(138, $t); } }

    /**
     * Output stream that hands every write to a per-peer writer thread through a bounded queue, so a peer that stops reading
     * (suspended phone, Wi-Fi stall: full TCP buffer) blocks only this thread. If the queue stays full for STALL_MS the peer
     * is dropped (socket closed -> its reader reports the loss), instead of freezing the host's game thread.
     */
    private static final class QueuedOut extends java.io.OutputStream {
        private static final int QUEUE = 512;
        private static final long STALL_MS = 3000;
        private final Socket sock;
        private final java.util.concurrent.ArrayBlockingQueue<byte[]> queue = new java.util.concurrent.ArrayBlockingQueue<byte[]>(QUEUE);
        private final java.io.OutputStream raw;
        private volatile boolean dead;

        QueuedOut(Socket socket, int id) throws IOException {
            this.sock = socket;
            this.raw = socket.getOutputStream();
            Thread t = new Thread(new Runnable() { public void run() { pump(); } }, "rally-lan-writer-" + id);
            t.setDaemon(true);
            t.start();
        }

        private void pump() { /*T*/ final long $t = rally3d.core.Trace.in(565); try {
            try {
                while (!dead && !sock.isClosed()) {
                    byte[] b = queue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (b != null) raw.write(b);
                }
            } catch (Exception e) {
                rally3d.core.RLog.caught("TheBluetooth.QueuedOut.pump", e);
            }
            dead = true;
            closeQuietly(sock);
         } finally { rally3d.core.Trace.out(565, $t); } }

        @Override public void write(byte[] b, int off, int len) throws IOException { /*T*/ final long $t = rally3d.core.Trace.in(566); try {
            if (dead) throw new IOException("peer writer stopped");
            byte[] copy = java.util.Arrays.copyOfRange(b, off, off + len);
            try {
                if (!queue.offer(copy, STALL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    dead = true;
                    closeQuietly(sock);
                    throw new IOException("peer stalled > " + STALL_MS + " ms, dropped");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
         } finally { rally3d.core.Trace.out(566, $t); } }

        @Override public void write(int b) throws IOException { write(new byte[]{(byte)b}, 0, 1); }
        @Override public void flush() {}
        @Override public void close() { dead = true; closeQuietly(sock); }
    }

    private static void writeRaw(BufferedOutputStream stream, byte[] packet) throws IOException {
        stream.write(packet);
        stream.flush();
    }

    private byte[] beaconBytes() { /*T*/ final long $t = rally3d.core.Trace.in(248); try {
        return (BEACON + TCP_PORT + "|" + gameVersion + "|" + playerName + "|" + (hostPassword.isEmpty() ? "0" : "1") + "|" + rally3d.core.Version.NAME).getBytes(StandardCharsets.UTF_8);
     } finally { rally3d.core.Trace.out(248, $t); } }

    /** Host: answers DISCOVER requests with a UNICAST beacon, so a phone that drops broadcast packets (Wi-Fi power save) still finds the host and gets the right (LAN) address. */
    private void startDiscoveryResponder() { /*T*/ final long $t = rally3d.core.Trace.in(249); try {
        try {
            final DatagramSocket ds = new DatagramSocket(null);
            ds.setReuseAddress(true);
            ds.bind(new InetSocketAddress(DISCOVERY_PORT));
            ds.setSoTimeout(500);
            discoverySocket = ds;
            Thread t = new Thread(new Runnable() { public void run() { /*T*/ final long $t = rally3d.core.Trace.in(146); try {
                byte[] buf = new byte[256];
                while (!closed && !ds.isClosed()) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    try {
                        ds.receive(p);
                        String text = new String(p.getData(), p.getOffset(), p.getLength(), StandardCharsets.UTF_8);
                        if (text.startsWith(DISCOVER)) {
                            byte[] reply = beaconBytes();
                            ds.send(new DatagramPacket(reply, reply.length, p.getAddress(), p.getPort()));
                            log("discover request from " + p.getAddress().getHostAddress() + ": unicast beacon sent");
                        }
                    } catch (SocketTimeoutException e) { rally3d.core.RLog.caught("TheBluetooth.discoveryResponder", e);
                    } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.discoveryResponder", e);
                        if (!closed) warn("discovery responder stopped: " + e, null);
                        break;
                    }
                }
             } finally { rally3d.core.Trace.out(146, $t); } } }, "rally-lan-discover");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.startDiscoveryResponder", e);
            warn("discovery responder failed (UDP " + DISCOVERY_PORT + "): " + e, null);
        }
     } finally { rally3d.core.Trace.out(249, $t); } }

    /** Subnet broadcast addresses of all usable interfaces (255.255.255.255 alone is dropped by some Android/Windows setups). */
    private static List<InetAddress> broadcastAddresses() { /*T*/ final long $t = rally3d.core.Trace.in(250); try {
        List<InetAddress> result = new ArrayList<InetAddress>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    InetAddress broadcast = address.getBroadcast();
                    if (broadcast != null && !result.contains(broadcast)) result.add(broadcast);
                }
            }
        } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.broadcastAddresses", e); }
        return result;
     } finally { rally3d.core.Trace.out(250, $t); } }

    private void broadcastBeacon() { /*T*/ final long $t = rally3d.core.Trace.in(139); try {
        DatagramSocket socket = beaconSocket;
        if (socket == null || socket.isClosed()) return;
        byte[] bytes = beaconBytes();
        boolean sent = false;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    InetAddress broadcast = address.getBroadcast();
                    if (broadcast == null) continue;
                    try {
                        socket.send(new DatagramPacket(bytes, bytes.length, broadcast, DISCOVERY_PORT));
                        sent = true;
                    } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.broadcastBeacon", e); warn("beacon send to " + broadcast + " failed: " + e, null); }
                }
            }
        } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.broadcastBeacon", e); warn("beacon: interface enumeration failed: " + e, e); }
        // Always try the limited broadcast and loopback too (VPN adapters have no broadcast address).
        try { socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)); sent = true; }
        catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.broadcastBeacon", e); warn("beacon to 255.255.255.255 failed: " + e, null); }
        if (!sent) warn("beacon: no broadcast packet could be sent (no network / Wi-Fi off?)", null);
        try { socket.send(new DatagramPacket(bytes, bytes.length, InetAddress.getLoopbackAddress(), DISCOVERY_PORT)); }
        catch (IOException ignored) { rally3d.core.RLog.caught("TheBluetooth.broadcastBeacon", ignored);}
     } finally { rally3d.core.Trace.out(139, $t); } }

    private static InetSocketAddress parseTarget(String target) { /*T*/ final long $t = rally3d.core.Trace.in(140); try {
        String host = target.trim();
        int port = TCP_PORT;
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            port = Integer.parseInt(host.substring(colon + 1).trim());
            host = host.substring(0, colon).trim();
        }
        if (host.length() == 0 || port < 1 || port > 65535) throw new IllegalArgumentException(rally3d.core.Lang.t("bad address"));
        InetSocketAddress address = new InetSocketAddress(host, port);
        if (address.isUnresolved()) throw new IllegalArgumentException(rally3d.core.Lang.t("unknown host"));
        return address;
     } finally { rally3d.core.Trace.out(140, $t); } }

    private void clientSession() { /*T*/ final long $t = rally3d.core.Trace.in(141); try {
        if (hasDirectTarget()) {
            try {
                InetSocketAddress address = parseTarget(directTarget);
                synchronized (this) {
                    servers[0] = address;
                    protectedServers[0] = false;
                    incompatibleServers[0] = false;   // direct IP: the host checks the version itself during the join handshake
                    deviceNames[0] = directTarget;
                    deviceCount = 1;
                }
                adapterAvailable = true;
            } catch (RuntimeException e) { rally3d.core.RLog.caught("TheBluetooth.clientSession", e);
                warn("direct target '" + directTarget + "' is invalid: " + e, e);
                connectError = rally3d.core.Lang.t("Invalid address: ") + directTarget;
                adapterAvailable = false;
            }
            inquiryDone = true;
        } else {
            log("client: discovering hosts on UDP " + DISCOVERY_PORT);
            discover();
            log("client: discovery finished, hosts found: " + deviceCount);
        }
        while (!closed && awaitingDevice) {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (!closed && selectedDevice >= 0 && selectedDevice < deviceCount) {
            Socket socket = null;
            try {
                if (incompatibleServers[selectedDevice]) {
                    remoteVersion = hostVersions[selectedDevice];
                    warn("client: refusing to join " + servers[selectedDevice] + ": host version " + remoteVersion + " != ours " + rally3d.core.Version.NAME, null);
                    joinRejected = true;
                    joinRejectReason = 4;
                    clientDone = true;
                    return;
                }
                socket = new Socket();
                log("client: connecting to " + servers[selectedDevice] + " (timeout 5s)");
                socket.connect(servers[selectedDevice], 5000);
                log("client: TCP connected, sending hello");
                socket.setSoTimeout(5000);
                BufferedOutputStream helloOut = new BufferedOutputStream(socket.getOutputStream());
                byte[] name = playerName.getBytes(StandardCharsets.UTF_8);
                byte[] password = joinPassword.getBytes(StandardCharsets.UTF_8);
                byte[] ver = rally3d.core.Version.NAME.getBytes(StandardCharsets.UTF_8);
                if (password.length > 96 || name.length + password.length + ver.length + 3 > 124) throw new IOException(rally3d.core.Lang.t("Nickname or password is too long"));
                byte[] hello = new byte[5 + name.length + password.length + ver.length];
                hello[0] = 17;                                   // 17 = hello with game version (16 = old clients, rejected by the host)
                hello[1] = (byte)(name.length + password.length + ver.length + 3);
                hello[2] = (byte)name.length;
                hello[3] = (byte)password.length;
                hello[4] = (byte)ver.length;
                System.arraycopy(name, 0, hello, 5, name.length);
                System.arraycopy(password, 0, hello, 5 + name.length, password.length);
                System.arraycopy(ver, 0, hello, 5 + name.length + password.length, ver.length);
                writeRaw(helloOut, hello);
                DataInputStream response = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                int responseType = response.readUnsignedByte();
                int responseSize = response.readUnsignedByte();
                if (responseType != 14 || responseSize < 2) throw new IOException(rally3d.core.Lang.t("Invalid LAN join response"));
                int accepted = response.readUnsignedByte();
                assignedPlayerId = response.readUnsignedByte();
                if (accepted != 1 && assignedPlayerId == 4) {
                    String hv = "unknown";
                    if (responseSize >= 3) {
                        int vl = response.readUnsignedByte();
                        byte[] vb = new byte[Math.min(vl, 64)];
                        response.readFully(vb);
                        hv = new String(vb, StandardCharsets.UTF_8);
                    }
                    remoteVersion = hv;
                }
                log("client: host response accepted=" + accepted + " id/reason=" + assignedPlayerId);
                if (accepted != 1 || assignedPlayerId < 1 || assignedPlayerId >= MAX_PLAYERS) {
                    warn("client: join rejected, reason " + assignedPlayerId + " (1=nickname, 2=full, 3=password)", null);
                    joinRejected = true;
                    joinRejectReason = assignedPlayerId;
                    closeQuietly(socket);
                } else {
                    attach(socket, 0, response);
                    clientConnected = true;
                    log("joined " + servers[selectedDevice]);
                }
            } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.clientSession", e);
                warn("connect failed: " + e, e);
                connectError = e.getMessage() == null ? e.toString() : e.getMessage();
                clientConnected = false;
                closeQuietly(socket);
            }
        }
        clientDone = true;
     } finally { rally3d.core.Trace.out(141, $t); } }

    private void discover() { /*T*/ final long $t = rally3d.core.Trace.in(142); try {
        DatagramSocket discovery = null;
        try {
            discovery = new DatagramSocket(null);
            discovery.setReuseAddress(true);
            discovery.bind(new InetSocketAddress(DISCOVERY_PORT));
            discovery.setBroadcast(true);
            discovery.setSoTimeout(250);
            adapterAvailable = true;
            long until = System.currentTimeMillis() + 4500;
            byte[] request = DISCOVER.getBytes(StandardCharsets.UTF_8);
            while (!closed && System.currentTimeMillis() < until) {
                try {
                    discovery.send(new DatagramPacket(request, request.length,
                            InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));
                } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.discover", e); warn("discovery request send failed: " + e, null); }
                for (InetAddress broadcast : broadcastAddresses()) {
                    try { discovery.send(new DatagramPacket(request, request.length, broadcast, DISCOVERY_PORT)); }
                    catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.discover", e); }
                }
                byte[] data = new byte[256];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                try {
                    discovery.receive(packet);
                    acceptBeacon(packet);
                } catch (SocketTimeoutException ignored) { rally3d.core.RLog.caught("TheBluetooth.discover", ignored);}
            }
        } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.discover", e);
            warn("discovery socket error (UDP " + DISCOVERY_PORT + "): " + e, e);
            adapterAvailable = false;
        } finally {
            if (discovery != null) discovery.close();
            inquiryDone = true;
        }
     } finally { rally3d.core.Trace.out(142, $t); } }

    private synchronized void acceptBeacon(DatagramPacket packet) { /*T*/ final long $t = rally3d.core.Trace.in(143); try {
        String line = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
        boolean legacy = line.startsWith(LEGACY_BEACON);
        if (!legacy && !line.startsWith(BEACON)) return;
        String[] fields = line.split("\\|", 7);
        if (fields.length < 5 || (!legacy && fields.length < 7)) return;
        try {
            int port = Integer.parseInt(fields[2]);
            InetSocketAddress address = new InetSocketAddress(packet.getAddress(), port);
            for (int i = 0; i < deviceCount; i++) if (servers[i].equals(address)) return;
            if (deviceCount >= servers.length) return;
            servers[deviceCount] = address;
            String name = sanitize(fields[4]);
            String version = "Vanilla".equals(fields[3]) ? "Vanilla" : "Modified";
            boolean password = fields.length >= 6 && "1".equals(fields[5]);
            String hostVer = legacy ? "old (before 2.0-alpha4)" : fields[6].replace('\n', ' ').replace('\r', ' ').trim();
            if (hostVer.length() > 32) hostVer = hostVer.substring(0, 32);
            boolean incompatible = legacy || !rally3d.core.Version.NAME.equals(hostVer);
            protectedServers[deviceCount] = password;
            incompatibleServers[deviceCount] = incompatible;
            hostVersions[deviceCount] = hostVer;
            deviceNames[deviceCount] = name + "  [" + version + "]" + (legacy ? rally3d.core.Lang.t("  [OLD VERSION - INCOMPATIBLE]") : incompatible ? "  [v" + hostVer + rally3d.core.Lang.t(" - INCOMPATIBLE]") : "  [v" + hostVer + "]") + (password ? rally3d.core.Lang.t("  [Password]") : "")
                    + "  (" + packet.getAddress().getHostAddress() + ")";
            if (incompatible) warn("discovered host with a DIFFERENT game version: " + deviceNames[deviceCount] + " (we are " + rally3d.core.Version.NAME + ") - cannot play together", null);
            deviceCount++;
            log("discovered host: " + deviceNames[deviceCount - 1]);
        } catch (RuntimeException e) { rally3d.core.RLog.caught("TheBluetooth.acceptBeacon", e); warn("bad beacon '" + line + "': " + e, null); }
     } finally { rally3d.core.Trace.out(143, $t); } }

    void close() { /*T*/ final long $t = rally3d.core.Trace.in(144); try {
        log("session closing (host=" + hostMode + ")");
        closed = true;
        awaitingDevice = false;
        closeQuietly(connection);
        for (Socket socket : peerSockets) closeQuietly(socket);
        closeQuietly(listener);
        if (beaconSocket != null) beaconSocket.close();
        if (discoverySocket != null) discoverySocket.close();
        thread.interrupt();
     } finally { rally3d.core.Trace.out(144, $t); } }

    private static void closeQuietly(java.io.Closeable closeable) { /*T*/ final long $t = rally3d.core.Trace.in(145); try {
        if (closeable != null) try { closeable.close(); } catch (IOException e) { rally3d.core.RLog.caught("TheBluetooth.closeQuietly", e); warn("close failed: " + e, null); }
     } finally { rally3d.core.Trace.out(145, $t); } }

    public void run() { /*T*/ final long $t = rally3d.core.Trace.in(146); try {
        while (!closed && waiting) {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        if (closed) return;
        if (hostMode) hostSession(); else clientSession();  } finally { rally3d.core.Trace.out(146, $t); } }
}
