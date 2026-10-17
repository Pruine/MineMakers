package dev.minemakers.bridge;

/** Resolved Trailmakers feet pose. Coordinates are relative to the session anchor. */
public record HostPose(double x,double y,double z,float yaw,float pitch,boolean grounded,boolean flying,boolean crouch) {
    public static HostPose read(Wire.Frame frame,String session) {
        boolean linked=false;
        HostPose pose=null;
        for (String[] r:frame.records()) {
            if (r.length==2 && r[0].equals("LINK") && r[1].equals(session)) linked=true;
            if (r.length==9 && r[0].equals("HOST_POSE")) {
                try { pose=new HostPose(Wire.number(r[1]),Wire.number(r[2]),Wire.number(r[3]),
                    (float)Wire.number(r[4]),(float)Wire.number(r[5]),r[6].equals("1"),r[7].equals("1"),r[8].equals("1")); }
                catch (IllegalArgumentException ignored) { return null; }
            }
        }
        return linked ? pose : null;
    }
}
