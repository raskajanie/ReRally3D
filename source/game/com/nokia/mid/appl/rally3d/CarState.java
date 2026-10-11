package com.nokia.mid.appl.rally3d;

import java.io.DataInputStream;
import java.io.DataOutputStream;

class CarState {
    float speed = 0.0f;
    float steer = 0.0f;
    float steerLean = 0.0f;
    int segmentIndex = 0;
    Vec3 position = new Vec3();
    Vec3 velocity = new Vec3();
    Vec3 rotation = new Vec3();
    Vec3[] wheelPositions = new Vec3[4];
    Vec3[] bodyCorners = new Vec3[4];
    float[] wheelY = new float[4];
    float[] wheelVelY = new float[4];
    float[] prevGroundY = new float[4];

    CarState() {
        for (int i = 0; i < 4; ++i) {
            this.wheelPositions[i] = new Vec3();
            this.bodyCorners[i] = new Vec3();
            this.wheelY[i] = 0.0f;
            this.wheelVelY[i] = 0.0f;
            this.prevGroundY[i] = 0.0f;
        }
    }

    void write(DataOutputStream dataOutputStream) {
        try {
            dataOutputStream.writeFloat(this.speed);
            dataOutputStream.writeFloat(this.steer);
            dataOutputStream.writeFloat(this.steerLean);
            dataOutputStream.writeInt(this.segmentIndex);
            dataOutputStream.writeFloat(this.position.v[0]);
            dataOutputStream.writeFloat(this.position.v[1]);
            dataOutputStream.writeFloat(this.position.v[2]);
            dataOutputStream.writeFloat(this.velocity.v[0]);
            dataOutputStream.writeFloat(this.velocity.v[1]);
            dataOutputStream.writeFloat(this.velocity.v[2]);
            dataOutputStream.writeFloat(this.rotation.v[0]);
            dataOutputStream.writeFloat(this.rotation.v[1]);
            dataOutputStream.writeFloat(this.rotation.v[2]);
            for (int i = 0; i < 4; ++i) {
                dataOutputStream.writeFloat(this.wheelPositions[i].v[0]);
                dataOutputStream.writeFloat(this.wheelPositions[i].v[1]);
                dataOutputStream.writeFloat(this.wheelPositions[i].v[2]);
                dataOutputStream.writeFloat(this.bodyCorners[i].v[0]);
                dataOutputStream.writeFloat(this.bodyCorners[i].v[1]);
                dataOutputStream.writeFloat(this.bodyCorners[i].v[2]);
                dataOutputStream.writeFloat(this.wheelY[i]);
                dataOutputStream.writeFloat(this.wheelVelY[i]);
                dataOutputStream.writeFloat(this.prevGroundY[i]);
            }
        }
        catch (Exception exception) { rally3d.core.RLog.caught("CarState.write", exception);
            // empty catch block
        }
    }

    void read(DataInputStream dataInputStream) {
        try {
            this.speed = dataInputStream.readFloat();
            this.steer = dataInputStream.readFloat();
            this.steerLean = dataInputStream.readFloat();
            this.segmentIndex = dataInputStream.readInt();
            this.position.v[0] = dataInputStream.readFloat();
            this.position.v[1] = dataInputStream.readFloat();
            this.position.v[2] = dataInputStream.readFloat();
            this.velocity.v[0] = dataInputStream.readFloat();
            this.velocity.v[1] = dataInputStream.readFloat();
            this.velocity.v[2] = dataInputStream.readFloat();
            this.rotation.v[0] = dataInputStream.readFloat();
            this.rotation.v[1] = dataInputStream.readFloat();
            this.rotation.v[2] = dataInputStream.readFloat();
            for (int i = 0; i < 4; ++i) {
                this.wheelPositions[i].v[0] = dataInputStream.readFloat();
                this.wheelPositions[i].v[1] = dataInputStream.readFloat();
                this.wheelPositions[i].v[2] = dataInputStream.readFloat();
                this.bodyCorners[i].v[0] = dataInputStream.readFloat();
                this.bodyCorners[i].v[1] = dataInputStream.readFloat();
                this.bodyCorners[i].v[2] = dataInputStream.readFloat();
                this.wheelY[i] = dataInputStream.readFloat();
                this.wheelVelY[i] = dataInputStream.readFloat();
                this.prevGroundY[i] = dataInputStream.readFloat();
            }
        }
        catch (Exception exception) { rally3d.core.RLog.caught("CarState.read", exception);
            // empty catch block
        }
    }

    void copyFrom(CarState carState) {
        this.speed = carState.speed;
        this.steer = carState.steer;
        this.steerLean = carState.steerLean;
        this.segmentIndex = carState.segmentIndex;
        this.position.v[0] = carState.position.v[0];
        this.position.v[1] = carState.position.v[1];
        this.position.v[2] = carState.position.v[2];
        this.velocity.v[0] = carState.velocity.v[0];
        this.velocity.v[1] = carState.velocity.v[1];
        this.velocity.v[2] = carState.velocity.v[2];
        this.rotation.v[0] = carState.rotation.v[0];
        this.rotation.v[1] = carState.rotation.v[1];
        this.rotation.v[2] = carState.rotation.v[2];
        for (int i = 0; i < 4; ++i) {
            this.wheelPositions[i].v[0] = carState.wheelPositions[i].v[0];
            this.wheelPositions[i].v[1] = carState.wheelPositions[i].v[1];
            this.wheelPositions[i].v[2] = carState.wheelPositions[i].v[2];
            this.bodyCorners[i].v[0] = carState.bodyCorners[i].v[0];
            this.bodyCorners[i].v[1] = carState.bodyCorners[i].v[1];
            this.bodyCorners[i].v[2] = carState.bodyCorners[i].v[2];
            this.wheelY[i] = carState.wheelY[i];
            this.wheelVelY[i] = carState.wheelVelY[i];
            this.prevGroundY[i] = carState.prevGroundY[i];
        }
    }
}

