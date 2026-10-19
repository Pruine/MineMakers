package dev.minemakers.client;

import dev.minemakers.MineMakers;
import dev.minemakers.bridge.HostPose;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Keeps authoritative physics separate from the interpolated view exported to both games. */
public final class HostMotionClient {
    private static final dev.minemakers.bridge.PoseTimeline timeline=new dev.minemakers.bridge.PoseTimeline();
    private static dev.minemakers.bridge.PoseTimeline.View view;
    public static boolean rendering() {
        var mc=MinecraftClient.getInstance();
        return MineMakers.isHostPlayer(mc.player) && mc.options.getPerspective().isFirstPerson() && view!=null;
    }
    public static dev.minemakers.bridge.PoseTimeline.View view() { return view; }
    public static double delayMillis() { return timeline.delayNanos()/1e6; }
    public static void beginFrame(MinecraftClient mc) {
        var bridge=MineMakers.bridge;
        var frame=bridge==null ? null : bridge.current();
        var pose=frame==null ? null : HostPose.read(frame.wire(),bridge.session());
        if (pose==null || mc.player==null) { timeline.clear(); view=null; return; }
        double clock=Double.NaN; long epoch=0;
        for (var r:frame.wire().records()) if (r.length==3 && r[0].equals("POSE_CLOCK")) {
            try { clock=dev.minemakers.bridge.Wire.number(r[1]); epoch=Long.parseLong(r[2]); } catch (IllegalArgumentException ignored) { }
        }
        timeline.accept(bridge.session()+frame.wire().session(),frame.wire().sequence(),frame.receivedNanos(),clock,epoch,pose);
        var sample=timeline.sample(System.nanoTime());
        // Mouse look belongs to this render frame; never wait for its round trip through Lua.
        view=new dev.minemakers.bridge.PoseTimeline.View(sample.x(),sample.y(),sample.z(),sample.eyeHeight(),mc.player.getYaw(),mc.player.getPitch());
    }
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
        var sample=view;
        if (sample==null) return null;
        return MineMakers.origin().add(sample.x(),sample.y()+sample.eyeHeight(),sample.z());
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
