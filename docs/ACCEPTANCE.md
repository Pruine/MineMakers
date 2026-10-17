# Validation record and in-game acceptance

Current cloud validation (native Trailmakers and Windows checks remain unrun):

- PASS: full Fabric/Loom Java 21 compile, resource processing and remapped JAR build.
- PASS: 119 Java protocol and accumulated-damage assertions, including every truncated
  prefix of a representative frame, nonfinite numbers, blast hit caps, resistance,
  unbreakable blocks, monotonic cracks, destruction and state reset.
- PASS: Python/Lua 5.2 tests for protocol framing and Java↔Lua interoperability.
- PASS: adapter contract test for physical object creation, crack texture updates,
  acknowledged combat event deduplication, damage/impulse forwarding, object removal,
  malformed frames and stale-file cleanup/control restoration.
- PASS: actual Minecraft 1.21.1 development client startup under Xvfb/Mesa with the
  client mixins loaded; a creative singleplayer world was opened.
- PASS: real transparent BGRA export of the vanilla hand, crosshair and hotbar;
  the 854×480 test frame contained both transparent background and opaque UI/hand pixels.
- PASS: desktop-protocol mouse delta rotated the actual Minecraft player to 45°;
  E opened the real creative inventory; remote slot clicks placed an oak log into
  hotbar slot 1; E closed it. These are real game behavior checks, not fake-API tests.
- PASS: a remote Escape opened the Minecraft pause menu while the linked server's
  snapshot sequence continued advancing, preventing host timeout during menus.
- UNRUN: Windows layered-window composition, Windows input hooks/raw mouse, actual
  Trailmakers/MoonSharp camera behavior, native physics/targeting and both games together.
- NOT IMPLEMENTED: native hit interception and custom auto-aim registration.
- Gradle reports `test NO-SOURCE`; it is not evidence that gameplay tests ran. The
  separate `tools/test.py` harness supplies the test results above.

Run these checks on the supported local game installations before calling the mod playable:

1. Both sides complete the fresh-session handshake. Pre-existing frame files cannot
   take control; stop either game and check that controls release within two seconds.
2. Start `tools/desktop_overlay.py` on Windows, focus borderless/windowed Trailmakers,
   press F8, and verify the real HUD, fist/held item, E inventory, clicks, dragging and
   text input. F8/focus loss must release capture. Then verify
   vanilla W/A/S/D, jump, crouch, double-Space creative flight, ascent/descent, yaw,
   pitch, attack and use work while Trailmakers has focus. Menus release input.
3. Check that linking enters the isolated host dimension and `/unlink` returns to
   the original Minecraft world. Walk into native walls, under ceilings and across
   slopes/platforms; test all four cardinal directions and crouch edges.
   Place wood/stone/bedrock/TNT on sampled ground. Walk a Trailmakers player into
   them and drive a contraption into them; check collision, scale and stability.
4. Mine a block, relog, and confirm Minecraft saves it. Confirm the corresponding
   Trailmakers object is removed and no duplicate collider remains after reconnect.
5. Strike the *same* contraption block with each sword, then shoot an arrow at it.
   Confirm damage ordering/cooldown and different impulses without duplicate hits.
   Rebuild/repair the creation and verify stale block IDs cannot damage another block.
6. Ignite TNT, wait for the real vanilla fuse, check block changes, proxy hits and
   contraption damage. Verify bedrock is unchanged. Test explosions near obstacles.
7. Throw an ender pearl onto sampled ground; confirm vanilla teleport, survival
   damage, no wall embedding, and creative immunity. Caves/overhangs remain unsupported.
8. Equip vanilla armor and inspect the proxy's armor indicator. Verify real vanilla
   armor calculations with Minecraft damage separately. Native pre-death armor
   mitigation cannot pass until the native hit interceptor is implemented.
9. After implementing the native hook, shoot a placed block: several hits must be
   required, cracks must advance, wood must break sooner than stone, and bedrock
   must survive. Retransmitting the same event ID must not apply another hit.
10. Test native weapon lock-on at multiple ranges/angles and teams. Retaining the
    native player is not evidence that targeting works; record the actual result.
11. Respawn/death, creative changes, switching dimensions, disconnects and stale
    snapshots must leave no stuck keys or invisible native player. `/unlink` must
    remove owned barrier blocks without changing Minecraft blocks placed by the user.

Completing items 9–10 requires the missing native plugin, the installed Trailmakers
version/API assemblies, and engine-specific testing. No current test result asserts
that those two requirements work.

0.3 validation additionally covers host-owned Lua locomotion against geometric ray
fixtures (cardinal directions, fractional-height floors, thin platforms, walls,
ceilings, stairs, crouch edges, flight and frame-rate variation). With the actual
Minecraft client and simulated host rays, remote W at yaw 90 moved 6.50 m along
host +X and A moved 4.27 m along host +Z; the Minecraft proxy followed at the same
height without void falling or correction warnings. The real inventory opened,
vanilla double-Space enabled host flight, and survival landed on host ground.
These checks simulate the Trailmakers API; they do not validate its native camera.
