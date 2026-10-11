package com.nokia.mid.appl.rally3d;

class TrackSegment {
    Vec3 startPos = new Vec3();
    Vec3 startDir = new Vec3();
    short profileStart;
    short profileEnd;
    float startHeight;
    float slope;
    float curvature;
    float yawStep;
    short centerMarking;
    short leftSignType;
    short leftSignLane;
    short rightSignType;
    short rightSignLane;
    float accumYaw;

    TrackSegment() {
    }
}

