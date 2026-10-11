package com.nokia.mid.appl.rally3d;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import javax.microedition.rms.RecordStore;

class SaveData {
    int soundLevel = 3;   // default: Loud (0 off, 1 low, 2 normal, 3 loud)
    int vibrationOff = 0;
    int speedKmh = 1;
    int autoAccelOff = 0;
    int savedFps = 10;
    int reservedA = -1;
    int reservedB = 1;
    String playerName = Texts.defaultPlayerName;
    int fontHeightBold;
    int fontHeightSmall;
    int[] trackMedals = new int[]{3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4};
    int[] carLocks = new int[]{0, 1, 1, 1};
    String[][] bestNames = new String[5][12];
    int[][] bestTimes = new int[5][12];
    int[] savedGameValid;
    CarState[] savedCars;
    CameraState[] savedCameras;
    RaceState[] savedRaces;
    String defaultName;
    static final int[][] defaultTimes = new int[][]{{556, 599, 752, 848}, {832, 907, 1053, 1191}, {811, 888, 1011, 1198}, {957, 1065, 1249, 1401}, {581, 632, 721, 800}, {784, 869, 990, 1091}, {637, 705, 782, 892}, {714, 761, 845, 955}, {564, 631, 708, 812}, {828, 930, 1043, 1231}, {869, 962, 1093, 1187}, {1065, 1144, 1299, 1495}};

    int defaultTime(int n, int n2) {
        int n3 = 0;
        if (n2 > 3) {
            n3 = (n2 - 3) * 100;
            n2 = 3;
        }
        return defaultTimes[n][n2] + n3;
    }

