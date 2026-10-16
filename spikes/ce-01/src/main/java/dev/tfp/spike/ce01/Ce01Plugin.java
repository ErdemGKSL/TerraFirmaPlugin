package dev.tfp.spike.ce01;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/** M0-CE-01: are CE blocks written by our Bukkit ChunkGenerator / BlockPopulator real CE blocks? */
public final class Ce01Plugin extends JavaPlugin implements Listener {
    static final Key MARKER = Key.of("tfpspike", "marker");
    static final Key MARKER_BE = Key.of("tfpspike", "marker_be");
    /** Far ring: generated in the first boot with the final config, unloaded, re-checked after restarts. */
    static final int FAR_CX = 250, FAR_CZ = 250, FAR_R = 2, SPAWN_R = 3;

    private final List<String> report = new ArrayList<>();
    private final AtomicInteger farUnloads = new AtomicInteger();
    private volatile boolean resolvedAtEnable;

    @Override
    public void onLoad() {
        installPack();
        MarkerBeBehavior.register();
    }

    @Override
    public void onEnable() {
        resolvedAtEnable = resolve();
        getServer().getPluginManager().registerEvents(this, this);
        log("onEnable: markers resolvable=" + resolvedAtEnable + " thread=" + Thread.currentThread().getName());
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new SpikeGenerator();
    }

    @EventHandler
    public void onCeReload(CraftEngineReloadEvent event) {
        boolean ok = resolve();
        log("CraftEngineReloadEvent first=" + event.isFirstReload() + " resolved=" + ok
                + " thread=" + Thread.currentThread().getName());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        if (Math.abs(cx - FAR_CX) <= FAR_R && Math.abs(cz - FAR_CZ) <= FAR_R) {
            farUnloads.incrementAndGet();
        }
    }

    private boolean resolve() {
        BlockDefinition m = CraftEngineBlocks.byId(MARKER), mb = CraftEngineBlocks.byId(MARKER_BE);
        if (m == null || mb == null) {
            return false;
        }
        SpikeGenerator.marker = CraftEngineBlocks.getBukkitBlockData(m.defaultState());
        SpikeGenerator.markerBe = CraftEngineBlocks.getBukkitBlockData(mb.defaultState());
        return true;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        World world = Bukkit.getWorlds().getFirst();
        Bukkit.getAsyncScheduler().runDelayed(this, t -> runChecks(world), 5, TimeUnit.SECONDS);
    }

    private void runChecks(World world) {
        try {
            log("ce.delay-configuration-load=" + ceConfig("delay-configuration-load"));
            log("ce.cache-system=" + ceConfig("cache-system"));
            log("generator: noiseWrites=" + SpikeGenerator.noiseWrites + " noiseWithoutCe=" + SpikeGenerator.noiseWithoutCe
                    + " popWrites=" + SpikeGenerator.popWrites + " popNeighbourWrites=" + SpikeGenerator.popNeighbourWrites
                    + " popNeighbourOutside=" + SpikeGenerator.popNeighbourOutside + " limitedRegionBuffer=" + SpikeGenerator.buffer);
            log("generator threads: noise=" + SpikeGenerator.NOISE_THREADS + " populate=" + SpikeGenerator.POP_THREADS);
            log("spawn: " + checkArea(world, 0, 0, SPAWN_R).summary());
            log("far(1st load this boot): " + checkArea(world, FAR_CX, FAR_CZ, FAR_R).summary());
            // Let the far chunks unload and leave CE's 30 s cache, then load them again.
            Thread.sleep(70_000);
            log("far unload events so far=" + farUnloads.get());
            log("far(reload this boot): " + checkArea(world, FAR_CX, FAR_CZ, FAR_R).summary());
            log("generator(end): noiseWrites=" + SpikeGenerator.noiseWrites + " noiseWithoutCe=" + SpikeGenerator.noiseWithoutCe
                    + " popWrites=" + SpikeGenerator.popWrites + " popNeighbourWrites=" + SpikeGenerator.popNeighbourWrites
                    + " popNeighbourOutside=" + SpikeGenerator.popNeighbourOutside + " limitedRegionBuffer=" + SpikeGenerator.buffer
                    + " threads noise=" + SpikeGenerator.NOISE_THREADS + " populate=" + SpikeGenerator.POP_THREADS);
            log("BE controller threads: constructed=" + MarkerBeBehavior.CONSTRUCTED_ON + " saved="
                    + MarkerBeBehavior.SAVED_ON + " onLoad=" + MarkerBeBehavior.ONLOAD_ON);
        } catch (Throwable t) {
            log("check failed: " + t);
            getSLF4JLogger().error("check failed", t);
        }
        writeReport();
    }

