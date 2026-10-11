package com.nokia.mid.appl.rally3d;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Texture2D;

class Billboard {
    short[] frameX = new short[6];
    short[] frameY = new short[6];
    short[] frameWidth = new short[6];
    short[] frameHeight = new short[6];
    Image sheet;
    int[][] framePixels = new int[6][];
    int[][][] opaqueRuns = new int[6][][];
    Texture2D gpuTexture;
    float[][] frameUvs = new float[6][4];

    Billboard(String string, String string2) {
        this(RallyGame.loadImage(string), Billboard.class.getResourceAsStream(string2), string2);
    }

    /** Custom sprite of a map pack: the LOD sheet image and the 48-byte .off table. */
    Billboard(Image sheetImage, InputStream offStream) {
        this(sheetImage, offStream, "(map sprite)");
    }

    private Billboard(Image sheetImage, InputStream inputStream, String string2) {
        this.sheet = sheetImage;
        try {
            int n;
            if (inputStream == null) throw new IOException("missing resource " + string2);
            for (n = 0; n < 6; ++n) {
                this.frameX[n] = (short)(inputStream.read() + inputStream.read() * 256);
            }
            for (n = 0; n < 6; ++n) {
                this.frameY[n] = (short)(inputStream.read() + inputStream.read() * 256);
            }
            for (n = 0; n < 6; ++n) {
                this.frameWidth[n] = (short)(inputStream.read() + inputStream.read() * 256);
            }
            for (n = 0; n < 6; ++n) {
                this.frameHeight[n] = (short)(inputStream.read() + inputStream.read() * 256);
            }
            inputStream.close();
            for (n = 0; n < 6; ++n) {
                int w = this.frameWidth[n], h = this.frameHeight[n];
                if (w > 0 && h > 0) {
                    this.framePixels[n] = new int[w * h];
                    this.sheet.getRGB(this.framePixels[n], 0, w, this.frameX[n], this.frameY[n], w, h);
                    this.opaqueRuns[n] = new int[h][];
                    for (int y = 0; y < h; y++) {
                        ArrayList<Integer> runs = new ArrayList<Integer>();
                        int x = 0;
                        while (x < w) {
                            while (x < w && (this.framePixels[n][y * w + x] >>> 24) == 0) x++;
                            if (x == w) break;
                            int start = x++;
                            while (x < w && (this.framePixels[n][y * w + x] >>> 24) != 0) x++;
                            runs.add(start);
                            runs.add(x);
                        }
                        int[] rowRuns = new int[runs.size()];
                        for (int i = 0; i < rowRuns.length; i++) rowRuns[i] = runs.get(i);
                        this.opaqueRuns[n][y] = rowRuns;
                    }
                }
            }
            int textureWidth = nextPowerOfTwo(this.sheet.getWidth());
            int textureHeight = nextPowerOfTwo(this.sheet.getHeight());
            int[] texturePixels = new int[textureWidth * textureHeight];
            this.sheet.getRGB(texturePixels, 0, textureWidth, 0, 0, this.sheet.getWidth(), this.sheet.getHeight());
            Image gpuImage = Image.createRGBImage(texturePixels, textureWidth, textureHeight, true);
            this.gpuTexture = new Texture2D(new Image2D(Image2D.RGBA, gpuImage));
            for (n = 0; n < 6; n++) {
                this.frameUvs[n][0] = this.frameX[n] / (float)textureWidth;
                this.frameUvs[n][1] = this.frameY[n] / (float)textureHeight;
                this.frameUvs[n][2] = (this.frameX[n] + this.frameWidth[n]) / (float)textureWidth;
                this.frameUvs[n][3] = (this.frameY[n] + this.frameHeight[n]) / (float)textureHeight;
            }
        }
        catch (IOException iOException) { rally3d.core.RLog.caught("Billboard.Billboard", iOException);
            // empty catch block
        }
    }

    void draw(Graphics graphics, int n, int n2, int n3) {
        int width = this.frameWidth[n3];
        int height = this.frameHeight[n3];
        graphics.drawRegion(this.sheet, this.frameX[n3], this.frameY[n3], width, height,
                n - width / 2, n2 - height);
    }

    void drawDepthTested(Graphics3D renderer, Graphics graphics, int x, int y, int frame, float depth, int targetHeight) {
        int width = this.frameWidth[frame], height = this.frameHeight[frame];
        int[] pixels = this.framePixels[frame];
        if (pixels == null) return;
        int displayHeight = targetHeight > 0 ? targetHeight : height;
        int displayWidth = targetHeight > 0 ? Math.max(1, Math.round(width * (displayHeight / (float) height))) : width;
        if (renderer.queueBillboard(graphics, this.gpuTexture, this.frameUvs[frame], pixels, this.opaqueRuns[frame],
                width, height, displayWidth, displayHeight,
                x - displayWidth / 2, y - displayHeight, depth)) return;
        // Until a depth image is available, omit the sprite for this frame.
        // Drawing it untested (including when every pixel is occluded) makes
        // trees and finish signs show through hills and the road.
        if (!renderer.isDepthMapValid()) return;
        renderer.drawDepthTestedImageScaled(graphics, pixels, opaqueRuns[frame], width, height,
                displayWidth, displayHeight, x - displayWidth / 2, y - displayHeight, depth);
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value && result < (1 << 30)) result <<= 1;
        return result;
    }
}
