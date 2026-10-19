# Upgrade to 0.4.1: camera cadence and alignment

[Download the complete update](https://github.com/Pruine/MineMakers/raw/refs/heads/main/downloads/MineMakers-experimental-0.4.1.zip).

Close both games and the old helper. Extract the whole ZIP, replace the old
MineMakers JAR with `Minecraft/mods/minemakers-fabric-0.4.1.jar`, replace the
Trailmakers mod's `main.lua` and `data_static`, and use the new launcher/tools
folder. The helper now needs `desktop_runtime.py` beside `desktop_overlay.py` and
`desktop_protocol.py`; replace the whole tools folder. Keep only one MineMakers JAR.

Start both games, link the same existing frame folder, and run the new helper.
Focus windowed/borderless Trailmakers and press F8. The full setup instructions
are in [INSTALL.md](INSTALL.md). No Gradle build is required.

## What changed

Smooth entities with a stepping world camera require a separate fix from image
throughput. The previous Minecraft camera copied the newest host pose directly;
it could hold for several render frames then jump. The native camera separately
followed current physics/input, so the two layers could also use different poses.

- Minecraft now interpolates timestamped host position samples on its render clock.
  The buffer adapts to the received cadence (25–100 ms), avoids extrapolating through
  walls, and resets on teleports/session changes. Physics and collision stay native.
- Mouse look uses the current local render-frame input, without waiting for an
  echoed yaw/pitch from Trailmakers.
- The exported image carries the exact camera eye/look used to draw it. After
  presenting that image, the helper echoes its camera pose; the Trailmakers custom
  camera follows that view. Native physics still updates independently.
- Keyboard/raw-mouse handling, input-file delivery and image composition run
  independently. A slow image copy, GDI composition or file write cannot occupy
  the window's input callback. Input delivery targets up to 120 updates/second;
  image export retains a 60 FPS target, matching the documented native mod limit.
- The native mod requests updates every engine frame instead of gating on exactly
  1/60 second. This avoids alternate-frame skipping when frame times vary around
  that threshold. The game still controls the maximum update rate. Builds that reject zero fall
  back to the previously working target.

The mapped image format changed. `overlay.shared` is now version 2 and expands
slightly to carry camera metadata. Do not combine an old helper/JAR with this
update; stop them before upgrading so the mapping can be resized.

## Validation and limits

A deterministic test feeds jittered 30 Hz host samples into a 120 Hz render loop:
the old raw-copy camera holds on 134 of 179 measured frames, while interpolation
advances on all 179. Tests also cover teleports, stale/out-of-order poses, session
resets, native camera matching to a presented image, and continued input delivery
while the compositor is deliberately blocked.

These checks cover the algorithms and bridge contract. Windows layered-window
composition and the installed Trailmakers game cannot be run in this Linux cloud
environment. The native camera can only change as fast as the game runs its Lua
updates; the actual picture rate is limited by both games and the compositor.
The games still do not share a depth buffer, and the collision/weapon/auto-aim
limitations in the main README remain.

The normal status files include camera interpolation delay, independent input and
presentation rates, and whether the native camera is following a presented frame.
They are available for optional troubleshooting.
