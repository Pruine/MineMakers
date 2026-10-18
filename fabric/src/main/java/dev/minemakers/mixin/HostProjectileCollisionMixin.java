package dev.minemakers.mixin;
import dev.minemakers.bridge.HostProjectiles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ProjectileUtil.class)
public abstract class HostProjectileCollisionMixin {
    @Redirect(method="getCollision(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/Entity;Ljava/util/function/Predicate;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/world/World;FLnet/minecraft/world/RaycastContext$ShapeType;)Lnet/minecraft/util/hit/HitResult;",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/World;raycast(Lnet/minecraft/world/RaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;"))
    private static BlockHitResult minemakers$nativeSurface(World world,RaycastContext context,Vec3d start,Entity entity,Predicate<Entity> predicate,Vec3d velocity,World originalWorld,float margin,RaycastContext.ShapeType shape) {
        return HostProjectiles.nativeCollision(entity) ? HostProjectiles.miss(context) : world.raycast(context);
    }
}
