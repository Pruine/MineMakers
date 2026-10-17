# Validation record and in-game acceptance

Current cloud validation:

- PASS: full Fabric/Loom Java 21 compile, resource processing and remapped JAR build.
- PASS: 112 Java protocol and accumulated-damage assertions, including every truncated
  prefix of a representative frame, nonfinite numbers, blast hit caps, resistance,
  unbreakable blocks, monotonic cracks, destruction and state reset.
- PASS: Python/Lua 5.2 tests for protocol framing and Java↔Lua interoperability.
- PASS: adapter contract test for physical object creation, crack texture updates,
  acknowledged combat event deduplication, damage/impulse forwarding, object removal,
  malformed frames and stale-file cleanup/control restoration.
- UNRUN: Trailmakers execution, actual MoonSharp behavior, dual-game input, native
  physics/camera/targeting, Minecraft client startup, and gameplay across both games.
- NOT IMPLEMENTED: native hit interception and custom auto-aim registration.
- Gradle reports `test NO-SOURCE`; it is not evidence that gameplay tests ran. The
  separate `tools/test.py` harness supplies the test results above.

Run these checks on the supported local game installations before calling the mod playable:

1. Both sides complete the fresh-session handshake. Pre-existing frame files cannot
   take control; stop either game and check that controls release within two seconds.
2. Vanilla W/A/S/D, jump, crouch, double-Space creative flight, ascent/descent, yaw,
   pitch, attack and use work while Trailmakers has focus. Menus release input.
3. Place wood/stone/bedrock/TNT on sampled ground. Walk a Trailmakers player into
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
