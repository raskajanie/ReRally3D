# GPU_MIGRATION.md — plan: full GPU graphics (PC). For next agent.

Read README.txt RULES FOR AGENTS first (full archive delivery, version bump in 3 places, `python tools/instrument.py`, RLog logging).
Honest assessment > impossible promises. Deliver FULL archive `rally3d-2_0-alpha<N>.zip`.

## 1. Facts (verified in source + user logs, alpha44-48)
- Frame today: `drawWorld` (CPU sky -> `pix`) -> `Graphics3D.bindTarget` -> `flushGpu`: `uploadPixels(pix)` into FBO -> GPU draw -> `consumeGpuReadback`/`queueGpuReadback` (`glReadPixels` colour BGRA + depth short, PBO ring + fences) -> CPU HUD/menus (`lcdui.Graphics` on AWT `BufferedImage`) -> `GameWindow.present(int[])` copy -> `GpuSurface.paintGL` PBO upload -> quad + FXAA.
  => per frame at 1080p internal: 2 full uploads + 1 readback + single-thread loops. Log rally3d-1 (alpha45, GPU raster): 72 FPS, GPU wait/readback 11.8 ms, CPU 13.9 ms. Log rally3d-0 (alpha44): GL context lost (`JAWT_DrawingSurface_Lock failed`) -> software raster 144 FPS, raster 1.2 ms. Same map (Night race), 1920x1080 internal, desktop GPU, 16 threads.
- GL context lives in `AWTGLCanvas` (`GpuSurface`) on AWT thread; `GameWindow.runGpu` does `runInContext` from game thread -> source of JAWT lock failures (retry up to 30, then `failGpu`).
- Sprites: GPU path already depth-tests billboards (`renderGpuBillboards`, `beginBillboardBatch`). Only CPU consumer of depth map after 3D: `Billboard.java:110` (`isDepthMapValid`).
- Software raster: `Graphics3D.flushSoftware`, parallel row bands (`PARALLEL_BANDS` = 2/core, max 48), `parallelRows` helper (alpha47) for clear + GPU readback loops.
- Done so far: alpha46 checker menu fill (`BaseCanvas.checkerBackground/wideBar/fillChecker`) + `Platform.gpuRaster` (default false = software, menu Settings -> Display / FPS (PC) -> "3D renderer"); alpha47 all-core raster/clear, run-gpu.bat heap `-Xms384m -XX:MaxRAMPercentage=15`; alpha48 camera-at-road-level fix (`RallyGame.updateCamera` pitch floor).
- GPU raster default stays OFF until stage 1 lands (readback makes it ~2x slower than parallel software here).

## 2. Goal
No CPU round trip: 3D renders straight into the presentation pipeline; UI eventually GPU too. Expected: GPU frame = draw calls + vertex build; readback/upload gone; headroom for >1080p internal.

