# Upgrade to 0.4.0

[Download the complete ZIP](https://github.com/Pruine/MineMakers/raw/refs/heads/main/downloads/MineMakers-experimental-0.4.0.zip).
Extract the whole ZIP. This update requires the new JAR, Lua files **and** helper.
No Gradle build is needed.

1. Exit Minecraft and Trailmakers and close the previous overlay helper.
2. Remove the old MineMakers JAR from Minecraft's `mods` folder. Install
   `Minecraft/mods/minemakers-fabric-0.4.0.jar` from the ZIP. Keep the matching
   Fabric API for Minecraft 1.21.1 and use Java 21.
3. Replace the Trailmakers mod's `main.lua` and entire `data_static` directory
   with the copies under the ZIP's `Trailmakers/` folder. Keep the game's mod
   metadata and existing dynamic data folder. Keep `firstPerson=true`.
4. Start both games, enable the Trailmakers mod, and stand outside a seat.
   In Minecraft singleplayer, run `/minemakers link "D:/SteamLibrary/steamapps/common/Trailmakers/mods/minemakers/data_dynamic_willNotBeUploadedToWorkshop"`,
   substituting your **existing** folder containing `trailmakers.frame`.
5. Run `Start-MineMakers-Overlay.cmd` **from this newly extracted ZIP** and paste
   that same folder. Keep Minecraft running behind Trailmakers without minimizing
   it. Use windowed/borderless Trailmakers and match the windows' aspect ratios.
6. Focus Trailmakers and press F8. Check the vanilla hand, hotbar, blocks, selection
   outline, E inventory, mouse look and projectiles. F8 or Alt-Tab releases capture.

## Rendering and lag fix

The reported 116.8 render FPS / 59.9 capture FPS / 23.0 written FPS isolated the
image-file writer as the bottleneck. The new `overlay.shared` mapping replaces
per-frame image-file write/replace/reopen. An actual Minecraft client under
software Mesa delivered about 60 captured and written FPS. This is cloud evidence,
not a Windows/Trailmakers performance guarantee.

Minecraft now supplies the real block models/textures, block selection outlines,
mining cracks, projectiles, TNT, particles and hand/UI. Sky and weather are excluded
so Trailmakers supplies the surrounding world. Visible native block/projectile
proxy cubes are removed; placed solid blocks still have hidden native collision
cubes. Minecraft block persistence and item behavior remain vanilla.

The native player camera and movement read current helper input directly and no
longer wait for a round trip through Minecraft rendering. Arrows, thrown items and
TNT submit motion segments to native surface raycasts; impacts invoke Minecraft's
arrow/pearl/thrown-item behavior or a damped TNT bounce. Projectiles have a server
round-trip correction delay, not native rigid-body physics.

## Diagnostics

While F8 is active, inspect these files in the linked frame folder:

- `minemakers-client-status.txt`: render, capture and shared-mapping write FPS.
  Written FPS should track captured FPS rather than remaining at the old 23 FPS.
- `minemakers-overlay-status.txt`: Windows helper read, presentation and input FPS,
  plus average read/poll and composition time. This identifies a slow compositor.
- `minemakers-status.txt`: native camera/control state, Lua update rate and API errors.

`overlay.shared` is a fixed approximately 11 MB mapped buffer. Its file modification
time is not a frame heartbeat. The old helper cannot consume it. Stop both games
and the helper before removing it; it will be recreated when capture resumes.
The updated helper only falls back to old `overlay.bgra` when no mapping exists.
Do not reuse the old helper with the new JAR.

## Current visual and physics limits

The games do not share a depth buffer. Minecraft geometry behind a Trailmakers
obstacle can draw over that obstacle. Matching camera projection is required;
`cameraFov` in `data_static/config.lua` is the vertical Minecraft overlay FOV,
default 60 degrees to match the assumed native custom-camera lens. Adjust it if
block edges do not align; it does not change the native camera's lens.

Player collision remains a ray-probed box, placed-block collision remains cubic,
and projectile queries remain bounded point sweeps. Moving platforms, narrow
features between probes, explosion shielding and custom shape collisions are
incomplete. Stock Lua still lacks incoming custom-object weapon-hit callbacks
and custom auto-aim registration. Windows composition and both games together
require local testing; this update does not supply the missing native plugin.

Restart recovery also returns an unlinked player saved in the empty host dimension
to the overworld spawn, and removes stale contraption hit proxies when relinking.
