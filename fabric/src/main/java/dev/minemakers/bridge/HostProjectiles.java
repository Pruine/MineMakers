package dev.minemakers.bridge;

import dev.minemakers.MineMakers;
import dev.minemakers.mixin.ProjectileCollisionInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.projectile.*;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Vanilla trajectories and models; native Trailmakers raycasts decide surface impacts. */
public final class HostProjectiles {
    private record Probe(UUID entity,Vec3d start,Vec3d end) {}
    private record Anchor(Vec3d position,long expires) {}
    private static final Map<String,Probe> probes=new LinkedHashMap<>();
    private static final Map<UUID,Vec3d> previous=new HashMap<>();
    private static final Map<UUID,Anchor> anchors=new ConcurrentHashMap<>();
    private static long sequence;
    public static boolean nativeCollision(Entity entity) {
        return MineMakers.isHostWorld(entity.getWorld()) &&
            (entity instanceof PersistentProjectileEntity || entity instanceof ThrownItemEntity || entity instanceof TntEntity);
    }
    public static Vec3d anchored(Entity entity) {
        Anchor anchor=anchors.get(entity.getUuid());
        return anchor==null ? null : anchor.position();
    }
    public static BlockHitResult miss(RaycastContext context) {
        Vec3d end=context.getEnd(),delta=end.subtract(context.getStart());
        return BlockHitResult.createMissed(end,Direction.getFacing(delta.x,delta.y,delta.z),BlockPos.ofFloored(end));
    }
    public static void publish(ServerPlayerEntity player,Bridge bridge) {
        ServerWorld world=player.getServerWorld();
        List<String[]> rows=new ArrayList<>(); Set<UUID> seen=new HashSet<>();
        long serial=++sequence;
        for (Entity entity:world.iterateEntities()) {
            if (!nativeCollision(entity) || entity.isRemoved() || entity.squaredDistanceTo(player)>16384) continue;
            UUID id=entity.getUuid(); seen.add(id);
            Anchor anchor=anchors.get(id);
            if (anchor!=null) {
                if (world.getTime()>=anchor.expires()) { entity.discard(); anchors.remove(id); }
                continue;
            }
            Vec3d end=entity.getPos();
            Vec3d start=previous.put(id,end);
            if (start==null) start=end.subtract(entity.getVelocity());
            double distance=start.squaredDistanceTo(end);
            if (distance<1e-10 || distance>4096 || rows.size()>=128) continue;
            probes.put(id+":"+serial,new Probe(id,start,end));
            Vec3d a=start.subtract(MineMakers.origin()),b=end.subtract(MineMakers.origin());
            rows.add(Bridge.row("PROJECTILE",id,serial,a.x,a.y,a.z,b.x,b.y,b.z));
        }
        previous.keySet().retainAll(seen); anchors.keySet().retainAll(seen);
        while (probes.size()>1024) probes.remove(probes.keySet().iterator().next());
        bridge.publishProjectiles(serial,rows);
    }
    public static void hit(ServerPlayerEntity player,String[] row) {
        UUID id=UUID.fromString(row[3]);
        Probe probe=probes.remove(id+":"+Long.parseLong(row[4]));
        if (probe==null || anchors.containsKey(id)) return;
        Entity entity=player.getServerWorld().getEntity(id);
        if (entity==null || !nativeCollision(entity) || entity.isRemoved()) return;
        Vec3d at=MineMakers.origin().add(Bridge.point(row,5)),normal=Bridge.point(row,8);
        Vec3d segment=probe.end().subtract(probe.start());
        double along=at.subtract(probe.start()).dotProduct(segment)/segment.lengthSquared();
        Vec3d closest=probe.start().add(segment.multiply(Math.clamp(along,0,1)));
        if (at.squaredDistanceTo(closest)>.04 || at.squaredDistanceTo(entity.getPos())>4096 || normal.lengthSquared()<.5 || normal.lengthSquared()>1.5) return;
        normal=normal.normalize();
        Direction face=Direction.getFacing(normal.x,normal.y,normal.z);
        if (entity instanceof TntEntity) {
            entity.setPosition(at.add(normal.multiply(.01)));
            Vec3d velocity=entity.getVelocity();
            entity.setVelocity(velocity.subtract(normal.multiply(2*velocity.dotProduct(normal))).multiply(.5));
            entity.setOnGround(normal.y>.5);
            previous.remove(id);
        } else if (entity instanceof ProjectileEntity projectile) {
            // Run Minecraft's pearl teleport, thrown-item particles, and arrow impact logic.
            projectile.setPosition(at.subtract(projectile.getVelocity().normalize().multiply(.01)));
            ((ProjectileCollisionInvoker)projectile).minemakers$onCollision(new BlockHitResult(at,face,BlockPos.ofFloored(at.subtract(normal.multiply(.01))),false));
            if (projectile instanceof PersistentProjectileEntity && !projectile.isRemoved()) {
                Vec3d position=at.add(normal.multiply(.01));
                projectile.setPosition(position); projectile.setVelocity(Vec3d.ZERO);
                anchors.put(id,new Anchor(position,player.getServerWorld().getTime()+1200));
            }
        }
        probes.entrySet().removeIf(entry -> entry.getValue().entity().equals(id));
    }
    public static void clear() { probes.clear(); previous.clear(); anchors.clear(); sequence=0; }
}
