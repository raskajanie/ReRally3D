package javax.microedition.m3g;

/** 4x4 row-major matrix, column-vector convention (v' = M * v), post-multiplying operations like M3G. */
public class Transform {
    final float[] m = new float[16];
    private final float[] tmp = new float[16];

    public Transform() { setIdentity(); }
    public Transform(Transform t) { set(t); }

    public void setIdentity() {
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = m[5] = m[10] = m[15] = 1;
    }

    public void set(Transform t) { System.arraycopy(t.m, 0, m, 0, 16); }

    public void postMultiply(Transform t) { mul(t.m); }

    void mul(float[] r) {
        float[] o = tmp;
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                o[i * 4 + j] = m[i * 4] * r[j] + m[i * 4 + 1] * r[4 + j] + m[i * 4 + 2] * r[8 + j] + m[i * 4 + 3] * r[12 + j];
        System.arraycopy(o, 0, m, 0, 16);
    }

    public void postRotate(float angle, float ax, float ay, float az) {
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len == 0) return;
        double x = ax / len, y = ay / len, z = az / len;
        double rad = Math.toRadians(angle), c = Math.cos(rad), s = Math.sin(rad), t = 1 - c;
        float[] r = {
            (float) (t * x * x + c), (float) (t * x * y - s * z), (float) (t * x * z + s * y), 0,
            (float) (t * x * y + s * z), (float) (t * y * y + c), (float) (t * y * z - s * x), 0,
            (float) (t * x * z - s * y), (float) (t * y * z + s * x), (float) (t * z * z + c), 0,
            0, 0, 0, 1};
        mul(r);
    }

    public void postTranslate(float x, float y, float z) {
        float[] r = {1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
        mul(r);
    }

    public void postScale(float x, float y, float z) {
        float[] r = {x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1};
        mul(r);
    }

    /** Transforms an array of 4-component vectors in place. */
    public void transform(float[] v) {
        for (int o = 0; o + 3 < v.length; o += 4) {
            float x = v[o], y = v[o + 1], z = v[o + 2], w = v[o + 3];
            for (int i = 0; i < 4; i++) v[o + i] = m[i * 4] * x + m[i * 4 + 1] * y + m[i * 4 + 2] * z + m[i * 4 + 3] * w;
        }
    }
}
