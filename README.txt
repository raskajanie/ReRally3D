GPU graphics migration plan for agents: GPU_MIGRATION.md (read before touching Graphics3D / GpuSurface / lcdui).

BUILD (Windows, nothing to install - the JDK and all tools are in tools\):
  build-all.bat  (or build-all.ps1: right click -> Run with PowerShell)  -> Rally3D-Java21-GPU.jar + Rally3D-Android.apk
  compilepc.ps1       -> PC jar only, run it with run-gpu.bat
  compileandroid.ps1  -> APK only (signed with source\android-pkg\debug.jks, do not delete it)
No Android SDK needed: tools\android.jar (API 34 stubs), dx.jar, apksigner.jar and ApkPack.java replace the SDK.
If you edit source\android-pkg\AndroidManifest.xml, recompile it with aapt into source\android-pkg\prebuilt (AndroidManifest.xml + resources.arsc + res); otherwise the APK keeps the old manifest.

SOURCES:
source\game, source\res   - shared by PC and Android (edit once)
source\pc, source\lib     - PC only
source\android, source\android-pkg - Android only (touch controls: source\android\rally3d\android\GameView.java)

LOGS: inside the game, no separate windows. PC: F1 only (no LOG button); Android: LOG button (always top right, left of the pause button in a race).
The TAB button (next to LOG) exists only in a multiplayer race: PC - click toggles the table, Android - hold.
Log panel: tap the top strip to change the filter (ALL / WARN+ERR / ERR); scroll with the mouse wheel (PC) or by dragging a finger (Android).
Code: source\game\rally3d\core\LogView.java, RallyGame.drawLogOverlay, pc\...\LogConsole.java (file rally3d-N.log).

DIAGNOSTICS / FULL LOGGING (Android):
Files: Android/data/com.rally3d.android/files/ -> rally3d.log (+ .1, .2; 2 MB each), crash.txt, rally3d-trace.log (only if full trace is on), session.running.
Written by a background thread (does not slow the game down). ERROR is flushed to disk immediately. The same lines are visible in the in-game LOG panel.
What is logged (tag): app/env/lifecycle/surface (start, version, device, RAM, onPause/Resume/Stop/Trim/LowMemory, focus, surface),
  session (the previous run did not exit cleanly + Android's exit reason: ANR/CRASH_NATIVE/LOW_MEMORY...), menu (which menu, items, what was chosen),
  track/race/save/mp (track loading, race start/end, saves, multiplayer), crash (Java crash: stack + last function calls + last key presses),
  freeze (game loop draws no frames for >2.5 s OR the Android main thread hangs >3.5 s: all-thread stack dump + function trace + input),
  hitch (a frame longer than 200 ms), slow (a function ran >300 ms), perf (every 30 s: fps, worst frame, memory, TOP-8 functions by time),
  memory (heap >85%), caught (EVERY caught exception: the first one with a stack, duplicates are counted), trace (automatic timing shut-off for functions that are called too often).
Function tracing: every game function writes entry/exit into an in-memory ring buffer (the last 256 calls per thread) - it reaches the disk on
  crash/freeze/error. Full stream of all calls into a file: Settings -> "Full function trace log" (the file is huge, turn it on only to hunt a bug).
RULES FOR AGENTS:
  0) !!! DELIVERY (STRICT, NO EXCEPTIONS): the user ALWAYS gets the FULL archive of the whole rally3d/ project with ALL changes (rally3d-<version with _>.zip, see "ARCHIVE FOR THE USER" below).
     NEVER a zip with only changed files, NEVER a .diff instead of the archive. Check before sending: the file count is not smaller than the previous archive, tools\jdk and debug.jks are inside.
  1) Added or changed functions/catch blocks -> run:  python tools/instrument.py  (idempotent, updates source\game\rally3d\core\FnTable.java; there must be no empty catch blocks).
  2) Log every new error/problem: RLog.e / RLog.w / RLog.i(tag, text); an exception you swallow yourself -> RLog.caught("Class.method", e).
  3) Do not log every frame in hot loops (use a counter/rate limit); the runtime switches hot functions off from timing by itself.
  4) Before a release version N.0 (without alpha/beta) run the game and make sure the log has no freeze/crash/ERROR; otherwise use alpha/beta.
SAVES (Android): Android/data/com.rally3d.android/files (cars, tracks, records, ghosts, settings). No storage permissions are needed
and none may be added (MANAGE_EXTERNAL_STORAGE / READ/WRITE_EXTERNAL_STORAGE are forbidden). On update, old data moves by itself from the internal
folder (rally3d\android\Backup.migrateLegacy). Uninstalling the game removes the data with it (except a possible Google Auto Backup, allowBackup=true).
CAMERA (Android): the "Mouse camera" setting (menu, Modified mode) = the camera orbits the car by dragging one finger on the free area of the screen
(not buttons, not the joystick); a quick double tap puts the camera back behind the car. Code: GameView.raceTouch + Platform.addMouseLookDelta, LOOK_GAIN = sensitivity.
Menus: finger scrolling (swipe + inertia) in all lists and text screens.

