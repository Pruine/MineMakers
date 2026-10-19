import dev.minemakers.bridge.*;

/** Uneven 30 Hz host packets sampled by a 120 Hz renderer, including stalls and resets. */
public final class CameraTimelineTest {
    private static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    private static HostPose at(double x) { return new HostPose(x,0,0,0,0,true,false,false); }
    public static void main(String[] args) {
        PoseTimeline timeline=new PoseTimeline();
        long base=10_000_000_000L;
        int next=0,held=0,rawHeld=0,count=0; double prior=0,raw=0,priorRaw=0,maxStep=0;
        double[] jitter={.004,.012,.002,.007};
        for (int render=0;render<240;render++) {
            double now=render/120.0;
            while (next/30.0+jitter[next%jitter.length]<=now) {
                double host=next/30.0; raw=host*4.317;
                timeline.accept("session",next,base+(long)((host+jitter[next%4])*1e9),host,0,at(raw));next++;
            }
            var view=timeline.sample(base+(long)(now*1e9));
            if (view==null) continue;
            if (render>60) {
                count++; double step=view.x()-prior;
                if (Math.abs(step)<1e-6) held++;
                if (Math.abs(raw-priorRaw)<1e-6) rawHeld++;
                maxStep=Math.max(maxStep,Math.abs(step));
                check(step>=0,"Jitter must not reverse a constant forward walk");
            }
            prior=view.x();priorRaw=raw;
        }
        check(rawHeld>count*.6,"The fixture must reproduce held/snap camera motion");
        check(held<count*.05,"Interpolated camera must advance between host packets");
        check(maxStep<.06,"Packet arrivals must not create a full 30 Hz positional jump");
        double stopped=raw;
        check(Math.abs(timeline.sample(base+10_000_000_000L).x()-stopped)<1e-6,"Never predict through a wall after delivery stops");
        timeline.accept("session",100,base+11_000_000_000L,11,1,at(2));
        check(timeline.sample(base+11_000_000_000L).x()==2,"Teleport epoch must snap rather than interpolate across space");
        timeline.accept("session",99,base+12_000_000_000L,12,1,at(9));
        check(timeline.sample(base+12_000_000_000L).x()==2,"Out-of-order pose must not rewind state");
        timeline.accept("new_session",1,base+13_000_000_000L,0,0,at(3));
        check(timeline.sample(base+13_000_000_000L).x()==3,"Session/clock reset must discard old history");
        timeline.clear();check(timeline.sample(base)==null,"Unlink clears the view");
        System.out.printf("Camera cadence: held frames raw=%d/%d, interpolated=%d/%d; maximum step=%.4f m%n",rawHeld,count,held,count,maxStep);
    }
}
