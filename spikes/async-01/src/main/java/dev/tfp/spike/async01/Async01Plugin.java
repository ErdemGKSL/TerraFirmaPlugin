package dev.tfp.spike.async01;

import dev.tfp.spike.async01.Pipeline.BlockChange;
import dev.tfp.spike.async01.Pipeline.Pos;
import dev.tfp.spike.async01.Pipeline.Snapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Piston;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-ASYNC-01 toy: "connected wool". A toy block's colour = number of toy neighbours (0..6).
 * Placing/removing a toy block changes the colour of itself and its neighbours: decided async from a
 * snapshot, applied on the owning region under a budget. Run with -Dtfp.async01.auto=true for the
 * scripted stress scenario; the report goes to async01-report.txt.
 */
public final class Async01Plugin extends JavaPlugin implements Listener {
    static final Material[] COLORS = {Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL,
            Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL};
    static final Set<Material> TOY = Set.of(COLORS);
    static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST};
    /** Cluster centres far apart, so on Folia they are different regions. Each straddles chunk borders. */
    static final int[][] CLUSTERS = {{0, 0}, {4000, 0}, {0, 4000}, {-4000, -4000}};
    static final int Y = 200;

    volatile boolean slowDecide;
    private Pipeline pipeline;
    private final Set<Pos> tracked = ConcurrentHashMap.newKeySet();
    private final List<String> report = new ArrayList<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onLoad(ServerLoadEvent e) {
        World world = Bukkit.getWorlds().getFirst();
        pipeline = new Pipeline(this, world, this::decide,
                Long.getLong("tfp.async01.budgetMicros", 1500) * 1000L);
        if (Boolean.getBoolean("tfp.async01.auto")) {
            Thread t = new Thread(() -> scenario(world), "tfp-async01-scenario");
            t.setDaemon(true);
            t.start();
        }
    }

    // ---------------------------------------------------------------- decide (pure)

    List<BlockChange> decide(Snapshot s) {
        if (slowDecide) {
            try {
                Thread.sleep(40); // widen the decide->apply window for the race test
            } catch (InterruptedException ignored) {
            }
        }
        List<BlockChange> out = new ArrayList<>();
        List<Pos> eval = new ArrayList<>();
        eval.add(s.center());
        for (BlockFace f : FACES) {
            eval.add(s.center().off(f.getModX(), f.getModY(), f.getModZ()));
        }
        for (Pos p : eval) {
            Material cur = s.blocks().get(p);
            if (cur == null || !TOY.contains(cur)) {
                continue;
            }
            int n = 0;
            boolean unknown = false;
            for (BlockFace f : FACES) {
                Material m = s.blocks().get(p.off(f.getModX(), f.getModY(), f.getModZ()));
                if (m == null) {
                    unknown = true;
                } else if (TOY.contains(m)) {
                    n++;
                }
            }
            if (!unknown && COLORS[n] != cur) {
                out.add(new BlockChange(p, cur, COLORS[n], s.submittedNanos()));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- event wiring (thin)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        afterRemoval(e.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        afterRemoval(e.blockList());
    }

    private void afterRemoval(List<Block> blocks) {
        for (Block b : blocks) {
            if (TOY.contains(b.getType())) {
                // the block is removed after the event: re-evaluate its neighbours next tick
                Pos p = new Pos(b.getX(), b.getY(), b.getZ());
                Bukkit.getRegionScheduler().runDelayed(this, b.getLocation(), t -> {
                    for (BlockFace f : FACES) {
                        submitIfOwned(p.off(f.getModX(), f.getModY(), f.getModZ()));
                    }
                }, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) {
        BlockFace d = e.getDirection();
        List<Pos> touched = new ArrayList<>();
        for (Block b : e.getBlocks()) {
            Pos p = new Pos(b.getX(), b.getY(), b.getZ());
            touched.add(p);
            touched.add(p.off(d.getModX(), d.getModY(), d.getModZ()));
        }
        // blocks move during the next ticks (moving piston); evaluate once they settled
        Bukkit.getRegionScheduler().runDelayed(this, e.getBlock().getLocation(), t -> {
            for (Pos p : touched) {
                tracked.add(p);
                submitIfOwned(p);
            }
        }, 4);
        pistonEvents++;
    }

    volatile int pistonEvents;

    /** Submissions must come from the owner thread; route if we are not it. */
    void submitIfOwned(Pos p) {
        World w = pipeline.world;
        if (Bukkit.isOwnedByCurrentRegion(w, p.cx(), p.cz())) {
            pipeline.submit(p);
        } else {
            Bukkit.getRegionScheduler().run(this, w, p.cx(), p.cz(), t -> pipeline.submit(p));
        }
    }

    // ---------------------------------------------------------------- scenario

    private void scenario(World world) {
        try {
            int n = Integer.getInteger("tfp.async01.blocks", 10_000);
            log("platform=" + Bukkit.getName() + " " + Bukkit.getMinecraftVersion() + " budget="
                    + pipeline.budgetNanos / 1000 + "us workers=" + (Runtime.getRuntime().availableProcessors() - 1));
            loadClusters(world);

            // 1. bulk placement: n toy blocks (white), each placement submits like a place event would
            Random r = new Random(42);
            Map<Long, List<Pos>> byChunk = new HashMap<>();
            for (int i = 0; i < n; i++) {
                int[] c = CLUSTERS[i % CLUSTERS.length];
                Pos p = new Pos(c[0] - 24 + r.nextInt(48), Y + r.nextInt(6), c[1] - 24 + r.nextInt(48));
                byChunk.computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
            }
            long t0 = System.nanoTime();
            onOwners(world, byChunk, (p) -> {
                world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.WHITE_WOOL, false);
                tracked.add(p);
                pipeline.submit(p);
            });
            awaitIdle("placement of " + n, t0);
            log("verify after placement: " + verify(world));

            // 2. explosions: 3 per cluster
            pipeline.stats.resetLatency();
            t0 = System.nanoTime();
            List<CompletableFuture<Void>> ex = new ArrayList<>();
            for (int[] c : CLUSTERS) {
                for (int k = 0; k < 3; k++) {
                    Location l = new Location(world, c[0] - 16 + k * 16, Y + 3, c[1]);
                    CompletableFuture<Void> f = new CompletableFuture<>();
                    Bukkit.getRegionScheduler().run(this, l, t -> {
                        world.createExplosion(l, 6f, false, true);
                        f.complete(null);
                    });
                    ex.add(f);
                }
            }
            CompletableFuture.allOf(ex.toArray(CompletableFuture[]::new)).join();
            Thread.sleep(500);
            awaitIdle("explosions", t0);
            log("verify after explosions: " + verify(world));

            // 3. piston chains across chunk borders: piston at x=15 (local) pushing 3 toy blocks east
            pipeline.stats.resetLatency();
            t0 = System.nanoTime();
            List<CompletableFuture<Void>> ps = new ArrayList<>();
            for (int[] c : CLUSTERS) {
                for (int k = 0; k < 5; k++) {
                    int bx = ((c[0] >> 4) << 4) + 12, bz = c[1] + 30 + k * 2, y = Y + 10;
                    CompletableFuture<Void> f = new CompletableFuture<>();
                    Bukkit.getRegionScheduler().run(this, new Location(world, bx, y, bz), t -> {
                        Block piston = world.getBlockAt(bx, y, bz);
                        Piston data = (Piston) Material.PISTON.createBlockData();
                        data.setFacing(BlockFace.EAST);
                        piston.setBlockData(data, false);
                        for (int i = 1; i <= 3; i++) {
                            world.getBlockAt(bx + i, y, bz).setType(Material.WHITE_WOOL, false);
                            tracked.add(new Pos(bx + i, y, bz));
                            pipeline.submit(new Pos(bx + i, y, bz));
                        }
                        f.complete(null);
                    });
                    ps.add(f);
                }
            }
            CompletableFuture.allOf(ps.toArray(CompletableFuture[]::new)).join();
            awaitIdle("piston setup", t0);
            ps.clear();
            for (int[] c : CLUSTERS) {
                for (int k = 0; k < 5; k++) {
                    int bx = ((c[0] >> 4) << 4) + 12, bz = c[1] + 30 + k * 2, y = Y + 10;
                    CompletableFuture<Void> f = new CompletableFuture<>();
                    Bukkit.getRegionScheduler().run(this, new Location(world, bx, y, bz), t -> {
                        world.getBlockAt(bx - 1, y, bz).setType(Material.REDSTONE_BLOCK, true); // power it
                        f.complete(null);
                    });
                    ps.add(f);
                }
            }
            CompletableFuture.allOf(ps.toArray(CompletableFuture[]::new)).join();
            Thread.sleep(2000);
            awaitIdle("pistons (events=" + pistonEvents + ", moved=" + countMoved(world) + ")", t0);
            log("verify after pistons: " + verify(world));

            // 4. race: slow decisions, and the world changes between decide and apply
            pipeline.stats.resetLatency();
            long staleBefore = pipeline.stats.stale.get();
            slowDecide = true;
            Map<Long, List<Pos>> race = new HashMap<>();
            for (int i = 0; i < 400; i++) {
                int[] c = CLUSTERS[i % CLUSTERS.length];
                Pos p = new Pos(c[0] - 6 + r.nextInt(12), Y + 20 + r.nextInt(2), c[1] - 6 + r.nextInt(12));
                race.computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
            }
            t0 = System.nanoTime();
            onOwners(world, race, p -> {
                world.getBlockAt(p.x(), p.y(), p.z()).setType(Material.WHITE_WOOL, false);
                tracked.add(p);
                pipeline.submit(p);
            });
            Thread.sleep(10); // decisions are now sleeping in the workers
            onOwners(world, race, p -> {
                if ((p.x() + p.z()) % 3 == 0) { // flip a third of them behind the pipeline's back
                    Block b = world.getBlockAt(p.x(), p.y(), p.z());
                    b.setType(TOY.contains(b.getType()) ? Material.STONE : Material.WHITE_WOOL, false);
                    submitIfOwned(p); // a real place/break event re-submits the block and its neighbours
                    for (BlockFace f : FACES) {
                        submitIfOwned(p.off(f.getModX(), f.getModY(), f.getModZ()));
                    }
                }
            });
            awaitIdle("race", t0);
            slowDecide = false;
            log("race: stale detected=" + (pipeline.stats.stale.get() - staleBefore));
            log("verify after race: " + verify(world));
            log("TOTAL " + pipeline.stats.summary());
        } catch (Throwable t) {
            log("scenario failed: " + t);
            getSLF4JLogger().error("scenario failed", t);
        }
        try {
            Files.write(Path.of("async01-report.txt"), report);
        } catch (IOException ignored) {
        }
    }

    /** Pistons that pushed their 3 blocks one step east (wool now at local +2..+4). */
    private int countMoved(World world) {
        java.util.concurrent.atomic.AtomicInteger moved = new java.util.concurrent.atomic.AtomicInteger();
        List<CompletableFuture<Void>> fs = new ArrayList<>();
        for (int[] c : CLUSTERS) {
            for (int k = 0; k < 5; k++) {
                int bx = ((c[0] >> 4) << 4) + 12, bz = c[1] + 30 + k * 2, y = Y + 10;
                CompletableFuture<Void> f = new CompletableFuture<>();
                Bukkit.getRegionScheduler().run(this, new Location(world, bx + 4, y, bz), t -> {
                    if (TOY.contains(world.getBlockAt(bx + 4, y, bz).getType())) {
                        moved.incrementAndGet();
                    }
                    f.complete(null);
                });
                fs.add(f);
            }
        }
        CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).join();
        return moved.get();
    }

    private void loadClusters(World world) {
        List<CompletableFuture<?>> fs = new ArrayList<>();
        for (int[] c : CLUSTERS) {
            for (int cx = (c[0] - 40) >> 4; cx <= (c[0] + 60) >> 4; cx++) {
                for (int cz = (c[1] - 40) >> 4; cz <= (c[1] + 60) >> 4; cz++) {
                    final int fx = cx, fz = cz;
                    CompletableFuture<Void> f = new CompletableFuture<>();
                    world.getChunkAtAsync(fx, fz, true).thenAccept(ch ->
                            Bukkit.getRegionScheduler().run(this, world, fx, fz, t -> {
                                world.addPluginChunkTicket(fx, fz, this);
                                f.complete(null);
                            }));
                    fs.add(f);
                }
            }
        }
        CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).orTimeout(300, TimeUnit.SECONDS).join();
        log("loaded " + fs.size() + " chunks with tickets");
    }

    private void onOwners(World world, Map<Long, List<Pos>> byChunk, java.util.function.Consumer<Pos> action) {
        List<CompletableFuture<Void>> fs = new ArrayList<>();
        byChunk.forEach((k, list) -> {
            CompletableFuture<Void> f = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(this, world, list.getFirst().cx(), list.getFirst().cz(), t -> {
                list.forEach(action);
                f.complete(null);
            });
            fs.add(f);
        });
        CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).orTimeout(120, TimeUnit.SECONDS).join();
    }

    private void awaitIdle(String phase, long t0) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 180_000;
        int stable = 0;
        while (System.currentTimeMillis() < deadline && stable < 5) {
            stable = pipeline.idle() ? stable + 1 : 0;
            Thread.sleep(100);
        }
        log(phase + ": settled=" + pipeline.idle() + " in " + (System.nanoTime() - t0) / 1_000_000 + "ms | "
                + pipeline.stats.summary());
    }

    /** Every tracked toy block must have the colour of its toy-neighbour count. Read on owners. */
    private String verify(World world) {
        Map<Long, List<Pos>> byChunk = new HashMap<>();
        for (Pos p : tracked) {
            byChunk.computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
        }
        Map<Pos, Material> seen = new ConcurrentHashMap<>();
        List<CompletableFuture<Map<Pos, Material>>> fs = new ArrayList<>();
        for (Pos p : tracked) {
            // capture on p's owner; neighbours in other regions are captured on theirs
            CompletableFuture<Map<Pos, Material>> f = new CompletableFuture<>();
            Bukkit.getRegionScheduler().run(this, world, p.cx(), p.cz(), t ->
                    pipeline.capture(p, 1).whenComplete((m, e) -> f.complete(m)));
            fs.add(f);
        }
        CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).orTimeout(120, TimeUnit.SECONDS).join();
        fs.forEach(f -> seen.putAll(f.join()));
        int toy = 0, wrong = 0;
        for (Pos p : tracked) {
            Material m = seen.get(p);
            if (m == null || !TOY.contains(m)) {
                continue;
            }
            toy++;
            int n = 0;
            for (BlockFace f : FACES) {
                Material o = seen.get(p.off(f.getModX(), f.getModY(), f.getModZ()));
                if (o != null && TOY.contains(o)) {
                    n++;
                }
            }
            if (COLORS[n] != m) {
                wrong++;
            }
        }
        return "toyBlocks=" + toy + " wrongColour=" + wrong;
    }

    private void log(String s) {
        synchronized (report) {
            report.add(s);
        }
        getLogger().info("[async01] " + s);
    }
}