SPECTATING (multiplayer): after a player finishes and the car stops, they watch whoever is still driving (camera behind that car, in real time).
  PC: A / D (or arrows) - switch player; Android: the steering arrows on the left, the right pedals are hidden. The hint at the top of the screen appears only after the finish.
  When the last player finishes - results screen and the next game. Code: RallyGame.updateSpectate / beginSpectate / followWatchedCar / drawSpectateHud.
  Spectating follows the primary opponent only. 3+ players: every guest packet (state 4, info 6) is relayed by the host as 9 / 11 with the origin id (TheBluetooth.relayToGuests);
  the extra cars live in RallyGame.extras (RemoteCar), drawn with spin slots 3+id; add-on packs of other guests are re-offered by the host in relay slots (AddonSync.vslot).
  Network: the car state is sent 20 times/s (nextSyncTime), ping/pong is answered by the network thread (TheBluetooth.receiveLoop) - TAB shows the real RTT of the client and the host.

VERSIONS (MANDATORY FOR EVERY AGENT THAT CHANGES THE GAME):
Current version: 2.0-alpha56 (code 51)
After EVERY change to the game (code, resources, manifest) - raise the version. Not changing the version = a violation.
How to pick the version (N = current whole version + 1):
  sure that everything works (built, ran, checked)  -> whole version: N.0   (e.g. 1.0 -> 2.0). No alpha/beta.
  not very sure (did not run it, big/risky edits, might be broken) -> N.0-alpha   (e.g. 2.0-alpha)
  slightly unsure (ran it, but did not check everything, minor doubts)           -> N.0-beta    (e.g. 2.0-beta)
If the previous version was alpha/beta and you are polishing it: do not raise the number N, change only the suffix
  (alpha -> alpha2 / beta; beta -> beta2 / whole N.0 if you are now sure).
A whole version N.0 is released only when the agent is sure. After it the next agent takes N+1 again.
Where to change it (identical everywhere):
  0) source\game\rally3d\core\Version.java: NAME and CODE - the MAIN source of the version (PC + Android). It is shown in the main menu at the bottom left ("v2.0-alpha4"),
     in the log, and multiplayer lets ONLY identical versions play together (the NAME string must match character by character; PC and Android with each other too).
  1) source\android-pkg\AndroidManifest.xml: android:versionName="<the same NAME>" and android:versionCode=<the same CODE> - CODE ALWAYS +1, even for alpha/beta.
     If Version.java and the manifest differ, Android logs ERROR "VERSION MISMATCH" at start.
  2) Recompile the manifest with aapt into source\android-pkg\prebuilt (see BUILD) - otherwise the APK keeps the old version.
  3) The "Current version:" line above in this README (and rebuild the PC jar + APK: build-all.bat).
  4) Rebuild (build-all) and tell the user the final version and why it is alpha/beta/whole.

MULTIPLAYER AND VERSIONS: the host sends its version in the beacon (RALLY3D|2|...) and checks it in the client's hello (type 17). Different versions do not play together:
in the list, hosts of another version are marked "[vX - INCOMPATIBLE]", the join is rejected (reason 4) with the message "Different game version", builds older than 2.0-alpha4 are rejected too.
If you changed the LAN protocol (TheBluetooth) - you MUST raise the version, otherwise old and new clients join together and break.

SAFE SAVING: write any player file (records, settings, ghosts, RecordStore) ONLY through rally3d.core.SafeFile.write (tmp -> fsync -> .bak -> rename).
A direct FileOutputStream onto a save file is forbidden: a crash/process kill during the write ruins progress. Reading: main -> .bak; a broken file is renamed to .corrupt, never silently overwritten.

CONTROLS (Android): bottom left - two steering arrows (< and >, keys -3/-4), right - pedals GAS (organ type, key -5) and BRAKE (hanging type, -2), drawn like real pedals
(GameView.drawPedal/rubberPad/steel), at the top II (pause) and LOG. There is no joystick any more.
BUTTON POSITIONS: Settings -> "Touch buttons position (drag)" (RallyGame case 18 -> Platform.showButtonLayoutEditor -> GameView.startLayoutEditor/editTouch).
Drag any of the 5 buttons (gas, brake, pause, <, >); DONE saves, RESET = default layout, CANCEL = roll back. Stored in settings as btnX0..4/btnY0..4
(fractions of the screen, button centre; -1 = default), Platform.btnPosX/btnPosY, applied in GameView.layoutButtons. Size - a separate "Touch buttons size" slider.
SPRITE LOD: "Disable sprite LOD" is on by default (Platform.noSpriteLod = true, key noSpriteLod); it has no effect in Vanilla mode.
The free area of the screen (not buttons) = finger camera (see CAMERA).
SPRITES (bushes, trees, roadside rocks): size is set in RoadSegmentMesh.spriteTargetHeight = height of frame 0 * SPRITE_SCALE (0.5) * viewH/240,
beyond SPRITE_FULL_SIZE_DISTANCE (90) it shrinks as 1/distance; the LOD frame (6 of them in the .off) is picked by lodFrameFor for the needed size, without stretching.
Want them bigger/smaller - change SPRITE_SCALE. Finish/checkpoint/person (isStaticSprite) are computed separately and are not affected.

MULTIPLAYER (race start): a type 6 packet (name/car) may arrive while the second player is still in the syncStart lobby loop - it is remembered (RallyGame.pendingPlayerInfo) and taken in exchangePlayerInfo;
on receiving another player's type 6 we answer with our own. Without this the client hung on the lobby frame for 20 s while the host was already driving.

