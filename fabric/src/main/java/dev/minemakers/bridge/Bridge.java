package dev.minemakers.bridge;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** File I/O is off the game thread. Only complete immutable snapshots cross threads. */
public final class Bridge implements AutoCloseable {
    public record Input(boolean forward,boolean back,boolean left,boolean right,boolean jump,
                        boolean sneak,boolean attack,boolean use,float yaw,float pitch,int slot) {}
    public record HostFrame(Wire.Frame wire,long receivedNanos) {}
    private final Path directory;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"minemakers-file-bridge"); t.setDaemon(true); return t;
    });
    private final String session=UUID.randomUUID().toString();
    private volatile HostFrame incoming;
    private volatile Wire.Frame outgoing;
    private volatile String lastError="waiting for Trailmakers";
    private long written=-1;
    public Bridge(Path directory) {
        this.directory=directory;
        worker.scheduleWithFixedDelay(this::exchange,0,50,TimeUnit.MILLISECONDS);
    }
    private void exchange() {
        try {
            Wire.Frame frame=Wire.read(directory.resolve("trailmakers.frame"));
            HostFrame old=incoming;
            if (old==null || !old.wire().session().equals(frame.session()) || frame.sequence()>old.wire().sequence())
                incoming=new HostFrame(frame,System.nanoTime());
            Wire.Frame send=outgoing;
            if (send!=null && send.sequence()>written) { Wire.write(directory.resolve("minecraft.frame"),send); written=send.sequence(); }
            lastError="";
        } catch (IOException | IllegalArgumentException e) {
            lastError=e.getClass().getSimpleName()+": "+e.getMessage();
            // Output must still be published before the first host frame exists.
            try {
                Wire.Frame send=outgoing;
                if (send!=null && send.sequence()>written) { Wire.write(directory.resolve("minecraft.frame"),send); written=send.sequence(); }
            } catch (IOException ignored) { lastError="Cannot write bridge directory"; }
        }
    }
    public HostFrame current() {
        HostFrame f=incoming;
        return f!=null && System.nanoTime()-f.receivedNanos()<2_000_000_000L ? f : null;
    }
    public String session() { return session; }
    public String error() { return lastError; }
    public void publish(long sequence,List<String[]> rows) { outgoing=new Wire.Frame(session,sequence,List.copyOf(rows)); }
    public static String[] row(Object... values) { return Arrays.stream(values).map(String::valueOf).toArray(String[]::new); }
    public static Vec3d point(String[] row,int offset) {
        return new Vec3d(Wire.number(row[offset]),Wire.number(row[offset+1]),Wire.number(row[offset+2]));
    }
    public static Input input(HostFrame f,String expectedSession) {
        if (f==null) return null;
        boolean linked=false; String[] input=null;
        for (String[] r:f.wire().records()) {
            if (r.length==2 && r[0].equals("LINK") && r[1].equals(expectedSession)) linked=true;
            if (r.length==13 && r[0].equals("INPUT")) input=r;
        }
        if (!linked || input==null) return null;
        try {
            int slot=Integer.parseInt(input[12]);
            return new Input(input[1].equals("1"),input[2].equals("1"),input[3].equals("1"),input[4].equals("1"),
                input[5].equals("1"),input[6].equals("1"),input[7].equals("1"),input[8].equals("1"),
                (float)Wire.number(input[9]),(float)Math.clamp(Wire.number(input[10]),-89,89),Math.clamp(slot,0,8));
        } catch (IllegalArgumentException e) { return null; }
    }
    public static List<Box> colliders(HostFrame f,String expectedSession) {
        if (input(f,expectedSession)==null) return List.of();
        List<Box> boxes=new ArrayList<>();
        for (String[] r:f.wire().records()) if (r.length==7 && r[0].equals("COLLIDER") && boxes.size()<512) {
            try {
                Vec3d a=point(r,1),b=point(r,4);
                if (b.x>a.x && b.y>a.y && b.z>a.z && b.squaredDistanceTo(a)<4096) boxes.add(new Box(a,b));
            } catch (IllegalArgumentException ignored) { }
        }
        return List.copyOf(boxes);
    }
    @Override public void close() { worker.shutdownNow(); }
}
