package com.nokia.mid.appl.rally3d;

import java.io.DataInputStream;
import java.io.DataOutputStream;

class CameraState {
    int segmentIndex = 0;
    float yaw = 180.0f;
    Vec3 position = new Vec3();

    CameraState() {
    }

    void write(DataOutputStream dataOutputStream) {
        try {
            dataOutputStream.writeInt(this.segmentIndex);
            dataOutputStream.writeFloat(this.yaw);
            dataOutputStream.writeFloat(this.position.v[0]);
            dataOutputStream.writeFloat(this.position.v[1]);
            dataOutputStream.writeFloat(this.position.v[2]);
        }
        catch (Exception exception) { rally3d.core.RLog.caught("CameraState.write", exception);
            // empty catch block
        }
    }

    void read(DataInputStream dataInputStream) {
        try {
            this.segmentIndex = dataInputStream.readInt();
            this.yaw = dataInputStream.readFloat();
            this.position.v[0] = dataInputStream.readFloat();
            this.position.v[1] = dataInputStream.readFloat();
            this.position.v[2] = dataInputStream.readFloat();
        }
        catch (Exception exception) { rally3d.core.RLog.caught("CameraState.read", exception);
            // empty catch block
        }
    }

    void copyFrom(CameraState cameraState) {
        this.segmentIndex = cameraState.segmentIndex;
        this.yaw = cameraState.yaw;
        this.position.v[0] = cameraState.position.v[0];
        this.position.v[1] = cameraState.position.v[1];
        this.position.v[2] = cameraState.position.v[2];
    }
}

