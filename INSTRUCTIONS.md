# Rally3D 2.0 - Modding Instructions

How to modify the game, and how to create **maps**, **cars**, **wheels**, **skins** and **sprites** for it.
Everything here matches version **2.0-alpha65**. For controls, settings and LAN play see [README.md](README.md).

Contents

1. [Two ways to mod](#1-two-ways-to-mod)
2. [Where the files are](#2-where-the-files-are)
3. [Add-on basics (zip format)](#3-add-on-basics-zip-format)
4. [Creating cars](#4-creating-cars)
5. [Creating wheels](#5-creating-wheels)
6. [Creating skins (texture only)](#6-creating-skins-texture-only)
7. [Creating maps](#7-creating-maps)
8. [Maps in Championship, Time Trial, Ghost Race, Multiplayer](#8-maps-in-championship-time-trial-ghost-race-multiplayer)
9. [Ghost Race rules](#9-ghost-race-rules)
10. [Modifying the game source code](#10-modifying-the-game-source-code)
11. [Replacing stock resources (tracks, themes, cars, sounds)](#11-replacing-stock-resources-tracks-themes-cars-sounds)
12. [Building, testing, versioning](#12-building-testing-versioning)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Two ways to mod

| Way | Needs | Good for |
|---|---|---|
| **Add-ons** (zip files) | Nothing, works in the released game, on PC and Android | New cars, wheels, skins, maps. Other players get your add-ons automatically in LAN multiplayer. |
| **Source code** | JDK 21 (PC) - see [section 10](#10-modifying-the-game-source-code) | New game rules, physics, menus, stock tracks/cars, engine changes. |

Prefer add-ons whenever possible: they never break multiplayer compatibility and need no rebuild.

## 2. Where the files are

| What | Windows | Linux / macOS | Android |
|---|---|---|---|
| Add-ons | `%APPDATA%\Rally3D\addons\{maps,cars,wheels}` | `~/.rally3d/addons/{maps,cars,wheels}` | `Android/data/com.rally3d.android/files/addons` |
| Enabled list | `addons\enabled.txt` | same | same |
| Settings | `C:\Users\<you>\.rally3d\settings.properties` | `~/.rally3d/settings.properties` | app data folder |
| Ghosts | `~/.rally3d/ghosts/` | same | app data `ghosts/` |
| Map records / medals | `addons\..\maprecords.txt`, `mapmedals.txt` | same | same |
| Images to import into the painters | `addons\import\` | same | same |
| Logs | `rally3d-N.log` next to the jar | same | `files/rally3d.log` |

The easiest way to install an add-on: main menu -> **Add-ons** -> **+ ZIP**. A zip dropped into the wrong sub-folder is sorted by its `addon.properties` automatically. Switch add-ons on/off with the green square; no restart is needed.

## 3. Add-on basics (zip format)

An add-on is a normal `.zip`. Files may be at the top level or in one top-level folder. File names are case-insensitive. Only these file names are read (everything else is ignored):

```
addon.properties            required for maps / wheels, recommended for cars
car.syn  car.png            car model + texture
wheel.syn  wheel.png        wheel model + texture
car.rcm                     Car Builder project (ignored by the game, read by the builder)
track.bin                   map road data            (maps only)
background.png squares.png  map background / road texture (maps only)
sprite1.png/.off ... sprite4.png/.off   map sprites (maps only)
```

`addon.properties` is a Java `.properties` file (UTF-8, `key=value`, one per line). **Never put a `#` comment after a value on the same line** - it becomes part of the value.

Common keys:

| Key | Meaning |
|---|---|
| `type` | `car` (default), `wheels` or `map` |
| `name` | Name shown in menus (max 24 chars). Together with `author` it is the identity of the add-on (see [section 9](#9-ghost-race-rules)). |
| `author` | Author (max 24 chars) |
| `description` | One line shown in the Add-ons menu (max 90 chars) |

Limits: one file inside a zip up to 32 MB, zip up to 64 MB, 64 files per zip, PNG width and height must each be a **power of two** (16, 32, 64, 128, 256, 512, 1024; max 1024), 32-bit PNG with alpha is fine.

Add-ons are untrusted input (they can arrive from another player) and are validated strictly. A rejected add-on appears in the list with a red error text; read it, fix the zip, import again (a re-imported zip with the same name replaces the old one).

## 4. Creating cars

### 4.1 Car Builder (no external tools)

Main menu -> **Car Builder**. Build the car from boxes, wedges and prisms in a live 3D view, paint each part, press **SAVE TO GAME** (installs and enables it) or **EXPORT ZIP** (to share). Details in [README.md - Car Builder](README.md#car-builder). The result is an ordinary car add-on.

### 4.2 By hand: stats only (re-stat a stock car)

`addon.properties`:

```properties
type=car
name=Red Rocket
author=you
description=Fast and twitchy
base=3
speed=110
accel=1.6
steer=1.2
steerMax=1.1
grip=1.3
```

| Key | Range | Meaning |
|---|---|---|
| `base` | 1..4 | Stock car whose **model and UV layout** are used when you give no `car.syn`: 1 Red danger, 2 Azure racer, 3 Fast racer, 4 Good oldie |
| `speed` | 20..200 | Top speed in game units. **km/h on the speedometer = speed x 1.5** (stock: 60, 68, 75, 80 = 90, 102, 112, 120 km/h) |
| `accel` | 0.2..4 | Acceleration multiplier (1 = stock physics) |
| `steer` | 0.3..3 | Steering speed multiplier |
| `steerMax` | 0.3..3 | Maximum steering angle multiplier |
| `grip` | 0.3..3 | Tyre grip multiplier |

Compare with the examples in `examples/`: Red Rocket 110 / 1.6 / 1.2 / 1.1 / 1.3, Slow Tank 45 / 0.5 / 0.7 / 0.8 / 2.0. Zip it together with an optional texture and install.

### 4.3 By hand: your own 3D model

1. Model the car in any 3D program (Blender etc.). **+Z = front of the car, +Y = up, X = left/right.** The stock cars are about 2.7 wide, 5.4 long and 1.5 tall (one unit is roughly one metre). Keep it under 20000 triangles.
2. UV-unwrap it and paint a texture. PNG size must be a power of two (128x128 is typical).
3. Export as **OBJ** (with UVs). Convert:

   ```
   python tools/obj2syn.py model.obj car.syn --scale 1.0
   ```

   Options: `--scale S` (multiply all coordinates), `--flip-z` (the car drives backwards -> mirror along Z), `--no-snap` (do not move the model so its lowest point sits at Y = 0.5). The converter checks the triangle winding (flips an inside-out model) and refuses coordinates beyond +-32 units.
4. Put `car.syn`, `car.png` and `addon.properties` into a zip, install it (Add-ons -> + ZIP), pick the car in *Select car*.

The wheels of the car are separate objects (the game draws four wheels itself and spins them), so **do not model wheels into the body** - see the next section. The body should have wheel arches.

The game has **no lighting**: shading must be painted into the texture (lighter on top, darker at the bottom).

## 5. Creating wheels

Wheels are an add-on of their own: `type=wheels`. One wheels pack is active at a time and fits **all** your cars. In multiplayer other players see your wheels.

```
addon.properties      type=wheels  name=Gold Wheels  author=you
wheel.png             texture (power of two, e.g. 32x32)  - optional if wheel.syn is given
wheel.syn             model of ONE wheel                  - optional if wheel.png is given
```

How the game uses it:

- The model is **one wheel**; the game draws it four times, at the four wheel positions of the car, and spins it around the **X axis** (the axle runs along X, so the tyre tread faces +-Y/Z).
- Model it **around its own centre at about Y = 0.54** (that is the spin centre, `RallyGame.WHEEL_CENTER_Y`); the stock wheel radius is **0.55**. Convert with `python tools/obj2syn.py wheel.obj wheel.syn --no-snap` (`--no-snap` keeps your centre).
- Texture only: give just `wheel.png` and the stock wheel model gets your texture.

Order in which wheels are chosen for a car: the car's own wheels (`wheel.png` / `wheel.syn` inside a car add-on) -> the driver's wheels pack -> stock wheels.

Wheel presets are also available in the Car Builder (WHEELS STANDARD / MONSTER / SLIM).

## 6. Creating skins (texture only)

A skin is a car add-on with only `car.png` (and `addon.properties` with `base=N`). The texture must follow the **UV layout of the stock car number N**; get the layout by exporting the stock skin from the game resources: `source/res/cars/N.png` (128x128). Paint over a copy of it. Example: `examples/example-test-skin-blue.zip`.

## 7. Creating maps

### 7.1 Map Editor (recommended)

Main menu -> **Map Editor**. Everything below is in the in-game help (**?** button, 8 pages) as well.

Workflow:

1. File -> **New from TEMPLATE** (7 tracks, 6 backgrounds, 5 road surfaces) or **New blank** or **New from stock** (copy a stock track).
2. **PATH** mode: tap the ground to grow the road; drag the orange ball at the road end to stretch it in any direction; yellow handles on the bottom strip stretch/shrink the selected part. Road length is 30..900 segments.
3. **HEIGHT** mode: draw the hills; **LAND**: terrain on both sides (hills, rock wall, high wall, drop-off, guard wall); **PROPS**: roadside signs/sprites; **MARKS**: checkpoints 1-3 and the finish line, centre-line markings; **WORLD**: theme (desert / winter / summer / night), colours, background, road texture, custom sprites.
4. Test: **TEST** drives the map with a car, **FLY** is a free camera (W A S D).
5. **SAVE** writes the add-on zip into `addons/maps` and enables it. **Export zip** saves a copy anywhere (to share).
6. Edit again later with File -> **Open**.

**Painter** (background, road surface, sprites): LINE / RECT / OVAL, SOLID, brush, mirror, FLIP, ROLL (seam check), MAKE SEAMLESS, RGB sliders + HEX. **IMPORT PNG** loads a file from the `addons/import` folder:

| What | Size / rules |
|---|---|
| Background | stretched to the background size, **tiles horizontally**, so the left and right edges must match (use MAKE SEAMLESS) |
| Road surface | an atlas of **8 tiles of 32x32 = 256x32**: tile 0 plain road, tile 5 road with dashed centre line. A single square tile is accepted too. |
| Sprites | up to 4 custom sprites, keep their aspect ratio |

Shortcuts (key-down only): Ctrl+Z / Ctrl+Y undo / redo, Ctrl+C / X / V / D copy / cut / paste / duplicate, Ctrl+A select all, Ctrl+S save, Ctrl+E export, Ctrl+T test, Ctrl+F fly, Ctrl+N new, Ctrl+O open, Del, arrows (+Shift extends), PgUp / PgDn, Home / End, 1-6 mode, Tab next mode, F fit, + / - zoom, H help, Esc file menu.

### 7.2 Map zip format (for tools and hand editing)

```
addon.properties
track.bin
background.png    optional painted background
squares.png       optional road atlas (256x32)
sprite1.png sprite1.off ... sprite4.png sprite4.off   optional custom sprites
```

`addon.properties` written by the editor (you can edit it by hand):

```properties
type=map
name=My Map
author=you
description=300 segments
format=1
theme=2                    0 desert, 1 winter, 2 summer, 3 night
bgFrom=1                   optional: borrow the background of theme 0..3
roadFrom=0                 optional: borrow the road texture of theme 0..3
sky=9ED0FF                 optional custom colours (hex RRGGBB): sky, ground, HUD text
ground=4F8F3A
hud=FFFFAA
checkpoints=60,120,180,290 checkpoint 1, 2, 3 and finish, ascending segment numbers (>= 6, < number of segments); invalid = automatic
sprite1=1                  written by the editor when a custom sprite exists
par=60,66,75,86            Championship reference times in SECONDS, 4 ascending values (optional, see section 8)
```

`track.bin` is the stock track format (little-endian): `uint16 theme`, `uint16 recordCount`, then per record 10 `int16` values:

| # | Field | Meaning |
|---|---|---|
| 1 | count | how many identical segments this record repeats (unsigned) |
| 2 | yaw | turn per step in 1/2048 half-turns, positive = RIGHT turn (about 0.703 degrees per unit per segment) |
| 3 | curv | change of slope per step, 1/16384 x 1.3 per unit |
| 4 | profile start | terrain profile at the start of the run (0 hills, 3 rock wall, 6 high wall, 7 drop-off, 11 guard wall) |
| 5 | profile end | terrain profile at the end of the run (transitions must be legal, see `MapData.PB`) |
| 6 | centre marking | 0 none, 1 plain / dashed centre line (tile 5) |
| 7-10 | signs | left sign type, left sign lane, right sign type, right sign lane (-1 = random, -2 = none) |

One segment = 8 simulation steps. The stock tracks in `source/res/tracks/trackNN.bin` use the same format, so they are good references. Easiest: build the map in the editor and only post-edit the zip. The file must be `4 + 20 x records` bytes and at least 24 bytes, otherwise the map is rejected as damaged.

The map's **identity** is `name` + `author` (case-insensitive): it gives the map its id (>= 1000000). Records, medals and ghosts belong to this id, so renaming a map or changing its author starts fresh records.

## 8. Maps in Championship, Time Trial, Ghost Race, Multiplayer

An enabled map appears in every mode:

| Mode | Where | Notes |
|---|---|---|
| Time Trial | first in the track list | own top-5 table in `maprecords.txt` |
| Ghost Race | first in the track list | ghost per (map, car), top-5 table, medals |
| Multiplayer | first in the track list (host) | the host's map is sent to all players automatically (MD5-checked) |
| **Championship** | **after the 12 stock tracks** | never locked, unlocks nothing, own medals |

**Championship rules for a custom map**

- You race the map like a stock Championship race and get a medal by finishing before the **3rd / 2nd / 1st reference time** (bronze / silver / gold). The best medal is kept in `mapmedals.txt`; the track menu shows it as a cup next to the map name. *Championship -> Reset all* clears custom-map medals too.
- Reference times: set them in `addon.properties` as `par=60,66,75,86` (seconds, four values, ascending, each 5..3600 s). A bad value is ignored (a warning appears in the log). Without `par` they are estimated from the road length: `segments x 3.9 / 4.3 / 4.9 / 5.7` tenths of a second. Tune `par` by driving the map once with the slowest and the fastest car.
- Custom maps never unlock stock tracks or stock cars.
- **Ghost switch:** the first entry of the Championship track menu is *Ghost: ON / OFF*. With ON you pick a ghost after choosing the car, the run is recorded and saved as in Ghost Race. Continued (resumed) races have no ghost.

## 9. Ghost Race rules

- A ghost is recorded for **every (track, car) pair** you finish in Ghost Race, on stock tracks and on custom maps, with stock cars and add-on cars. Only a better time replaces the saved ghost of that pair.
- Files: `ghosts/track-<track>-car-<carKey>.rpl`.
  - `<track>` = `0..11` (stock) or the map id (>= 1000000, from `name|author`).
  - `<carKey>` = `0..3` (stock cars) or `1000 + CRC32(lowercase(name) + "|" + lowercase(author)) & 0x3FFFFFFF` (add-on cars).
- Because the key comes from name + author, **keep them stable** when you update a car or map and its old ghosts keep working. Changing the name or the author creates a new identity.
- *Select ghost* lists all your ghosts for the track: installed cars and cars that are no longer installed (shown as "(not installed)" and drawn with your current car's model). You can race the ghost of any car with any car.
- Ghost Race is available in Vanilla mode too.
- File format v3: `int version(3), int finishTime(0.1 s), int finishMs, int carKey, UTF carName, int frameCount, frames { int ms, float x, y, z, rotX, rotY, rotZ }`. v1 and v2 files are still read.

## 10. Modifying the game source code

### 10.1 Setup

1. Install **JDK 21** (e.g. Temurin) or use `tools\jdk` if you have it. All other tools are in `tools/`.
2. Windows: `build-all.bat` builds `Rally3D-Java21-GPU.jar` + `Rally3D-Android.apk`; `compilepc.ps1` only the PC jar; `compileandroid.ps1` only the APK (offline, no Android SDK).
3. Linux / macOS: `source/lib` contains Windows natives only. Add `lwjgl*-natives-linux` (or `-macos`) jars of version 3.4.2, then:

   ```bash
   CP=$(ls source/lib/*.jar | grep -v natives | tr '\n' ':')
   mkdir -p build/pc && javac --release 21 -nowarn -cp "$CP" -d build/pc $(find source/game source/pc -name '*.java')
   cp -r source/res/* build/pc/
   ```

   Compile check for Android (no packaging): `javac -source 8 -target 8 -bootclasspath tools/android.jar -d build/and $(find source/game source/android -name '*.java')`.

### 10.2 What is where

| Want to change | File |
|---|---|
| Game logic, menus, race rules, car list, Championship, ghosts | `source/game/com/nokia/mid/appl/rally3d/RallyGame.java` |
| Stock car stats / car definitions | `CarDef.java`, `RallyGame.statSpeed / statAccel / statGrip`, `RallyGame.initCars` |
| Max speed cap | `RallyGame.updateRace` (cap = `car.speed / 10`) |
| Ghost recording / file format | `GhostReplay.java`, `RallyGame.chooseGhost / finishGhostRecord / ghostKeyOf`, `Platform.loadGhost / saveGhost / ghostKeys` (PC and Android copies) |
| Custom maps: ids, records, medals, par times | `MapLibrary.java` |
| Map loading and track format | `RallyGame.loadTrack / loadTrackStream / applyCustomLook / applyCustomCheckpoints` |
| Add-on zip parsing and limits | `AddonPack.java`, `AddonManager.java`, `AddonUi.java`, `AddonSync.java` |
| Map Editor / Painter / templates | `MapEditor.java`, `Painter.java`, `MapTemplates.java`, `MapData.java` |
| Car Builder | `CarEditor.java`, `CarModel.java` |
| Sprite size (trees, bushes) | `RoadSegmentMesh.SPRITE_SCALE`, `SPRITE_FULL_SIZE_DISTANCE` |
| Settings menus | `RallyGame.settingsMenu / desktopSettingsMenu / fpsSettingsMenu` |
| PC window, input, settings file | `source/pc/rally3d/desktop/` |
| Android activity, touch buttons | `source/android/rally3d/android/` |
| 3D engine | `javax/microedition/m3g/Graphics3D.java` (PC and Android copies), `GpuSurface`, `GpuRenderer` |
| LAN protocol | `TheBluetooth.java`, `AddonSync.java` (**changing it requires a version bump**) |
| Saves | `SaveData.java`, `javax/microedition/rms/RecordStore.java`, `rally3d/core/SafeFile.java` |
| Texts / languages | `Texts.java`, `Localization.java`, `source/res/lang.*` |

`source/pc` and `source/android` contain **two copies** of the platform classes (`javax/microedition/*`, `rally3d/desktop/Platform.java`). Whatever you change in one of them usually has to be done in the other.

### 10.3 Rules when you touch code

1. After adding / changing functions or `catch` blocks run `python tools/instrument.py` (idempotent; adds tracing and updates `rally3d/core/FnTable.java`). No empty `catch` blocks.
2. Log with `RLog.e / RLog.w / RLog.i(tag, text)`; log swallowed exceptions with `RLog.caught("Class.method", e)`. Never log every frame.
3. Write player files **only** through `SafeFile.write` (tmp -> fsync -> .bak -> rename).
4. Do not add storage permissions to the Android manifest.
5. Bump the version ([section 12](#12-building-testing-versioning)).

### 10.4 Recipes

**Add a stock car.** Add the model + texture to `source/res/cars/` (`5.syn`, `5.png`), extend the 4-element arrays (`statSpeed`, `statAccel`, `statGrip`, `SaveData.carLocks`, `Texts.carNames`) and the loops in `RallyGame.initCars`. Easier: ship it as an add-on.

**Change a stock car's speed.** Edit `statSpeed` in `RallyGame` (km/h = value x 1.5). It applies in all modes including multiplayer.

**Change the Championship reference times of custom maps.** `MapLibrary.parTimes` (`perSegment` array). For stock tracks: `SaveData.defaultTimes`.

**Replace a stock track.** Overwrite `source/res/tracks/trackNN.bin` (see `RallyGame.trackFiles` for the track-number-to-file order), keep the file format of [section 7.2](#72-map-zip-format-for-tools-and-hand-editing). `Texts.trackNames` has the names.

**Add a language.** `Texts.java` reads `source/res/lang.<locale>` (`lang.xx` is the default, `lang.zh-CN` and `lang.zh-TW` exist). The file is the original Nokia string table: a table of `uint16` offsets followed by `writeUTF` strings; indexes are the numbers in `Texts.text(N)`.

## 11. Replacing stock resources (tracks, themes, cars, sounds)

All in `source/res/` (packed into the jar / APK):

| Folder / file | Content |
|---|---|
| `tracks/trackNN.bin` | the 12 stock tracks |
| `summer/ winter/ desert/ night/ cup/` | themes: `background.png`, `squares.png` (road atlas), `billboardN.png/.off` (sprites + frame offsets). `cup/` is the track-selection theme. |
| `cars/N.syn`, `cars/N.png` | stock car models and 128x128 skins; `wheel.syn`, `wheel.png` stock wheel |
| `cup/1..5.png` | medal icons (gold, silver, bronze, none, locked) |
| `midi/` | sounds and music |
| `title/`, `icon.png`, `shadowN.png` | title screen, icon, car shadow |
| `lang.*` | texts |

The original assets come from a commercial J2ME game: check their rights before publishing or redistributing the game with them.

## 12. Building, testing, versioning

- **Run a PC build:** `run-gpu.bat` (or `java -jar Rally3D-Java21-GPU.jar` with `source/lib` next to the jar).
- **Look at the log** (F1 in game, or the log file) after every start: `[gpu]` lines for the renderer, `[addon]`, `[map]`, `[ghost]`, `[race]` lines for what you changed. Every caught exception is logged.
- **Multiplayer only works between identical versions.** Every change to the game (code, resources, manifest) must raise the version in all of these places:
  1. `source/game/rally3d/core/Version.java` - `NAME` and `CODE`
  2. `source/android-pkg/AndroidManifest.xml` - `versionName` = `NAME`, `versionCode` = `CODE` (always +1)
  3. `source/android-pkg/prebuilt/AndroidManifest.xml` - the binary manifest must match (patch the UTF-16 version string in the string pool and the `versionCode` int, or regenerate it with `aapt`)
  4. the "Current version" lines in `README.md` and `README.txt`

  Suffixes: `N.0` = checked and sure, `N.0-beta` = ran it, minor doubts, `N.0-alpha` = not fully run / risky edits.
- Add-ons do **not** change the game version.

## 13. Troubleshooting

| Problem | Fix |
|---|---|
| Add-on is listed in red | Read the error: PNG not a power of two, bad `addon.properties`, `type=map` without `track.bin`, bad `.syn`. |
| Car drives backwards / is inside-out | `obj2syn.py --flip-z`; the converter fixes winding automatically. |
| Car floats / sinks | The body's lowest point must be at Y = 0.5 (default snap); wheels use their own centre (`--no-snap`, centre about Y = 0.54). |
| Wheel wobbles | The wheel centre is not on the X axis through Y = 0.54, or the model is not round around X. |
| Texture is garbled | UV does not match `base` car (skin-only add-on), or the PNG is not a power of two. |
| Custom map not in Championship | The map must be **enabled** (Add-ons -> MAPS, green square). It is listed after the 12 stock tracks. |
| Medal never appears | You must finish before the 3rd reference time. Lower the `par` values or drive faster; see the log line `race END`. |
| Ghost not offered | A ghost exists only after you **finished** a Ghost Race on that track with that car (a better time replaces it). |
| Ghost lost after editing a car / map | `name` or `author` was changed (they define the identity). |
| Players cannot join | Different versions, firewall (TCP 47832 / UDP 47833), wrong IP. |
