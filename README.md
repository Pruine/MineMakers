# MineMakers

An **experimental Minecraft: Java Edition ↔ Trailmakers bridge**, inspired by
[SkyCraft](https://github.com/chasmlol/SkyCraft). Minecraft runs alongside Trailmakers
and owns inventory, combat rules, armor, blocks, TNT, ender pearls and game modes.
Trailmakers resolves movement/collisions. Both world layers use the same first-person view.

**This is a compiled development prototype, not a finished playable crossover.**
Version 0.4 renders the real Minecraft blocks, selection outlines, break cracks,
projectiles, hand and UI over the Trailmakers first-person camera. It replaces
per-frame image-file replacement with a shared memory mapping. Player motion and
projectile surface queries use Trailmakers raycasts; placed blocks have hidden
native collision cubes. Windows composition and both games together still need
local validation. Native incoming weapon damage and custom auto-aim require engine
work. There is no native Trailmakers plugin in this release.

0.4.1 fixes the camera pipeline separately from image delivery: host positions are
interpolated, mouse look is sampled locally each render, and Trailmakers follows
the camera pose of the presented Minecraft image. Input/file delivery and image
composition use separate workers. Native physics remains authoritative.

## Download

[Download the experimental prototype ZIP](https://github.com/Pruine/MineMakers/raw/refs/heads/main/downloads/MineMakers-experimental-0.4.1.zip).

Extract the whole ZIP and open **`START-HERE.txt`**. Double-click
**`Start-MineMakers-Overlay.cmd`** to run the UI helper. Paste the existing game
folder containing `trailmakers.frame`, normally
`data_dynamic_willnotbeuploadedtoworkshop`. The helper itself is under the ZIP's
`tools/` folder.

The ZIP includes the compiled Fabric JAR, Trailmakers Lua files, source, and setup
instructions. The limitations above still apply; this is not a finished crossover.

## What is implemented

- Fabric mod for **Minecraft 1.21.1, Java 21, Fabric Loader 0.16.10+** and Fabric API.
- One integrated-singleplayer Minecraft session paired with Trailmakers player 0.
- Host-owned movement and first-person camera. Minecraft sends controls/look; Lua
  resolves a player box against Trailmakers raycasts every update and returns the pose.
  Minecraft physics cannot move the linked player. X is reflected between the two
  coordinate systems to keep turning and strafing aligned.
- Experimental Windows overlay/input helper: real Minecraft world geometry, selection outlines, break cracks, projectiles, hand, held item,
  crosshair, hotbar, inventory and menus, with mouse look, click/scroll, typing and
  modifier forwarding. Requires Python 3.10+ and borderless/windowed Trailmakers.
  Press F8 to capture/release while Trailmakers is focused. See [upgrade steps](docs/UPGRADE-0.4.1.md).
- Focusing Minecraft now preserves native Minecraft controls instead of forcing
  its view and hotbar back to the Trailmakers snapshot.
- Vanilla wooden, stone, iron, golden, diamond and netherite swords; bow/arrows;
  armor; ender pearls; TNT/flint and steel; wood, stone and bedrock. These are real
  vanilla items, supplied by `/minemakers starter`, not reimplementations.
- Vanilla survival/creative switching, flight and invulnerability on the Minecraft
  side. Creative invulnerability is also set on the paired Trailmakers player.
- Invisible Trailmakers collision cubes for nearby solid Minecraft blocks. Vanilla
  Minecraft renders the visible block shapes/textures and breaking stages. World
  persistence stays in Minecraft. Native collision shapes are still simplified cubes.
- A three-slot mapped image transport targeting 60 FPS, independent direct host
  movement/mouse input, plus a file bridge with handshake, bounded snapshots, stale-input release, acknowledged
  combat events and duplicate-hit prevention.
- Minecraft melee/projectile/TNT damage against invisible vanilla slime proxies
  representing nearby Trailmakers contraption blocks, forwarded to the native blocks
  with damage and impulse. Proxy geometry is approximate and needs in-game testing.
- A dedicated empty Minecraft dimension isolates ordinary Minecraft terrain.
  Temporary sampled barriers support item targeting. Player collision uses native
  rays directly, including horizontal and ceiling probes. Arrows, thrown items
  and TNT send bounded surface sweeps to Trailmakers while retaining their vanilla
  trajectories/models; native hits trigger vanilla impacts, pearl teleport or TNT bounce.
- Accumulated incoming block-damage logic: resistance from hardness/blast resistance,
  no more than 35% total health removed per native hit, progressive cracks, eventual
  destruction, and unbreakable bedrock. **The native weapon detector that would feed
  this logic does not exist yet.** The Lua entry points are integration hooks only.

## What remains

| Requirement | Current limitation |
|---|---|
| Trailmakers guns progressively damage placed Minecraft blocks | Damage/crack pipeline implemented; stock Lua cannot observe those custom-object weapon hits. Needs native hook. |
| Native weapons damage Minecraft health with armor and knockback | No nonfatal native damage callback. Only a coarse native-death → Minecraft damage fallback exists; it cannot apply armor before native death. |
| Native auto-aim locks on Minecraft players | The existing native player is retained as a hidden static trigger underneath the visual proxy. Target selection has not been verified; custom target registration is unavailable through documented Lua. |
| Accurate world collision | Host ray probes approximate a player box. Small features between probes, moving-object penetration and moving-platform transport remain limitations; no native capsule sweep is available. |
| Minecraft graphics in Trailmakers | Real vanilla world/hand/UI via a transparent Windows desktop overlay. The two games do not share a depth buffer: Minecraft geometry can draw over native obstacles. Match window aspect ratios and the native camera FOV. |
| Multiplayer | One local pair only. No dedicated-server bridge, player mapping, replication or authoritative multiplayer validation. |
| SkyCraft-style automatic launch/hidden window | Launch Minecraft and the Windows helper manually. No launcher/account integration or automatic hiding. The helper is a desktop overlay, not a native Trailmakers renderer. |

See [installation and controls](docs/INSTALL.md), [protocol/architecture](docs/ARCHITECTURE.md)
and the [in-game acceptance checklist](docs/ACCEPTANCE.md).

## Build and test

```sh
# JDK 21 with javac is required.
cd fabric
./gradlew --no-daemon build
cd ..
python3 -m venv .tools/test-venv
.tools/test-venv/bin/pip install -r tools/test-requirements.txt
.tools/test-venv/bin/python tools/test.py
python3 tools/package.py
```

Windows: use `gradlew.bat` and `.tools\test-venv\Scripts\python.exe` as appropriate.
The remapped mod is `fabric/build/libs/minemakers-fabric-0.4.1.jar`. The package tool
requires that JAR; `--source-only` explicitly creates a developer source bundle.
Do not mistake the `-sources.jar` for an installable mod.

The Lua test double validates our adapter contract; it cannot establish Trailmakers
physics, rendering, MoonSharp behavior or weapon behavior. The Gradle build has no
JUnit tests; the separately invoked test harness is the test evidence.

Development needs outbound access to Fabric/Minecraft repositories and Maven/Gradle.
Runtime uses local frame files and one fixed image mapping in the Trailmakers mod's `data_dynamic_willnotbeuploadedtoworkshop` directory;
there is no bridge TCP listener and no secret or token requirement.

In this managed Linux workspace, `python3 tools/cloud_setup.py` additionally prepares
a checksum-verified JDK when needed, uses the existing system Java CA trust store and
managed HTTP proxy, builds the JAR, installs the pinned test dependency outside the
checkout, and runs the test harness. Normal local installations can use the manual
commands above. The Steam game itself is not installed in this cloud workspace.
