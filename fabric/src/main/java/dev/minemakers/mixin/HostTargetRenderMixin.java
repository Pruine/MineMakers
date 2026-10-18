package dev.minemakers.mixin;
import dev.minemakers.MineMakers;
import net.minecraft.entity.Entity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderDispatcher.class)
public abstract class HostTargetRenderMixin {
    // Invisible slime outer layers can still render. Contraption hit proxies have no visible Minecraft model.
    @Inject(method="render(Lnet/minecraft/entity/Entity;DDDFFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",at=@At("HEAD"),cancellable=true)
    private void minemakers$hideTarget(Entity entity,double x,double y,double z,float yaw,float delta,net.minecraft.client.util.math.MatrixStack matrices,net.minecraft.client.render.VertexConsumerProvider vertices,int light,CallbackInfo ci) {
        if (MineMakers.isHostTarget(entity)) ci.cancel();
    }
}
