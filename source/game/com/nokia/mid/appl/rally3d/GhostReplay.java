package com.nokia.mid.appl.rally3d;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** Compact recording of the best run for one (track, car) pair; timeline is real milliseconds since the start. */
final class GhostReplay {
    private static final int VERSION = 2;          // v1 stored 0.1 s ticks (one pose per tick); still readable
    private static final int MAX_FRAMES = 30050;
    private static final int MIN_STEP_MS = 16;     // <= ~62 poses/s whatever the render fps -> 300 s race stays under MAX_FRAMES
    private static final int MAX_TIME_MS = 600000;   // > MAX_FRAMES * MIN_STEP_MS (~481 s): every recorded run must decode again
    int finishTime;                                // 0.1 s units, same as RaceState.raceTime (HUD / split comparison)
    int finishMs;                                  // exact finish time, used to decide whether a run beats the record
    int carIndex;
    private int[] times = new int[256];
    private float[] poses = new float[256 * 6];
    private int count;

    void record(int elapsedTime, CarState car) { /*T*/ final long $t = rally3d.core.Trace.in(19); try {
        recordPose(elapsedTime, car, false);
     } finally { rally3d.core.Trace.out(19, $t); } }

    /** force=true always appends (used for the exact finish pose), otherwise poses closer than MIN_STEP_MS are dropped. */
    void recordPose(int elapsedTime, CarState car, boolean force) { /*T*/ final long $t = rally3d.core.Trace.in(277); try {
        if (car == null || elapsedTime < 0 || elapsedTime > MAX_TIME_MS) return;
        if (count > 0 && elapsedTime < times[count - 1]) return;
        if (count > 0 && elapsedTime == times[count - 1]) {
            writePose((count - 1) * 6, car);
            return;
        }
        if (!force && count > 0 && elapsedTime - times[count - 1] < MIN_STEP_MS) return;
        if (count >= MAX_FRAMES) return;
        if (count == times.length) {
            int size = Math.min(MAX_FRAMES, times.length * 2);
            times = java.util.Arrays.copyOf(times, size);
            poses = java.util.Arrays.copyOf(poses, size * 6);
        }
        times[count] = elapsedTime;
        writePose(count * 6, car);
        count++;
     } finally { rally3d.core.Trace.out(277, $t); } }

    int lastTimeMs() { return count == 0 ? 0 : times[count - 1]; }

    private void writePose(int offset, CarState car) { /*T*/ final long $t = rally3d.core.Trace.in(20); try {
        poses[offset] = car.position.v[0];
        poses[offset + 1] = car.position.v[1];
        poses[offset + 2] = car.position.v[2];
        poses[offset + 3] = car.rotation.v[0];
        poses[offset + 4] = car.rotation.v[1];
        poses[offset + 5] = car.rotation.v[2];
     } finally { rally3d.core.Trace.out(20, $t); } }

    boolean sample(int time, Vec3 position, Vec3 rotation) { /*T*/ final long $t = rally3d.core.Trace.in(21); try {
        if (count == 0) return false;
        int next = java.util.Arrays.binarySearch(times, 0, count, time);
        if (next >= 0) {
            copyPose(next, position, rotation);
            return true;
        }
        next = -next - 1;
        if (next <= 0) { copyPose(0, position, rotation); return true; }
        if (next >= count) { copyPose(count - 1, position, rotation); return true; }
        int previous = next - 1;
        int span = times[next] - times[previous];
        float t = span <= 0 ? 0.0f : (time - times[previous]) / (float)span;
        int a = previous * 6, b = next * 6;
        position.v[0] = lerp(poses[a], poses[b], t);
        position.v[1] = lerp(poses[a + 1], poses[b + 1], t);
        position.v[2] = lerp(poses[a + 2], poses[b + 2], t);
        rotation.v[0] = lerp(poses[a + 3], poses[b + 3], t);
        float yawDelta = (poses[b + 4] - poses[a + 4] + 540.0f) % 360.0f - 180.0f;
        rotation.v[1] = poses[a + 4] + yawDelta * t;
        rotation.v[2] = lerp(poses[a + 5], poses[b + 5], t);
        return true;
     } finally { rally3d.core.Trace.out(21, $t); } }

    private void copyPose(int frame, Vec3 position, Vec3 rotation) { /*T*/ final long $t = rally3d.core.Trace.in(22); try {
        int p = frame * 6;
        position.v[0] = poses[p]; position.v[1] = poses[p + 1]; position.v[2] = poses[p + 2];
        rotation.v[0] = poses[p + 3]; rotation.v[1] = poses[p + 4]; rotation.v[2] = poses[p + 5];
     } finally { rally3d.core.Trace.out(22, $t); } }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    byte[] encode() { /*T*/ final long $t = rally3d.core.Trace.in(23); try {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(20 + count * 28);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            out.writeInt(finishTime);
            out.writeInt(finishMs);
            out.writeInt(carIndex);
            out.writeInt(count);
            for (int i = 0; i < count; i++) {
                out.writeInt(times[i]);
                for (int j = 0; j < 6; j++) out.writeFloat(poses[i * 6 + j]);
            }
            out.close();
            return bytes.toByteArray();
        } catch (Exception e) { rally3d.core.RLog.caught("GhostReplay.encode", e); return null; }
     } finally { rally3d.core.Trace.out(23, $t); } }

    static GhostReplay decode(byte[] data) { /*T*/ final long $t = rally3d.core.Trace.in(24); try {
        if (data == null || data.length < 16 || data.length > 1024 * 1024) return null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            int version = in.readInt();
            if (version != 1 && version != VERSION) return null;
            GhostReplay replay = new GhostReplay();
            replay.finishTime = in.readInt();
            replay.finishMs = version == 1 ? replay.finishTime * 100 : in.readInt();
            replay.carIndex = in.readInt();
            int count = in.readInt();
            if (replay.finishTime < 0 || replay.finishMs < 0 || replay.carIndex < 0 || replay.carIndex > 3 || count < 1 || count > MAX_FRAMES) return null;
            replay.times = new int[Math.max(256, count)];
            replay.poses = new float[Math.max(256, count) * 6];
            int previousTime = -1;
            for (int i = 0; i < count; i++) {
                int time = in.readInt();
                if (version == 1) time *= 100;
                if (time < previousTime || time > MAX_TIME_MS) return null;
                replay.times[i] = time;
                previousTime = time;
                for (int j = 0; j < 6; j++) {
                    float value = in.readFloat();
                    if (!Float.isFinite(value)) return null;
                    replay.poses[i * 6 + j] = value;
                }
            }
            if (in.available() != 0) return null;
            replay.count = count;
            return replay;
        } catch (Exception e) { rally3d.core.RLog.caught("GhostReplay.decode", e); return null; }
     } finally { rally3d.core.Trace.out(24, $t); } }
}
