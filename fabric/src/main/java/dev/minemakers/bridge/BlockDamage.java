package dev.minemakers.bridge;

import java.util.HashMap;
import java.util.Map;

/** Accumulated host-weapon damage; a hit never erases a healthy block instantly. */
public final class BlockDamage {
    public record Result(float remaining, int crackStage, boolean destroyed) {}
    private final Map<Long, Float> remaining = new HashMap<>();
    public Result hit(long key, float incoming, float maximum, float resistance, boolean unbreakable) {
        if (!Float.isFinite(incoming) || incoming<=0 || !Float.isFinite(maximum) || maximum<=0 ||
            !Float.isFinite(resistance) || resistance<1) throw new IllegalArgumentException("Invalid damage");
        if (unbreakable) return new Result(maximum,-1,false);
        // Maximum of 35% total health per native hit; explosive weapons still need multiple hits.
        float damage=Math.min(incoming/resistance,maximum*0.35f);
        float health=Math.max(0,remaining.getOrDefault(key,maximum)-damage);
        if (health==0) remaining.remove(key); else remaining.put(key,health);
        return new Result(health,health==0 ? -1 : Math.min(9,(int)((1-health/maximum)*10)),health==0);
    }
    public void forget(long key) { remaining.remove(key); }
    public void clear() { remaining.clear(); }
}
