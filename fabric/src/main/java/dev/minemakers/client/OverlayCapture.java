package dev.minemakers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.minemakers.MineMakers;
import dev.minemakers.bridge.Bridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.option.Perspective;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import java.nio.*;
import java.nio.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Exports vanilla hand + GUI pixels, not a mock inventory. Windows compositing stays out of process. */
public final class OverlayCapture {
    private record Image(Path file,byte[] bytes) {}
    private static final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"minemakers-overlay-writer"); t.setDaemon(true); return t;
    });
    private static volatile Image pending;
    private static SimpleFramebuffer resized;
    private static ByteBuffer pixels;
    private static long lastCapture,sequence;
    private static Perspective previous;
    public static volatile String error="";
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("MineMakersOverlay");
    private static void failure(Exception e) {
        String message=e.toString();
        if (!message.equals(error)) LOG.warn("Minecraft overlay export failed: {}",message);
        error=message;
    }
    static { io.scheduleWithFixedDelay(OverlayCapture::write,0,10,TimeUnit.MILLISECONDS); }
    public static boolean enabled() {
        MinecraftClient mc=MinecraftClient.getInstance();
        return DesktopController.active() && MineMakers.bridge!=null && mc.world!=null && !mc.isWindowFocused();
    }
    public static void tick(MinecraftClient mc) {
        if (enabled()) {
            if (previous==null) previous=mc.options.getPerspective();
            mc.options.setPerspective(Perspective.FIRST_PERSON);
        } else if (previous!=null) { mc.options.setPerspective(previous); previous=null; }
    }
    public static void clearWorld() {
        if (!enabled()) return;
        RenderSystem.clearColor(0,0,0,0);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,MinecraftClient.IS_SYSTEM_MAC);
    }
    public static void capture() {
        Bridge bridge=MineMakers.bridge;
        long now=System.nanoTime();
        if (!enabled() || bridge==null || now-lastCapture<33_333_333L) return;
        lastCapture=now;
        MinecraftClient mc=MinecraftClient.getInstance();
        int sw=mc.getFramebuffer().textureWidth,sh=mc.getFramebuffer().textureHeight;
        if (sw<1 || sh<1) return;
        double ratio=Math.min(1,Math.min(1280.0/sw,720.0/sh));
        int w=Math.max(1,(int)(sw*ratio)),h=Math.max(1,(int)(sh*ratio));
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldPack=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            if (resized==null || resized.textureWidth!=w || resized.textureHeight!=h) {
                if (resized!=null) resized.delete();
                resized=new SimpleFramebuffer(w,h,false,MinecraftClient.IS_SYSTEM_MAC);
                if (pixels!=null) MemoryUtil.memFree(pixels);
                pixels=MemoryUtil.memAlloc(w*h*4);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,mc.getFramebuffer().fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,resized.fbo);
            GL30.glBlitFramebuffer(0,0,sw,sh,0,0,w,h,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,resized.fbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
            pixels.clear();
            GL11.glReadPixels(0,0,w,h,GL12.GL_BGRA,GL11.GL_UNSIGNED_BYTE,pixels);
            // 100-byte little-endian header; BGRA rows follow, bottom-up and premultiplied by GL blending.
            ByteBuffer data=ByteBuffer.allocate(100+w*h*4).order(ByteOrder.LITTLE_ENDIAN);
            data.put(new byte[]{'M','M','O','V'}).putInt(1).putInt(w).putInt(h).putLong(++sequence)
                .putLong(DesktopController.ack()).putInt(mc.currentScreen!=null ? 1 : 0);
            byte[] session=DesktopController.session().getBytes(StandardCharsets.US_ASCII);
            data.put(session,0,Math.min(64,session.length)); data.position(100);
            data.put(pixels);
            pending=new Image(bridge.directory().resolve("overlay.bgra"),data.array());
        } catch (RuntimeException e) { failure(e); }
        finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,oldPack);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
        }
    }
    private static void write() {
        Image image=pending;
        if (image==null) return;
        pending=null;
        Path temp=image.file().resolveSibling("overlay.bgra.tmp");
        try {
            Files.write(temp,image.bytes());
            try { Files.move(temp,image.file(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,image.file(),StandardCopyOption.REPLACE_EXISTING); }
            error="";
        } catch (IOException e) { failure(e); }
    }
}