    public SaveData(String string) {
        int n;
        boolean bl = false;
        boolean failed = false;
        RecordStore recordStore = null;
        this.defaultName = string;
        this.savedGameValid = new int[2];
        this.savedCars = new CarState[2];
        this.savedCameras = new CameraState[2];
        this.savedRaces = new RaceState[2];
        for (n = 0; n < 2; ++n) {
            this.savedGameValid[n] = 0;
            this.savedCars[n] = new CarState();
            this.savedCameras[n] = new CameraState();
            this.savedRaces[n] = new RaceState();
        }
        this.resetBestTimes();
        this.resetProgress();
        try {
            recordStore = RecordStore.openRecordStore((String)"rally3d101", (boolean)false);
            if (recordStore.getNumRecords() > 0) {
                byte[] byArray = recordStore.getRecord(1);
                DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(byArray, 0, byArray.length));
                this.soundLevel = dataInputStream.readInt();
                this.vibrationOff = dataInputStream.readInt();
                this.speedKmh = dataInputStream.readInt();
                this.autoAccelOff = dataInputStream.readInt();
                this.reservedA = dataInputStream.readInt();
                this.savedFps = dataInputStream.readInt();
                this.reservedB = dataInputStream.readInt();
                this.playerName = dataInputStream.readUTF();
                this.fontHeightBold = dataInputStream.readInt();
                this.fontHeightSmall = dataInputStream.readInt();
                for (n = 0; n < 12; ++n) {
                    this.trackMedals[n] = dataInputStream.readInt();
                }
                for (n = 0; n < 4; ++n) {
                    this.carLocks[n] = dataInputStream.readInt();
                }
                for (int i = 0; i < 12; ++i) {
                    for (n = 0; n < 5; ++n) {
                        this.bestNames[n][i] = dataInputStream.readUTF();
                        this.bestTimes[n][i] = dataInputStream.readInt();
                    }
                }
                for (n = 0; n < 2; ++n) {
                    this.savedGameValid[n] = dataInputStream.readInt();
                    this.savedCars[n].read(dataInputStream);
                    this.savedCameras[n].read(dataInputStream);
                    this.savedRaces[n].read(dataInputStream);
                }
                bl = true;
            }
            recordStore.closeRecordStore();
        }
        catch (Exception exception) { rally3d.core.RLog.caught("SaveData.SaveData", exception);
            System.out.println("ReadException... " + exception);
            failed = true;
            this.resetBestTimes();
            this.resetProgress();
            this.soundLevel = 3; this.vibrationOff = 0; this.speedKmh = 1; this.autoAccelOff = 0; this.savedFps = 10;
            for (n = 0; n < 2; ++n) this.savedGameValid[n] = 0;
        }
        this.sanitize();
        if (!bl && !failed) {
            this.save();
        }
    }

    /** Keeps values a corrupted record could have produced inside the ranges the menus and the race code index with. */
    private static boolean fin(float f) { return !Float.isNaN(f) && !Float.isInfinite(f); }

    private void sanitize() { /*T*/ final long $t = rally3d.core.Trace.in(502); try {
        if (this.soundLevel < 0 || this.soundLevel > 3) this.soundLevel = 3;
        if (this.vibrationOff != 0) this.vibrationOff = 1;
        if (this.speedKmh != 0) this.speedKmh = 1;
        if (this.autoAccelOff != 0) this.autoAccelOff = 1;
        if (this.savedFps < 0 || this.savedFps > 1000) this.savedFps = 10;
        if (this.playerName == null || this.playerName.length() == 0 || this.playerName.length() > 64) this.playerName = Texts.defaultPlayerName;
        for (int i = 0; i < 12; ++i) if (this.trackMedals[i] < 0 || this.trackMedals[i] > 4) this.trackMedals[i] = i < 2 ? 3 : 4;
        for (int i = 0; i < 4; ++i) if (this.carLocks[i] < 0 || this.carLocks[i] > 1) this.carLocks[i] = i < 1 ? 0 : 1;
        for (int i = 0; i < 2; ++i) if (this.savedGameValid[i] != 0 && this.savedGameValid[i] != 1) this.savedGameValid[i] = 0;
        for (int i = 0; i < 12; ++i) for (int j = 0; j < 5; ++j) {   // best-time table: a corrupt row must not poison record comparison / menus
            if (this.bestTimes[j][i] < 0 || this.bestTimes[j][i] > 36000000) this.bestTimes[j][i] = this.defaultTime(i, j);
            String nm = this.bestNames[j][i];
            if (nm == null || nm.length() > 64) this.bestNames[j][i] = this.defaultName == null ? "" : this.defaultName;
        }
        for (int i = 0; i < 2; ++i) if (this.savedGameValid[i] == 1) {   // saved game with NaN / Infinity / negative index would freeze or crash the resume
            CarState c = this.savedCars[i]; CameraState m = this.savedCameras[i];
            boolean ok = c.segmentIndex >= 0 && m.segmentIndex >= 0 && fin(c.speed) && fin(c.steer) && fin(c.steerLean) && fin(m.yaw);
            for (int k = 0; k < 3 && ok; ++k) ok = fin(c.position.v[k]) && fin(c.velocity.v[k]) && fin(c.rotation.v[k]) && fin(m.position.v[k]);
            if (!ok) { this.savedGameValid[i] = 0; rally3d.core.RLog.w("save", "saved game " + i + " had invalid values: discarded"); }
        }
     } finally { rally3d.core.Trace.out(502, $t); } }

    synchronized void save() { /*T*/ final long $t = rally3d.core.Trace.in(117); try {
        rally3d.core.RLog.i("save", "saving game data (player " + this.playerName + ")");
        RecordStore recordStore = null;
        try {
            int n;
            recordStore = RecordStore.openRecordStore((String)"rally3d101", (boolean)true);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(8192);
            DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
            dataOutputStream.writeInt(this.soundLevel);
            dataOutputStream.writeInt(this.vibrationOff);
            dataOutputStream.writeInt(this.speedKmh);
            dataOutputStream.writeInt(this.autoAccelOff);
            dataOutputStream.writeInt(this.reservedA);
            dataOutputStream.writeInt(this.savedFps);
            dataOutputStream.writeInt(this.reservedB);
            dataOutputStream.writeUTF(this.playerName);
            dataOutputStream.writeInt(this.fontHeightBold);
            dataOutputStream.writeInt(this.fontHeightSmall);
            for (n = 0; n < 12; ++n) {
                dataOutputStream.writeInt(this.trackMedals[n]);
            }
            for (n = 0; n < 4; ++n) {
                dataOutputStream.writeInt(this.carLocks[n]);
            }
            for (int i = 0; i < 12; ++i) {
                for (n = 0; n < 5; ++n) {
                    dataOutputStream.writeUTF(this.bestNames[n][i]);
                    dataOutputStream.writeInt(this.bestTimes[n][i]);
                }
            }
            for (n = 0; n < 2; ++n) {
                dataOutputStream.writeInt(this.savedGameValid[n]);
                this.savedCars[n].write(dataOutputStream);
                this.savedCameras[n].write(dataOutputStream);
                this.savedRaces[n].write(dataOutputStream);
            }
            dataOutputStream.flush();
            dataOutputStream.close();
            byte[] byArray = byteArrayOutputStream.toByteArray();
            try {
                recordStore.setRecord(1, byArray, 0, byArray.length);
            }
            catch (Exception exception) { rally3d.core.RLog.caught("SaveData.save", exception);
                recordStore.addRecord(byArray, 0, byArray.length);
            }
            recordStore.closeRecordStore();
        }
        catch (Exception exception) { rally3d.core.RLog.caught("SaveData.save", exception);
            System.out.println("WriteException... " + exception);
        }
     } finally { rally3d.core.Trace.out(117, $t); } }

    boolean hasProgress() { /*T*/ final long $t = rally3d.core.Trace.in(118); try {
        for (int i = 0; i < 12; ++i) {
            if ((i >= 2 || this.trackMedals[i] >= 3) && (i < 2 || this.trackMedals[i] >= 4)) continue;
            return true;
        }
        return false;
     } finally { rally3d.core.Trace.out(118, $t); } }

    void resetProgress() { /*T*/ final long $t = rally3d.core.Trace.in(119); try {
        int n;
        for (n = 0; n < 12; ++n) {
            this.trackMedals[n] = n < 2 ? 3 : 4;
        }
        for (n = 0; n < 4; ++n) {
            this.carLocks[n] = n < 1 ? 0 : 1;
        }
     } finally { rally3d.core.Trace.out(119, $t); } }

    void resetBestTimes() { /*T*/ final long $t = rally3d.core.Trace.in(120); try {
        for (int i = 0; i < 12; ++i) {
            for (int j = 0; j < 5; ++j) {
                this.bestNames[j][i] = this.defaultName;
                this.bestTimes[j][i] = this.defaultTime(i, j);
            }
        }
     } finally { rally3d.core.Trace.out(120, $t); } }

    void unlockAll() { /*T*/ final long $t = rally3d.core.Trace.in(121); try {
        int n;
        for (n = 0; n < 12; ++n) {
            if (this.trackMedals[n] <= 3) continue;
            this.trackMedals[n] = 3;
        }
        for (n = 0; n < 4; ++n) {
            this.carLocks[n] = 0;
        }
     } finally { rally3d.core.Trace.out(121, $t); } }
}

