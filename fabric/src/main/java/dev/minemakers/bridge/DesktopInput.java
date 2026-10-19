package dev.minemakers.bridge;

import java.util.*;

/** Untrusted local file parser. Events are replayed until acknowledged by the compositor. */
public record DesktopInput(String session, long sequence, boolean active, double totalX, double totalY,
                           double cursorX, double cursorY, Set<Integer> held, List<Event> events) {
    public record Event(long id,String type,List<String> args) {}
    public static DesktopInput parse(Wire.Frame frame) {
        boolean active=false; double x=0,y=0,cx=0.5,cy=0.5;
        Set<Integer> held=new HashSet<>(); List<Event> events=new ArrayList<>();
        for (String[] r:frame.records()) {
            if (r.length==2 && r[0].equals("ACTIVE")) active=r[1].equals("1");
            else if (r.length==5 && r[0].equals("POINTER")) {
                x=Wire.number(r[1]); y=Wire.number(r[2]); cx=Wire.number(r[3]); cy=Wire.number(r[4]);
                if (cx<0 || cx>1 || cy<0 || cy>1) throw new IllegalArgumentException("Cursor out of bounds");
            } else if (r.length==2 && r[0].equals("HELD")) {
                int key=Integer.parseInt(r[1]); if (key<0 || key>348) throw new IllegalArgumentException("Bad key"); held.add(key);
            } else if (r.length==9 && r[0].equals("VIEW")) {
                // Presentation feedback is consumed by Lua, but shares this input envelope.
                if (!r[1].matches("[A-Za-z0-9_-]{1,64}") || Long.parseLong(r[2])<0) throw new IllegalArgumentException("Bad view session/sequence");
                for (int i=3;i<9;i++) Wire.number(r[i]);
                if (Wire.number(r[7])< -90 || Wire.number(r[7])>90 || Wire.number(r[8])<30 || Wire.number(r[8])>110) throw new IllegalArgumentException("Bad view projection");
            } else if (r.length>=4 && r[0].equals("EVENT")) {
                long id=Long.parseLong(r[1]);
                String type=r[2]; int expected=switch(type) { case "KEY" -> 6; case "CHAR","SCROLL" -> 4; case "BUTTON" -> 8; default -> 0; };
                if (id<1 || r.length!=expected || events.size()>=256) throw new IllegalArgumentException("Bad event");
                for (int i=3;i<r.length;i++) Wire.number(r[i]);
                events.add(new Event(id,type,List.of(Arrays.copyOfRange(r,3,r.length))));
            } else throw new IllegalArgumentException("Unknown desktop row");
        }
        events.sort(Comparator.comparingLong(Event::id));
        return new DesktopInput(frame.session(),frame.sequence(),active,x,y,cx,cy,Set.copyOf(held),List.copyOf(events));
    }
}
