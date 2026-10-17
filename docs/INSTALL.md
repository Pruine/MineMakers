# Experimental local setup

**Upgrading from 0.1? Follow [UPGRADE-0.2.md](UPGRADE-0.2.md) first.**
The prebuilt ZIP already includes the Fabric JAR; building is optional.

Use the Steam Windows version of Trailmakers with its official Lua mod loader,
a legitimate Minecraft Java installation, Java 21, Fabric Loader 0.16.10 or newer,
Minecraft **1.21.1**, and Fabric API **0.115.1+1.21.1**. No game or launcher is bundled.
The Trailmakers version must be checked against its generated `tm.GetDocs()` API;
no installed copy was available during development.

1. Use the prebuilt JAR in the ZIP's `Minecraft/mods/` folder. If building from source,
   build the JAR using `fabric/gradlew build` (Windows: `gradlew.bat build`). Copy
   `fabric/build/libs/minemakers-fabric-0.2.2.jar` and the matching Fabric API JAR to
   the Minecraft instance's `mods` directory.
2. In Trailmakers, create a local mod using its mod tools, then copy `mod/main.lua`
   and the entire `mod/data_static` directory into that generated mod directory.
   Preserve the game's generated mod metadata. Use the existing
   `data_dynamic_willnotbeuploadedtoworkshop` directory created by Trailmakers.
   Select the folder that already contains `trailmakers.frame`. The release ZIP puts these files under `Trailmakers/`.
3. Start a **singleplayer Trailmakers session**, enable the mod, and stand in an
   open area outside a contraption. The default paired player ID is 0, configured
   in `data_static/config.lua`. Stay outside a seat while driving Minecraft.
4. Start Minecraft in a **new, disposable, creative superflat void world with cheats**.
   A void preset is `minecraft:air;minecraft:the_void`. Start in flight so there is
   time to link before falling. Keep this world separate from regular saves.
   The bridge temporarily writes barrier blocks to represent host terrain; normal
   unlink removes barriers it owns, but a crash can leave them in this dedicated world.
5. Run `/minemakers link "C:/absolute/path/to/the/mod/data_dynamic_willnotbeuploadedtoworkshop"` in Minecraft.
   The path must be the **same real local directory** Trailmakers reads/writes.
   Forward slashes avoid Brigadier's backslash escaping. Both games must run on the
   same PC with access to this directory. No network share or untrusted writer.
6. Run `/minemakers starter` once. This grants the requested items and armor sets;
   a full inventory may spill excess items into the world. Arrange the nine hotbar
   slots in Minecraft. `/gamemode creative` and `/gamemode survival` use vanilla
   commands. `/minemakers status` reports whether fresh host frames are arriving.
7. Install 64-bit [Python 3.10 or newer](https://www.python.org/downloads/windows/) if
   necessary. Extract the **whole ZIP**, then double-click
   **`Start-MineMakers-Overlay.cmd`** at its root and paste the existing frame-folder
   path when prompted. The `.py` files are in the ZIP's `tools/` folder, not in the
   game's dynamic data directory. No Python packages are needed. Alternatively,
   in PowerShell in the extracted ZIP, run:

   ```powershell
   py -3 .\tools\desktop_overlay.py "C:/absolute/path/to/the/mod/data_dynamic_willnotbeuploadedtoworkshop"
   ```

   Use the same directory as `/minemakers link`. Keep Minecraft open and running
   behind Trailmakers; do not minimize it. Use **windowed or borderless** mode in
   Trailmakers. Exclusive fullscreen cannot show a desktop overlay. Matching the
   two windows' aspect ratios avoids stretching the Minecraft UI.
8. Close Minecraft's menus, return focus to Trailmakers, and press **F8**. The helper
   arms capture, waits for fresh rendered pixels, and displays the actual Minecraft
   hand/HUD/GUI over the first-person Trailmakers camera. F8 releases controls again.
   This is experimental Windows code, not yet validated on an installed game. Minecraft's
   `pauseOnLostFocus` option is temporarily disabled while linked and restored
   on `/minemakers unlink` or world exit. Keep both games running; there is no automatic launcher.

With the helper captured, use **normal Minecraft controls**: mouse look,
left-click attack/mine, right-click use/place, E inventory, mouse wheel/1–9 hotbar,
W/A/S/D, Space, Shift, chat and Escape. Inventory clicks, dragging, scrolling,
shift/control modifiers and text are forwarded to vanilla Minecraft screens.
The helper stops capture on focus loss, F8, Windows keys, Alt-Tab or Alt-F4.
The cursor shown in inventory is a virtual cursor driven by raw mouse input.

Without the helper, the legacy keyboard-only fallback remains available:

| Control | Action |
|---|---|
| W/A/S/D, Space, Left Shift | Forwarded to vanilla Minecraft movement/jump/crouch or creative descent |
| J / L | Turn Minecraft yaw; the custom camera no longer depends on the native third-person controller |
| Up/Down arrow keys | Look up/down continuously |
| Hold F | Vanilla attack/mining |
| Hold G | Vanilla use/place; hold/release to draw/shoot the bow |
| 1–9 | Select Minecraft hotbar slot |
| Switch focus to Minecraft | Inventory, crafting, chat, menus and game-mode commands |

For creative flight, press Space twice with enough separation for the 50 ms input
snapshot interval, then use Space/Shift to ascend/descend. These keyboard fallback controls have no inventory/HUD overlay; use the Windows helper for those.

Release capture with F8 and close the helper before disabling the mod.
`/minemakers unlink` stops the bridge, removes mirrored barriers and combat proxies,
and releases Minecraft keys. Within two seconds the Lua side removes its objects,
restores native player visibility, restores its previous builder setting, and
returns native invincibility to false. Do not combine this prototype with another
mod controlling invincibility or the same player transform.

The Lua side's block/player hit functions are **not console commands** and are not
a native weapon detector. Calling them manually tests the pipeline, not gun integration.
