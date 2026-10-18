package dev.minemakers.mixin;

import dev.minemakers.MineMakers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trailmakers resolves the linked player's motion. Never run a second collision solver. */
@Mixin(Entity.class)
public abstract class HostPlayerMovementMixin {
    @Inject(method="move",at=@At("HEAD"),cancellable=true)
    private void minemakers$hostMotion(MovementType type,Vec3d movement,CallbackInfo ci) {
        Entity self=(Entity)(Object)this;
        if (self instanceof net.minecraft.entity.TntEntity && dev.minemakers.bridge.HostProjectiles.nativeCollision(self)) {
            self.setOnGround(false); self.setPosition(self.getPos().add(movement)); ci.cancel();
        } else if (MineMakers.isHostPlayer(self)) {
            self.setVelocity(Vec3d.ZERO);
            self.fallDistance=0;
            ci.cancel();
        }
    }
}
