package dev.minemakers.mixin;
import dev.minemakers.bridge.HostProjectiles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PersistentProjectileEntity.class)
public abstract class HostArrowCollisionMixin {
    @Shadow protected boolean inGround;
    @Shadow public int shake;
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;raycast(Lnet/minecraft/world/RaycastContext;)Lnet/minecraft/util/hit/BlockHitResult;"))
    private BlockHitResult minemakers$nativeSurface(World world,RaycastContext context) {
        return HostProjectiles.nativeCollision((Entity)(Object)this) ? HostProjectiles.miss(context) : world.raycast(context);
    }
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/block/BlockState;getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/shape/VoxelShape;"))
    private net.minecraft.util.shape.VoxelShape minemakers$nativeInside(net.minecraft.block.BlockState state,BlockView world,net.minecraft.util.math.BlockPos pos) {
        return HostProjectiles.nativeCollision((Entity)(Object)this) ? net.minecraft.util.shape.VoxelShapes.empty() : state.getCollisionShape(world,pos);
    }
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/entity/projectile/ProjectileEntity;tick()V",shift=At.Shift.AFTER),cancellable=true)
    private void minemakers$nativeAnchor(CallbackInfo ci) {
        Entity self=(Entity)(Object)this;
        Vec3d at=HostProjectiles.anchored(self);
        if (at!=null) { self.setPosition(at); self.setVelocity(Vec3d.ZERO); inGround=true; shake=0; ci.cancel(); }
    }
}
