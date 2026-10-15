package com.nokia.mid.appl.rally3d;

import java.io.IOException;
import java.io.InputStream;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;

class RallyGame
extends BaseCanvas
implements Runnable {
    private static final int[] EMPTY_TEXT_INPUT = new int[0];
    private final int[] menuTextInputEvents = new int[256];
    static final int ROAD_CACHE_SIZE = 129;
    static final int[] statSpeed = new int[]{60, 68, 75, 80};
    static final int[] statAccel = new int[]{20, 60, 90, 80};
    static final int[] statGrip = new int[]{40, 80, 80, 90};
    Thread gameThread;
    boolean paused;
    Texts textsInstance;
    SaveData saveData;
    SoundManager sound;
    Graphics graphics;
    Graphics3D g3d;
    Texture2D roadTexture;
    Billboard[] billboards;
    private javax.microedition.m3g.VertexBuffer[] roadBatch;
    Camera camera;
    Transform viewTransform;
    Transform modelTransform;
    Background background;
    Mesh3D[] carMeshes;
    Texture2D[] carTextures;
    Mesh3D wheelMesh;
    Texture2D wheelTexture;
    // --- add-on cars ("Add-ons"): stock cars 0..3, then enabled add-on cars; cars received from other players live in remoteCars (index REMOTE_BASE+k)
    static final int REMOTE_BASE = 1000;
    final CarDef[] stockCars = new CarDef[4];
    final java.util.ArrayList<CarDef> localCars = new java.util.ArrayList<CarDef>();
    final java.util.ArrayList<CarDef> remoteCars = new java.util.ArrayList<CarDef>();
    final java.util.HashMap<String, CarDef> addonCarCache = new java.util.HashMap<String, CarDef>();
    int localCarsRevision = -1;
    boolean carsDepthDisabled;
    CarDef globalWheels;
    /** Wheels pack the opponent uses (received over multiplayer); null = opponent car's own wheels or stock. */
    CarDef opponentWheels;
    Mesh3D curWheelMesh;
    Image skyImage;
    int skyColor;
    int groundColor;
    int segmentCount;
    TrackSegment[] segments;
    RoadSegmentMesh[] roadMeshes;
    SegmentMask cameraMask = new SegmentMask();
    SegmentMask carMask = new SegmentMask();
    boolean forward;
    CameraState cameraState = new CameraState();
    float mouseYawOffset;
    float mouseCameraPitch;
    float cameraDistance = 8.0f;
    float cameraBaseHeight;
    boolean cameraBaseHeightInitialized;
    CarState car = new CarState();
    RaceState race = new RaceState();
    String bannerText;
    /** Optional second banner line (multiplayer pause: who paused / how to resume). */
    String bannerText2;
    int hudTop;
    int hudBottom;
    int splitColor;
    int hudColor;
    String splitText;
    int splitTimer;
    boolean racing;
    int targetTime;
    int startTimeOffset;
    int[] checkpoints = new int[4];
    boolean raceFinished;
    int finishDelay;
    TheBluetooth bluetooth;
    byte[] netBuffer = new byte[128];
    boolean isHost;
    boolean opponentPresent;
    int opponentSender = -1;
    String lastLanAddress = "";
    int maxPlayers = 2;
    String multiplayerPassword = "";
    long nextSyncTime;
    int opponentCar;
    int stockOpponentCar;
    int opponentExtrapFrames;
    Vec3 opponentRotation = new Vec3();
    Vec3 opponentPosition = new Vec3();
    Vec3 opponentVelocity = new Vec3();
    int opponentCorrFrames;
    Vec3 opponentCorrection = new Vec3();
    GhostReplay ghostPlayback;
    GhostReplay ghostRecorder;
    Vec3 ghostPosition = new Vec3();
    Vec3 ghostRotation = new Vec3();
    int ghostFinishMs;

    /** Real milliseconds since the race start (pause-safe: startTimeOffset is re-based on resume). */
    int ghostMs() { return (int)Math.max(0L, this.currentTime() - (long)this.startTimeOffset * 100L); }
    boolean ghostRecordBeaten;
    Vec3[] opponentWheelPts;
    float[] opponentWheelY = new float[4];
    SegmentMask opponentMask = new SegmentMask();
    int opponentFinishTime;
    int opponentRaceTime;
    int multiplayerPingMs = -1;
    /** Ping the OTHER player measured and reported in his state packets (-1 = unknown). */
    int opponentPingMs = -1;
    /** Finished multiplayer racer watching the others (camera follows a remote car, own car is frozen). */
    boolean spectating;
    int spectateIndex;
    private int spectateKeysPrev;
    private int spectateWait;
    private int multiplayerPingSequence;
    private long multiplayerPingSentAt;
    private long nextMultiplayerPingAt;
    boolean showMultiplayerScores;
    String opponentName;
    boolean bluetoothAvailable;
    Image titleImage;
    Image titleUpImage;
    Image titleDownImage;
    Image titleTextImage;
    int barHeight;
    Image[] cupImages = new Image[5];
    Image[] arrowLeft = new Image[2];
    Image[] arrowRight = new Image[2];
    int arrowWidth;
    int arrowHeight;
    int viewX;
    int viewY;
    int viewW;
    int viewH;
    Transform projection;
    String[] trackFiles = new String[]{"track12.bin", "track06.bin", "track07.bin", "track09.bin", "track05.bin", "track02.bin", "track04.bin", "track01.bin", "track03.bin", "track10.bin", "track08.bin", "track11.bin"};
    int wheelsNoSurface;
    int wheelsOffRoad;
    int wheelsAirborne;
    int offRoadSide;
    int rearWheelsOffRoad;
    static short[][] profileBlend = new short[][]{{0, -1, -1, 1, -1, -1, 1, 9, -1, -1, -1, 9, -1, -1, -1}, {2, -1, -1, 3, -1, -1, 4, 2, -1, -1, -1, 2, -1, -1, -1}, {0, -1, -1, 1, -1, -1, 1, 9, -1, -1, -1, 9, -1, -1, -1}, {2, -1, -1, 3, -1, -1, 4, 2, -1, -1, -1, 2, -1, -1, -1}, {5, -1, -1, 5, -1, -1, 6, 5, -1, -1, -1, 5, -1, -1, -1}, {2, -1, -1, 3, -1, -1, 4, 2, -1, -1, -1, 2, -1, -1, -1}, {5, -1, -1, 5, -1, -1, 6, 5, -1, -1, -1, 5, -1, -1, -1}, {8, -1, -1, 8, -1, -1, 8, 7, -1, -1, -1, 12, -1, -1, -1}, {0, -1, -1, 1, -1, -1, 1, 9, -1, -1, -1, 9, -1, -1, -1}, {8, -1, -1, 8, -1, -1, 8, 7, -1, -1, -1, 12, -1, -1, -1}, {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}, {13, -1, -1, 13, -1, -1, 13, 13, -1, -1, -1, 11, -1, -1, -1}, {13, -1, -1, 13, -1, -1, 13, 13, -1, -1, -1, 11, -1, -1, -1}, {8, -1, -1, 8, -1, -1, 8, 7, -1, -1, -1, 12, -1, -1, -1}};
    int[] quadOrder = new int[]{0, 1, 3, 2, 0};
    float[] edgeSides = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
    Vec3 edgeStart = new Vec3();
    Vec3 edgeEnd = new Vec3();
    Vec3 tmpVecA = new Vec3();
    Vec3 edgeDir = new Vec3();
    Vec3 edgeNormal = new Vec3();
    Vec3 intersection = new Vec3();
    Vec3 tmpVecB = new Vec3();
    Vec3 lastEdgeDir = new Vec3();
    Vec3 pushOut = new Vec3();
    int hitQuad;
    int hitStrip;
    long lastVibrateTime = 0L;
    long lastImpactTime = 0L;
    /** Size of the last wall push-out (world units per frame); proxy for the speed component hitting the wall. */
    float lastCollisionDepth = 0.0f;

    /** Wall hit: pulse strength and length grow with how hard the car hit (normal speed relative to the car's top speed). Kept moderate on purpose. */
    void impactVibrate(float normalSpeed, float topSpeed) { /*T*/ final long $t = rally3d.core.Trace.in(624); try {
        long now = System.currentTimeMillis();
        if (now - this.lastImpactTime < 140L) return;
        float r = Math.max(0.0f, Math.min(1.0f, normalSpeed / Math.max(0.1f, topSpeed)));
        if (r < 0.04f) return;   // scraping along a wall: no buzz
        this.lastImpactTime = now;
        float k = (float)Math.sqrt(r);
        this.vibrate(25 + (int)(55.0f * k), 35 + (int)(105.0f * k));   // 25..80 ms, amplitude 35..140 of 255
     } finally { rally3d.core.Trace.out(624, $t); } }
    static final int[] translucentPixels = new int[]{0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF, 0x55FFFFFF};
    String infoTitle;
    String infoSubtitle;
    int infoRows;
    int[] infoTimes = new int[5];
    String[] infoNames = new String[5];
    boolean infoLaidOut;
    String infoFooter;
    int blinkCounter;
    boolean introMode;
    String messageTitle;
    String messageText;
    int screenMode = -1;
    int loadedScreen = -1;
    int loadingPercent = 0;
    String menuTitle1;
    String menuTitle2;
    String[] menuItems;
    boolean[] menuTextEditable;
    String[] menuTextPrefix;
    String[] menuTextValue;
    int[] menuTextLimit;
    boolean textEditing;
    boolean startTextEditingOnMenuOpen;
    int editingMenuIndex = -1;
    String editBuffer = "";
    String lastMenuInputValue;
    boolean textInputCancelled;
    Image[] menuIcons;
    boolean[] menuDisabled;
    int[] menuValues;
    boolean showLeftSoftkey;
    String leftSoftkeyLabel;
    boolean showRightSoftkey;
    String rightSoftkeyLabel;
    int menuItemCount;
    boolean mainMenuTag;   // true only while the main menu is shown: draws the game version bottom-left
    int menuStyle;
    int hideTitleText;
    int showCarPreview;
    int showTextBlock;
    int showTitleBackground;
    boolean menuTimeoutOn;
    long menuTimeout;
    boolean showArrows;
    boolean hasMenuIcons;
    boolean menuHeaderStatic = true;
    int menuWaitMode;
    int softkeyY;
    int arrowBlink;
    int menuListY;
    int selectedIndex;
    int visibleRows;
    int scrollOffset;
    float carPreviewYaw;
    long carPreviewLastNanos;
    int menuSlide;
    int softkeyPressTimer;
    int softkeyPressed;
    int menuSlideEnabled;
    /** Non-null while the add-ons screen is up: drawMenu hands the whole frame to it. */
    AddonUi addonUi;
    MapEditor mapEditor;
    CarEditor carEditor;
    /** Non-null while the editor's free-fly preview runs (FlyView): renderFrame draws its HUD on top of the world. */
    FlyView flyView;
    int menuExitMode;
    boolean redrawBackground;
    int textLeft;
    int textTop;
    int textRight;
    int textBottom;
    int textOffsetY;
    int visibleTextLines;
    int totalTextLines;
    String[] textLines;
    int[] lineWidths;
    int[] lineLengths;
    int textScroll;
    boolean rainbowText;
    int highScoreTrack;
    int rainbowPhase;
    int[] rainbowColors = new int[]{0xFF0000, 16730368, 16758272, 0xFFFF00, 11992832, 4849408, 2406058, 18943, 9568426, 14361770};
    int btTrackChoice;
    String debugText;

    protected void hideNotify() {
        this.paused = true;
    }

    protected void showNotify() {
        this.graphics = this.getGraphics();
        this.repaint();
    }

    public RallyGame(TheMidlet theMidlet) {
        super(theMidlet);
        this.graphics = this.getGraphics();
    }

    void start() { /*T*/ final long $t = rally3d.core.Trace.in(30); try {
        if (this.gameThread == null) {
            super.start();
            this.gameThread = new Thread(this);
            this.gameThread.start();
            this.bluetoothAvailable = true;    // desktop local-network multiplayer
        }
     } finally { rally3d.core.Trace.out(30, $t); } }

    void setViewport(int n, int n2, int n3, int n4) { /*T*/ final long $t = rally3d.core.Trace.in(31); try {
        this.viewX = n;
        this.viewY = n2;
        this.viewW = n3;
        this.viewH = n4;
        this.g3d.setViewport(this.viewX, this.viewY, this.viewW, this.viewH);
        RoadSegmentMesh.setViewport(this.viewX, this.viewY, this.viewW, this.viewH);
     } finally { rally3d.core.Trace.out(31, $t); } }

    void setupCamera() { /*T*/ final long $t = rally3d.core.Trace.in(32); try {
        if (this.camera == null) {
            this.camera = new Camera();
            this.viewTransform = new Transform();
        }
        // Keep the far plane beyond the full 65-segment forward view so the horizon
        // does not visibly end at the old near clipping distance.
        this.camera.setPerspective(60.0f, (float)this.viewW / (float)this.viewH, 1.0f, 4096.0f);
        this.projection = new Transform();
        this.camera.getProjection(this.projection);
        this.g3d.setCamera(this.camera, null);
     } finally { rally3d.core.Trace.out(32, $t); } }

    void updateCamera(float f) { /*T*/ final long $t = rally3d.core.Trace.in(33); try {
        final boolean spec = this.spectating;
        final Vec3 targetPos = spec ? this.watchedPos() : this.car.position;
        final float targetHeading = spec ? this.watchedRot().v[1] : this.car.rotation.v[1];
        boolean mouseLook = this.racing && (rally3d.desktop.Platform.isMouseLookEnabled() || rally3d.desktop.Platform.isSpectateLookEnabled());
        boolean controllerLook = this.racing && rally3d.desktop.Platform.isControllerConnected();
        rally3d.desktop.Platform.setMouseLookActive(this.racing);
        float orbitDistance = f;
        if (this.racing) {
            float wheel = rally3d.desktop.Platform.consumeCameraZoomDelta();
            this.cameraDistance = Math.max(3.0f, Math.min(24.0f, this.cameraDistance + wheel * 0.85f));
            orbitDistance = this.cameraDistance;
        }
        if (rally3d.desktop.Platform.consumeCameraReset()) { this.mouseYawOffset = 0.0f; this.mouseCameraPitch = 0.0f; }
        if (mouseLook || controllerLook) {
            if (mouseLook) this.mouseYawOffset += rally3d.desktop.Platform.consumeMouseLookDeltaX() * 0.28f;
            if (controllerLook) {
                float controllerFps = Math.max(15.0f, Math.min(1000.0f, (float)this.fps));
                this.mouseYawOffset += rally3d.desktop.Platform.controllerCameraX() * 150.0f / controllerFps;
                this.mouseCameraPitch += rally3d.desktop.Platform.controllerCameraY() * 110.0f / controllerFps;
            }
            if (this.mouseYawOffset > 360.0f || this.mouseYawOffset < -360.0f) this.mouseYawOffset %= 360.0f;
            if (mouseLook) this.mouseCameraPitch += rally3d.desktop.Platform.consumeMouseLookDeltaY() * 0.22f;
            this.mouseCameraPitch = Math.max(-20.0f, Math.min(75.0f, this.mouseCameraPitch));
        } else {
            this.mouseCameraPitch = 0.0f;
        }
        float targetYaw = targetHeading + (mouseLook || controllerLook ? this.mouseYawOffset : 0.0f);
        if (mouseLook || controllerLook) {
            // Mouse look should track raw cursor displacement without controller-style easing.
            this.cameraState.yaw = targetYaw;
        } else {
            this.cameraState.yaw += (targetYaw - this.cameraState.yaw) * this.smooth(1.0f / 6.0f);
        }
        this.cameraState.position.v[0] = targetPos.v[0];
        this.cameraState.position.v[2] = targetPos.v[2];
        float targetBaseHeight = targetPos.v[1] + 3.5f;
        if (!this.cameraBaseHeightInitialized) {
            this.cameraBaseHeight = targetBaseHeight;
            this.cameraBaseHeightInitialized = true;
        } else {
            this.cameraBaseHeight += (targetBaseHeight - this.cameraBaseHeight) * this.smooth(1.0f / 1.6f);
        }
        // Never let the orbit camera sink to the road plane: at pitch -20 deg and distance ~10 the camera height equals the car's ground height,
        // the road collapses into a 1 px line and the sky/ground fill shows until the mouse moves. Keep >= 1.2 units above the car's ground.
        if (Float.isNaN(this.mouseCameraPitch)) this.mouseCameraPitch = 0.0f;
        float camDown = Math.max(0.0f, this.cameraBaseHeight - targetPos.v[1] - 1.2f);
        float minPitch = -(float)Math.toDegrees(Math.asin(Math.min(1.0f, camDown / Math.max(0.1f, orbitDistance))));
        this.mouseCameraPitch = Math.max(this.mouseCameraPitch, Math.max(-20.0f, minPitch));
        // Smooth only the car's vertical movement; apply the orbit pitch
        // directly so camera height and view direction stay in sync.
        this.cameraState.position.v[1] = this.cameraBaseHeight
                + (float)Math.sin(Math.toRadians(this.mouseCameraPitch)) * orbitDistance;
        float horizontalDistance = (float)Math.cos(Math.toRadians(this.mouseCameraPitch)) * orbitDistance;
        // Place the camera behind its world-space look direction. Keep the
        // car-relative offset opposite to the view transform's yaw rotation.
        this.cameraState.position.v[0] = (float)((double)this.cameraState.position.v[0] - Math.sin((double)this.cameraState.yaw * Math.PI / 180.0) * (double)horizontalDistance);
        this.cameraState.position.v[2] = (float)((double)this.cameraState.position.v[2] + Math.cos((double)this.cameraState.yaw * Math.PI / 180.0) * (double)horizontalDistance);
     } finally { rally3d.core.Trace.out(33, $t); } }

    void drawStatBar(Graphics graphics, String string, int n, int n2) {
        int n3 = this.width / 2;
        int n4 = this.width / 2 - 6;
        if (Texts.rightToLeft) {
            this.drawText(graphics, 5, n, string, -86, 9);
            n3 = 6;
        } else {
            this.drawText(graphics, 5, n, string, -86, 1);
        }
        graphics.setColor(0);
        graphics.fillRect(n3 - 1, n + (this.fontBoldHeight - 4) / 2 - 1, n4 + 2, 6);
        graphics.setColor(0xFF0000);
        graphics.fillRect(n3, n + (this.fontBoldHeight - 4) / 2, n4, 4);
        if (n2 > 0) {
            graphics.setColor(-86);
            graphics.fillRect(n3, n + (this.fontBoldHeight - 4) / 2, n4 * n2 / 100, 4);
        }
    }

    void drawCarPreview(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(34); try {
        if (this.selectedIndex >= this.localCars.size() || this.menuSlide > 28) {
            return;
        }
        CarDef previewCar = this.carDef(this.selectedIndex);
        this.applyWheelLook(previewCar);
        this.g3d.bindTarget((Object)graphics, true, 0);
        this.g3d.beginBillboardBatch();
        this.g3d.clear(this.background);
        this.modelTransform.setIdentity();
        this.modelTransform.postTranslate(0.0f, 0.0f, -(8.0f + (float)this.menuSlide));
        this.modelTransform.postRotate(35.0f, 1.0f, 0.0f, 0.0f);
        this.modelTransform.postRotate(this.carPreviewYaw, 0.0f, 1.0f, 0.0f);
        previewCar.mesh.render(this.g3d, this.modelTransform);
        this.modelTransform.postTranslate(1.05f, -0.1f, 1.5f);
        this.curWheelMesh.render(this.g3d, this.modelTransform);
        this.modelTransform.postTranslate(-2.1f, 0.0f, 0.0f);
        this.curWheelMesh.render(this.g3d, this.modelTransform);
        this.modelTransform.postTranslate(0.0f, 0.0f, -3.1f);
        this.curWheelMesh.render(this.g3d, this.modelTransform);
        this.modelTransform.postTranslate(2.1f, 0.0f, 0.0f);
        this.curWheelMesh.render(this.g3d, this.modelTransform);
        this.g3d.releaseTarget();
        if (this.menuSlide == 0) {
            if (this.menuDisabled[this.selectedIndex]) {
                this.drawText(graphics, 0, (this.height - this.fontBoldHeight) / 2, Texts.carLocked, -86, 5);
            } else {
                int n = this.height - this.barHeight - this.fontBoldHeight * 3 - 2;
                this.drawStatBar(graphics, Texts.speedLabel, n, previewCar.barSpeed);
                this.drawStatBar(graphics, Texts.accelLabel, n += this.fontBoldHeight, previewCar.barAccel);
                this.drawStatBar(graphics, Texts.gripLabel, n += this.fontBoldHeight, previewCar.barGrip);
            }
        }
     } finally { rally3d.core.Trace.out(34, $t); } }

    // wheel spin: per-slot (0 player, 1 opponent, 2 ghost) angle in degrees,
    // advanced by distance travelled since previous draw (idempotent per frame)
    static final float WHEEL_RADIUS = 0.55f;
    static final float WHEEL_CENTER_Y = 0.54f;
    final float[] wheelSpinDeg = new float[36];
    final float[] wheelLastX = new float[36];
    final float[] wheelLastZ = new float[36];
    final boolean[] wheelLastValid = new boolean[36];

    void resetWheelSpin() { /*T*/ final long $t = rally3d.core.Trace.in(343); try {
        for (int i = 0; i < this.wheelSpinDeg.length; ++i) {
            this.wheelSpinDeg[i] = 0.0f;
            this.wheelLastValid[i] = false;
        }
     } finally { rally3d.core.Trace.out(343, $t); } }

    float updateWheelSpin(int slot, Vec3 pos, Vec3 rot) {
        float x = pos.v[0];
        float z = pos.v[2];
        if (this.wheelLastValid[slot]) {
            float dx = x - this.wheelLastX[slot];
            float dz = z - this.wheelLastZ[slot];
            float dist = (float)Math.sqrt(dx * dx + dz * dz);
            if (dist < 6.0f) {
                double yaw = Math.toRadians(rot.v[1]);
                float along = dx * (float)Math.sin(yaw) - dz * (float)Math.cos(yaw);
                float signed = along >= 0.0f ? dist : -dist;
                float spin = this.wheelSpinDeg[slot] + signed / WHEEL_RADIUS * 57.29578f;
                spin %= 360.0f;
                this.wheelSpinDeg[slot] = spin;
            }
        }
        this.wheelLastX[slot] = x;
        this.wheelLastZ[slot] = z;
        this.wheelLastValid[slot] = true;
        return this.wheelSpinDeg[slot];
    }

    private void renderWheel(Vec3 vec3, float f, float f2, float f3, float wx, float wz, float spin) {
        try {
            this.modelTransform.set(this.viewTransform);
            this.modelTransform.postTranslate(vec3.v[0], vec3.v[1], vec3.v[2]);
            this.modelTransform.postRotate(f2, 0.0f, 1.0f, 0.0f);
            this.modelTransform.postRotate(f3, 0.0f, 0.0f, 1.0f);
            this.modelTransform.postRotate(f, 1.0f, 0.0f, 0.0f);
            this.modelTransform.postTranslate(wx, -0.1f, wz);
            this.modelTransform.postTranslate(0.0f, WHEEL_CENTER_Y, 0.0f);
            this.modelTransform.postRotate(spin, 1.0f, 0.0f, 0.0f);
            this.modelTransform.postTranslate(0.0f, -WHEEL_CENTER_Y, 0.0f);
        }
        catch (Exception exception) { rally3d.core.RLog.caught("RallyGame.renderWheel", exception);
            // empty catch block
        }
        this.curWheelMesh.render(this.g3d, this.modelTransform);
    }

    CarDef spinWheelsOverride;   // wheels pack of the extra (3rd+) player being drawn
    void drawCar(Vec3 vec3, Vec3 vec32, int n, boolean bl) {
        this.drawCar(vec3, vec32, n, bl, bl ? 0 : 1);
    }

    void drawCar(Vec3 vec3, Vec3 vec32, int n, boolean bl, int spinSlot) { /*T*/ final long $t = rally3d.core.Trace.in(35); try {
        float f = -vec32.v[0];
        float f2 = -vec32.v[1] + 180.0f;
        float f3 = vec32.v[2];
        if (f < 0.001f && f > -0.001f) {
            f = 0.001f;
        }
        if (f2 < 0.001f && f2 > -0.001f) {
            f2 = 0.001f;
        }
        if (f3 < 0.001f && f3 > -0.001f) {
            f3 = 0.001f;
        }
        CarDef carDef = this.carDef(n);
        this.applyWheelLook(carDef, spinSlot >= 3 ? this.spinWheelsOverride : spinSlot == 1 ? this.opponentWheels : this.globalWheels);   // slot 1 = opponent: never my own wheels pack
        float spin = this.updateWheelSpin(spinSlot, vec3, vec32);
        this.renderWheel(vec3, f, f2, f3, 1.05f, 1.5f, spin);
        this.renderWheel(vec3, f, f2, f3, -1.05f, 1.5f, spin);
        this.renderWheel(vec3, f, f2, f3, 1.05f, -1.6f, spin);
        this.renderWheel(vec3, f, f2, f3, -1.05f, -1.6f, spin);
        try {
            this.modelTransform.set(this.viewTransform);
            this.modelTransform.postTranslate(vec3.v[0], vec3.v[1], vec3.v[2]);
            this.modelTransform.postRotate(f2, 0.0f, 1.0f, 0.0f);
            this.modelTransform.postRotate(f3 - (bl ? this.car.steer : 0.0f), 0.0f, 0.0f, 1.0f);
            this.modelTransform.postRotate(f, 1.0f, 0.0f, 0.0f);
        }
        catch (Exception exception) { rally3d.core.RLog.caught("RallyGame.drawCar", exception);
            // empty catch block
        }
        carDef.mesh.render(this.g3d, this.modelTransform);
     } finally { rally3d.core.Trace.out(35, $t); } }

    /** CPU sky: tiled sky image, flat sky colour above it and ground colour below it (clip = the 3D viewport). */
    void paintSkyCpu(Graphics graphics, int firstX, int top) { /*T*/ final long $t = rally3d.core.Trace.in(572); try {
        for (int i = firstX; i < this.width; i += this.skyImage.getWidth()) {
            graphics.drawImage(this.skyImage, i, top, 0);
        }
        if (top > 0) {
            graphics.setColor(this.skyColor);
            graphics.fillRect(0, 0, this.width, top);
        }
        int bottom = top + this.skyImage.getHeight();
        if (bottom < this.viewY + this.viewH) {
            graphics.setColor(this.groundColor);
            graphics.fillRect(0, bottom, this.width, this.viewY + this.viewH - bottom);
        }
     } finally { rally3d.core.Trace.out(572, $t); } }

    void drawWorld(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(36); try {
        graphics.setClip(this.viewX, this.viewY, this.viewW, this.viewH);
        // Horizon shift on screen for a camera pitch: (viewH/2) * tan(pitch) / tan(30 deg) (60 deg vertical fov).
        // The old linear height/180 per degree was ~2.7x too small, so the sky looked static vertically.
        int n = this.height / 2 - 110 - (int)(0.5f * (float)this.viewH * (float)Math.tan(Math.toRadians(this.mouseCameraPitch)) / (float)Math.tan(Math.toRadians(30.0)));
        final int skyFirstX = (int)(-(this.cameraState.yaw * SKY_PX_PER_DEG_PER_VIEW_HEIGHT * (float)this.viewH) + 3600.0f) % this.skyImage.getWidth() - this.skyImage.getWidth();
        final int skyTopY = n;
        // GPU layered mode: the GPU draws the sky + 3D into its own layer and this graphics becomes a transparent UI layer (no CPU sky, no readback).
        // The runnable is the CPU fallback for a frame whose GL context is unavailable.
        final Graphics skyTarget = graphics;
        if (!this.g3d.beginWorld(graphics, this.skyImage, skyFirstX, skyTopY, this.skyColor, this.groundColor,
                new Runnable() { public void run() { RallyGame.this.paintSkyCpu(skyTarget, skyFirstX, skyTopY); } })) {
            this.paintSkyCpu(graphics, skyFirstX, skyTopY);
        }
        this.g3d.bindTarget((Object)graphics, true, 0);
        this.g3d.beginBillboardBatch();
        this.g3d.clear(this.background);
        this.viewTransform.setIdentity();
        // Apply pitch in camera-local space, then yaw around the car. Reversing
        // this order pitches around world X, which makes the whole track seem
        // to tilt when the camera is not facing straight down the road.
        this.viewTransform.postRotate(this.mouseCameraPitch, 1.0f, 0.0f, 0.0f);
        this.viewTransform.postRotate(this.cameraState.yaw, 0.0f, 1.0f, 0.0f);
        this.viewTransform.postTranslate(-this.cameraState.position.v[0], -this.cameraState.position.v[1], -this.cameraState.position.v[2]);
        // The road meshes are a 129-entry ring buffer centered on the player.
        // Submit the visible arc as one GPU batch: releasing the M3G target for
        // every roadside billboard forced a full GPU->CPU->GPU frame transfer.
        int trackStep = this.forward ? 1 : -1;
        float orbitAngle = Math.abs((float)Math.IEEEremainder(this.mouseYawOffset, 360.0f));
        boolean renderOppositeTrack = orbitAngle > 70.0f;
        // Keep road geometry available on both sides of the orbit camera. The
        // old one-sided draw window left the view empty when looking past the car.
        // Same submit order as before, but collected so the renderer can run the per-mesh vertex transform on all CPU cores.
        if (this.roadBatch == null || this.roadBatch.length < ROAD_CACHE_SIZE * 2) this.roadBatch = new javax.microedition.m3g.VertexBuffer[ROAD_CACHE_SIZE * 2];
        int roadCount = 0;
        if (renderOppositeTrack) {
            for (int i = this.roadMeshes.length / 2; i >= 1; --i) {
                int n2 = this.cameraState.segmentIndex - trackStep * i;
                if (n2 < 0 || n2 >= this.segmentCount) continue;
                this.roadBatch[roadCount++] = this.roadMeshes[n2 %= ROAD_CACHE_SIZE].vertexBuffer;
            }
        }
        for (int i = this.roadMeshes.length / 2; i >= 0; --i) {
            int n2 = this.cameraState.segmentIndex + trackStep * i;
            if (n2 < 0 || n2 >= this.segmentCount) continue;
            this.roadBatch[roadCount++] = this.roadMeshes[n2 %= ROAD_CACHE_SIZE].vertexBuffer;
        }
        RoadSegmentMesh.renderAll(this.g3d, this.roadBatch, roadCount, this.viewTransform);
        // Queue the cars with the road into the same renderer submission. The
        // old split forced a second synchronous GL readback every racing frame.
        if (this.ghostOn() && this.ghostPlayback != null
                && this.ghostPlayback.sample(this.racing ? this.ghostMs() : 0, this.ghostPosition, this.ghostRotation)) {
            int ghostCar = this.localIndexForCarKey(this.ghostPlayback.carKey);
            if (ghostCar < 0) ghostCar = this.race.carIndex;   // the ghost's car is not installed any more: drawn as the player's own car
            this.drawCar(this.ghostPosition, this.ghostRotation, ghostCar, false, 2);
        }
        if (this.race.mode == 2 && this.opponentPresent) {
            this.drawCar(this.opponentPosition, this.opponentRotation, this.opponentCar, false, 1);
        }
        if (this.race.mode == 2) {
            for (RemoteCar rc : this.extras.values()) {
                if (!rc.seen) continue;
                this.spinWheelsOverride = rc.wheels;
                this.drawCar(rc.pos, rc.rot, rc.car, false, 3 + (rc.id & 31));
            }
            this.spinWheelsOverride = null;
        }
        if (!this.spectating || Math.abs(this.car.segmentIndex - this.cameraState.segmentIndex) <= ROAD_CACHE_SIZE / 2) {
            this.drawCar(this.car.position, this.car.rotation, this.race.carIndex, true);
        }
        long billboardStart = System.nanoTime();
        if (renderOppositeTrack) {
            for (int i = this.roadMeshes.length / 2; i >= 1; --i) {
                int n2 = this.cameraState.segmentIndex - trackStep * i;
                if (n2 < 0 || n2 >= this.segmentCount) continue;
                n2 %= ROAD_CACHE_SIZE;
                if (this.roadMeshes[n2].leftBillboard == -1 && this.roadMeshes[n2].rightBillboard == -1) continue;
                this.roadMeshes[n2].drawBillboards(this.g3d, graphics, this.projection, this.viewTransform, this.cameraState.position);
            }
        }
        for (int i = this.roadMeshes.length / 2; i >= 0; --i) {
            int n2 = this.cameraState.segmentIndex + trackStep * i;
            if (n2 < 0 || n2 >= this.segmentCount) continue;
            n2 %= ROAD_CACHE_SIZE;
            if (this.roadMeshes[n2].leftBillboard == -1 && this.roadMeshes[n2].rightBillboard == -1) continue;
            this.roadMeshes[n2].drawBillboards(this.g3d, graphics, this.projection, this.viewTransform, this.cameraState.position);
        }
        rally3d.desktop.Platform.recordPerformance(3, System.nanoTime() - billboardStart);
        this.g3d.releaseTarget();
        this.g3d.finishBillboardBatch(graphics);
        graphics.setClip(0, 0, this.width, this.height);
        graphics.setFont(this.fontBold);
        if (this.bannerText != null) {
            this.drawText(graphics, 0, this.height / 3, this.bannerText, -86, 5);
            if (this.bannerText2 != null) this.drawText(graphics, 0, this.height / 3 + this.fontBoldHeight + 2, this.bannerText2, 0xFFFFFF, 5);
        }
        if (this.racing) {
            if (rally3d.desktop.Platform.isShowFps()) {
                this.drawText(graphics, this.viewX + 2, this.viewY + 2 + this.fontBoldHeight, rally3d.core.Lang.t("FPS ") + this.fps, 0x00FF00, 0);
            }
            this.drawText(graphics, this.viewX + 2, this.viewY + 2, this.formatTime(this.spectating ? this.opponentRaceTime : this.race.raceTime), this.hudColor, 8);
            if (this.splitTimer > 0) {
                this.drawText(graphics, this.viewX + 2, this.viewY + 2 + this.fontBoldHeight, this.splitText, this.splitColor, 8);
                --this.splitTimer;
            }
            float shownSpeed = this.car.speed;
            if (this.spectating) {   // watching: speed and clock of the watched racer
                shownSpeed = (float)Math.sqrt(this.opponentVelocity.v[0] * this.opponentVelocity.v[0] + this.opponentVelocity.v[2] * this.opponentVelocity.v[2]);
            }
            if (this.saveData.speedKmh == 0) {
                this.drawText(graphics, this.viewX + 2, this.viewY + 2, Texts.mphLabel + (int)(shownSpeed * 9.32f), this.hudColor, 0);
            } else {
                this.drawText(graphics, this.viewX + 2, this.viewY + 2, Texts.kmhLabel + (int)(shownSpeed * 15.0f), this.hudColor, 0);
            }
            if (this.race.mode == 2) {
                if (this.opponentPresent) {
                    if (!this.spectating) this.drawPlayerMarker(graphics, this.opponentPosition, this.opponentName, 0xFFFF8800);
                    if (!this.spectating) for (RemoteCar rc : this.extras.values()) if (rc.seen) this.drawPlayerMarker(graphics, rc.pos, rc.name, EXTRA_COLORS[rc.id % EXTRA_COLORS.length]);
                    else this.drawSpectateHud(graphics);
                    if (this.isTabDown()) this.drawMultiplayerScoreboard(graphics);
                }
            }
        }
     } finally { rally3d.core.Trace.out(36, $t); } }

    /** Top-of-screen hint, shown only to a racer who has finished and is now watching the others. */
    private void drawSpectateHud(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(251); try {
        String who = this.watchedName();
        int count = this.spectateTargetCount();
        String keys = rally3d.desktop.Platform.isTouchDevice() ? "< / >" : "A / D";
        graphics.setFont(this.fontSmall);
        int y = this.viewY + 2;
        this.drawText(graphics, 0, y, rally3d.core.Lang.t("SPECTATING: ") + who + " (" + (this.spectateIndex + 1) + "/" + Math.max(1, count) + ")", 0xFFFFD060, 6);
        this.drawText(graphics, 0, y + this.fontSmallHeight + 2, keys + rally3d.core.Lang.t(" - switch player"), 0xFFFFFFFF, 6);
        graphics.setFont(this.fontBold);
     } finally { rally3d.core.Trace.out(251, $t); } }

    private final Font[] nameFontCache = new Font[64];

    private void drawPlayerMarker(Graphics graphics, Vec3 position, String name, int color) { /*T*/ final long $t = rally3d.core.Trace.in(37); try {
        if (name == null || name.length() == 0) return;
        float[] p = {position.v[0], position.v[1] + 2.2f, position.v[2], 1.0f};
        this.viewTransform.transform(p);
        this.projection.transform(p);
        if (p[3] <= 0.001f) return;
        float nx = p[0] / p[3], ny = p[1] / p[3];
        if (nx < -1.2f || nx > 1.2f || ny < -1.2f || ny > 1.2f) return;
        int x = this.viewX + (int)((nx + 1.0f) * 0.5f * this.viewW);
        int y = this.viewY + (int)((1.0f - ny) * 0.5f * this.viewH);
        // name centred over the car (drawText flag 4 centres on the whole screen, so measure here); outlined, no black box
        // the name shrinks a little with distance from the camera (full size up to ~15 units, min 50 %)
        float dx = position.v[0] - this.cameraState.position.v[0], dy = position.v[1] - this.cameraState.position.v[1], dz = position.v[2] - this.cameraState.position.v[2];
        float dist = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nameScale = Math.max(0.5f, Math.min(1.0f, 1.15f - dist * 0.009f));
        int namePx = Math.max(6, Math.round(this.fontBold.getPixelSize() * nameScale));
        Font nameFont = this.nameFontCache[namePx & 63];
        if (nameFont == null || nameFont.getPixelSize() != namePx) { nameFont = Font.getSizedFont(this.fontBold.getFace(), this.fontBold.getStyle(), namePx); this.nameFontCache[namePx & 63] = nameFont; }
        graphics.setFont(nameFont);
        int tx = x - nameFont.stringWidth(name) / 2;
        this.drawText(graphics, tx, y - nameFont.getHeight() - 2, name, color, 3);
        graphics.setColor(color);
        graphics.drawLine(x, y - 1, x, y + 4);
        graphics.drawLine(x, y + 4, x - 3, y + 1);
        graphics.drawLine(x, y + 4, x + 3, y + 1);
        graphics.setFont(this.fontBold);
     } finally { rally3d.core.Trace.out(37, $t); } }

    private void drawMultiplayerScoreboard(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(38); try {
        // original-style window (drawPanel + shadowed yellow/white small font); columns are right-aligned INSIDE the panel
        // (drawText flag 8 aligns to the SCREEN edge, so it is not used here)
        graphics.setFont(this.fontSmall);
        final int cnt = 2 + this.extrasSeenCount();
        final String[] nm = new String[cnt], car = new String[cnt], spd = new String[cnt], tm = new String[cnt], png = new String[cnt];
        final boolean[] fin = new boolean[cnt];
        nm[0] = this.saveData.playerName; car[0] = this.carDef(this.race.carIndex).name;
        spd[0] = (int)(this.car.speed * 15.0f) + ""; tm[0] = this.formatTime(this.race.raceTime);
        png[0] = this.multiplayerPingMs < 0 ? "--" : this.multiplayerPingMs + "";
        fin[0] = this.raceFinished;
        nm[1] = this.opponentName == null ? rally3d.core.Lang.t("Player 2") : this.opponentName; car[1] = this.carDef(this.opponentCar).name;
        spd[1] = (int)(Math.sqrt(this.opponentVelocity.v[0] * this.opponentVelocity.v[0] + this.opponentVelocity.v[2] * this.opponentVelocity.v[2]) * 15.0f) + "";
        fin[1] = this.opponentFinishTime >= 0;
        tm[1] = this.formatTime(fin[1] ? this.opponentFinishTime : this.opponentRaceTime);
        png[1] = this.opponentPingMs < 0 ? "--" : this.opponentPingMs + "";
        int k = 2;
        for (RemoteCar rc : this.extras.values()) {
            if (!rc.seen) continue;
            nm[k] = rc.name == null ? rally3d.core.Lang.t("Player ") + (rc.id + 1) : rc.name; car[k] = this.carDef(rc.car).name;
            spd[k] = (int)(Math.sqrt(rc.vel.v[0] * rc.vel.v[0] + rc.vel.v[2] * rc.vel.v[2]) * 15.0f) + "";
            fin[k] = rc.finishTime >= 0;
            tm[k] = this.formatTime(fin[k] ? rc.finishTime : this.race.raceTime);
            png[k] = "--";
            ++k;
        }
        final int pad = 6, gap = 10, fh = this.fontSmallHeight, rowH = fh + 3;
        final int wSpd = Math.max(this.fontSmall.stringWidth("999"), this.fontSmall.stringWidth(rally3d.core.Lang.t("km/h"))) ;
        final int wTime = Math.max(this.fontSmall.stringWidth("00:00"), this.fontSmall.stringWidth(rally3d.core.Lang.t("TIME")));
        final int wPing = Math.max(this.fontSmall.stringWidth("999 ms"), this.fontSmall.stringWidth(rally3d.core.Lang.t("PING")));
        int wName = this.fontSmall.stringWidth(rally3d.core.Lang.t("PLAYER / CAR"));
        for (int i = 0; i < cnt; i++) wName = Math.max(wName, this.fontSmall.stringWidth(nm[i] + " | " + car[i]));
        int panelW = pad * 2 + wName + gap + wSpd + gap + wTime + gap + wPing;
        final int maxW = this.viewW - 12;
        if (panelW > maxW) { wName -= panelW - maxW; panelW = maxW; }
        boolean showTelemetry = rally3d.desktop.Platform.isTelemetryEnabled();
        final int panelH = pad * 2 + rowH * (cnt + 1) + (showTelemetry ? rowH : 0);
        final int x = this.viewX + (this.viewW - panelW) / 2, y = this.viewY + 12;
        this.drawPanel(graphics, x, y, panelW, panelH);
        graphics.setClip(x, y, panelW, panelH);
        final int rPing = x + panelW - pad, rTime = rPing - wPing - gap, rSpd = rTime - wTime - gap;
        int ty = y + pad;
        this.drawText(graphics, x + pad, ty, rally3d.core.Lang.t("PLAYER / CAR"), -86, 1);
        this.drawText(graphics, rSpd - this.fontSmall.stringWidth(rally3d.core.Lang.t("km/h")), ty, rally3d.core.Lang.t("km/h"), -86, 1);
        this.drawText(graphics, rTime - this.fontSmall.stringWidth(rally3d.core.Lang.t("TIME")), ty, rally3d.core.Lang.t("TIME"), -86, 1);
        this.drawText(graphics, rPing - this.fontSmall.stringWidth(rally3d.core.Lang.t("PING")), ty, rally3d.core.Lang.t("PING"), -86, 1);
        ty += rowH;
        for (int i = 0; i < cnt; i++) {
            String left = nm[i] + " | " + car[i];
            while (left.length() > 3 && this.fontSmall.stringWidth(left) > wName) left = left.substring(0, left.length() - 2) + "~";
            this.drawText(graphics, x + pad, ty, left, i == 0 ? -86 : -1, 1);
            this.drawText(graphics, rSpd - this.fontSmall.stringWidth(spd[i]), ty, spd[i], -1, 1);
            this.drawText(graphics, rTime - this.fontSmall.stringWidth(tm[i]), ty, tm[i], fin[i] ? -86 : -1, 1);
            String pg = png[i].equals("--") ? "--" : png[i] + " ms";
            this.drawText(graphics, rPing - this.fontSmall.stringWidth(pg), ty, pg, -1, 1);
            ty += rowH;
        }
        if (showTelemetry) {
            String roadState = this.wheelsOffRoad > 0 ? rally3d.core.Lang.t("OFF ROAD") : rally3d.core.Lang.t("ON ROAD");
            this.drawText(graphics, x + pad, ty, rally3d.core.Lang.t("STEER ") + (int)this.car.steer + "  " + roadState + rally3d.core.Lang.t("  CHECKPOINT ") + Math.min(4, this.race.checkpointsPassed + 1) + "/4", -1, 1);
        }
        graphics.setClip(0, 0, this.width, this.height);
        graphics.setFont(this.fontBold);
     } finally { rally3d.core.Trace.out(38, $t); } }

    Texture2D loadTexture(String string, boolean bl, boolean bl2, boolean bl3) { /*T*/ final long $t = rally3d.core.Trace.in(39); try {
        int n = 32;
        if (bl3) {
            // empty if block
        }
        return this.textureFromImage(RallyGame.loadImage(string), bl, bl2);
     } finally { rally3d.core.Trace.out(39, $t); } }

    Texture2D textureFromImage(Image image, boolean bl, boolean bl2) { /*T*/ final long $t = rally3d.core.Trace.in(494); try {
        int n = 32;
        int[][] nArray = null;
        int n2 = bl ? 100 : 99;
        Image2D image2D = new Image2D(n2, (Object)image);
        Texture2D texture2D = new Texture2D(image2D);
        image2D = null;
        if (bl2) {
            texture2D.setBlending(227);
        } else {
            texture2D.setBlending(228);
        }
        return texture2D;
     } finally { rally3d.core.Trace.out(494, $t); } }

    void loadTheme(int n, boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(40); try {
        String string;
        switch (n) {
            default: {
                string = "/desert";
                this.skyColor = -9667900;
                this.groundColor = -4940137;
                this.hudColor = -7770560;
                break;
            }
            case 1: {
                string = "/winter";
                this.skyColor = -12821864;
                this.groundColor = -2828839;
                this.hudColor = -86;
                break;
            }
            case 2: {
                string = "/summer";
                this.skyColor = -10058565;
                this.groundColor = -10198189;
                this.hudColor = -86;
                break;
            }
            case 3: {
                string = "/night";
                this.skyColor = -9078942;
                this.groundColor = -16514546;
                this.hudColor = -86;
            }
        }
        this.skyImage = RallyGame.loadImage(string + "/background.png");
        this.roadTexture = this.loadTexture(string + "/squares.png", false, bl, true);
        this.billboards = new Billboard[7];
        this.billboards[0] = new Billboard(string + "/billboard1.png", string + "/billboard1.off");
        this.billboards[1] = new Billboard(string + "/billboard2.png", string + "/billboard2.off");
        this.billboards[2] = new Billboard(string + "/billboard3.png", string + "/billboard3.off");
        this.billboards[3] = new Billboard("/billboard4.png", "/billboard4.off");
        this.billboards[4] = new Billboard(string + "/billboard1.png", string + "/billboard1.off");
        this.billboards[5] = new Billboard("/billboard6.png", "/billboard6.off");
        this.billboards[6] = new Billboard("/billboard7.png", "/billboard7.off");
     } finally { rally3d.core.Trace.out(40, $t); } }

    void loadCarModels() { /*T*/ final long $t = rally3d.core.Trace.in(41); try {
        this.carMeshes = new Mesh3D[4];
        this.carTextures = new Texture2D[4];
        for (int i = 0; i < 4; ++i) {
            this.carTextures[i] = this.loadTexture("/cars/" + (i + 1) + ".png", false, false, false);
            this.carMeshes[i] = new Mesh3D();
            this.carMeshes[i].load("/cars/" + (i + 1) + ".syn");
            this.carMeshes[i].setTexture(this.carTextures[i]);
        }
        this.modelTransform = new Transform();
        this.wheelMesh = new Mesh3D();
        this.wheelMesh.load("/cars/wheel.syn");
        this.wheelTexture = this.loadTexture("/cars/wheel.png", false, false, false);
        this.wheelMesh.setTexture(this.wheelTexture);
        for (int i = 0; i < 4; ++i) {
            CarDef d = new CarDef();
            d.name = Texts.carNames[i];
            d.speed = statSpeed[i];
            d.barSpeed = statSpeed[i];
            d.barAccel = statAccel[i];
            d.barGrip = statGrip[i];
            d.mesh = this.carMeshes[i];
            d.skin = this.carTextures[i];
            this.stockCars[i] = d;
        }
        this.curWheelMesh = this.wheelMesh;
        try { AddonManager.rescan(); }   // every launch: (re)create the add-ons folders, sort/migrate zips, load enabled.txt (before this, enabled add-ons only appeared after opening the Add-ons menu)
        catch (RuntimeException e) { rally3d.core.RLog.caught("RallyGame.initCars", e); }
        this.localCarsRevision = -1;
        this.refreshLocalCars();
     } finally { rally3d.core.Trace.out(41, $t); } }

    CarDef carDef(int index) { /*T*/ final long $t = rally3d.core.Trace.in(278); try {
        if (index >= REMOTE_BASE) {
            int k = index - REMOTE_BASE;
            if (k < this.remoteCars.size()) return this.remoteCars.get(k);
            return this.stockCars[0];
        }
        if (index >= 0 && index < this.localCars.size()) return this.localCars.get(index);
        return this.stockCars[0];
     } finally { rally3d.core.Trace.out(278, $t); } }

    /** Stable key of a car for ghost files: stock car = 0..3, add-on car = AddonPack.carKey() (>= 1000). */
    int ghostKeyOf(int carIndex) { /*T*/ final long $t = rally3d.core.Trace.in(729); try {
        CarDef d = this.carDef(carIndex);
        if (d == null || d.pack == null) return carIndex >= 0 && carIndex < 4 ? carIndex : 0;
        return d.pack.carKey();
     } finally { rally3d.core.Trace.out(729, $t); } }

    /** Index in localCars of the car with this ghost key, -1 when that car is not available (add-on switched off / deleted). */
    int localIndexForCarKey(int key) { /*T*/ final long $t = rally3d.core.Trace.in(730); try {
        if (key >= 0 && key < 4) return key < this.localCars.size() ? key : -1;
        for (int i = 4; i < this.localCars.size(); i++) {
            CarDef d = this.localCars.get(i);
            if (d != null && d.pack != null && d.pack.carKey() == key) return i;
        }
        return -1;
     } finally { rally3d.core.Trace.out(730, $t); } }

    Texture2D textureFromBytes(byte[] data) throws java.io.IOException { /*T*/ final long $t = rally3d.core.Trace.in(279); try {
        Image image = Image.createImage(data, 0, data.length);
        Image2D image2D = new Image2D(99, (Object)image);
        Texture2D texture2D = new Texture2D(image2D);
        texture2D.setBlending(228);
        return texture2D;
     } finally { rally3d.core.Trace.out(279, $t); } }

    /** Builds (or returns the cached) car from an add-on pack; null if the pack cannot be used. */
    CarDef addonCar(AddonPack p, boolean remote) { /*T*/ final long $t = rally3d.core.Trace.in(280); try {
        String key = p.shaHex() + (p.wheelsOnly ? "w" : "c");
        CarDef cached = this.addonCarCache.get(key);
        if (cached != null) return cached;
        try {
            int base = Math.max(0, Math.min(3, p.base));
            CarDef d = new CarDef();
            d.pack = p;
            d.remote = remote;
            d.name = p.name;
            if (!p.wheelsOnly) {
                d.speed = p.speed > 0 ? p.speed : statSpeed[base];
                d.accel = p.accel; d.steer = p.steer; d.steerMax = p.steerMax; d.grip = p.grip;
                d.barSpeed = Math.min(100, d.speed);
                d.barAccel = Math.max(1, Math.min(100, Math.round(statAccel[base] * p.accel)));
                d.barGrip = Math.max(1, Math.min(100, Math.round(statGrip[base] * p.grip)));
                d.mesh = new Mesh3D();
                if (p.syn != null) d.mesh.loadBytes(p.syn); else d.mesh.load("/cars/" + (base + 1) + ".syn");
                d.skin = p.skin != null ? this.textureFromBytes(p.skin) : this.stockCars[base].skin;
                d.mesh.setTexture(d.skin);
                d.mesh.setDepthEnabled(!this.carsDepthDisabled);
            }
            if (p.wheelSyn != null) { d.wheelMesh = new Mesh3D(); d.wheelMesh.loadBytes(p.wheelSyn); d.wheelMesh.setDepthEnabled(!this.carsDepthDisabled); }
            if (p.wheelPng != null) d.wheelSkin = this.textureFromBytes(p.wheelPng);
            this.addonCarCache.put(key, d);
            rally3d.core.RLog.i("addon", "built " + (p.wheelsOnly ? "wheels " : "car ") + "'" + p.name + "' speed=" + d.speed + " accel=" + d.accel + " steer=" + d.steer + " steerMax=" + d.steerMax + " grip=" + d.grip + (remote ? " (from another player)" : ""));
            return d;
        } catch (Exception e) {
            rally3d.core.RLog.caught("RallyGame.addonCar", e);
            rally3d.core.RLog.e("addon", "cannot build '" + p.name + "': " + e);
            return null;
        }
     } finally { rally3d.core.Trace.out(280, $t); } }

    /** Rebuilds the list of selectable cars after add-ons were switched on/off (no restart needed). */
    void refreshLocalCars() { /*T*/ final long $t = rally3d.core.Trace.in(281); try {
        if (this.stockCars[0] == null) return;
        if (this.localCarsRevision == AddonManager.revision && !this.localCars.isEmpty()) return;
        this.localCars.clear();
        for (int i = 0; i < 4; ++i) this.localCars.add(this.stockCars[i]);
        for (AddonPack p : AddonManager.enabledCars()) {
            CarDef d = this.addonCar(p, false);
            if (d != null) this.localCars.add(d);
        }
        AddonPack w = AddonManager.enabledWheels();
        this.globalWheels = w == null ? null : this.addonCar(w, false);
        this.localCarsRevision = AddonManager.revision;
     } finally { rally3d.core.Trace.out(281, $t); } }

    void applyWheelLook(CarDef d) { this.applyWheelLook(d, this.globalWheels); }

    /** Wheel look order: the car's own wheels, then the wheels pack of its driver (mine / the opponent's), then stock. */
    void applyWheelLook(CarDef d, CarDef g) { /*T*/ final long $t = rally3d.core.Trace.in(282); try {
        Mesh3D m = d.wheelMesh != null ? d.wheelMesh : g != null && g.wheelMesh != null ? g.wheelMesh : this.wheelMesh;
        Texture2D t = d.wheelSkin != null ? d.wheelSkin : g != null && g.wheelSkin != null ? g.wheelSkin : this.wheelTexture;
        m.setTexture(t);
        this.curWheelMesh = m;
     } finally { rally3d.core.Trace.out(282, $t); } }

    private void showAddonStatus(String text) { /*T*/ final long $t = rally3d.core.Trace.in(283); try {
        this.layoutText(2, this.barHeight + 2, this.width - 5, this.height - this.barHeight - 3, text, true);
        this.redrawBackground = true;
        this.renderFrame();
     } finally { rally3d.core.Trace.out(283, $t); } }

    /** Multiplayer: exchange add-on cars so the opponent really sees (and is drawn with) the modded car. Runs after player info is exchanged. */
    void syncAddonCars() { /*T*/ final long $t = rally3d.core.Trace.in(284); try {
        this.remoteCars.clear();
        this.opponentWheels = null;
        for (RemoteCar rc : this.extras.values()) { rc.wheels = null; rc.car = rc.stockCar; }
        this.evictUnusedAddonCars();
        if (this.bluetooth == null) return;
        AddonSync sync = this.bluetooth.addon;
        sync.setRole(this.isHost, this.myPlayerId());
        CarDef mine = this.carDef(this.race.carIndex);
        sync.setMine(AddonSync.SLOT_CAR, mine.pack != null && !mine.pack.wheelsOnly ? mine.pack.zip : null);
        sync.setMine(AddonSync.SLOT_WHEELS, this.globalWheels != null && this.globalWheels.pack != null ? this.globalWheels.pack.zip : null);
        int peer = this.isHost ? Math.max(0, this.opponentSender) : 0;
        final AddonSync.Remote rc0 = sync.remote(peer, AddonSync.SLOT_CAR), rw0 = sync.remote(peer, AddonSync.SLOT_WHEELS);
        // everybody else: the host holds guest packs itself, a guest downloads them from the host (relay slots)
        final java.util.ArrayList<RemoteCar> xs = new java.util.ArrayList<RemoteCar>(this.extras.values());
        final AddonSync.Remote[] xc = new AddonSync.Remote[xs.size()], xw = new AddonSync.Remote[xs.size()];
        for (int i = 0; i < xs.size(); i++) {
            int id = xs.get(i).id;
            xc[i] = this.isHost ? sync.remote(id, AddonSync.SLOT_CAR) : sync.remote(0, AddonSync.vslot(id, AddonSync.SLOT_CAR));
            xw[i] = this.isHost ? sync.remote(id, AddonSync.SLOT_WHEELS) : sync.remote(0, AddonSync.vslot(id, AddonSync.SLOT_WHEELS));
        }
        java.util.ArrayList<AddonSync.Remote> all = new java.util.ArrayList<AddonSync.Remote>();
        all.add(rc0); all.add(rw0);
        for (int i = 0; i < xs.size(); i++) { all.add(xc[i]); all.add(xw[i]); }
        long start = System.currentTimeMillis(), nextOffer = 0L, lastUi = 0L, doneAt = 0L, primaryDoneAt = 0L;
        int previousScreen = this.screenMode;
        boolean ui = false;
        while (System.currentTimeMillis() - start < 45000L) {
            long now = System.currentTimeMillis();
            if (now >= nextOffer && (doneAt == 0L || now - doneAt < 1500L)) { sync.sendOffer(); nextOffer = now + 500L; }
            boolean done = true, downloading = false;
            long total = 0L, got = 0L;
            for (AddonSync.Remote r : all) {
                if (!(r.complete || r.failed)) done = false;
                if (r.buf != null) { downloading = true; total += r.size; got += r.got; }
            }
            if (primaryDoneAt == 0L && (rc0.complete || rc0.failed) && (rw0.complete || rw0.failed)) primaryDoneAt = now;
            if (!done && primaryDoneAt != 0L && now - primaryDoneAt > 10000L) { rally3d.core.RLog.w("addon", "extra players' packs not complete 10 s after the main opponent's: continuing with stock for the rest"); break; }
            if (done) { if (doneAt == 0L) doneAt = now; if (now - doneAt >= 1500L) break; }
            else if (downloading && !ui) {   // a download is really happening: show progress like the lobby does
                ui = true;
                this.beginMenu(rally3d.core.Lang.t("Multiplayer Lobby"));
                this.addMenuItem("", 0);
                this.showLeftSoftkey = false; this.showRightSoftkey = false;
                this.showTextBlock = 1; this.showTitleBackground = 0; this.showArrows = false;
                this.menuSlide = 0; this.menuWaitMode = 0; this.visibleRows = 0; this.scrollOffset = 0; this.selectedIndex = 0;
                this.screenMode = 2;
            }
            if (ui && now - lastUi > 150L) {
                lastUi = now;
                int pct = total > 0 ? (int)(got * 100L / total) : 0;
                this.showAddonStatus(rally3d.core.Lang.t("Downloading the other players' cars... ") + pct + "%|" + (total / 1024) + rally3d.core.Lang.t(" KB"));
            }
            this.sleep(15);
            if (this.bluetooth.linkLost()) break;
        }
        if (ui) this.screenMode = previousScreen;
        this.opponentCar = this.stockOpponentCar;
        CarDef pc = this.remotePackCar(rc0, false, "opponent");
        if (pc != null) this.opponentCar = REMOTE_BASE + this.remoteCars.size() - 1;
        this.opponentWheels = this.remotePackCar(rw0, true, "opponent");
        for (int i = 0; i < xs.size(); i++) {
            RemoteCar x = xs.get(i);
            CarDef c = this.remotePackCar(xc[i], false, "player " + x.id);
            if (c != null) x.car = REMOTE_BASE + this.remoteCars.size() - 1;
            x.wheels = this.remotePackCar(xw[i], true, "player " + x.id);
        }
        rally3d.core.RLog.i("addon", "opponent car = " + this.opponentCar + " (" + this.carDef(this.opponentCar).name + "), wheels = " + (this.opponentWheels == null ? "own/stock" : this.opponentWheels.name) + ", extra players: " + xs.size());
     } finally { rally3d.core.Trace.out(284, $t); } }

    /** Builds the car (or the wheels pack) a remote player sent; null = stock. A car is also added to remoteCars. */
    private CarDef remotePackCar(AddonSync.Remote rem, boolean wheelsSlot, String who) { /*T*/ final long $t = rally3d.core.Trace.in(344); try {
        if (!rem.complete || rem.pack == null) {
            if (rem.failed || !rem.complete) rally3d.core.RLog.w("addon", who + (wheelsSlot ? " wheels" : " car") + " pack not received (failed=" + rem.failed + "), stock");
            return null;
        }
        AddonPack p = AddonPack.parse(rem.pack, null);
        CarDef d = wheelsSlot ? (p.error == null && p.wheelsOnly ? this.addonCar(p, true) : null)
                : (p.error == null && !p.wheelsOnly && !p.mapOnly ? this.addonCar(p, true) : null);
        if (d == null) { rally3d.core.RLog.w("addon", who + (wheelsSlot ? " wheels" : " car") + " pack unusable" + (p.error != null ? ": " + p.error : "")); return null; }
        if (!wheelsSlot) this.remoteCars.add(d);
        return d;
     } finally { rally3d.core.Trace.out(344, $t); } }

    /** Drops cached cars (and their GPU textures) that neither the local car list nor the wheel override use: opponents' packs of earlier rounds, switched-off add-ons. */
    void evictUnusedAddonCars() { /*T*/ final long $t = rally3d.core.Trace.in(322); try {
        java.util.HashSet<CarDef> used = new java.util.HashSet<CarDef>(this.localCars);
        if (this.globalWheels != null) used.add(this.globalWheels);
        if (this.opponentWheels != null) used.add(this.opponentWheels);
        for (RemoteCar rc : this.extras.values()) if (rc.wheels != null) used.add(rc.wheels);
        for (java.util.Iterator<CarDef> it = this.addonCarCache.values().iterator(); it.hasNext();) {
            CarDef d = it.next();
            if (used.contains(d)) continue;
            try {
                if (d.skin != null && d.pack != null && d.pack.skin != null) this.g3d.releaseTexture(d.skin);
                if (d.wheelSkin != null) this.g3d.releaseTexture(d.wheelSkin);
            } catch (RuntimeException e) { rally3d.core.RLog.caught("RallyGame.evictUnusedAddonCars", e); }
            it.remove();
        }
     } finally { rally3d.core.Trace.out(322, $t); } }

    void setCarsDepthDisabled(boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(42); try {
        this.carsDepthDisabled = bl;
        for (int i = 0; i < 4; ++i) {
            this.carMeshes[i].setDepthEnabled(!bl);
        }
        this.wheelMesh.setDepthEnabled(!bl);
        for (CarDef d : this.addonCarCache.values()) {
            if (d.mesh != null) d.mesh.setDepthEnabled(!bl);
            if (d.wheelMesh != null) d.wheelMesh.setDepthEnabled(!bl);
        }
     } finally { rally3d.core.Trace.out(42, $t); } }

    void init3D(boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(43); try {
        this.g3d = Graphics3D.getInstance();
        if (bl) {
            this.setViewport(0, this.hudTop, this.width, this.height - this.hudBottom - this.hudTop);
        } else {
            this.setViewport(0, 0, this.width, this.height);
        }
        this.setupCamera();
        if (this.background == null) {
            this.background = new Background();
            this.background.setColorClearEnable(false);
        }
     } finally { rally3d.core.Trace.out(43, $t); } }

    /** Pack of the custom map being raced (null on stock tracks); read by loadTrack / checkpoint logic. */
    AddonPack customMap;
    /** True while the editor test-drives a map: no records, no resume slot. */
    boolean testDrive;

    void loadTrack(int n) { /*T*/ final long $t = rally3d.core.Trace.in(44); try {
        customMap = null;
        if (MapLibrary.isCustom(n)) {
            customMap = MapLibrary.byId(n);
            if (customMap == null) {
                rally3d.core.RLog.e("track", "custom map " + n + " is not installed, falling back to track 0");
                n = 0;
                this.race.trackIndex = 0;
            }
        }
        rally3d.core.RLog.i("track", "loading track " + n + (customMap != null ? " (custom map '" + customMap.name + "')" : n >= 0 && n < this.trackFiles.length ? " (" + this.trackFiles[n] + ")" : " (INDEX OUT OF RANGE)") + ", heap " + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory() >> 20) + " MB");
        int n2;
        if (this.segments == null) {
            this.segments = new TrackSegment[MapData.MAX_SEGS + 8];
            for (n2 = 0; n2 < this.segments.length; ++n2) {
                this.segments[n2] = new TrackSegment();
            }
        }
        int n3;
        if (customMap != null) {
            n3 = this.loadTrackStream(new java.io.ByteArrayInputStream(customMap.mapBin), -1);
            this.applyCustomCheckpoints(customMap);
        } else {
            n3 = this.loadTrackData("/tracks/" + this.trackFiles[n], n);
        }
        rally3d.core.RLog.i("track", "track " + n + " data loaded: segments=" + this.segmentCount + ", theme=" + n3);
        this.loadTheme(n3, true);
        if (customMap != null) this.applyCustomLook(customMap);
        RoadSegmentMesh.init(this.roadTexture, this.billboards, true);
        this.roadMeshes = new RoadSegmentMesh[ROAD_CACHE_SIZE];
        for (n2 = 0; n2 < this.roadMeshes.length; ++n2) {
            this.roadMeshes[n2] = new RoadSegmentMesh();
        }
     } finally { rally3d.core.Trace.out(44, $t); } }

    /** Custom checkpoints / finish of a map pack (properties "checkpoints=a,b,c,finish"); sign posts are placed like on stock tracks. */
    void applyCustomCheckpoints(AddonPack mp) { /*T*/ final long $t = rally3d.core.Trace.in(495); try {
        String c = mp.mapProps == null ? null : mp.mapProps.getProperty("checkpoints");
        if (c == null) return;
        String[] t = c.split(",");
        if (t.length < 4) return;
        int[] v = new int[4];
        try { for (int k = 0; k < 4; k++) v[k] = Integer.parseInt(t[k].trim()); }
        catch (NumberFormatException e) { rally3d.core.RLog.caught("RallyGame.applyCustomCheckpoints", e); return; }
        for (int k = 0; k < 4; k++) if (v[k] < 6 || v[k] >= this.segmentCount || (k > 0 && v[k] <= v[k - 1])) { rally3d.core.RLog.w("track", "map '" + mp.name + "': bad checkpoints, using automatic ones"); return; }
        for (int k = 0; k < 4; k++) {
            this.segments[this.checkpoints[k]].leftSignType = (short) -1;   // undo the automatic posts
            this.segments[this.checkpoints[k]].rightSignType = (short) -1;
            this.checkpoints[k] = v[k];
            short type = (short) (k == 3 ? 3 : 5);
            this.segments[v[k]].leftSignType = type; this.segments[v[k]].leftSignLane = 0;
            this.segments[v[k]].rightSignType = type; this.segments[v[k]].rightSignLane = 0;
        }
     } finally { rally3d.core.Trace.out(495, $t); } }

    /** Colours, background, road texture and custom sprites of a map pack on top of its base theme. */
    void applyCustomLook(AddonPack mp) { /*T*/ final long $t = rally3d.core.Trace.in(496); try {
        java.util.Properties pr = mp.mapProps == null ? new java.util.Properties() : mp.mapProps;
        try {
            int bgFrom = propInt(pr, "bgFrom", -1), roadFrom = propInt(pr, "roadFrom", -1);
            if (mp.mapBg != null) this.skyImage = Image.createImage(mp.mapBg, 0, mp.mapBg.length);
            else if (bgFrom >= 0 && bgFrom < 4) this.skyImage = RallyGame.loadImage(MapData.THEME_DIRS[bgFrom] + "/background.png");
            if (mp.mapRoad != null) this.roadTexture = this.textureFromImage(Image.createImage(mp.mapRoad, 0, mp.mapRoad.length), false, true);
            else if (roadFrom >= 0 && roadFrom < 4) this.roadTexture = this.loadTexture(MapData.THEME_DIRS[roadFrom] + "/squares.png", false, true, true);
            if (pr.getProperty("sky") != null) {
                this.skyColor = 0xFF000000 | Integer.parseInt(pr.getProperty("sky").trim(), 16);
                this.groundColor = 0xFF000000 | Integer.parseInt(pr.getProperty("ground", "000000").trim(), 16);
                this.hudColor = 0xFF000000 | Integer.parseInt(pr.getProperty("hud", "FFFFAA").trim(), 16);
            }
            Billboard[] all = new Billboard[7 + MapData.CUSTOM_SPRITES];
            System.arraycopy(this.billboards, 0, all, 0, 7);
            for (int i = 0; i < MapData.CUSTOM_SPRITES; i++) {
                if (mp.mapSpritePng[i] == null || mp.mapSpriteOff[i] == null) continue;
                all[7 + i] = new Billboard(Image.createImage(mp.mapSpritePng[i], 0, mp.mapSpritePng[i].length), new java.io.ByteArrayInputStream(mp.mapSpriteOff[i]));
            }
            this.billboards = all;
        } catch (Exception | OutOfMemoryError e) { rally3d.core.RLog.caught("RallyGame.applyCustomLook", e);
            rally3d.core.RLog.e("track", "map '" + mp.name + "': cannot apply its look: " + e);
        }
        // props of a type that has no sprite (unused custom slot, bad image) are dropped instead of crashing the renderer
        for (int i = 0; i < this.segmentCount; i++) {
            TrackSegment sg = this.segments[i];
            if (sg.leftSignType >= this.billboards.length || (sg.leftSignType > 6 && this.billboards[sg.leftSignType] == null)) sg.leftSignType = (short) -1;
            if (sg.rightSignType >= this.billboards.length || (sg.rightSignType > 6 && this.billboards[sg.rightSignType] == null)) sg.rightSignType = (short) -1;
        }
     } finally { rally3d.core.Trace.out(496, $t); } }

    static int propInt(java.util.Properties p, String k, int def) { /*T*/ final long $t = rally3d.core.Trace.in(497); try {
        if (p.getProperty(k) == null) return def;
        try { return Integer.parseInt(p.getProperty(k).trim()); } catch (NumberFormatException e) { rally3d.core.RLog.caught("RallyGame.propInt", e); return def; }
     } finally { rally3d.core.Trace.out(497, $t); } }

    void computeCorners(Vec3[] vec3Array, Vec3[] vec3Array2, Vec3 vec3, Vec3 vec32) {
        this.modelTransform.setIdentity();
        this.modelTransform.postTranslate(vec3.v[0], vec3.v[1], vec3.v[2]);
        this.modelTransform.postRotate(-vec32.v[1], 0.0f, 1.0f, 0.0f);
        if (vec3Array != null) {
            vec3Array[0].v[0] = 2.0f;
            vec3Array[0].v[1] = 0.0f;
            vec3Array[0].v[2] = 4.0f;
            vec3Array[1].v[0] = -2.0f;
            vec3Array[1].v[1] = 0.0f;
            vec3Array[1].v[2] = 4.0f;
            vec3Array[2].v[0] = 2.0f;
            vec3Array[2].v[1] = 0.0f;
            vec3Array[2].v[2] = -4.0f;
            vec3Array[3].v[0] = -2.0f;
            vec3Array[3].v[1] = 0.0f;
            vec3Array[3].v[2] = -4.0f;
            this.modelTransform.transform(vec3Array[0].v);
            this.modelTransform.transform(vec3Array[1].v);
            this.modelTransform.transform(vec3Array[2].v);
            this.modelTransform.transform(vec3Array[3].v);
        }
        if (vec3Array2 != null) {
            vec3Array2[0].v[0] = 6.0f;
            vec3Array2[0].v[1] = 0.0f;
            vec3Array2[0].v[2] = 8.0f;
            vec3Array2[1].v[0] = -6.0f;
            vec3Array2[1].v[1] = 0.0f;
            vec3Array2[1].v[2] = 8.0f;
            vec3Array2[2].v[0] = 6.0f;
            vec3Array2[2].v[1] = 0.0f;
            vec3Array2[2].v[2] = -8.0f;
            vec3Array2[3].v[0] = -6.0f;
            vec3Array2[3].v[1] = 0.0f;
            vec3Array2[3].v[2] = -8.0f;
            this.modelTransform.transform(vec3Array2[0].v);
            this.modelTransform.transform(vec3Array2[1].v);
            this.modelTransform.transform(vec3Array2[2].v);
            this.modelTransform.transform(vec3Array2[3].v);
        }
    }

    void updateOpponentGround() {
        this.groundFor(this.opponentPosition, this.opponentRotation, this.opponentWheelPts, this.opponentWheelY, this.opponentMask);
    }

    void groundFor(Vec3 pos, Vec3 rot, Vec3[] pts, float[] wy, SegmentMask mask) { /*T*/ final long $t = rally3d.core.Trace.in(345); try {
        this.computeCorners(pts, null, pos, rot);
        for (int i = 0; i < 4; ++i) {
            this.findSegments(mask, pts[i]);
            float f = this.groundHeight(mask, pts[i], wy[i]);
            if (f == -999999.0f) continue;
            wy[i] = f;
        }
        rot.v[2] = rot.v[2] + ((wy[3] - wy[2]) * 6.0f - rot.v[2]) / 2.0f;
        if (rot.v[2] > 45.0f) rot.v[2] = 45.0f;
        if (rot.v[2] < -45.0f) rot.v[2] = -45.0f;
        rot.v[0] = rot.v[0] + ((wy[2] + wy[3] - wy[0] - wy[1]) * 4.0f - rot.v[0]) / 2.0f;
        if (rot.v[0] > 45.0f) rot.v[0] = 45.0f;
        if (rot.v[0] < -45.0f) rot.v[0] = -45.0f;
        pos.v[1] = (wy[0] + wy[1] + wy[2] + wy[3]) / 4.0f;
     } finally { rally3d.core.Trace.out(345, $t); } }

    // ================= 3rd+ players (everybody sees everybody; the host relays state / info / add-on packs) =================
    static final int[] EXTRA_COLORS = {0xFF44CCFF, 0xFF66FF66, 0xFFFF66FF, 0xFFFFFF44, 0xFFFF6666, 0xFFAAAAFF};
    static final class RemoteCar {
        final int id;            // global player id: 0 = host, 1.. = guests
        String name;
        int car, stockCar;       // car index (REMOTE_BASE+k for a received add-on car)
        CarDef wheels;
        boolean seen;
        long lastAt = System.nanoTime();
        int corrFrames, extrapFrames;
        final Vec3 pos = new Vec3(), rot = new Vec3(), vel = new Vec3(), corr = new Vec3();
        final Vec3[] wheelPts = {new Vec3(), new Vec3(), new Vec3(), new Vec3()};
        final float[] wheelY = new float[4];
        final SegmentMask mask = new SegmentMask();
        int finishTime = -1;     // race time in 1/10 s once finished
        RemoteCar(int id) { this.id = id; }
    }
    final java.util.LinkedHashMap<Integer, RemoteCar> extras = new java.util.LinkedHashMap<Integer, RemoteCar>();
    final java.util.HashSet<Integer> mpInfoGot = new java.util.HashSet<Integer>();
    int mpInfoTotal = -1;

    /** Grid slot of player id: lateral +-2 by parity, row id/2 behind the line, along segments[5].startDir. */
    void slotPosition(int id, Vec3 out) { /*T*/ final long $t = rally3d.core.Trace.in(351); try {
        TrackSegment sg = this.segments[5];
        float dx = sg.startDir.v[0], dz = sg.startDir.v[2];
        float len = (float)Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4f) { dx = 1.0f; dz = 0.0f; len = 1.0f; }
        dx /= len; dz /= len;
        float lat = (id & 1) == 0 ? -2.0f : 2.0f, back = (id >> 1) * 5.0f;
        out.set(sg.startPos);
        out.v[0] += -dz * lat - dx * back;
        out.v[2] += dx * lat - dz * back;
     } finally { rally3d.core.Trace.out(351, $t); } }

    int extrasSeenCount() { int c = 0; for (RemoteCar rc : this.extras.values()) if (rc.seen) ++c; return c; }

    int myPlayerId() { return this.isHost ? 0 : Math.max(0, this.bluetooth.assignedPlayerId()); }

    /** The extra car of player id (created on demand); null for me and for the primary opponent. */
    RemoteCar extraFor(int id) { /*T*/ final long $t = rally3d.core.Trace.in(346); try {
        if (id < 0 || id >= 32 || id == this.myPlayerId() || id == (this.isHost ? this.opponentSender : 0)) return null;
        RemoteCar rc = this.extras.get(Integer.valueOf(id));
        if (rc == null) {
            rc = new RemoteCar(id);
            rc.name = rally3d.core.Lang.t("Player ") + (id + 1);
            this.slotPosition(id, rc.pos); rc.rot.set(this.car.rotation);
            this.extras.put(Integer.valueOf(id), rc);
            rally3d.core.RLog.i("mp", "extra player " + id + " appeared (total extras " + this.extras.size() + ")");
        }
        return rc;
     } finally { rally3d.core.Trace.out(346, $t); } }

    /** State of an extra player: type 4 (host, from a guest, payload at 2) or type 9 (guest, relayed, payload at 3). */
    void applyExtraState(int id, byte[] b, int off) { /*T*/ final long $t = rally3d.core.Trace.in(347); try {
        RemoteCar rc = this.extraFor(id);
        if (rc == null) return;
        float x = this.readFixed(b, off), z = this.readFixed(b, off + 4);
        rc.rot.v[1] = this.readFixed(b, off + 8);
        rc.vel.v[0] = this.readFixed(b, off + 12);
        rc.vel.v[2] = this.readFixed(b, off + 16);
        float maxRemote = (float)this.carDef(rc.car).speed / 10.0f * 1.02f;
        float sp = (float)Math.sqrt(rc.vel.v[0] * rc.vel.v[0] + rc.vel.v[2] * rc.vel.v[2]);
        if (sp > maxRemote && sp > 0.0f) { float k = maxRemote / sp; rc.vel.v[0] *= k; rc.vel.v[2] *= k; }
        float lead = Math.min(Math.max(this.multiplayerPingMs, 0), 200) / 200.0f;
        x += rc.vel.v[0] * lead; z += rc.vel.v[2] * lead;
        float sx = x - rc.pos.v[0], sz = z - rc.pos.v[2];
        if (!rc.seen || sx * sx + sz * sz > 900.0f) { rc.seen = true; rc.pos.v[0] = x; rc.pos.v[2] = z; rc.corrFrames = 0; }
        else { rc.corrFrames = 4; rc.corr.v[0] = sx / 4.0f; rc.corr.v[2] = sz / 4.0f; }
        rc.extrapFrames = (int)this.fps;
        rc.lastAt = System.nanoTime();
     } finally { rally3d.core.Trace.out(347, $t); } }

    /** Relayed player info (type 11): [11, len, origin, car, nameLen, name UTF-16BE]. */
    void applyRelayedInfo(byte[] b) { /*T*/ final long $t = rally3d.core.Trace.in(348); try {
        int origin = b[2] & 0xFF;
        if (b.length < 5 || origin == this.myPlayerId()) return;
        int nl = b[4] & 0xFF;
        this.mpInfoGot.add(Integer.valueOf(origin));
        RemoteCar rc = this.extraFor(origin);
        if (rc == null) return;
        try { if (5 + nl <= b.length) rc.name = new String(b, 5, nl, "UTF-16BE").trim(); }
        catch (java.io.UnsupportedEncodingException e) { rally3d.core.RLog.caught("RallyGame.applyRelayedInfo", e); }
        if (rc.name == null || rc.name.length() == 0) rc.name = rally3d.core.Lang.t("Player ") + (origin + 1);
        int oc = b[3];
        rc.stockCar = oc >= 0 && oc < 4 ? oc : 0;
        if (rc.car < REMOTE_BASE) rc.car = rc.stockCar;
     } finally { rally3d.core.Trace.out(348, $t); } }

    void updateExtras(float f, boolean anyPause) { /*T*/ final long $t = rally3d.core.Trace.in(349); try {
        long now = System.nanoTime();
        for (java.util.Iterator<RemoteCar> it = this.extras.values().iterator(); it.hasNext();) {
            RemoteCar rc = it.next();
            if (!anyPause && rc.seen && now - rc.lastAt > 6_000_000_000L) { rally3d.core.RLog.i("mp", "extra player " + rc.id + " silent for 6 s: removed"); it.remove(); continue; }
            if (rc.extrapFrames > 0) { rc.pos.v[0] += rc.vel.v[0] * f; rc.pos.v[2] += rc.vel.v[2] * f; --rc.extrapFrames; }
            if (rc.corrFrames > 0) { rc.pos.v[0] += rc.corr.v[0]; rc.pos.v[2] += rc.corr.v[2]; --rc.corrFrames; }
            if (rc.seen) this.groundFor(rc.pos, rc.rot, rc.wheelPts, rc.wheelY, rc.mask);
        }
     } finally { rally3d.core.Trace.out(349, $t); } }

    void updateWheels(float f) {
        float f2 = 0.35f * (f * f);
        this.wheelsNoSurface = 0;
        this.wheelsOffRoad = 0;
        this.wheelsAirborne = 4;
        this.offRoadSide = 0;
        this.rearWheelsOffRoad = 0;
        int n = 0;
        while (n < 4) {
            float f3 = this.car.wheelY[n];
            float f4 = this.car.wheelY[n] + this.car.wheelVelY[n];
            this.findSegments(this.carMask, this.car.wheelPositions[n]);
            float f5 = this.groundHeight(this.carMask, this.car.wheelPositions[n], this.car.wheelY[n]);
            if (f5 == -999999.0f) {
                ++this.wheelsNoSurface;
            } else {
                float f6;
                // Anti-launch: ground-height jumps (hidden bumps, overlapping segments) must not
                // become upward momentum. Cap the takeoff velocity taken from the ground delta.
                final float launchCap = MAX_GROUND_LAUNCH * f;
                if (f3 < f5 && this.car.wheelVelY[n] < (f6 = Math.min(launchCap, f5 - this.car.prevGroundY[n]))) {
                    this.car.wheelVelY[n] = f6;
                }
                boolean stuck = false;
                if (f4 < f5) {
                    this.car.wheelY[n] = f5;
                    f6 = Math.min(launchCap, f5 - this.car.prevGroundY[n]);
                    if (this.car.wheelVelY[n] <= f6) {
                        this.car.wheelVelY[n] = f6;
                    }
                } else if (f3 - this.car.prevGroundY[n] < 0.02f && f4 - f5 < GROUND_STICK * f && f5 - this.car.prevGroundY[n] > -GROUND_STICK_DROP * f) {
                    // Ground stick: a wheel that was on the road and would lift off by only a small gap
                    // (crest of a roller / washboard bump) stays on the road instead of hopping.
                    this.car.wheelY[n] = f5;
                    this.car.wheelVelY[n] = Math.min(launchCap, f5 - this.car.prevGroundY[n]);
                    stuck = true;
                } else {
                    this.car.wheelY[n] = f4;
                }
                this.car.prevGroundY[n] = f5;
                if (this.hitStrip > 4 || this.hitStrip < 2) {
                    ++this.wheelsOffRoad;
                    if (n >= 2) {
                        ++this.rearWheelsOffRoad;
                    }
                    this.offRoadSide = this.hitStrip < 3 ? -1 : 1;
                }
                if (f3 <= f5 || f4 <= f5 || stuck) {
                    --this.wheelsAirborne;
                }
            }
            int n2 = n++;
            this.car.wheelVelY[n2] = this.car.wheelVelY[n2] - f2;
        }
        this.car.rotation.v[2] = this.car.rotation.v[2] + ((this.car.wheelY[3] - this.car.wheelY[2]) * 6.0f - this.car.rotation.v[2]) / 2.0f;
        if (this.car.rotation.v[2] > 45.0f) {
            this.car.rotation.v[2] = 45.0f;
        }
        if (this.car.rotation.v[2] < -45.0f) { this.car.rotation.v[2] = -45.0f; }
        this.car.rotation.v[0] = this.car.rotation.v[0] + ((this.car.wheelY[2] + this.car.wheelY[3] - this.car.wheelY[0] - this.car.wheelY[1]) * 4.0f - this.car.rotation.v[0]) / 2.0f;
        if (this.car.rotation.v[0] > 45.0f) {
            this.car.rotation.v[0] = 45.0f;
        }
        if (this.car.rotation.v[0] < -45.0f) { this.car.rotation.v[0] = -45.0f; }
        this.car.position.v[1] = (this.car.wheelY[0] + this.car.wheelY[1] + this.car.wheelY[2] + this.car.wheelY[3]) / 4.0f;
    }

    void findSegments(SegmentMask segmentMask, Vec3 vec3) {
        for (int i = 0; i < this.roadMeshes.length; ++i) {
            segmentMask.flags[i] = this.roadMeshes[i].bounds.containsXZ(vec3);
        }
    }

    void addSegments(SegmentMask segmentMask, Vec3 vec3) {
        for (int i = 0; i < this.roadMeshes.length; ++i) {
            if (!this.roadMeshes[i].bounds.containsXZ(vec3)) continue;
            segmentMask.flags[i] = true;
        }
    }

    int firstSegment(SegmentMask segmentMask) {
        int n = 999;
        for (int i = 0; i < this.roadMeshes.length; ++i) {
            if (!segmentMask.flags[i] || this.roadMeshes[i].segmentIndex >= n) continue;
            n = this.roadMeshes[i].segmentIndex;
        }
        if (n == 999) {
            return -1;
        }
        return n;
    }

    // Level-aware: among overlapping road levels pick the segment whose surface
    // at pos is closest to refY (lowest index only breaks ties / is the fallback).
    int firstSegment(SegmentMask segmentMask, Vec3 pos, float refY) {
        return this.firstSegment(segmentMask, pos, refY, -1);
    }

    /**
     * Like above, but with the segment the car was on last step: a candidate within SEGMENT_FOLLOW segments of it
     * wins over another road level unless that one is clearly the better surface. Without this the car "jumps" to
     * a road crossing/below the track (e.g. start road) and the segment<4 guard teleports it back to the start.
     */
    int firstSegment(SegmentMask segmentMask, Vec3 pos, float refY, int prevSeg) {
        if (prevSeg >= 0) {
            int followIdx = -1;
            float followScore = Float.MAX_VALUE;
            for (int i = 0; i < this.roadMeshes.length; ++i) {
                if (!segmentMask.flags[i]) continue;
                int idx = this.roadMeshes[i].segmentIndex;
                if (Math.abs(idx - prevSeg) > SEGMENT_FOLLOW) continue;
                float h = this.meshHeightAt(this.roadMeshes[i], pos, refY);
                if (h == -999999.0f) continue;
                float score = surfaceScore(h, refY);
                if (score < followScore) { followScore = score; followIdx = idx; }
            }
            // a near-continuous surface (within 2.5 units of the car) is accepted as "still on the same road"
            if (followIdx >= 0 && followScore < SURFACE_TOL + 2.5f) return followIdx;
        }
        int bestIdx = -1;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < this.roadMeshes.length; ++i) {
            if (!segmentMask.flags[i]) continue;
            float h = this.meshHeightAt(this.roadMeshes[i], pos, refY);
            if (h == -999999.0f) continue;
            float score = surfaceScore(h, refY);
            int idx = this.roadMeshes[i].segmentIndex;
            if (score < bestScore - 0.01f || (Math.abs(score - bestScore) <= 0.01f && idx < bestIdx)) {
                bestScore = score;
                bestIdx = idx;
            }
        }
        return bestIdx >= 0 ? bestIdx : this.firstSegment(segmentMask);
    }

    void buildRoadWindow(int n) { /*T*/ final long $t = rally3d.core.Trace.in(45); try {
        for (int i = -(ROAD_CACHE_SIZE / 2); i <= ROAD_CACHE_SIZE / 2; ++i) {
            int n2 = n + i;
            if (n2 < 0 || n2 >= this.segmentCount) continue;
            int n3 = n2 % ROAD_CACHE_SIZE;
            TrackSegment trackSegment = n2 > 0 ? this.segments[n2 - 1] : null;
            TrackSegment trackSegment2 = n2 < this.segmentCount - 1 ? this.segments[n2 + 1] : null;
            this.roadMeshes[n3].build(n2, this.segments[n2], trackSegment, trackSegment2);
        }
     } finally { rally3d.core.Trace.out(45, $t); } }

    short blendProfile(short s, short s2) {
        if (s2 == -1) {
            return s;
        }
        if (s2 == s) {
            return s;
        }
        return profileBlend[s2][s];
    }

    int loadTrackData(String string, int n) { /*T*/ final long $t = rally3d.core.Trace.in(46); try {
        InputStream inputStream = this.getClass().getResourceAsStream(string);
        try {
            if (inputStream == null) throw new IOException("missing resource " + string);
        } catch (IOException iOException) { rally3d.core.RLog.caught("RallyGame.loadTrackData", iOException);
            return -1;
        }
        return this.loadTrackStream(inputStream, n);
     } finally { rally3d.core.Trace.out(46, $t); } }

    int loadTrackStream(InputStream inputStream, int n) { /*T*/ final long $t = rally3d.core.Trace.in(564); try {
        Transform transform = new Transform();
        Vec3 vec3 = new Vec3(0.0f, 0.0f, 0.0f);
        Vec3 vec32 = new Vec3(0.0f, 0.0f, RoadSegmentMesh.lengthSteps * 4 / 2);
        float f = 0.0f;
        float f2 = 0.0f;
        float f3 = 0.0f;
        float f4 = 0.0f;
        try {
            int n2;
            short s = (short)(inputStream.read() + inputStream.read() * 256);
            int n3 = inputStream.read() + inputStream.read() * 256;
            short s2 = -1;
            short s3 = -1;
            this.segmentCount = 0;
            for (n2 = 0; n2 < n3; ++n2) {
                int n4 = inputStream.read() + inputStream.read() * 256;
                short s4 = (short)(inputStream.read() + inputStream.read() * 256);
                short s5 = (short)(inputStream.read() + inputStream.read() * 256);
                short s6 = (short)(inputStream.read() + inputStream.read() * 256);
                short s7 = (short)(inputStream.read() + inputStream.read() * 256);
                short s8 = (short)(inputStream.read() + inputStream.read() * 256);
                short s9 = (short)(inputStream.read() + inputStream.read() * 256);
                short s10 = (short)(inputStream.read() + inputStream.read() * 256);
                short s11 = (short)(inputStream.read() + inputStream.read() * 256);
                short s12 = (short)(inputStream.read() + inputStream.read() * 256);
                f3 = (float)((double)s5 / 16384.0 * 1.3);
                float f5 = -((float)((double)s4 * 180.0 / 2048.0));
                for (int i = 0; i < n4 && this.segmentCount < this.segments.length - 2; ++i) {
                    s2 = this.blendProfile(s6, s2);
                    s3 = this.blendProfile(s7, s3);
                    this.segments[this.segmentCount].startPos = new Vec3(vec3);
                    this.segments[this.segmentCount].startDir = new Vec3(vec32);
                    this.segments[this.segmentCount].startHeight = f;
                    this.segments[this.segmentCount].slope = f2;
                    this.segments[this.segmentCount].curvature = f3;
                    this.segments[this.segmentCount].profileStart = s2;
                    this.segments[this.segmentCount].profileEnd = s3;
                    this.segments[this.segmentCount].leftSignType = s9;
                    this.segments[this.segmentCount].leftSignLane = s10;
                    this.segments[this.segmentCount].rightSignType = s11;
                    this.segments[this.segmentCount].rightSignLane = s12;
                    this.segments[this.segmentCount].yawStep = f5;
                    this.segments[this.segmentCount].centerMarking = s8;
                    this.segments[this.segmentCount].accumYaw = f4;
                    if (this.segments[this.segmentCount].leftSignType == -1) {
                        this.segments[this.segmentCount].leftSignType = (short)(RallyGame.randomPositive() % 3);
                    }
                    if (this.segments[this.segmentCount].rightSignType == -1) {
                        this.segments[this.segmentCount].rightSignType = (short)(RallyGame.randomPositive() % 3);
                    }
                    if (this.segments[this.segmentCount].leftSignType == -2) {
                        this.segments[this.segmentCount].leftSignType = (short)-1;
                    }
                    if (this.segments[this.segmentCount].rightSignType == -2) {
                        this.segments[this.segmentCount].rightSignType = (short)-1;
                    }
                    if (this.segments[this.segmentCount].leftSignLane <= -1) {
                        this.segments[this.segmentCount].leftSignLane = (short)(RallyGame.randomPositive() % 2 + 1);
                    }
                    if (this.segments[this.segmentCount].rightSignLane <= -1) {
                        this.segments[this.segmentCount].rightSignLane = (short)(RallyGame.randomPositive() % 2 + 1);
                    }
                    ++this.segmentCount;
                    transform.setIdentity();
                    transform.postRotate(f5, 0.0f, 1.0f, 0.0f);
                    for (int j = 0; j < RoadSegmentMesh.sections; ++j) {
                        vec3.v[1] = f;
                        vec3.add(vec3, vec32);
                        for (int k = 0; k < RoadSegmentMesh.lengthSteps / 2; ++k) {
                            f += f2;
                            f2 += f3;
                            transform.transform(vec32.v);
                            f4 += f5;
                        }
                    }
                }
            }
            inputStream.close();
            if (n == 0) {
                this.segments[27].rightSignType = (short)6;
                this.segments[27].rightSignLane = 1;
            }
            this.checkpoints[3] = this.segmentCount - 5;
            this.segments[this.checkpoints[3]].leftSignType = (short)3;
            this.segments[this.checkpoints[3]].leftSignLane = 0;
            this.segments[this.checkpoints[3]].rightSignType = (short)3;
            this.segments[this.checkpoints[3]].rightSignLane = 0;
            for (n2 = 0; n2 < 3; ++n2) {
                this.checkpoints[n2] = this.segmentCount * (n2 + 1) / 4;
                this.segments[this.checkpoints[n2]].leftSignType = (short)5;
                this.segments[this.checkpoints[n2]].leftSignLane = 0;
                this.segments[this.checkpoints[n2]].rightSignType = (short)5;
                this.segments[this.checkpoints[n2]].rightSignLane = 0;
            }
            return s;
        }
        catch (IOException iOException) { rally3d.core.RLog.caught("RallyGame.loadTrackData", iOException);
            return -1;
        }  } finally { rally3d.core.Trace.out(564, $t); } }

    /** Walls of a road further than this above / below the car are on another level and ignored. */
    static final float WALL_LEVEL_ABOVE = 4.0f, WALL_LEVEL_BELOW = 14.0f;
    /** Walls of road meshes further than this many segments from the car are ignored. */
    /**
     * Sky scroll speed. The 3D camera has a 60 deg VERTICAL fov, so at the screen centre the world moves
     * (viewH/2)/tan(30 deg) px per radian = viewH * 0.015115 px per degree whatever the aspect ratio.
     * The old formula (5*width/256) was only right for the original 3:4 screen; on 16:9 the sky spun ~2.3x
     * faster than the road when steering.
     */
    static final float SKY_PX_PER_DEG_PER_VIEW_HEIGHT = (float) (0.5 / Math.tan(Math.toRadians(30.0)) * Math.PI / 180.0);
    static final int WALL_NEIGHBOUR_SEGMENTS = 4;
    /** Max segment distance from the previous car segment still treated as "same road" by firstSegment. */
    static final int SEGMENT_FOLLOW = 6;
    /** Max lift-off gap (units per 10 fps step, scaled by step) that is still snapped back onto the road. */
    static final float GROUND_STICK = 0.9f;
    /** ...but only while the road falls away slower than this (units per 10 fps step, scaled by step); steeper drops are real jumps. */
    static final float GROUND_STICK_DROP = 0.35f;

    int resolveCollisions(SegmentMask segmentMask) {
        int n;
        float f = 0.0f;
        int n2 = 0;
        this.pushOut.v[0] = 0.0f;
        this.pushOut.v[1] = 0.0f;
        this.pushOut.v[2] = 0.0f;
        f = 0.0f;
        for (int i = 0; i < segmentMask.flags.length; ++i) {
            if (!segmentMask.flags[i]) continue;
            {
                // Ignore walls of another road level (bridge deck above / road far below / self-crossing track).
                // levelH == -999999 means the car centre is not over this mesh's surface; that used to skip the
                // test completely, so walls of a road under an overpass (or stale cached segments) pushed the
                // car sideways and sometimes launched it. Use the mesh's own height range in that case.
                RoadSegmentMesh mesh = this.roadMeshes[i];
                float carY = this.car.position.v[1];
                float levelH = this.meshHeightAt(mesh, this.car.position, carY);
                if (levelH != -999999.0f && (levelH - carY > WALL_LEVEL_ABOVE || carY - levelH > WALL_LEVEL_BELOW)) continue;
                if (mesh.bounds.min.v[1] - carY > WALL_LEVEL_ABOVE || carY - mesh.bounds.max.v[1] > WALL_LEVEL_BELOW) continue;
                // Track parts further than WALL_NEIGHBOUR_SEGMENTS from the car (self-crossings, X junctions,
                // roads below/above) are never obstacles: walls there pushed the car sideways and trapped it.
                int carSeg = this.car.segmentIndex;
                if (carSeg >= 0 && Math.abs(mesh.segmentIndex - carSeg) > WALL_NEIGHBOUR_SEGMENTS) continue;
            }
            for (n = 0; n < this.roadMeshes[i].edgeCount; ++n) {
                int n3;
                this.edgeStart.set(this.roadMeshes[i].leftEdges[n]);
                this.edgeEnd.set(this.roadMeshes[i].rightEdges[n]);
                this.edgeDir.sub(this.edgeStart, this.edgeEnd);
                this.edgeDir.v[1] = 0.0f;
                this.edgeDir.normalize();
                this.edgeNormal.v[0] = -this.edgeDir.v[2];
                this.edgeNormal.v[1] = 0.0f;
                this.edgeNormal.v[2] = this.edgeDir.v[0];
                for (n3 = 0; n3 < 4; ++n3) {
                    this.tmpVecA.sub(this.edgeStart, this.car.bodyCorners[this.quadOrder[n3]]);
                    this.tmpVecA.v[1] = 0.0f;
                    this.edgeSides[n3] = this.tmpVecA.v[0] * this.edgeDir.v[2] - this.edgeDir.v[0] * this.tmpVecA.v[2];
                }
                this.edgeSides[4] = this.edgeSides[0];
                for (n3 = 0; n3 < 4; ++n3) {
                    float f2;
                    float f3;
                    boolean bl;
                    if (!(this.edgeSides[n3] * this.edgeSides[n3 + 1] <= 0.0f)) continue;
                    if (this.edgeSides[n3] > 0.0f) {
                        bl = false;
                        f3 = this.edgeSides[n3] / (this.edgeSides[n3] - this.edgeSides[n3 + 1]);
                        if (!(f3 <= 1.0f)) continue;
                        this.intersection.v[0] = this.car.bodyCorners[this.quadOrder[n3]].v[0] + f3 * (this.car.bodyCorners[this.quadOrder[n3 + 1]].v[0] - this.car.bodyCorners[this.quadOrder[n3]].v[0]);
                        this.intersection.v[2] = this.car.bodyCorners[this.quadOrder[n3]].v[2] + f3 * (this.car.bodyCorners[this.quadOrder[n3 + 1]].v[2] - this.car.bodyCorners[this.quadOrder[n3]].v[2]);
                        if (Math.abs(this.edgeNormal.v[2]) > Math.abs(this.edgeNormal.v[0])) {
                            if (this.intersection.v[0] >= this.edgeStart.v[0] && this.intersection.v[0] <= this.edgeEnd.v[0]) {
                                bl = true;
                            }
                            if (this.intersection.v[0] <= this.edgeStart.v[0] && this.intersection.v[0] >= this.edgeEnd.v[0]) {
                                bl = true;
                            }
                        } else {
                            if (this.intersection.v[2] >= this.edgeStart.v[2] && this.intersection.v[2] <= this.edgeEnd.v[2]) {
                                bl = true;
                            }
                            if (this.intersection.v[2] <= this.edgeStart.v[2] && this.intersection.v[2] >= this.edgeEnd.v[2]) {
                                bl = true;
                            }
                        }
                        if (!bl) continue;
                        this.tmpVecB.v[0] = this.intersection.v[0] - this.car.bodyCorners[this.quadOrder[n3]].v[0];
                        this.tmpVecB.v[1] = 0.0f;
                        this.tmpVecB.v[2] = this.intersection.v[2] - this.car.bodyCorners[this.quadOrder[n3]].v[2];
                        f2 = this.tmpVecB.dot(this.edgeNormal);
                        if (!(f2 < f)) continue;
                        this.pushOut.v[0] = f2 * this.edgeNormal.v[0];
                        this.pushOut.v[2] = f2 * this.edgeNormal.v[2];
                        f = f2;
                        n2 = 1;
                        this.lastEdgeDir.set(this.edgeDir);
                        continue;
                    }
                    bl = false;
                    f3 = this.edgeSides[n3 + 1] / (this.edgeSides[n3 + 1] - this.edgeSides[n3]);
                    if (!(f3 <= 1.0f)) continue;
                    this.intersection.v[0] = this.car.bodyCorners[this.quadOrder[n3 + 1]].v[0] + f3 * (this.car.bodyCorners[this.quadOrder[n3]].v[0] - this.car.bodyCorners[this.quadOrder[n3 + 1]].v[0]);
                    this.intersection.v[2] = this.car.bodyCorners[this.quadOrder[n3 + 1]].v[2] + f3 * (this.car.bodyCorners[this.quadOrder[n3]].v[2] - this.car.bodyCorners[this.quadOrder[n3 + 1]].v[2]);
                    if (Math.abs(this.edgeNormal.v[2]) > Math.abs(this.edgeNormal.v[0])) {
                        if (this.intersection.v[0] >= this.edgeStart.v[0] && this.intersection.v[0] <= this.edgeEnd.v[0]) {
                            bl = true;
                        }
                        if (this.intersection.v[0] <= this.edgeStart.v[0] && this.intersection.v[0] >= this.edgeEnd.v[0]) {
                            bl = true;
                        }
                    } else {
                        if (this.intersection.v[2] >= this.edgeStart.v[2] && this.intersection.v[2] <= this.edgeEnd.v[2]) {
                            bl = true;
                        }
                        if (this.intersection.v[2] <= this.edgeStart.v[2] && this.intersection.v[2] >= this.edgeEnd.v[2]) {
                            bl = true;
                        }
                    }
                    if (!bl) continue;
                    this.tmpVecB.v[0] = this.intersection.v[0] - this.car.bodyCorners[this.quadOrder[n3 + 1]].v[0];
                    this.tmpVecB.v[1] = 0.0f;
                    this.tmpVecB.v[2] = this.intersection.v[2] - this.car.bodyCorners[this.quadOrder[n3 + 1]].v[2];
                    f2 = this.tmpVecB.dot(this.edgeNormal);
                    if (!(f2 < f)) continue;
                    this.pushOut.v[0] = f2 * this.edgeNormal.v[0];
                    this.pushOut.v[2] = f2 * this.edgeNormal.v[2];
                    f = f2;
                    n2 = 1;
                    this.lastEdgeDir.set(this.edgeDir);
                }
            }
        }
        this.lastCollisionDepth = n2 > 0 ? Math.abs(f) : 0.0f;
        if (n2 > 0) {
            this.car.position.add(this.car.position, this.pushOut);
            for (n = 0; n < 4; ++n) {
                this.car.wheelPositions[n].add(this.car.wheelPositions[n], this.pushOut);
            }
        }
        return n2;
    }

    float triangleHeight(short[] sArray, int n, Vec3 vec3, int n2) {
        float f;
        float f2;
        float f3;
        float f4;
        float f5;
        float f6;
        float f7 = (short)(vec3.v[0] * 8.0f);
        float f8 = (short)(vec3.v[2] * 8.0f);
        if (n2 != 1) {
            f6 = sArray[(n + 1) * 3 + 0] - sArray[(n + 0) * 3 + 0];
            f5 = sArray[(n + 1) * 3 + 1] - sArray[(n + 0) * 3 + 1];
            f4 = sArray[(n + 1) * 3 + 2] - sArray[(n + 0) * 3 + 2];
            f3 = sArray[(n + 2) * 3 + 0] - sArray[(n + 0) * 3 + 0];
            f2 = sArray[(n + 2) * 3 + 1] - sArray[(n + 0) * 3 + 1];
            f = sArray[(n + 2) * 3 + 2] - sArray[(n + 0) * 3 + 2];
        } else {
            f6 = sArray[(n + 3) * 3 + 0] - sArray[(n + 1) * 3 + 0];
            f5 = sArray[(n + 3) * 3 + 1] - sArray[(n + 1) * 3 + 1];
            f4 = sArray[(n + 3) * 3 + 2] - sArray[(n + 1) * 3 + 2];
            f3 = sArray[(n + 2) * 3 + 0] - sArray[(n + 1) * 3 + 0];
            f2 = sArray[(n + 2) * 3 + 1] - sArray[(n + 1) * 3 + 1];
            f = sArray[(n + 2) * 3 + 2] - sArray[(n + 1) * 3 + 2];
        }
        float f9 = f5 * f - f4 * f2;
        float f10 = f4 * f3 - f6 * f;
        float f11 = f6 * f2 - f5 * f3;
        float f12 = -(f9 * (float)sArray[(n + 1) * 3] + f10 * (float)sArray[(n + 1) * 3 + 1] + f11 * (float)sArray[(n + 1) * 3 + 2]);
        return -(f9 * f7 + f11 * f8 + f12) / f10;
    }

    int pointInQuad(short[] sArray, int n, Vec3 vec3) {
        short s = (short)(vec3.v[0] * 8.0f);
        int n2 = s - sArray[(n + 0) * 3];
        int n3 = sArray[(n + 1) * 3 + 2] - sArray[(n + 0) * 3 + 2];
        short s2 = (short)(vec3.v[2] * 8.0f);
        int n4 = s2 - sArray[(n + 0) * 3 + 2];
        int n5 = sArray[(n + 1) * 3] - sArray[(n + 0) * 3];
        int n6 = n2 * n3 - n4 * n5;
        if (n6 < 0) {
            return 0;
        }
        n2 = s - sArray[(n + 1) * 3];
        n3 = sArray[(n + 3) * 3 + 2] - sArray[(n + 1) * 3 + 2];
        n4 = s2 - sArray[(n + 1) * 3 + 2];
        n5 = sArray[(n + 3) * 3] - sArray[(n + 1) * 3];
        n6 = n2 * n3 - n4 * n5;
        if (n6 < 0) {
            return 0;
        }
        n2 = s - sArray[(n + 3) * 3];
        n3 = sArray[(n + 2) * 3 + 2] - sArray[(n + 3) * 3 + 2];
        n4 = s2 - sArray[(n + 3) * 3 + 2];
        n5 = sArray[(n + 2) * 3] - sArray[(n + 3) * 3];
        n6 = n2 * n3 - n4 * n5;
        if (n6 < 0) {
            return 0;
        }
        n2 = s - sArray[(n + 2) * 3];
        n3 = sArray[(n + 0) * 3 + 2] - sArray[(n + 2) * 3 + 2];
        n4 = s2 - sArray[(n + 2) * 3 + 2];
        n5 = sArray[(n + 0) * 3] - sArray[(n + 2) * 3];
        n6 = n2 * n3 - n4 * n5;
        if (n6 < 0) {
            return 0;
        }
        n2 = s - sArray[(n + 1) * 3];
        n3 = sArray[(n + 2) * 3 + 2] - sArray[(n + 1) * 3 + 2];
        n4 = s2 - sArray[(n + 1) * 3 + 2];
        n5 = sArray[(n + 2) * 3] - sArray[(n + 1) * 3];
        n6 = n2 * n3 - n4 * n5;
        if (n6 < 0) {
            return 1;
        }
        return 2;
    }

    float groundHeight(SegmentMask segmentMask, Vec3 vec3) {
        return this.groundHeight(segmentMask, vec3, Float.NaN);
    }

    // Overpass-safe surface lookup: when several road levels overlap in XZ
    // (track crosses itself on a bridge) pick the surface nearest to refY
    // instead of the first quad in slot order, which dropped the car under the map.
    float groundHeight(SegmentMask segmentMask, Vec3 vec3, float refY) {
        boolean useRef = !Float.isNaN(refY);
        float best = -999999.0f;
        float bestScore = Float.MAX_VALUE;
        int bestQuad = 0;
        int bestStrip = 0;
        for (int i = 0; i < segmentMask.flags.length; ++i) {
            if (!segmentMask.flags[i]) continue;
            for (int j = 0; j < 7; ++j) {
                if (this.roadMeshes[i].laneSolid[j] == 0) continue;
                short[] sArray = this.roadMeshes[i].vertexData;
                for (int k = 0; k < RoadSegmentMesh.sections; ++k) {
                    int n = k * 2 + j * (RoadSegmentMesh.sections + 1) * 2;
                    int n2 = this.pointInQuad(sArray, n, vec3);
                    if (n2 == 0) continue;
                    float f = this.triangleHeight(sArray, n, vec3, n2) / 8.0f;
                    if (!useRef) {
                        this.hitQuad = k;
                        this.hitStrip = j;
                        return f;
                    }
                    float score = surfaceScore(f, refY);
                    if (score < bestScore) {
                        bestScore = score;
                        best = f;
                        bestQuad = k;
                        bestStrip = j;
                    }
                }
            }
        }
        if (best != -999999.0f) {
            this.hitQuad = bestQuad;
            this.hitStrip = bestStrip;
        }
        return best;
    }

    static final float SURFACE_TOL = 3.0f;
    /** Max upward wheel velocity (world units per 10-fps step) inherited from ground delta. Lower = fewer accidental jumps. */
    static final float MAX_GROUND_LAUNCH = 0.45f;

    // lower = better: highest surface not above refY (+tol), else lowest surface above it
    static float surfaceScore(float h, float refY) {
        float d = h - refY;
        return d <= SURFACE_TOL ? SURFACE_TOL - d : 1.0e6f + d;
    }

    float meshHeightAt(RoadSegmentMesh mesh, Vec3 vec3, float refY) {
        float best = -999999.0f;
        float bestScore = Float.MAX_VALUE;
        short[] sArray = mesh.vertexData;
        for (int j = 0; j < 7; ++j) {
            if (mesh.laneSolid[j] == 0) continue;
            for (int k = 0; k < RoadSegmentMesh.sections; ++k) {
                int n = k * 2 + j * (RoadSegmentMesh.sections + 1) * 2;
                int n2 = this.pointInQuad(sArray, n, vec3);
                if (n2 == 0) continue;
                float f = this.triangleHeight(sArray, n, vec3, n2) / 8.0f;
                float score = surfaceScore(f, refY);
                if (score < bestScore) {
                    bestScore = score;
                    best = f;
                }
            }
        }
        return best;
    }

    private long lastRespawnLogAt;
    /** Multiplayer diagnostics: every respawn of MY car is logged (rate limited) so a \"everybody gets respawned\" report can be matched with the other player's log. */
    void logRespawn(int segment) { /*T*/ final long $t = rally3d.core.Trace.in(333); try {
        long now = System.nanoTime();
        if (now - this.lastRespawnLogAt < 300_000_000L) return;
        this.lastRespawnLogAt = now;
        rally3d.core.RLog.i("mp", "respawn: MY car -> segment " + segment + " (wheelsNoSurface=" + this.wheelsNoSurface + ", own segment " + this.car.segmentIndex + ", raceTime " + this.race.raceTime + ")");
     } finally { rally3d.core.Trace.out(333, $t); } }

    // ================= multiplayer pause =================
    // Pause in a multiplayer race does NOT leave the session any more: it freezes the race for everybody (packet type 8, the host relays it to
    // all guests), shows who paused, and after the last pauser resumes a 3 s countdown runs before the race continues.
    // Leaving: hold BRAKE for 2 s while paused (PC: S / Down, Android: the BRAKE pedal).
    static final long MP_RESUME_NANOS = 3_000_000_000L, MP_LEAVE_NANOS = 2_000_000_000L;
    boolean mpLocalPaused;
    boolean mpClockFrozen;
    long mpResumeAt;
    long mpLeaveSince;
    int mpPauseKeysPrev;
    long mpSpectateLeaveArmed;
    /** Players that are paused right now: origin id (0 = host) -> name. */
    final java.util.LinkedHashMap<Integer, String> mpPausers = new java.util.LinkedHashMap<Integer, String>();

    void mpResetPause() { /*T*/ final long $t = rally3d.core.Trace.in(334); try {
        this.mpLocalPaused = false;
        this.mpClockFrozen = false;
        this.mpResumeAt = 0L;
        this.mpLeaveSince = 0L;
        this.mpPauseKeysPrev = 0;
        this.mpSpectateLeaveArmed = 0L;
        this.mpPausers.clear();
        this.bannerText2 = null;
        rally3d.desktop.Platform.mpPauseRequest = false;
     } finally { rally3d.core.Trace.out(334, $t); } }

    /** Sends my pause / resume state (type 8: state, origin id (the host fills it in for guests), name length, name UTF-16BE). */
    void mpSendPause(boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(335); try {
        if (this.bluetooth == null) return;
        byte[] nm;
        try { String n = this.saveData.playerName == null ? "" : this.saveData.playerName; if (n.length() > 16) n = n.substring(0, 16); nm = n.getBytes("UTF-16BE"); }
        catch (java.io.UnsupportedEncodingException e) { rally3d.core.RLog.caught("RallyGame.mpSendPause", e); nm = new byte[0]; }
        byte[] pk = new byte[2 + 3 + nm.length];
        pk[0] = 8; pk[1] = (byte)(3 + nm.length); pk[2] = (byte)(on ? 1 : 0); pk[3] = 0; pk[4] = (byte)nm.length;
        System.arraycopy(nm, 0, pk, 5, nm.length);
        this.bluetooth.sendPacket(pk, pk.length);
        rally3d.core.RLog.i("mp", "local player " + (on ? "PAUSED" : "resumed") + " the race");
     } finally { rally3d.core.Trace.out(335, $t); } }

    long mpPauseSentAt;
    /** Re-sends "I am paused" without logging (idempotent on the receivers). */
    void mpSendPauseQuiet() { /*T*/ final long $t = rally3d.core.Trace.in(341); try {
        try {
        if (this.bluetooth == null) return;
        byte[] nm;
        try { String n = this.saveData.playerName == null ? "" : this.saveData.playerName; if (n.length() > 16) n = n.substring(0, 16); nm = n.getBytes("UTF-16BE"); }
        catch (java.io.UnsupportedEncodingException e) { rally3d.core.RLog.caught("RallyGame.mpSendPauseQuiet", e); nm = new byte[0]; }
        byte[] pk = new byte[2 + 3 + nm.length];
        pk[0] = 8; pk[1] = (byte)(3 + nm.length); pk[2] = 1; pk[3] = 0; pk[4] = (byte)nm.length;
        System.arraycopy(nm, 0, pk, 5, nm.length);
        this.bluetooth.sendPacket(pk, pk.length);
        } finally { }
     } finally { rally3d.core.Trace.out(341, $t); } }

    /** Host only: a guest that was paused disappeared - release the others (the guest cannot resume itself any more). */
    void mpPeerGone(int id) { /*T*/ final long $t = rally3d.core.Trace.in(336); try {
        String name = this.mpPausers.remove(Integer.valueOf(id));
        if (name == null || !this.isHost) return;
        byte[] pk = new byte[]{8, 3, 0, (byte)id, 0};
        this.bluetooth.sendPacket(pk, pk.length);
        rally3d.core.RLog.i("mp", "paused player " + id + " (" + name + ") is gone: released");
     } finally { rally3d.core.Trace.out(336, $t); } }

    /** Type 8 from the network (called from updateOpponent). */
    void mpOnPausePacket(byte[] p) { /*T*/ final long $t = rally3d.core.Trace.in(337); try {
        int origin = p[3] & 0xFF;
        boolean on = p[2] != 0;
        if (!this.isHost && origin != 0 && origin == this.bluetooth.assignedPlayerId()) return;   // my own state echoed back
        String name = "Player";
        try {
            int nl = p[4] & 0xFF;
            if (nl > 0 && 5 + nl <= p.length && 5 + nl <= (p[1] & 0xFF) + 2) name = new String(p, 5, nl, "UTF-16BE").trim();
        } catch (java.io.UnsupportedEncodingException e) { rally3d.core.RLog.caught("RallyGame.mpOnPausePacket", e); }
        if (name.length() == 0) name = "Player";
        if (on) { this.mpPausers.put(Integer.valueOf(origin), name); this.mpResumeAt = 0L; }
        else this.mpPausers.remove(Integer.valueOf(origin));
        rally3d.core.RLog.i("mp", "player " + origin + " (" + name + ") " + (on ? "PAUSED" : "resumed") + ", paused now: " + this.mpPausers.size() + (this.mpLocalPaused ? " + me" : ""));
     } finally { rally3d.core.Trace.out(337, $t); } }

    private String mpPausedNames() { /*T*/ final long $t = rally3d.core.Trace.in(338); try {
        StringBuilder sb = new StringBuilder();
        for (String n : this.mpPausers.values()) { if (sb.length() > 0) sb.append(", "); sb.append(n); }
        return sb.toString();
     } finally { rally3d.core.Trace.out(338, $t); } }

    /** Banner for a spectator (finished racer): only shows that somebody paused. */
    void mpSpectateBanner() { /*T*/ final long $t = rally3d.core.Trace.in(339); try {
        if (this.mpPausers.isEmpty()) { this.bannerText2 = null; return; }
        this.bannerText = rally3d.core.Lang.t("GAME PAUSED");
        this.bannerText2 = this.mpPausedNames() + rally3d.core.Lang.t(" paused the game. Waiting for players...");
     } finally { rally3d.core.Trace.out(339, $t); } }

    /**
     * One step of the multiplayer pause state machine (called at the start of updateRace in a multiplayer race).
     * Returns -2 = not paused, simulate normally; 0 = frozen (the loop renders and calls again); -1 = leave the race.
     */
    int mpPauseStep(int keys) { /*T*/ final long $t = rally3d.core.Trace.in(340); try {
        long now = System.nanoTime();
        boolean pauseDown = (keys & 0x200) != 0;
        boolean pressed = pauseDown && (this.mpPauseKeysPrev & 0x200) == 0;
        this.mpPauseKeysPrev = keys;
        boolean request = rally3d.desktop.Platform.mpPauseRequest;   // Android: app went to the background
        rally3d.desktop.Platform.mpPauseRequest = false;
        if ((request || !this.isShown()) && !this.mpLocalPaused) pressed = true;   // lost focus / background = the same as pressing pause
        if (pressed) {
            this.mpLocalPaused = !this.mpLocalPaused;
            this.mpLeaveSince = 0L;
            this.mpSendPause(this.mpLocalPaused);
            if (this.mpLocalPaused) this.mpResumeAt = 0L;
        }
        if (this.mpLocalPaused && now - this.mpPauseSentAt > 1_000_000_000L) { this.mpPauseSentAt = now; this.mpSendPauseQuiet(); }
        boolean paused = this.mpLocalPaused || !this.mpPausers.isEmpty();
        if (!paused && !this.mpClockFrozen) { this.bannerText2 = null; return -2; }
        // ---- frozen (paused or counting down to the restart) ----
        if (!this.mpClockFrozen) {
            this.mpClockFrozen = true;
            rally3d.core.RLog.i("mp", "race frozen at " + this.race.raceTime);
        }
        this.car.velocity.v[0] = 0.0f; this.car.velocity.v[1] = 0.0f; this.car.velocity.v[2] = 0.0f;   // the other player must not extrapolate my car
        if (!this.updateOpponent(0.0f)) return -1;   // keeps state/ping packets flowing (no timeout) and applies pause packets
        paused = this.mpLocalPaused || !this.mpPausers.isEmpty();
        if (paused) {
            this.mpResumeAt = 0L;
            if (this.mpLocalPaused && (keys & 0x40) != 0) {
                if (this.mpLeaveSince == 0L) this.mpLeaveSince = now;
                if (now - this.mpLeaveSince >= MP_LEAVE_NANOS) { rally3d.core.RLog.i("mp", "local player left the race from the pause screen"); return -1; }
            } else this.mpLeaveSince = 0L;
            this.bannerText = this.mpLocalPaused ? rally3d.core.Lang.t("PAUSED") : rally3d.core.Lang.t("GAME PAUSED");
            if (this.mpLocalPaused && this.mpLeaveSince != 0L) this.bannerText2 = rally3d.core.Lang.t("Leaving...");
            else if (!this.mpPausers.isEmpty()) this.bannerText2 = (this.mpLocalPaused ? rally3d.core.Lang.t("You and ") : "") + this.mpPausedNames() + rally3d.core.Lang.t(" paused the game. Waiting for players...");
            else this.bannerText2 = rally3d.core.Lang.t("Pause again - resume | hold BRAKE 2 s - leave");
            return 0;
        }
        // everybody is back: 3 s countdown, then the race goes on
        if (this.mpResumeAt == 0L) { this.mpResumeAt = now + MP_RESUME_NANOS; rally3d.core.RLog.i("mp", "all players back, resuming in 3 s"); }
        long left = this.mpResumeAt - now;
        if (left > 0L) {
            this.bannerText = rally3d.core.Lang.t("Resuming in ") + (int)((left + 999_999_999L) / 1_000_000_000L);
            this.bannerText2 = null;
            return 0;
        }
        this.startTimeOffset = (int)(this.currentTime() / 100L) - this.race.raceTime;   // the clock did not run while paused
        this.mpClockFrozen = false;
        this.mpResumeAt = 0L;
        this.bannerText = null;
        this.bannerText2 = null;
        this.lastOpponentPacketAt = System.nanoTime();
        rally3d.core.RLog.i("mp", "race resumed at " + this.race.raceTime);
        return -2;
     } finally { rally3d.core.Trace.out(340, $t); } }

    int lastGoodSegment = 5;
    void resetCar(int n) { /*T*/ final long $t = rally3d.core.Trace.in(47); try {
        int maxSeg = this.segmentCount - 2;
        if (n < 0 || n > maxSeg) {
            rally3d.core.RLog.w("mp", "resetCar: segment " + n + " out of range 0.." + maxSeg + ", using last good segment " + this.lastGoodSegment);
            n = Math.max(0, Math.min(n < 0 ? this.lastGoodSegment : maxSeg, maxSeg));
        }
        if (this.race != null && this.race.mode == 2) this.logRespawn(n);
        this.resetWheelSpin();
        this.buildRoadWindow(n);
        this.car.position.set(this.segments[n].startPos);
        this.car.rotation.v[1] = 180.0f - (this.segments[n].accumYaw + this.segments[n + 1].accumYaw) / 2.0f;
        this.computeCorners(this.car.wheelPositions, this.car.bodyCorners, this.car.position, this.car.rotation);
        this.car.velocity.v[0] = 0.0f;
        this.car.velocity.v[1] = 0.0f;
        this.car.velocity.v[2] = 0.0f;
        this.car.speed = 0.0f;
        for (int i = 0; i < 4; ++i) {
            this.findSegments(this.carMask, this.car.wheelPositions[i]);
            float f = this.groundHeight(this.carMask, this.car.wheelPositions[i], this.car.position.v[1]);
            if (f != -999999.0f) {
                this.car.wheelY[i] = f;
                this.car.prevGroundY[i] = f;
            }
            this.car.wheelVelY[i] = 0.0f;
        }
     } finally { rally3d.core.Trace.out(47, $t); } }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    int updateRace(boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(48); try {
        float f;
        float f2;
        float f3;
        int n;
        int n2 = this.readKeys();
        if (this.spectating) return this.updateSpectate(bl, n2);
        if (this.race.mode == 2 && bl) {
            int pauseResult = this.mpPauseStep(n2);
            if (pauseResult != -2) return pauseResult;
        }
        float padSteer = rally3d.desktop.Platform.controllerSteer();
        float padThrottle = rally3d.desktop.Platform.controllerThrottle();
        float padBrake = rally3d.desktop.Platform.controllerBrake();
        int n3 = 4;
        int n4 = 32;
        int n5 = 2;
        int n6 = 64;
        CarDef myCar = this.carDef(this.race.carIndex);   // every mode (also multiplayer): the chosen car's own top speed and handling
        float f4 = (float)myCar.speed / 10.0f;
        if (!this.raceFinished) {
            this.race.raceTime = (int)(this.currentTime() / 100L) - this.startTimeOffset;
        }
        if ((n = (int)this.fps) < 2) {
            n = 2;
        }
        float f5 = 10.0f / (float)n;
        this.car.velocity.v[0] = 0.0f;
        this.car.velocity.v[1] = 0.0f;
        this.car.velocity.v[2] = 0.0f;
        {
            if ((n2 & n3) != 0 && (double)this.car.speed > 0.2 || padSteer < -0.12f && (double)this.car.speed > 0.2) {
                float steering = padSteer < -0.12f ? Math.max(-1.0f, padSteer) : -1.0f;
                this.car.steer += 1.5f * myCar.steer * f5 * steering;
                if (this.car.steer < -6.0f * myCar.steerMax) {
                    this.car.steer = -6.0f * myCar.steerMax;
                }
                this.car.steerLean = -45.0f;
            } else if ((n2 & n4) != 0 && (double)this.car.speed > 0.2 || padSteer > 0.12f && (double)this.car.speed > 0.2) {
                float steering = padSteer > 0.12f ? Math.min(1.0f, padSteer) : 1.0f;
                this.car.steer += 1.5f * myCar.steer * f5 * steering;
                if (this.car.steer > 6.0f * myCar.steerMax) {
                    this.car.steer = 6.0f * myCar.steerMax;
                }
                this.car.steerLean = -45.0f;
            } else {
                if (this.car.steer > 0.0f) {
                    this.car.steer -= 2.5f * f5;
                    if (this.car.steer < 0.0f) {
                        this.car.steer = 0.0f;
                    }
                } else {
                    this.car.steer += 2.5f * f5;
                    if (this.car.steer > 0.0f) {
                        this.car.steer = 0.0f;
                    }
                }
                this.car.steerLean = 0.0f;
            }
        }
        this.car.rotation.v[1] = this.car.rotation.v[1] + this.car.steer * f5;
        if (this.wheelsAirborne < 4) {
            long l;
            if ((n2 & n6) != 0 || padBrake > 0.08f || this.raceFinished) {
                if (this.car.speed > -0.0f) {
                    float brake = padBrake > 0.08f ? Math.max(0.08f, padBrake) : 1.0f;
                    this.car.speed = (float)((double)this.car.speed - 0.3 * (double)brake * (double)f5);
                    if (this.car.speed < 0.0f) {
                        this.car.speed = 0.0f;
                    }
                }
            } else if ((rally3d.desktop.Platform.controllerThrottleActive() ? padThrottle > 0.03f || (n2 & (n5 | 0x100)) != 0 : this.saveData.autoAccelOff == 0 || (n2 & (n5 | 0x100)) != 0) && this.car.speed < f4) {
                float throttle = rally3d.desktop.Platform.controllerThrottleActive() && padThrottle > 0.03f ? padThrottle : 1.0f;
                this.car.speed = (float)((double)this.car.speed + 0.25 * (double)myCar.accel * (double)(f4 - this.car.speed) / 10.0 * (double)throttle * (double)f5);
                if (this.car.speed > f4) {
                    this.car.speed = f4;
                }
            }
            if (this.wheelsOffRoad > 1 && this.car.speed > 1.0f) {
                this.car.speed = (float)((double)this.car.speed - 0.14 * (double)f5 / (double)myCar.grip);
                if (this.car.speed < 1.0f) {
                    this.car.speed = 1.0f;
                }
            }
            if (this.wheelsOffRoad > 0 && this.lastVibrateTime < (l = System.currentTimeMillis()) - 500L) {
                this.lastVibrateTime = l;
                if (this.saveData.vibrationOff == 0 && rally3d.desktop.Platform.supportsVibration()) {
                    // soft rumble on gravel / grass, a bit stronger the faster you go
                    this.vibrate(35, 24 + (int)(36.0f * Math.min(1.0f, this.car.speed / Math.max(0.1f, f4))));
                }
            }
        }
        if (this.car.speed > f4) this.car.speed = f4;   // hard cap: top speed of the chosen car, whatever happened above
        this.car.velocity.v[0] = (float)((double)this.car.velocity.v[0] + Math.sin((double)this.car.rotation.v[1] * Math.PI / 180.0) * (double)this.car.speed);
        this.car.velocity.v[2] = (float)((double)this.car.velocity.v[2] - Math.cos((double)this.car.rotation.v[1] * Math.PI / 180.0) * (double)this.car.speed);
        this.car.position.v[0] = this.car.position.v[0] + this.car.velocity.v[0] * f5;
        this.car.position.v[2] = this.car.position.v[2] + this.car.velocity.v[2] * f5;
        if (this.ghostOn() && this.ghostRecorder != null && !this.raceFinished) {
            this.ghostRecorder.recordPose(this.ghostMs(), this.car, false);
        }
        this.computeCorners(this.car.wheelPositions, this.car.bodyCorners, this.car.position, this.car.rotation);
        this.findSegments(this.carMask, this.car.bodyCorners[0]);
        this.addSegments(this.carMask, this.car.bodyCorners[1]);
        this.addSegments(this.carMask, this.car.bodyCorners[2]);
        this.addSegments(this.carMask, this.car.bodyCorners[3]);
        this.car.segmentIndex = this.firstSegment(this.carMask, this.car.position, this.car.position.v[1], this.car.segmentIndex);
        int wallHit = this.resolveCollisions(this.carMask);
        if (wallHit > 0 && this.saveData.vibrationOff == 0 && rally3d.desktop.Platform.supportsVibration()) {
            this.impactVibrate(this.lastCollisionDepth / Math.max(0.05f, f5), f4);
        }
        this.updateWheels(f5);
        if (!this.raceFinished) {
            this.updateCamera(8.0f);
        }
        this.findSegments(this.cameraMask, this.cameraState.position);
        int n8 = this.firstSegment(this.cameraMask, this.cameraState.position, this.car.position.v[1]);
        if (n8 >= 0) {
            this.cameraState.segmentIndex = n8;
            this.buildRoadWindow(this.cameraState.segmentIndex);
        }
        this.forward = (f3 = (f2 = (float)Math.cos(Math.toRadians(this.cameraState.yaw - 90.0f))) * this.segments[this.cameraState.segmentIndex].startDir.v[0] + (f = (float)Math.sin(Math.toRadians(this.cameraState.yaw - 90.0f))) * this.segments[this.cameraState.segmentIndex].startDir.v[2]) > 0.0f;
        float carForwardX = (float)Math.sin(Math.toRadians(this.car.rotation.v[1]));
        float carForwardZ = -(float)Math.cos(Math.toRadians(this.car.rotation.v[1]));
        boolean drivingForward = true;
        if (this.car.segmentIndex >= 0 && this.car.segmentIndex < this.segmentCount) {
            drivingForward = carForwardX * this.segments[this.car.segmentIndex].startDir.v[0]
                    + carForwardZ * this.segments[this.car.segmentIndex].startDir.v[2] > 0.0f;
        }
        if (this.raceFinished) {
            if (this.race.mode != 2 || this.opponentFinishTime >= 0) {
                if (this.finishDelay <= 0) return 1;
                --this.finishDelay;
            } else if (this.opponentPresent) {
                // finished first: as soon as the car has rolled to a stop, start watching the racers still on the road
                ++this.spectateWait;
                if (this.car.speed <= 0.3f || (long)this.spectateWait > this.fps * 6L) {
                    this.beginSpectate();
                    return 0;   // the rest of this step would act on the (now far away) own car
                }
            }
        } else if (this.car.segmentIndex >= 0 && this.checkpoints[this.race.checkpointsPassed] == this.car.segmentIndex) {
            this.saveData.savedFps = (int)this.fps;
            if (this.targetTime < 0) {
                this.splitText = this.formatTime(this.race.raceTime);
                this.splitColor = -86;
            } else {
                int n9 = this.race.raceTime - this.targetTime * (this.race.checkpointsPassed + 1) / 4;
                if (n9 > 0) {
                    this.splitText = "+" + this.formatTime(n9);
                    this.splitColor = 0xFF0000;
                } else {
                    this.splitText = "-" + this.formatTime(-n9);
                    this.splitColor = 65280;
                }
            }
            this.splitTimer = (int)(this.fps * 2L);
            ++this.race.checkpointsPassed;
            if (this.race.checkpointsPassed >= 4) {
                this.sound.play(4);
                if (this.ghostOn() && this.ghostRecorder != null) {
                    this.ghostFinishMs = this.ghostMs();
                    this.ghostRecorder.recordPose(this.ghostFinishMs, this.car, true);
                }
                this.raceFinished = true;
                this.finishDelay = (int)(this.fps * 2L / 3L);
                if (this.race.mode == 2) {
                    this.netBuffer[0] = 5;
                    this.netBuffer[1] = 4;
                    this.writeInt(this.race.raceTime, this.netBuffer, 2);
                    this.bluetooth.sendPacket(this.netBuffer, 6);
                }
            } else {
                this.sound.play(0);
            }
        }
        if (this.race.raceTime > 30) {
            this.bannerText = !drivingForward ? Texts.wrongWay : null;
        }
        if (this.race.raceTime >= 3000) {
            this.bannerText = Texts.timeIsUp;
            for (long e = this.currentTime() + 1000L; this.currentTime() < e; ) this.renderFrame();
            return 2;
        }
        boolean segOk = this.car.segmentIndex >= 0 && this.car.segmentIndex < this.segmentCount;
        if (this.wheelsNoSurface == 0 && this.car.segmentIndex >= 4 && this.car.segmentIndex < this.segmentCount - 1) this.lastGoodSegment = this.car.segmentIndex;
        if (this.wheelsNoSurface > 1) {
            int resetSegment = segOk ? this.car.segmentIndex : this.lastGoodSegment;   // off the map: back to the last place that had a road
            this.resetCar(resetSegment);
            segOk = false;
        }
        if (segOk && this.rearWheelsOffRoad >= 2 && (this.offRoadSide == -1 && this.segments[this.car.segmentIndex].profileEnd == 7 || this.offRoadSide == 1 && this.segments[this.car.segmentIndex].profileStart == 7)) {
            this.resetCar(this.car.segmentIndex);
        }
        if (this.car.segmentIndex >= 0 && this.car.segmentIndex < 4) {
            this.resetCar(5);
        }
        if (this.race.mode == 2 && !this.updateOpponent(f5)) {
            return -1;
        }
        if (!this.raceFinished && this.race.mode >= 0 && this.race.mode <= 1) {
            this.saveData.savedCars[this.race.mode].copyFrom(this.car);
            this.saveData.savedCameras[this.race.mode].copyFrom(this.cameraState);
            this.saveData.savedRaces[this.race.mode].copyFrom(this.race);
        }
        if (!bl) return 0;
        if (this.race.mode == 2) return 0;   // multiplayer: pause is handled by mpPauseStep and never leaves the session
        if (!this.isShown()) {
            this.paused = true;
        }
        if ((n2 & 0x200) == 0 && !this.paused) return 0;
        return -1;
     } finally { rally3d.core.Trace.out(48, $t); } }

    void writeInt(int n, byte[] byArray, int n2) {
        byArray[n2 + 0] = (byte)(n >> 0 & 0xFF);
        byArray[n2 + 1] = (byte)(n >> 8 & 0xFF);
        byArray[n2 + 2] = (byte)(n >> 16 & 0xFF);
        byArray[n2 + 3] = (byte)(n >> 24 & 0xFF);
    }

    int readInt(byte[] byArray, int n) {
        return ((byArray[n + 0] + 256) % 256 << 0) + ((byArray[n + 1] + 256) % 256 << 8) + ((byArray[n + 2] + 256) % 256 << 16) + ((byArray[n + 3] + 256) % 256 << 24);
    }

    void writeFixed(float f, byte[] byArray, int n) {
        int n2 = (int)(f * 16384.0f);
        byArray[n + 0] = (byte)(n2 >> 0 & 0xFF);
        byArray[n + 1] = (byte)(n2 >> 8 & 0xFF);
        byArray[n + 2] = (byte)(n2 >> 16 & 0xFF);
        byArray[n + 3] = (byte)(n2 >> 24 & 0xFF);
    }

    float readFixed(byte[] byArray, int n) {
        int n2 = ((byArray[n + 0] + 256) % 256 << 0) + ((byArray[n + 1] + 256) % 256 << 8) + ((byArray[n + 2] + 256) % 256 << 16) + ((byArray[n + 3] + 256) % 256 << 24);
        return (float)n2 / 16384.0f;
    }

    long lastOpponentPacketAt;
    boolean updateOpponent(float f) { /*T*/ final long $t = rally3d.core.Trace.in(49); try {
        long l = this.currentTime();
        if (l > this.nextSyncTime) {
            this.netBuffer[0] = 4;
                this.netBuffer[1] = 26;
            this.writeFixed(this.car.position.v[0], this.netBuffer, 2);
            this.writeFixed(this.car.position.v[2], this.netBuffer, 6);
            this.writeFixed(this.car.rotation.v[1], this.netBuffer, 10);
                this.writeFixed(this.car.velocity.v[0], this.netBuffer, 14);
                this.writeFixed(this.car.velocity.v[2], this.netBuffer, 18);
                this.writeInt(this.race.raceTime, this.netBuffer, 22);
            int reportedPing = this.multiplayerPingMs < 0 ? 0xFFFF : Math.min(this.multiplayerPingMs, 0xFFFE);
            this.netBuffer[26] = (byte)(reportedPing & 0xFF);
            this.netBuffer[27] = (byte)(reportedPing >> 8 & 0xFF);
            this.netBuffer[1] = 26;
            this.bluetooth.sendPacket(this.netBuffer, 28);
            this.nextSyncTime = l + 50L;   // 20 state packets per second: smooth for the watching racers too
        }
        if (l > this.nextMultiplayerPingAt) {
            this.netBuffer[0] = 17;
            this.netBuffer[1] = 4;
            ++this.multiplayerPingSequence;
            this.writeInt(this.multiplayerPingSequence, this.netBuffer, 2);
            this.multiplayerPingSentAt = System.nanoTime();
            this.bluetooth.sendPacket(this.netBuffer, 6);
            this.nextMultiplayerPingAt = l + 500L;
        }
        if (this.multiplayerPingSentAt != 0L) {   // pong is timestamped on the network thread: real round-trip time
            long arrived = this.bluetooth.pongArrival(this.isHost ? Math.max(0, this.opponentSender) : 0, this.multiplayerPingSequence);
            if (arrived > 0L) {
                this.multiplayerPingMs = (int)Math.max(0L, (arrived - this.multiplayerPingSentAt) / 1_000_000L);
                this.multiplayerPingSentAt = 0L;
            }
        }
        for (int pk = 0; pk < 96 && this.bluetooth.receivePacket(this.netBuffer); ++pk) {
            this.lastOpponentPacketAt = System.nanoTime();
            switch (this.netBuffer[0]) {
                case 9: {   // relayed state of another guest (guests only)
                    if ((this.netBuffer[1] & 0xFF) >= 21) this.applyExtraState(this.netBuffer[2] & 0xFF, this.netBuffer, 3);
                    break;
                }
                case 11: {  // relayed player info of another guest
                    this.applyRelayedInfo(this.netBuffer);
                    break;
                }
                case 6: {   // late / repeated player info: only matters for extra players (the primary opponent is set up before the race)
                    if (this.isHost && this.opponentSender > 0 && this.bluetooth.lastSenderId != this.opponentSender) this.applyPlayerInfo(this.netBuffer, this.bluetooth.lastSenderId);
                    break;
                }
                case 4: {
                    if (this.isHost && this.opponentSender > 0 && this.bluetooth.lastSenderId != this.opponentSender) {
                        if ((this.netBuffer[1] & 0xFF) >= 20) this.applyExtraState(this.bluetooth.lastSenderId, this.netBuffer, 2);
                        break;
                    }
                    if ((this.netBuffer[1] & 0xFF) < 20) break;   // truncated / hostile state packet
                    float f2 = this.readFixed(this.netBuffer, 2);
                    float f3 = this.readFixed(this.netBuffer, 6);
                    {   // diagnostics: a big jump of the other car = it respawned (or fell); logged so reports about respawns can be traced
                        float jx = f2 - this.opponentPosition.v[0], jz = f3 - this.opponentPosition.v[2];
                        if (jx * jx + jz * jz > 900.0f && this.opponentCorrFrames <= 0) rally3d.core.RLog.i("mp", "opponent car jumped " + (int)Math.sqrt(jx * jx + jz * jz) + " units (it respawned?)");
                    }
                    this.opponentRotation.v[1] = this.readFixed(this.netBuffer, 10);
                    this.opponentVelocity.v[0] = this.readFixed(this.netBuffer, 14);
                    this.opponentVelocity.v[2] = this.readFixed(this.netBuffer, 18);
                    {   // never trust the peer: its speed cannot exceed the top speed of its car (+2 % tolerance)
                        float maxRemote = (float)this.carDef(this.opponentCar).speed / 10.0f * 1.02f;
                        float remoteSpeed = (float)Math.sqrt(this.opponentVelocity.v[0] * this.opponentVelocity.v[0] + this.opponentVelocity.v[2] * this.opponentVelocity.v[2]);
                        if (remoteSpeed > maxRemote && remoteSpeed > 0.0f) { float k = maxRemote / remoteSpeed; this.opponentVelocity.v[0] *= k; this.opponentVelocity.v[2] *= k; }
                    }
                    if ((this.netBuffer[1] & 0xFF) >= 24) this.opponentRaceTime = this.readInt(this.netBuffer, 22);
                    if ((this.netBuffer[1] & 0xFF) >= 26) {
                        int reported = (this.netBuffer[26] & 0xFF) | (this.netBuffer[27] & 0xFF) << 8;
                        this.opponentPingMs = reported == 0xFFFF ? -1 : reported;
                    }
                    {   // the packet is already about half a round trip old: move the car on by that much
                        float lead = Math.min(Math.max(this.multiplayerPingMs, 0), 200) / 200.0f;
                        f2 += this.opponentVelocity.v[0] * lead;
                        f3 += this.opponentVelocity.v[2] * lead;
                    }
                    {
                        float sx = f2 - this.opponentPosition.v[0], sz = f3 - this.opponentPosition.v[2];
                        if (sx * sx + sz * sz > 900.0f) { this.opponentPosition.v[0] = f2; this.opponentPosition.v[2] = f3; }   // respawn: teleport, do not slide
                    }
                    this.opponentCorrFrames = 4;
                    this.opponentCorrection.v[0] = (f2 - this.opponentPosition.v[0]) / (float)this.opponentCorrFrames;
                    this.opponentCorrection.v[2] = (f3 - this.opponentPosition.v[2]) / (float)this.opponentCorrFrames;
                    this.opponentExtrapFrames = (int)this.fps;
                    break;
                }
                case 12: {  // relayed finish of another guest (guests only): [12, len, origin, int time]
                    RemoteCar fx = this.extraFor(this.netBuffer[2] & 0xFF);
                    if (fx != null && (this.netBuffer[1] & 0xFF) >= 5) { fx.finishTime = this.readInt(this.netBuffer, 3); rally3d.core.RLog.i("mp", "extra player " + fx.id + " finished: " + fx.finishTime); }
                    break;
                }
                case 5: {
                    if (this.isHost && this.opponentSender > 0 && this.bluetooth.lastSenderId != this.opponentSender) {
                        RemoteCar fx = this.extraFor(this.bluetooth.lastSenderId);
                        if (fx != null) { fx.finishTime = this.readInt(this.netBuffer, 2); rally3d.core.RLog.i("mp", "extra player " + fx.id + " finished: " + fx.finishTime); }
                        break;
                    }
                    this.opponentFinishTime = this.readInt(this.netBuffer, 2);
                    this.opponentRaceTime = this.opponentFinishTime;
                    break;
                }
                case 17: {
                    this.netBuffer[0] = 18;
                    this.bluetooth.sendPacket(this.netBuffer, 6);
                    break;
                }
                case 18: {
                    if (this.readInt(this.netBuffer, 2) == this.multiplayerPingSequence && this.multiplayerPingSentAt != 0L) {
                        this.multiplayerPingMs = (int)Math.max(0L, (System.nanoTime() - this.multiplayerPingSentAt) / 1_000_000L);
                        this.multiplayerPingSentAt = 0L;
                    }
                    break;
                }
                case 8: {
                    this.mpOnPausePacket(this.netBuffer);
                    break;
                }
                case 1: {
                    if (this.isHost && this.opponentSender > 0 && this.bluetooth.lastSenderId != this.opponentSender) { this.mpPeerGone(this.bluetooth.lastSenderId); break; }
                    return false;
                }
                case 0: {
                    if (this.isHost && this.opponentSender > 0 && this.bluetooth.lastSenderId != this.opponentSender) { this.mpPeerGone(this.bluetooth.lastSenderId); break; }
                    this.bannerText = Texts.connectionLost;
                    this.renderFrame();
                    this.sleep(500);
                    return false;
                }
            }
        }
        boolean anyPause = this.mpLocalPaused || this.mpClockFrozen || !this.mpPausers.isEmpty();
        long silenceLimit = anyPause ? 90_000_000_000L : 10_000_000_000L;   // a paused / backgrounded player must never get the others thrown out
        if (System.nanoTime() - this.lastOpponentPacketAt > silenceLimit
                && this.bluetooth.silentNanos(this.isHost ? Math.max(0, this.opponentSender) : 0) > silenceLimit) {
            rally3d.core.RLog.w("mp", "no packets from opponent for " + (silenceLimit / 1_000_000_000L) + " s, connection lost");
            this.bannerText = Texts.connectionLost;
            this.renderFrame();
            this.sleep(500);
            return false;
        }
        if (this.opponentExtrapFrames > 0) {
            this.opponentPosition.v[0] = this.opponentPosition.v[0] + this.opponentVelocity.v[0] * f;
            this.opponentPosition.v[2] = this.opponentPosition.v[2] + this.opponentVelocity.v[2] * f;
            --this.opponentExtrapFrames;
        }
        if (this.opponentCorrFrames > 0) {
            this.opponentPosition.v[0] = this.opponentPosition.v[0] + this.opponentCorrection.v[0];
            this.opponentPosition.v[2] = this.opponentPosition.v[2] + this.opponentCorrection.v[2];
            --this.opponentCorrFrames;
        }
        this.updateExtras(f, anyPause);
        this.updateOpponentGround();
        return true;
     } finally { rally3d.core.Trace.out(49, $t); } }

    /** Racers a finished player can watch. Race code tracks exactly one remote car (the other player), so this is 1 while he is present. */
    int spectateTargetCount() { /*T*/ final long $t = rally3d.core.Trace.in(352); try {
        int c = this.opponentPresent ? 1 : 0;
        for (RemoteCar rc : this.extras.values()) if (rc.seen) ++c;
        return c;
     } finally { rally3d.core.Trace.out(352, $t); } }

    /** Target index -> extra car (index 0 = primary opponent, 1.. = seen extras in order); null = primary. */
    RemoteCar watchedExtra() { /*T*/ final long $t = rally3d.core.Trace.in(353); try {
        if (this.spectateIndex <= 0) return null;
        int k = 0;
        for (RemoteCar rc : this.extras.values()) if (rc.seen && ++k == this.spectateIndex) return rc;
        return null;
     } finally { rally3d.core.Trace.out(353, $t); } }
    Vec3 watchedPos() { RemoteCar rc = this.watchedExtra(); return rc != null ? rc.pos : this.opponentPosition; }
    Vec3 watchedRot() { RemoteCar rc = this.watchedExtra(); return rc != null ? rc.rot : this.opponentRotation; }
    String watchedName() { /*T*/ final long $t = rally3d.core.Trace.in(354); try { RemoteCar rc = this.watchedExtra(); return rc != null ? rc.name : (this.opponentName == null ? rally3d.core.Lang.t("Player 2") : this.opponentName);  } finally { rally3d.core.Trace.out(354, $t); } }
    boolean allFinished() { /*T*/ final long $t = rally3d.core.Trace.in(355); try {
        if (this.opponentFinishTime < 0) return false;
        for (RemoteCar rc : this.extras.values()) if (rc.seen && rc.finishTime < 0) return false;
        return true;
     } finally { rally3d.core.Trace.out(355, $t); } }

    /** Index of the track segment whose start is closest to p (whole track; used when the cached road window is far from the watched car). */
    int nearestSegment(Vec3 p) { /*T*/ final long $t = rally3d.core.Trace.in(252); try {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < this.segmentCount; ++i) {
            float dx = this.segments[i].startPos.v[0] - p.v[0];
            float dy = this.segments[i].startPos.v[1] - p.v[1];
            float dz = this.segments[i].startPos.v[2] - p.v[2];
            float d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
     } finally { rally3d.core.Trace.out(252, $t); } }

    /** Moves the road window and the camera to the watched car (it can be hundreds of segments away from where we finished). */
    private void relocateToWatched() { /*T*/ final long $t = rally3d.core.Trace.in(253); try {
        this.cameraBaseHeightInitialized = false;
        this.mouseYawOffset = 0.0f;
        this.mouseCameraPitch = 0.0f;
        int seg = this.nearestSegment(this.watchedPos());
        if (seg >= 0) {
            this.cameraState.segmentIndex = seg;
            this.buildRoadWindow(seg);
        }
        this.updateOpponentGround();
     } finally { rally3d.core.Trace.out(253, $t); } }

    /** Chase camera + road window for the watched car (same steps the normal race does for the own car). */
    private void followWatchedCar() { /*T*/ final long $t = rally3d.core.Trace.in(254); try {
        this.updateCamera(8.0f);
        this.findSegments(this.cameraMask, this.cameraState.position);
        int seg = this.firstSegment(this.cameraMask, this.cameraState.position, this.watchedPos().v[1]);
        if (seg < 0) seg = this.nearestSegment(this.watchedPos());
        if (seg >= 0) {
            this.cameraState.segmentIndex = seg;
            this.buildRoadWindow(seg);
        }
        if (this.cameraState.segmentIndex >= 0 && this.cameraState.segmentIndex < this.segmentCount) {
            float cy = (float)Math.cos(Math.toRadians(this.cameraState.yaw - 90.0f));
            float sy = (float)Math.sin(Math.toRadians(this.cameraState.yaw - 90.0f));
            this.forward = cy * this.segments[this.cameraState.segmentIndex].startDir.v[0] + sy * this.segments[this.cameraState.segmentIndex].startDir.v[2] > 0.0f;
        }
     } finally { rally3d.core.Trace.out(254, $t); } }

    void beginSpectate() { /*T*/ final long $t = rally3d.core.Trace.in(255); try {
        this.spectating = true;
        rally3d.desktop.Platform.spectating = true;
        this.spectateIndex = 0;
        this.spectateKeysPrev = this.readKeys();   // keys still held from driving are not "new" presses
        this.car.speed = 0.0f;
        this.car.velocity.v[0] = 0.0f;
        this.car.velocity.v[1] = 0.0f;
        this.car.velocity.v[2] = 0.0f;
        this.relocateToWatched();
        this.followWatchedCar();
        rally3d.core.RLog.i("race", "spectating " + this.opponentName);
     } finally { rally3d.core.Trace.out(255, $t); } }

    void endSpectate() { /*T*/ final long $t = rally3d.core.Trace.in(256); try {
        this.spectating = false;
        this.spectateWait = 0;
        this.spectateKeysPrev = 0;
        rally3d.desktop.Platform.spectating = false;
     } finally { rally3d.core.Trace.out(256, $t); } }

    /** Race step for a racer who already finished: own car frozen, camera follows the other racer in real time. */
    int updateSpectate(boolean bl, int keys) { /*T*/ final long $t = rally3d.core.Trace.in(257); try {
        int n = (int)this.fps;
        if (n < 2) n = 2;
        float f5 = 10.0f / (float)n;
        // left / right = previous / next racer
        int newKeys = keys & ~this.spectateKeysPrev;
        this.spectateKeysPrev = keys;
        int count = this.spectateTargetCount();
        if (count > 1 && (newKeys & 0x24) != 0) {
            this.spectateIndex = (this.spectateIndex + ((newKeys & 0x20) != 0 ? 1 : count - 1)) % count;
            this.relocateToWatched();
        }
        // everybody is in: results screen, then the next game
        if (this.allFinished()) {
            if (this.finishDelay <= 0) return 1;
            --this.finishDelay;
        }
        this.car.velocity.v[0] = 0.0f;
        this.car.velocity.v[1] = 0.0f;
        this.car.velocity.v[2] = 0.0f;
        if (!this.updateOpponent(f5)) return -1;
        this.followWatchedCar();
        this.bannerText = null;
        this.mpSpectateBanner();
        if (!bl) return 0;
        // a finished racer leaves with a double press of pause: an accidental single press (or losing the window focus) must not end the session for the others
        long nowNs = System.nanoTime();
        boolean pausePressed = (keys & 0x200) != 0 && (this.mpPauseKeysPrev & 0x200) == 0;
        this.mpPauseKeysPrev = keys;
        if (pausePressed) {
            if (this.mpSpectateLeaveArmed != 0L && nowNs - this.mpSpectateLeaveArmed < 2_000_000_000L) return -1;
            this.mpSpectateLeaveArmed = nowNs;
        }
        if (this.mpSpectateLeaveArmed != 0L && nowNs - this.mpSpectateLeaveArmed < 2_000_000_000L) this.bannerText2 = rally3d.core.Lang.t("Press pause again to leave");
        return 0;
     } finally { rally3d.core.Trace.out(257, $t); } }

    void placeCamera() { /*T*/ final long $t = rally3d.core.Trace.in(50); try {
        this.updateCamera(8.0f);
        this.findSegments(this.cameraMask, this.cameraState.position);
        this.cameraState.segmentIndex = this.firstSegment(this.cameraMask, this.cameraState.position, this.car.position.v[1]);
        if (this.cameraState.segmentIndex < 0) {
            this.cameraState.segmentIndex = 0;
        }
        this.buildRoadWindow(this.cameraState.segmentIndex);
        this.computeCorners(this.car.wheelPositions, this.car.bodyCorners, this.car.position, this.car.rotation);
     } finally { rally3d.core.Trace.out(50, $t); } }

    String formatTime(int n) {
        if (n < 0) {
            return "-:-";
        }
        int n2 = n % 10;
        int n3 = n / 10 % 60;
        int n4 = n / 600;
        if (n4 <= 0) {
            return "" + n3 + ":" + n2;
        }
        if (n3 < 10) {
            return "" + n4 + ":0" + n3 + ":" + n2;
        }
        return "" + n4 + ":" + n3 + ":" + n2;
    }

    void drawRect(Graphics graphics, int n, int n2, int n3, int n4) {
        graphics.fillRect(n, n2, n3, 1);
        graphics.fillRect(n, n2, 1, n4);
        graphics.fillRect(n, n2 + n4, n3 + 1, 1);
        graphics.fillRect(n + n3, n2, 1, n4);
    }

    void drawPanel(Graphics graphics, int n, int n2, int n3, int n4) {
        graphics.setClip(n, n2, n3, n4);
        graphics.fillRectAlpha(n, n2, n3, n4, 0x55FFFFFF);
        graphics.setClip(0, 0, this.width, this.height);
        graphics.setColor(0xFFFFFF);
        this.drawRect(graphics, n, n2, n3, n4);
        graphics.setColor(0);
        this.drawRect(graphics, n - 1, n2 - 1, n3 + 2, n4 + 2);
    }

    void drawInfoScreen(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(51); try {
        int n;
        int n2 = 2;
        boolean bl = false;
        int n3 = 3;
        if (!this.infoLaidOut) {
            this.layoutText(n3, 0, this.width - n3 * 2, this.height, this.infoFooter, true);
            n2 = this.totalTextLines;
            if (n2 > 3) {
                n3 = 0;
            }
        }
        int n4 = 3;
        int n5 = this.fontBold.stringWidth("3. ");
        int n6 = this.fontBoldHeight * (n2 + 3) + this.fontSmallHeight * this.infoRows;
        if (this.introMode) {
            n6 -= this.fontSmallHeight * this.infoRows + this.fontBoldHeight;
        } else {
            n6 -= this.fontBoldHeight * 3;
            n6 += this.fontSmallHeight * this.infoRows;
        }
        bl = true;
        if (n6 > this.height) {
            n6 -= this.fontBoldHeight;
            bl = true;
        }
        int n7 = n = (this.height - n6) / 2;
        if (!this.infoLaidOut) {
            int n8 = n7 - 2;
            int n9 = n6 + 4;
            if (n8 < 0) {
                n8 = 0;
            }
            if (n9 > this.height - 1) {
                n9 = this.height - 1;
            }
            this.drawPanel(graphics, n4 - 1, n8, this.width - n4 * 2, n9);
            graphics.setFont(this.fontBold);
            if (this.introMode) {
                this.drawText(graphics, 0, n7, this.infoTitle, -86, 5);
                this.drawText(graphics, 3, n7 += this.fontBoldHeight, MapLibrary.nameOf(this.race.trackIndex), -1, 5);
                n7 += this.fontBoldHeight;
            }
            if (!this.introMode) {
                if (!bl) {
                    this.drawText(graphics, 0, n7, this.infoSubtitle, -86, 5);
                    n7 += this.fontBoldHeight;
                }
                graphics.setFont(this.fontSmall);
                for (int i = 0; i < this.infoRows; ++i) {
                    String string;
                    String[] stringArray = new String[]{"" + (i + 1), this.infoNames[i]};
                    String string2 = Localization.getFormatted(31, stringArray);
                    if (Texts.rightToLeft) {
                        string = "" + this.formatTime(this.infoTimes[i]) + " " + Localization.getString(32);
                        this.drawText(graphics, n4 + 3, n7, string2, -86, 9);
                        this.drawText(graphics, n4 + 3, n7 += this.fontSmallHeight, string, -1, 9);
                    } else {
                        string = Localization.getString(32) + " " + this.formatTime(this.infoTimes[i]);
                        this.drawText(graphics, n4 + 3, n7, string2, -86, 1);
                        this.drawText(graphics, n4 + 3, n7 += this.fontSmallHeight, string, -1, 1);
                    }
                    n7 += this.fontSmallHeight;
                }
            }
            this.layoutText(n3, n + n6 - this.fontBoldHeight * n2, this.width - n3 * 2, n + n6, this.infoFooter, true);
            this.infoLaidOut = true;
        }
        this.blinkCounter = (this.blinkCounter + 1) % 6;
        int n10 = this.blinkCounter < 3 ? 0x94FFFF : 37549;
        graphics.setFont(this.fontBold);
        this.drawTextBlock(graphics, n10);
     } finally { rally3d.core.Trace.out(51, $t); } }

    int showRaceIntro(boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(52); try {
        int n;
        this.introMode = true;
        if (this.race.mode == 1 || this.race.mode == 3) {
            if (this.race.mode == 3) this.infoTitle = rally3d.core.Lang.t("Ghost Race");
            else this.infoTitle = Texts.timeTrial;
            this.infoSubtitle = Texts.highScores;
            this.infoRows = 5;
            if (MapLibrary.isCustom(this.race.trackIndex)) {
                String[] cn = MapLibrary.bestNames(this.race.trackIndex);
                int[] ct = MapLibrary.bestTimes(this.race.trackIndex);
                for (n = 0; n < 5; ++n) { this.infoNames[n] = cn[n]; this.infoTimes[n] = ct[n]; }
            } else
            for (n = 0; n < 5; ++n) {
                this.infoNames[n] = this.saveData.bestNames[n][this.race.trackIndex];
                this.infoTimes[n] = this.saveData.bestTimes[n][this.race.trackIndex];
            }
        } else if (this.race.mode == 0) {
            this.infoTitle = Texts.championship;
            this.infoSubtitle = Texts.opponents;
            this.infoRows = 5;
            for (n = 0; n < 4; ++n) {
                this.infoNames[n] = Texts.playerNames[n];
                this.infoTimes[n] = this.refTime(this.race.trackIndex, n);
            }
            this.infoNames[4] = this.saveData.playerName;
            this.infoTimes[4] = -1;
        } else {
            this.infoTitle = Texts.multiplayer;
            this.infoSubtitle = Texts.playersLabel;
            this.infoRows = 2;
            this.infoNames[0] = this.saveData.playerName;
            this.infoTimes[0] = -1;
            this.infoNames[1] = this.opponentName;
            this.infoTimes[1] = -1;
        }
        this.targetTime = this.ghostOn() && this.ghostPlayback != null || this.race.mode == 3
                ? (this.ghostPlayback != null ? this.ghostPlayback.finishTime : -1)
                : this.infoTimes[0];
        this.menuSlide = 0;
        this.infoLaidOut = false;
        this.infoFooter = Texts.pressToStart;
        if (bl && this.race.mode != 2) {
            int n2 = this.screenMode;
            this.screenMode = 4;
            int[] tapXY = new int[4];
            this.pollPointerEvents(tapXY); // drop stale taps from the previous menu
            while (true) {
                int n3;
                if (((n3 = this.pollNewKeys()) & 0x200) != 0) {
                    this.screenMode = n2;
                    return -1;
                }
                if ((this.pollPointerEvents(tapXY) & 2) != 0) n3 = 1; // tap/click = confirm
                if (n3 != 0) {
                    this.screenMode = n2;
                    return 1;
                }
                this.renderFrame();
            }
        }
        return 1;
     } finally { rally3d.core.Trace.out(52, $t); } }

    void recordTimeTrial() { /*T*/ final long $t = rally3d.core.Trace.in(53); try {
        this.race.mode = 1;
        this.showRaceIntro(false);
        this.insertScore();
        if (MapLibrary.isCustom(this.race.trackIndex)) {
            MapLibrary.setBest(this.race.trackIndex, this.infoNames, this.infoTimes);
        } else {
            for (int i = 0; i < 5; ++i) {
                this.saveData.bestNames[i][this.race.trackIndex] = this.infoNames[i];
                this.saveData.bestTimes[i][this.race.trackIndex] = this.infoTimes[i];
            }
            this.saveData.save();
        }
        this.race.mode = 0;
     } finally { rally3d.core.Trace.out(53, $t); } }

    void showRaceEnd() { /*T*/ final long $t = rally3d.core.Trace.in(54); try {
        rally3d.core.RLog.i("race", "race END: mode=" + this.race.mode + " track=" + this.race.trackIndex + " car=" + this.race.carIndex + " time=" + this.race.raceTime + " checkpoints=" + this.race.checkpointsPassed);
        int n = this.screenMode;
        this.introMode = false;
        int n2 = this.insertScore();
        switch (this.race.mode) {
            case 1: {
                this.infoFooter = Texts.placeMessages[n2];
                if (MapLibrary.isCustom(this.race.trackIndex)) {
                    MapLibrary.setBest(this.race.trackIndex, this.infoNames, this.infoTimes);
                    break;
                }
                for (int i = 0; i < 5; ++i) {
                    this.saveData.bestNames[i][this.race.trackIndex] = this.infoNames[i];
                    this.saveData.bestTimes[i][this.race.trackIndex] = this.infoTimes[i];
                }
                this.saveData.save();
                break;
            }
            case 0: {
                if (n2 > 3) {
                    n2 = 3;
                }
                this.infoFooter = Texts.medalMessages[n2];
                break;
            }
            case 2: {
                // all finished players (me, primary opponent, extras), best time first; the table holds 5 rows
                final java.util.ArrayList<Object[]> res = new java.util.ArrayList<Object[]>();
                res.add(new Object[]{this.saveData.playerName, Integer.valueOf(this.race.raceTime), Boolean.TRUE});
                res.add(new Object[]{this.opponentName == null ? rally3d.core.Lang.t("Player 2") : this.opponentName, Integer.valueOf(this.opponentFinishTime), Boolean.FALSE});
                for (RemoteCar rc : this.extras.values()) if (rc.finishTime >= 0) res.add(new Object[]{rc.name == null ? rally3d.core.Lang.t("Player ") + (rc.id + 1) : rc.name, Integer.valueOf(rc.finishTime), Boolean.FALSE});
                java.util.Collections.sort(res, new java.util.Comparator<Object[]>() {   // stable: on a tie I stay first
                    public int compare(Object[] x, Object[] y) { return ((Integer)x[1]).intValue() - ((Integer)y[1]).intValue(); }
                });
                int rows = Math.min(res.size(), this.infoNames.length);
                for (int r = 0; r < rows; ++r) { this.infoNames[r] = (String)res.get(r)[0]; this.infoTimes[r] = ((Integer)res.get(r)[1]).intValue(); }
                this.infoRows = rows;
                this.infoFooter = Texts.placeMessages[((Boolean)res.get(0)[2]).booleanValue() ? 0 : 1];
            }
        }
        this.menuSlide = 0;
        this.infoLaidOut = false;
        this.screenMode = 4;
        this.snapshotKeys();
        long l = this.currentTime() + 3000L;
        while (this.currentTime() < l) {
            this.renderFrame();
        }
        this.screenMode = n;
        boolean customRace = MapLibrary.isCustom(this.race.trackIndex);
        if (this.race.mode == 1) {
            if (!customRace) this.updateMedals(this.race.trackIndex, this.campaignPlaceForTime(this.race.trackIndex, this.race.raceTime));
        } else if (this.race.mode == 2) {
            this.saveTrackHighScore();
            if (!customRace) this.updateMedals(this.race.trackIndex, this.campaignPlaceForTime(this.race.trackIndex, this.race.raceTime));
        }
        if (this.race.mode == 0) {
            this.updateMedals(this.race.trackIndex, n2);
        }
        if (this.race.mode == 0) {
            this.recordTimeTrial();
        }
     } finally { rally3d.core.Trace.out(54, $t); } }

    int insertScore() { /*T*/ final long $t = rally3d.core.Trace.in(55); try {
        int n = 5;
        for (int i = 4; i >= 0; --i) {
            if (this.infoTimes[i] >= 0 && this.race.raceTime >= this.infoTimes[i]) continue;
            if (i < 4) {
                this.infoNames[i + 1] = this.infoNames[i];
                this.infoTimes[i + 1] = this.infoTimes[i];
            }
            this.infoNames[i] = this.saveData.playerName;
            this.infoTimes[i] = this.race.raceTime;
            n = i;
        }
        return n;
     } finally { rally3d.core.Trace.out(55, $t); } }

    /** Championship opponent time (0.1 s) number i (0 = fastest) on a stock track or a custom map. */
    int refTime(int track, int i) { /*T*/ final long $t = rally3d.core.Trace.in(731); try {
        if (MapLibrary.isCustom(track)) return MapLibrary.parTimes(MapLibrary.byId(track), this.segmentCount)[i];
        return this.saveData.defaultTime(track, i);
     } finally { rally3d.core.Trace.out(731, $t); } }

    private int campaignPlaceForTime(int track, int time) { /*T*/ final long $t = rally3d.core.Trace.in(56); try {
        int place = 0;
        for (int i = 0; i < 4; i++) {
            if (time >= this.refTime(track, i)) place++;
        }
        return place;
     } finally { rally3d.core.Trace.out(56, $t); } }

    private void saveTrackHighScore() { /*T*/ final long $t = rally3d.core.Trace.in(57); try {
        int track = this.race.trackIndex;
        if (MapLibrary.isCustom(track)) {   // custom map: its own top-5 table (maprecords.txt)
            if (this.race.raceTime < 0) return;
            String[] cn = MapLibrary.bestNames(track).clone();
            int[] ct = MapLibrary.bestTimes(track).clone();
            int at = -1;
            for (int i = 0; i < 5; i++) if (ct[i] < 0 || this.race.raceTime < ct[i]) { at = i; break; }
            if (at < 0) return;
            for (int i = 4; i > at; i--) { ct[i] = ct[i - 1]; cn[i] = cn[i - 1]; }
            ct[at] = this.race.raceTime;
            cn[at] = this.saveData.playerName;
            MapLibrary.setBest(track, cn, ct);
            return;
        }
        if (track < 0 || track >= 12 || this.race.raceTime < 0) return;
        int insert = -1;
        for (int i = 0; i < 5; i++) {
            if (this.race.raceTime < this.saveData.bestTimes[i][track]) { insert = i; break; }
        }
        if (insert < 0) return;
        for (int i = 4; i > insert; i--) {
            this.saveData.bestTimes[i][track] = this.saveData.bestTimes[i - 1][track];
            this.saveData.bestNames[i][track] = this.saveData.bestNames[i - 1][track];
        }
        this.saveData.bestTimes[insert][track] = this.race.raceTime;
        this.saveData.bestNames[insert][track] = this.saveData.playerName;
        this.saveData.save();
     } finally { rally3d.core.Trace.out(57, $t); } }

    void updateMedals(int n, int n2) { /*T*/ final long $t = rally3d.core.Trace.in(58); try {
        int n3;
        int[] nArray = new int[]{-1, 4, 8, 12};
        int[] nArray2 = new int[]{-1, 2, 1, 0};
        if (n2 > 2) {
            return;
        }
        if (MapLibrary.isCustom(n)) {   // custom maps are never locked and unlock nothing: they only keep their own medal
            MapLibrary.setMedal(n, n2);
            return;
        }
        if (n < 0 || n >= 12) return;
        if (this.saveData.trackMedals[n] <= n2) {
            return;
        }
        this.saveData.trackMedals[n] = n2;
        for (n3 = 1; n3 < 12; ++n3) {
            if (this.saveData.trackMedals[n3] != 4 || this.saveData.trackMedals[n3 - 1] > 2) continue;
            this.saveData.trackMedals[n3] = 3;
            this.showMessage(Texts.trackUnlocked, Texts.trackNames[n3]);
        }
        for (int i = 1; i < 4; ++i) {
            if (this.saveData.carLocks[i] != 1) continue;
            boolean bl = false;
            for (n3 = 0; n3 < nArray[i]; ++n3) {
                if (this.saveData.trackMedals[n3] <= nArray2[i]) continue;
                bl = true;
            }
            if (bl) continue;
            this.saveData.carLocks[i] = 0;
            this.showMessage(Texts.carUnlocked, Texts.carNames[i]);
            if (i != 3) continue;
            this.showMessage(Texts.goodJob, Texts.youAreChampion);
        }
        this.saveData.save();
     } finally { rally3d.core.Trace.out(58, $t); } }

    void drawMessage(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(59); try {
        int n = 3;
        int n2 = this.fontBoldHeight * 6;
        int n3 = (this.height - n2) / 2;
        if (this.skyImage != null && this.roadMeshes != null) this.drawWorld(graphics);
        this.drawPanel(graphics, n - 1, n3 - 3, this.width - n * 2, n2 + 6);
        n3 = (this.height - this.fontBoldHeight * 3) / 2;
        graphics.setFont(this.fontBold);
        this.drawText(graphics, 0, n3, this.messageTitle, -86, 5);
        this.drawText(graphics, 0, n3 += this.fontBoldHeight * 2, this.messageText, 0xFFFFFF, 5);
     } finally { rally3d.core.Trace.out(59, $t); } }

    void showMessage(String string, String string2) { /*T*/ final long $t = rally3d.core.Trace.in(60); try {
        int n = this.screenMode;
        this.messageTitle = string;
        this.messageText = string2;
        long l = this.currentTime() + 3000L;
        this.screenMode = 6;
        do { this.renderFrame(); } while (this.currentTime() < l);
        this.screenMode = n;
     } finally { rally3d.core.Trace.out(60, $t); } }

    int lastUnlockedTrack() { /*T*/ final long $t = rally3d.core.Trace.in(61); try {
        int n = 0;
        int n2 = 0;
        for (int i = 0; i < 12; ++i) {
            if (this.saveData.trackMedals[i] >= 4 || this.saveData.trackMedals[i] <= n) continue;
            n = this.saveData.trackMedals[i];
            n2 = i;
        }
        return n2;
     } finally { rally3d.core.Trace.out(61, $t); } }

    /** Championship: race with a ghost (record the run, optionally race a recorded ghost). Switched in the track menu. */
    boolean championshipGhost;
    static final int TOGGLE_GHOST = 99999;

    /** Ghost recording / playback is active in Ghost Race and in a Championship race with the ghost switch on. */
    boolean ghostOn() { return this.race.mode == 3 || (this.race.mode == 0 && this.championshipGhost); }

    int trackMenu(int n, boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(62); try {
        int forced = -1;
        while (true) {
            int r = this.trackMenuOnce(n, bl, forced);
            if (r != TOGGLE_GHOST) return r;
            this.championshipGhost = !this.championshipGhost;
            rally3d.core.RLog.i("ghost", "Championship ghost " + (this.championshipGhost ? "ON" : "OFF"));
            forced = TOGGLE_GHOST;
        }
     } finally { rally3d.core.Trace.out(62, $t); } }

    private int trackMenuOnce(int n, boolean bl, int forced) { /*T*/ final long $t = rally3d.core.Trace.in(62); try {
        if (bl) {
            n = this.lastUnlockedTrack();
        }
        this.beginMenu(Texts.selectTrack);
        if (this.race.mode == 0) this.addMenuItem(rally3d.core.Lang.t("Ghost: ") + (this.championshipGhost ? rally3d.core.Lang.t("ON") : rally3d.core.Lang.t("OFF")), TOGGLE_GHOST);
        java.util.List<AddonPack> maps = MapLibrary.menuMaps();
        if (this.race.mode != 0) {   // custom maps (add-ons) come first; in the Championship they follow the stock tracks
            for (AddonPack mp : maps) this.addIconMenuItem(mp.name, bl ? this.cupImages[MapLibrary.medalOf(MapLibrary.idOf(mp))] : null, MapLibrary.idOf(mp), false);
            if (!maps.isEmpty() && !bl) n = MapLibrary.idOf(maps.get(0));
        }
        for (int i = 0; i < 12; ++i) {
            int n2 = this.saveData.trackMedals[i];
            if (bl) {
                this.addIconMenuItem(Texts.trackNames[i], this.cupImages[n2], i, this.saveData.trackMedals[i] > 3);
                continue;
            }
            this.addIconMenuItem(Texts.trackNames[i], null, i, this.saveData.trackMedals[i] > 3);
        }
        if (this.race.mode == 0) {   // Championship: enabled custom maps after the 12 stock tracks, always unlocked, own medals
            for (AddonPack mp : maps) this.addIconMenuItem(mp.name, this.cupImages[MapLibrary.medalOf(MapLibrary.idOf(mp))], MapLibrary.idOf(mp), false);
        }
        return this.runMenu(forced >= 0 ? forced : n);
     } finally { rally3d.core.Trace.out(62, $t); } }

    int carMenu(boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(63); try {
        int n = 0;
        this.refreshLocalCars();
        this.beginMenu(Texts.selectCar);
        for (int i = 0; i < this.localCars.size() && i < 32; ++i) {
            if (i >= 4) { this.addIconMenuItem(this.localCars.get(i).name, null, i, false); continue; }   // add-on cars are never locked
            this.addIconMenuItem(Texts.carNames[i], null, i, this.saveData.carLocks[i] > 0 && bl);
            if (this.saveData.carLocks[i] != 0) continue;
            n = i;
        }
        this.showCarPreview = 1;
        this.menuStyle = 1;
        this.carPreviewYaw = 0.0f;
        this.carPreviewLastNanos = System.nanoTime();
        return this.runMenu(n);
     } finally { rally3d.core.Trace.out(63, $t); } }

    // Player-info packet (type 6) that arrived while this side was still in the lobby loop.
    private byte[] pendingPlayerInfo;
    private int pendingPlayerInfoSender;

    boolean syncStart() { /*T*/ final long $t = rally3d.core.Trace.in(64); try {
        int previousScreen = this.screenMode;
        this.beginMenu(rally3d.core.Lang.t("Multiplayer Lobby"));
        this.addMenuItem("", 0);
        this.showLeftSoftkey = false;
        this.showRightSoftkey = false;
        this.showTextBlock = 1;
        // Multiplayer setup has already loaded the race scene; drawing the title
        // image here dereferences a resource intentionally cleared by loadScreen(3).
        this.showTitleBackground = 0;
        this.showArrows = false;
        this.menuSlide = 0;
        this.menuWaitMode = 0;
        // beginMenu() resets the item count but not the layout of the previous menu:
        // stale rows of the track list were drawn over the lobby text.
        this.visibleRows = 0;
        this.scrollOffset = 0;
        this.selectedIndex = 0;
        this.screenMode = 2;
        this.bluetooth.resetLobby();
        this.pendingPlayerInfo = null;
        boolean localReady = false;
        boolean remoteReady = false;
        long nextStartResend = 0L;
        boolean previousSpace = this.isSpaceDown() || rally3d.desktop.Platform.controllerReadyDown();
        int[] readyTapXY = new int[4];
        this.pollPointerEvents(readyTapXY); // drop stale taps
        long nextStatus = 0L;
        long countdownEnds = 0L;
        boolean startSent = false;
        boolean startReceived = false;
        int previousLocal = -1;
        int previousRemote = -1;
        int previousSeconds = -1;
        int lobbyReady = 0;
        int lobbyTotal = 1;
        int previousLobbyReady = -1;
        int previousLobbyTotal = -1;
        int previousPeers = -1;
        long nextLobbyStatus = 0L;
        while (true) {
            long now = System.nanoTime();
            boolean space = this.isSpaceDown() || rally3d.desktop.Platform.controllerReadyDown();
            if (space && !previousSpace && !localReady) { localReady = true; rally3d.core.RLog.i("mp", "local player READY (key)"); }
            previousSpace = space;
            if ((this.pollPointerEvents(readyTapXY) & 2) != 0 && !localReady) { localReady = true; rally3d.core.RLog.i("mp", "local player READY (tap)"); }
            if ((this.pollNewKeys() & 0x200) != 0) {
                this.screenMode = previousScreen;
                return false;
            }
            if (localReady && now >= nextStatus) {
                this.netBuffer[0] = 7;
                this.netBuffer[1] = 1;
                this.netBuffer[2] = 1;
                this.bluetooth.sendPacket(this.netBuffer, 3);
                nextStatus = now + 250_000_000L;
            }
            while (this.bluetooth.receivePacket(this.netBuffer)) {
                int type = this.netBuffer[0] & 0xFF;
                if (type == 7 && (this.netBuffer[1] & 0xFF) >= 1) {
                    int command = this.netBuffer[2] & 0xFF;
                    rally3d.core.RLog.i("mp", "lobby packet command=" + command + " from sender " + this.bluetooth.lastSenderId + " (localReady=" + localReady + ", remoteReady=" + remoteReady + ")");
                    if (command == 1) {
                        if (this.isHost) this.bluetooth.markReady(this.bluetooth.lastSenderId);
                        else remoteReady = true;
                    }
                    else if (command == 2 && localReady) {
                        // START is re-sent by the host; take the first one only (do not restart the countdown)
                        if (!startReceived) { startReceived = true; countdownEnds = now + 5_000_000_000L; rally3d.core.RLog.i("mp", "START received, countdown 5s"); }
                    } else if (command == 3 && (this.netBuffer[1] & 0xFF) >= 3) {
                        lobbyReady = this.netBuffer[3] & 0xFF;
                        lobbyTotal = this.netBuffer[4] & 0xFF;
                    } else if (command == 4) {
                        startReceived = false;
                        startSent = false;
                        countdownEnds = 0L;
                    }
                } else if (type == 6) {
                    // Peer left the lobby first and already sent its info: keep it, otherwise it is lost and exchangePlayerInfo() waits 20 s.
                    this.pendingPlayerInfo = java.util.Arrays.copyOf(this.netBuffer, Math.min(this.netBuffer.length, (this.netBuffer[1] & 0xFF) + 2));
                    this.pendingPlayerInfoSender = this.bluetooth.lastSenderId;
                    rally3d.core.RLog.i("mp", "lobby: stored early player info from sender " + this.bluetooth.lastSenderId);
                } else if (type == 0 || type == 1) {
                    rally3d.core.RLog.w("mp", "lobby: peer " + this.bluetooth.lastSenderId + " sent type " + type + " (" + (type == 0 ? "connection lost" : "left") + ")");
                    if (this.isHost && this.bluetooth.lastSenderId > 0) {
                        remoteReady = false;
                        startSent = false;
                        startReceived = false;
                        countdownEnds = 0L;
                    } else {
                        this.screenMode = previousScreen;
                        return false;
                    }
                }
            }
            if (this.isHost) {
                remoteReady = this.bluetooth.allConnectedPeersReady();
                lobbyReady = this.bluetooth.readyPeerCount() + (localReady ? 1 : 0);
                lobbyTotal = this.bluetooth.playerCount();
                if (startSent && !remoteReady) {
                    this.netBuffer[0] = 7; this.netBuffer[1] = 1; this.netBuffer[2] = 4;
                    this.bluetooth.sendPacket(this.netBuffer, 3);
                    startSent = false; startReceived = false; countdownEnds = 0L;
                }
                if (startSent && now >= nextStartResend) {   // START is not a one-shot: a guest that missed it still starts
                    this.netBuffer[0] = 7; this.netBuffer[1] = 1; this.netBuffer[2] = 2;
                    this.bluetooth.sendPacket(this.netBuffer, 3);
                    nextStartResend = now + 250_000_000L;
                }
                if (now >= nextLobbyStatus) {
                    this.netBuffer[0] = 7; this.netBuffer[1] = 3; this.netBuffer[2] = 3;
                    this.netBuffer[3] = (byte)lobbyReady; this.netBuffer[4] = (byte)lobbyTotal;
                    this.bluetooth.sendPacket(this.netBuffer, 5);
                    nextLobbyStatus = now + 250_000_000L;
                }
            }
            if (this.isHost && localReady && remoteReady && !startSent) {
                this.netBuffer[0] = 7;
                this.netBuffer[1] = 1;
                this.netBuffer[2] = 2;
                this.bluetooth.sendPacket(this.netBuffer, 3);
                startSent = true;
                startReceived = true;
                rally3d.core.RLog.i("mp", "host: everyone ready, sending START, countdown 5s");
                countdownEnds = now + 5_000_000_000L;
            }
            int localState = localReady ? 1 : 0;
            int remoteState = remoteReady ? 1 : 0;
            int seconds = startReceived ? (int)Math.max(0L, (countdownEnds - now + 999_999_999L) / 1_000_000_000L) : -1;
            int peersNow = this.isHost ? this.bluetooth.connectedPeerCount() : 1;
            if (peersNow != previousPeers) { previousPeers = peersNow; previousLocal = -1; }
            if (localState != previousLocal || remoteState != previousRemote || seconds != previousSeconds
                    || lobbyReady != previousLobbyReady || lobbyTotal != previousLobbyTotal) {
                String others = this.isHost
                        ? rally3d.core.Lang.t("Players ready: ") + lobbyReady + " / " + lobbyTotal
                        : rally3d.core.Lang.t("Players ready: ") + lobbyReady + " / " + Math.max(1, lobbyTotal);
                boolean alone = this.isHost && this.bluetooth.connectedPeerCount() < 1;
                String status = (localReady ? rally3d.core.Lang.t("You: READY") : rally3d.core.Lang.t("You: press SPACE or tap to get ready"))
                        + "|" + others
                        + (startReceived ? rally3d.core.Lang.t("|Race starts in ") + seconds + "..."
                        : alone ? rally3d.core.Lang.t("|Waiting for another player to join...") : rally3d.core.Lang.t("|The race starts when everyone is ready"));
                this.layoutText(2, this.barHeight + 2, this.width - 5, this.height - this.barHeight - 3, status, true);
                previousLocal = localState;
                previousRemote = remoteState;
                previousSeconds = seconds;
                previousLobbyReady = lobbyReady;
                previousLobbyTotal = lobbyTotal;
            }
            if (startReceived && now >= countdownEnds) {
                rally3d.core.RLog.i("mp", "countdown finished, starting race");
                this.screenMode = previousScreen;
                return true;
            }
            // Refresh the track scene behind changing lobby status text.
            this.redrawBackground = true;
            this.renderFrame();
        }
     } finally { rally3d.core.Trace.out(64, $t); } }

    void drainNetwork() {
        while (this.bluetooth.receivePacket(this.netBuffer)) {
            this.sleep(10);
        }
    }

    boolean applyPlayerInfo(byte[] data, int senderId) { /*T*/ final long $t = rally3d.core.Trace.in(240); try {
        if (data == null || data.length < 4) return false;
        int oc = data[2];
        int car = oc >= 0 && oc < 4 ? oc : 0;
        int nl = data[3] & 0xFF;
        String nm;
        try { nm = new String(data, 4, Math.min(nl, Math.max(0, data.length - 4)), "UTF-16BE"); }
        catch (Exception exception) { rally3d.core.RLog.caught("RallyGame.exchangePlayerInfo", exception); nm = "??? fel"; }
        int sid = this.isHost ? senderId : 0;
        if (!this.isHost || !this.opponentPresent || this.opponentSender == sid) {   // the first player to report is the primary opponent (HUD, results, spectating)
            this.opponentCar = car;
            this.stockOpponentCar = car;
            this.opponentName = nm;
            if (!this.opponentPresent) this.slotPosition(sid, this.opponentPosition);
            this.opponentSender = sid;
            this.opponentPresent = true;
        } else {
            RemoteCar rc = this.extraFor(sid);
            if (rc != null) { rc.name = nm.trim().length() == 0 ? rc.name : nm.trim(); rc.stockCar = car; if (rc.car < REMOTE_BASE) rc.car = car; }
        }
        this.mpInfoGot.add(Integer.valueOf(sid));
        if (!this.isHost && (data[1] & 0xFF) >= 3 + nl && 4 + nl < data.length) this.mpInfoTotal = data[4 + nl] & 0xFF;   // the host appends the player count
        return true;
     } finally { rally3d.core.Trace.out(240, $t); } }

    boolean exchangePlayerInfo() { /*T*/ final long $t = rally3d.core.Trace.in(65); try {
        byte[] nameBytes;
        try { nameBytes = this.saveData.playerName.getBytes("UTF-16BE"); }
        catch (Exception exception) { rally3d.core.RLog.caught("RallyGame.exchangePlayerInfo", exception); nameBytes = new byte[2]; }
        int tail = this.isHost ? 1 : 0;
        final byte[] infoPacket = new byte[4 + nameBytes.length + tail];
        infoPacket[0] = 6;
        infoPacket[1] = (byte)(2 + nameBytes.length + tail);
        infoPacket[2] = (byte)(this.race.carIndex >= 0 && this.race.carIndex < 4 ? this.race.carIndex : 0);   // add-on cars travel as a pack (AddonSync), stock model 0 is the fallback
        infoPacket[3] = (byte)nameBytes.length;
        System.arraycopy(nameBytes, 0, infoPacket, 4, nameBytes.length);
        if (this.isHost) infoPacket[4 + nameBytes.length] = (byte)this.bluetooth.playerCount();
        this.mpInfoGot.clear();
        this.mpInfoTotal = -1;
        this.bluetooth.sendPacket(infoPacket, infoPacket.length);
        long deadline = System.currentTimeMillis() + 20000L;
        long nextInfoResend = System.currentTimeMillis() + 500L;
        long doneAt = 0L;
        while (System.currentTimeMillis() < deadline) {
            long now = System.currentTimeMillis();
            if (this.pendingPlayerInfo != null) {
                byte[] early = this.pendingPlayerInfo;
                this.pendingPlayerInfo = null;
                this.applyPlayerInfo(early, this.pendingPlayerInfoSender);
            }
            if (now >= nextInfoResend) {   // peers may still be in their lobby loop (which discards type 6 / 11): keep re-sending
                this.bluetooth.sendPacket(infoPacket, infoPacket.length);
                nextInfoResend = now + 500L;
            }
            if (this.bluetooth.receivePacket(this.netBuffer)) {
                int type = this.netBuffer[0] & 0xFF;
                if (type == 6) this.applyPlayerInfo(this.netBuffer, this.bluetooth.lastSenderId);
                else if (type == 11) this.applyRelayedInfo(this.netBuffer);
                else if ((type == 0 || type == 1) && !(this.isHost && this.opponentSender > 0 && this.bluetooth.lastSenderId != this.opponentSender)) return false;   // only a lost link to the primary opponent / host aborts
                continue;
            }
            boolean have = this.isHost
                    ? this.bluetooth.connectedPeerCount() > 0 && this.mpInfoGot.size() >= this.bluetooth.connectedPeerCount()
                    : this.mpInfoTotal > 0 && this.mpInfoGot.size() >= this.mpInfoTotal - 1;
            if (have && doneAt == 0L) doneAt = now;
            if (doneAt != 0L && now - doneAt >= 1500L) return true;   // linger: slower peers still need my info
            this.sleep(20);
        }
        rally3d.core.RLog.w("mp", "player info: " + this.mpInfoGot.size() + " received, expected " + (this.isHost ? this.bluetooth.connectedPeerCount() : this.mpInfoTotal - 1));
        return this.opponentPresent;
     } finally { rally3d.core.Trace.out(65, $t); } }

    void introSweep() {
        this.placeCamera();
        for (int i = 18; i >= 8; --i) {
            this.updateCamera(i);
            this.renderFrame();
        }
    }

    void prepareRace(boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(66); try {
        if (bl) {
            this.buildRoadWindow(this.car.segmentIndex);
            this.startTimeOffset = (int)(this.currentTime() / 100L) - this.race.raceTime;
        } else {
            this.car = new CarState();
            this.cameraState = new CameraState();
            this.cameraState.position.v[0] = 0.0f;
            this.cameraState.position.v[1] = 5.0f;
            this.cameraState.position.v[2] = 0.0f;
            this.cameraState.yaw = 180.0f;
            this.resetCar(5);
            this.opponentWheelPts = new Vec3[4];
            for (int i = 0; i < 4; ++i) {
                this.opponentWheelPts[i] = new Vec3();
            }
        }
        this.endSpectate();
        this.mpResetPause();
        if (this.race.mode == 2) {
            this.multiplayerPingMs = -1;
            this.opponentPingMs = -1;
            this.multiplayerPingSentAt = 0L;
            this.nextMultiplayerPingAt = 0L;
            this.opponentPresent = false;
            this.extras.clear();
            this.opponentRotation.set(this.car.rotation);
            this.slotPosition(this.myPlayerId(), this.car.position);
            this.slotPosition(this.isHost ? 1 : 0, this.opponentPosition);
            this.opponentCorrFrames = 0;
        }
        this.lastGoodSegment = 5;
        this.wheelsNoSurface = 0;
        this.wheelsOffRoad = 0;
        this.wheelsAirborne = 0;
        this.splitTimer = 0;
        this.raceFinished = false;
        this.finishDelay = 0;
     } finally { rally3d.core.Trace.out(66, $t); } }

    int raceLoop() { /*T*/ final long $t = rally3d.core.Trace.in(67); try {
        this.highFps = true;
        try {
            return this.raceLoopImpl();
        }
        finally {
            this.highFps = false;
            rally3d.desktop.Platform.mpRace = false;
            this.mpResetPause();
            rally3d.desktop.Platform.setMouseLookActive(false);
        }
     } finally { rally3d.core.Trace.out(67, $t); } }

    int raceLoopImpl() { /*T*/ final long $t = rally3d.core.Trace.in(68); try {
        int n;
        this.mouseYawOffset = 0.0f;
        this.mouseCameraPitch = 0.0f;
        rally3d.desktop.Platform.consumeCameraZoomDelta();
        this.cameraBaseHeightInitialized = false;
        this.racing = true;
        rally3d.desktop.Platform.mpRace = this.race.mode == 2;
        do {
            long simulationStarted = System.nanoTime();
            n = this.updateRace(true);
            rally3d.desktop.Platform.recordPerformance(6, System.nanoTime() - simulationStarted);
            if (n != 0) continue;
            this.renderFrame();
        } while (n == 0);
        this.endSpectate();
        switch (n) {
            case 2: {
                if (this.race.mode >= 0 && this.race.mode <= 1) {
                    this.saveData.savedGameValid[this.race.mode] = 0;
                }
                return 1;
            }
            case -1: {
                return -1;
            }
            case 1: {
                this.racing = false;
                if (this.race.mode == 0 && this.championshipGhost) this.finishGhostRecord();
                if (this.race.mode == 3) {
                    this.finishGhostRecord();
                    this.saveTrackHighScore();
                    this.updateMedals(this.race.trackIndex, this.campaignPlaceForTime(this.race.trackIndex, this.race.raceTime));
                    this.showMessage(rally3d.core.Lang.t("Ghost Race"), this.ghostRecordBeaten
                            ? rally3d.core.Lang.t("New track ghost saved: ") + this.formatTime(this.race.raceTime)
                            : (this.ghostPlayback == null ? rally3d.core.Lang.t("Ghost recording saved: ") : rally3d.core.Lang.t("Finish time: ")) + this.formatTime(this.race.raceTime));
                    return 1;
                }
                this.renderFrame();
                this.showRaceEnd();
                if (this.race.mode >= 0 && this.race.mode <= 1) {
                    this.saveData.savedGameValid[this.race.mode] = 0;
                }
                return 1;
            }
        }
        return 1;
     } finally { rally3d.core.Trace.out(68, $t); } }

    int resumeRace(int n) { /*T*/ final long $t = rally3d.core.Trace.in(69); try {
        this.car.copyFrom(this.saveData.savedCars[n]);
        this.cameraState.copyFrom(this.saveData.savedCameras[n]);
        this.race.copyFrom(this.saveData.savedRaces[n]);
        this.ghostRecorder = null;   // a resumed race has no ghost
        this.ghostPlayback = null;
        this.loadScreen(3, this.race.trackIndex);
        this.prepareRace(true);
        this.showRaceIntro(false);
        this.sound.stop();
        this.setTargetFps(this.initialFps());
        this.paused = false;
        return this.raceLoop();
     } finally { rally3d.core.Trace.out(69, $t); } }

    int startRace() { /*T*/ final long $t = rally3d.core.Trace.in(70); try {
        rally3d.core.RLog.i("race", "startRace: mode=" + this.race.mode + " track=" + this.race.trackIndex + " car=" + this.race.carIndex + " multiplayer=" + (this.bluetooth != null));
        this.race.carIndex = -1;
        do {
            this.loadScreen(2, 0);
            if (this.race.mode == 2) {
                if (this.bluetooth != null) this.bluetooth.addon.forgetRemotes();   // new round: offers of the previous round must not be reused
                this.race.carIndex = this.carMenu(true);
                if (this.race.carIndex == -1) {
                    return -1;
                }
                this.race.trackIndex = this.multiplayerTrackSelect();
                if (this.race.trackIndex == -1) return -1;
                if (!this.isHost && this.race.trackIndex == MapLibrary.MP_MARK && !this.receiveHostMap()) return -1;
                if (this.isHost && this.publishMultiplayerTrack() == -1) return -1;
            } else {
                do {
                    boolean bl = this.race.mode != 1;
                    if (this.race.carIndex == -1) {
                        this.race.trackIndex = this.forcedTrack >= 0 ? this.forcedTrack : this.trackMenu(0, bl);
                        if (this.race.trackIndex == -1) {
                            return 1;
                        }
                    }
                    if (this.ghostOn()) { this.ghostPlayback = null; this.ghostRecordBeaten = false; }
                    boolean pickAgain;
                    do {
                        pickAgain = false;
                        this.race.carIndex = this.carMenu(true);
                        if (this.ghostOn() && this.race.carIndex != -1) {
                            // records are per track+car, but any car's ghost can be raced with any car
                            int choice = this.chooseGhost(this.race.trackIndex, this.race.carIndex);
                            if (choice == -2) pickAgain = true;   // back from the ghost menu -> car menu
                        }
                    } while (pickAgain);
                } while (this.race.carIndex == -1);
            }
            this.race.checkpointsPassed = 0;
            this.loadScreen(3, this.race.trackIndex);
            this.prepareRace(false);
            this.sound.stop();
            this.lastVibrateTime = 0L;
            this.opponentFinishTime = -1;
            this.forward = true;
            this.bannerText = null;
            this.racing = false;
            this.race.raceTime = 0;
            this.introSweep();
            if (this.race.mode == 2) {
                if (!this.syncStart()) return -1;
                if (!this.exchangePlayerInfo()) return -1;
                this.syncAddonCars();
            }
        } while (this.showRaceIntro(true) == -1);
        if (this.race.mode >= 0 && this.race.mode <= 1 && !this.testDrive) {
            this.saveData.savedGameValid[this.race.mode] = 1;
        }
        this.lastOpponentPacketAt = System.nanoTime();
        for (int i = 0; i < 3; ++i) {
            if (i < 2) {
                this.sound.play(1);
            } else {
                this.sound.play(2);
            }
            this.bannerText = Texts.countdown[i];
            this.highFps = i == 2;
            if (i == 2) {
                this.paused = false;
                this.setTargetFps(this.initialFps());
                this.startTimeOffset = (int)(this.currentTime() / 100L);
                if (this.ghostOn()) {
                    this.ghostRecorder = new GhostReplay();
                    this.ghostRecorder.carKey = this.ghostKeyOf(this.race.carIndex);
                    this.ghostRecorder.carName = this.carDef(this.race.carIndex).name;
                    this.ghostFinishMs = 0;
                }
            }
            long l = this.currentTime();
            while (this.currentTime() < l + 1000L) {
                if (i == 2) {
                    this.updateRace(false);
                }
                this.renderFrame();
            }
        }
        this.bannerText = null;
        this.highFps = false;
        return this.raceLoop();
     } finally { rally3d.core.Trace.out(70, $t); } }

    // ---- in-game log overlay (drawn with the game's own font; no separate window) ----
    private int logCacheCount = -1, logCacheFilter = -1, logCacheWidth = -1;
    private final java.util.ArrayList<String> logRows = new java.util.ArrayList<String>();
    private final java.util.ArrayList<Integer> logRowColors = new java.util.ArrayList<Integer>();

    private void rebuildLogRows(javax.microedition.lcdui.Font font, int maxWidth) { /*T*/ final long $t = rally3d.core.Trace.in(71); try {
        logRows.clear(); logRowColors.clear();
        int filter = rally3d.core.LogView.filter;
        for (String line : rally3d.core.RLog.recent(600)) {
            boolean err = line.indexOf(" E [") >= 0, warn = line.indexOf(" W [") >= 0;
            if (filter == 1 && !err && !warn) continue;
            if (filter == 2 && !err) continue;
            int color = err ? -86 : warn ? -1 : -4737097;   // original palette: yellow > white > grey
            for (String part : line.split("\n")) {
                part = part.replace("\t", "  ");
                while (part.length() > 0) {
                    int n = part.length();
                    if (font.stringWidth(part) > maxWidth) { int lo = 1, hi = n; while (lo < hi) { int mid = (lo + hi + 1) >>> 1; if (font.stringWidth(part.substring(0, mid)) <= maxWidth) lo = mid; else hi = mid - 1; } n = lo; }
                    logRows.add(part.substring(0, n)); logRowColors.add(Integer.valueOf(color));
                    part = part.substring(n);
                }
            }
        }
     } finally { rally3d.core.Trace.out(71, $t); } }

    private void drawLogButton(Graphics g, int[] r, String label, boolean on) { /*T*/ final long $t = rally3d.core.Trace.in(72); try {
        // same window look as drawPanel (translucent white, white frame, black outer frame), shadowed yellow / grey text
        this.drawPanel(g, r[0], r[1], Math.max(1, r[2] - 1), Math.max(1, r[3] - 1));
        if (on) g.fillRectAlpha(r[0], r[1], r[2], r[3], 0x55FFFFFF);
        g.setFont(this.fontSmall);
        int tw = this.fontSmall.stringWidth(label);
        this.drawText(g, r[0] + (r[2] - tw) / 2, r[1] + (r[3] - this.fontSmallHeight) / 2, label, on ? -86 : -4737097, 1);
     } finally { rally3d.core.Trace.out(72, $t); } }

    void drawLogOverlay(Graphics g) { /*T*/ final long $t = rally3d.core.Trace.in(73); try {
        int w = this.width, h = this.height;
        rally3d.core.LogView.mpRace = this.screenMode == 3 && this.race != null && this.race.mode == 2;
        rally3d.core.LogView.menuRowLogical = this.fontBoldHeight + 2;
        int[] r = new int[4];
        if (rally3d.core.LogView.showLogButton) {
            rally3d.core.LogView.rect(rally3d.core.LogView.BTN_LOG, w, h, r);
            this.drawLogButton(g, r, "LOG", rally3d.core.LogView.visible);
        }
        if (rally3d.core.LogView.mpRace) {
            rally3d.core.LogView.rect(rally3d.core.LogView.BTN_TAB, w, h, r);
            this.drawLogButton(g, r, "TAB", rally3d.core.LogView.tabHeld || this.isTabDown());
        }
        if (!rally3d.core.LogView.visible) return;
        javax.microedition.lcdui.Font font = this.fontSmall;
        int fh = this.fontSmallHeight + 1;
        rally3d.core.LogView.rowLogical = fh;
        int left = w / 20, top = (int) (h * 0.2f), right = w - w / 20, bottom = h - h / 40;
        g.fillRectAlpha(left, top, right - left, bottom - top, 0xC8000000);   // dark for readability, framed like drawPanel
        g.setColor(0xFFFFFF);
        this.drawRect(g, left, top, right - left - 1, bottom - top - 1);
        g.setColor(0);
        this.drawRect(g, left - 1, top - 1, right - left + 1, bottom - top + 1);
        g.setFont(font);
        String[] names = {"ALL", "WARN+ERR", "ERR"};
        int headH = rally3d.core.LogView.headerH(h, fh);
        if (rally3d.core.LogView.exportButton) {
            String head = rally3d.core.Lang.t("LOG [") + names[rally3d.core.LogView.filter] + rally3d.core.Lang.t("]  top: filter | drag: scroll");
            rally3d.core.LogView.exportRect(w, h, r);
            int maxHead = r[0] - left - 8;
            while (head.length() > 4 && font.stringWidth(head) > maxHead) head = head.substring(0, head.length() - 1);
            this.drawText(g, left + 3, top + (headH - this.fontSmallHeight) / 2, head, -86, 1);
            this.drawLogButton(g, r, rally3d.core.Lang.t("EXPORT"), false);
            g.setFont(font);
        } else {
            this.drawText(g, left + 3, top + 2, rally3d.core.Lang.t("LOG [") + names[rally3d.core.LogView.filter] + rally3d.core.Lang.t("]  top: filter  |  scroll: wheel / drag  (F1)"), -86, 1);
        }
        int count = rally3d.core.RLog.count();
        int textWidth = right - left - 6;
        if (count != logCacheCount || rally3d.core.LogView.filter != logCacheFilter || textWidth != logCacheWidth) {
            this.rebuildLogRows(font, textWidth);
            logCacheCount = count; logCacheFilter = rally3d.core.LogView.filter; logCacheWidth = textWidth;
        }
        int bodyTop = top + headH;
        int maxRows = Math.max(1, (bottom - bodyTop - 2) / fh);
        int maxScroll = Math.max(0, logRows.size() - maxRows);
        if (rally3d.core.LogView.scroll > maxScroll) rally3d.core.LogView.scroll = maxScroll;
        if (rally3d.core.LogView.scroll < 0) rally3d.core.LogView.scroll = 0;
        int start = Math.max(0, logRows.size() - maxRows - rally3d.core.LogView.scroll);
        int y = bodyTop;
        for (int i = start; i < logRows.size() && i < start + maxRows; i++) {
            this.drawText(g, left + 3, y, logRows.get(i), logRowColors.get(i).intValue(), 1);
            y += fh;
        }
     } finally { rally3d.core.Trace.out(73, $t); } }

    void renderFrame() { /*T*/ final long $t = rally3d.core.Trace.in(74); try {
        while (rally3d.desktop.Platform.isBackground()) { this.sleep(100); }
        long frameRenderStarted = System.nanoTime();
        this.width = this.getWidth();
        this.height = this.getHeight();
        switch (this.screenMode) {
            case 2: {
                this.drawMenu(this.graphics);
                if (this.mainMenuTag) {
                    this.graphics.setFont(this.fontSmall);
                    this.drawText(this.graphics, 2, this.height - this.fontSmallHeight - 1, "v" + rally3d.core.Version.NAME, -1, 1);
                }
                break;
            }
            case 3: {
                this.drawWorld(this.graphics);
                if (this.flyView != null) this.flyView.drawHud(this.graphics);
                break;
            }
            case 4: {
                this.drawInfoScreen(this.graphics);
                break;
            }
            case 5: {
                this.drawLoading(this.graphics);
                break;
            }
            case 6: {
                this.drawMessage(this.graphics);
                break;
            }
            case 7: {
                this.drawFps(this.graphics);
            }
        }
        this.drawLogOverlay(this.graphics);
        this.flushGraphics();
        rally3d.desktop.Platform.recordPerformance(8, System.nanoTime() - frameRenderStarted);
        this.updateFps();
        this.waitFrame();
     } finally { rally3d.core.Trace.out(74, $t); } }

    void loadScreen(int n, int n2) { /*T*/ final long $t = rally3d.core.Trace.in(75); try {
        if (this.loadedScreen == n) {
            return;
        }
        this.screenMode = -1;
        this.loadedScreen = -1;
        this.titleImage = null;
        this.titleTextImage = null;
        this.segments = null;
        this.roadMeshes = null;
        this.roadTexture = null;
        this.billboards = null;
        this.skyImage = null;
        RoadSegmentMesh.release();
        System.gc();
        switch (n) {
            case 1: {
                this.sound.loadTracks(0);
                this.sound.setVolume(this.saveData.soundLevel * 99 / 3);
                this.titleUpImage = RallyGame.loadImage("/title/title_up.png");
                this.titleDownImage = RallyGame.loadImage("/title/title_down.png");
                this.barHeight = this.titleDownImage.getHeight();
                this.cupImages[0] = RallyGame.loadImage("/cup/1.png");
                this.cupImages[1] = RallyGame.loadImage("/cup/2.png");
                this.cupImages[2] = RallyGame.loadImage("/cup/3.png");
                this.cupImages[3] = RallyGame.loadImage("/cup/4.png");
                this.cupImages[4] = RallyGame.loadImage("/cup/5.png");
                this.arrowLeft[0] = RallyGame.loadImage("/arrows/l1.png");
                this.arrowLeft[1] = RallyGame.loadImage("/arrows/l2.png");
                this.arrowRight[0] = RallyGame.loadImage("/arrows/r1.png");
                this.arrowRight[1] = RallyGame.loadImage("/arrows/r2.png");
                this.arrowWidth = this.arrowLeft[0].getWidth();
                this.arrowHeight = this.arrowLeft[0].getHeight();
                this.loadCarModels();
                break;
            }
            case 2: {
                this.init3D(false);
                this.setCarsDepthDisabled(false);
                this.titleImage = RallyGame.loadImage("/title/title.png");
                this.titleTextImage = RallyGame.loadImage("/title/title_text.png");
                this.screenMode = 2;
                break;
            }
            case 3: {
                this.setLoading(5);
                RoadSegmentMesh.setDetail(3);
                this.setLoading(50);
                this.loadTrack(n2);
                this.setLoading(60);
                // Depth-test the car body against all four wheels in racing view.
                this.setCarsDepthDisabled(false);
                this.setLoading(100);
                this.screenMode = 3;
                this.init3D(true);
            }
        }
        this.loadedScreen = n;
     } finally { rally3d.core.Trace.out(75, $t); } }

    void drawLoading(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(76); try {
        int n = this.loadingPercent * (this.width - 4) / 100;
        int n2 = this.height - 8;
        this.drawText(graphics, 3, n2 - this.fontBoldHeight - 3, Texts.loading, -86, 1);
        graphics.setColor(0);
        graphics.fillRect(2, n2 + 1, n, 3);
        graphics.setColor(-86);
        graphics.fillRect(1, n2, n, 3);
     } finally { rally3d.core.Trace.out(76, $t); } }

    void setLoading(int n) { /*T*/ final long $t = rally3d.core.Trace.in(77); try {
        int n2 = this.screenMode;
        this.screenMode = 5;
        this.loadingPercent = n;
        this.renderFrame();
        this.screenMode = n2;
     } finally { rally3d.core.Trace.out(77, $t); } }

    void drawMenu(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(78); try {
        block36: {
            int n;
            block31: {
                int n2;
                graphics.setFont(this.fontBold);
                if (this.showTitleBackground == 1) {
                    // title picture at its native size, never stretched: centred, checker around it; if the window is shorter than the picture, crop around the car (~63 % of its height)
                    this.fillChecker(graphics, 0, 0, this.width, this.height);
                    int ih = this.titleImage.getHeight();
                    int ty = ih <= this.height ? (this.height - ih) / 2
                            : Math.max(this.height - ih, Math.min(0, -Math.round(ih * 0.63f - this.height / 2.0f)));
                    this.drawWide(graphics, this.titleImage, ty);
                } else if (this.redrawBackground) {
                    this.drawWorld(graphics);
                    graphics.setClip(0, 0, this.width, this.height);
                    this.redrawBackground = false;
                }
                if (this.addonUi != null) {
                    this.addonUi.draw(graphics);
                    break block36;
                }
                if (this.mapEditor != null) {
                    this.mapEditor.draw(graphics);
                    break block36;
                }
                if (this.carEditor != null) {
                    this.carEditor.draw(graphics);
                    break block36;
                }
                if (this.showCarPreview != 0) {
                    long now = System.nanoTime();
                    if (this.carPreviewLastNanos == 0L) this.carPreviewLastNanos = now;
                    long elapsed = Math.max(0L, Math.min(100_000_000L, now - this.carPreviewLastNanos));
                    this.carPreviewLastNanos = now;
                    // The car always turns on its own (45 deg/s): it must not depend on the "UI animations" / vanilla settings.
                    this.carPreviewYaw = (this.carPreviewYaw + elapsed * 0.000000045f) % 360.0f;
                    this.drawCarPreview(graphics);
                }
                if (this.showTextBlock != 0) {
                    this.drawTextBlock(graphics, -86);
                }
                if (this.menuHeaderStatic) {
                    this.drawWide(graphics, this.titleUpImage, 0);
                } else {
                    this.drawWide(graphics, this.titleUpImage, -this.menuSlide);
                }
                this.drawWide(graphics, this.titleDownImage, this.height - this.barHeight + this.menuSlide);
                if (this.hideTitleText == 0) {
                    if (this.menuTitle1 != null) {
                        if (this.menuTitle2 != null) {
                            n = (this.barHeight - this.fontBoldHeight * 2) / 2 - this.menuSlide;
                            this.drawText(graphics, 0, n, this.menuTitle1, -86, 5);
                            this.drawText(graphics, 0, n += this.fontBoldHeight, this.menuTitle2, -86, 5);
                        } else {
                            n = (this.barHeight - this.fontBoldHeight) / 2 - this.menuSlide;
                            this.drawText(graphics, 0, n, this.menuTitle1, -86, 5);
                        }
                    } else if (this.titleTextImage != null) {
                        n = this.menuHeaderStatic ? (this.barHeight - this.titleTextImage.getHeight()) / 2 : (this.barHeight - this.titleTextImage.getHeight()) / 2 - this.menuSlide;
                        graphics.drawImage(this.titleTextImage, (this.width - this.titleTextImage.getWidth()) / 2, n, 0);
                    }
                }
                int n3 = -this.menuSlide * 6;
                if (this.menuStyle != 0) break block31;
                n = this.menuListY;
                for (int i = 0; i < this.visibleRows; ++i) {
                    block32: {
                        int n4;
                        int n5;
                        block35: {
                            block34: {
                                block33: {
                                    n2 = this.menuDisabled[i + this.scrollOffset] ? -4737097 : -86;
                                    if (!this.showArrows || this.showTextBlock != 0 || this.selectedIndex != i + this.scrollOffset || this.menuSlide != 0) break block32;
                                    n5 = this.arrowBlink < 3 ? 1 : 0;
                                    n4 = n + (this.fontBoldHeight - this.arrowLeft[0].getHeight()) / 2;
                                    if (!this.hasMenuIcons) break block33;
                                    if (Texts.rightToLeft) break block34;
                                }
                                graphics.drawImage(this.arrowRight[n5], 1, n4, 0);
                            }
                            if (!this.hasMenuIcons) break block35;
                            if (!Texts.rightToLeft) break block32;
                        }
                        graphics.drawImage(this.arrowLeft[n5], this.width - this.arrowLeft[0].getWidth() - 1, n4, 0);
                    }
                    if (this.menuIcons[i + this.scrollOffset] != null) {
                        if (Texts.rightToLeft) {
                            graphics.drawImage(this.menuIcons[i + this.scrollOffset], n3, n + (this.fontBoldHeight - this.menuIcons[i + this.scrollOffset].getHeight()) / 2, 0);
                            this.drawText(graphics, 1 + this.arrowWidth + 1 + n3, n, this.menuItems[i + this.scrollOffset], n2, 9);
                        } else {
                            graphics.drawImage(this.menuIcons[i + this.scrollOffset], this.width - this.menuIcons[i + this.scrollOffset].getWidth() - n3 - 1, n + (this.fontBoldHeight - this.menuIcons[i + this.scrollOffset].getHeight()) / 2, 0);
                            this.drawText(graphics, 1 + this.arrowWidth + 1 + n3, n, this.menuItems[i + this.scrollOffset], n2, 1);
                        }
                    } else {
                        this.drawText(graphics, n3, n, this.menuItems[i + this.scrollOffset], n2, 5);
                        n3 = -n3;
                    }
                    n += this.fontBoldHeight + 2;
                }
                if (this.showLeftSoftkey) {
                    n2 = this.softkeyPressTimer > 0 && this.softkeyPressed == 1 ? -4737097 : -86;
                    if (this.menuDisabled[this.selectedIndex]) {
                        n2 = -4737097;
                    }
                    this.drawText(graphics, -5, this.softkeyY + this.menuSlide, this.leftSoftkeyLabel, n2, 5);
                }
                if (this.showRightSoftkey) {
                    n2 = this.softkeyPressTimer > 0 && this.softkeyPressed == 2 ? -4737097 : -86;
                    this.drawText(graphics, 4, this.softkeyY + this.menuSlide, this.rightSoftkeyLabel, n2, 9);
                }
                break block36;
            }
            if (this.menuStyle == 1) {
                n = this.softkeyY + this.menuSlide;
                int n6 = -86;
                if (this.menuDisabled[this.selectedIndex]) {
                    n6 = -4737097;
                }
                int n7 = this.arrowBlink < 3 ? 1 : 0;
                int n8 = n + (this.fontBoldHeight - this.arrowLeft[0].getHeight()) / 2;
                graphics.drawImage(this.arrowLeft[n7], 1, n8, 0);
                graphics.drawImage(this.arrowRight[n7], this.width - this.arrowLeft[0].getWidth() - 1, n8, 0);
                this.drawText(graphics, 0, n, this.menuItems[this.selectedIndex], n6, 5);
            }
        }
     } finally { rally3d.core.Trace.out(78, $t); } }

    /** Logs every menu that is shown and what the user picked (wrapper; real loop is runMenuInner). */
    int runMenu(int n) { /*T*/
        String title = this.menuTitle1 != null ? this.menuTitle1 : "(main/untitled)";
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < this.menuItemCount && i < 12; i++) items.append(i == 0 ? "" : " | ").append(this.menuItems[i]);
        rally3d.core.RLog.i("menu", "open '" + title + "' [" + this.menuItemCount + " items: " + items + "] preselect=" + n);
        long t0 = System.currentTimeMillis();
        int r = this.runMenuInner(n);
        String label = "back/none";
        for (int i = 0; i < this.menuItemCount; i++) if (this.menuValues[i] == r && r != -1) { label = this.menuItems[i]; break; }
        rally3d.core.RLog.i("menu", "close '" + title + "' -> action " + r + " (" + label + ") after " + (System.currentTimeMillis() - t0) + " ms");
        return r;
    }

    int runMenuInner(int n) { /*T*/ final long $t = rally3d.core.Trace.in(79); try {
        int n2 = -1;
        int n3 = 0;
        boolean bl = false;
        this.redrawBackground = true;
        boolean animateMenu = rally3d.desktop.Platform.areUiAnimationsEnabled() && this.menuSlideEnabled != 0;
        if (!animateMenu) {
            this.menuSlide = 0;
        }
        this.visibleRows = (this.height - this.barHeight - this.barHeight) / (this.fontBoldHeight + 2);
        if (this.menuItemCount < this.visibleRows) {
            this.visibleRows = this.menuItemCount;
        }
        this.menuListY = (this.height - this.visibleRows * (this.fontBoldHeight + 2)) / 2;
        this.arrowBlink = 0;
        this.scrollOffset = 0;
        this.softkeyY = this.height - this.barHeight + (this.barHeight - (this.fontBoldHeight + 2)) / 2;
        this.selectedIndex = 0;
        for (int i = 0; i < this.menuItemCount; ++i) {
            if (this.menuValues[i] != n) continue;
            this.selectedIndex = i;
        }
        if (this.startTextEditingOnMenuOpen && this.menuTextEditable[this.selectedIndex]) {
            this.beginMenuTextEdit(this.selectedIndex);
            this.startTextEditingOnMenuOpen = false;
        }
        if (this.menuTimeoutOn) {
            this.menuTimeout += this.currentTime();
        }
        int n4 = this.screenMode;
        this.screenMode = 2;
        this.snapshotKeys();
        int[] pointer = new int[4];
        boolean hoverScrollFrozen = false;
        this.pollPointerEvents(pointer); // discard clicks made before this menu became active
        while (true) {
            if (this.softkeyPressTimer > 0) {
                --this.softkeyPressTimer;
            }
            if (!bl) {
                if (animateMenu && this.menuSlide > 0) {
                    this.menuSlide -= 4;
                    if (this.menuSlide < 0) {
                        this.menuSlide = 0;
                    }
                }
            } else if (animateMenu && this.softkeyPressTimer == 0 && this.menuSlide < this.barHeight) {
                this.menuSlide += 4;
            }
            if (this.menuStyle == 0 && !hoverScrollFrozen) {
                // while typing, keep the edited row near the top so the soft keyboard never covers it
                this.scrollOffset = this.textEditing ? this.selectedIndex : this.selectedIndex - this.visibleRows / 2;
                if (this.scrollOffset < 0) {
                    this.scrollOffset = 0;
                }
                if (this.scrollOffset > this.menuItemCount - this.visibleRows) {
                    this.scrollOffset = this.menuItemCount - this.visibleRows;
                }
                n2 = this.scrollOffset;
            }
            this.renderFrame();
            if (this.textEditing && this.editingMenuIndex >= 0) {
                String cursor = this.currentTime() % 900L < 450L ? "|" : " ";
                this.menuItems[this.editingMenuIndex] = this.menuTextPrefix[this.editingMenuIndex] + this.editBuffer + cursor;
            }
            if (animateMenu) this.arrowBlink = (this.arrowBlink + 1) % 6;
            else this.arrowBlink = 0;
            int n5 = this.pollNewKeys();
            int keyboardInput = n5;
            int pointerFlags = this.pollPointerEvents(pointer);
            if (this.menuStyle == 0) {
                if ((pointerFlags & 1) != 0 && pointer[1] >= this.menuListY
                        && pointer[1] < this.menuListY + this.visibleRows * (this.fontBoldHeight + 2)) {
                    int row = (pointer[1] - this.menuListY) / (this.fontBoldHeight + 2);
                    int item = row + this.scrollOffset;
                    if (row >= 0 && row < this.visibleRows && item < this.menuItemCount) {
                        this.selectedIndex = item;
                        hoverScrollFrozen = true;
                    }
                }
                if ((pointerFlags & 4) != 0) {
                    int wheel = this.consumePointerWheel();
                    int maxOffset = Math.max(0, this.menuItemCount - this.visibleRows);
                    if (wheel != 0 && maxOffset > 0) {
                        this.scrollOffset = Math.max(0, Math.min(maxOffset, this.scrollOffset + wheel));
                        this.selectedIndex = Math.max(this.scrollOffset,
                                Math.min(this.selectedIndex, this.scrollOffset + this.visibleRows - 1));
                        hoverScrollFrozen = true;
                    }
                }
                if ((pointerFlags & 2) != 0) {
                    int clickY = pointer[3];
                    if (clickY >= this.menuListY && clickY < this.menuListY + this.visibleRows * (this.fontBoldHeight + 2)) {
                        int row = (clickY - this.menuListY) / (this.fontBoldHeight + 2);
                        int item = row + this.scrollOffset;
                        if (row >= 0 && row < this.visibleRows && item < this.menuItemCount) {
                            this.selectedIndex = item;
                            if (!this.menuDisabled[item]) n5 |= 0x100;
                        }
                    } else if (clickY >= this.softkeyY) {
                        if (pointer[2] < this.width / 2 && this.showLeftSoftkey && !this.menuDisabled[this.selectedIndex]) n5 |= 0x100;
                        else if (pointer[2] >= this.width / 2 && this.showRightSoftkey) n5 |= 0x200;
                    }
                }
            } else if ((pointerFlags & 2) != 0) {
                int clickX = pointer[2];
                int arrowZone = Math.max(this.arrowWidth * 3, this.width / 5);
                if (clickX < arrowZone) n5 |= 6;
                else if (clickX >= this.width - arrowZone) n5 |= 0x60;
                else n5 |= 0x100;
            }
            boolean committedTextField = false;
            if (this.textEditing) {
                // A click or wheel event cannot confirm/cancel an edit; only Enter/Escape keys can.
                n5 = keyboardInput;
                int textCount = this.consumeTextInput(this.menuTextInputEvents);
                for (int ti = 0; ti < textCount; ti++) {
                    int value = this.menuTextInputEvents[ti];
                    if (value == javax.microedition.lcdui.Canvas.TEXT_BACKSPACE) {
                        if (!this.editBuffer.isEmpty()) this.editBuffer = this.editBuffer.substring(0, this.editBuffer.length() - 1);
                    } else if (!Character.isISOControl(value) && this.editBuffer.length() < this.menuTextLimit[this.editingMenuIndex]) {
                        this.editBuffer += (char)value;
                    }
                }
                boolean commit = (n5 & 0x100) != 0;
                boolean cancel = (n5 & 0x200) != 0;
                // While editing, arrows, shortcuts, mouse clicks and wheel input never navigate the menu.
                n5 = 0;
                if (commit) {
                    int edited = this.editingMenuIndex;
                    this.menuTextValue[edited] = this.editBuffer;
                    this.lastMenuInputValue = this.editBuffer;
                    this.menuItems[edited] = this.menuTextPrefix[edited] + this.editBuffer;
                    this.textEditing = false;
                    this.editingMenuIndex = -1;
                    this.setTextEntryMode(false);
                    committedTextField = true;
                    n5 = 0x100;
                } else if (cancel) {
                    int edited = this.editingMenuIndex;
                    this.menuItems[edited] = this.menuTextPrefix[edited] + this.menuTextValue[edited];
                    this.editBuffer = this.menuTextValue[edited];
                    this.textEditing = false;
                    this.editingMenuIndex = -1;
                    this.setTextEntryMode(false);
                    bl = true;
                    n3 = -1;
                }
            } else {
                this.consumeTextInput(EMPTY_TEXT_INPUT);
            }
            // Desktop-style vertical navigation for controller D-pads and sticks.
            // Keep legacy horizontal navigation available as well.
            if (!this.textEditing) {
                if ((n5 & 0x2) != 0) n5 |= 0x6;
                if ((n5 & 0x40) != 0) n5 |= 0x60;
            }
            if (n5 != 0) {
                this.arrowBlink = 0;
                this.redrawBackground = true;
                if ((n5 & (6 | 0x60)) != 0) hoverScrollFrozen = false;
            }
            this.selectedIndex += this.menuItemCount;
            if ((n5 & 6) != 0) {
                --this.selectedIndex;
            }
            if ((n5 & 0x60) != 0) {
                ++this.selectedIndex;
            }
            this.selectedIndex %= this.menuItemCount;
            if (!bl) {
                if (this.showLeftSoftkey && (n5 & 0x100) != 0 && !this.menuDisabled[this.selectedIndex]) {
                    if (this.menuTextEditable[this.selectedIndex] && !committedTextField) {
                        this.beginMenuTextEdit(this.selectedIndex);
                        n5 = 0;
                    } else {
                    bl = true;
                    n3 = this.menuValues[this.selectedIndex];
                    this.softkeyPressTimer = animateMenu ? 4 : 0;
                    this.softkeyPressed = 1;
                    this.menuHeaderStatic = false;
                    if (!animateMenu) this.menuSlide = this.barHeight;
                    }
                }
                if (this.showRightSoftkey && (n5 & 0x200) != 0) {
                    bl = true;
                    n3 = -1;
                    this.softkeyPressTimer = animateMenu ? 4 : 0;
                    this.softkeyPressed = 2;
                    if (this.menuExitMode != -1) {
                        this.menuExitMode = 1;
                    }
                    this.menuHeaderStatic = false;
                    if (!animateMenu) this.menuSlide = this.barHeight;
                }
            }
            if (this.menuTimeoutOn && this.menuTimeout < this.currentTime()) {
                bl = true;
                n3 = -1;
                this.softkeyPressTimer = 1;
                this.softkeyPressed = 1;
                this.menuHeaderStatic = false;
            }
            if (!bl) {
                switch (this.menuWaitMode) {
                    default: {
                        break;
                    }
                    case 1: {
                        if (!this.bluetooth.isHostDone()) break;
                        n3 = this.bluetooth.isHostConnected() ? 1 : -1;
                        bl = true;
                        break;
                    }
                    case 2: {
                        if (!this.bluetooth.isInquiryDone()) break;
                        n3 = this.bluetooth.hasDevices() ? 1 : -1;
                        bl = true;
                        break;
                    }
                    case 3: {
                        if (!this.bluetooth.isClientDone()) break;
                        n3 = this.bluetooth.isClientConnected() ? 1 : -1;
                        bl = true;
                        break;
                    }
                    case 4: {
                        if (!this.pollTrackChoice()) break;
                        n3 = this.btTrackChoice;
                        bl = true;
                    }
                }
            }
            if (bl && (!animateMenu || this.menuSlide >= this.barHeight || this.menuExitMode <= 0)) {
                this.setTextEntryMode(false);
                this.screenMode = n4;
                return n3;
            }
            if (this.sound.getPlayState() == -1) {
                this.sound.play(3);
            }
            if (!this.paused) continue;
            this.paused = false;
            this.clearKeys();
        }
     } finally { rally3d.core.Trace.out(79, $t); } }

    void setMenuTimeout(long l) {
        this.menuTimeoutOn = true;
        this.menuTimeout = l;
    }

    void addMenuItem(String string, int n) {
        this.addMenuEntry(string, null, n, false);
    }

    void addTextFieldMenuItem(String prefix, String value, int action, int maxLength) { /*T*/ final long $t = rally3d.core.Trace.in(80); try {
        String safeValue = value == null ? "" : value;
        int index = this.menuItemCount;
        this.addMenuEntry(prefix + safeValue, null, action, false);
        if (index < this.menuItemCount) {
            this.menuTextEditable[index] = true;
            this.menuTextPrefix[index] = prefix;
            this.menuTextValue[index] = safeValue;
            this.menuTextLimit[index] = Math.max(1, maxLength);
        }
     } finally { rally3d.core.Trace.out(80, $t); } }

    private void beginMenuTextEdit(int index) { /*T*/ final long $t = rally3d.core.Trace.in(81); try {
        if (index < 0 || index >= this.menuItemCount || !this.menuTextEditable[index]) return;
        this.editingMenuIndex = index;
        this.editBuffer = this.menuTextValue[index] == null ? "" : this.menuTextValue[index];
        this.lastMenuInputValue = null;
        this.textInputCancelled = false;
        this.textEditing = true;
        this.setTextEntryMode(true);
        this.clearKeys();
        this.consumeTextInput(EMPTY_TEXT_INPUT);
     } finally { rally3d.core.Trace.out(81, $t); } }

    String editTextInGame(String title, String prefix, String initial, int maxLength) { /*T*/ final long $t = rally3d.core.Trace.in(82); try {
        this.beginMenu(title);
        this.menuSlideEnabled = 0;
        this.addTextFieldMenuItem(prefix, initial, 0, maxLength);
        this.startTextEditingOnMenuOpen = true;
        int result = this.runMenu(0);
        this.startTextEditingOnMenuOpen = false;
        return result < 0 ? null : this.lastMenuInputValue;
     } finally { rally3d.core.Trace.out(82, $t); } }

    void addIconMenuItem(String string, Image image, int n, boolean bl) {
        this.addMenuEntry(string, image, n, bl);
    }

    void addMenuEntry(String string, Image image, int n, boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(83); try {
        if (this.menuItemCount < this.menuItems.length) {
            this.menuItems[this.menuItemCount] = string;
            this.menuIcons[this.menuItemCount] = image;
            this.menuValues[this.menuItemCount] = n;
            this.menuDisabled[this.menuItemCount] = bl;
            ++this.menuItemCount;
            if (image != null) {
                this.hasMenuIcons = true;
            }
        }
     } finally { rally3d.core.Trace.out(83, $t); } }

    void beginMenu(String string) { /*T*/ final long $t = rally3d.core.Trace.in(84); try {
        this.mainMenuTag = false;
        if (this.menuItems == null) {
            this.menuItems = new String[64];
            this.menuTextEditable = new boolean[64];
            this.menuTextPrefix = new String[64];
            this.menuTextValue = new String[64];
            this.menuTextLimit = new int[64];
            this.menuValues = new int[64];
            this.menuIcons = new Image[64];
            this.menuDisabled = new boolean[64];
        }
        this.showLeftSoftkey = true;
        this.leftSoftkeyLabel = Texts.select;
        this.showRightSoftkey = true;
        this.rightSoftkeyLabel = Texts.back;
        this.menuSlide = rally3d.desktop.Platform.areUiAnimationsEnabled() ? this.barHeight : 0;
        this.softkeyPressTimer = 0;
        this.softkeyPressed = 0;
        this.showCarPreview = 0;
        this.showTextBlock = 0;
        this.menuItemCount = 0;
        this.textEditing = false;
        this.setTextEntryMode(false);
        this.editingMenuIndex = -1;
        this.startTextEditingOnMenuOpen = false;
        this.lastMenuInputValue = null;
        this.textInputCancelled = false;
        for (int i = 0; i < this.menuTextEditable.length; i++) {
            this.menuTextEditable[i] = false;
            this.menuTextPrefix[i] = "";
            this.menuTextValue[i] = "";
            this.menuTextLimit[i] = 0;
        }
        this.menuStyle = 0;
        this.hideTitleText = 0;
        this.menuSlideEnabled = 1;
        this.menuExitMode = 1;
        this.showTitleBackground = 1;
        this.menuTimeoutOn = false;
        this.menuWaitMode = 0;
        this.showArrows = true;
        this.menuTitle1 = string;
        this.menuTitle2 = null;
        if (string != null && this.fontBold.stringWidth(string) >= this.width) {
            this.layoutText(0, 0, this.width - 1, this.height, string, true);
            this.menuTitle1 = this.textLines[0];
            this.menuTitle2 = this.textLines[1];
        }
        this.hasMenuIcons = false;
     } finally { rally3d.core.Trace.out(84, $t); } }

    void drawTextBlock(Graphics graphics, int n) {
        int n2;
        int n3 = this.textTop + this.textOffsetY;
        if (this.menuSlide != 0) {
            return;
        }
        for (int i = 0; i < this.visibleTextLines; ++i) {
            int n4 = 4;
            int n5 = 0;
            int n6 = n;
            if (this.rainbowText) {
                n4 = 0;
                if (Texts.rightToLeft) {
                    n4 = 8;
                    n5 = 4;
                }
                n6 = this.rainbowColors[(this.rainbowPhase + i) % 10];
            }
            this.drawText(graphics, n5, n3 + 1, this.textLines[this.textScroll + i], n6, n4 | 1);
            n3 += this.fontBoldHeight;
        }
        if (rally3d.desktop.Platform.areUiAnimationsEnabled()) ++this.rainbowPhase;
        if (this.totalTextLines > this.visibleTextLines) {
            int n7 = this.textBottom - this.textTop;
            int n8 = n7 * this.textScroll / this.totalTextLines;
            int n9 = n7 * (this.textScroll + this.visibleTextLines) / this.totalTextLines;
            graphics.setColor(n);
            graphics.fillRect(this.textRight + 2, this.textTop + n8, 2, n9 - n8);
        }
        int textWheel = this.consumePointerWheel();
        if (textWheel != 0) {
            this.textScroll = Math.max(0, Math.min(Math.max(0, this.totalTextLines - this.visibleTextLines), this.textScroll + textWheel));
        }
        if (((n2 = this.readKeys()) & 2) != 0 && this.textScroll > 0) {
            --this.textScroll;
        }
        if ((n2 & 0x40) != 0 && this.textScroll < this.totalTextLines - this.visibleTextLines) {
            ++this.textScroll;
        }
    }

    int layoutText(int n, int n2, int n3, int n4, String string, boolean bl) {
        int n5;
        this.rainbowText = false;
        this.textLeft = n;
        this.textTop = n2;
        this.textRight = n3;
        this.textBottom = n4;
        this.textLines = new String[2048];   // long help texts (Instructions) need far more than the original 128 lines
        this.lineWidths = new int[2048];
        this.lineLengths = new int[2048];
        int n6 = this.textRight - this.textLeft;
        this.visibleTextLines = (this.textBottom - this.textTop) / this.fontBoldHeight;
        this.totalTextLines = 0;
        while ((n5 = this.wrapLine(string, n6)) > 0) {
            string = string.substring(n5);
        }
        if (this.visibleTextLines > this.totalTextLines) {
            this.visibleTextLines = this.totalTextLines;
        }
        this.textScroll = 0;
        if (Texts.rightToLeft && bl) {
            int n7 = 0;
            for (int i = this.totalTextLines - 1; n7 < i; ++n7, --i) {
                String string2 = this.textLines[n7];
                this.textLines[n7] = this.textLines[i];
                this.textLines[i] = string2;
                int n8 = this.lineWidths[n7];
                this.lineWidths[n7] = this.lineWidths[i];
                this.lineWidths[i] = n8;
                n8 = this.lineLengths[n7];
                this.lineLengths[n7] = this.lineLengths[i];
                this.lineLengths[i] = n8;
            }
        }
        this.textOffsetY = (this.textBottom - this.textTop - this.visibleTextLines * this.fontBoldHeight) / 2;
        return this.totalTextLines - this.visibleTextLines;
    }

    int wrapLine(String string, int n) {
        int n2 = 0;
        int n3 = -1;
        int n4 = 0;
        int n5 = 0;
        while (true) {
            char c;
            if ((c = n2 >= string.length() ? (char)'\u0000' : string.charAt(n2)) == ' ' || c == '\u0000' || c == '|' || c == '-' || c == '\n') {
                String string2 = string.substring(0, n2);
                int n6 = this.fontBold.stringWidth(string2);
                if (n6 <= n) {
                    if (c == '\u0000' || c == '|' || c == '\n') {
                        if (this.totalTextLines < this.textLines.length) {
                            this.textLines[this.totalTextLines] = string.substring(0, n2);
                            this.lineLengths[this.totalTextLines] = n2;
                            this.lineWidths[this.totalTextLines] = n6;
                            ++this.totalTextLines;
                        }
                        if (c == '\u0000') {
                            return -1;
                        }
                        return n2 + 1;
                    }
                    n3 = n2;
                    n4 = n6;
                } else {
                    String string3 = null;
                    if (n3 == -1) {
                        n3 = n2;
                        do {
                            string3 = string2.substring(0, n3);
                            n4 = this.fontBold.stringWidth(string3);
                            --n3;
                        } while (n4 > n);
                    }
                    n5 = 1;
                    if (this.totalTextLines < this.textLines.length) {
                        this.textLines[this.totalTextLines] = string.substring(0, n3);
                        this.lineLengths[this.totalTextLines] = n3;
                        this.lineWidths[this.totalTextLines] = n4;
                        ++this.totalTextLines;
                    }
                    return n3 + 1 - n5;
                }
            }
            ++n2;
        }
    }

    void showTextScreen(String string, String string2, long l, int n) { /*T*/ final long $t = rally3d.core.Trace.in(85); try {
        this.layoutText(2, this.barHeight + 2, this.width - 5, this.height - this.barHeight - 3, string2, true);
        this.beginMenu(string);
        this.addMenuItem("", 0);
        if (n == 0) {
            this.showLeftSoftkey = false;
        } else {
            this.leftSoftkeyLabel = Texts.ok;
            this.rightSoftkeyLabel = "";
        }
        this.showTextBlock = 1;
        if (l > 0L) {
            this.setMenuTimeout(l);
        }
        this.runMenu(0);
     } finally { rally3d.core.Trace.out(85, $t); } }

    void showHighScoreTable(int n) { /*T*/ final long $t = rally3d.core.Trace.in(86); try {
        int n2;
        String string = "";
        for (int i = 0; i < 5; ++i) {
            String[] stringArray = new String[1];
            String[] stringArray2 = new String[]{"" + (i + 1), this.saveData.bestNames[i][this.highScoreTrack]};
            string = string + Localization.getFormatted(31, stringArray2);
            string = string + "|";
            stringArray[0] = this.formatTime(this.saveData.bestTimes[i][this.highScoreTrack]);
            string = Texts.rightToLeft ? string + "" + stringArray[0] + " " + Localization.getString(32) : string + Localization.getString(32) + " " + stringArray[0];
            if (i >= 4) continue;
            string = string + "|";
        }
        do {
            this.layoutText(2, this.barHeight + 2, this.width - 5, this.height - this.barHeight - 3, string, false);
            this.beginMenu(Texts.highScoresTitle);
            this.addMenuItem("", 0);
            this.leftSoftkeyLabel = Texts.clear;
            this.showTextBlock = 1;
            this.rainbowText = true;
            n2 = this.runMenu(0);
            if (n2 != 0) continue;
            if (!this.confirm(Texts.clearTitle)) continue;
            this.saveData.resetBestTimes();
            this.showTextScreen(Texts.clearTitle, Texts.highScoreCleared, 0L, 1);
            return;
        } while (n2 != -1);
     } finally { rally3d.core.Trace.out(86, $t); } }

    void highScoresMenu() { /*T*/ final long $t = rally3d.core.Trace.in(87); try {
        this.highScoreTrack = 0;
        while (true) {
            this.highScoreTrack = this.trackMenu(this.highScoreTrack, false);
            if (this.highScoreTrack == -1) {
                return;
            }
            this.showHighScoreTable(this.highScoreTrack);
        }
     } finally { rally3d.core.Trace.out(87, $t); } }

    boolean confirm(String string) { /*T*/ final long $t = rally3d.core.Trace.in(88); try {
        this.beginMenu(string);
        this.addMenuItem("", 0);
        this.leftSoftkeyLabel = Texts.yes;
        this.rightSoftkeyLabel = Texts.no;
        this.showArrows = false;
        return this.runMenu(0) == 0;
     } finally { rally3d.core.Trace.out(88, $t); } }

    int waitScreen(String string, int n) { /*T*/ final long $t = rally3d.core.Trace.in(89); try {
        this.layoutText(2, this.barHeight + 2, this.width - 5, this.height - this.barHeight - 3, string, true);
        this.beginMenu(Texts.multiplayerTitle);
        this.addMenuItem("", 1);
        this.showLeftSoftkey = false;
        this.showTextBlock = 1;
        this.menuWaitMode = n;
        return this.runMenu(0);
     } finally { rally3d.core.Trace.out(89, $t); } }

    void bluetoothMenu() { /*T*/ final long $t = rally3d.core.Trace.in(90); try {
        int n;
        while (true) {
            int action = this.roleMenu();
            if (action == -1) return;
            if (action == 2) {
                String entered = this.lastMenuInputValue == null ? this.saveData.playerName : this.lastMenuInputValue.trim();
                if (entered.length() > 0) this.saveData.playerName = entered;
                this.saveData.save();
                continue;
            }
            if (action == 3) {
                int selectedMaximum = this.maxPlayersMenu();
                if (selectedMaximum >= 2 && selectedMaximum <= TheBluetooth.MAX_PLAYERS) this.maxPlayers = selectedMaximum;
                continue;
            }
            rally3d.core.RLog.i("mp", "multiplayer menu action " + action);
            if (action == 4) {
                String entered = this.lastMenuInputValue == null ? this.multiplayerPassword : this.lastMenuInputValue.trim();
                this.multiplayerPassword = entered;
                continue;
            }
            String directAddress = null;
            if (action == 5) {
                String typed = this.editTextInGame(rally3d.core.Lang.t("Join by IP"), rally3d.core.Lang.t("Host IP: "), this.lastLanAddress, 40);
                if (typed == null || typed.trim().length() == 0) continue;
                directAddress = typed.trim();
                this.lastLanAddress = directAddress;
            }
            this.bluetooth = new TheBluetooth(this, this.saveData.playerName,
                    rally3d.desktop.Platform.isVanillaMode(), this.maxPlayers, this.multiplayerPassword);
            this.bluetooth.setDirectTarget(directAddress);
            rally3d.core.RLog.i("mp", "menu action=" + action + (directAddress != null ? ", direct=" + directAddress : ""));
            this.opponentSender = -1;
            this.isHost = action == 0;
            n = this.bluetoothSession();
            this.sleep(300);
            this.bluetooth.close();
            this.bluetooth = null;
            if (n == 1) return;
        }
     } finally { rally3d.core.Trace.out(90, $t); } }

    int bluetoothSession() { /*T*/ final long $t = rally3d.core.Trace.in(91); try {
        int n;
        if (this.isHost) {
            this.bluetooth.startHost();
            long deadline = System.currentTimeMillis() + 5000L;
            while (!this.bluetooth.adapterAvailable && !this.bluetooth.hostDone
                    && System.currentTimeMillis() < deadline) this.renderFrame();
            if (!this.bluetooth.adapterAvailable) {
                rally3d.core.RLog.e("mp", "host start failed: adapterAvailable=false hostDone=" + this.bluetooth.hostDone);
                this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("Could not create a local game. Check firewall and network permissions."), 0L, 1);
                return -1;
            }
        } else {
            this.bluetooth.startClient();
            if (this.bluetooth.hasDirectTarget()) {
                long resolveDeadline = System.currentTimeMillis() + 8000L;
                while (!this.bluetooth.isInquiryDone() && System.currentTimeMillis() < resolveDeadline) this.renderFrame();
                if (!this.bluetooth.hasDevices()) {
                    rally3d.core.RLog.e("mp", "direct address invalid: " + this.bluetooth.connectError);
                    this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("Invalid or unknown host address."), 0L, 1);
                    return -1;
                }
                n = 0;
            } else {
                if (this.waitScreen(Texts.searchingDevices, 2) == -1) {
                    rally3d.core.RLog.e("mp", "no host: adapterAvailable=" + this.bluetooth.adapterAvailable + " devices=" + this.bluetooth.deviceCount);
                    if (!this.bluetooth.adapterAvailable) {
                        this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("Local network discovery failed. Check firewall permissions. You can still use Join by IP address."), 0L, 1);
                    } else if (this.bluetooth.deviceCount == 0) {
                        this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("No local games found. Make sure both devices are on the same Wi-Fi/LAN (no VPN, no guest Wi-Fi/AP isolation) and the host is in the lobby, or use Join by IP address (the host's 192.168.x.x shown in its lobby)."), 0L, 1);
                    }
                    return -1;
                }
                n = this.deviceMenu(this.bluetooth);
                if (n == -1) {
                    return -1;
                }
            }
            // Ask for the password BEFORE connecting: the client thread used to connect
            // with an empty password the moment the server was selected.
            this.bluetooth.chooseDevice(n);
            if (this.bluetooth.hasDirectTarget() || this.bluetooth.selectedServerNeedsPassword()) {
                String password = this.editTextInGame(rally3d.core.Lang.t("Join LAN game"),
                        this.bluetooth.hasDirectTarget() ? rally3d.core.Lang.t("Password (empty if none): ") : rally3d.core.Lang.t("Server password: "), "", 24);
                if (password == null) return -1;
                this.bluetooth.setJoinPassword(password);
            }
            this.bluetooth.connect();
            if (this.waitScreen(Texts.waitingForConnection, 3) == -1) {
                rally3d.core.RLog.e("mp", "join failed: rejected=" + this.bluetooth.joinRejected + " reason=" + this.bluetooth.joinRejectReason + " error=" + this.bluetooth.connectError);
                if (this.bluetooth.joinRejected && this.bluetooth.joinRejectReason == 1) {
                    this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("Nickname is taken. Change it."), 0L, 1);
                } else if (this.bluetooth.joinRejected && this.bluetooth.joinRejectReason == 2) {
                    this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("Server is full."), 0L, 1);
                } else if (this.bluetooth.joinRejected && this.bluetooth.joinRejectReason == 4) {
                    this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("Different game version. You: v") + rally3d.core.Version.NAME + rally3d.core.Lang.t(", host: v") + this.bluetooth.remoteVersion + rally3d.core.Lang.t(". Everyone must play the same version."), 0L, 1);
                } else if (this.bluetooth.joinRejected && this.bluetooth.joinRejectReason == 3) {
                    this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("Incorrect password."), 0L, 1);
                } else {
                    this.showTextScreen(rally3d.core.Lang.t("LAN"), rally3d.core.Lang.t("Could not connect to the host")
                            + (this.bluetooth.connectError != null ? " (" + this.bluetooth.connectError + ")" : "")
                            + rally3d.core.Lang.t(". Check the IP, that the host is in the lobby and that TCP port ") + TheBluetooth.TCP_PORT + rally3d.core.Lang.t(" is allowed in the firewall. From a phone use the host's Wi-Fi/LAN IP (192.168.x.x), not a VPN address (26.x Radmin, 25.x Hamachi) - unless the phone is in the same VPN."), 0L, 1);
                }
                return -1;
            }
        }
        do {
            this.drainNetwork();
            this.race.mode = 2;
            // The host must not reach the map before the other player(s) are connected.
            if (this.isHost && !this.hostWaitForPlayers()) {
                n = -1;
                break;
            }
        } while ((n = this.startRace()) != -1);
        this.netBuffer[0] = 1;
        this.netBuffer[1] = 0;
        this.bluetooth.sendPacket(this.netBuffer, 2);
        this.loadScreen(2, 0);
        return n;
     } finally { rally3d.core.Trace.out(91, $t); } }

    boolean pollTrackChoice() { /*T*/ final long $t = rally3d.core.Trace.in(92); try {
        // Only the track packet counts; stale lobby/ping traffic used to kick the client out.
        while (this.bluetooth.receivePacket(this.netBuffer)) {
            int type = this.netBuffer[0] & 0xFF;
            if (type == 2) {
                int track = this.netBuffer[2];
                if (track < 0 || (track >= 12 && track != MapLibrary.MP_MARK)) continue;
                this.btTrackChoice = track;
                return true;
            }
            if (type == 0 || type == 1) {
                this.btTrackChoice = -1;
                return true;
            }
        }
        return false;
     } finally { rally3d.core.Trace.out(92, $t); } }

    /** Lobby shown to the host before car/track selection. Returns false when the host backs out. */
    boolean hostWaitForPlayers() { /*T*/ final long $t = rally3d.core.Trace.in(93); try {
        this.bluetooth.beginRound();
        int previousScreen = this.screenMode;
        this.loadScreen(2, 0);
        this.beginMenu(rally3d.core.Lang.t("Multiplayer Lobby"));
        this.addMenuItem("", 0);
        this.showLeftSoftkey = false;
        this.showRightSoftkey = true;
        this.showTextBlock = 1;
        this.showArrows = false;
        this.menuSlide = 0;
        this.menuWaitMode = 0;
        this.visibleRows = 0;
        this.scrollOffset = 0;
        this.selectedIndex = 0;
        this.screenMode = 2;
        this.snapshotKeys();
        boolean previousSpace = this.isSpaceDown();
        int[] lobbyTapXY = new int[4];
        this.pollPointerEvents(lobbyTapXY); // drop stale taps
        String lastStatus = null;
        java.util.List<String> addresses = TheBluetooth.localAddresses();
        String ip = "";
        if (!addresses.isEmpty()) {   // LAN addresses are sorted first; VPN (Radmin 26.x, Hamachi 25.x) ones are listed after
            StringBuilder ipText = new StringBuilder(rally3d.core.Lang.t("Your IP: ")).append(addresses.get(0)).append(":").append(TheBluetooth.TCP_PORT);
            if (addresses.size() > 1) {
                ipText.append(rally3d.core.Lang.t("  (also "));
                for (int ai = 1; ai < Math.min(3, addresses.size()); ai++) { if (ai > 1) ipText.append(", "); ipText.append(addresses.get(ai)); }
                ipText.append(")");
            }
            ip = ipText.toString();
        }
        while (true) {
            int players = this.bluetooth.playerCount();
            int max = this.maxPlayers;
            boolean space = this.isSpaceDown();
            int keys = this.pollNewKeys();
            boolean lobbyTap = (this.pollPointerEvents(lobbyTapXY) & 2) != 0;
            if ((keys & 0x200) != 0) {
                this.screenMode = previousScreen;
                return false;
            }
            if (players >= 2 && (players >= max || (keys & 0x100) != 0 || lobbyTap || (space && !previousSpace))) {
                this.screenMode = previousScreen;
                return true;
            }
            previousSpace = space;
            StringBuilder names = new StringBuilder();
            for (String name : this.bluetooth.playerNames()) {
                if (names.length() > 0) names.append(", ");
                names.append(name);
            }
            String status = rally3d.core.Lang.t("Players: ") + players + " / " + max
                    + "|" + names
                    + "|" + (players >= 2 ? rally3d.core.Lang.t("Press ENTER or tap to start without waiting") : rally3d.core.Lang.t("Waiting for players to join..."))
                    + (ip.length() > 0 ? "|" + ip : "");
            if (!status.equals(lastStatus)) {
                this.layoutText(2, this.barHeight + 2, this.width - 5, this.height - this.barHeight - 3, status, true);
                lastStatus = status;
            }
            this.renderFrame();
            this.sleep(10);
        }
     } finally { rally3d.core.Trace.out(93, $t); } }

    int multiplayerTrackSelect() { /*T*/ final long $t = rally3d.core.Trace.in(94); try {
        if (!this.isHost) {
            return this.waitScreen(rally3d.core.Lang.t("Waiting for the host to choose a track..."), 4);
        }
        return this.trackMenu(0, false);
     } finally { rally3d.core.Trace.out(94, $t); } }

    int publishMultiplayerTrack() { /*T*/ final long $t = rally3d.core.Trace.in(498); try {
        AddonSync sync = this.bluetooth.addon;
        sync.setRole(true, this.myPlayerId());
        if (!MapLibrary.isCustom(this.race.trackIndex)) {
            sync.setMine(AddonSync.SLOT_MAP, null);
            this.bluetooth.setTrackChoice(this.race.trackIndex);
            return this.race.trackIndex;
        }
        AddonPack mp = MapLibrary.byId(this.race.trackIndex);
        if (mp == null || mp.zip == null) { rally3d.core.RLog.e("mp", "custom map " + this.race.trackIndex + " vanished before it could be sent"); return -1; }
        sync.setMine(AddonSync.SLOT_MAP, mp.zip);
        this.bluetooth.mapAcks.clear();
        this.bluetooth.setTrackChoice(this.race.trackIndex);
        // keep offering the map until every guest confirmed it (or 90 s / Back)
        this.beginMenu(rally3d.core.Lang.t("Multiplayer Lobby"));
        this.addMenuItem("", 0);
        this.showLeftSoftkey = false; this.showRightSoftkey = false;
        this.showTextBlock = 1; this.showTitleBackground = 0; this.showArrows = false;
        this.menuSlide = 0; this.menuWaitMode = 0; this.visibleRows = 0; this.scrollOffset = 0; this.selectedIndex = 0;
        int previousScreen = this.screenMode;
        this.screenMode = 2;
        this.snapshotKeys();
        long start = System.currentTimeMillis(), nextOffer = 0L, lastUi = 0L;
        boolean ok = true;
        while (System.currentTimeMillis() - start < 90000L) {
            long now = System.currentTimeMillis();
            int guests = this.bluetooth.guestCount(), got = this.bluetooth.mapAcks.size();
            if (guests <= 0 || got >= guests) break;
            if (now >= nextOffer) { sync.sendOffer(); nextOffer = now + 500L; }
            if (now - lastUi > 150L) { lastUi = now; this.showAddonStatus(rally3d.core.Lang.t("Sending the map '") + mp.name + rally3d.core.Lang.t("' to the players... ") + got + "/" + guests + "|" + (mp.zip.length / 1024) + rally3d.core.Lang.t(" KB")); }
            if ((this.pollNewKeys() & 0x200) != 0) { ok = false; break; }
            if (this.bluetooth.linkLost()) { ok = false; break; }
            this.sleep(15);
        }
        this.screenMode = previousScreen;
        return ok ? this.race.trackIndex : -1;
     } finally { rally3d.core.Trace.out(498, $t); } }

    /** Guest: downloads the host's custom map for this round (AddonSync map slot), registers it as a session map and confirms to the host. */
    boolean receiveHostMap() { /*T*/ final long $t = rally3d.core.Trace.in(499); try {
        AddonSync sync = this.bluetooth.addon;
        sync.setRole(false, this.myPlayerId());
        final AddonSync.Remote r = sync.remote(0, AddonSync.SLOT_MAP);
        this.beginMenu(rally3d.core.Lang.t("Multiplayer Lobby"));
        this.addMenuItem("", 0);
        this.showLeftSoftkey = false; this.showRightSoftkey = false;
        this.showTextBlock = 1; this.showTitleBackground = 0; this.showArrows = false;
        this.menuSlide = 0; this.menuWaitMode = 0; this.visibleRows = 0; this.scrollOffset = 0; this.selectedIndex = 0;
        int previousScreen = this.screenMode;
        this.screenMode = 2;
        this.snapshotKeys();
        long start = System.currentTimeMillis(), lastUi = 0L, nextOffer = 0L;
        while (System.currentTimeMillis() - start < 60000L && !(r.complete || r.failed)) {
            long now = System.currentTimeMillis();
            if (now >= nextOffer) { sync.sendOffer(); nextOffer = now + 700L; }
            if (now - lastUi > 150L) {
                lastUi = now;
                int pct = r.size > 0 ? (int) ((long) r.got * 100L / r.size) : 0;
                this.showAddonStatus(rally3d.core.Lang.t("Downloading the host's map... ") + pct + "%|" + (r.size / 1024) + rally3d.core.Lang.t(" KB"));
            }
            if ((this.pollNewKeys() & 0x200) != 0) break;
            if (this.bluetooth.linkLost()) break;
            this.sleep(15);
        }
        this.screenMode = previousScreen;
        AddonPack mp = r.complete && r.pack != null ? AddonPack.parse(r.pack, null) : null;
        if (mp == null || mp.error != null || !mp.mapOnly) {
            rally3d.core.RLog.e("mp", "could not get the host's map (complete=" + r.complete + ", failed=" + r.failed + (mp != null && mp.error != null ? ", " + mp.error : "") + ")");
            this.showMessage(rally3d.core.Lang.t("Multiplayer"), rally3d.core.Lang.t("No map from the host"));
            return false;
        }
        int id = MapLibrary.idOf(mp);
        MapLibrary.putSession(id, mp);
        this.race.trackIndex = id;
        this.bluetooth.sendMapAck();
        rally3d.core.RLog.i("mp", "got the host's map '" + mp.name + "' (" + mp.zip.length + " bytes), id " + id);
        return true;
     } finally { rally3d.core.Trace.out(499, $t); } }

    /** -1 = normal track menu; otherwise startRace races this track without asking (map editor test drive). */
    int forcedTrack = -1;

    /** Map editor: races the given map pack (time trial rules, no records, no resume slot) and returns when the player leaves the race. */
    void testDriveMap(AddonPack pack) { /*T*/ final long $t = rally3d.core.Trace.in(500); try {
        MapLibrary.putSession(MapLibrary.TEST_ID, pack);
        int oldMode = this.race.mode;
        this.testDrive = true;
        this.forcedTrack = MapLibrary.TEST_ID;
        this.race.mode = 1;
        try { this.startRace(); }
        finally {
            this.testDrive = false;
            this.forcedTrack = -1;
            this.race.mode = oldMode;
            MapLibrary.removeSession(MapLibrary.TEST_ID);
            this.loadScreen(2, 0);
            this.clearKeys();
        }
     } finally { rally3d.core.Trace.out(500, $t); } }

    /** Map editor: free-fly preview of a map pack (real renderer, free camera). startSeg = where the camera begins. */
    void flyMap(AddonPack pack, int startSeg) { /*T*/ final long $t = rally3d.core.Trace.in(553); try {
        MapLibrary.putSession(MapLibrary.TEST_ID, pack);
        int oldMode = this.race.mode, oldTrack = this.race.trackIndex, oldCar = this.race.carIndex;
        this.testDrive = true;
        try {
            this.loadScreen(2, 0);
            this.refreshLocalCars();
            this.race.mode = 1;
            this.race.trackIndex = MapLibrary.TEST_ID;
            this.race.carIndex = 0;
            this.forward = true;
            this.bannerText = null;
            this.racing = false;
            this.loadScreen(3, MapLibrary.TEST_ID);
            this.prepareRace(false);
            this.sound.stop();
            this.highFps = true;
            this.setTargetFps(this.initialFps());
            this.paused = false;
            new FlyView(this, startSeg).run();
        } catch (RuntimeException e) {
            rally3d.core.RLog.caught("RallyGame.flyMap", e);
            rally3d.core.RLog.e("map", "fly view failed: " + e);
            throw e;
        } finally {
            this.highFps = false;
            this.testDrive = false;
            this.flyView = null;
            this.race.mode = oldMode; this.race.trackIndex = oldTrack; this.race.carIndex = oldCar;
            this.mouseYawOffset = 0.0f; this.mouseCameraPitch = 0.0f;
            MapLibrary.removeSession(MapLibrary.TEST_ID);
            this.loadScreen(2, 0);
            this.clearKeys();
        }
     } finally { rally3d.core.Trace.out(553, $t); } }

    int deviceMenu(TheBluetooth theBluetooth) { /*T*/ final long $t = rally3d.core.Trace.in(95); try {
        this.beginMenu(Texts.selectDevice);
        for (int i = 0; i < theBluetooth.deviceCount; ++i) {
            this.addMenuItem(theBluetooth.deviceNames[i], i);
        }
        return this.runMenu(0);
     } finally { rally3d.core.Trace.out(95, $t); } }

    int championshipMenu(boolean bl) { /*T*/ final long $t = rally3d.core.Trace.in(96); try {
        int n = 0;
        while (true) {
            this.loadScreen(2, 0);
            if (bl) {
                n = 0;
            } else {
                this.beginMenu(Texts.championshipTitle);
                this.addMenuItem(Texts.nextRace, 0);
                if (this.saveData.hasProgress() || MapLibrary.hasMedals()) {
                    this.addMenuItem(Texts.resetAll, 1);
                }
                n = this.runMenu(n);
            }
            switch (n) {
                case 0: {
                    this.race.mode = 0;
                    int n2 = bl ? this.resumeRace(0) : this.startRace();
                    if (n2 != -1) break;
                    return -1;
                }
                case 1: {
                    if (!this.confirm(Texts.resetAllTitle)) break;
                    this.saveData.resetProgress();
                    MapLibrary.resetMedals();
                    this.saveData.save();
                    this.showTextScreen(Texts.resetAllTitle, Texts.resetConfirmed, 0L, 1);
                    return -1;
                }
                default: {
                    return 1;
                }
            }
            bl = false;
        }
     } finally { rally3d.core.Trace.out(96, $t); } }

    int timeTrialMenu(boolean bl) {
        this.race.mode = 1;
        if (bl) {
            return this.resumeRace(1);
        }
        return this.startRace();
    }

    int roleMenu() { /*T*/ final long $t = rally3d.core.Trace.in(97); try {
        this.beginMenu(Texts.multiplayerTitle);
        this.addTextFieldMenuItem(rally3d.core.Lang.t("Player name: "), this.saveData.playerName, 2, 16);
        this.addMenuItem(rally3d.core.Lang.t("Maximum players: ") + this.maxPlayers, 3);
        this.addTextFieldMenuItem(rally3d.core.Lang.t("Server password: "), this.multiplayerPassword, 4, 24);
        this.addMenuItem(rally3d.core.Lang.t("Create LAN game"), 0);
        this.addMenuItem(rally3d.core.Lang.t("Join LAN game"), 1);
        this.addMenuItem(rally3d.core.Lang.t("Join by IP address"), 5);
        return this.runMenu(0);
     } finally { rally3d.core.Trace.out(97, $t); } }

    int maxPlayersMenu() { /*T*/ final long $t = rally3d.core.Trace.in(98); try {
        this.beginMenu(rally3d.core.Lang.t("Maximum Players"));
        for (int count = 2; count <= TheBluetooth.MAX_PLAYERS; count++) {
            this.addMenuItem(count + rally3d.core.Lang.t(" players"), count);
        }
        return this.runMenu(this.maxPlayers - 2);
     } finally { rally3d.core.Trace.out(98, $t); } }

    void modeMenu(boolean bl, int n) { /*T*/ final long $t = rally3d.core.Trace.in(99); try {
        int n2 = 0;
        while (true) {
            this.loadScreen(2, 0);
            if (bl) {
                n2 = n;
            } else {
                this.beginMenu(Texts.newGameTitle);
                this.addMenuItem(Texts.championship, 0);
                this.addMenuItem(Texts.timeTrial, 1);
                this.addMenuItem(rally3d.core.Lang.t("Ghost Race"), 2);
                n2 = this.runMenu(n2);
            }
            switch (n2) {
                case 0: {
                    if (this.championshipMenu(bl) != -1) break;
                    return;
                }
                case 1: {
                    if (this.timeTrialMenu(bl) != -1) break;
                    return;
                }
                case 2: {
                    this.race.mode = 3;
                    if (this.startRace() != -1) break;
                    return;
                }
                default: {
                    return;
                }
            }
            bl = false;
        }
     } finally { rally3d.core.Trace.out(99, $t); } }

    /**
     * Loads the saved ghost of every car for the track (stock cars, installed add-on cars and ghosts on disk whose car is
     * no longer installed), lets the player pick one (or none). Works for stock tracks and custom maps. Sets ghostPlayback. -2 = back.
     */
    private int chooseGhost(int track, int ownCar) { /*T*/ final long $t = rally3d.core.Trace.in(285); try {
        this.refreshLocalCars();
        java.util.ArrayList<Integer> keys = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 4; i++) keys.add(Integer.valueOf(i));
        for (int i = 4; i < this.localCars.size(); i++) {
            CarDef d = this.localCars.get(i);
            if (d != null && d.pack != null) { Integer k = Integer.valueOf(d.pack.carKey()); if (!keys.contains(k)) keys.add(k); }
        }
        int[] onDisk = rally3d.desktop.Platform.ghostKeys(track);
        if (onDisk != null) for (int k : onDisk) { Integer o = Integer.valueOf(k); if (k >= 0 && !keys.contains(o)) keys.add(o); }
        java.util.ArrayList<GhostReplay> found = new java.util.ArrayList<GhostReplay>();
        for (Integer k : keys) {
            GhostReplay g = GhostReplay.decode(rally3d.desktop.Platform.loadGhost(track, k.intValue()));
            if (g != null && g.carKey == k.intValue()) found.add(g);
        }
        this.ghostPlayback = null;
        this.ghostRecordBeaten = false;
        if (found.isEmpty()) { rally3d.core.RLog.i("ghost", "track=" + track + ": no ghosts yet"); return -1; }
        this.beginMenu(rally3d.core.Lang.t("Select ghost"));
        int ownKey = this.ghostKeyOf(ownCar);
        int pre = 0, best = -1, own = -1;
        for (int i = 0; i < found.size(); i++) {
            GhostReplay g = found.get(i);
            int li = this.localIndexForCarKey(g.carKey);
            String nm = li >= 0 ? this.carDef(li).name : (g.carName != null && g.carName.length() > 0 ? g.carName : rally3d.core.Lang.t("Car ") + g.carKey) + rally3d.core.Lang.t(" (not installed)");
            if (g.carKey < 4 && li >= 0 && li < Texts.carNames.length) nm = Texts.carNames[li];
            this.addMenuItem(nm + "  " + this.formatTime(g.finishTime), i);
            if (best == -1 || g.finishMs < found.get(best).finishMs) best = i;
            if (g.carKey == ownKey) own = i;
        }
        pre = own >= 0 ? own : Math.max(0, best);   // preselect the ghost of my own car, otherwise the fastest one
        this.addMenuItem(rally3d.core.Lang.t("No ghost"), found.size());
        int r = this.runMenu(pre);
        if (r == -1) return -2;
        if (r >= 0 && r < found.size()) this.ghostPlayback = found.get(r);
        rally3d.core.RLog.i("ghost", "track=" + track + " car=" + ownCar + " ghost=" + (this.ghostPlayback == null ? "none" : "carKey " + this.ghostPlayback.carKey + " time=" + this.formatTime(this.ghostPlayback.finishTime)));
        return r;
     } finally { rally3d.core.Trace.out(285, $t); } }

    private void finishGhostRecord() { /*T*/ final long $t = rally3d.core.Trace.in(100); try {
        if (!this.ghostOn() || this.ghostRecorder == null) return;
        int key = this.ghostKeyOf(this.race.carIndex);
        this.ghostRecorder.finishTime = this.race.raceTime;
        this.ghostRecorder.finishMs = this.ghostFinishMs > 0 ? this.ghostFinishMs : Math.max(this.ghostRecorder.lastTimeMs(), this.race.raceTime * 100);
        this.ghostRecorder.carKey = key;
        this.ghostRecorder.carName = this.carDef(this.race.carIndex).name;
        GhostReplay ownRecord = GhostReplay.decode(rally3d.desktop.Platform.loadGhost(this.race.trackIndex, key));
        if (ownRecord != null && ownRecord.carKey != key) ownRecord = null;
        boolean better = ownRecord == null || this.ghostRecorder.finishMs < ownRecord.finishMs;
        rally3d.core.RLog.i("ghost", "finish track=" + this.race.trackIndex + " carKey=" + key + " ms=" + this.ghostRecorder.finishMs + " record=" + (ownRecord == null ? "none" : String.valueOf(ownRecord.finishMs)) + " better=" + better);
        if (better) {
            byte[] replay = this.ghostRecorder.encode();
            if (replay != null && rally3d.desktop.Platform.saveGhost(this.race.trackIndex, key, replay)) {
                this.ghostRecordBeaten = true;
                this.ghostPlayback = this.ghostRecorder;
            }
        }
        this.ghostRecorder = null;
     } finally { rally3d.core.Trace.out(100, $t); } }

    /** "Add-ons": zip add-ons in three tabs (maps / cars / wheels), see AddonUi. Tap a row = details, [X] = delete, green box = on/off. */
    void addonsMenu() { /*T*/ final long $t = rally3d.core.Trace.in(286); try {
        new AddonUi(this).run();
        this.refreshLocalCars();
        this.evictUnusedAddonCars();
     } finally { rally3d.core.Trace.out(286, $t); } }

    /** "Map Editor": the in-game track builder (see MapEditor). {@code open} = a map pack to edit, null = new map. */
    /** "Car Builder": the in-game car / wheels builder (see CarEditor). {@code open} = a pack made with it to edit, null = new car. */
    void carEditorMenu(AddonPack open) {
        new CarEditor(this).run(open);
    }

    void mapEditorMenu(AddonPack open) {
        new MapEditor(this).run(open);
        this.refreshLocalCars();
        this.loadScreen(2, 0);
    }

    /** Progress (medals, best times, unlocked cars, ghosts, add-ons, settings) as one zip the player keeps outside the app: survives an uninstall on Android. */
    private void backupProgress() {
        try {
            this.saveData.save();
            java.io.File ad = rally3d.desktop.Platform.addonsDir();
            byte[] z = rally3d.core.ProgressBackup.create(rally3d.desktop.Platform.progressDir(), ad, ad == null ? null : ad.getParentFile());
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(new java.util.Date());
            String saved = rally3d.desktop.Platform.exportFile("rally3d-progress-" + stamp + ".zip", z);
            this.showMessage(rally3d.core.Lang.t("Backup"), saved != null ? rally3d.core.Lang.t("Saved: ") + saved : rally3d.core.Lang.t("Backup cancelled"));
        } catch (Throwable t) {
            rally3d.core.RLog.caught("RallyGame.backupProgress", t);
            this.showMessage(rally3d.core.Lang.t("Backup"), rally3d.core.Lang.t("Failed: ") + (t.getMessage() == null ? t.toString() : t.getMessage()));
        }
    }

    private void restoreProgress() {
        try {
            byte[] z = rally3d.desktop.Platform.pickFile(rally3d.core.Lang.t("Rally3D - restore progress backup"));
            if (z == null) { this.showMessage(rally3d.core.Lang.t("Restore"), rally3d.core.Lang.t("Cancelled")); return; }
            java.io.File ad = rally3d.desktop.Platform.addonsDir();
            int n = rally3d.core.ProgressBackup.restore(z, rally3d.desktop.Platform.progressDir(), ad, ad == null ? null : ad.getParentFile());
            // re-read what was just written, otherwise the old in-memory state would be saved over it on the next save
            String keepName = this.saveData.playerName;
            this.saveData = new SaveData(Texts.defaultPlayerName);
            this.saveData.fontHeightBold = this.fontBoldHeight;
            this.saveData.fontHeightSmall = this.fontSmallHeight;
            if (this.saveData.playerName == null || this.saveData.playerName.length() == 0) this.saveData.playerName = keepName;
            this.sound.setEnabled(this.saveData.soundLevel != 0);
            this.sound.setVolume(this.saveData.soundLevel * 99 / 3);
            MapLibrary.reloadFromDisk();
            AddonManager.rescan();
            this.refreshLocalCars();
            rally3d.core.RLog.i("backup", "progress restored: " + n + " files");
            this.showMessage(rally3d.core.Lang.t("Restore"), n + rally3d.core.Lang.t(" files restored. Restart the game to apply all settings."));
        } catch (Throwable t) {
            rally3d.core.RLog.caught("RallyGame.restoreProgress", t);
            this.showMessage(rally3d.core.Lang.t("Restore"), rally3d.core.Lang.t("Failed: ") + (t.getMessage() == null ? t.toString() : t.getMessage()));
        }
    }

    /** Language list (first launch: no way back, Back keeps the highlighted language). Applies, saves and rebuilds all texts at once. */
    void languageMenu(boolean first) {
        String[][] langs = rally3d.core.Lang.LANGS;
        int sel = Math.max(0, rally3d.core.Lang.indexOf(rally3d.core.Lang.code()));
        this.beginMenu(first ? "Language / \u042f\u0437\u044b\u043a / Sprache" : rally3d.core.Lang.t("Language"));
        for (int i = 0; i < langs.length; i++) this.addMenuItem(langs[i][1], i);
        this.menuSlideEnabled = first ? 0 : 1;
        int n = this.runMenu(sel);
        if (n < 0) { if (!first) return; n = sel; }
        this.applyLanguage(langs[n][0]);
    }

    private void applyLanguage(String code) {
        String oldDefaultName = Texts.defaultPlayerName;
        rally3d.core.Lang.set(code);
        rally3d.desktop.Platform.setLanguage(code);
        Localization.reset();
        Texts.reinit();
        if (this.saveData != null && oldDefaultName != null && oldDefaultName.equals(this.saveData.playerName)) {
            this.saveData.playerName = Texts.defaultPlayerName;   // the untouched default name follows the language
            this.saveData.save();
        }
        rally3d.core.RLog.i("lang", "language set to " + code);
    }

    void settingsMenu() { /*T*/ final long $t = rally3d.core.Trace.in(101); try {
        int n = 10;
        int n2 = 1;
        while (true) {
            this.beginMenu(Texts.settingsTitle);
            // Ordered by importance: graphics / controls first, then gameplay, then cosmetics and developer options.
            this.addMenuItem(Texts.pcSettings, 10);
            this.addMenuItem(rally3d.core.Lang.t("Language: ") + rally3d.core.Lang.name(), 22);
            if (rally3d.desktop.Platform.supportsTouchLayout()) {
                this.addMenuItem(rally3d.core.Lang.t("Touch buttons size (slider)"), 17);
                this.addMenuItem(rally3d.core.Lang.t("Touch buttons position (drag)"), 18);
            }
            if (this.saveData.autoAccelOff == 0) {
                this.addMenuItem(Texts.autoAccelOnText, 7);
            } else {
                this.addMenuItem(Texts.autoAccelOffText, 7);
            }
            if (!rally3d.desktop.Platform.isVanillaMode()) {
                this.addMenuItem(rally3d.core.Lang.t("Mouse camera: ") + (rally3d.desktop.Platform.isMouseLookEnabled() ? rally3d.core.Lang.t("On") : rally3d.core.Lang.t("Off")), 12);
            }
            switch (this.saveData.soundLevel) {
                case 0: {
                    this.addMenuItem(Texts.soundOff, 0);
                    break;
                }
                case 1: {
                    this.addMenuItem(Texts.soundLow, 0);
                    break;
                }
                case 2: {
                    this.addMenuItem(Texts.soundNormal, 0);
                    break;
                }
                case 3: {
                    this.addMenuItem(Texts.soundLoud, 0);
                }
            }
            if (this.saveData.vibrationOff == 0) {
                if (rally3d.desktop.Platform.supportsVibration()) this.addMenuItem(Texts.vibrationOnText, 1);
            } else {
                if (rally3d.desktop.Platform.supportsVibration()) this.addMenuItem(Texts.vibrationOffText, 1);
            }
            if (this.saveData.speedKmh == 0) {
                this.addMenuItem(Texts.speedMphText, 8);
            } else {
                this.addMenuItem(Texts.speedKmhText, 8);
            }
            this.addTextFieldMenuItem(rally3d.core.Lang.t("Player name: "), this.saveData.playerName, 9, 16);
            if (!rally3d.desktop.Platform.isVanillaMode()) {
                this.addMenuItem(rally3d.core.Lang.t("Telemetry HUD: ") + (rally3d.desktop.Platform.isTelemetryEnabled() ? rally3d.core.Lang.t("On") : rally3d.core.Lang.t("Off")), 14);
                this.addMenuItem(rally3d.core.Lang.t("Menu animations: ") + (rally3d.desktop.Platform.areUiAnimationsEnabled() ? rally3d.core.Lang.t("On") : rally3d.core.Lang.t("Off")), 11);
            }
            this.addMenuItem(rally3d.core.Lang.t("Game version: ") + (rally3d.desktop.Platform.isVanillaMode()
                    ? rally3d.core.Lang.t("Vanilla — return to Modified") : rally3d.core.Lang.t("Modified — return to Vanilla")), 15);
            if (rally3d.desktop.Platform.fullTraceSupported()) {
                this.addMenuItem(rally3d.core.Lang.t("Full function trace log: ") + (rally3d.desktop.Platform.isFullTrace() ? rally3d.core.Lang.t("On (big file)") : rally3d.core.Lang.t("Off")), 16);
            }
            this.addMenuItem(rally3d.core.Lang.t("Save progress to a file (backup)"), 20);
            this.addMenuItem(rally3d.core.Lang.t("Restore progress from a file"), 21);
            this.menuSlideEnabled = n2;
            this.menuExitMode = 0;
            n = this.runMenu(n);
            n2 = 0;
            switch (n) {
                case 22: { this.languageMenu(false); break; }
                case 20: { this.backupProgress(); break; }
                case 21: { this.restoreProgress(); break; }
                case 0: {
                    this.saveData.soundLevel = (this.saveData.soundLevel + 1) % 4;
                    this.sound.setVolume(this.saveData.soundLevel * 99 / 3);
                    this.sound.setEnabled(this.saveData.soundLevel != 0);
                    if (this.saveData.soundLevel != 0) break;
                    this.sound.stop();
                    break;
                }
                case 1: {
                    if (this.saveData.vibrationOff == 0) {
                        this.saveData.vibrationOff = 1;
                        break;
                    }
                    this.saveData.vibrationOff = 0;
                    this.vibrate(70, 90);
                    break;
                }
                case 7: {
                    if (this.saveData.autoAccelOff == 0) {
                        this.saveData.autoAccelOff = 1;
                        break;
                    }
                    this.saveData.autoAccelOff = 0;
                    break;
                }
                case 8: {
                    if (this.saveData.speedKmh == 0) {
                        this.saveData.speedKmh = 1;
                        break;
                    }
                    this.saveData.speedKmh = 0;
                    break;
                }
                case 9: {
                    String editedName = this.lastMenuInputValue;
                    if (editedName == null) break;
                    this.saveData.playerName = editedName;
                    if (!this.saveData.playerName.equals("unlock")) {
                        this.saveData.save();
                        break;
                    }
                    this.saveData.unlockAll();
                    this.saveData.save();
                    break;
                }
                case 10: {
                    this.desktopSettingsMenu();
                    break;
                }
                case 11: {
                    rally3d.desktop.Platform.setUiAnimationsEnabled(!rally3d.desktop.Platform.areUiAnimationsEnabled());
                    break;
                }
                case 12: {
                    rally3d.desktop.Platform.setMouseLookEnabled(!rally3d.desktop.Platform.isMouseLookEnabled());
                    break;
                }
                case 13: {
                    rally3d.desktop.Platform.setNoSpriteLod(!rally3d.desktop.Platform.isNoSpriteLod());
                    break;
                }
                case 14: {
                    rally3d.desktop.Platform.setTelemetryEnabled(!rally3d.desktop.Platform.isTelemetryEnabled());
                    break;
                }
                case 17: {
                    rally3d.desktop.Platform.showButtonScaleDialog();
                    break;
                }
                case 18: {
                    rally3d.desktop.Platform.showButtonLayoutEditor();
                    break;
                }
                case 16: {
                    rally3d.desktop.Platform.setFullTrace(!rally3d.desktop.Platform.isFullTrace());
                    break;
                }
                case 15: {
                    rally3d.desktop.Platform.setVanillaMode(!rally3d.desktop.Platform.isVanillaMode());
                    break;
                }
                case -1: {
                    this.saveData.save();
                    return;
                }
            }
        }
     } finally { rally3d.core.Trace.out(101, $t); } }

    /** PC display controls rendered and navigated by the game's own menu system. */
    void desktopSettingsMenu() { /*T*/ final long $t = rally3d.core.Trace.in(102); try {
        int selected = 0;
        while (true) {
            int resolution = rally3d.desktop.Platform.resolutionIndex();
            int fps = rally3d.desktop.Platform.fpsIndex();
            boolean fullscreen = rally3d.desktop.Platform.isFullscreen();
            boolean showFps = rally3d.desktop.Platform.isShowFps();
            int fpsValue = rally3d.desktop.Platform.currentFpsLimit();
            this.beginMenu(rally3d.core.Lang.t("Display / FPS"));
            this.menuSlideEnabled = 0;
            this.addMenuItem(rally3d.core.Lang.t("Resolution: ") + rally3d.desktop.Platform.RESOLUTIONS[resolution][0] + " x " + rally3d.desktop.Platform.RESOLUTIONS[resolution][1] + rally3d.core.Lang.t("  [select]"), 0);
            this.addMenuItem(rally3d.core.Lang.t("Frame limit: ") + (fpsValue <= 0 ? rally3d.core.Lang.t("Unlimited") : fpsValue + rally3d.core.Lang.t(" FPS")) + rally3d.core.Lang.t("  [select]"), 1);
            if (!rally3d.desktop.Platform.supportsTouchLayout()) this.addMenuItem(rally3d.core.Lang.t("Fullscreen window (borderless): ") + (fullscreen ? rally3d.core.Lang.t("On") : rally3d.core.Lang.t("Off")), 2);
            this.addMenuItem(rally3d.core.Lang.t("FPS counter: ") + (showFps ? rally3d.core.Lang.t("On") : rally3d.core.Lang.t("Off")), 3);
            String[] aaModes = {rally3d.core.Lang.t("Off"), rally3d.core.Lang.t("FXAA"), rally3d.core.Lang.t("FXAA Quality")};
            int aaMode = rally3d.desktop.Platform.antiAliasing();
            this.addMenuItem(rally3d.core.Lang.t("Anti-aliasing: ") + aaModes[aaMode], 4);
            this.addMenuItem(rally3d.core.Lang.t("3D renderer: ") + (rally3d.desktop.Platform.gpuRaster() ? rally3d.core.Lang.t("GPU") : rally3d.core.Lang.t("Software (multi-core)")), 6);
            if (rally3d.desktop.Platform.gpuRaster()) this.addMenuItem(rally3d.core.Lang.t("UI renderer: ") + (rally3d.desktop.Platform.gpuUiSetting() ? rally3d.core.Lang.t("GPU") : rally3d.core.Lang.t("CPU")), 7);
            { int ct = rally3d.desktop.Platform.cpuThreadsSetting(); this.addMenuItem(rally3d.core.Lang.t("CPU threads: ") + (ct <= 0 ? rally3d.core.Lang.t("Auto (") + rally3d.desktop.Platform.cpuThreadsEffective() + ")" : String.valueOf(ct)), 8); }
            if (rally3d.desktop.Platform.supportsTouchLayout()) this.addMenuItem(rally3d.core.Lang.t("Dynamic resolution: ") + (rally3d.desktop.Platform.dynamicResolution() ? rally3d.core.Lang.t("On") : rally3d.core.Lang.t("Off")), 10);
            if (rally3d.desktop.Platform.gpuRaster()) this.addMenuItem(rally3d.core.Lang.t("V-sync: ") + (rally3d.desktop.Platform.vsync() ? rally3d.core.Lang.t("On") : rally3d.core.Lang.t("Off")), 9);
            this.addMenuItem(rally3d.core.Lang.t("Back"), 5);
            selected = this.runMenu(selected);
            if (selected < 0 || selected == 5) return;

            int nextFps = fps;
            boolean nextFullscreen = fullscreen;
            boolean nextShowFps = showFps;
            switch (selected) {
                case 0: {
                    this.resolutionSettingsMenu();
                    continue;
                }
                case 1: {
                    this.fpsSettingsMenu();
                    continue;
                }
                case 2: nextFullscreen = !fullscreen; break;
                case 3: nextShowFps = !showFps; break;
                case 4: rally3d.desktop.Platform.setAntiAliasing((aaMode + 1) % aaModes.length); continue;
                case 6: rally3d.desktop.Platform.setGpuRaster(!rally3d.desktop.Platform.gpuRaster()); continue;
                case 7: rally3d.desktop.Platform.setGpuUi(!rally3d.desktop.Platform.gpuUiSetting()); continue;
                case 10: rally3d.desktop.Platform.setDynamicResolution(!rally3d.desktop.Platform.dynamicResolution()); continue;
                case 9: rally3d.desktop.Platform.setVsync(!rally3d.desktop.Platform.vsync()); continue;
                case 8: rally3d.desktop.Platform.setCpuThreads(rally3d.desktop.Platform.nextCpuThreads()); continue;
                default: continue;
            }
            rally3d.desktop.Platform.configure(resolution, nextFps, nextFullscreen, nextShowFps);
            rally3d.desktop.Platform.save();
            if (nextFullscreen != fullscreen) {
                rally3d.desktop.Platform.applyResolution();
                this.width = this.getWidth();
                this.height = this.getHeight();
                this.graphics = this.getGraphics();
                this.loadedScreen = -1;
                this.loadScreen(2, 0);
            }
        }
     } finally { rally3d.core.Trace.out(102, $t); } }

    /** List of all render resolutions, smallest to largest (like the frame rate list). Picking one applies it and returns. */
    private void resolutionSettingsMenu() { /*T*/ final long $t = rally3d.core.Trace.in(626); try {
        final int[][] res = rally3d.desktop.Platform.RESOLUTIONS;
        Integer[] order = new Integer[res.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer x, Integer y) { /*T*/ final long $t = rally3d.core.Trace.in(625); try {
                long ax = (long) res[x][0] * res[x][1], ay = (long) res[y][0] * res[y][1];
                if (ax != ay) return ax < ay ? -1 : 1;
                return res[x][0] - res[y][0];
             } finally { rally3d.core.Trace.out(625, $t); } }
        });
        int current = rally3d.desktop.Platform.resolutionIndex();
        this.beginMenu(rally3d.core.Lang.t("Resolution"));
        this.menuSlideEnabled = 0;
        for (int k = 0; k < order.length; k++) {
            int idx = order[k];
            this.addMenuItem(res[idx][0] + " x " + res[idx][1] + (idx == current ? rally3d.core.Lang.t("  [selected]") : ""), idx);
        }
        this.addMenuItem(rally3d.core.Lang.t("Back"), res.length);
        int selected = this.runMenu(current);
        if (selected < 0 || selected >= res.length || selected == current) return;
        rally3d.desktop.Platform.configure(selected, rally3d.desktop.Platform.fpsIndex(), rally3d.desktop.Platform.isFullscreen(), rally3d.desktop.Platform.isShowFps());
        rally3d.desktop.Platform.save();
        rally3d.desktop.Platform.applyResolution();
        this.width = this.getWidth();
        this.height = this.getHeight();
        this.graphics = this.getGraphics();
        this.loadedScreen = -1;
        this.loadScreen(2, 0);
     } finally { rally3d.core.Trace.out(626, $t); } }

    private void fpsSettingsMenu() { /*T*/ final long $t = rally3d.core.Trace.in(103); try {
        int selected = 0;
        while (true) {
            int current = rally3d.desktop.Platform.currentFpsLimit();
            this.beginMenu(rally3d.core.Lang.t("Frame rate limit"));
            this.menuSlideEnabled = 0;
            int currentIndex = -1;
            for (int i = 0; i < rally3d.desktop.Platform.FPS_VALUES.length; i++) {
                int value = rally3d.desktop.Platform.FPS_VALUES[i];
                if (value == current) currentIndex = i;
                this.addMenuItem((value <= 0 ? rally3d.core.Lang.t("Unlimited") : value + rally3d.core.Lang.t(" FPS")) + (value == current ? rally3d.core.Lang.t("  [selected]") : ""), i);
            }
            int customIndex = rally3d.desktop.Platform.FPS_VALUES.length;
            this.addMenuItem(rally3d.core.Lang.t("Custom value...") + (currentIndex < 0 ? "  [" + (current <= 0 ? rally3d.core.Lang.t("Unlimited") : current + rally3d.core.Lang.t(" FPS")) + rally3d.core.Lang.t(" selected]") : ""), customIndex);
            this.addMenuItem(rally3d.core.Lang.t("Back"), customIndex + 1);
            selected = this.runMenu(selected);
            if (selected < 0 || selected == customIndex + 1) return;
            if (selected >= 0 && selected < rally3d.desktop.Platform.FPS_VALUES.length) {
                rally3d.desktop.Platform.setFpsLimit(rally3d.desktop.Platform.FPS_VALUES[selected]);
                return;
            }
            if (selected == customIndex) {
                String entered = this.editTextInGame(rally3d.core.Lang.t("Frame rate limit"), rally3d.core.Lang.t("Custom FPS (30-1000): "), String.valueOf(current), 4);
                if (entered == null) continue;
                try {
                    int limit = Integer.parseInt(entered.trim());
                    if (limit >= 30 && limit <= 1000) {
                        rally3d.desktop.Platform.setFpsLimit(limit);
                        return;
                    }
                } catch (NumberFormatException ignored) { rally3d.core.RLog.caught("RallyGame.fpsSettingsMenu", ignored); }
                this.showMessage(rally3d.core.Lang.t("Invalid FPS limit"), rally3d.core.Lang.t("Enter a whole number from 30 to 1000"));
            }
        }
     } finally { rally3d.core.Trace.out(103, $t); } }

    void drawFps(Graphics graphics) { /*T*/ final long $t = rally3d.core.Trace.in(104); try {
        graphics.setColor(0);
        graphics.fillRect(0, 0, this.width, this.fontBoldHeight);
        this.drawText(graphics, 0, 0, this.debugText, 0xFFFFFF, 0);
     } finally { rally3d.core.Trace.out(104, $t); } }

    void continueMenu() { /*T*/ final long $t = rally3d.core.Trace.in(105); try {
        this.beginMenu(Texts.continueQuestion);
        if (this.saveData.savedGameValid[0] != 0) {
            this.addMenuItem(Texts.championship, 0);
        }
        if (this.saveData.savedGameValid[1] != 0) {
            this.addMenuItem(Texts.timeTrial, 1);
        }
        this.rightSoftkeyLabel = Texts.no;
        this.leftSoftkeyLabel = Texts.yes;
        int n = this.runMenu(0);
        if (n != -1) {
            this.modeMenu(true, n);
        }
     } finally { rally3d.core.Trace.out(105, $t); } }

    public void run() { /*T*/ final long $t = rally3d.core.Trace.in(106); try {
        rally3d.core.RLog.i("game", "Rally3D version " + rally3d.core.Version.NAME + " (code " + rally3d.core.Version.CODE + ") game thread started");
        int n = 0;
        if (this.sound == null) {
            try {
                this.sound = new RallySoundManager();
            }
            catch (Exception exception) { rally3d.core.RLog.caught("RallyGame.run", exception);
                this.sound = new SoundManager();
            }
        }
        this.resetTimer();
        this.initFonts();
        this.textsInstance = new Texts();
        String savedLang = rally3d.desktop.Platform.getLanguage();
        boolean askLanguage = !rally3d.core.Lang.isKnown(savedLang);   // first launch (or an unknown value): the player picks the language below
        rally3d.core.Lang.set(askLanguage ? rally3d.core.Lang.detect() : savedLang);
        Texts.init(0, this.getKeyName(this.getKeyCode(8)), this.getKeyName(this.getKeyCode(2)), this.getKeyName(this.getKeyCode(5)), this.getKeyName(this.getKeyCode(1)), this.getKeyName(this.getKeyCode(6)), 4, 8);
        this.saveData = new SaveData(Texts.defaultPlayerName);
        if (this.fontBoldHeight < this.saveData.fontHeightBold) {
            this.fontBoldHeight = this.saveData.fontHeightBold;
        }
        this.saveData.fontHeightBold = this.fontBoldHeight;
        if (this.fontSmallHeight < this.saveData.fontHeightSmall) {
            this.fontSmallHeight = this.saveData.fontHeightSmall;
        }
        this.saveData.fontHeightSmall = this.fontSmallHeight;
        this.sound.setEnabled(this.saveData.soundLevel != 0);
        while (this.midlet.splashShowing) {
            this.sleep(50);
        }
        this.loadScreen(1, -1);
        this.sound.play(3);
        this.loadScreen(2, 0);
        this.display.setCurrent((Displayable)this);
        this.clearKeys();
        if (askLanguage) {
            this.loadScreen(2, 0);
            this.languageMenu(true);
        }
        while (true) {
            this.loadScreen(2, 0);
            this.beginMenu(null);
            this.mainMenuTag = true;
            if (this.saveData.savedGameValid[0] != 0 || this.saveData.savedGameValid[1] != 0) {
            this.addMenuItem(Texts.continueText, 8);
                n = 8;
            }
            this.addMenuItem(Texts.newGame, 0);
            this.addMenuItem(Texts.multiplayer, 6);
            this.addMenuItem(Texts.highScores, 3);
            this.addMenuItem(rally3d.core.Lang.t("Add-ons"), 9);
            this.addMenuItem(rally3d.core.Lang.t("Map Editor"), 10);
            this.addMenuItem(rally3d.core.Lang.t("Car Builder"), 11);
            this.addMenuItem(Texts.settings, 4);
            this.addMenuItem(Texts.instructions, 5);
            this.rightSoftkeyLabel = Texts.exit;
            n = this.runMenu(n);
            switch (n) {
                case 8: {
                    this.continueMenu();
                    break;
                }
                case 0: {
                    this.modeMenu(false, -1);
                    break;
                }
                case 3: {
                    this.highScoresMenu();
                    break;
                }
                case 4: {
                    this.settingsMenu();
                    break;
                }
                case 9: {
                    this.addonsMenu();
                    break;
                }
                case 10: {
                    this.mapEditorMenu(null);
                    break;
                }
                case 11: {
                    this.carEditorMenu(null);
                    break;
                }
                case 5: {
                    String string = HelpPages.all(java.util.Arrays.copyOf(Texts.helpPages, 6), Texts.rightToLeft);
                    this.showTextScreen(Texts.instructionsTitle, string, 0L, 0);
                    break;
                }
                case 6: {
                    this.bluetoothMenu();
                    break;
                }
                case -1: {
                    this.sound.stop();
                    this.midlet.exitApp();
                }
            }
        }
     } finally { rally3d.core.Trace.out(106, $t); } }
}