    /** Checks every chunk of a square on its owning region thread. */
    private Tally checkArea(World world, int ccx, int ccz, int r) {
        Tally tally = new Tally();
        List<CompletableFuture<Void>> all = new ArrayList<>();
        for (int cx = ccx - r; cx <= ccx + r; cx++) {
            for (int cz = ccz - r; cz <= ccz + r; cz++) {
                final int fx = cx, fz = cz;
                final boolean inner = cx > ccx - r; // west neighbour also in the area
                CompletableFuture<Void> f = new CompletableFuture<>();
                world.getChunkAtAsync(fx, fz, true).thenAccept(chunk ->
                        Bukkit.getRegionScheduler().run(this, world, fx, fz, task -> {
                            try {
                                checkChunk(world, fx, fz, inner, tally);
                                f.complete(null);
                            } catch (Throwable t) {
                                f.completeExceptionally(t);
                            }
                        }));
                all.add(f);
            }
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).orTimeout(120, TimeUnit.SECONDS).join();
        return tally;
    }

    private void checkChunk(World world, int cx, int cz, boolean inner, Tally tally) {
        if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz)) {
            tally.add("threadViolation");
        }
        CEWorld ce = BukkitAdaptor.adapt(world).storageWorld();
        int bx = cx * 16, bz = cz * 16, y = SpikeGenerator.Y;
        tally.add("noise.marker." + classify(world, ce, bx + 8, y, bz + 8, MARKER));
        tally.add("noise.marker_be." + classify(world, ce, bx + 4, y, bz + 4, MARKER_BE));
        tally.add("pop.own." + classify(world, ce, bx + 12, y, bz + 12, MARKER));
        if (inner) {
            tally.add("pop.neighbour." + classify(world, ce, bx + 2, y, bz + 2, MARKER));
        }
        BlockEntity be = ce.getBlockEntityAtIfLoaded(new BlockPos(bx + 4, y, bz + 4), false);
        if (be == null) {
            tally.add("be.missing");
        } else if (be.controller instanceof MarkerBeBehavior.MarkerController mc) {
            tally.add("be.ok");
            tally.add("be.createdOn=" + MarkerBeBehavior.threadKind(String.valueOf(mc.createdOn)));
            tally.add("be.loads=" + Math.min(mc.loads, 9));
        } else {
            tally.add("be.otherController=" + be.controller.getClass().getSimpleName());
        }
    }

    private static String classify(World world, CEWorld ce, int x, int y, int z, Key expected) {
        Block b = world.getBlockAt(x, y, z);
        ImmutableBlockState live = CraftEngineBlocks.getCustomBlockState(b);
        ImmutableBlockState stored = ce.getBlockStateAtIfLoaded(x, y, z);
        boolean liveOk = live != null && live.owner().value().id().equals(expected);
        boolean storedOk = stored != null && !stored.isEmpty() && stored.owner().value().id().equals(expected);
        if (liveOk && storedOk) {
            return "CUSTOM";
        }
        if (liveOk) {
            return "LIVE_ONLY";
        }
        if (storedOk) {
            return "STORAGE_ONLY";
        }
        return "VANILLA(" + b.getType().getKey().getKey() + ")";
    }

    private String ceConfig(String key) {
        try {
            for (String line : Files.readAllLines(Path.of("plugins/CraftEngine/config.yml"))) {
                if (line.trim().startsWith(key + ":")) {
                    return line.trim().substring(key.length() + 1).trim();
                }
            }
        } catch (IOException ignored) {
        }
        return "?";
    }

    private void installPack() {
        Path root = getDataFolder().toPath().getParent().resolve("CraftEngine/resources/tfpspike");
        try {
            for (String f : List.of("pack.yml", "configuration/blocks.yml")) {
                Path out = root.resolve(f);
                Files.createDirectories(out.getParent());
                try (InputStream in = getResource("pack/" + f)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot install spike pack", e);
        }
    }

    private void log(String line) {
        synchronized (report) {
            report.add(line);
        }
        getLogger().info("[ce01] " + line);
    }

    private void writeReport() {
        try {
            List<String> out = new ArrayList<>();
            out.add("=== boot " + java.time.Instant.now() + " " + Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
            synchronized (report) {
                out.addAll(report);
            }
            Files.write(Path.of("ce01-report.txt"), out, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            getLogger().severe("cannot write report " + e);
        }
    }

    static final class Tally {
        final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

        void add(String key) {
            counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        }

        String summary() {
            return new TreeMap<>(counts).toString();
        }
    }
}
