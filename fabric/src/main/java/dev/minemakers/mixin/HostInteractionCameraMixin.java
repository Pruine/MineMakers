package dev.minemakers.mixin;

import dev.minemakers.client.HostMotionClient;
import dev.minemakers.client.OverlayCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla targeting uses the same host eye/look as the rendered Minecraft layer. */
@Mixin(Entity.class)
public abstract class HostInteractionCameraMixin {
    @Inject(method="getCameraPosVec",at=@At("RETURN"),cancellable=true)
    private void minemakers$eye(float delta,CallbackInfoReturnable<Vec3d> cir) {
        if ((Object)this==MinecraftClient.getInstance().player && OverlayCapture.enabled()) {
            var eye=HostMotionClient.eye(); if (eye!=null) cir.setReturnValue(eye);
        }
    }
    @Inject(method="getRotationVec",at=@At("RETURN"),cancellable=true)
    private void minemakers$look(float delta,CallbackInfoReturnable<Vec3d> cir) {
        if ((Object)this==MinecraftClient.getInstance().player && OverlayCapture.enabled()) {
            var p=HostMotionClient.pose();
            if (p!=null) { double yaw=Math.toRadians(p.yaw()),pitch=Math.toRadians(p.pitch());
                cir.setReturnValue(new Vec3d(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch))); }
        }
    }
}