ARCHIVE FOR THE USER (MANDATORY FOR EVERY AGENT): ALWAYS hand over the FULL archive of the whole rally3d/ project with ALL changes and ALL files:
  source\ (game, pc, android, android-pkg incl. prebuilt and debug.jks, res, lib), tools\ (INCLUDING jdk, android.jar, dx.jar, apksig*.jar, ApkPack.java, instrument.py, env.ps1),
  build\, *.ps1, *.bat, README.txt. Do NOT send only changed files/patches, do NOT cut tools\jdk and the compilers, do NOT cut .jks/resources: the user must unpack it and run build-all.bat right away.
  Format: rally3d-<version with _ instead of .>.zip (e.g. rally3d-2_0-alpha14.zip), the root inside the archive = the rally3d/ folder. Before sending, compare the file count with the previous archive (it must not shrink without a reason).
  The binary manifest source\android-pkg\prebuilt\AndroidManifest.xml must match the version (there is no aapt - if needed patch the string pool/versionCode with a script, as in alpha14).
  Do not put machine-specific files (logs with user paths, user names) into the archive.

LAN DISCOVERY (alpha15): the host sends a UDP 47833 beacon every 700 ms AND answers a DISCOVER request by unicast (TheBluetooth.startDiscoveryResponder) - a phone that filters broadcast still finds the host
  and gets its LAN address. The client sends DISCOVER to 255.255.255.255 and to the broadcast address of every interface. Android holds a WifiManager.MulticastLock (MainActivity, while the app is open).
  In the host lobby the IP list shows the LAN address first (192.168/10/172.16-31), VPN addresses (Radmin 26.x, Hamachi 25.x) - in brackets after it. The phone needs the host's LAN IP, not the VPN IP.
SKY (background): vertical offset when the camera tilts = (viewH/2)*tan(pitch)/tan(30 deg) (RallyGame.drawWorld); it used to be linear height/180 per degree (about 2.7 times too small).

MAX SPEED (alpha16): the speed ceiling = RallyGame.statSpeed[selected car]/10 in ALL modes, multiplayer included (before, everyone in MP drove with statSpeed[2] -> Red danger accelerated to 112 instead of ~90 km/h).
  On-screen km/h = car.speed*15: Red danger 90 (60), Azure racer 102 (68), Fast racer 112 (75), Good oldie 120 (80). The hard cap car.speed<=f4 sits in updateRace right before the velocity calculation;
  the opponent's speed from the type 4 packet is also clamped to its car's ceiling (+2%) in updateOpponent.
NICKS IN MP: your own nick is not drawn above your car; the opponent's nick shrinks with distance from the camera (100% up to ~15 units, minimum 50%) - Font.getSizedFont (PC and Android), cache RallyGame.nameFontCache.


Ghost Race (alpha21): recorded per track+car pair (ghosts/track-N-car-C.rpl), time in ms (format v2, v1 is still read), the old track-N.rpl is used only if the car matches. Default volume: Loud (new profiles only).

alpha22: in Ghost Race, after choosing a car, a "Select ghost" menu appears - you can race against a ghost of any car (the record is saved for the car you drove).

alpha23: ADD-ONS (main menu -> "Add-ons"). Zip packs: cars, skins, wheel skins, speed/accel/steer/steerMax/grip. On/off on the fly, enabled cars appear in car select.
  Folder: PC ~/.rally3d/addons, Android Android/data/com.rally3d.android/files/addons; "Add zip" button (Platform.importAddons, Android - ACTION_OPEN_DOCUMENT, MainActivity.onActivityResult).
  Zip format: addon.properties (name, base=1..4, speed, accel, steer, steerMax, grip, type=wheels), car.syn, car.png, wheel.png, wheel.syn. Examples: examples\. OBJ->syn: python tools/obj2syn.py m.obj car.syn
  Multiplayer: packets 20 OFFER[...,slot] / 21 REQUEST[slot] / 22 CHUNK[slot,off,data] (AddonSync, network thread; slot 0 = car, 1 = wheels), the opponent downloads the zip (MD5), after exchangePlayerInfo -> RallyGame.syncAddonCars. Protocol changed -> version raised.
  Code: AddonPack (parser/validation), AddonManager (folder, enabled.txt), AddonSync, CarDef, RallyGame.addonsMenu/carDef/addonCar/refreshLocalCars. type=wheels - wheel skin only locally.
  Rally3D-Java21-GPU.jar and Rally3D-Android.apk rebuilt (alpha23; apk signed with debug.jks, v2/v3 verify OK). Not run on a GPU/device.
  Test skin: examples\example-test-skin-blue.zip (addon.properties: name=Test Blue, base=1; car.png 128x128, UV of stock car 1, blue recolour + white stripe on the roof).

  alpha23 audit (fixed): a non-power-of-two PNG is rejected with an error (before, the pack switched on but the car was missing from the list); a repeated OFFER after a fail resets failed; duplicate/broken chunks are not counted; pack name without control characters; rescan rereads a swapped zip (mtime); GPU textures of foreign packs are released (Graphics3D.releaseTexture). Known: ghost records for add-on cars are not saved (stock 0..3 only).

alpha24: WHEELS ADD-ONS visible to everyone. type=wheels (wheel.png and/or wheel.syn) fits any of your cars, one wheels pack is enabled at a time (a new one switches the previous off). In multiplayer the wheels pack goes to the opponent as the second AddonSync slot; order: the car's own wheels -> the driver's wheels pack -> stock. The opponent's wheels do not take your pack (drawCar spinSlot==1 -> RallyGame.opponentWheels). Protocol changed (slot) -> version alpha24, code 25 (Version.java, AndroidManifest.xml, prebuilt/AndroidManifest.xml). Test wheels: examples\example-test-wheels-lime.zip (type=wheels, wheel.png 32x32).

