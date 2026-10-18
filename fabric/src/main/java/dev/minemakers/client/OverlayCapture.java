package dev.minemakers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.minemakers.MineMakers;
import dev.minemakers.bridge.Bridge;
import dev.minemakers.bridge.FramePacer;
import dev.minemakers.bridge.SharedOverlayWriter;
import dev.minemakers.bridge.OverlayFrames;
import dev.minemakers.bridge.OverlayFrames.Image;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
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

/** Exports vanilla world, hand and GUI pixels. Windows compositing stays out of process. */
public final class OverlayCapture {
    private record Report(Path file,String text) {}
    private static final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"minemakers-overlay-writer"); t.setDaemon(true); return t;
    });
    private static final OverlayFrames frames=new OverlayFrames();
    private static final FramePacer pacer=new FramePacer(16_666_667L);
    private static final AtomicLong written=new AtomicLong();
    private static volatile Report report;
    private static Report lastReport;
    private static SharedOverlayWriter shared;
    private static Path sharedPath;
    private static long measuredSince,rendered,captured,previousWritten;
    private static SimpleFramebuffer resized;
    private static ByteBuffer pixels;
    private static long sequence;
    private static Perspective previous;
    public static volatile String error="";
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("MineMakersOverlay");
    private static void failure(Exception e) {
        String message=e.toString();
        if (!message.equals(error)) LOG.warn("Minecraft overlay export failed: {}",message);
        error=message;
    }
    static { io.scheduleWithFixedDelay(OverlayCapture::write,0,2,TimeUnit.MILLISECONDS); }
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
        // WorldRenderer performs its normal clear immediately after this hook.
    }
    public static void capture() {
        Bridge bridge=MineMakers.bridge;
        long now=System.nanoTime();
        if (!enabled() || bridge==null) return;
        if (measuredSince==0) measuredSince=now;
        rendered++;
        if (now-measuredSince>=1_000_000_000L) {
            double seconds=(now-measuredSince)/1_000_000_000.0;
            long writes=written.get();
            report=new Report(bridge.directory().resolve("minemakers-client-status.txt"),String.format(java.util.Locale.ROOT,
                "MineMakers 0.4.0 | transport: shared mapping | overlay target: 60 FPS%nRender: %.1f FPS | captured: %.1f FPS | written: %.1f FPS%nCapture error: %s%n",
                rendered/seconds,captured/seconds,(writes-previousWritten)/seconds,error.isEmpty()?"none":error));
            previousWritten=writes; measuredSince=now; rendered=0; captured=0;
        }
        if (!pacer.due(now)) return;
        MinecraftClient mc=MinecraftClient.getInstance();
        int sw=mc.getFramebuffer().textureWidth,sh=mc.getFramebuffer().textureHeight;
        if (sw<1 || sh<1) return;
        double ratio=Math.min(1,Math.min(1280.0/sw,720.0/sh));
        int w=Math.max(1,(int)(sw*ratio)),h=Math.max(1,(int)(sh*ratio));
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),oldDraw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldPack=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        byte[] buffer=null;
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
            buffer=frames.acquire(100+w*h*4);
            Arrays.fill(buffer,36,100,(byte)0); // clear the previous session's padding
            ByteBuffer data=ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
            data.put(new byte[]{'M','M','O','V'}).putInt(1).putInt(w).putInt(h).putLong(++sequence)
                .putLong(DesktopController.ack()).putInt(mc.currentScreen!=null ? 1 : 0);
            byte[] session=DesktopController.session().getBytes(StandardCharsets.US_ASCII);
            data.put(session,0,Math.min(64,session.length)); data.position(100);
            data.put(pixels);
            frames.publish(new Image(bridge.directory().resolve("overlay.bgra"),buffer));
            buffer=null; captured++;
        } catch (RuntimeException e) { failure(e); }
        finally {
            if (buffer!=null) frames.release(new Image(bridge.directory().resolve("overlay.bgra"),buffer));
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,oldPack);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,oldDraw);
        }
    }
    private static void write() {
        Report next=report;
        if (next!=null && next!=lastReport) {
            try {
                Path temp=next.file().resolveSibling("minemakers-client-status.txt.tmp");
                Files.writeString(temp,next.text());
                Files.move(temp,next.file(),StandardCopyOption.REPLACE_EXISTING);
                lastReport=next;
            } catch (IOException ignored) { /* retry diagnostics without blocking image delivery */ }
        }
        Image image=frames.take();
        if (image==null) return;
        try {
            Path path=image.file().resolveSibling("overlay.shared");
            if (shared==null || !path.equals(sharedPath)) {
                if (shared!=null) shared.close();
                shared=new SharedOverlayWriter(path); sharedPath=path;
            }
            shared.publish(image.bytes());
            error=""; written.incrementAndGet();
        } catch (IOException | RuntimeException e) { failure(e); }
        finally { frames.release(image); }
    }
}
