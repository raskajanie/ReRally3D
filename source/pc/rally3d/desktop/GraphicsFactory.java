package rally3d.desktop;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.GraphicsBridge;

final class GraphicsFactory {
    private GraphicsFactory() {}
    static Graphics create(int pw, int ph, double scale, boolean alpha) { return GraphicsBridge.create(pw, ph, scale, alpha); }
    static void resize(Graphics g, int pw, int ph, double scale, boolean alpha) { GraphicsBridge.resize(g, pw, ph, scale, alpha); }
}
