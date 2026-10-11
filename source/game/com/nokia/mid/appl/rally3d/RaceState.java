package com.nokia.mid.appl.rally3d;

import java.io.DataInputStream;
import java.io.DataOutputStream;

class RaceState {
    int carIndex;
    int trackIndex;
    int mode;
    int raceTime;
    int checkpointsPassed;

    RaceState() {
    }

    void write(DataOutputStream dataOutputStream) {
        try {
            dataOutputStream.writeInt(this.carIndex);
            dataOutputStream.writeInt(this.trackIndex);
            dataOutputStream.writeInt(this.mode);
            dataOutputStream.writeInt(this.raceTime);
            dataOutputStream.writeInt(this.checkpointsPassed);
        }
        catch (Exception exception) { rally3d.core.RLog.caught("RaceState.write", exception);
            // empty catch block
        }
    }

    void read(DataInputStream dataInputStream) {
        try {
            this.carIndex = dataInputStream.readInt();
            this.trackIndex = dataInputStream.readInt();
            this.mode = dataInputStream.readInt();
            this.raceTime = dataInputStream.readInt();
            this.checkpointsPassed = dataInputStream.readInt();
        }
        catch (Exception exception) { rally3d.core.RLog.caught("RaceState.read", exception);
            // empty catch block
        }
    }

    void copyFrom(RaceState raceState) {
        this.carIndex = raceState.carIndex;
        this.trackIndex = raceState.trackIndex;
        this.mode = raceState.mode;
        this.raceTime = raceState.raceTime;
        this.checkpointsPassed = raceState.checkpointsPassed;
    }
}

