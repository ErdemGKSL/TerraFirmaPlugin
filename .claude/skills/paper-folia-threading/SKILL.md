---
name: paper-folia-threading
description: Threading and performance architecture rules for this Paper + Folia plugin. Covers the scheduler abstraction (region/entity/global/async), region ownership, the snapshot → async compute → budgeted commit pattern, lazy calendar-based simulation instead of ticking, async worldgen, async packet rewriting (chunk biome remap, item lore), off-thread persistence and thread-safe registries. Use this whenever writing or reviewing code that schedules work, touches blocks/entities/players, runs periodically, scans the world, handles packets, loads/saves data, or whenever performance, lag, TPS/MSPT, "main thread", async, Folia, regions or concurrency come up, even if the user doesn't say "threading".
---

# Threading on Paper + Folia

The goal is to keep the server/region thread nearly idle. Most TFC systems can be expressed as pure functions of time + immutable data. Those that can't are computed off-thread and committed in small, budgeted batches.

Folia shapes everything: there is **no single main thread**. Each region of loaded chunks ticks on its own thread, and code may only touch blocks/entities that the current thread owns. On plain Paper, every "region" call simply lands on the main thread. Write for Folia and Paper works for free.

`references/folia-api.md` has the exact scheduler APIs and the list of forbidden Bukkit calls. `references/patterns.md` has code sketches for each pattern below.

## The five rules

### 1. Never use `BukkitScheduler`, use our `Scheduler`
`paper/` exposes one interface; feature code never calls Bukkit/Folia schedulers directly:

```java
public interface Scheduler {
    void region(Location loc, Runnable task);            // owner of that block
    void region(World w, int chunkX, int chunkZ, Runnable task);
    void entity(Entity e, Runnable task, Runnable retired); // follows the entity across regions
    void global(Runnable task);                          // world-wide state (time, weather) — rare
    void async(Runnable task);                           // CPU pool, never touches Bukkit world state
    CompletableFuture<Void> io(Runnable task);           // virtual threads for disk/DB
}
```

The implementation uses Folia's region/entity/global/async schedulers. They exist in the Paper API too, so a single implementation serves both platforms. `entity(...)` needs the `retired` callback because the entity may be removed before the task runs.

### 2. Compute lazily from the calendar; don't tick
TFC already models most change as "value at time t" (food rot from `creationDate`, item heat from `lastTemperature` + `lastTick`). Generalize this:
- Store **start time + parameters**, never a counter you increment each tick.
- Evaluate on access (interaction, chunk load, GUI open, packet send for tooltips).
- On chunk load, catch up a device in a single computation, not by replaying ticks.

So: inventories are never ticked, unloaded chunks cost nothing, and a crop that grew for 3 in-game months is one formula.

### 3. Snapshot → async compute → budgeted commit
Use this for anything that must look at many blocks: collapse/landslide propagation, snow and ice accumulation, crop/plant random checks, mechanical power networks, fluid spreading, tree felling.
1. On the owning region thread, capture an immutable snapshot (`ChunkSnapshot`, or our compact copy of the needed sections/block-data).
2. Compute on the async pool. Pure functions only.
3. Produce a list of `BlockChange`s grouped by chunk, and hand each group to `scheduler.region(...)` for its chunk.
4. Each region applies changes under a **per-tick time budget** (start at ~1–2 ms/tick per region, configurable). Leftovers roll over to the next tick.
5. Validate before applying: if the block changed since the snapshot, drop or recompute. Never blindly overwrite.

On Folia a multi-chunk result may span several regions. That's why commits are split per chunk and never applied from one thread across region borders.

### 4. Thread-safe by construction
- Registries (recipes, heat, food, climate, tags) are **immutable snapshots** behind an `AtomicReference`. Reload builds a new one off-thread and swaps it.
- The calendar is a global clock read through a volatile/atomic. Writes happen only on the global scheduler.
- Per-chunk data belongs to its region thread. Async workers only see copies.
- Per-player data lives in our player store. Mutate it on the player's entity scheduler.
- Prefer `fastutil` primitive collections, Caffeine for caches with bounded size, and JCTools MPSC queues for cross-thread hand-off.
- No `synchronized` on hot paths. If you need a lock, the design probably needs another snapshot.

### 5. Packets: async, allocation-light, precomputed
Packet rewriting runs on netty event-loop threads. It must never call Bukkit world APIs and must stay cheap per packet:
- **Seasonal biome colours:** precompute `int[] remap[season][biomeId]` when the season changes. In the chunk data packet, rewrite only the biome palette entries. Don't decode and re-encode whole sections if the palette rewrite suffices.
- **Item lore (heat, food expiry, size/weight):** compute from the item's PDC timestamps against the calendar clock and build the Adventure component off-thread. Cache by `(itemTypeId, bucketedValue)` where possible. The server-side stack is never modified.
- Never block a netty thread on I/O or on a region-thread future.

## Worldgen
Bukkit `ChunkGenerator`/`BiomeProvider` methods are invoked on worldgen worker threads in parallel. Our `worldgen/` module is platform-free and deterministic from the seed. Caches must be concurrent (TFC's `FastConcurrentCache` is the model). Per-chunk climate/rock data is recomputed from the seed on demand (bounded cache), not persisted, unless gameplay has modified it. Placing CraftEngine blocks during generation: see the `craftengine` skill.

## Persistence
- Per-chunk block data: on chunk load, copy the raw `byte[]` out of the chunk PDC on the region thread, deserialize async, then publish to the region. On save, serialize the region-owned state to bytes on the region thread (cheap copy), and compress/write async.
- Player data: load async at `AsyncPlayerPreLoginEvent`, save async on quit/interval.
- Disk/DB I/O goes on virtual threads (`scheduler.io`), never the async CPU pool.

## Entities (the unavoidable main-thread cost)
Vanilla pathfinding and our Mob Goals run on the entity's region thread. Keep goals O(1) per tick, throttle expensive checks (e.g. evaluate "is it time to eat" every 20–40 ticks, staggered by entity id), and keep animal state (age, familiarity, pregnancy) lazy and calendar-based like everything else.

## Review checklist
- [ ] No `Bukkit.getScheduler()`, no `BukkitRunnable`.
- [ ] Every block/entity access happens on the owner thread. Assert it in debug builds with `Bukkit.isOwnedByCurrentRegion(...)`.
- [ ] No per-tick loops over all loaded blocks/items/players for time-based state.
- [ ] Async code touches only snapshots/immutable data.
- [ ] Region commits are budgeted and re-validated.
- [ ] Packet handlers do no world access, no I/O, minimal allocation.
- [ ] Reloadable data is swapped atomically.
- [ ] No ambient particles. Particles only on explicit actions.
