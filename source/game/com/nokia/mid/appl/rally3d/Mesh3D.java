package com.nokia.mid.appl.rally3d;

import java.io.IOException;
import java.io.InputStream;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.IndexBuffer;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;

class Mesh3D {
    private VertexArray positions;
    private VertexArray texCoords;
    private VertexBuffer vertexBuffer;
    private TriangleStripArray triangles;
    private Appearance appearance;
    private CompositingMode compositing;

    Mesh3D() {
    }

    void load(String string) {
        try {
            InputStream in = this.getClass().getResourceAsStream(string);
            if (in == null) throw new IOException("missing resource " + string);
            try { this.load(in); } finally { try { in.close(); } catch (IOException ignored) { rally3d.core.RLog.caught("Mesh3D.load", ignored); } }
        }
        catch (IOException iOException) { rally3d.core.RLog.caught("Mesh3D.load", iOException);
            // empty catch block
        }
    }

    /** Load a .syn mesh from memory (add-on cars). Throws if the data is unreadable; the caller falls back to a stock mesh. */
    void loadBytes(byte[] data) throws IOException {
        AddonPack.checkSyn(data, "mesh");
        this.load(new java.io.ByteArrayInputStream(data));
        if (this.vertexBuffer == null) throw new IOException("bad mesh data");
    }

    private void load(InputStream inputStream) {
        try {
            int n;
            int n2;
            int n3;
            int n4;
            int n5 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
            int n6 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
            int n7 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
            int n8 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
            int n9 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
            int n10 = inputStream.read() + inputStream.read() * 256;
            inputStream.read();
            inputStream.read();
            short[] sArray = new short[3 * n10];
            byte[] byArray = new byte[2 * n10];
            byte[] byArray2 = new byte[3 * n10];
            for (n4 = 0; n4 < n10; ++n4) {
                n3 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
                n2 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
                n = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
                sArray[3 * n4 + 0] = (short)(n3 >> 4);
                sArray[3 * n4 + 1] = (short)(n2 >> 4);
                sArray[3 * n4 + 2] = (short)(n >> 4);
            }
            for (n4 = 0; n4 < n10; ++n4) {
                n3 = inputStream.read() + inputStream.read() * 256;
                n2 = inputStream.read() + inputStream.read() * 256;
                n2 >>= 3;
                if ((n3 >>= 3) > 127) {
                    n3 = 127;
                }
                if (n2 > 127) {
                    n2 = 127;
                }
                byArray[2 * n4 + 0] = (byte)n3;
                byArray[2 * n4 + 1] = (byte)n2;
            }
            if (n9 != 0) {
                for (n4 = 0; n4 < n10; ++n4) {
                    n3 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
                    n2 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
                    n = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
                    byArray2[3 * n4 + 0] = (byte)(n3 >> 7);
                    byArray2[3 * n4 + 1] = (byte)(n2 >> 7);
                    byArray2[3 * n4 + 2] = (byte)(n >> 7);
                }
            }
            int n11 = inputStream.read() + inputStream.read() * 256 + inputStream.read() * 256 * 256 + inputStream.read() * 256 * 256 * 256;
            int[] nArray = new int[n11];
            int[] nArray2 = new int[n11 / 3];
            for (n4 = 0; n4 < n11; ++n4) {
                nArray[n4] = inputStream.read() + inputStream.read() * 256;
            }
            for (n4 = 0; n4 < n11 / 3; ++n4) {
                nArray2[n4] = 3;
            }
            this.vertexBuffer = new VertexBuffer();
            this.positions = new VertexArray(sArray.length / 3, 3, 2);
            this.positions.set(0, sArray.length / 3, sArray);
            this.vertexBuffer.setPositions(this.positions, 0.001f, null);
            this.texCoords = new VertexArray(byArray.length / 2, 2, 1);
            this.texCoords.set(0, byArray.length / 2, byArray);
            this.vertexBuffer.setTexCoords(0, this.texCoords, 0.0078125f, null);
            this.triangles = new TriangleStripArray(nArray, nArray2);
            this.appearance = new Appearance();
            this.compositing = new CompositingMode();
            this.compositing.setAlphaWriteEnable(false);
            this.compositing.setBlending(68);
            this.setDepthEnabled(true);
            this.appearance.setCompositingMode(this.compositing);
            inputStream.close();
        }
        catch (IOException iOException) { rally3d.core.RLog.caught("Mesh3D.load", iOException);
            // empty catch block
        }
    }

    void setTexture(Texture2D texture2D) {
        this.appearance.setTexture(0, texture2D);
    }

    void setDepthEnabled(boolean bl) {
        this.compositing.setDepthTestEnable(bl);
        this.compositing.setDepthWriteEnable(bl);
    }

    void render(Graphics3D graphics3D, Transform transform) {
        graphics3D.render(this.vertexBuffer, (IndexBuffer)this.triangles, this.appearance, transform);
    }
}