## 3. Stages (each shippable; keep software fallback + `gpuRaster` switch)
**STATUS alpha55: GPU UI verified by user log (1090-1213 FPS race, no errors); menu car preview was lost (software 3D -> CPU buffer never uploaded), fixed via Graphics.recordPixelBlit. Still CPU-rasterised in menus: car preview (blit), shadows, FlyView. Next: user confirms visuals (HUD, info screens), then default UI renderer ON, then move car preview to GPU (gpuProgram into an FBO region).
**STATUS alpha54: the 144 FPS cap was the NVIDIA driver V-sync override (user turned it off: 825-900 FPS, GPU idle). Stage 4 is NOT needed for FPS. Stage 3a done (not run): lcdui Graphics records ops (UiRecorder) -> GpuUi replays into a premultiplied RGBA UI FBO (setting "UI renderer", default OFF). Next: user logs + screenshots with UI renderer GPU; then fix visual diffs (text position, lines), consider default ON, then stage 5 (render thread) only if profiling shows the game thread is the limit.
**STATUS alpha53: game is present-bound at the monitor refresh (144): ~6.2 ms of 6.94 blocked in GL context unlock after swap; engine work ~0.8 ms. alpha53 logs swap interval + splits lock/unlock. Next: read those numbers; if still capped -> stage 4 (GLFW window).
**STATUS alpha52: alpha51 had a GL.getCapabilities() regression (forced software fallback), fixed. GL context overhead measured ~1 ms/frame, not the main cost; ~2 ms of 'frame snapshot' still unexplained.
**STATUS alpha51 (later than the text below): parity measured good (see README alpha51); CPU-bound at ~137 FPS, GPU idle (0.04 ms) -> GL context lock/make-current + caps refresh suspected, fixed caps part, measure "GL ctx lock+switch" next; if large -> stage 4. Intro screen black fix + unlimited FPS done.
**STATUS alpha50: stage 1 DONE and verified by user log (desktop GPU, 143 FPS at cap 144, no GL errors). Stage 2a done (GPU timers + parity harness, not run). Next agent: ask the user for run-gpu-parity.bat output ([gpu-parity] lines + rally3d-parity-*.png) and an unlimited-FPS [perf] log for software vs GPU, then fix the biggest parity diffs; only then consider flipping `gpuRaster` default. See README alpha49/alpha50.**
1. **Kill readback.** [DONE alpha49 - unverified on GPU; UI layer upload = dirty 16-row bands, no PBO ring yet] Layered frame: (a) background/sky = GPU quad from sky texture scrolled by yaw; (b) 3D = FBO at internal res; (c) UI = separate RGBA CPU layer, upload dirty rects only. Final shader composites a+b+c, upscales, FXAA (`GpuSurface` shader). Delete `uploadPixels(pix)`, `consumeGpuReadback`, `queueGpuReadback`, `GameWindow.present` copy. CPU sprite path off in GPU mode. Biggest win, moderate risk.
2. **Parity 3D** [harness DONE alpha50: run-gpu-parity.bat; fixes pending real numbers] (`gpuProgram` vs software raster): perspective-correct UV, vertex colour x texture, alpha test, `softDepthWrite` semantics, cull, blend, texture filtering/mipmaps (FBO now NEAREST). Test: render same frame both paths, pixel diff. Remaining CPU bits: `drawCarPreview` (menus), shadows (`shadow*.png`), FlyView. Optional: static VBO road meshes + vertex-shader transform; first parallelise `appendGpuTriangle`.
3. **GPU 2D** replacing `javax.microedition.lcdui.Graphics`: textured-quad batcher (+ atlases), `fillRect`/`fillRectAlpha`/`drawRect`/lines, clip -> `glScissor`, text via on-demand glyph atlas from AWT `Font` (RTL `Texts.rightToLeft`, `lang.zh-CN/zh-TW`, outline/shadow in `drawText`). Dynamic images (`Image.createRGBImage`, `getRGB`, `Painter`, `MapEditor`, `PngCodec`, `Image.getGraphics` offscreen, ~5 sites in game/) -> texture with dirty flag or render-to-texture. Menu checker -> texture/shader.
4. **GLFW window instead of AWT canvas** (LWJGL already used, gamepad on GLFW main thread). Removes `AWTGLCanvas`/JAWT failures, gives swapInterval + true fullscreen. Rewrite input: keys, mouse, wheel, mouse-look capture. Replace Swing `SettingsDialog` + `Platform.inputDialog` overlay with in-game screens (`editTextInGame` exists). Riskiest: touches all input/window code. Separate branch.
5. **Render thread.** GL context owned by render thread; game thread builds command lists (3D tris + 2D batch) into double/triple buffers (orphaning or `glMapBufferRange` unsynchronized). Pipeline 1-2 frames. Dynamic resolution = FBO resize.
6. **Android** (GLES3; own `Graphics3D` 823 lines + `GameView`): only after PC. Shared `game/` code uses lcdui API, so 2D renderer must exist on both or Android keeps CPU 2D.

## 4. Safety / testing
- Keep software fallback; auto-switch on context failure (`GameWindow.failGpu`).
- Log GL errors (`glGetError` in debug), new `[perf]` entries; add GPU timers (`GL_TIME_ELAPSED`) to separate GPU vs CPU bottleneck. Rule 3: no per-frame logging.
- Agent sandbox has NO GPU/display and no javac binary. Compile check works: `java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -cp "<source/lib/*.jar without natives>" -d /tmp/out $(find source/game source/pc -name "*.java")` (no `--release`). Android check: `-cp tools/android.jar` with `source/game source/android`. Visual/perf verification = user's logs + screenshots after each stage; say so honestly.
- Hot per-pixel helpers must not be instrumented (add to `HOT_NAMES` in `tools/instrument.py`).

## 5. Warnings
- Realistically a rewrite of the lcdui layer + window: thousands of lines, not one iteration. Do stage 1 first, ship, get logs.
- CPU-core gain is limited: software frame CPU was ~3 ms at 144 FPS cap. GPU benefit = no readback/upload + higher resolution.
- Known open issue: camera can still be covered by terrain rising behind the car (no terrain check at camera position).
- Unverified: pixel parity GPU vs software on all 12 tracks; GPU path behaviour after alpha47 parallel readback (user default is software).
