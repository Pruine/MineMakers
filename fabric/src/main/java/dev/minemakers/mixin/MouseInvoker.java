package dev.minemakers.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Mouse.class)
public interface MouseInvoker {
    @Invoker("onCursorPos") void minemakers$cursor(long window,double x,double y);
    @Invoker("onMouseButton") void minemakers$button(long window,int button,int action,int mods);
    @Invoker("onMouseScroll") void minemakers$scroll(long window,double horizontal,double vertical);
}
