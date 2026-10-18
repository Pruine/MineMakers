package dev.minemakers.mixin;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ProjectileEntity.class)
public interface ProjectileCollisionInvoker {
    @Invoker("onCollision") void minemakers$onCollision(HitResult hit);
}
