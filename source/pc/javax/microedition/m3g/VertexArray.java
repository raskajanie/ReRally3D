package javax.microedition.m3g;

public class VertexArray extends Object3D {
    final int count, comps, size;
    final short[] s; final byte[] b;

    public VertexArray(int numVertices, int numComponents, int componentSize) {
        count = numVertices; comps = numComponents; size = componentSize;
        s = componentSize == 2 ? new short[numVertices * numComponents] : null;
        b = componentSize == 1 ? new byte[numVertices * numComponents] : null;
    }

    public void set(int first, int n, short[] values) { System.arraycopy(values, 0, s, first * comps, n * comps); }
    public void set(int first, int n, byte[] values) { System.arraycopy(values, 0, b, first * comps, n * comps); }
    public int getVertexCount() { return count; }
    public int getComponentCount() { return comps; }
}
