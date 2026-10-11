package javax.microedition.m3g;

public class Background extends Object3D {
    boolean colorClear = true, depthClear = true; int color;

    public Background() {}
    public void setColorClearEnable(boolean e) { colorClear = e; }
    public void setDepthClearEnable(boolean e) { depthClear = e; }
    public void setColor(int argb) { color = argb; }
}
