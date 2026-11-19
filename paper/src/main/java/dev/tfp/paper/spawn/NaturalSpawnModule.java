/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/entities/Fauna.java, Faunas.java and
 * src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java @ c3b4d9536
 */
package dev.tfp.paper.spawn;

import com.google.gson.JsonParser;
import dev.tfp.core.animal.AnimalSpawnRoll;
import dev.tfp.core.animal.Carrier;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.spawn.ChunkSpawns;
import dev.tfp.core.spawn.FaunaRule;
import dev.tfp.core.spawn.SpawnRandom;
import dev.tfp.core.spawn.SpawnRules;
import dev.tfp.core.spawn.SpawnSite;
import dev.tfp.core.spawn.SpawnTables;
import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.chunk.ChunkDataGenerator;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.animal.AnimalPdc;
import dev.tfp.paper.animal.AnimalService;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.data.RegistryService;
import dev.tfp.paper.hud.HudHemisphere;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Natural spawning of TFC animals by climate (PAR-FAUNA-04).
 *
 * <ul>
 *   <li><b>New chunks</b>: when a chunk is generated, {@link ChunkSpawns#forNewChunk} plans the groups of the biome's
 *       creature list (vanilla's chunk-generation spawn with TFC's lists), and each member passes TFC's gate
 *       ({@link SpawnRules#accepts}: chance, depth, climate window with fuzzy draw, months, brightness) with the climate of
 *       its own column.</li>
 *   <li><b>Top-up</b>: every {@value #TOP_UP_TICKS} ticks, for each player, one group in a loaded chunk 2 to 6 chunks
 *       away, skipped when that chunk already holds {@value #CHUNK_CAP} TFC animals (a documented divergence: TFC has the
 *       vanilla mob cap).</li>
 * </ul>
 * Planning is a few random draws and the gate a few comparisons on cached climate data, so both run on the chunk's or
 * player's own thread; the spawn itself goes through {@link AnimalService#spawn}. Only land animals of the {@code creature}
 * category are spawned here; fish and other water categories belong to PAR-FAUNA-11.
 */
public final class NaturalSpawnModule implements FeatureModule, Listener {
    static final long TOP_UP_TICKS = Long.getLong("tfp.spawn.topUpTicks", 600L);
    static final int CHUNK_CAP = Integer.getInteger("tfp.spawn.chunkCap", 8);
    static final boolean ENABLED = !Boolean.getBoolean("tfp.spawn.disabled");

    private ModuleContext ctx;
    private volatile SpawnTables tables;
    private final Map<UUID, Scheduler.TaskHandle> topUps = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return "spawn";
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        this.ctx = context;
        var file = RegistryService.jarDataRoot(NaturalSpawnModule.class.getClassLoader()).resolve("tfp/data_maps/spawn_table.json");
        tables = SpawnTables.parse(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        if (ENABLED) {
            context.listen(this);
        }
        context.logger().info("Spawn: " + tables.biomes().size() + " biome lists, " + tables.fauna().size() + " species gates"
                + (ENABLED ? "" : " (spawning disabled by -Dtfp.spawn.disabled)"));
    }

    @Override
    public void disable() {
        topUps.values().forEach(Scheduler.TaskHandle::cancel);
        topUps.clear();
    }

    private AnimalService animals() {
        return ((TfpPlugin) ctx.plugin()).animals();
    }

    // ---- new chunks --------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || event.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        Chunk chunk = event.getChunk();
        ctx.scheduler().region(chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> populate(chunk, true));
    }

    /** Plans and spawns one chunk's animals. Region thread of the chunk. */
    void populate(Chunk chunk, boolean newChunk) {
        AnimalService animals = animals();
        ClockSnapshot now = ctx.clock().get();
        if (animals == null || !now.isSet() || !chunk.isLoaded()) {
            return;
        }
        World world = chunk.getWorld();
        SpawnRandom rand = random();
        int bx = chunk.getX() << 4;
        int bz = chunk.getZ() << 4;
        Block centre = world.getBlockAt(bx + 8, world.getHighestBlockYAt(bx + 8, bz + 8), bz + 8);
        var biome = tables.biome(centre.getBiome().getKey().getKey());
        if (biome.isEmpty()) {
            return;
        }
        List<ChunkSpawns.Member> members = newChunk ? ChunkSpawns.forNewChunk(biome.get(), rand)
                : ChunkSpawns.topUp(biome.get(), rand);
        for (ChunkSpawns.Member m : members) {
            Carrier carrier = animals.table().find(m.entry().type()).orElse(null);
            FaunaRule rule = tables.fauna().get(m.entry().type());
            if (carrier == null || rule == null || isWater(carrier)) {
                continue;
            }
            int x = bx + m.x();
            int z = bz + m.z();
            Location at = standingPlace(world, x, z, carrier);
            if (at == null) {
                continue;
            }
            SpawnSite site = site(world, at, now);
            if (!SpawnRules.accepts(rule, site, rand)) {
                continue;
            }
            animals.spawn(at, carrier, AnimalSpawnRoll.AgeHint.RANDOM, null).exceptionally(e -> {
                ctx.logger().fine("Spawn of " + carrier.id() + " failed: " + e.getMessage());
                return null;
            });
        }
    }

    static boolean isWater(Carrier c) {
        return c.category().startsWith("WATER") || c.category().startsWith("UNDERGROUND_WATER");
    }

    /** The column's standing place (centre of the block above the highest solid ground), or null if a land animal cannot stand there. */
    static Location standingPlace(World world, int x, int z, Carrier carrier) {
        int y = world.getHighestBlockYAt(x, z);
        Block ground = world.getBlockAt(x, y, z);
        Material type = ground.getType();
        if (!type.isSolid() || ground.isLiquid() || isLeaves(type)) {
            return null;
        }
        int clear = (int) Math.ceil(carrier.height());
        for (int i = 1; i <= Math.max(1, clear); i++) {
            Block above = world.getBlockAt(x, y + i, z);
            if (!above.isPassable() || above.isLiquid()) {
                return null;
            }
        }
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private static boolean isLeaves(Material m) {
        return org.bukkit.Tag.LEAVES.isTagged(m);
    }

    /** The gate's inputs at {@code at}: climate and forest of the column's chunk, hemisphere, month and raw brightness. */
    SpawnSite site(World world, Location at, ClockSnapshot now) {
        long seed = world.getSeed();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        ChunkClimate climate = dev.tfp.paper.climate.ChunkClimateSource.climateAt(seed, x, z);
        int density = ChunkDataGenerator.forLevel(seed).forestType(x >> 4, z >> 4).density();
        boolean north = HudHemisphere.north(true, seed, z);
        Block feet = at.getBlock();
        int brightness = Math.max(feet.getLightFromBlocks(), feet.getLightFromSky());
        return new SpawnSite(at.getBlockY(), ChunkData.SEA_LEVEL_Y, climate, density, north, brightness,
                CalendarTicks.month(now.calendarTicks(), now.daysInMonth()));
    }

    private static SpawnRandom random() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new SpawnRandom() {
            @Override
            public int nextInt(int bound) {
                return r.nextInt(bound);
            }

            @Override
            public float nextFloat() {
                return r.nextFloat();
            }
        };
    }

    // ---- top-up ------------------------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        long delay = TOP_UP_TICKS + Math.floorMod(p.getUniqueId().hashCode(), 100);
        Scheduler.TaskHandle h = ctx.scheduler().entityRepeating(p, delay, TOP_UP_TICKS, () -> topUp(p), null);
        if (h != null) {
            Scheduler.TaskHandle old = topUps.put(p.getUniqueId(), h);
            if (old != null) {
                old.cancel();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Scheduler.TaskHandle h = topUps.remove(event.getPlayer().getUniqueId());
        if (h != null) {
            h.cancel();
        }
    }

    /** One top-up for {@code player}: a loaded chunk 2 to 6 chunks away, in a region this thread owns. Player thread. */
    private void topUp(Player player) {
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int cx = (player.getLocation().getBlockX() >> 4) + (r.nextBoolean() ? 1 : -1) * (2 + r.nextInt(5));
        int cz = (player.getLocation().getBlockZ() >> 4) + (r.nextBoolean() ? 1 : -1) * (2 + r.nextInt(5));
        ctx.scheduler().region(world, cx, cz, () -> {
            if (!world.isChunkLoaded(cx, cz)) {
                return;
            }
            Chunk chunk = world.getChunkAt(cx, cz);
            if (countAnimals(chunk) >= CHUNK_CAP) {
                return;
            }
            populate(chunk, false);
        });
    }

    static int countAnimals(Chunk chunk) {
        int n = 0;
        for (Entity e : chunk.getEntities()) {
            if (AnimalPdc.has(e.getPersistentDataContainer())) {
                n++;
            }
        }
        return n;
    }

    // ---- /tfp debug census [radius] ----------------------------------------------------------------------

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("census", node -> {
            node.executes(tree.run(ctx -> census(ctx.getSource().getSender(), 128)));
            node.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                    .<io.papermc.paper.command.brigadier.CommandSourceStack, Integer>argument("radius",
                            IntegerArgumentType.integer(16, 512))
                    .executes(tree.run(ctx -> census(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "radius")))));
        });
    }

    /** Counts TFC animals per species in the loaded chunks within {@code radius} blocks of the player, chunk by chunk on each region. */
    CompletableFuture<Reply> census(org.bukkit.command.CommandSender sender, int radius) {
        if (!(sender instanceof Player player)) {
            return CompletableFuture.completedFuture(Reply.error("debug.census.player_only"));
        }
        World world = player.getWorld();
        int pcx = player.getLocation().getBlockX() >> 4;
        int pcz = player.getLocation().getBlockZ() >> 4;
        int r = (radius + 15) >> 4;
        Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> jobs = new ArrayList<>();
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                CompletableFuture<Void> job = new CompletableFuture<>();
                final int fx = cx;
                final int fz = cz;
                ctx.scheduler().region(world, fx, fz, () -> {
                    try {
                        for (Entity e : world.getChunkAt(fx, fz).getEntities()) {
                            animals().record(e).ifPresent(rec -> counts.computeIfAbsent(rec.species(), k -> new AtomicInteger())
                                    .incrementAndGet());
                        }
                    } finally {
                        job.complete(null);
                    }
                });
                jobs.add(job);
            }
        }
        return CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).thenApply(v -> {
            Map<String, Integer> sorted = new TreeMap<>();
            counts.forEach((k, n) -> sorted.put(k, n.get()));
            int total = sorted.values().stream().mapToInt(Integer::intValue).sum();
            List<String> lines = new ArrayList<>();
            sorted.forEach((k, n) -> lines.add(k + ": " + n));
            return Reply.ok("debug.census.header", total, sorted.size(), radius).withDetails(lines);
        });
    }
}
