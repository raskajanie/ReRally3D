package com.nokia.mid.appl.rally3d;

import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.IndexBuffer;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;

class RoadSegmentMesh {
    static final int[][] profiles = new int[][]{{8, 3, 6, 2, 3, 1, 2, 0, 8, 3, 6, 2, 3, 1, 2, 0, 30, 1, 2, 2, 2, 1, 1, 1, 0, 0, 0}, {8, 3, 6, 2, 3, 1, 2, 0, 6, 5, 6, 5, 3, 2, 2, 0, 25, 1, 2, 2, 2, 1, 1, 1, 0, 0, 0}, {6, 5, 6, 5, 3, 2, 2, 0, 8, 3, 6, 2, 3, 1, 2, 0, 25, 1, 2, 2, 2, 1, 1, 1, 0, 0, 0}, {6, 13, 6, 5, 3, 2, 2, 0, 6, 13, 6, 5, 3, 2, 2, 0, 25, 1, 1, 2, 3, 0, 1, 1, 1, 0, 0}, {6, 5, 3, 2, 3, 2, 2, 0, 6, 18, 3, 12, 3, 6, 2, 0, 12, 1, 4, 1, 2, 0, 1, 1, 0, 0, 0}, {6, 18, 3, 12, 3, 6, 2, 0, 6, 5, 3, 2, 3, 2, 2, 0, 12, 1, 4, 1, 2, 0, 1, 1, 0, 0, 0}, {6, 18, 3, 12, 3, 6, 2, 0, 6, 18, 3, 12, 3, 6, 2, 0, 12, 1, 4, 1, 2, 0, 0, 1, 0, 0, 0}, {5, -20, 4, -6, 3, -1, 2, 0, 5, -20, 4, -6, 3, -1, 2, 0, 37, 0, 2, 2, 2, 1, 1, 1, 0, 0, 0}, {5, -20, 4, -6, 3, -1, 2, 0, 8, 3, 6, 2, 3, 1, 2, 0, 29, 1, 2, 2, 2, 1, 1, 1, 0, 0, 0}, {8, 3, 6, 2, 3, 1, 2, 0, 5, -20, 4, -6, 3, -1, 2, 0, 29, 1, 2, 2, 2, 1, 1, 1, 0, 0, 0}, {5, 15, 4, 6, 3, 1, 2, 0, 5, 15, 4, 6, 3, 1, 2, 0, 17, 1, 4, 4, 4, 1, 1, 1, 0, 0, 0}, {3, 0, 3, 3, 2, 3, 2, 0, 3, 0, 3, 3, 2, 3, 2, 0, 12, 1, 6, 6, 6, 0, 0, 0, 0, 0, 0}, {3, -1, 3, -1, 2, 0, 2, 0, 3, 0, 3, 3, 2, 3, 2, 0, 12, 1, 6, 6, 6, 0, 0, 0, 0, 0, 0}, {3, 0, 3, 3, 2, 3, 2, 0, 3, -1, 3, -1, 2, 0, 2, 0, 12, 1, 6, 6, 6, 0, 0, 0, 0, 0, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0}};
    static int detailLevel;
    static int sections;
    static int lengthSteps;
    static int vertsPerStrip;
    static int viewX;
    static int viewY;
    static int viewW;
    static int viewH;
    static int halfW;
    static int halfH;
    static Vec3[] crossPoints;
    static Vec3 upVector;
    static Vec3 rightVec;
    static Vec3 centerPos;
    static Vec3 forwardStep;
    static int[] stripIndices;
    static int[] stripLengths;
    static TriangleStripArray stripIndexBuffer;
    static CompositingMode blendReplace;
    static CompositingMode blendAlpha;
    static Transform rotation;
    static Appearance roadAppearance;
    static PolygonMode polyMode;
    static Billboard[] billboards;
    static VertexColors[] colorSets;
    static boolean useVertexColors;
    static int[] laneRows;
    static Vec3[] edgeLeftTmp;
    static Vec3[] edgeRightTmp;
    int segmentIndex = -1;
    BoundingBox bounds = new BoundingBox();
    short leftBillboard;
    short leftBillboardLane;
    short rightBillboard;
    short rightBillboardLane;
    short[] texCoordData;
    VertexArray texCoordArray;
    short[] vertexData;
    VertexArray vertexArray;
    VertexBuffer vertexBuffer = new VertexBuffer();
    int[] laneSolid;
    int edgeCount;
    Vec3[] leftEdges;
    Vec3[] rightEdges;
    static float[] projTemp;

    static void setDetail(int n) { /*T*/ final long $t = rally3d.core.Trace.in(108); try {
        detailLevel = n;
        switch (detailLevel) {
            default: {
                lengthSteps = 8;
                sections = 2;
                detailLevel = 3;
                break;
            }
            case 4: {
                lengthSteps = 16;
                sections = 1;
                detailLevel = 4;
            }
        }
        vertsPerStrip = sections * 2 + 2;
     } finally { rally3d.core.Trace.out(108, $t); } }

