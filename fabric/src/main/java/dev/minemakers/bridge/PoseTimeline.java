package dev.minemakers.bridge;

import java.util.ArrayDeque;

/** Render at a steady point on the host clock instead of holding/jumping between arrivals. */
public final class PoseTimeline {
    public record View(double x,double y,double z,double eyeHeight,float yaw,float pitch) {}
    private record Sample(long time,HostPose pose) {}
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    private String session="";
    private long sequence=-1,epoch=-1,offset=Long.MAX_VALUE;
    private double interval=16_666_667;
    public void clear() { samples.clear(); session=""; sequence=-1; epoch=-1; offset=Long.MAX_VALUE; interval=16_666_667; }
    public void accept(String session,long sequence,long received,double hostSeconds,long epoch,HostPose pose) {
        if (this.session.equals(session) && sequence<=this.sequence) return;
        long time=Double.isFinite(hostSeconds) && hostSeconds>=0 ? (long)(hostSeconds*1e9) : received;
        Sample last=samples.peekLast();
        boolean reset=!this.session.equals(session) || epoch!=this.epoch || last==null || time<=last.time() ||
            time-last.time()>250_000_000L || squared(pose,last.pose())>16;
        if (reset) { samples.clear(); offset=Long.MAX_VALUE; interval=16_666_667; }
        else interval=interval*.85+Math.clamp(time-last.time(),4_000_000L,100_000_000L)*.15;
        this.session=session; this.sequence=sequence; this.epoch=epoch;
        offset=Math.min(offset,received-time);
        samples.addLast(new Sample(time,pose));
        while (samples.size()>64) samples.removeFirst();
    }
    public View sample(long now) {
        if (samples.isEmpty()) return null;
        long target=now-offset-delayNanos();
        Sample a=samples.peekFirst();
        for (Sample b:samples) {
            if (b.time()>=target) {
                double t=b.time()==a.time()?0:Math.clamp((double)(target-a.time())/(b.time()-a.time()),0,1);
                return blend(a.pose(),b.pose(),t);
            }
            a=b;
        }
        return blend(a.pose(),a.pose(),0); // no prediction through a wall when the host stops sending
    }
    public long delayNanos() { return (long)Math.clamp(interval*1.5,25_000_000,100_000_000); }
    private static double squared(HostPose a,HostPose b) { return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2); }
    private static double lerp(double a,double b,double t) { return a+(b-a)*t; }
    private static View blend(HostPose a,HostPose b,double t) {
        double turn=(b.yaw()-a.yaw()+540)%360-180;
        return new View(lerp(a.x(),b.x(),t),lerp(a.y(),b.y(),t),lerp(a.z(),b.z(),t),
            lerp(a.crouch()?1.27:1.62,b.crouch()?1.27:1.62,t),(float)(a.yaw()+turn*t),(float)lerp(a.pitch(),b.pitch(),t));
    }
}
