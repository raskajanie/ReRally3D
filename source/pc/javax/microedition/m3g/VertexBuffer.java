package javax.microedition.m3g;

public class VertexBuffer extends Object3D {
    VertexArray pos, tex, col;
    float posScale = 1, texScale = 1;
    final float[] posBias = new float[3];
    final float[] texBias = new float[3];

    public VertexBuffer() {}

    public void setPositions(VertexArray a, float scale, float[] bias) {
        pos = a; posScale = scale;
        for (int i = 0; i < 3; i++) posBias[i] = bias == null ? 0 : bias[i];
    }

    public void setTexCoords(int index, VertexArray a, float scale, float[] bias) {
        if (index != 0) return;
        tex = a; texScale = scale;
        for (int i = 0; i < 3; i++) texBias[i] = bias == null ? 0 : bias[i];
    }

    public void setColors(VertexArray a) { col = a; }
    public int getVertexCount() { return pos == null ? 0 : pos.count; }
}