    static void setViewport(int n, int n2, int n3, int n4) {
        viewX = n;
        viewY = n2;
        viewW = n3;
        viewH = n4;
        halfW = n3 / 2;
        halfH = n4 / 2;
    }

    static void init(Texture2D texture2D, Billboard[] billboardArray, boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(109); try {
        int n;
        int n2;
        useVertexColors = bl;
        crossPoints = new Vec3[8];
        for (n2 = 0; n2 < 8; ++n2) {
            RoadSegmentMesh.crossPoints[n2] = new Vec3();
        }
        upVector = new Vec3(0.0f, 1.0f, 0.0f);
        rightVec = new Vec3();
        centerPos = new Vec3();
        forwardStep = new Vec3();
        stripIndices = new int[vertsPerStrip * 7];
        stripLengths = new int[7];
        for (n = 0; n < 7; ++n) {
            RoadSegmentMesh.stripLengths[n] = sections * 2 + 2;
            for (n2 = 0; n2 < sections * 2 + 2; ++n2) {
                RoadSegmentMesh.stripIndices[n2 + (RoadSegmentMesh.sections * 2 + 2) * n] = n2 + (sections * 2 + 2) * n;
            }
        }
        stripIndexBuffer = new TriangleStripArray(stripIndices, stripLengths);
        edgeLeftTmp = new Vec3[2];
        edgeRightTmp = new Vec3[2];
        for (n2 = 0; n2 < 2; ++n2) {
            RoadSegmentMesh.edgeLeftTmp[n2] = new Vec3();
            RoadSegmentMesh.edgeRightTmp[n2] = new Vec3();
        }
        byte[] byArray = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
        colorSets = new VertexColors[8];
        for (int i = 0; i < 8; ++i) {
            RoadSegmentMesh.colorSets[i] = new VertexColors();
            RoadSegmentMesh.colorSets[i].colors = new byte[vertsPerStrip * 3 * 7];
            for (n2 = 0; n2 < vertsPerStrip * 3; n2 += 6) {
                if (i <= 0 || n2 != 0) {
                    for (n = 0; n < 8; ++n) {
                        byArray[n] = (byte)(64 + RallyGame.randomPositive() % 192);
                    }
                }
                for (n = 0; n < 7; ++n) {
                    RoadSegmentMesh.colorSets[i].colors[n2 + 0 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n + 1];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 1 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n + 1];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 2 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n + 1];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 3 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 4 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 5 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n];
                    if (i == 7 && n2 == vertsPerStrip * 3 - 6) {
                        RoadSegmentMesh.colorSets[i].colors[n2 + 0 + n * RoadSegmentMesh.vertsPerStrip * 3] = RoadSegmentMesh.colorSets[0].colors[0 + n * vertsPerStrip * 3];
                        RoadSegmentMesh.colorSets[i].colors[n2 + 1 + n * RoadSegmentMesh.vertsPerStrip * 3] = RoadSegmentMesh.colorSets[0].colors[1 + n * vertsPerStrip * 3];
                        RoadSegmentMesh.colorSets[i].colors[n2 + 2 + n * RoadSegmentMesh.vertsPerStrip * 3] = RoadSegmentMesh.colorSets[0].colors[2 + n * vertsPerStrip * 3];
                        RoadSegmentMesh.colorSets[i].colors[n2 + 3 + n * RoadSegmentMesh.vertsPerStrip * 3] = RoadSegmentMesh.colorSets[0].colors[3 + n * vertsPerStrip * 3];
                        RoadSegmentMesh.colorSets[i].colors[n2 + 4 + n * RoadSegmentMesh.vertsPerStrip * 3] = RoadSegmentMesh.colorSets[0].colors[4 + n * vertsPerStrip * 3];
                        RoadSegmentMesh.colorSets[i].colors[n2 + 5 + n * RoadSegmentMesh.vertsPerStrip * 3] = RoadSegmentMesh.colorSets[0].colors[5 + n * vertsPerStrip * 3];
                        continue;
                    }
                    RoadSegmentMesh.colorSets[i].colors[n2 + 0 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n + 1];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 1 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n + 1];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 2 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n + 1];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 3 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 4 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n];
                    RoadSegmentMesh.colorSets[i].colors[n2 + 5 + n * RoadSegmentMesh.vertsPerStrip * 3] = byArray[n];
                }
            }
            RoadSegmentMesh.colorSets[i].array = new VertexArray(vertsPerStrip * 7, 3, 1);
            RoadSegmentMesh.colorSets[i].array.set(0, vertsPerStrip * 7, RoadSegmentMesh.colorSets[i].colors);
        }
        laneRows = new int[7];
        roadAppearance = new Appearance();
        blendAlpha = new CompositingMode();
        // Road strips overlap at shared edges; depth writes made the GPU discard
        // valid textured fragments on some drivers, leaving visible holes.
        blendAlpha.setDepthTestEnable(true);
        blendAlpha.setDepthWriteEnable(false);
        blendAlpha.setColorWriteEnable(true);
        blendAlpha.setAlphaWriteEnable(false);
        blendAlpha.setBlending(68);
        blendReplace = new CompositingMode();
        blendReplace.setDepthTestEnable(true);
        blendReplace.setDepthWriteEnable(false);
        blendReplace.setColorWriteEnable(true);
        blendReplace.setAlphaWriteEnable(false);
        blendReplace.setBlending(64);
        roadAppearance.setCompositingMode(blendAlpha);
        polyMode = new PolygonMode();
        polyMode.setPerspectiveCorrectionEnable(true);
        roadAppearance.setPolygonMode(polyMode);
        roadAppearance.setTexture(0, texture2D);
        rotation = new Transform();
        billboards = billboardArray;
     } finally { rally3d.core.Trace.out(109, $t); } }

    static void release() { /*T*/ final long $t = rally3d.core.Trace.in(110); try {
        crossPoints = null;
        upVector = null;
        rightVec = null;
        centerPos = null;
        forwardStep = null;
        stripIndices = null;
        stripLengths = null;
        stripIndexBuffer = null;
        blendReplace = null;
        blendAlpha = null;
        rotation = null;
        roadAppearance = null;
        polyMode = null;
        billboards = null;
        colorSets = null;
        laneRows = null;
        edgeLeftTmp = null;
        edgeRightTmp = null;
     } finally { rally3d.core.Trace.out(110, $t); } }

    RoadSegmentMesh() {
        this.vertexData = new short[vertsPerStrip * 3 * 7];
        this.vertexArray = new VertexArray(vertsPerStrip * 7, 3, 2);
        this.texCoordData = new short[vertsPerStrip * 2 * 7];
        this.texCoordArray = new VertexArray(vertsPerStrip * 7, 2, 2);
        this.laneSolid = new int[7];
        this.leftEdges = new Vec3[16];
        this.rightEdges = new Vec3[16];
        for (int i = 0; i < 16; ++i) {
            this.leftEdges[i] = new Vec3();
            this.rightEdges[i] = new Vec3();
        }
    }

    void resetBounds() { /*T*/ final long $t = rally3d.core.Trace.in(111); try {
        this.bounds.min.v[0] = 10000.0f;
        this.bounds.min.v[1] = 10000.0f;
        this.bounds.min.v[2] = 10000.0f;
        this.bounds.max.v[0] = -10000.0f;
        this.bounds.max.v[1] = -10000.0f;
        this.bounds.max.v[2] = -10000.0f;
     } finally { rally3d.core.Trace.out(111, $t); } }

    void expandBounds(Vec3 vec3) { /*T*/ final long $t = rally3d.core.Trace.in(112); try {
        if (vec3.v[0] < this.bounds.min.v[0]) {
            this.bounds.min.v[0] = vec3.v[0];
        }
        if (vec3.v[1] < this.bounds.min.v[1]) {
            this.bounds.min.v[1] = vec3.v[1];
        }
        if (vec3.v[2] < this.bounds.min.v[2]) {
            this.bounds.min.v[2] = vec3.v[2];
        }
        if (vec3.v[0] > this.bounds.max.v[0]) {
            this.bounds.max.v[0] = vec3.v[0];
        }
        if (vec3.v[1] > this.bounds.max.v[1]) {
            this.bounds.max.v[1] = vec3.v[1];
        }
        if (vec3.v[2] > this.bounds.max.v[2]) {
            this.bounds.max.v[2] = vec3.v[2];
        }
     } finally { rally3d.core.Trace.out(112, $t); } }

    void build(int n, TrackSegment trackSegment, TrackSegment trackSegment2, TrackSegment trackSegment3) { /*T*/ final long $t = rally3d.core.Trace.in(113); try {
        int n2;
        int n3;
        float f = trackSegment.yawStep;
        float f2 = trackSegment.startHeight;
        float f3 = trackSegment.slope;
        float f4 = trackSegment.curvature;
        short s = trackSegment.profileStart;
        short s2 = trackSegment.profileEnd;
        this.leftBillboard = trackSegment.leftSignType;
        this.leftBillboardLane = trackSegment.leftSignLane;
        this.rightBillboard = trackSegment.rightSignType;
        this.rightBillboardLane = trackSegment.rightSignLane;
        if (this.segmentIndex == n) {
            return;
        }
        this.segmentIndex = n;
        this.resetBounds();
        float f5 = profiles[s][0];
        float f6 = profiles[s][1];
        float f7 = profiles[s][2];
        float f8 = profiles[s][3];
        float f9 = profiles[s][4];
        float f10 = profiles[s][5];
        float f11 = profiles[s][6];
        float f12 = profiles[s][7];
        float f13 = profiles[s2][0];
        float f14 = profiles[s2][1];
        float f15 = profiles[s2][2];
        float f16 = profiles[s2][3];
        float f17 = profiles[s2][4];
        float f18 = profiles[s2][5];
        float f19 = profiles[s2][6];
        float f20 = profiles[s2][7];
        float f21 = ((float)profiles[s][8] - f5) / (float)sections;
        float f22 = ((float)profiles[s][10] - f7) / (float)sections;
        float f23 = ((float)profiles[s][12] - f9) / (float)sections;
        float f24 = ((float)profiles[s][14] - f11) / (float)sections;
        float f25 = ((float)profiles[s][9] - f6) / (float)sections;
        float f26 = ((float)profiles[s][11] - f8) / (float)sections;
        float f27 = ((float)profiles[s][13] - f10) / (float)sections;
        float f28 = ((float)profiles[s][15] - f12) / (float)sections;
        float f29 = ((float)profiles[s2][8] - f13) / (float)sections;
        float f30 = ((float)profiles[s2][10] - f15) / (float)sections;
        float f31 = ((float)profiles[s2][12] - f17) / (float)sections;
        float f32 = ((float)profiles[s2][14] - f19) / (float)sections;
        float f33 = ((float)profiles[s2][9] - f14) / (float)sections;
        float f34 = ((float)profiles[s2][11] - f16) / (float)sections;
        float f35 = ((float)profiles[s2][13] - f18) / (float)sections;
        float f36 = ((float)profiles[s2][15] - f20) / (float)sections;
        float f37 = profiles[s][16] / 4;
        float f38 = profiles[s2][16] / 4;
        float f39 = profiles[s][16] / 4;
        float f40 = profiles[s2][16] / 4;
        if (trackSegment2 != null) {
            if (trackSegment2.profileStart != trackSegment.profileStart && profiles[trackSegment2.profileStart][16] < profiles[trackSegment.profileStart][16]) {
                f37 = profiles[trackSegment2.profileStart][16] / 4;
            }
            if (trackSegment2.profileEnd != trackSegment.profileEnd && profiles[trackSegment2.profileEnd][16] < profiles[trackSegment.profileEnd][16]) {
                f38 = profiles[trackSegment2.profileEnd][16] / 4;
            }
        }
        if (trackSegment3 != null) {
            if (trackSegment3.profileStart != trackSegment.profileStart && profiles[trackSegment.profileStart][16] > profiles[trackSegment3.profileStart][16]) {
                f39 = profiles[trackSegment3.profileStart][16] / 4;
            }
            if (trackSegment3.profileEnd != trackSegment.profileEnd && profiles[trackSegment.profileEnd][16] > profiles[trackSegment3.profileEnd][16]) {
                f40 = profiles[trackSegment3.profileEnd][16] / 4;
            }
        }
        float f41 = (f39 - f37) / (float)sections;
        float f42 = (f40 - f38) / (float)sections;
        centerPos.set(trackSegment.startPos);
        forwardStep.set(trackSegment.startDir);
        this.edgeCount = 0;
        rotation.setIdentity();
        rotation.postRotate(f, 0.0f, 1.0f, 0.0f);
        for (n3 = 0; n3 < vertsPerStrip; n3 += 2) {
            rightVec.cross(upVector, forwardStep);
            rightVec.normalize();
            RoadSegmentMesh.rightVec.v[0] = RoadSegmentMesh.rightVec.v[0] * 4.0f;
            RoadSegmentMesh.rightVec.v[1] = RoadSegmentMesh.rightVec.v[1] * 4.0f;
            RoadSegmentMesh.rightVec.v[2] = RoadSegmentMesh.rightVec.v[2] * 4.0f;
            RoadSegmentMesh.crossPoints[0].v[0] = RoadSegmentMesh.centerPos.v[0] - f13 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.crossPoints[0].v[1] = RoadSegmentMesh.centerPos.v[1] + f14;
            RoadSegmentMesh.crossPoints[0].v[2] = RoadSegmentMesh.centerPos.v[2] - f13 * RoadSegmentMesh.rightVec.v[2];
            RoadSegmentMesh.crossPoints[1].v[0] = RoadSegmentMesh.centerPos.v[0] - f15 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.crossPoints[1].v[1] = RoadSegmentMesh.centerPos.v[1] + f16;
            RoadSegmentMesh.crossPoints[1].v[2] = RoadSegmentMesh.centerPos.v[2] - f15 * RoadSegmentMesh.rightVec.v[2];
            RoadSegmentMesh.crossPoints[2].v[0] = RoadSegmentMesh.centerPos.v[0] - f17 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.crossPoints[2].v[1] = RoadSegmentMesh.centerPos.v[1] + f18;
            RoadSegmentMesh.crossPoints[2].v[2] = RoadSegmentMesh.centerPos.v[2] - f17 * RoadSegmentMesh.rightVec.v[2];
            RoadSegmentMesh.crossPoints[3].v[0] = RoadSegmentMesh.centerPos.v[0] - f19 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.crossPoints[3].v[1] = RoadSegmentMesh.centerPos.v[1] + f20;
            RoadSegmentMesh.crossPoints[3].v[2] = RoadSegmentMesh.centerPos.v[2] - f19 * RoadSegmentMesh.rightVec.v[2];
            RoadSegmentMesh.crossPoints[4].v[0] = RoadSegmentMesh.centerPos.v[0] + f11 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.crossPoints[4].v[1] = RoadSegmentMesh.centerPos.v[1] + f12;
            RoadSegmentMesh.crossPoints[4].v[2] = RoadSegmentMesh.centerPos.v[2] + f11 * RoadSegmentMesh.rightVec.v[2];
            RoadSegmentMesh.crossPoints[5].v[0] = RoadSegmentMesh.centerPos.v[0] + f9 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.crossPoints[5].v[1] = RoadSegmentMesh.centerPos.v[1] + f10;
            RoadSegmentMesh.crossPoints[5].v[2] = RoadSegmentMesh.centerPos.v[2] + f9 * RoadSegmentMesh.rightVec.v[2];
            RoadSegmentMesh.crossPoints[6].v[0] = RoadSegmentMesh.centerPos.v[0] + f7 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.crossPoints[6].v[1] = RoadSegmentMesh.centerPos.v[1] + f8;
            RoadSegmentMesh.crossPoints[6].v[2] = RoadSegmentMesh.centerPos.v[2] + f7 * RoadSegmentMesh.rightVec.v[2];
            RoadSegmentMesh.crossPoints[7].v[0] = RoadSegmentMesh.centerPos.v[0] + f5 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.crossPoints[7].v[1] = RoadSegmentMesh.centerPos.v[1] + f6;
            RoadSegmentMesh.crossPoints[7].v[2] = RoadSegmentMesh.centerPos.v[2] + f5 * RoadSegmentMesh.rightVec.v[2];
            for (n2 = 0; n2 < 7; ++n2) {
                this.vertexData[n3 * 3 + 0 + n2 * RoadSegmentMesh.vertsPerStrip * 3] = (short)(8.0f * RoadSegmentMesh.crossPoints[n2 + 1].v[0]);
                this.vertexData[n3 * 3 + 1 + n2 * RoadSegmentMesh.vertsPerStrip * 3] = (short)(8.0f * RoadSegmentMesh.crossPoints[n2 + 1].v[1]);
                this.vertexData[n3 * 3 + 2 + n2 * RoadSegmentMesh.vertsPerStrip * 3] = (short)(8.0f * RoadSegmentMesh.crossPoints[n2 + 1].v[2]);
                this.vertexData[n3 * 3 + 3 + n2 * RoadSegmentMesh.vertsPerStrip * 3] = (short)(8.0f * RoadSegmentMesh.crossPoints[n2 + 0].v[0]);
                this.vertexData[n3 * 3 + 4 + n2 * RoadSegmentMesh.vertsPerStrip * 3] = (short)(8.0f * RoadSegmentMesh.crossPoints[n2 + 0].v[1]);
                this.vertexData[n3 * 3 + 5 + n2 * RoadSegmentMesh.vertsPerStrip * 3] = (short)(8.0f * RoadSegmentMesh.crossPoints[n2 + 0].v[2]);
            }
            edgeLeftTmp[0].set(edgeLeftTmp[1]);
            RoadSegmentMesh.edgeLeftTmp[1].v[0] = RoadSegmentMesh.centerPos.v[0] + f37 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.edgeLeftTmp[1].v[1] = 0.0f;
            RoadSegmentMesh.edgeLeftTmp[1].v[2] = RoadSegmentMesh.centerPos.v[2] + f37 * RoadSegmentMesh.rightVec.v[2];
            edgeRightTmp[0].set(edgeRightTmp[1]);
            RoadSegmentMesh.edgeRightTmp[1].v[0] = RoadSegmentMesh.centerPos.v[0] - f38 * RoadSegmentMesh.rightVec.v[0];
            RoadSegmentMesh.edgeRightTmp[1].v[1] = 0.0f;
            RoadSegmentMesh.edgeRightTmp[1].v[2] = RoadSegmentMesh.centerPos.v[2] - f38 * RoadSegmentMesh.rightVec.v[2];
            if (n3 > 0) {
                this.leftEdges[this.edgeCount].set(edgeLeftTmp[0]);
                this.rightEdges[this.edgeCount].set(edgeLeftTmp[1]);
                ++this.edgeCount;
                this.rightEdges[this.edgeCount].set(edgeRightTmp[0]);
                this.leftEdges[this.edgeCount].set(edgeRightTmp[1]);
                ++this.edgeCount;
            }
            this.expandBounds(crossPoints[0]);
            this.expandBounds(crossPoints[7]);
            RoadSegmentMesh.centerPos.v[1] = f2;
            centerPos.add(centerPos, forwardStep);
            for (int i = 0; i < lengthSteps / 2; ++i) {
                f2 += f3;
                f3 += f4;
                rotation.transform(RoadSegmentMesh.forwardStep.v);
            }
            f5 += f21;
            f6 += f25;
            f7 += f22;
            f8 += f26;
            f9 += f23;
            f10 += f27;
            f11 += f24;
            f12 += f28;
            f13 += f29;
            f14 += f33;
            f15 += f30;
            f16 += f34;
            f17 += f31;
            f18 += f35;
            f19 += f32;
            f20 += f36;
            f37 += f41;
            f38 += f42;
        }
        this.vertexArray.set(0, vertsPerStrip * 7, this.vertexData);
        this.vertexBuffer.setPositions(this.vertexArray, 0.125f, null);
        RoadSegmentMesh.laneRows[0] = profiles[s2][20];
        RoadSegmentMesh.laneRows[1] = profiles[s2][19];
        RoadSegmentMesh.laneRows[2] = profiles[s2][18];
        RoadSegmentMesh.laneRows[3] = trackSegment.centerMarking == 0 ? 0 : 5;
        RoadSegmentMesh.laneRows[4] = profiles[s][18];
        RoadSegmentMesh.laneRows[5] = profiles[s][19];
        RoadSegmentMesh.laneRows[6] = profiles[s][20];
        for (n2 = 0; n2 < 7; ++n2) {
            short s3;
            short s4;
            if (n2 < 3) {
                s4 = (short)(1 + laneRows[n2] * 32);
                s3 = (short)(31 + laneRows[n2] * 32);
            } else {
                s3 = (short)(1 + laneRows[n2] * 32);
                s4 = (short)(31 + laneRows[n2] * 32);
            }
            for (n3 = 0; n3 < vertsPerStrip; n3 += 2) {
                this.texCoordData[n3 * 2 + 0 + n2 * RoadSegmentMesh.vertsPerStrip * 2] = s3;
                this.texCoordData[n3 * 2 + 2 + n2 * RoadSegmentMesh.vertsPerStrip * 2] = s4;
                short s5 = useVertexColors ? (short)(511 * n3 / (2 * sections)) : (short)(511 * n3 / (2 * sections * 4));
                if ((n & 1) != 0) {
                    s5 = (short)(s5 + 128);
                }
                short s6 = s5;
                this.texCoordData[n3 * 2 + 3 + n2 * RoadSegmentMesh.vertsPerStrip * 2] = s6;
                this.texCoordData[n3 * 2 + 1 + n2 * RoadSegmentMesh.vertsPerStrip * 2] = s6;
            }
        }
        this.texCoordArray.set(0, vertsPerStrip * 7, this.texCoordData);
        this.vertexBuffer.setTexCoords(0, this.texCoordArray, 0.00390625f, null);
        if (useVertexColors) {
            this.vertexBuffer.setColors(RoadSegmentMesh.colorSets[this.segmentIndex % 8].array);
        }
        this.laneSolid[0] = profiles[s2][21];
        this.laneSolid[1] = profiles[s2][22];
        this.laneSolid[2] = profiles[s2][23];
        this.laneSolid[3] = 1;
        this.laneSolid[4] = profiles[s][23];
        this.laneSolid[5] = profiles[s][22];
        this.laneSolid[6] = profiles[s][21];
     } finally { rally3d.core.Trace.out(113, $t); } }

    /** Submits the vertex buffers of many road meshes in order (vertex stage runs in parallel on PC). */
    static void renderAll(Graphics3D graphics3D, VertexBuffer[] vbs, int count, Transform transform) {
        graphics3D.renderBatch(vbs, (IndexBuffer)stripIndexBuffer, roadAppearance, transform, count);
    }

    void render(Graphics3D graphics3D, Transform transform) { /*T*/ final long $t = rally3d.core.Trace.in(114); try {
        graphics3D.render(this.vertexBuffer, (IndexBuffer)stripIndexBuffer, roadAppearance, transform);
     } finally { rally3d.core.Trace.out(114, $t); } }

    void drawBillboards(Graphics3D renderer, Graphics graphics, Transform transform, Transform transform2, Vec3 cameraPosition) { /*T*/ final long $t = rally3d.core.Trace.in(115); try {
        int n;
        if (this.leftBillboard > -1) {
            RoadSegmentMesh.projTemp[0] = this.vertexData[(vertsPerStrip - 2) * 3 + vertsPerStrip * 3 * (3 + this.leftBillboardLane) + 0] / 8.0f;
            RoadSegmentMesh.projTemp[1] = this.vertexData[(vertsPerStrip - 2) * 3 + vertsPerStrip * 3 * (3 + this.leftBillboardLane) + 1] / 8.0f;
            RoadSegmentMesh.projTemp[2] = this.vertexData[(vertsPerStrip - 2) * 3 + vertsPerStrip * 3 * (3 + this.leftBillboardLane) + 2] / 8.0f;
            RoadSegmentMesh.projTemp[3] = 1.0f;
            float leftDx = RoadSegmentMesh.projTemp[0] - cameraPosition.v[0];
            float leftDy = RoadSegmentMesh.projTemp[1] - cameraPosition.v[1];
            float leftDz = RoadSegmentMesh.projTemp[2] - cameraPosition.v[2];
            float leftDistance = (float)Math.sqrt(leftDx * leftDx + leftDy * leftDy + leftDz * leftDz);
            transform2.transform(projTemp);
            if (projTemp[2] < -nearCull(this.leftBillboard) && projTemp[2] > -4096.0f) {
                n = 0;   // static sprites: frame 0; trees/bushes: LOD picked below
                if (n > 5) {
                    n = 5;
                }
                if (n < 0) {
                    n = 0;
                }
                // Use one stable, readable sprite size instead of distance LOD.
                if (isStaticSprite(this.leftBillboard)) n = 0;
                final float viewDepth = -projTemp[2];
                transform.transform(projTemp);
                float depth = projTemp[2] / projTemp[3] * 0.5f + 0.5f;
                RoadSegmentMesh.projTemp[0] = projTemp[0] / projTemp[3];
                RoadSegmentMesh.projTemp[1] = projTemp[1] / projTemp[3];
                RoadSegmentMesh.projTemp[0] = (projTemp[0] + 1.0f) * (float)halfW + (float)viewX;
                RoadSegmentMesh.projTemp[1] = (-projTemp[1] + 1.0f) * (float)halfH + (float)viewY;
                int displayHeight = this.billboardDisplayHeight(this.leftBillboard, n, viewDepth);
                if (!isStaticSprite(this.leftBillboard)) n = lodFrameFor(billboards[this.leftBillboard].frameHeight, displayHeight);
                billboards[this.leftBillboard].drawDepthTested(renderer, graphics, Math.round(projTemp[0]), Math.round(projTemp[1]), n, depth, displayHeight);
                graphics.setClip(viewX, viewY, viewW, viewH);
            }
        }
        if (this.rightBillboard > -1) {
            RoadSegmentMesh.projTemp[0] = this.vertexData[(vertsPerStrip - 2) * 3 + vertsPerStrip * 3 * (3 - this.rightBillboardLane) + 3] / 8.0f;
            RoadSegmentMesh.projTemp[1] = this.vertexData[(vertsPerStrip - 2) * 3 + vertsPerStrip * 3 * (3 - this.rightBillboardLane) + 4] / 8.0f;
            RoadSegmentMesh.projTemp[2] = this.vertexData[(vertsPerStrip - 2) * 3 + vertsPerStrip * 3 * (3 - this.rightBillboardLane) + 5] / 8.0f;
            RoadSegmentMesh.projTemp[3] = 1.0f;
            float rightDx = RoadSegmentMesh.projTemp[0] - cameraPosition.v[0];
            float rightDy = RoadSegmentMesh.projTemp[1] - cameraPosition.v[1];
            float rightDz = RoadSegmentMesh.projTemp[2] - cameraPosition.v[2];
            float rightDistance = (float)Math.sqrt(rightDx * rightDx + rightDy * rightDy + rightDz * rightDz);
            transform2.transform(projTemp);
            if (projTemp[2] < -nearCull(this.rightBillboard) && projTemp[2] > -4096.0f) {
                n = 0;   // static sprites: frame 0; trees/bushes: LOD picked below
                if (n > 5) {
                    n = 5;
                }
                if (n < 0) {
                    n = 0;
                }
                if (isStaticSprite(this.rightBillboard)) n = 0;
                final float viewDepth = -projTemp[2];
                transform.transform(projTemp);
                float depth = projTemp[2] / projTemp[3] * 0.5f + 0.5f;
                RoadSegmentMesh.projTemp[0] = projTemp[0] / projTemp[3];
                RoadSegmentMesh.projTemp[1] = projTemp[1] / projTemp[3];
                RoadSegmentMesh.projTemp[0] = (projTemp[0] + 1.0f) * (float)halfW + (float)viewX;
                RoadSegmentMesh.projTemp[1] = (-projTemp[1] + 1.0f) * (float)halfH + (float)viewY;
                int displayHeight = this.billboardDisplayHeight(this.rightBillboard, n, viewDepth);
                if (!isStaticSprite(this.rightBillboard)) n = lodFrameFor(billboards[this.rightBillboard].frameHeight, displayHeight);
                billboards[this.rightBillboard].drawDepthTested(renderer, graphics, Math.round(projTemp[0]), Math.round(projTemp[1]), n, depth, displayHeight);
                graphics.setClip(viewX, viewY, viewW, viewH);
            }
        }
     } finally { rally3d.core.Trace.out(115, $t); } }

    // person (6): one fixed frame. Finish (3) and checkpoint (5) behave like trees: real world size, perspective
    // scaling and the pre-scaled LOD frames, so they are small far away and grow smoothly (no pop-in) as the car comes closer.
    private static boolean isStaticSprite(int billboardType) {
        return billboardType == 6;
    }

    /** Finish / checkpoint signs: world-size sprites that use the LOD frames like trees. */
    private static boolean isMarkerSprite(int billboardType) {
        return billboardType == 3 || billboardType == 5;
    }

    // World-space sizes (1 world unit ~ 0.77 m: stock car mesh is 5.45 units long ~ 4.2 m).
    static final float STATIC_CHECKPOINT_HEIGHT = 1.6f; // cone, fixed
    static final float STATIC_FINISH_HEIGHT = 4.0f;     // finish, fixed
    static final float STATIC_PERSON_HEIGHT = 3.5f;     // person, fixed
    /** Real tree ~8.5 m tall. */
    static final float TREE_WORLD_HEIGHT = 11.0f;
    /** Bush / shrub ~3 m tall (enlarged from 1.4). */
    static final float BUSH_WORLD_HEIGHT = 4.0f;
    /** Frame-0 art at or below this height (px) is a bush/shrub, above it a tree. */
    static final int BUSH_MAX_ART_HEIGHT = 30;
    /** Reference art height of tree sprites (px); taller/shorter art varies proportionally. */
    static final float TREE_ART_REF = 64.0f;
    /** Sprites never exceed this multiple of view height (keeps close-up cost bounded; never shrinks). */
    static final float MAX_SPRITE_VIEW_HEIGHTS = 3.0f;

    /** Minimum view depth before a sprite is culled: static markers 6, trees/bushes 1 (so they slide off screen instead of popping). */
    private static float nearCull(int billboardType) {
        return isStaticSprite(billboardType) ? 6.0f : 1.0f;
    }

    /** Real-world height (world units) of a roadside tree/bush sprite, from its frame-0 art. */
    static float spriteWorldHeight(int frame0Height) { /*T*/ final long $t = rally3d.core.Trace.in(501); try {
        if (frame0Height <= BUSH_MAX_ART_HEIGHT) return BUSH_WORLD_HEIGHT;
        return TREE_WORLD_HEIGHT * (frame0Height / TREE_ART_REF);
     } finally { rally3d.core.Trace.out(501, $t); } }

    /** On-screen height in render pixels: pure perspective of the real size, height_px = worldHeight * focal / viewDepth. Grows monotonically as the camera approaches. */
    static int spriteTargetHeight(int frame0Height, int viewHeight, float viewDepth) { /*T*/ final long $t = rally3d.core.Trace.in(220); try {
        float focal = (viewHeight * 0.5f) * 1.7320508f; // 60 deg vertical FOV
        float px = spriteWorldHeight(frame0Height) * focal / Math.max(1.0f, viewDepth);
        return Math.max(2, Math.min(Math.round(px), Math.round(viewHeight * MAX_SPRITE_VIEW_HEIGHTS)));
     } finally { rally3d.core.Trace.out(220, $t); } }

    /** Picks the pre-scaled LOD frame that needs the least stretching: the smallest frame that is at least targetHeight tall (else frame 0). */
    static int lodFrameFor(short[] frameHeights, int targetHeight) { /*T*/ final long $t = rally3d.core.Trace.in(221); try {
        int best = 0, bestH = Integer.MAX_VALUE;
        for (int f = 0; f < frameHeights.length; f++) {
            int h = frameHeights[f];
            if (h > 0 && h >= targetHeight && h < bestH) { best = f; bestH = h; }
        }
        return best;
     } finally { rally3d.core.Trace.out(221, $t); } }

    private int billboardDisplayHeight(int billboardType, int frame, float viewDepth) { /*T*/ final long $t = rally3d.core.Trace.in(116); try {
        if (isMarkerSprite(billboardType)) {
            // Same pure perspective as trees (grows monotonically on approach), real size of the marker.
            float worldHeight = billboardType == 3 ? STATIC_FINISH_HEIGHT : STATIC_CHECKPOINT_HEIGHT;
            float focal = (viewH * 0.5f) * 1.7320508f;
            float px = worldHeight * focal / Math.max(1.0f, viewDepth);
            return Math.max(2, Math.min(Math.round(px), Math.round(viewH * MAX_SPRITE_VIEW_HEIGHTS)));
        }
        if (isStaticSprite(billboardType)) {
            // World-fixed size: perspective scaling only, glued to the road.
            float worldHeight = STATIC_PERSON_HEIGHT;
            float focal = halfH * 1.7320508f;
            float px = worldHeight * focal / Math.max(1.0f, viewDepth);
            return Math.max(3, Math.min(Math.round(px), viewH));
        }
        return spriteTargetHeight(billboards[billboardType].frameHeight[0], viewH, viewDepth);
     } finally { rally3d.core.Trace.out(116, $t); } }

    static {
        projTemp = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }
}
