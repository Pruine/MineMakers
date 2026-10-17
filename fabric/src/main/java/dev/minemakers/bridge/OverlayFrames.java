package dev.minemakers.bridge;

import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/** One pending image, one writer-owned image, and a small pool of reusable pixel arrays. */
public final class OverlayFrames {
    public record Image(Path file,byte[] bytes) {}
    private final AtomicReference<Image> pending=new AtomicReference<>();
    private final ArrayBlockingQueue<byte[]> spare=new ArrayBlockingQueue<>(3);
    public byte[] acquire(int size) {
        byte[] bytes;
        while ((bytes=spare.poll())!=null) if (bytes.length==size) return bytes;
        return new byte[size];
    }
    public void publish(Image image) { release(pending.getAndSet(image)); }
    public Image take() { return pending.getAndSet(null); }
    public void release(Image image) { if (image!=null) spare.offer(image.bytes()); }
}
