package javax.microedition.m3g;

public class TriangleStripArray extends IndexBuffer {
    final int[] indices; final int[] stripLengths;
    public TriangleStripArray(int[] indices, int[] stripLengths) { this.indices = indices.clone(); this.stripLengths = stripLengths.clone(); }
}
