package dev.minemakers.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.minemakers.client.OverlayCapture;
import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundFogOverlayMixin {
    @Inject(method="applyFog",at=@At("TAIL"))
    private static void minemakers$hostFog(CallbackInfo ci) {
        if (OverlayCapture.enabled()) { RenderSystem.setShaderFogStart(1_000_000); RenderSystem.setShaderFogEnd(2_000_000); }
    }
}
