package dev.minemakers.mixin;

import dev.minemakers.client.DesktopController;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputUtil.class)
public abstract class DesktopInputMixin {
    @Inject(method="isKeyPressed",at=@At("HEAD"),cancellable=true)
    private static void minemakers$modifiers(long window,int key,CallbackInfoReturnable<Boolean> cir) {
        if (DesktopController.active()) cir.setReturnValue(DesktopController.isHeld(key));
    }
}
