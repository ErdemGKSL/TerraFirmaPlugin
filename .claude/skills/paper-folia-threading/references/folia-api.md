# Folia / Paper scheduler API (exact signatures)

Copied from `paper-api/src/main/java/io/papermc/paper/threadedregions/scheduler/*.java`. These classes are in the Paper API from 1.21.x through 26.x and work on plain Paper ("they will be internally handled"). Full context and sources: `tooling-report.md` §4.

```java
// Bukkit.getRegionScheduler(), Bukkit.getAsyncScheduler(), Bukkit.getGlobalRegionScheduler(), entity.getScheduler()

interface RegionScheduler {
  void execute(Plugin plugin, World world, int chunkX, int chunkZ, Runnable run);
  default void execute(Plugin plugin, Location location, Runnable run);
  ScheduledTask run(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task);
  ScheduledTask runDelayed(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task, long delayTicks);
  ScheduledTask runAtFixedRate(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task,
                               long initialDelayTicks, long periodTicks);
  // + Location overloads
}

interface EntityScheduler {   // follows the entity across regions
  boolean execute(Plugin plugin, Runnable run, @Nullable Runnable retired, long delay);
  @Nullable ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired);
  @Nullable ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired, long delayTicks);
  @Nullable ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, @Nullable Runnable retired,
                                         long initialDelayTicks, long periodTicks);
}  // null/false => entity already removed; `retired` runs if it is removed before execution
   // (retired callbacks must not load chunks or remove entities)

interface GlobalRegionScheduler {   // game rules, world time, weather, console commands
  void execute(Plugin plugin, Runnable run);
  ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task);
  ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks);
  ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks);
  void cancelTasks(Plugin plugin);
}

interface AsyncScheduler {   // wall-clock time, not ticks
  ScheduledTask runNow(Plugin plugin, Consumer<ScheduledTask> task);
  ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delay, TimeUnit unit);
  ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelay, long period, TimeUnit unit);
  void cancelTasks(Plugin plugin);
}
```

Ownership checks (`Bukkit.*`): `isOwnedByCurrentRegion(World, Position[, radiusChunks])`, `(Location[, radius])`, `(Block)`, `(World, chunkX, chunkZ[, radius])`, `(World, minCX, minCZ, maxCX, maxCZ)`, `(Entity)`, `isGlobalTickThread()`. On Paper, `Bukkit.isPrimaryThread()` means "a tick thread". Use `isOwnedByCurrentRegion` to answer "the *right* tick thread".

Folia detection, only when behaviour must differ:
```java
static final boolean FOLIA = ServerBuildInfo.buildInfo().isBrandCompatible(Key.key("papermc", "folia"));
```

## Forbidden or broken on Folia
- `Bukkit.getScheduler()` / `BukkitRunnable`: throws.
- **Scoreboard API:** throws. Never use scoreboards for HUD or teams.
- `createWorld` / `unloadWorld`: throws. **TFC worlds must be the server's configured default worlds.** Set the generator via `bukkit.yml` `worlds.<name>.generator` / `getDefaultWorldGenerator`, with `load: STARTUP` in `paper-plugin.yml`.
- `Entity#teleport`: throws. Use `teleportAsync`.
- `dispatchCommand` off-region: throws.
- Portal/respawn and some login API are broken.

## Thread contexts
- Events about an entity fire on its owning region. Events acting on a *target* entity fire on the target's region.
- Player commands run on the player's region. Console commands run on the global region.
- Chunk generator, biome provider and block populator methods: arbitrary worldgen worker threads, must be fully thread-safe. Never touch `World`/`Chunk` APIs there.
- Outgoing packet handlers (Netty/PacketEvents): the connection's Netty event-loop thread. Never call Bukkit world/entity APIs there.

## Platform status (2026-10-16): plan around it
- Paper: 26.2 and 26.3 supported (Java 25). 1.21.11 stable but out of upstream support.
- Folia: 26.3 **alpha**, 26.2 beta (deprecated), 1.21.11 stable (unsupported). Folia has no 1.21.9/1.21.10 builds.
- So test Folia on 26.3 alpha *and* 1.21.11, and treat Folia 26.x problems as possibly upstream bugs. Note them in the ledger.
