package javax.microedition.m3g;

public class PolygonMode extends Object3D {
    public static final int CULL_BACK = 160, CULL_FRONT = 161, CULL_NONE = 162;
    public static final int WINDING_CCW = 168, WINDING_CW = 169;
    int culling = CULL_BACK, winding = WINDING_CCW;
    boolean perspectiveCorrection;

    public PolygonMode() {}
    public void setCulling(int c) { culling = c; }
    public void setWinding(int w) { winding = w; }
    public void setPerspectiveCorrectionEnable(boolean e) { perspectiveCorrection = e; }
}
