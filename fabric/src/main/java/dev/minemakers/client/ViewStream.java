package dev.minemakers.client;

import dev.minemakers.MineMakers;
import dev.minemakers.bridge.Bridge;
import net.minecraft.client.MinecraftClient;
import dev.minemakers.bridge.FramePacer;
import java.util.List;

/** Sends intent, never Minecraft's simulated position. Trailmakers moves and renders locally. */
public final class ViewStream {
    private static long sequence;
    private static final FramePacer pacer=new FramePacer(16_666_667L);
    public static void publish() {
        MinecraftClient mc=MinecraftClient.getInstance();
        Bridge bridge=MineMakers.bridge;
        long now=System.nanoTime();
        if (bridge==null || mc.player==null || !pacer.due(now)) return;
        boolean menu=mc.currentScreen!=null;
        int forward=menu ? 0 : (mc.options.forwardKey.isPressed()?1:0)-(mc.options.backKey.isPressed()?1:0);
        int side=menu ? 0 : (mc.options.leftKey.isPressed()?1:0)-(mc.options.rightKey.isPressed()?1:0);
        bridge.publishView(++sequence,List.<String[]>of(Bridge.row("CONTROL",forward,side,
            !menu && mc.options.jumpKey.isPressed()?1:0,!menu && mc.options.sneakKey.isPressed()?1:0,
            !menu && (mc.options.sprintKey.isPressed() || mc.player.isSprinting())?1:0,
            mc.player.getAbilities().flying?1:0,mc.player.isCreative()?1:0,
            mc.player.getYaw(),mc.player.getPitch(),mc.player.getEyeY()-mc.player.getY(),menu?1:0,0)));
    }
}
