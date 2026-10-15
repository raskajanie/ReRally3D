package com.nokia.mid.appl.rally3d;

/**
 * Extra pages of the main menu "Instructions" screen (after the six original pages): the features added by the port.
 * Plain English text; '|' = line break, '||' = blank line (the text screen wraps the lines itself).
 */
final class HelpPages {
    private HelpPages() {}

    static final String[] PAGES = {
        "GAME MODES||"
        + "CHAMPIONSHIP: the 12 tracks in order. A medal on a track unlocks the next one and some cars. Enabled custom maps are listed after the 12 tracks: always unlocked, with their own medals.|"
        + "TIME TRIAL: race the clock on any track or custom map. Best times are saved per track.|"
        + "GHOST RACE: race a recording of your best run. Works with every car (also add-on cars) on every track (also custom maps).|"
        + "MULTIPLAYER: LAN race for 2-32 players. The host picks the track (custom maps are sent to everybody) and add-on cars and wheels are transferred automatically.",

        "GHOST RACE||"
        + "Finish a Ghost Race and your run is saved as the ghost of that track + car. Only a faster run replaces it.|"
        + "After you pick your car, the Select ghost menu lists the ghosts of all cars on that track. You can race the ghost of any car with any car. A ghost of a car that is no longer installed is marked (not installed) and is drawn with your own car.|"
        + "No ghost = free run (your run is still recorded).|"
        + "Add-on cars and custom maps are identified by name + author: keep them unchanged when you update an add-on, or its old ghosts and records are not found.",

        "CHAMPIONSHIP ON CUSTOM MAPS||"
        + "Custom maps appear below the 12 stock tracks. You race four reference times: finish before the 3rd / 2nd / 1st one for bronze / silver / gold. The cup is shown next to the map name. Medals unlock nothing.|"
        + "Map authors can set the times in addon.properties: par=60,66,75,86 (4 ascending values, seconds). Without par they are estimated from the road length.|"
        + "The first item of the track menu, Ghost: ON / OFF, switches the ghost for Championship races: you pick a ghost after the car and your run is recorded. Continued races have no ghost.|Championship > Reset all also clears the medals of custom maps.",

        "ADD-ONS||"
        + "Main menu > Add-ons. Tabs: MAPS / CARS / WHEELS. Green square = on / off, tap a row = details, red X = delete, + ZIP = import a zip.|"
        + "An add-on is a zip with addon.properties and the files of its kind:|"
        + "CAR: car.syn (model), car.png (texture, power of two), optional wheel.png / wheel.syn. Keys: name, author, base (1-4 stock model), speed, accel, steer, steerMax, grip.|"
        + "WHEELS: type=wheels with wheel.png and / or wheel.syn. Fits every car, one pack active at a time.|"
        + "MAP: type=map with track.bin and optional background.png, squares.png, sprite1-4. Make maps with the Map Editor.|"
        + "Skin only: car.png with the UV layout of the base car.|"
        + "Do not put # comments after a value in addon.properties. Everything is checked when loaded; a broken add-on is shown with an error text.",

        "MAP EDITOR||"
        + "Main menu > Map Editor. Start from a template, a blank map or a stock track. Modes: PATH (grow and bend the road), HEIGHT (hills), LAND (terrain at the sides), PROPS (signs and sprites), MARKS (checkpoints, finish, centre line), WORLD (theme, colours, background, road texture, custom sprites).|"
        + "TEST drives the map, FLY is a free camera. SAVE installs the map and turns it on, Export zip writes a copy to share. The ? button opens an 8 page help.|"
        + "Painter: draws the background (tiles sideways, make it seamless), the road atlas (8 tiles of 32x32) and sprites. IMPORT PNG reads pictures from the add-ons/import folder.|"
        + "The map is identified by name + author: records, medals and ghosts belong to them.",

        "CAR BUILDER||"
        + "Main menu > Car Builder. A car or a set of wheels is a list of PARTS (boxes, wedges, prisms). Every part has its own 16x16 texture.|"
        + "PARTS page: add a box / wedge / prism, copy, delete, rename, MIRROR (same part on the other side), HIDE.|"
        + "SHAPE page: position (X, Y up, Z front), size, wedge taper (top width / length %, top shift), prism axis, sides and end size. - / + change a value by the chosen step, tap the number to type it, DRAG lets you push the part in the 3D view.|"
        + "PAINT page: pick a colour, tap the 16x16 grid; patterns (stripes, checker, carbon, noise, frame, window); IMPORT PNG shrinks a picture to 16x16.|"
        + "CAR page: name, author, type (car / wheels), base model, stats, LIGHT BAKED (the game has no lighting, so the shade is painted into the texture).|"
        + "FILE: SAVE TO GAME installs and enables it, EXPORT ZIP shares it, NEW (TEMPLATE), OPEN INSTALLED edits an add-on made here.|"
        + "Limits: 64 parts, 20000 triangles. The car front is +Z, the ground is Y=0. A wheel is built around Y=0.54 with its axle along X.",

        "MODDING THE GAME||"
        + "Own 3D model: export OBJ with UVs from Blender, then python tools/obj2syn.py model.obj car.syn (options --scale, --flip-z, --no-snap). Put car.syn + car.png + addon.properties in a zip.|"
        + "Stats: speed (km/h shown = speed x 1.5), accel, steer, steerMax, grip (1 = stock).|"
        + "Source code: JDK 21, build-all.bat. Game logic is in RallyGame.java, add-ons in AddonPack.java, maps in MapLibrary.java and MapEditor.java. Every change needs a version bump, because multiplayer only works between identical versions.|"
        + "Full guide: INSTRUCTIONS.md and README.md in the game folder."
    };

    /** The six original pages followed by the extra ones, as one text for the Instructions screen. */
    static String all(String[] original, boolean rightToLeft) { /*T*/ final long $t = rally3d.core.Trace.in(733); try {
        StringBuilder sb = new StringBuilder();
        int n = original.length;
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append("||");
            sb.append(rightToLeft ? original[n - 1 - i] : original[i]);
        }
        for (String p : PAGES) sb.append("||||").append(rally3d.core.Lang.t(p));
        return sb.toString();
     } finally { rally3d.core.Trace.out(733, $t); } }
}
