package javax.microedition.m3g;

public class CompositingMode extends Object3D {
    public static final int ALPHA = 64, ALPHA_ADD = 65, MODULATE = 66, MODULATE_X2 = 67, REPLACE = 68;
    boolean depthTest = true, depthWrite = true, colorWrite = true, alphaWrite = true;
    int blending = REPLACE;

    public CompositingMode() {}
    public void setDepthTestEnable(boolean e) { depthTest = e; }
    public void setDepthWriteEnable(boolean e) { depthWrite = e; }
    public void setColorWriteEnable(boolean e) { colorWrite = e; }
    public void setAlphaWriteEnable(boolean e) { alphaWrite = e; }
    public void setBlending(int mode) { blending = mode; }
}
