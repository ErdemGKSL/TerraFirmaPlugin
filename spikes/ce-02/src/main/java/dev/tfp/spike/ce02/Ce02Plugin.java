package dev.tfp.spike.ce02;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Piston;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * M0-CE-02: the same toy crop stored two ways. Boot 1 places and saves, boot 2 loads, verifies and
 * runs the destructive tests. Report: ce02-report.txt (appended per boot).
 */
public final class Ce02Plugin extends JavaPlugin implements Listener {
    static final int A_CX = 100, B_CX = 200, CZ = 100, SIZE = 5, PER_CHUNK = 400, Y = 100;
    final PdcStore store = new PdcStore();
    private final List<String> report = new ArrayList<>();
    private final AtomicInteger threadViolations = new AtomicInteger();
    private volatile CountDownLatch reloadLatch;
    private ImmutableBlockState beState, plainState;
    private BlockData beData, plainData;

    @Override
    public void onLoad() {
        Path root = getDataFolder().toPath().getParent().resolve("CraftEngine/resources/tfpspike2");
        try {
            for (String f : List.of("pack.yml", "configuration/blocks.yml")) {
                Path out = root.resolve(f);
                Files.createDirectories(out.getParent());
                try (InputStream in = getResource("pack/" + f)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        CropBe.register();
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onCeReload(CraftEngineReloadEvent e) {
        beState = CraftEngineBlocks.byId(Key.of("tfpspike2", "crop_be")).defaultState();
        plainState = CraftEngineBlocks.byId(Key.of("tfpspike2", "crop_plain")).defaultState();
        beData = CraftEngineBlocks.getBukkitBlockData(beState);
        plainData = CraftEngineBlocks.getBukkitBlockData(plainState);
        CountDownLatch l = reloadLatch;
        if (l != null) {
            l.countDown();
        }
    }

    // ----- variant (b) lifecycle: after CE restored its blocks (LOWEST) / before CE saves (HIGHEST)
    @EventHandler(priority = EventPriority.LOW)
    public void onChunkLoad(ChunkLoadEvent e) {
        store.onLoad(e.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChunkUnload(ChunkUnloadEvent e) {
        store.onUnload(e.getChunk());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        World w = Bukkit.getWorlds().getFirst();
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (Files.exists(Path.of("ce02-state.txt"))) {
                    boot2(w);
                } else {
                    boot1(w);
                }
            } catch (Throwable ex) {
                log("FAILED: " + ex);
                getSLF4JLogger().error("ce02", ex);
            }
            try {
                Files.write(Path.of("ce02-report.txt"), report, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        }, "tfp-ce02");
        t.setDaemon(true);
        t.start();
    }

    static long plantedFor(int x, int y, int z) {
        return 1_000_000L + (x * 31L + y) * 31L + z;
    }

    private void boot1(World w) throws Exception {
        log("=== boot 1 " + Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
        load(w, A_CX, true);
        load(w, B_CX, true);
        long heap0 = heap();
        long ta = forEachPos(w, A_CX, (b) -> {
            CraftEngineBlocks.place(b.getLocation(), beState, false);
            BlockEntity be = ce(w).getBlockEntityAtIfLoaded(new BlockPos(b.getX(), b.getY(), b.getZ()));
            ((CropBe.Controller) be.controller).plant(plantedFor(b.getX(), b.getY(), b.getZ()));
        }, null);
        long heapA = heap();
        long tb = forEachPos(w, B_CX, (b) -> {
            b.setBlockData(plainData, false);
            store.put(b.getChunk(), b.getX(), b.getY(), b.getZ(), plantedFor(b.getX(), b.getY(), b.getZ()));
        }, store::flush);
        long heapB = heap();
        int n = SIZE * SIZE * PER_CHUNK;
        log("place (a) CE BE: " + n + " blocks in " + ms(ta) + " region time (" + us(ta / n) + "/block); constructed="
                + CropBe.CONSTRUCTED);
        log("place (b) PDC:   " + n + " blocks in " + ms(tb) + " region time (" + us(tb / n) + "/block) incl. flush "
                + ms(PdcStore.ENCODE_NANOS.get()));
        log("heap delta (noisy): (a) " + (heapA - heap0) / n + " B/block, (b) " + (heapB - heapA) / n + " B/block");
        load(w, A_CX, false);
        load(w, B_CX, false);
        Thread.sleep(75_000); // unload + CE cache expiry
        log("save (a): saveCustomData calls=" + CropBe.SAVES + " total " + ms(CropBe.SAVE_NANOS.get()));
        log("save (b): encodes=" + PdcStore.ENCODES + " total " + ms(PdcStore.ENCODE_NANOS.get()));
        log("threadViolations=" + threadViolations);
        Files.writeString(Path.of("ce02-state.txt"), "boot1 done");
    }

    private void boot2(World w) throws Exception {
        log("=== boot 2 " + Bukkit.getName() + " " + Bukkit.getMinecraftVersion());
        long t0 = System.nanoTime();
        load(w, A_CX, true);
        long la = System.nanoTime() - t0;
        t0 = System.nanoTime();
        load(w, B_CX, true);
        long lb = System.nanoTime() - t0;
        log("load 25 chunks wall: (a) " + ms(la) + " loadCustomData=" + CropBe.LOADS + " in " + ms(CropBe.LOAD_NANOS.get())
                + " onLoad=" + CropBe.ONLOAD + "; (b) " + ms(lb) + " decode=" + PdcStore.DECODES + " in "
                + ms(PdcStore.DECODE_NANOS.get()) + " entries=" + PdcStore.ENTRIES_LOADED);
        log("verify (a): " + verifyA(w) + " | verify (b): " + verifyB(w));

        // explosion in the middle chunk of each area
        long removed0 = CropBe.REMOVED.get();
        onOwner(w, new Location(w, A_CX * 16 + 40, Y, CZ * 16 + 40), l -> w.createExplosion(l, 4f, false, true));
        onOwner(w, new Location(w, B_CX * 16 + 40, Y, CZ * 16 + 40), l -> w.createExplosion(l, 4f, false, true));
        Thread.sleep(500);
        log("explosion: (a) onRemove=" + (CropBe.REMOVED.get() - removed0) + " " + verifyA(w) + " | (b) " + verifyB(w));

        // vanilla bypass (WorldEdit-like): setType(AIR, false) on a row
        removed0 = CropBe.REMOVED.get();
        onOwner(w, new Location(w, A_CX * 16 + 2, Y, CZ * 16 + 2), l -> {
            for (int i = 0; i < 10; i++) w.getBlockAt(l.getBlockX() + i, Y, l.getBlockZ()).setType(Material.AIR, false);
        });
        onOwner(w, new Location(w, B_CX * 16 + 2, Y, CZ * 16 + 2), l -> {
            for (int i = 0; i < 10; i++) w.getBlockAt(l.getBlockX() + i, Y, l.getBlockZ()).setType(Material.AIR, false);
        });
        log("setType(AIR) x10: (a) onRemove=" + (CropBe.REMOVED.get() - removed0) + " " + verifyA(w) + " | (b) " + verifyB(w));

        // piston pushing one crop of each kind one block east (inside the chunk)
        for (int cx : new int[] {A_CX, B_CX}) {
            onOwner(w, new Location(w, cx * 16 + 4 + 16, Y + 5, CZ * 16 + 8), l -> {
                int x = l.getBlockX(), z = l.getBlockZ();
                Block target = w.getBlockAt(x + 1, Y + 5, z);
                if (cx == A_CX) {
                    CraftEngineBlocks.place(target.getLocation(), beState, false);
                    ((CropBe.Controller) ce(w).getBlockEntityAtIfLoaded(new BlockPos(x + 1, Y + 5, z)).controller).plant(42);
                } else {
                    target.setBlockData(plainData, false);
                    store.put(target.getChunk(), x + 1, Y + 5, z, 42);
                }
                Piston p = (Piston) Material.PISTON.createBlockData();
                p.setFacing(BlockFace.EAST);
                w.getBlockAt(x, Y + 5, z).setBlockData(p, false);
                w.getBlockAt(x - 1, Y + 5, z).setType(Material.REDSTONE_BLOCK, true);
            });
        }
        Thread.sleep(1500);
        for (int cx : new int[] {A_CX, B_CX}) {
            onOwner(w, new Location(w, cx * 16 + 4 + 16, Y + 5, CZ * 16 + 8), l -> {
                int x = l.getBlockX(), z = l.getBlockZ();
                Block from = w.getBlockAt(x + 1, Y + 5, z), to = w.getBlockAt(x + 2, Y + 5, z);
                String s = "piston " + (cx == A_CX ? "(a)" : "(b)") + ": from=" + describe(w, from) + " to=" + describe(w, to);
                if (cx == B_CX) {
                    s += " store[from]=" + store.get(x + 1, Y + 5, z) + " store[to]=" + store.get(x + 2, Y + 5, z);
                }
                log(s);
            });
        }

        // /ce reload while everything is loaded
        reloadLatch = new CountDownLatch(1);
        Bukkit.getGlobalRegionScheduler().execute(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ce reload"));
        boolean reloaded = reloadLatch.await(120, TimeUnit.SECONDS);
        Thread.sleep(1000);
        log("after /ce reload (event=" + reloaded + "): (a) " + verifyA(w) + " | (b) " + verifyB(w));
        log("threadViolations=" + threadViolations);
    }

    private String describe(World w, Block b) {
        ImmutableBlockState s = CraftEngineBlocks.getCustomBlockState(b);
        String id = s == null ? b.getType().getKey().getKey() : s.owner().value().id().toString();
        BlockEntity be = ce(w).getBlockEntityAtIfLoaded(new BlockPos(b.getX(), b.getY(), b.getZ()), false);
        if (be != null && be.controller instanceof CropBe.Controller c) {
            id += "{planted=" + c.planted + "}";
        }
        return id;
    }

    private String verifyA(World w) throws Exception {
        AtomicInteger ok = new AtomicInteger(), wrong = new AtomicInteger(), missing = new AtomicInteger(),
                foreign = new AtomicInteger();
        forEachPos(w, A_CX, b -> {
            BlockEntity be = ce(w).getBlockEntityAtIfLoaded(new BlockPos(b.getX(), b.getY(), b.getZ()), false);
            if (be == null) {
                missing.incrementAndGet();
            } else if (!(be.controller instanceof CropBe.Controller c)) {
                foreign.incrementAndGet();
            } else if (c.planted == plantedFor(b.getX(), b.getY(), b.getZ())) {
                ok.incrementAndGet();
            } else {
                wrong.incrementAndGet();
            }
        }, null);
        return "ok=" + ok + " wrong=" + wrong + " noBE=" + missing + " foreignController=" + foreign;
    }

    private String verifyB(World w) throws Exception {
        AtomicInteger ok = new AtomicInteger(), wrong = new AtomicInteger(), orphan = new AtomicInteger(),
                missing = new AtomicInteger();
        forEachPos(w, B_CX, b -> {
            Long v = store.get(b.getX(), b.getY(), b.getZ());
            ImmutableBlockState s = CraftEngineBlocks.getCustomBlockState(b);
            boolean isCrop = s != null && s.owner() == plainState.owner();
            if (v == null) {
                if (isCrop) missing.incrementAndGet();
            } else if (!isCrop) {
                orphan.incrementAndGet(); // data whose block is gone: our store must clean these up itself
            } else if (v == plantedFor(b.getX(), b.getY(), b.getZ())) {
                ok.incrementAndGet();
            } else {
                wrong.incrementAndGet();
            }
        }, null);
        return "ok=" + ok + " wrong=" + wrong + " orphanEntries=" + orphan + " missingEntries=" + missing;
    }

    /** Runs the action for every test position, chunk by chunk on the owning region. Returns region nanos. */
    private long forEachPos(World w, int baseCx, Consumer<Block> action, Consumer<Chunk> perChunkEnd) throws Exception {
        AtomicLong nanos = new AtomicLong();
        List<CompletableFuture<Void>> fs = new ArrayList<>();
        for (int cx = baseCx; cx < baseCx + SIZE; cx++) {
            for (int cz = CZ; cz < CZ + SIZE; cz++) {
                final int fx = cx, fz = cz;
                CompletableFuture<Void> f = new CompletableFuture<>();
                Bukkit.getRegionScheduler().run(this, w, fx, fz, t -> {
                    try {
                        if (!Bukkit.isOwnedByCurrentRegion(w, fx, fz)) threadViolations.incrementAndGet();
                        long s = System.nanoTime();
                        for (int i = 0; i < PER_CHUNK; i++) {
                            action.accept(w.getBlockAt(fx * 16 + (i & 15), Y + (i >> 8), fz * 16 + ((i >> 4) & 15)));
                        }
                        if (perChunkEnd != null) perChunkEnd.accept(w.getChunkAt(fx, fz));
                        nanos.addAndGet(System.nanoTime() - s);
                        f.complete(null);
                    } catch (Throwable ex) {
                        f.completeExceptionally(ex);
                    }
                });
                fs.add(f);
            }
        }
        CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).get(120, TimeUnit.SECONDS);
        return nanos.get();
    }

    private void load(World w, int baseCx, boolean ticket) throws Exception {
        List<CompletableFuture<Void>> fs = new ArrayList<>();
        for (int cx = baseCx - 1; cx <= baseCx + SIZE; cx++) {
            for (int cz = CZ - 1; cz <= CZ + SIZE; cz++) {
                final int fx = cx, fz = cz;
                CompletableFuture<Void> f = new CompletableFuture<>();
                if (ticket) {
                    w.getChunkAtAsync(fx, fz, true).thenAccept(c -> Bukkit.getRegionScheduler().run(this, w, fx, fz, t -> {
                        w.addPluginChunkTicket(fx, fz, this);
                        f.complete(null);
                    }));
                } else {
                    Bukkit.getRegionScheduler().run(this, w, fx, fz, t -> {
                        w.removePluginChunkTicket(fx, fz, this);
                        f.complete(null);
                    });
                }
                fs.add(f);
            }
        }
        CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).get(180, TimeUnit.SECONDS);
    }

    private void onOwner(World w, Location l, Consumer<Location> action) throws Exception {
        CompletableFuture<Void> f = new CompletableFuture<>();
        Bukkit.getRegionScheduler().run(this, l, t -> {
            action.accept(l);
            f.complete(null);
        });
        f.get(60, TimeUnit.SECONDS);
    }

    private static CEWorld ce(World w) {
        return BukkitAdaptor.adapt(w).storageWorld();
    }

    private static long heap() {
        for (int i = 0; i < 3; i++) System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    static String ms(long nanos) {
        return String.format("%.1fms", nanos / 1e6);
    }

    static String us(long nanos) {
        return String.format("%.2fus", nanos / 1e3);
    }

    private void log(String s) {
        synchronized (report) {
            report.add(s);
        }
        getLogger().info("[ce02] " + s);
    }
}
