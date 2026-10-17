package dev.minemakers.client;

import dev.minemakers.MineMakers;
import dev.minemakers.bridge.HostPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Repositions the Minecraft interaction/UI proxy; it does not drive the host camera. */
public final class HostMotionClient {
    public static void tick(MinecraftClient mc) {
        if (mc.player==null || !MineMakers.isHostPlayer(mc.player)) return;
        var bridge=MineMakers.bridge;
        var frame=bridge==null ? null : bridge.current();
        HostPose p=frame==null ? null : HostPose.read(frame.wire(),bridge.session());
        mc.player.setVelocity(Vec3d.ZERO); mc.player.fallDistance=0;
        if (p==null) return;
        var at=MineMakers.origin().add(p.x(),p.y(),p.z());
        mc.player.setPosition(at);
        mc.player.setPose(p.crouch() ? net.minecraft.entity.EntityPose.CROUCHING : net.minecraft.entity.EntityPose.STANDING);
        mc.player.setOnGround(p.grounded());
    }
}