alpha25: NEW ADD-ONS MENU (main menu -> "Add-ons"). Code: game\...\AddonUi.java (drawing + input), hook in RallyGame.drawMenu (field addonUi) and RallyGame.addonsMenu.
  3 tabs on top: MAPS / CARS / WHEELS (left/right on the keyboard = switch tab). A row: green square = on/off (tap), tap on the row = expand details
  (author, description, stats, file + size, big TURN ON/TURN OFF button), a red [X] on the right of EVERY item = delete completely (zip + enabled.txt) with a confirmation.
  Bottom: "+ ZIP" (import) and "Back". Scrolling: wheel / swipe with inertia. A dot on a tab = something inside is enabled.
  Custom maps are a PLACEHOLDER: addon.properties type=map (name, author, description) -> the pack is listed in the MAPS tab, can be enabled/deleted, but the game does not load it yet.
  Empty "[ empty slot: map ]" cards show where maps will appear. Hook for later: AddonManager.enabledMaps() (list of enabled map packs) - plug the track loader in here.
  AddonPack.mapOnly / desc, AddonManager.ofKind(kind)/kindOf(p); map packs are excluded from enabledCars and from network receiving (AddonSync -> RallyGame.syncAddonCars). The LAN protocol did not change.
  Checked: PC and Android compile (javac 21), the screen was rendered headless to a screenshot, scripted click/key/delete test passed. Not run on a device/GPU -> alpha.

alpha26: ALL TEXT IN ENGLISH (add-ons screen, help text, menu entry, README, code comments). The add-ons help screen shows the add-ons folder with the home directory replaced by "~" (AddonUi.shownDir), so no account name appears on screen. Removed a stray log file with a user path from the project.

alpha41: MAP EDITOR (main menu > Map Editor)
  - Modes PATH / HEIGHT / LAND / PROPS / MARKS / WORLD; tap ground = grow road; drag orange ball at road end = stretch road any direction (terrain/props continue);
    drag yellow handles on the bottom strip = stretch/shrink the selected part like a clip; 4 custom paintable sprites, painted sky/road texture; TEST drives the map.
  - Shortcuts (PC/keyboard): Ctrl+C/X/V/D copy/cut/paste/duplicate segments, Ctrl+Z / Ctrl+Y undo/redo, Ctrl+A select all, Ctrl+S save, Del delete, arrows select, Esc menu.
  - SAVE writes add-ons/maps/<name>.zip (type=map: addon.properties, track.bin, optional background.png, squares.png, spriteN.png/.off) and enables it.
  - Enabled maps are listed FIRST in Time Trial, Ghost Race and Multiplayer (host sends the map zip to guests, new AddonSync map slot + packet 24). Not in the Championship.
  - Protocol changed (AddonSync slots 2->3): host and guests must both run alpha41.
  - Checked: PC compiles, model + engine-loader tests pass (editor geometry = engine geometry), editor screens rendered headless. NOT checked: Android build, real GPU/touch, MP transfer on a network.

alpha42: MAP EDITOR FINISHED (not run on a GPU / device -> alpha)
  - Top bar: FLY (free-fly preview, W A S D / joystick, up + down), TEST, SAVE. File menu: Save, Export zip, Test, Fly, Info, New blank, New from a TEMPLATE, New from stock, Open, Help (8 pages), Exit.
  - PATH panel has touch buttons COPY / CUT / PASTE / DUP / SELECT ALL. Shortcuts act on key-DOWN only (MapEditor.rawKey ignores key-up):
    Ctrl+Z/Y/Shift+Z, C, X, V, D, A, S (save), E (export zip), T (test), F (fly), N (new), O (open); Del, arrows (+Shift extends), PgUp/PgDn, Home/End, 1-6 mode, Tab/Shift+Tab mode, F fit, + / - zoom, H help, Esc file menu.
    Android hardware keyboard: MainActivity.rawCode now maps digits, Tab, Home/End, PgUp/PgDn, +/-.
  - Export zip: Platform.exportFile(name, bytes) -> PC save dialog (AWT FileDialog on Windows, JFileChooser elsewhere, headless -> addons/export), Android ACTION_CREATE_DOCUMENT (request 7702, MainActivity.onActivityResult -> Platform.onAddonExported). Map zips are capped at 600 KB (AddonPack.MAX_ZIP) so the 700 KB Android import limit is never hit.
  - Templates (MapTemplates.java): 7 tracks (sprint, mountain pass, canyon, night city, forest, coast, zigzag), 6 seamless backgrounds (sky, sunset, stars, mountains, city, dunes), 5 road surfaces (asphalt, gravel, dirt, snow, cobbles).
    Road atlas = 8 tiles of 32x32: tile 0 plain road, tile 5 road + dashed centre line (RoadSegmentMesh.laneRows[3]); road templates only rewrite tiles 0 and 5. Backgrounds repeat left-right.
  - Painter: LINE / RECT / OVAL (SOLID toggle, brush, mirror), FLIP H/V, ROLL (seam check), MAKE SEAMLESS (backgrounds), RGB sliders + HEX colour, IMPORT PNG from addons/import (created on demand; sprites keep aspect, background stretched, road: 256x32 atlas or one square tile -> tiles 0 + 5), scrollable tool panel. Keys: P E F I L R O M X, +/-.
  - Checked: PC + Android (javac -source 8 vs android.jar, dx) compile; headless tests: all 7 templates validate, zip round trip, no too-steep road; seamless checks; editor / painter screens rendered headless; key-down-only shortcuts tested.
  - NOT checked: real GPU / touch, Android device, save dialogs, fly view on a device, MP map transfer.

