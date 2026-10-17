package dev.minemakers.client;

import dev.minemakers.MineMakers;
import dev.minemakers.bridge.Bridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import java.util.List;

/** Use the same interpolated eye/look as Minecraft's renderer, not raw 20 Hz tick positions. */
public final class ViewStream {
    private static long sequence,last;
    public static void publish(Camera camera) {
        MinecraftClient mc=MinecraftClient.getInstance();
        Bridge bridge=MineMakers.bridge;
        long now=System.nanoTime();
        if (bridge==null || mc.player==null || now-last<15_000_000L) return;
        last=now;
        double eye=mc.player.getEyeY()-mc.player.getY();
        var feet=camera.getPos().subtract(0,eye,0).subtract(MineMakers.origin());
        bridge.publishView(++sequence,List.<String[]>of(Bridge.row("VIEW",feet.x,feet.y,feet.z,
            camera.getYaw(),camera.getPitch(),eye,mc.currentScreen!=null ? 1 : 0)));
    }
}
