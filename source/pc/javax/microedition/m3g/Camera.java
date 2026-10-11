package javax.microedition.m3g;

public class Camera extends Object3D {
    public static final int GENERIC = 48, PARALLEL = 49, PERSPECTIVE = 50;
    final Transform proj = new Transform();

    public Camera() {}

    public void setPerspective(float fovy, float aspect, float near, float far) {
        double f = 1.0 / Math.tan(Math.toRadians(fovy) / 2.0);
        float[] m = proj.m;
        for (int i = 0; i < 16; i++) m[i] = 0;
        m[0] = (float) (f / aspect);
        m[5] = (float) f;
        m[10] = -(far + near) / (far - near);
        m[11] = -2f * far * near / (far - near);
        m[14] = -1;
    }

    public int getProjection(Transform dest) { dest.set(proj); return PERSPECTIVE; }
}
