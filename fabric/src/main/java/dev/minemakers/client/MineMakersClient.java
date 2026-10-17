package dev.minemakers.client;

import dev.minemakers.MineMakers;
import dev.minemakers.bridge.Bridge;
import dev.minemakers.mixin.KeyBindingAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import java.util.HashMap;
import java.util.Map;

/** Trailmakers stays in the foreground; Minecraft supplies controls and UI. */
public final class MineMakersClient implements ClientModInitializer {
    private final Map<KeyBinding,Boolean> held=new HashMap<>();
    private Boolean oldPause;

    @Override public void onInitializeClient() { ClientTickEvents.START_CLIENT_TICK.register(this::tick);
        ClientTickEvents.END_CLIENT_TICK.register(HostMotionClient::tick);
    }
    public static void renderInput() {
        MinecraftClient mc=MinecraftClient.getInstance();
        DesktopController.tick(mc,MineMakers.bridge);
        OverlayCapture.tick(mc);
        ViewStream.publish();
    }
    private void key(KeyBinding key,boolean down) {
        boolean previous=held.getOrDefault(key,false);
        key.setPressed(down);
        if (down && !previous) KeyBinding.onKeyPressed(((KeyBindingAccessor)key).minemakers$boundKey());
        held.put(key,down);
    }
    private void tick(MinecraftClient mc) {
        Bridge bridge=MineMakers.bridge;
        if (bridge!=null) {
            if (oldPause==null) oldPause=mc.options.pauseOnLostFocus;
            mc.options.pauseOnLostFocus=false;
        }
        boolean desktop=DesktopController.tick(mc,bridge);
        OverlayCapture.tick(mc);
        Bridge.Input input=bridge==null ? null : Bridge.input(bridge.current(),bridge.session());
        if (desktop || mc.isWindowFocused() || input==null || mc.player==null || mc.currentScreen!=null) {
            for (KeyBinding key:held.keySet()) key.setPressed(false);
            held.clear();
            if (bridge==null && oldPause!=null) { mc.options.pauseOnLostFocus=oldPause; oldPause=null; }
            return;
        }
        if (oldPause==null) oldPause=mc.options.pauseOnLostFocus;
        mc.options.pauseOnLostFocus=false;
        mc.player.setYaw(input.yaw()); mc.player.setPitch(input.pitch());
        mc.player.getInventory().selectedSlot=input.slot();
        key(mc.options.forwardKey,input.forward()); key(mc.options.backKey,input.back());
        key(mc.options.leftKey,input.left()); key(mc.options.rightKey,input.right());
        key(mc.options.jumpKey,input.jump()); key(mc.options.sneakKey,input.sneak());
        key(mc.options.attackKey,input.attack()); key(mc.options.useKey,input.use());
    }
}
