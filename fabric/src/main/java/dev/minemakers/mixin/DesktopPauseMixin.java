package dev.minemakers.mixin;

import dev.minemakers.client.DesktopController;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class DesktopPauseMixin {
    @Inject(method="isPaused",at=@At("HEAD"),cancellable=true)
    private void minemakers$keepHostAlive(CallbackInfoReturnable<Boolean> cir) {
        if (DesktopController.active()) cir.setReturnValue(false);
    }
}
