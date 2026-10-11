package rally3d.desktop;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Cursor;
import java.awt.image.BufferedImage;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.CardLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.microedition.lcdui.Canvas;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Main window: shows the latest finished frame, letterboxed; forwards keys to the active canvas. */
final class GameWindow extends JFrame {
    private final Object frameLock = new Object();
    private volatile Canvas canvas;
    private final CardLayout surfaceCards = new CardLayout();
    private final JPanel panel = new JPanel(surfaceCards);
    private final GpuSurface gpuSurface = new GpuSurface();
    private final SoftwarePanel softwarePanel = new SoftwarePanel();
    private final AtomicBoolean gpuRenderQueued = new AtomicBoolean();
    private volatile boolean gpuFailed;
    private BufferedImage front;
    private int[] frontPixels;
    /** Buffer currently being filled by the game thread. */
    private int[] gpuFramePixels;
    /** Last completed frame retained by GpuSurface for expose/repaint events. */
    private int[] gpuDisplayedPixels;
    private int gpuFrameWidth, gpuFrameHeight;
    private long gpuFrameSequence;
    private final Map<Integer, Timer> pendingRelease = new HashMap<Integer, Timer>();
    private final Map<Integer, Integer> held = new HashMap<Integer, Integer>();
    private volatile boolean mouseLookRequested;
    private volatile boolean gameplayActive;
    private volatile boolean inputOverlayActive;
    private volatile JButton inputCancelButton;
    private boolean mouseLookActive;
    private Robot mouseRobot;
    private Cursor normalCursor;
    private Cursor hiddenCursor;

