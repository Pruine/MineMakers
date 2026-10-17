# Experimental local setup

Use the Steam Windows version of Trailmakers with its official Lua mod loader,
a legitimate Minecraft Java installation, Java 21, Fabric Loader 0.16.10 or newer,
Minecraft **1.21.1**, and Fabric API **0.115.1+1.21.1**. No game or launcher is bundled.
The Trailmakers version must be checked against its generated `tm.GetDocs()` API;
no installed copy was available during development.

1. Build the JAR using `fabric/gradlew build` (Windows: `gradlew.bat build`). Copy
   `fabric/build/libs/minemakers-fabric-0.1.0.jar` and the matching Fabric API JAR to
   the Minecraft instance's `mods` directory.
2. In Trailmakers, create a local mod using its mod tools, then copy `mod/main.lua`
   and the entire `mod/data_static` directory into that generated mod directory.
   Preserve the game's generated mod metadata. Create its `data_dynamic` directory
   if needed. The release ZIP puts these files under `Trailmakers/`.
3. Start a **singleplayer Trailmakers session**, enable the mod, and stand in an
   open area outside a contraption. The default paired player ID is 0, configured
   in `data_static/config.lua`. Stay outside a seat while driving Minecraft.
4. Start Minecraft in a **new, disposable, creative superflat void world with cheats**.
   A void preset is `minecraft:air;minecraft:the_void`. Start in flight so there is
   time to link before falling. Keep this world separate from regular saves.
   The bridge temporarily writes barrier blocks to represent host terrain; normal
   unlink removes barriers it owns, but a crash can leave them in this dedicated world.
5. Run `/minemakers link "C:/absolute/path/to/the/mod/data_dynamic"` in Minecraft.
   The path must be the **same real local directory** Trailmakers reads/writes.
   Forward slashes avoid Brigadier's backslash escaping. Both games must run on the
   same PC with access to this directory. No network share or untrusted writer.
6. Run `/minemakers starter` once. This grants the requested items and armor sets;
   a full inventory may spill excess items into the world. Arrange the nine hotbar
   slots in Minecraft. `/gamemode creative` and `/gamemode survival` use vanilla
   commands. `/minemakers status` reports whether fresh host frames are arriving.
7. Close Minecraft's menus and return focus to Trailmakers. Minecraft's
   `pauseOnLostFocus` option is temporarily disabled while linked and restored
   on `/minemakers unlink` or world exit. Keep both games running; there is no automatic launcher.

Controls while Trailmakers is focused:

| Control | Action |
|---|---|
| W/A/S/D, Space, Left Shift | Forwarded to vanilla Minecraft movement/jump/crouch or creative descent |
| Native Trailmakers mouse look | Player yaw; camera behavior needs in-game verification |
| Up/Down arrow keys | Adjust Minecraft pitch by 5 degrees per key-down |
| Hold F | Vanilla attack/mining |
| Hold G | Vanilla use/place; hold/release to draw/shoot the bow |
| 1–9 | Select Minecraft hotbar slot |
| Switch focus to Minecraft | Inventory, crafting, chat, menus and game-mode commands |

For creative flight, press Space twice with enough separation for the 200 ms input
snapshot interval, then use Space/Shift to ascend/descend. These are development
controls, not a complete Minecraft input/HUD replacement.

`/minemakers unlink` stops the bridge, removes mirrored barriers and combat proxies,
and releases Minecraft keys. Within two seconds the Lua side removes its objects,
restores native player visibility, restores its previous builder setting, and
returns native invincibility to false. Do not combine this prototype with another
mod controlling invincibility or the same player transform.

The Lua side's block/player hit functions are **not console commands** and are not
a native weapon detector. Calling them manually tests the pipeline, not gun integration.
