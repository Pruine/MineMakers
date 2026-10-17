package dev.minemakers.mixin;

import dev.minemakers.client.OverlayCapture;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererOverlayMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void minemakers$renderInput(CallbackInfo ci) { dev.minemakers.client.MineMakersClient.renderInput(); }
    @Inject(method="renderHand",at=@At("HEAD"))
    private void minemakers$transparentWorld(CallbackInfo ci) { OverlayCapture.clearWorld(); }
    @Inject(method="render",at=@At("TAIL"))
    private void minemakers$export(CallbackInfo ci) { OverlayCapture.capture(); }
    @Inject(method="renderBlur",at=@At("HEAD"),cancellable=true)
    private void minemakers$noOpaqueBlur(CallbackInfo ci) { if (OverlayCapture.enabled()) ci.cancel(); }
}
