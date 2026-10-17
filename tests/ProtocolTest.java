import dev.minemakers.bridge.Wire;
import dev.minemakers.bridge.BlockDamage;
import dev.minemakers.bridge.DesktopInput;
import dev.minemakers.bridge.HostPose;
import java.nio.file.*;
import java.util.*;

public final class ProtocolTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    private static void rejects(String text) {
        boolean rejected=false; try { Wire.decode(text); } catch (IllegalArgumentException e) { rejected=true; }
        check(rejected);
    }
    public static void main(String[] args) throws Exception {
        var rows=List.of(new String[]{"PLAYER","0","1","2","0","0","20","0","1","0"},new String[]{"BLOCK","0","0","0","minecraft:stone","3"});
        String encoded=Wire.encode(new Wire.Frame("session_1",42,rows));
        var decoded=Wire.decode(encoded);
        check(decoded.sequence()==42 && decoded.records().size()==2);
        for (int i=0;i<encoded.length();i++) rejects(encoded.substring(0,i));
        rejects(encoded.replace("END\tsession_1\t42","END\tsession_1\t43"));
        rejects(encoded.replace("MMT1","MMT2"));
        rejects(encoded.replace("\t42\t2","\t42\t99999"));
        for (String n:List.of("NaN","Infinity","-Infinity","30000001")) {
            boolean invalid=false; try { Wire.number(n); } catch (IllegalArgumentException e) { invalid=true; } check(invalid);
        }
        var hostRows=List.of(new String[]{"LINK","mc_session"},new String[]{"HOST_POSE","1","2","3","90","-30","1","0","0"});
        var hostFrame=new Wire.Frame("host",1,hostRows);
        var pose=HostPose.read(hostFrame,"mc_session");
        check(pose!=null && pose.x()==1 && pose.yaw()==90 && pose.grounded());
        check(HostPose.read(hostFrame,"stale_session")==null);
        check(HostPose.read(new Wire.Frame("host",2,List.<String[]>of(new String[]{"LINK","mc_session"})),"mc_session")==null);
        check(HostPose.read(new Wire.Frame("host",3,List.of(new String[]{"LINK","mc_session"},new String[]{"HOST_POSE","NaN","2","3","0","0","1","0","0"})),"mc_session")==null);
        var damage=new BlockDamage();
        var wood=damage.hit(1,20,40,1,false); var stone=damage.hit(2,20,120,2,false);
        check(wood.remaining()==26 && stone.remaining()==110);
        check(damage.hit(3,Float.MAX_VALUE,40,1,false).remaining()==26); // blast cannot one-shot
        check(!damage.hit(4,Float.MAX_VALUE,1,1,true).destroyed());
        int stage=-1; boolean destroyed=false;
        for (int i=0;i<4;i++) {
            var r=damage.hit(5,Float.MAX_VALUE,40,1,false);
            if (!r.destroyed()) { check(r.crackStage()>=stage); stage=r.crackStage(); }
            destroyed=r.destroyed();
            if (destroyed) break;
        }
        check(destroyed);
        damage.forget(1); check(damage.hit(1,1,40,1,false).remaining()==39);
        Path dir=Path.of(args[0]); Files.createDirectories(dir);
        Wire.write(dir.resolve("java.frame"),new Wire.Frame("session_1",42,rows));
        if (Files.exists(dir.resolve("lua.frame"))) {
            var lua=Wire.read(dir.resolve("lua.frame")); check(lua.session().equals("lua_test")); check(lua.records().size()==2);
        }
        var input=DesktopInput.parse(new Wire.Frame("desktop",1,List.of(
            new String[]{"ACTIVE","1"},new String[]{"POINTER","25","-8","0.5","0.25"},new String[]{"HELD","340"},
            new String[]{"EVENT","2","BUTTON","0","1","1","0.5","0.25"},new String[]{"EVENT","1","KEY","69","1","0"})));
        check(input.active() && input.totalX()==25 && input.totalY()==-8 && input.held().contains(340));
        check(input.events().getFirst().id()==1 && input.events().getLast().type().equals("BUTTON"));
        boolean badDesktop=false;
        try { DesktopInput.parse(new Wire.Frame("d",1,List.<String[]>of(new String[]{"POINTER","0","0","-0.2","0.5"}))); }
        catch (IllegalArgumentException e) { badDesktop=true; } check(badDesktop);
        System.out.println(checks+" Java protocol/damage checks passed");
    }
}
