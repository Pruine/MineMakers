# 0.2: first person, mouse look and Minecraft UI

The 0.1 prototype only moved the Trailmakers body and placed proxy objects. It did
not create a first-person camera or export Minecraft's HUD, inventory, or hand.
It also forcibly reset Minecraft yaw/pitch even while Minecraft had focus. Those
were implementation gaps, not a setup error or a Java-version problem.

0.2 adds a first-person camera, a high-frequency eye/rotation stream, a vanilla
hand/HUD/GUI exporter, and a Windows desktop overlay/input helper. The helper is
needed because the documented Trailmakers Lua UI API cannot composite a Minecraft
framebuffer or forward a complete desktop mouse/keyboard stream on its own.

## Replace the old installation

1. Download `MineMakers-experimental-0.2.0.zip` from the repository's `downloads/`
   directory. **You do not need to rebuild it.**
2. Unlink the old bridge, exit both games, and remove the old
   `minemakers-fabric-0.1.0.jar` from Minecraft's `mods` folder. Copy the new JAR
   from the ZIP's `Minecraft/mods/` folder. Keep the matching Fabric API installed.
3. Replace the Trailmakers mod's `main.lua` and `data_static` with the new
   `Trailmakers/` files. If you changed `playerId` or other settings, carry those
   values into the new `config.lua`; keep `firstPerson=true`. Do not replace your
   Minecraft save. Old files in `data_dynamic` are ignored until a fresh handshake.
4. Start both games and run the same `/minemakers link ".../data_dynamic"` command
   in Minecraft. The Trailmakers view should now be first person and should turn
   with the Minecraft view when Minecraft has focus.
5. Install **64-bit Python 3.10+** on Windows. Open PowerShell in the extracted ZIP:

   ```powershell
   py -3 .\tools\desktop_overlay.py "C:/the/same/Trailmakers/mod/data_dynamic"
   ```

6. Set Trailmakers to **windowed or borderless**, keep Minecraft running behind it
   (not minimized), focus Trailmakers, and press **F8**. Wait for the helper to
   receive a fresh Minecraft overlay frame. Then use normal Minecraft controls:
   mouse look, left/right click, E inventory, wheel/1–9, Space, Shift and chat.
7. Press **F8** again to release controls. Alt-Tab/Windows keys also release capture.
   The helper only captures inputs while it is armed and Trailmakers is foreground.
   Closing it or losing fresh desktop input releases Minecraft keys/buttons.

The first-person camera is provided by Lua. The hand, hotbar and inventory are
Minecraft's real rendered pixels, displayed in a separate transparent Windows
window. They are not drawn by the Trailmakers render engine. This approach cannot
work over exclusive fullscreen, and it does not solve native weapon hits, auto-aim,
accurate host geometry or multiplayer.

## Diagnostics

- **Still third person:** replace the Trailmakers Lua files, not just the JAR;
  confirm `firstPerson=true`. Check the game's mod log for a camera API error.
- **No UI:** the helper is required. Confirm both games use the same real
  `data_dynamic` path and that `desktop.frame`, `view.frame` and `overlay.bgra`
  update there. Focus Trailmakers and press F8; use borderless/windowed mode.
- **Helper says waiting:** keep Minecraft running and unfocused behind Trailmakers,
  close its loading screen, check the new JAR is loaded, and check Minecraft's
  `latest.log` for a mixin/capture failure. Do not install both versions of the JAR.
- **Black opaque background:** this renderer has not yet been verified on Windows.
  Disable shader/post-processing mods and include Minecraft's log in a bug report;
  do not treat this as confirmation that full compositing works.
- **No mouse capture:** run both games and the helper at the same Windows integrity
  level. The helper does not need administrator privileges. Raw relative mouse
  input is supported; absolute pointing/tablet input is not implemented.
- **UI stretched:** use the same aspect ratio for both windows. The initial export
  is capped at 1280×720 and 15 FPS; this is a compatibility prototype.

Compilation, framing, acknowledgments and camera contracts are tested in the cloud.
The actual Windows compositor and Trailmakers camera/input behavior remain unverified
until exercised on the user's installed games. This update should not be described
as a finished or fully tested full-Minecraft port.
