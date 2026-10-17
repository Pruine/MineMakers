package dev.minemakers.mixin;

import dev.minemakers.client.OverlayCapture;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudOverlayMixin {
    // Vanilla's destination-color vignette writes opaque alpha over the entire transparent frame.
    @Inject(method="renderVignetteOverlay",at=@At("HEAD"),cancellable=true)
    private void minemakers$noOpaqueVignette(CallbackInfo ci) { if (OverlayCapture.enabled()) ci.cancel(); }
}
