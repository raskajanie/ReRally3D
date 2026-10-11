package javax.microedition.m3g;

public class Appearance extends Object3D {
    CompositingMode compositing; PolygonMode polygon; Texture2D texture;

    public Appearance() {}
    public void setCompositingMode(CompositingMode m) { compositing = m; }
    public void setPolygonMode(PolygonMode m) { polygon = m; }
    public void setTexture(int unit, Texture2D t) { if (unit == 0) texture = t; }
}