alpha43: AUDIT FIXES (compiled PC + Android; headless tests only, not run on a GPU / device -> alpha)
  - PC RecordStore (pc/javax/microedition/rms) = same code as Android: SafeFile.write (tmp -> fsync -> .bak -> rename), bounded reads (count <= 100000, record <= 4 MB),
    a broken file is renamed to .corrupt and the .bak is used. Before: a truncated rally3d101.rms silently reset medals / car unlocks / best times; a corrupt length field = OutOfMemoryError at every start.
  - PC settings.properties written through SafeFile.write. GhostReplay: MAX_TIME_MS 400000 -> 600000 and no poses after it (a run > 400 s produced a ghost that could not be decoded).
  - SaveData.sanitize: best-time rows and saved games with NaN / Infinity / negative index are repaired / discarded.
  - AddonSync: one running send thread per (peer, slot) - a REQUEST flood no longer spawns unlimited threads.
  - No more empty catch blocks in game / pc code (TheMidlet, MidiPlayer, GameWindow); instrument.py run (6 MapEditor methods were missing in FnTable).
  - TheBluetooth: every peer socket is written by its own writer thread through a bounded queue (QueuedOut, 512 packets). A peer that stops reading is dropped after 3 s instead of freezing the host's game thread forever.
  - AddonSync: the receive buffer of an offered pack is allocated when its first chunk arrives (not at OFFER time).
  - Trace ids: duplicate ids of different functions removed (instrument.py re-run); the ids still shared by methods with an identical Class.name(signature) key (anonymous run() etc.) are intended.
  - Checked: PC + Android compile (0 errors), APK builds / dexes / signs / verifies (v2+v3) with the bundled tools. Headless tests only; not run on a GPU / device -> alpha.

alpha44: PERFORMANCE (CPU/software path measured headless on 1 core, 1920x1080, static scene; GPU path NOT touched / not measured)
  - PC lcdui Graphics.drawImage: big images (sky, >= 16384 px) are scaled once to device pixels and blitted with an identity transform instead of Java2D's per-frame scaled blit
    (DrawImage.renderImageXform was ~11% of the frame). Non-raster part of the frame: ~6.5-8 ms -> ~4.9 ms.
  - Graphics3D (PC + Android) texture*vertex-colour * depth-test loop split into depth-write / no-write variants (no per-pixel branch): ~3-5% less raster time. Pixel results identical.
  - Total frame CPU time in the benchmark: 18.6-19.6 ms -> 16.2-18.0 ms. Profile: Graphics3D.rasterRows ~78% of CPU, overdraw ~0.9 writes/pixel, depth rejects 0.3% -> no gain from front-to-back sorting.
  - Tried, no gain (not shipped): JVM flags (-TieredCompilation, LoopUnrollLimit, ParallelGC).

alpha45: SPRITES / CAR PREVIEW / GPU FALLBACK (from a user log + screenshots; GPU path not testable here)
  - Sprites seen through hills / walls: on the CPU (software) path road + terrain meshes (CompositingMode depthWrite=false, a GPU-driver workaround)
    never wrote depth, so only cars occluded trees. PC Graphics3D now writes depth for every depth-tested mesh on the CPU path (State.softDepthWrite;
    Android already did). Measured: depth map covered 1.2% of the frame before, 58.7% after. The GPU path is unchanged (own depth pre-pass).
  - Finish (billboard type 3) and checkpoint (type 5) sprites now behave like trees: real world size, pure perspective, the 6 pre-scaled LOD frames,
    near cull 1 instead of 6 (no pop-in), same size cap. Only the person sprite (6) stays a single fixed frame.
  - Select car: the car turns on its own (45 deg/s) again; it no longer depends on the "Menu animations" / vanilla settings.
  - GameWindow.runGpu: a failed OpenGL context lock (JAWT_DrawingSurface_Lock) no longer disables the GPU renderer for the whole session:
    that frame is drawn in software and the GPU is retried; it gives up only after 30 failures in a row (LinkageError still gives up at once).

alpha46: MENU CHECKER FILL / FPS 144 -> 72 FIX (from logs rally3d-0 / rally3d-1)
  - FPS cause: log 0 (alpha44) lost the OpenGL context (JAWT lock) and ran the 3D raster in SOFTWARE: 144 FPS, raster 1.2 ms/frame, CPU 3 ms.
    Log 1 (alpha45) kept the GPU raster: 72 FPS, "GPU wait/readback" 11.8 ms + CPU 13.9 ms/frame: per frame full 1920x1080 colour+depth glReadPixels,
    single-threaded row-flip + depth short->float loop (2M px) + full-frame re-upload. Same map (Night race), same car. The parallel software raster is ~2x faster here.
  - Platform.gpuRaster (property gpuRaster, DEFAULT false = software raster); menu Settings -> Display / FPS (PC) -> "3D renderer: Software (fast) / GPU".
    Platform.runGpu returns false when off -> Graphics3D.flush uses flushSoftware (final OpenGL presentation/FXAA pass is unchanged). Android unaffected.
  - Menus: black/mirrored bars replaced by a seamless checker. BaseCanvas.fillChecker/checkerBackground (full-screen, pre-rendered once per size, exact 16 px integer grid,
    centred, smooth vignette, no tile seams) behind the title picture and the splash; BaseCanvas.wideBar widens the header/footer bars (title_up/title_down) to the screen
    width with the same row colours + exact 16 px cells (old code mirrored the 240 px bar -> dark seam every 240 px). Screens <= 240 px wide keep the original bar image.

