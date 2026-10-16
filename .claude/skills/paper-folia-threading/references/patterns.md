# Threading patterns (code sketches)

These are design sketches, not final code. Adapt the names to the real modules once they exist.

## Contents
1. Lazy calendar value
2. Snapshot → async → budgeted commit
3. Budgeted region commit queue
4. Atomic registry reload
5. Chunk block-data load/save
6. Packet rewrite: seasonal biome remap
7. Packet rewrite: item lore
8. Throttled, staggered entity checks

## 1. Lazy calendar value

```java
// core/: pure, immutable, thread-safe
public record TimedGrowth(long startTick, float ratePerTick, float max) {
    public float valueAt(long nowTick) {
        return Math.min(max, (nowTick - startTick) * ratePerTick);
    }
}
```

The same idea as TFC's `HeatComponent` (`lastTemperature` + `lastTick`) and `FoodComponent` (`creationDate`). Persist the record; never persist the evaluated value.

## 2. Snapshot → async → budgeted commit

```java
// On the region thread that owns `origin` (e.g. inside BlockBreakEvent):
CollapseSnapshot snap = CollapseSnapshot.capture(origin, radius);   // copies only what's needed
scheduler.async(() -> {
    List<BlockChange> changes = CollapseRules.compute(snap, registries.get()); // pure
    for (var group : BlockChange.groupByChunk(changes)) {
        scheduler.region(origin.getWorld(), group.chunkX(), group.chunkZ(),
            () -> commitQueue.enqueue(group));                        // applied under budget
    }
});
```

Snapshots must be small: copy the block ids/states in the affected box, plus the relevant per-chunk block data. A multi-chunk snapshot must be captured on a thread that owns all of those chunks. If it doesn't (on Folia), capture one snapshot per chunk on each owner and join them with `CompletableFuture.allOf` before computing.

## 3. Budgeted region commit queue

```java
final class RegionCommitQueue {
    private final Queue<BlockChange> pending = new ArrayDeque<>();   // region-thread only
    private final long budgetNanos;

    void enqueue(ChunkChanges group) { pending.addAll(group.changes()); }

    /** Called once per tick on the owning region thread. */
    void drain() {
        long deadline = System.nanoTime() + budgetNanos;
        BlockChange c;
        while (System.nanoTime() < deadline && (c = pending.poll()) != null) {
            if (c.stillValid()) c.apply();   // re-check the expected previous state
        }
    }
}
```

One queue per region. Drive `drain()` from a repeating region task per active chunk group, or from a per-world global tick that fans out per chunk on Folia.

## 4. Atomic registry reload

```java
public final class Registries {
    private static final AtomicReference<RegistrySnapshot> CURRENT = new AtomicReference<>(RegistrySnapshot.EMPTY);
    public static RegistrySnapshot get() { return CURRENT.get(); }

    public static CompletableFuture<Void> reload(Path dataRoot, Executor io) {
        return CompletableFuture.supplyAsync(() -> RegistryLoader.load(dataRoot), io) // parse + validate
            .thenAccept(CURRENT::set);                                                 // atomic swap
    }
}
```

Callers read `Registries.get()` once per operation and keep using that snapshot, so a reload mid-operation can't produce mixed data.

## 5. Chunk block-data load/save

```java
@EventHandler
void onChunkLoad(ChunkLoadEvent e) {                       // region thread
    byte[] raw = e.getChunk().getPersistentDataContainer().get(KEY, PersistentDataType.BYTE_ARRAY);
    if (raw == null) return;
    long key = e.getChunk().getChunkKey();
    scheduler.async(() -> {
        ChunkBlockData data = ChunkBlockDataCodec.decode(raw);   // off-thread
        scheduler.region(e.getWorld(), e.getChunk().getX(), e.getChunk().getZ(),
            () -> store.publish(key, data));                     // back on owner
    });
}
```

Gameplay that touches the chunk before `publish` must handle the "not loaded yet" state, e.g. by queueing the interaction or treating the data as default. Write a test for this race.

## 6. Packet rewrite: seasonal biome remap

```java
// Recomputed on the global scheduler when the season/month changes:
volatile int[] biomeRemap; // index: server biome id → client biome id for current season

// Netty thread, outgoing chunk packet:
void rewrite(ChunkPacket p) {
    int[] map = biomeRemap;                      // single volatile read
    for (BiomePalette pal : p.biomePalettes()) { // only palettes, not every cell
        pal.remapInPlace(map);
    }
}
```

Biome ids must be stable registry ids of our datapack biomes (one variant per base biome × season colour band). Also re-send chunks near players when the season changes, rate-limited and spread over time, so colours update without a spike.

## 7. Packet rewrite: item lore

```java
// Netty thread, outgoing set-slot / window-items / entity-equipment packets:
ItemStack view = stack.clone();                   // never mutate the server stack
long now = calendar.ticks();                      // volatile read
TooltipLines lines = TooltipCache.get(view, now); // computed from PDC timestamps, cached in buckets
view.lore(lines.components());
```

Bucket times (e.g. temperature to the nearest 10°C, expiry to the nearest in-game hour) so the cache hits and so you don't spam packets when nothing visible changed. Creative-mode inventories echo items back to the server, so strip our client-only lore on the way in.

## 8. Throttled, staggered entity checks

```java
// Inside a Mob Goal running on the entity's region thread
@Override public void tick() {
    if ((serverTick + entityId) % 40 != 0) return;   // every 2 s, spread across entities
    // cheap decision using lazily-evaluated animal state
}
```
