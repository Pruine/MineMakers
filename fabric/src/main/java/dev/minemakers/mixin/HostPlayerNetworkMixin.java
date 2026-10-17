package dev.minemakers.mixin;

import dev.minemakers.MineMakers;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The host pose is authoritative; client move packets must not trigger rubber-banding. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class HostPlayerNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="onPlayerMove",at=@At("HEAD"),cancellable=true)
    private void minemakers$hostMotion(PlayerMoveC2SPacket packet,CallbackInfo ci) {
        if (MineMakers.isHostPlayer(player)) ci.cancel();
    }
}
