package dev.minemakers;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.minemakers.bridge.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.registry.Registries;
import net.minecraft.world.GameMode;
import java.nio.file.Path;
import java.util.*;

/** One local Minecraft player paired with one Trailmakers session. Disabled until explicitly linked. */
public final class MineMakers implements ModInitializer {
    public static volatile Bridge bridge;
    public static volatile List<Box> colliders=List.of();
    private static volatile UUID playerId;
    private static volatile Vec3d origin=Vec3d.ZERO;
    private static long sequence,eventId,lastHostSequence=-1;
    private static String hostSession="";
    private static final Map<Long,String[]> events=new LinkedHashMap<>();
    private static final Map<String,SlimeEntity> targets=new HashMap<>();
    private static final Map<UUID,String> targetIds=new java.util.concurrent.ConcurrentHashMap<>();
    private static final BlockDamage damage=new BlockDamage();
    private static long ticks;
    private static long lastHostEvent=0;
    private static final Set<BlockPos> terrain=new HashSet<>();
    private static ServerWorld linkedWorld;
    private static ServerPlayerEntity linkedPlayer;
    private static ServerWorld returnWorld;
    private static Vec3d returnPosition;
    private static float returnYaw,returnPitch;
    private static Vec3d lastResolved,pendingTeleport;
    private static final net.minecraft.registry.RegistryKey<net.minecraft.world.World> HOST_WORLD=
        net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,net.minecraft.util.Identifier.of("minemakers","trailmakers"));
    public static boolean isHostPlayer(Entity entity) {
        return entity!=null && bridge!=null && playerId!=null && playerId.equals(entity.getUuid());
    }
    public static boolean isHostWorld(net.minecraft.world.World world) { return bridge!=null && world.getRegistryKey().equals(HOST_WORLD); }
    public static boolean isHostTarget(Entity entity) { return bridge!=null && targetIds.containsKey(entity.getUuid()); }
    private static final Map<Long,BlockState> damagedStates=new HashMap<>();

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> {
            dispatcher.register(CommandManager.literal("minemakers").requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("link").then(CommandManager.argument("directory",StringArgumentType.string())
                    .executes(c -> {
                        ServerPlayerEntity p=c.getSource().getPlayerOrThrow();
                        if (!c.getSource().getServer().isSingleplayer()) {
                            c.getSource().sendError(Text.literal("MineMakers currently supports integrated singleplayer only.")); return 0;
                        }
                        unlink();
                        ServerWorld hostWorld=c.getSource().getServer().getWorld(HOST_WORLD);
                        if (hostWorld==null) {
                            c.getSource().sendError(Text.literal("MineMakers host dimension missing. Restart Minecraft after installing the updated JAR.")); return 0;
                        }
                        // Remove only our tagged proxies (or the exact legacy proxy signature) after a crash/restart.
                        for (Entity old:hostWorld.iterateEntities()) if (old.getCommandTags().contains("minemakers_target") ||
                            (old instanceof SlimeEntity slime && slime.isAiDisabled() && slime.hasNoGravity() && slime.isSilent() && slime.isPersistent() && slime.getSize()==1)) old.discard();
                        Path directory=Path.of(StringArgumentType.getString(c,"directory")).toAbsolutePath();
                        if (!java.nio.file.Files.isDirectory(directory)) {
                            c.getSource().sendError(Text.literal("Bridge directory does not exist; use the Trailmakers folder containing trailmakers.frame.")); return 0;
                        }
                        returnWorld=p.getServerWorld(); returnPosition=p.getPos(); returnYaw=p.getYaw(); returnPitch=p.getPitch();
                        // A dedicated void dimension isolates the bridge from normal Minecraft terrain.
                        p.teleport(hostWorld,0,128,0,p.getYaw(),p.getPitch());
                        linkedWorld=hostWorld; linkedPlayer=p; playerId=p.getUuid(); origin=new Vec3d(0,128,0);
                        lastResolved=p.getPos(); pendingTeleport=null;
                        bridge=new Bridge(directory);
                        sequence=0; eventId=0; lastHostSequence=-1; hostSession="";
                        c.getSource().sendFeedback(() -> Text.literal("MineMakers linked: Trailmakers owns movement and collisions. Minecraft is now an interaction/UI proxy."),false);
                        return 1;
                    })))
                .then(CommandManager.literal("unlink").executes(c -> { unlink(); return 1; }))
                .then(CommandManager.literal("status").executes(c -> {
                    String status=bridge==null ? "not linked" : bridge.current()==null ? "host disconnected: "+bridge.error() : "host connected; custom weapon-hit and auto-aim hooks unavailable";
                    c.getSource().sendFeedback(() -> Text.literal("MineMakers: "+status),false); return 1;
                }))
                .then(CommandManager.literal("starter").executes(c -> { starter(c.getSource().getPlayerOrThrow()); return 1; })));
        });
        ServerTickEvents.END_SERVER_TICK.register(MineMakers::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> unlink());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount) -> {
            String target=targetIds.get(entity.getUuid());
            if (target==null || bridge==null) return true;
            Entity attacker=source.getAttacker();
            if (attacker==null || !attacker.getUuid().equals(playerId)) return false;
            Vec3d direction=entity.getPos().subtract(attacker.getPos()).normalize();
            event("HIT",target,amount,direction.x,direction.y,direction.z,Math.min(12,2+amount*0.5),source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION) ? "Explosion" : "Generic");
            return false; // proxy never dies or drops items
        });
    }
    private static void starter(ServerPlayerEntity p) {
        Item[] items={Items.ENDER_PEARL,Items.TNT,Items.FLINT_AND_STEEL,Items.WOODEN_SWORD,Items.STONE_SWORD,
            Items.IRON_SWORD,Items.GOLDEN_SWORD,Items.DIAMOND_SWORD,Items.NETHERITE_SWORD,Items.BOW,Items.ARROW,
            Items.LEATHER_HELMET,Items.LEATHER_CHESTPLATE,Items.LEATHER_LEGGINGS,Items.LEATHER_BOOTS,
            Items.CHAINMAIL_HELMET,Items.CHAINMAIL_CHESTPLATE,Items.CHAINMAIL_LEGGINGS,Items.CHAINMAIL_BOOTS,
            Items.IRON_HELMET,Items.IRON_CHESTPLATE,Items.IRON_LEGGINGS,Items.IRON_BOOTS,
            Items.GOLDEN_HELMET,Items.GOLDEN_CHESTPLATE,Items.GOLDEN_LEGGINGS,Items.GOLDEN_BOOTS,
            Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS,
            Items.NETHERITE_HELMET,Items.NETHERITE_CHESTPLATE,Items.NETHERITE_LEGGINGS,Items.NETHERITE_BOOTS,
            Items.OAK_PLANKS,Items.STONE,Items.BEDROCK};
        for (Item item:items) p.giveItemStack(new ItemStack(item,item.getMaxCount()==1 ? 1 : Math.min(64,item.getMaxCount())));
    }
    public static void event(Object... data) {
        if (bridge==null) return;
        if (events.size()>=256) return; // bounded reliable outbox; do not repeat unacknowledged combat
        long id=++eventId; Object[] row=new Object[data.length+2]; row[0]="EVENT"; row[1]=id;
        System.arraycopy(data,0,row,2,data.length); events.put(id,Bridge.row(row));
    }
    private static boolean linked(Bridge.HostFrame f) {
        return bridge!=null && Bridge.input(f,bridge.session())!=null;
    }
    private static void tick(MinecraftServer server) {
        if (bridge==null || playerId==null) {
            // A crashed/closed bridge cannot leave its saved player falling in an empty dimension.
            if (server.isSingleplayer()) for (ServerPlayerEntity stranded:server.getPlayerManager().getPlayerList()) {
                if (!stranded.isAlive() || !stranded.getWorld().getRegistryKey().equals(HOST_WORLD)) continue;
                ServerWorld safe=server.getOverworld(); BlockPos spawn=safe.getSpawnPos();
                int y=Math.max(spawn.getY(),safe.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,spawn.getX(),spawn.getZ()));
                stranded.teleport(safe,spawn.getX()+.5,y+1,spawn.getZ()+.5,safe.getSpawnAngle(),0);
                stranded.sendMessage(Text.literal("MineMakers restored your player to the overworld spawn after an unlinked session. Link again when both games are running."),false);
            }
            return;
        }
        ServerPlayerEntity p=server.getPlayerManager().getPlayer(playerId);
        if (p==null || p.getServerWorld()!=linkedWorld) { unlink(); return; }
        ticks++;
        Bridge.HostFrame host=bridge.current();
        colliders=Bridge.colliders(host,bridge.session());
        if (host!=null && linked(host) && (!hostSession.equals(host.wire().session()) || host.wire().sequence()>lastHostSequence)) {
            if (!hostSession.equals(host.wire().session())) { hostSession=host.wire().session(); lastHostSequence=-1; lastHostEvent=0; }
            lastHostSequence=host.wire().sequence();
            applyHost(p,host.wire());
        }
        if (lastResolved!=null && pendingTeleport==null && p.getPos().squaredDistanceTo(lastResolved)>0.25) {
            pendingTeleport=p.getPos();
            Vec3d target=pendingTeleport.subtract(origin);
            event("TELEPORT",target.x,target.y,target.z);
        }
        HostProjectiles.publish(p,bridge);
        HostPose pose=host==null ? null : HostPose.read(host.wire(),bridge.session());
        if (pose!=null) {
            Vec3d at=origin.add(pose.x(),pose.y(),pose.z());
            if (pendingTeleport!=null && at.squaredDistanceTo(pendingTeleport)<0.25) pendingTeleport=null;
            if (pendingTeleport==null) {
                p.refreshPositionAndAngles(at.x,at.y,at.z,pose.yaw(),pose.pitch());
                p.setPose(pose.crouch() ? EntityPose.CROUCHING : EntityPose.STANDING);
                p.setOnGround(pose.grounded()); p.setVelocity(Vec3d.ZERO); p.fallDistance=0;
                lastResolved=at;
            }
        }
        if (ticks%4!=0) return;
        mirrorTerrain(p);
        if (!linked(host)) {
            for (SlimeEntity e:targets.values()) e.discard(); targets.clear(); targetIds.clear();
        }
        List<String[]> rows=new ArrayList<>();
        rows.add(Bridge.row("CAPS","HOST_MOTION_1"));
        if (host!=null) rows.add(Bridge.row("HOST_SESSION",host.wire().session()));
        for (long key:new ArrayList<>(damagedStates.keySet())) {
            BlockPos pos=BlockPos.fromLong(key);
            if (linkedWorld.getBlockState(pos)!=damagedStates.get(key)) { damage.forget(key); damagedStates.remove(key); crackStages.remove(key); linkedWorld.setBlockBreakingInfo(crackId(pos),pos,-1); }
        }
        Vec3d position=p.getPos().subtract(origin);
        rows.add(Bridge.row("PLAYER",position.x,position.y,position.z,p.getYaw(),p.getPitch(),p.getHealth(),p.isCreative()?1:0,p.isAlive()?1:0,p.getArmor()));
        rows.add(Bridge.row("ORIGIN",origin.x,origin.y,origin.z));
        for (int slot=0;slot<9;slot++) {
            ItemStack stack=p.getInventory().getStack(slot);
            rows.add(Bridge.row("SLOT",slot,Registries.ITEM.getId(stack.getItem()),stack.getCount()));
        }
        // Export a bounded local volume. Minecraft owns all block placement, mining, TNT and pearl logic.
        BlockPos at=p.getBlockPos(); int blocks=0;
        for (BlockPos pos:BlockPos.iterate(at.add(-8,-5,-8),at.add(8,6,8))) {
            BlockState state=p.getWorld().getBlockState(pos);
            if (state.isOf(Blocks.BARRIER) || state.isAir() || state.getCollisionShape(p.getWorld(),pos).isEmpty()) continue;
            if (++blocks>4096) break;
            Vec3d relative=Vec3d.of(pos).subtract(origin);
            rows.add(Bridge.row("BLOCK",relative.x,relative.y,relative.z,Registries.BLOCK.getId(state.getBlock()),stage(p.getServerWorld(),pos,state)));
        }
        for (Entity entity:p.getServerWorld().iterateEntities()) {
            if (entity instanceof net.minecraft.entity.projectile.ProjectileEntity || entity instanceof TntEntity) {
                if (entity.squaredDistanceTo(p)>1024 || rows.size()>5000) continue;
                Vec3d e=entity.getPos().subtract(origin);
                rows.add(Bridge.row("ENTITY",entity.getId(),Registries.ENTITY_TYPE.getId(entity.getType()),e.x,e.y,e.z));
            }
        }
        rows.add(Bridge.row("HOST_ACK",lastHostEvent));
        rows.addAll(events.values());
        bridge.publish(++sequence,rows);
    }
    private static int stage(ServerWorld world,BlockPos pos,BlockState state) {
        long key=pos.asLong();
        BlockState old=damagedStates.get(key);
        if (old!=null && old!=state) { damage.forget(key); damagedStates.remove(key); crackStages.remove(key); world.setBlockBreakingInfo(crackId(pos),pos,-1); }
        return crackStages.getOrDefault(key,-1);
    }
    private static final Map<Long,Integer> crackStages=new HashMap<>();
    private static int crackId(BlockPos p) { return -1-Math.floorMod(p.hashCode(),1_000_000); }
    private static void applyHost(ServerPlayerEntity p,Wire.Frame frame) {
        Set<String> seen=new HashSet<>();
        for (String[] input:frame.records()) {
            try {
                String[] r=input;
                if (r.length>=3 && r[0].equals("EVENT")) {
                    long id=Long.parseLong(r[1]);
                    if (id<=lastHostEvent) continue;
                    if (r.length==8 && r[2].equals("BLOCK_HIT"))
                        r=Bridge.row("BLOCK_HIT",r[3],r[4],r[5],r[6],r[7],id,"native");
                    else if (r.length==4 && r[2].equals("PLAYER_FALL"))
                        r=Bridge.row("PLAYER_FALL",r[3]);
                    else if (r.length==7 && r[2].equals("PLAYER_HIT"))
                        r=Bridge.row("PLAYER_HIT",r[3],r[4],r[5],r[6]);
                    else if (r.length==11 && r[2].equals("PROJECTILE_HIT")) {
                        HostProjectiles.hit(p,r); lastHostEvent=id; continue;
                    } else continue;
                    lastHostEvent=id;
                }
                if (r.length==2 && r[0].equals("ACK")) {
                    long ack=Long.parseLong(r[1]); events.keySet().removeIf(id -> id<=ack);
                } else if (r.length==8 && r[0].equals("TARGET") && seen.size()<64) {
                    String id=r[1]; if (!id.matches("[A-Za-z0-9_-]{1,64}")) continue;
                    Vec3d relative=Bridge.point(r,2); if (origin.add(relative).squaredDistanceTo(p.getPos())>4096) continue;
                    Vec3d at=origin.add(relative); SlimeEntity entity=targets.get(id);
                    if (entity==null || entity.isRemoved()) {
                        if (entity!=null) targetIds.remove(entity.getUuid());
                        entity=EntityType.SLIME.create(p.getServerWorld()); if (entity==null) continue;
                        entity.setAiDisabled(true); entity.setNoGravity(true); entity.setSilent(true); entity.setInvisible(true); entity.setPersistent();
                        entity.addCommandTag("minemakers_target"); entity.setSize(1,true); p.getServerWorld().spawnEntity(entity);
                        targets.put(id,entity); targetIds.put(entity.getUuid(),id);
                    }
                    entity.refreshPositionAndAngles(at.x,at.y,at.z,0,0); seen.add(id);
                } else if (r.length==8 && r[0].equals("BLOCK_HIT")) {
                    BlockPos pos=BlockPos.ofFloored(origin.add(Bridge.point(r,1)));
                    if (pos.getSquaredDistance(p.getBlockPos())>1024) continue;
                    BlockState state=p.getWorld().getBlockState(pos); if (state.isAir()) continue;
                    float hardness=state.getHardness(p.getWorld(),pos);
                    boolean unbreakable=hardness<0 || state.isOf(Blocks.BEDROCK);
                    float hp=Math.max(20,hardness*40); float resistance=Math.max(1,state.getBlock().getBlastResistance()/3);
                    BlockDamage.Result result=damage.hit(pos.asLong(),(float)Wire.number(r[4]),hp,resistance,unbreakable);
                    damagedStates.put(pos.asLong(),state); crackStages.put(pos.asLong(),result.crackStage());
                    p.getServerWorld().setBlockBreakingInfo(crackId(pos),pos,result.crackStage());
                    if (result.destroyed()) { p.getWorld().breakBlock(pos,true); damagedStates.remove(pos.asLong()); crackStages.remove(pos.asLong()); }
                } else if (r.length==2 && r[0].equals("PLAYER_FALL")) {
                    float amount=(float)Math.clamp(Math.ceil(Wire.number(r[1])-3),0,100);
                    if (amount>0) p.damage(p.getDamageSources().fall(),amount);
                } else if (r.length==5 && r[0].equals("PLAYER_HIT")) {
                    float amount=(float)Math.clamp(Wire.number(r[1]),0,100);
                    p.damage(p.getDamageSources().generic(),amount);
                    Vec3d velocity=Bridge.point(r,2); if (velocity.lengthSquared()<400) event("PUSH",velocity.x,velocity.y,velocity.z);
                }
            } catch (IllegalArgumentException ignored) { /* malformed row cannot affect game state */ }
        }
        List<String> gone=targets.keySet().stream().filter(id -> !seen.contains(id)).toList();
        for (String id:gone) { SlimeEntity e=targets.remove(id); targetIds.remove(e.getUuid()); e.discard(); }
    }
    public static Vec3d origin() { return origin; }
    private static void mirrorTerrain(ServerPlayerEntity player) {
        Set<BlockPos> next=new HashSet<>();
        outer: for (Box box:colliders) {
            Box b=box.offset(origin);
            for (BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(b.minX,b.minY,b.minZ),BlockPos.ofFloored(b.maxX-0.001,b.maxY-0.001,b.maxZ-0.001))) {
                if (next.size()>=2048) break outer;
                if (pos.getSquaredDistance(player.getBlockPos())>1024) continue;
                BlockPos immutable=pos.toImmutable();
                BlockState state=linkedWorld.getBlockState(pos);
                if (state.isAir() || (state.isOf(Blocks.BARRIER) && terrain.contains(pos))) next.add(immutable);
            }
        }
        for (BlockPos pos:terrain) if (!next.contains(pos) && linkedWorld.getBlockState(pos).isOf(Blocks.BARRIER)) linkedWorld.setBlockState(pos,Blocks.AIR.getDefaultState());
        for (BlockPos pos:next) if (linkedWorld.getBlockState(pos).isAir()) linkedWorld.setBlockState(pos,Blocks.BARRIER.getDefaultState());
        terrain.clear(); terrain.addAll(next);
    }
    private static void unlink() {
        if (linkedWorld!=null) for (BlockPos pos:terrain) if (linkedWorld.getBlockState(pos).isOf(Blocks.BARRIER)) linkedWorld.setBlockState(pos,Blocks.AIR.getDefaultState());
        terrain.clear(); HostProjectiles.clear();
        if (bridge!=null) bridge.close(); bridge=null; playerId=null; colliders=List.of();
        for (SlimeEntity e:targets.values()) e.discard(); targets.clear(); targetIds.clear(); events.clear();
        if (linkedWorld!=null) for (long key:crackStages.keySet()) linkedWorld.setBlockBreakingInfo(crackId(BlockPos.fromLong(key)),BlockPos.fromLong(key),-1);
        damage.clear(); crackStages.clear(); damagedStates.clear(); linkedWorld=null;
        if (linkedPlayer!=null && !linkedPlayer.isRemoved() && returnWorld!=null && returnPosition!=null)
            linkedPlayer.teleport(returnWorld,returnPosition.x,returnPosition.y,returnPosition.z,returnYaw,returnPitch);
        linkedPlayer=null; returnWorld=null; returnPosition=null; lastResolved=null; pendingTeleport=null;
    }
}