alpha47: ALL-CORE RENDERING / MEMORY (PC)
  - Graphics3D.PARALLEL_BANDS: was min(12, cores-1) one band per worker; now 2 bands per core (common pool workers + caller), max 48. Bands cost unevenly (sky/road/sprites),
    extra bands let idle cores steal work. Min band stays 16 rows.
  - Graphics3D.clear(): colour + depth clear in ONE parallel pass (was 2 single-thread loops, 2 x 2M elements at 1080p).
  - GPU raster path (optional, Settings -> 3D renderer): row flip of readback, depth short->float conversion and upload flip now run on all cores (parallelRows, per-chunk buffer views).
  - run-gpu.bat: heap -Xms384m + MaxRAMPercentage=15 (was fixed 1g pretouched / 2g max; live heap is ~80-90 MB), ZGC generational kept, -XX:+UseStringDeduplication.
  - Not parallelised on purpose: game logic (race simulation 0.02 ms/frame), mesh preparation (~0.1 ms), frame handoff (memcpy ~1 ms). Sound/network already have own threads.

alpha48: CAMERA AT ROAD LEVEL ("stripe" frame until the mouse moves)
  - Cause (screenshot: only sky/ground fill + 1 px dark line at the horizon): orbit camera height = carGround + 3.5 + sin(pitch)*distance. Mouse-look pitch is clamped to -20 deg,
    so at distance ~10.2 the camera sits exactly in the road plane (distance > 10.2: under it) -> road/terrain project edge-on into a 1 px line. Pitch stayed there until the mouse moved.
  - RallyGame.updateCamera: pitch floor is now max(-20, -asin((cameraBase - carGround - 1.2) / distance)), camera stays >= 1.2 above the car's ground; NaN pitch reset to 0.
  - Known limit: terrain rising behind the car can still cover the camera (no terrain-height check against the camera position).

alpha49: GPU STAGE 1 - NO READBACK (not run on a GPU here: sandbox has no GPU/display -> alpha; compiled PC + Android only)
  - GPU raster ON (Settings -> Display / FPS (PC) -> "3D renderer") = layered frame, default stays OFF (software) until a user log confirms it.
    Frame: (1) world FBO: GPU sky (Graphics3D.drawGpuSky: flat colours + tiled sky texture) + 3D + billboards, cleared to alpha 0;
    (2) UI = the lcdui Graphics buffer, now ARGB in GPU mode, cleared to transparent at the start of every world frame (Graphics3D.beginWorld -> Graphics.clearTransparent),
    the CPU draws only HUD/text on it; (3) GpuSurface.presentLayers on the GAME thread: upload only changed 16-row bands of the UI layer, compose world+UI into a texture, FXAA/upscale, swapBuffers.
  - Removed: uploadPixels(pix), consumeGpuReadback/queueGpuReadback (glReadPixels colour+depth, PBO ring, fences), GameWindow copy + AWT-thread render hop in GPU mode.
  - Menus/other screens: the UI layer is composed over black (no world). drawCarPreview (menu 3D car) still rasterizes on the CPU into the UI layer (software raster now writes alpha 255).
  - Fallback: GL context unavailable for a frame -> Graphics3D paints the sky on the CPU (RallyGame.paintSkyCpu via the Runnable passed to beginWorld) + software raster; after GPU_MAX_RUN_FAILURES -> software for the session;
    Platform.present() re-creates the Graphics buffer (ARGB <-> RGB) when the mode changes (setGpuRaster / failGpu).
  - Perf log stages renamed: "GPU world pass" (flushGpuWorld CPU time), "GPU compose+present" (presentLayers). Still no GPU timers (stage 2).
  - Platform.FrameListener / screenshots of the CPU buffer lack the 3D world in GPU mode (they see only the UI layer).
  - Android: Graphics3D.beginWorld stub returns false (CPU sky as before). Binary prebuilt manifest was stale (alpha45/code 40): patched to alpha49/code 44.
  - Unverified: pixel parity of the sky (GPU vs CPU tiling), FXAA over the composed image, behaviour on resize/fullscreen toggle, translucent sky PNGs.

alpha50: GPU STAGE 2a - GPU TIMERS + PARITY HARNESS (user log rally3d-0 confirmed alpha49 stage 1 works on a desktop GPU; alpha50 not run here -> alpha; compiled PC + Android only)
  - alpha49 log (Blizzard passage, 1920x1080 internal -> native, cap 144): 143 / 140 FPS, GPU world pass 2.4-2.9 ms CPU, compose+present 0.2-0.55 ms, frame CPU rendering ~6.7 ms (~= 6.94 ms cap budget), frame snapshot 3.3 ms, menu ~60 FPS.
  - New rally3d.desktop.GpuTimer (GL_TIME_ELAPSED ring of 4 queries, results read without stalling): [perf] stages "GPU time world" (Graphics3D.flushGpuWorld) and "GPU time compose" (GpuSurface.presentLayers, before swapBuffers). Averaged per finished query, not per frame.
    Compare them with "GPU world pass"/"GPU compose+present" (those are CPU time spent issuing GL) and with "frame CPU rendering" to see who is the bottleneck. Test with Frame limit = unlimited: at cap 144 CPU and GPU both idle part of the frame.
  - Parity harness: run-gpu-parity.bat (= run-gpu.bat + -Drally3d.parity=true). Turn 3D renderer = GPU ON, race. Every 300 world frames (max 3 runs) Graphics3D.parityCheck reads the world FBO (3D, no billboards) back,
    rasterizes the SAME triangle queue in software into temp buffers and compares only pixels the software drew: log line [gpu-parity] = px compared, % diff>2, % diff>16, mean, max (+ x,y, sw/gpu ARGB), tris, software ms.
    First run also writes rally3d-parity-gpu.png / -sw.png / -diff.png (|sw-gpu| x8) next to the log. Debug only: stalls a frame, never enabled without the flag.
  - GPU default is still OFF (software). Not changed: stage 2 fixes themselves (nothing is known to differ until the user sends [gpu-parity] lines + pngs).

