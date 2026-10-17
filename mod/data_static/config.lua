return {
    playerId=0, -- paired Trailmakers host; other players keep ordinary Trailmakers controls
    firstPerson=true, eyeHeight=1.62, turnSpeed=120,
    inputInterval=0.05, viewInterval=0.02,
    interval=0.2, timeout=2, maxBlocks=4096, maxTargets=64,
    nativeDamageScale=20, -- Minecraft health point -> Trailmakers block damage
    terrainRadius=4, -- 9x9 downward samples, 1 metre apart
    terrainDepth=12,
    builderUnit=0.25, -- tune against the installed Trailmakers version
}
