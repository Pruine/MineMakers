package dev.minemakers.mixin;

import dev.minemakers.client.OverlayCapture;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererOverlayMixin {
    // Clear before geometry, preserving vanilla blocks, entities, breaking cracks and selection lines.
    @Inject(method="render",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void minemakers$transparentBackground(CallbackInfo ci) { OverlayCapture.clearWorld(); }
    @Inject(method={"renderSky","renderClouds","renderWeather","renderWorldBorder"},at=@At("HEAD"),cancellable=true)
    private void minemakers$hostBackground(CallbackInfo ci) { if (OverlayCapture.enabled()) ci.cancel(); }
}