alpha51: GPU STAGE 2b - BLACK INTRO SCREEN, UNLIMITED FPS, GL CONTEXT OVERHEAD (from user log + screenshot; not run here -> alpha; compiled PC + Android only)
  - alpha50 log: [gpu-parity] 3 runs on Blizzard passage: diff>2 = 0.17-0.23% of 3D pixels, diff>16 = 0.11-0.16%, mean 0.38-0.41, max 202-241 near screen centre (x~1060-1130, y~750-860 = car area). Parity is good; remaining diffs = silhouette/coplanar-depth pixels, pngs not received yet.
    Same log: GPU time world 0.04 ms/frame (the GPU is idle), race 137 FPS at cap 360 = CPU bound: frame CPU rendering ~7 ms, GPU world pass 2.9 ms + frame snapshot 3.4 ms, while compose is 0.3 ms => ~5 ms per frame are NOT GL work.
  - Suspect + fix: GameWindow.runGpu ran GL.createCapabilities() (reloads all GL function pointers) on EVERY call = 2x per frame. GpuSurface.refreshCapabilities now does it once per context/thread (capsContext). New [perf] stages measure it:
    "GL ctx lock+switch" (JAWT lock + make current + release per frame, excludes task and caps) and "GL caps refresh" (per frame). If "GL ctx lock+switch" is still several ms, the fix is stage 4 (GLFW window, context owned by the game thread; no per-call make-current).
  - Black screen on the race intro panel ("Championship / track / Press ... to start", screenMode 4): drawInfoScreen paints its panel ONCE on top of the previous frame (software keeps the last world frame in the buffer). In GPU mode that frame lives in the world FBO texture, which was
    not composited on frames without a world pass. Graphics3D now keeps worldHeld = the last world frame stays under the UI layer until the next world frame; dropHeldWorld() on renderer switch / buffer rebuild, held flag also cleared on FBO resize and on the CPU-sky fallback frame.
  - Unlimited FPS: Platform.setFpsLimit(0) clamped 0 to 30, so the "0" entry of FPS_VALUES behaved like 30. Now 0 = unlimited (targetFrameNanos 0); menus show "Unlimited" instead of "0 FPS".
  - GpuTimer: in the alpha50 log "GPU time compose" appeared only in the first window -> ring stuck full. Now the ring is recreated after 120 consecutive full frames (RLog.w "queries never completed, recreated"). Cause of the stall is not confirmed - if the warning shows up in a log, report it.
  - Menus run at 60 FPS even with cap 360 (frame pacing ~9 ms in menus): not investigated.

alpha52: HOTFIX OF alpha51 REGRESSION (not run here -> alpha; compiled PC + Android only)
  - alpha51 log: GpuSurface.refreshCapabilities called GL.getCapabilities(), which THROWS IllegalStateException ("No GLCapabilities instance set for the current thread") in LWJGL 3.4 when the thread has none ->
    30 failed GL frames in a row -> GameWindow.failGpu -> permanent Java2D software fallback for the session (slow, 35-60 FPS, dynamic resolution dropped to 1493x840). NEVER call GL.getCapabilities() to test for presence.
  - Now a ThreadLocal<long[]> (capsFor) remembers the GL context this thread called GL.createCapabilities() for; recreated only when the thread or the context changes.
  - Measured in that log before the fall-back (35 FPS window, GPU just woken up): GL caps refresh 0.53 ms/frame (2 calls), GL ctx lock+switch 0.42 ms/frame => context overhead is ~1 ms per frame, NOT the ~5 ms suspected. alpha50 numbers (frame snapshot 3.4 ms vs compose 0.3 ms) are still not explained: look at what presentLayered/Platform.present do outside runGpu.

alpha53: 144 FPS CEILING DIAGNOSTICS (not run here -> alpha; compiled PC + Android only)
  - alpha52 log (Night race, Frame limit Unlimited, GPU renderer, parity on): actual = 144.0-144.1 FPS exactly, frame pacing 0.00 ms, GPU time world 0.04 ms, GPU time compose 0.10 ms, GPU world pass 0.27 ms,
    frame CPU rendering 6.92 ms of which frame snapshot (present) 6.17 ms and GL ctx lock+switch 6.14-6.19 ms => the game thread spends ~6.2 of 6.94 ms BLOCKED inside the GL context lock/unlock around the swap. Game logic + 3D submit is only ~0.8 ms/frame.
    144 = the monitor refresh rate (144 Hz display): presentation backpressure (vsync/DWM/driver frame cap), not CPU or GPU load. GpuSurface.presentLayers: swapBuffers itself returns in ~0.2 ms (stage "GPU compose+present"), so the stall is in make-current / unlock (implicit glFlush) with the driver queue full.
  - alpha53: GpuSurface.enforceSwapIntervalZero() queries WGLEXTSwapControl.wglGetSwapIntervalEXT() before every swapBuffers, logs [gpu] "WGL swap interval is N" on change and forces wglSwapIntervalEXT(0) when N != 0.
    [perf] stage split: "GL lock+make-current" (until the runnable starts), "GL caps refresh", "GL unlock+release" (after the task; includes glFlush). If the stall stays in unlock+release with interval 0, it is the driver/DWM, check NVIDIA Control Panel (Vertical sync, Max Frame Rate, G-SYNC) for java.exe in tools\\jdk\\bin and try Fullscreen window (borderless) = Off.
    Real fix if still capped: stage 4 (GLFW window, context owned by the game thread, no JAWT lock; borderless fullscreen can get independent flip + tearing).
  - Parity (alpha52 log, Night race, 3 runs): diff>2 0.56-0.70%, diff>16 0.49-0.59%, mean 0.51-0.54 -> nearly all differing pixels differ hugely (edge coverage/depth-fight pixels), colour/texture math matches. diff png: scattered pixels on terrain silhouettes, wall top edge (vertical dashed seams) and horizon; interior is clean. Not worth fixing now.

