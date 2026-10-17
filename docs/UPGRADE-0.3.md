# 0.3: Trailmakers owns the player

0.2 let Minecraft simulate movement and copied the resulting position into
Trailmakers. Coarse barrier samples were the player's only host-world collision,
so missing samples meant falling into Minecraft's void. Direct X mapping also
reversed horizontal handedness, and native character physics could compete with
the camera/body updates.

0.3 replaces that loop. Trailmakers receives movement intent and resolves the
player locally against its physics raycasts every update. Minecraft follows the
resolved position in a dedicated empty `minemakers:trailmakers` dimension. Its
player movement and movement-packet correction are disabled only for the linked
player. Minecraft still runs inventory, item use, screens, health and game modes.
This is a Minecraft-style Lua controller, rather than vanilla Minecraft physics.

## Install the whole update

1. Exit both games and close the overlay helper. Download and extract the entire
   `MineMakers-experimental-0.3.0.zip`.
2. Remove the old MineMakers JAR from Minecraft's `mods` folder and copy the new
   JAR from `Minecraft/mods/`. Keep Fabric API installed. Restart Minecraft so the
   new dimension and mixins load; an already running client cannot load this update.
3. Replace Trailmakers `main.lua` and the entire `data_static` folder from the ZIP's
   `Trailmakers/` directory. Keep `firstPerson=true`. Preserve the generated mod
   metadata and existing dynamic frame directory. Carry across a custom `playerId`.
4. Start Trailmakers outside a seat and enable the mod. Open a Minecraft
   singleplayer test world with commands enabled and run your existing
   `/minemakers link ".../data_dynamic_willnotbeuploadedtoworkshop"` command.
   Linking transfers the Minecraft player to the isolated host dimension;
   `/minemakers unlink` returns to the original world and position in this session.
   A manually selected void world and pre-link creative flight are no longer needed.
5. Run `Start-MineMakers-Overlay.cmd` from this extracted ZIP. Select the same
   existing frame directory. Keep Minecraft running behind windowed/borderless
   Trailmakers, focus Trailmakers, and press F8.

Use W/A/S/D, mouse look, Space to jump, Shift to crouch, and Ctrl to sprint.
Creative double-Space toggles flight; Space/Shift ascend/descend. E opens the real
Minecraft inventory. F8 or Alt-Tab releases captured controls. Menu screens stop
host movement. Stale input stops movement; disconnect restores the native body,
camera, building and repair settings.

Old and new Lua/JAR combinations are intentionally rejected: `CAPS HOST_MOTION_1`
is required. `control.frame` now carries intent, and `HOST_POSE` in
`trailmakers.frame` carries the resolved position. Old `view.frame` files are unused.

## Diagnostics and validation

The game frame directory now contains `minemakers-status.txt` with version,
configured player, camera state/error and age of the last control frame.
`minemakers-runtime-api.txt` contains the installed game's generated Lua API when
`tm.GetDocs()` is supported. Camera creation attempts the documented priority API
and falls back to the earlier three-argument overload. Activation happens once;
position and look are updated locally without restarting camera activation.

Automated geometry tests cover cardinal movement, fractional-height ground, thin
platforms, walls, ceilings, stepping, crouch edges, flight and frame rates. The real
Minecraft client has also been exercised with this Lua bridge and simulated host
raycasts: direction, position following, inventory, creative flight, survival
landing and unlink were checked. This does not verify the actual Trailmakers
camera or Windows compositor.

Ray probes approximate a player box; they are not a native capsule sweep. Small
features between rays, penetration by rapidly moving objects, and riding moving
contraptions remain limitations. Native weapon/auto-aim integration is still
unfinished. Ground barriers remain interaction/projectile proxies only; they no
longer determine player movement.