    GameWindow() {
        super("Rally 3D");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { Platform.exit(); }
        });
        addWindowFocusListener(new WindowFocusListener() {
            public void windowGainedFocus(WindowEvent e) { updateMouseLookCapture(); raiseOverTaskbar(); }
            public void windowLostFocus(WindowEvent e) { releaseMouseLookCapture(); releaseAllKeys(); dropTopmost(); }
        });
        panel.setFocusable(true);
        panel.setFocusTraversalKeysEnabled(false);
        panel.setIgnoreRepaint(false);
        panel.add(gpuSurface, "gpu");
        panel.add(softwarePanel, "software");
        gpuSurface.setBackground(Color.BLACK);
        softwarePanel.setBackground(Color.BLACK);
        surfaceCards.show(panel, "gpu");
        add(panel);
        KeyAdapter keys = new KeyAdapter() {
            public void keyPressed(KeyEvent e) { onKey(e, true); }
            public void keyReleased(KeyEvent e) { onKey(e, false); }
            public void keyTyped(KeyEvent e) {
                Canvas c = canvas;
                if (c != null && !Character.isISOControl(e.getKeyChar())) c.dispatchTextCharacter(e.getKeyChar());
            }
        };
        gpuSurface.addKeyListener(keys);
        softwarePanel.addKeyListener(keys);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { if (dispatchRawPointer(e, 1)) return; if (!handleMouseLookEvent(e)) dispatchPointer(e, false); }
            @Override public void mouseDragged(MouseEvent e) { if (dispatchRawPointer(e, 1)) return; if (!handleMouseLookEvent(e)) dispatchPointer(e, false); }
            @Override public void mousePressed(MouseEvent e) {
                if (dispatchRawPointer(e, 0)) return;
                if (e.getButton() == MouseEvent.BUTTON1) dispatchPointer(e, true);
            }
            @Override public void mouseReleased(MouseEvent e) { dispatchRawPointer(e, 2); }
        };
        gpuSurface.addMouseListener(mouse);
        gpuSurface.addMouseMotionListener(mouse);
        gpuSurface.addMouseWheelListener(new MouseWheelListener() {
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                dispatchWheel(e);
            }
        });
        softwarePanel.addMouseListener(mouse);
        softwarePanel.addMouseMotionListener(mouse);
        softwarePanel.addMouseWheelListener(new MouseWheelListener() {
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                dispatchWheel(e);
            }
        });
        applyModeNow();
    }

    void setCanvas(Canvas c) { canvas = c; }
    Canvas gameCanvas() { return canvas; }

    void dispatchControllerKey(int keyCode, boolean pressed) {
        if (inputOverlayActive && (keyCode == -5 || keyCode == -7)) {
            if (pressed) SwingUtilities.invokeLater(() -> {
                if (!inputOverlayActive) return;
                if (keyCode == -5) {
                    JButton button = getRootPane().getDefaultButton();
                    if (button != null) button.doClick();
                } else {
                    JButton button = inputCancelButton;
                    if (button != null) button.doClick();
                }
            });
            return;
        }
        Canvas c = canvas;
        if (c != null) c.dispatchControllerKey(keyCode, pressed);
    }

    void setMouseLookActive(boolean active) {
        mouseLookRequested = active;
        if (SwingUtilities.isEventDispatchThread()) updateMouseLookCapture();
        else SwingUtilities.invokeLater(this::updateMouseLookCapture);
    }

    void setGameplayActive(boolean active) { gameplayActive = active; }

    private void updateMouseLookCapture() {
        if (mouseLookRequested && !rally3d.core.LogView.visible && isFocused() && isVisible()) {
            if (!mouseLookActive) {
                try {
                    if (mouseRobot == null) mouseRobot = new Robot();
                    normalCursor = getCursor();
                    if (hiddenCursor == null) hiddenCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "hidden");
                    mouseLookActive = true;
                    setCursor(hiddenCursor);
                    centerMouse();
                } catch (Exception error) {
                    mouseLookActive = false;
                    LogConsole.warning("mouse", "Could not capture the mouse for camera control", error);
                }
            }
        } else releaseMouseLookCapture();
    }

    private void releaseMouseLookCapture() {
        if (!mouseLookActive) return;
        mouseLookActive = false;
        if (normalCursor != null) setCursor(normalCursor);
    }

    private Point mouseCenterOnScreen() {
        Component surface = gpuFailed ? softwarePanel : gpuSurface;
        Point p = surface.getLocationOnScreen();
        return new Point(p.x + surface.getWidth() / 2, p.y + surface.getHeight() / 2);
    }

    private void centerMouse() {
        if (!mouseLookActive || mouseRobot == null || !isShowing()) return;
        try { mouseRobot.mouseMove(mouseCenterOnScreen().x, mouseCenterOnScreen().y); }
        catch (IllegalStateException ignored) { rally3d.core.RLog.caught("GameWindow.recenter", ignored); }
    }

    private boolean handleMouseLookEvent(MouseEvent e) {
        if (!mouseLookActive || !mouseLookRequested || !isFocused()) return false;
        try {
            Point center = mouseCenterOnScreen();
            Point location = e.getLocationOnScreen();
            int dx = location.x - center.x;
            int dy = location.y - center.y;
            if (dx != 0 || dy != 0) Platform.addMouseLookDelta(dx, dy);
            if (location.x != center.x || location.y != center.y) mouseRobot.mouseMove(center.x, center.y);
        } catch (IllegalStateException ignored) { rally3d.core.RLog.caught("GameWindow.mouseLook", ignored); }
        return true;
    }

    /** In-window name editor: stays inside the game surface and never opens a second Java window. */
    void showInputOverlay(String title, String initial, int maxLen,
                          java.util.function.Consumer<String> callback) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showInputOverlay(title, initial, maxLen, callback));
            return;
        }
        final String oldSurface = gpuFailed ? "software" : "gpu";
        final JButton oldDefaultButton = getRootPane().getDefaultButton();
        JPanel overlay = new JPanel(new java.awt.GridBagLayout());
        overlay.setOpaque(true);
        overlay.setBackground(new Color(16, 18, 22));
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(35, 38, 43));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(235, 205, 80), 2),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)));
        JLabel heading = new JLabel(title == null ? "Player name" : title);
        heading.setForeground(Color.WHITE);
        heading.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        JTextField input = new JTextField(initial == null ? "" : initial, Math.max(8, Math.min(24, maxLen)));
        input.setMaximumSize(new Dimension(360, input.getPreferredSize().height + 8));
        input.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new DocumentFilter() {
            private String fit(String s, int length) {
                int room = Math.max(0, maxLen - (length - s.length()));
                return s.length() <= room ? s : s.substring(0, room);
            }
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                if (string != null) super.insertString(fb, offset, fit(string, fb.getDocument().getLength()), attr);
            }
            public void replace(FilterBypass fb, int offset, int length, String string, AttributeSet attr) throws BadLocationException {
                super.replace(fb, offset, length, string == null ? "" : fit(string, fb.getDocument().getLength() - length), attr);
            }
        });
        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        inputCancelButton = cancel;
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(save);
        buttons.add(cancel);
        card.add(heading);
        card.add(Box.createVerticalStrut(12));
        card.add(input);
        card.add(Box.createVerticalStrut(12));
        card.add(buttons);
        overlay.add(card);
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable finish = () -> {
            if (!completed.compareAndSet(false, true)) return;
            inputOverlayActive = false;
            inputCancelButton = null;
            panel.remove(overlay);
            surfaceCards.show(panel, oldSurface);
            panel.revalidate();
            panel.repaint();
            getRootPane().setDefaultButton(oldDefaultButton);
            (gpuFailed ? softwarePanel : gpuSurface).requestFocusInWindow();
        };
        save.addActionListener(e -> {
            if (completed.get()) return;
            String value = input.getText();
            finish.run();
            try { callback.accept(value); }
            catch (RuntimeException error) { LogConsole.error("input", "Failed to apply the entered value", error); }
        });
        input.addActionListener(e -> save.doClick());
        cancel.addActionListener(e -> {
            if (completed.get()) return;
            finish.run();
            try { callback.accept(null); }
            catch (RuntimeException error) { LogConsole.error("input", "Failed to cancel text input", error); }
        });
        overlay.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        overlay.getActionMap().put("cancel", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(java.awt.event.ActionEvent e) { cancel.doClick(); }
        });
        input.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        input.getActionMap().put("cancel", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            public void actionPerformed(java.awt.event.ActionEvent e) { cancel.doClick(); }
        });
        panel.add(overlay, "input");
        inputOverlayActive = true;
        surfaceCards.show(panel, "input");
        panel.revalidate();
        panel.repaint();
        getRootPane().setDefaultButton(save);
        SwingUtilities.invokeLater(() -> {
            if (!input.requestFocusInWindow()) input.requestFocus();
            input.selectAll();
        });
    }

    /** Map editor: while Canvas.rawPointer is set, every mouse press / move / release goes to the canvas raw queue. Returns true when consumed. */
    private boolean dispatchRawPointer(MouseEvent e, int action) {
        Canvas c = canvas;
        if (c == null || !Canvas.rawPointer) return false;
        int logicalWidth = Platform.logicalWidth(), logicalHeight = Platform.logicalHeight();
        if (logicalWidth <= 0 || logicalHeight <= 0 || e.getComponent().getWidth() <= 0 || e.getComponent().getHeight() <= 0) return true;
        double scale = Math.min(e.getComponent().getWidth() / (double) logicalWidth,
                                e.getComponent().getHeight() / (double) logicalHeight);
        int drawWidth = (int) Math.round(logicalWidth * scale);
        int drawHeight = (int) Math.round(logicalHeight * scale);
        int left = (e.getComponent().getWidth() - drawWidth) / 2;
        int top = (e.getComponent().getHeight() - drawHeight) / 2;
        int x = (int) Math.floor((e.getX() - left) / scale);
        int y = (int) Math.floor((e.getY() - top) / scale);
        boolean inside = x >= 0 && y >= 0 && x < logicalWidth && y < logicalHeight;
        if (!inside) {
            if (action != 2) return true;
            x = Math.max(0, Math.min(logicalWidth - 1, x));
            y = Math.max(0, Math.min(logicalHeight - 1, y));
        }
        int button = e.getButton();
        if (button == 0) {   // move / drag events carry the held buttons only in the modifiers
            int m = e.getModifiersEx();
            button = (m & java.awt.event.InputEvent.BUTTON3_DOWN_MASK) != 0 ? 3 : (m & java.awt.event.InputEvent.BUTTON2_DOWN_MASK) != 0 ? 2 : 1;
        }
        if (action == 0 && handleLogPointer(c, x, y, logicalWidth, logicalHeight)) return true;
        if (rally3d.core.LogView.visible) return true;
        c.dispatchRaw(action, x, y, 0, button);
        return true;
    }

    private void dispatchPointer(MouseEvent e, boolean pressed) {
        Canvas c = canvas;
        int logicalWidth = Platform.logicalWidth(), logicalHeight = Platform.logicalHeight();
        if (c == null || logicalWidth <= 0 || logicalHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        double scale = Math.min(e.getComponent().getWidth() / (double) logicalWidth,
                                e.getComponent().getHeight() / (double) logicalHeight);
        int drawWidth = (int) Math.round(logicalWidth * scale);
        int drawHeight = (int) Math.round(logicalHeight * scale);
        int left = (e.getComponent().getWidth() - drawWidth) / 2;
        int top = (e.getComponent().getHeight() - drawHeight) / 2;
        int x = (int) Math.floor((e.getX() - left) / scale);
        int y = (int) Math.floor((e.getY() - top) / scale);
        if (x < 0 || y < 0 || x >= logicalWidth || y >= logicalHeight) return;
        if (pressed && handleLogPointer(c, x, y, logicalWidth, logicalHeight)) return;
        if (rally3d.core.LogView.visible) return;   // overlay open: don't leak hover/clicks to the game menus
        if (pressed) c.dispatchPointerPressed(x, y);
        else c.dispatchPointerMoved(x, y);
    }

    /** LOG / TAB buttons and overlay panel taps. Returns true if the click was consumed. */
    private boolean handleLogPointer(Canvas c, int x, int y, int w, int h) {
        int b = rally3d.core.LogView.hit(x, y, w, h);
        if (b == rally3d.core.LogView.BTN_LOG) { LogConsole.toggle(); return true; }
        if (b == rally3d.core.LogView.BTN_TAB) {
            // mouse can't "hold" a key: the button toggles the scoreboard on/off
            boolean on = !rally3d.core.LogView.tabHeld;
            rally3d.core.LogView.tabHeld = on;
            c.dispatchKey(-13, on);
            return true;
        }
        return rally3d.core.LogView.visible && rally3d.core.LogView.panelTap(x, y, w, h);
    }

    private void dispatchWheel(MouseWheelEvent e) {
        if (rally3d.core.LogView.visible) {
            rally3d.core.LogView.scroll = Math.max(0, rally3d.core.LogView.scroll - e.getWheelRotation() * 3);
            return;
        }
        if (gameplayActive) {
            Platform.addCameraZoomDelta(e.getPreciseWheelRotation());
            return;
        }
        Canvas c = canvas;
        if (c != null) c.dispatchPointerWheel(e.getWheelRotation());
    }

    /** False once the OpenGL renderer gave up (context failures): everything runs in software. */
    boolean gpuUsable() { return !gpuFailed; }

    /** Called from the game thread after every flushGraphics(). */
    void present(javax.microedition.lcdui.Graphics g) {
        if (!gpuFailed && g.hasAlpha() && Platform.gpuLayered()) { presentLayered(g); return; }
        present(g.pixelBuffer(), g.physicalWidth(), g.physicalHeight());
    }

    /**
     * GPU layered frame: the 3D world already sits in a GL texture (Graphics3D FBO); the CPU UI layer is uploaded (changed rows only),
     * both are composited, FXAA'd and swapped right here on the game thread. No glReadPixels, no full-frame CPU copy, no AWT-thread hop.
     */
    private void presentLayered(javax.microedition.lcdui.Graphics g) {
        long started = System.nanoTime();
        final int[] ui = g.pixelBuffer();
        final int w = g.physicalWidth(), h = g.physicalHeight();
        final int world = javax.microedition.m3g.Graphics3D.getInstance().takeGpuWorldTexture(w, h);
        final javax.microedition.lcdui.UiRecorder rec = javax.microedition.lcdui.GraphicsBridge.recorder(g);   // non-null = GPU UI mode
        final double uiScale = g.scale();
        boolean ok = runGpu(new Runnable() { public void run() { gpuSurface.presentLayers(ui, w, h, world, rec, uiScale); } });
        if (!ok && !gpuFailed) { gpuLayerDropped++; if (gpuLayerDropped == 1 || gpuLayerDropped % 300 == 0) rally3d.core.RLog.w("gpu", "layered frame dropped (GL context unavailable), total " + gpuLayerDropped); }
        Platform.recordPerformance(4, System.nanoTime() - started);
        if (gpuFailed) {
            // The context is gone for good: show this frame in software. It lacks the GPU world, but the next one is a full software frame.
            present(ui, w, h);
        }
    }
    private int gpuLayerDropped;

    private void present(int[] src, int w, int h) {
        long started = System.nanoTime();
        if (!gpuFailed) {
            synchronized (frameLock) {
                if (gpuFramePixels == null || gpuFrameWidth != w || gpuFrameHeight != h) gpuFramePixels = new int[w * h];
                gpuFrameWidth = w;
                gpuFrameHeight = h;
                System.arraycopy(src, 0, gpuFramePixels, 0, Math.min(src.length, gpuFramePixels.length));
                gpuFrameSequence++;
            }
            requestGpuRender();
            Platform.recordPerformance(4, System.nanoTime() - started);
            return;
        }
        synchronized (frameLock) {
            if (front == null || front.getWidth() != w || front.getHeight() != h) {
                front = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                frontPixels = ((DataBufferInt) front.getRaster().getDataBuffer()).getData();
            }
            System.arraycopy(src, 0, frontPixels, 0, Math.min(src.length, frontPixels.length));
        }
        softwarePanel.repaint();
        Platform.recordPerformance(4, System.nanoTime() - started);
    }

    /** AWTGLCanvas.render() must run on the AWT event thread; coalesce queued frames. */
    private void requestGpuRender() {
        if (!gpuRenderQueued.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                long renderedSequence = -1L;
                try {
                    int[] frame;
                    int width, height;
                    synchronized (frameLock) {
                        renderedSequence = gpuFrameSequence;
                        frame = gpuFramePixels;
                        width = gpuFrameWidth;
                        height = gpuFrameHeight;
                        if (!gpuFailed && frame != null) {
                            // Publish the immutable frame to the GL surface before
                            // handing the previous displayed buffer back to the game.
                            // The AWT render can then proceed without holding frameLock.
                            gpuSurface.setFrame(frame, width, height);
                            gpuFramePixels = gpuDisplayedPixels;
                            if (gpuFramePixels == null || gpuFramePixels.length != width * height)
                                gpuFramePixels = new int[width * height];
                            gpuDisplayedPixels = frame;
                        } else {
                            frame = null;
                        }
                    }
                    if (frame != null) gpuSurface.render();
                } catch (RuntimeException | LinkageError error) {
                    failGpu(error);
                } finally {
                    gpuRenderQueued.set(false);
                    synchronized (frameLock) {
                        if (!gpuFailed && gpuFrameSequence != renderedSequence) requestGpuRender();
                    }
                }
            }
        });
    }

    boolean runGpu(Runnable task) {
        if (gpuFailed) return false;
        try {
            final long[] inner = new long[3];   // [0] = caps refresh / initGL, [1] = the task itself, [2] = lock + make current (time until the runnable starts)
            final long t0 = System.nanoTime();
            gpuSurface.runInContext(new Runnable() {
                public void run() {
                    long a = System.nanoTime();
                    inner[2] = a - t0;
                    if (gpuSurface.programReady()) gpuSurface.refreshCapabilities();
                    else gpuSurface.initGL();
                    long b = System.nanoTime();
                    task.run();
                    inner[0] = b - a; inner[1] = System.nanoTime() - b;
                }
            });
            long total = System.nanoTime() - t0;
            Platform.recordPerformance(11, inner[2]);                                                  // JAWT lock + make current
            Platform.recordPerformance(12, inner[0]);                                                  // caps refresh
            Platform.recordPerformance(13, Math.max(0L, total - inner[0] - inner[1] - inner[2]));     // release current + unlock (implicit glFlush)
            gpuRunFailures = 0;
            return true;
        } catch (RuntimeException | LinkageError error) {
            // A single failed context lock (JAWT_DrawingSurface_Lock while the AWT thread is painting / the window changes) used to disable the
            // GPU renderer for the whole session. Draw this frame in software and retry; only give up after GPU_MAX_RUN_FAILURES in a row.
            if (error instanceof LinkageError || ++gpuRunFailures >= GPU_MAX_RUN_FAILURES) { failGpu(error); return false; }
            if (gpuRunFailures == 1) LogConsole.warning("gpu", "OpenGL context unavailable for this frame; using software for it and retrying (gives up after " + GPU_MAX_RUN_FAILURES + " in a row)", error);
            return false;
        }
    }

    private static final int GPU_MAX_RUN_FAILURES = 30;
    private int gpuRunFailures;

    private void failGpu(Throwable error) {
        if (gpuFailed) return;
        gpuFailed = true;
        LogConsole.warning("gpu", "OpenGL renderer unavailable; switching to software fallback", error);
        Runnable showFallback = new Runnable() {
            public void run() { surfaceCards.show(panel, "software"); softwarePanel.requestFocusInWindow(); }
        };
        if (SwingUtilities.isEventDispatchThread()) showFallback.run();
        else SwingUtilities.invokeLater(showFallback);
    }

    void applyMode() {
        if (SwingUtilities.isEventDispatchThread()) { applyModeNow(); return; }
        try { SwingUtilities.invokeAndWait(new Runnable() { public void run() { applyModeNow(); } }); }
        catch (Exception error) { LogConsole.error("window", "Could not update the window mode", error); }
    }

    /** alpha57: borderless fullscreen = oversized decorated window, Windows does not hide the taskbar for it. Topmost + toFront puts the window over the taskbar. */
    private void raiseOverTaskbar() {
        try {
            if (!Platform.fullscreen || !isDisplayable() || !isVisible()) return;
            if (!isAlwaysOnTop()) setAlwaysOnTop(true);
            toFront();
        } catch (Exception ex) { LogConsole.warning("window", "raiseOverTaskbar failed", ex); }
    }

    /** Topmost only while the game has focus and is fullscreen: Alt+Tab to other windows works normally. */
    private void dropTopmost() {
        try { if (isAlwaysOnTop()) setAlwaysOnTop(false); }
        catch (Exception ex) { LogConsole.warning("window", "dropTopmost failed", ex); }
    }

    private void applyModeNow() {
        boolean fs = Platform.fullscreen;
        if (!fs) dropTopmost();
        GraphicsDevice dev = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle screen = dev.getDefaultConfiguration().getBounds();
        boolean wasVisible = isVisible();
        // alpha56 fix: with the GL canvas alive, dispose()/setUndecorated() destroys the native peer under the render thread's GL context
        // (native crash on F11). Keep the peer: stay decorated and push the title bar / borders off-screen so the client area covers the monitor.
        if (!gpuFailed && (isDisplayable() ? !isUndecorated() : true)) {
            if (!isDisplayable()) setUndecorated(false);
            if (fs) {
                if (!wasVisible) { setResizable(true); pack(); setVisible(true); }
                java.awt.Insets in = getInsets();
                setBounds(screen.x - in.left, screen.y - in.top, screen.width + in.left + in.right, screen.height + in.top + in.bottom);
            } else {
                setResizable(true);
                int w = Platform.displayWidth(), h = Platform.displayHeight();
                Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                double k = Math.min(1.0, Math.min((usable.width - 16) / (double) w, (usable.height - 48) / (double) h));
                panel.setPreferredSize(new Dimension((int) (w * k), (int) (h * k)));
                pack();
                setLocation(usable.x + Math.max(0, (usable.width - getWidth()) / 2), usable.y + Math.max(0, (usable.height - getHeight()) / 2));
                setVisible(true);
            }
            if (!isVisible()) setVisible(true);
            if (fs) raiseOverTaskbar();
            gpuSurface.requestFocusInWindow();
            panel.repaint();
            return;
        }
        if (wasVisible && isUndecorated() != fs) { setVisible(false); dispose(); }
        setUndecorated(fs);
        if (fs) {
            setResizable(false);
            setBounds(screen);
        } else {
            setResizable(true);
            int w = Platform.displayWidth(), h = Platform.displayHeight();
            Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            double k = Math.min(1.0, Math.min((usable.width - 16) / (double) w, (usable.height - 48) / (double) h));
            Dimension d = new Dimension((int) (w * k), (int) (h * k));
            panel.setPreferredSize(d);
            pack();
            setLocation(usable.x + Math.max(0, (usable.width - getWidth()) / 2), usable.y + Math.max(0, (usable.height - getHeight()) / 2));
        }
        setVisible(true);
        if (fs) raiseOverTaskbar();
        (gpuFailed ? softwarePanel : gpuSurface).requestFocusInWindow();
        panel.repaint();
    }

    // ---- keyboard ----
    private void releaseAllKeys() {
        Canvas c = canvas;
        if (c != null && Canvas.rawPointer) c.dispatchRawKeyReset();
        for (Map.Entry<Integer, Integer> key : held.entrySet()) {
            int code = key.getValue().intValue();
            if (code != 1 && c != null) c.dispatchKey(code, false);
        }
        held.clear();
        for (Timer timer : pendingRelease.values()) timer.stop();
        pendingRelease.clear();
    }

    private void onKey(KeyEvent e, boolean down) {
        int vk = e.getKeyCode();
        if (vk == KeyEvent.VK_F1) {
            if (down && e.getID() == KeyEvent.KEY_PRESSED) LogConsole.toggle();
            e.consume();
            return;
        }
        if (Canvas.rawPointer && canvas != null && vk != KeyEvent.VK_F11) {   // map editor: keys go to the raw key queue (shortcuts need the modifiers)
            int mods = (e.isControlDown() || e.isMetaDown() ? 1 : 0) | (e.isShiftDown() ? 2 : 0) | (e.isAltDown() ? 4 : 0);
            if (down) canvas.dispatchRawKey(vk, mods, e.getKeyChar());
            else canvas.dispatchRawKeyRelease(vk, mods);   // the fly view needs real key-up events (held WASD)
            e.consume();
            return;
        }
        if (down && (vk == KeyEvent.VK_F11 || (vk == KeyEvent.VK_ENTER && e.isAltDown()))) {
            if (e.getID() == KeyEvent.KEY_PRESSED && !held.containsKey(vk)) {
                held.put(vk, 1);
                Platform.fullscreen = !Platform.fullscreen;
                Platform.save();
                applyModeNow();
            }
            return;
        }
        int code = KeyMap.map(e);
        Canvas c = canvas;
        if (down && vk == KeyEvent.VK_BACK_SPACE && c != null) c.dispatchTextCommand(Canvas.TEXT_BACKSPACE);
        if (code == 0) return;
        if (down) {
            Timer t = pendingRelease.remove(vk);
            if (t != null) t.stop();                       // X11 auto-repeat sends release+press pairs
            if (held.containsKey(vk)) return;
            held.put(vk, code);
            if (c != null) c.dispatchKey(code, true);
        } else {
            final int fvk = vk, fcode = code;
            Timer t = new Timer(8, new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent ev) {
                    pendingRelease.remove(fvk);
                    held.remove(fvk);
                    Canvas cc = canvas;
                    if (cc != null) cc.dispatchKey(fcode, false);
                }
            });
            t.setRepeats(false);
            pendingRelease.put(vk, t);
            t.start();
        }
    }

    private final class SoftwarePanel extends JPanel {
        private static final long serialVersionUID = 1L;
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            synchronized (frameLock) {
                if (front == null) return;
                int pw = getWidth(), ph = getHeight(), iw = front.getWidth(), ih = front.getHeight();
                double k = Math.min(pw / (double) iw, ph / (double) ih);
                int dw = (int) Math.round(iw * k), dh = (int) Math.round(ih * k);
                int dx = (pw - dw) / 2, dy = (ph - dh) / 2;
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        dw == iw ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(front, dx, dy, dw, dh, null);
            }
        }
    }

}