alpha54: 144 FPS CEILING SOLVED (driver V-sync) + STAGE 3a GPU UI (opt-in; not run here -> alpha; compiled PC + Android only)
  - The 144 cap was NOT the engine: NVIDIA Control Panel "Vertical sync" was forced On for java.exe. alpha53 log with it Off: "WGL swap interval is 0", 825-900 FPS in a race (Night race, Unlimited), frame CPU rendering 1.1-1.2 ms, GPU time world 0.04 ms.
    Stage 4 (GLFW window) is therefore NOT needed for FPS. Platform.checkRefreshLock logs one WARN when FPS sits on the display refresh for 4 windows with Frame limit Unlimited (tells the user to switch V-sync off in the driver).
  - Stage 3a: javax.microedition.lcdui.Graphics can RECORD its draw calls (UiRecorder, device-pixel ints) instead of using Java2D; rally3d.desktop.GpuUi replays them on the GL thread into a premultiplied RGBA UI framebuffer (GpuSurface.uiGpuFbo), which the compose shader
    uses instead of the uploaded CPU layer (uniform uiPremult). Ops: clear, clip (glScissor), fillRect, fillRectAlpha, drawLine, drawImage/drawRegion (image texture cache), drawString (Java2D renders the string once into a white texture, cached LRU, tinted on the GPU), drawRGB (temp texture).
    Settings -> Display / FPS (PC) -> "UI renderer: CPU / GPU (experimental)" (only shown when 3D renderer = GPU; setting key gpuUi, default OFF). A GPU UI exception only disables the GPU UI (RLog.e "GPU UI failed", CPU UI from the next frame), not the whole GPU renderer.
    New [perf] lines: "GPU UI replay" (CPU time issuing the UI GL calls) and "GPU time UI" (GL_TIME_ELAPSED).
  - Known limits of 3a: FrameListener/screenshots get nothing in GPU UI mode (pixel buffer is empty); text is positioned at whole device pixels (no sub-pixel offset); lines are 1 logical px with square caps; Android unchanged (CPU 2D).
  - Test: GPU renderer + UI renderer GPU, play menus + a race, send rally3d-0.log (look for "GPU UI renderer ready", "GPU UI failed", "GPU UI replay", "GPU time UI") and screenshots of main menu / settings / HUD / pre-race info screen / finish screen / F1 log overlay.

alpha55: GPU UI FIX - menu car preview invisible (not run here -> alpha; compiled PC + Android only)
  - alpha54 user log (UI renderer GPU, Night race, Unlimited): no GPU UI errors, race 1090-1213 FPS (was 825-900 with CPU UI), "GPU UI replay" 0.01-0.03 ms, "GPU time UI" 0.07-0.14 ms. Select-car screen: the 3D car was missing.
  - Cause: drawCarPreview (and any other software 3D: shadows, fly view) rasterises into Graphics.pixelBuffer(); in GPU UI mode that buffer is never uploaded ("software raster" 0.09-0.11 ms still showed in the menu perf line).
  - Fix: Graphics3D.blitToGpuUi() after flushSoftware() -> Graphics.recordPixelBlit(clip rect) records the touched rectangle as an ARGB RGB op (draw order kept) and zeroes it in the CPU buffer. Cost = one upload of the 3D viewport rectangle per frame.

ANDROID vs PC PARITY (alpha56): same resolution table (27 presets), Anti-aliasing (GPU: FXAA 3.11 fragment shader on both; CPU fallback = rally3d.core.Fxaa), renderBatch,
CPU threads (Platform.parallelFor / workers / nextCpuThreads, pool "rally-worker-N", key cpuThreads), 3D renderer GPU/Software, V-sync, GPU UI.
Android GPU renderer = OpenGL ES 3.0 over EGL on the GameView surface: rally3d/android/GpuRenderer (EGL, compose world+UI, FXAA, swap), GpuUi (recorded lcdui ops -> quad batcher),
Gl (helpers), Graphics3D GPU world (same triangle queue as PC: sky quad, depth pre-pass, batches, billboards), lcdui/UiRecorder + Graphics recording mode.
On Android the UI always follows the 3D renderer (no separate "UI renderer" item). Touch buttons are an overlay View above the SurfaceView while the GPU renderer owns the surface.
Safety: any GL failure x3 -> CPU renderer + resolution <= 720p (RLog "GPU renderer failed"); crash guard file gpu.trying in the data dir: if the previous run died during the first
180 GPU frames, the next start switches the GPU renderer off (Settings -> Display / FPS -> 3D renderer re-enables). GPU renderer setting key: gpuRaster, V-sync key: vsync.
NOT verified on a device when this was written (no GPU in the build sandbox): compile + GLSL ES validation (glslangValidator) only. Check the log for [gpu] lines on first run.

README.md = public GitHub docs (build, controls, settings, add-ons, modding). Keep its version line and settings tables in sync when you change them.
