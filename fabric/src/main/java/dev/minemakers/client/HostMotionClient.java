package dev.minemakers.client;

import dev.minemakers.MineMakers;
import dev.minemakers.bridge.HostPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Repositions the Minecraft interaction/UI proxy; it does not drive the host camera. */
public final class HostMotionClient {
    public static HostPose pose() {
        var bridge=MineMakers.bridge;
        var frame=bridge==null ? null : bridge.current();
        return frame==null ? null : HostPose.read(frame.wire(),bridge.session());
    }
    public static double fov() {
        var bridge=MineMakers.bridge;
        var frame=bridge==null ? null : bridge.current();
        if (frame!=null) for (var row:frame.wire().records()) if (row.length==2 && row[0].equals("CAMERA_FOV")) {
            try { return Math.clamp(dev.minemakers.bridge.Wire.number(row[1]),30,110); } catch (IllegalArgumentException ignored) { }
        }
        return 60;
    }
    public static Vec3d eye() {
        var mc=MinecraftClient.getInstance();
        var pose=pose();
        if (pose==null || mc.player==null) return null;
        double height=pose.crouch()?1.27:mc.player.getStandingEyeHeight();
        return MineMakers.origin().add(pose.x(),pose.y()+height,pose.z());
    }
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
