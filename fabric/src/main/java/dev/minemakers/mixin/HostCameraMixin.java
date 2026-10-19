package dev.minemakers.mixin;

import dev.minemakers.client.HostMotionClient;
import dev.minemakers.client.OverlayCapture;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class HostCameraMixin {
    @Shadow protected abstract void setPos(double x,double y,double z);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="update",at=@At("TAIL"))
    private void minemakers$renderPose(CallbackInfo ci) {
        if (!HostMotionClient.rendering()) return;
        var eye=HostMotionClient.eye();
        var pose=HostMotionClient.view();
        if (eye!=null && pose!=null) { setPos(eye.x,eye.y,eye.z); setRotation(pose.yaw(),pose.pitch()); }
    }
}
