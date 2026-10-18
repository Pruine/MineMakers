package dev.minemakers.mixin;

import dev.minemakers.client.OverlayCapture;
import dev.minemakers.client.HostMotionClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererOverlayMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void minemakers$renderInput(CallbackInfo ci) { dev.minemakers.client.MineMakersClient.renderInput(); }
    @Inject(method="render",at=@At("TAIL"))
    private void minemakers$export(CallbackInfo ci) { OverlayCapture.capture(); }
    @Inject(method="getFov",at=@At("RETURN"),cancellable=true)
    private void minemakers$hostProjection(Camera camera,float delta,boolean world,CallbackInfoReturnable<Double> cir) {
        if (world && OverlayCapture.enabled()) cir.setReturnValue(HostMotionClient.fov());
    }
    @Inject(method="bobView",at=@At("HEAD"),cancellable=true)
    private void minemakers$stableWorld(CallbackInfo ci) { if (OverlayCapture.enabled()) ci.cancel(); }
    @Inject(method="renderBlur",at=@At("HEAD"),cancellable=true)
    private void minemakers$noOpaqueBlur(CallbackInfo ci) { if (OverlayCapture.enabled()) ci.cancel(); }
}
