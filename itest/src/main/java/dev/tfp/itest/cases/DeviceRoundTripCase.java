/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.itest.SelfTestPack;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.device.DeviceBridge;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.ProbeDevice;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * {@code device.roundtrip} (PAR-INFRA-18): the device framework end to end on a real server, over two starts on one
 * run directory (as {@code animal.spawn_persist}). The self-test pack's {@code tfpitest:device/probe} block has the
 * {@code tfp:device} CraftEngine behaviour; this case registers the framework's {@link ProbeDevice} kind for it.
 *
 * <ol>
 *   <li>First start: the probe is placed through our CraftEngine adapter; its block entity exists and holds a record
 *       stamped at creation (so {@code tfp:device} is registered and {@code DeviceController.onLoad} ran). A right
 *       click with a diamond fills slot 0; the calendar moves forward; a plain right click catches the device up once
 *       with the whole gap (the simulated ticks grow by exactly the {@code lastUpdateTick} step). The chunk is
 *       unloaded, CraftEngine's 30 s cache is waited out, the chunk is loaded again and the record is unchanged. The
 *       block and a marker are kept.</li>
 *   <li>Second start: the record is unchanged after the restart. The probe is broken: its block entity is gone and
 *       the diamond lies at the block (TFC {@code ejectInventory}).</li>
 * </ol>
 * The right clicks call {@link DeviceService#use} on the region thread without a player (the self-test server has
 * none): the same router method CraftEngine's {@code useOnBlock} calls.
 */
public final class DeviceRoundTripCase implements SelfTestCase {
    static final String PROBE = SelfTestPack.NAMESPACE + ":device/probe";
    static final String MARKER = "device-roundtrip.marker";
    /** Chunks east of spawn: away from ce.block_roundtrip (40) and the animal cases (0..3). */
    static final int CHUNK_OFFSET = 48;
    /** Calendar ticks moved between the two clicks. */
    static final int SKIP_TICKS = 2_400;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "device.roundtrip: a tfp:device probe places, catches up once, persists over unload and restart and "
                + "drops its slots on break (PAR-INFRA-18)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(CraftEngineBlockRoundTripCase.REBUILD.toSeconds() + 60
                + CraftEngineBlockRoundTripCase.cacheSeconds() + 8 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        Path dataDir = ctx.dataDir();
        if (dataDir == null) {
            throw new Skip("no self-test data folder for the restart marker");
        }
        DeviceService devices = tfp.moduleServices().get(DeviceService.class)
                .orElseThrow(() -> new AssertionError("no DeviceService: the device module is not enabled"));
        Path marker = dataDir.resolve(MARKER);
        return OnThread.script("device-roundtrip", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            CustomBlocks blocks = tfp.integrations().customBlocks();
            if (!blocks.exists(PROBE)) {
                throw new Skip(PROBE + " is not loaded: the self-test pack was not installed this run "
                        + "(plugins/CraftEngine/resources must exist before the start: run the self-test a second time)");
            }
            if (devices.kinds().all().stream().noneMatch(k -> k.id().equals(PROBE))) {
                devices.register(ProbeDevice.kind(PROBE, DeviceKind.RemoveBehaviour.DROP));
            }
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            return Files.exists(marker) ? secondStart(tfp, devices, marker) : firstStart(tfp, devices, dataDir, marker);
        });
    }

    @SuppressWarnings("unchecked")
    static DeviceRecord<ProbeDevice.State> record(DeviceService devices, Block b) {
        DeviceService.Inspection i = devices.inspect(b)
                .orElseThrow(() -> new AssertionError("the block at " + b.getX() + "," + b.getY() + "," + b.getZ()
                        + " is no device"));
        if (!i.problem().isEmpty() || i.record().isEmpty()) {
            throw new AssertionError("device " + i.kindId() + ": " + i.problem());
        }
        return (DeviceRecord<ProbeDevice.State>) i.record().get();
    }

    static String describe(DeviceRecord<ProbeDevice.State> r) {
        return "uses=" + r.state().uses() + " ticks=" + r.state().simulatedTicks() + " last=" + r.lastUpdateTick();
    }

    private static Material slot0(DeviceService devices, DeviceRecord<ProbeDevice.State> r) {
        ItemBlob b = r.slot(0);
        return b.isEmpty() ? Material.AIR : devices.stacks().decode(b).getType();
    }

    private static String firstStart(TfpPlugin tfp, DeviceService devices, Path dataDir, Path marker)
            throws Exception {
        Scheduler s = tfp.scheduler();
        CustomBlocks blocks = tfp.integrations().customBlocks();
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        int x = (cx << 4) + 8;
        int z = (cz << 4) + 8;
        List<String> log = new ArrayList<>();
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            return null;
        });
        int[] y = {Integer.MIN_VALUE};
        boolean ok = false;
        try {
            // place: the block entity is created and stamped at once
            DeviceRecord<ProbeDevice.State> placed = OnThread.region(s, world, cx, cz, STEP, () -> {
                y[0] = CraftEngineBlockRoundTripCase.freeY(world, blocks, x, world.getHighestBlockYAt(x, z) + 2, z, log);
                Block b = world.getBlockAt(x, y[0], z);
                check(blocks.place(b, BlockStateRef.of(PROBE), false), "place(" + PROBE + ") returned false");
                DeviceRecord<ProbeDevice.State> r = record(devices, b);
                check(!r.neverUpdated(), "the record was not stamped when its block entity was created: " + describe(r));
                return r;
            });
            log.add("placed at " + x + "," + y[0] + "," + z + ": " + describe(placed));

            // right click with a diamond: slot filled
            DeviceRecord<ProbeDevice.State> filled = OnThread.region(s, world, cx, cz, STEP, () -> {
                Block b = world.getBlockAt(x, y[0], z);
                check(devices.use(b, null, EquipmentSlot.HAND, new ItemStack(Material.DIAMOND)),
                        "the diamond click was not consumed");
                DeviceRecord<ProbeDevice.State> r = record(devices, b);
                check(slot0(devices, r) == Material.DIAMOND, "slot 0 holds " + slot0(devices, r));
                return r;
            });

            // the calendar moves on; the next click catches up once with the whole gap
            long before = tfp.clock().get().playerTicks();
            OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, SKIP_TICKS)));
            long skipped = tfp.clock().get().playerTicks() - before;
            check(skipped > 0, "the calendar skip moved no player ticks");
            DeviceRecord<ProbeDevice.State> caught = OnThread.region(s, world, cx, cz, STEP, () -> {
                Block b = world.getBlockAt(x, y[0], z);
                check(devices.use(b, null, EquipmentSlot.HAND, ItemStack.empty()), "the plain click was not consumed");
                return record(devices, b);
            });
            long gap = caught.lastUpdateTick() - filled.lastUpdateTick();
            long simulated = caught.state().simulatedTicks() - filled.state().simulatedTicks();
            check(caught.state().uses() == filled.state().uses() + 1, "uses " + describe(caught));
            check(gap >= skipped, "lastUpdateTick moved " + gap + " < the skipped " + skipped);
            check(simulated == gap, "catch-up simulated " + simulated + " ticks for a gap of " + gap);
            log.add("+" + skipped + " player ticks: caught up once, " + describe(caught));

            // unload, wait out CraftEngine's cache, load again
            unload(tfp, world, cx, cz);
            log.add("chunk unloaded");
            Thread.sleep(CraftEngineBlockRoundTripCase.cacheSeconds() * 1000);
            OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "reloading chunk " + cx + "," + cz);
            DeviceRecord<ProbeDevice.State> reloaded = OnThread.region(s, world, cx, cz, STEP,
                    () -> record(devices, world.getBlockAt(x, y[0], z)));
            check(reloaded.equals(caught), "after unload + reload: " + describe(reloaded) + ", expected "
                    + describe(caught));
            log.add("same record after unload + reload");

            Properties p = new Properties();
            p.setProperty("x", Integer.toString(x));
            p.setProperty("y", Integer.toString(y[0]));
            p.setProperty("z", Integer.toString(z));
            p.setProperty("record", describe(caught));
            Files.createDirectories(dataDir);
            try (var w = Files.newBufferedWriter(marker, StandardCharsets.UTF_8)) {
                p.store(w, "device.roundtrip first start");
            }
            ok = true;
            return "first start: " + String.join("; ", log) + "; kept for the second start";
        } finally {
            if (!ok && y[0] != Integer.MIN_VALUE) {
                try {
                    OnThread.region(s, world, cx, cz, STEP, () -> blocks.remove(world.getBlockAt(x, y[0], z)));
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("device.roundtrip cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    /** Releases the chunk's force-load ticket and waits for it to unload (no players). */
    private static void unload(TfpPlugin tfp, World world, int cx, int cz) throws Exception {
        CompletableFuture<Void> unloaded = new CompletableFuture<>();
        Listener listener = new Listener() {
            @EventHandler
            public void onUnload(ChunkUnloadEvent e) {
                if (e.getWorld().equals(world) && e.getChunk().getX() == cx && e.getChunk().getZ() == cz) {
                    unloaded.complete(null);
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(listener, tfp);
        try {
            OnThread.global(tfp.scheduler(), STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
            OnThread.await(unloaded, Duration.ofSeconds(60), "unloading chunk " + cx + "," + cz);
        } finally {
            HandlerList.unregisterAll(listener);
        }
    }

    private static String secondStart(TfpPlugin tfp, DeviceService devices, Path marker) throws Exception {
        Properties p = new Properties();
        try {
            p.load(new StringReader(Files.readString(marker, StandardCharsets.UTF_8)));
        } finally {
            Files.deleteIfExists(marker); // one try per pair of starts
        }
        Scheduler s = tfp.scheduler();
        CustomBlocks blocks = tfp.integrations().customBlocks();
        World world = Bukkit.getWorlds().getFirst();
        int x = Integer.parseInt(p.getProperty("x"));
        int y = Integer.parseInt(p.getProperty("y"));
        int z = Integer.parseInt(p.getProperty("z"));
        int cx = x >> 4;
        int cz = z >> 4;
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        return OnThread.region(s, world, cx, cz, STEP, () -> {
            Block b = world.getBlockAt(x, y, z);
            try {
                DeviceRecord<ProbeDevice.State> r = record(devices, b);
                check(describe(r).equals(p.getProperty("record")), "after the restart: " + describe(r)
                        + ", expected " + p.getProperty("record"));
                check(slot0(devices, r) == Material.DIAMOND, "after the restart slot 0 holds " + slot0(devices, r));
            } catch (AssertionError | RuntimeException e) {
                blocks.remove(b);
                throw e;
            }
            check(blocks.remove(b), "remove returned false");
            check(blocks.stateAt(b).isEmpty(), "still a custom block after remove: " + blocks.stateAt(b));
            check(DeviceBridge.store().read(b).isEmpty(), "the device block entity survived the break");
            Location centre = b.getLocation().add(0.5, 0.5, 0.5);
            List<Item> drops = new ArrayList<>();
            for (var e : world.getNearbyEntities(centre, 1.5, 1.5, 1.5)) {
                if (e instanceof Item item && item.getItemStack().getType() == Material.DIAMOND) {
                    drops.add(item);
                }
            }
            drops.forEach(Item::remove);
            check(drops.size() == 1, drops.size() + " diamond item(s) at the broken probe, expected 1");
            return "second start: same record after the restart (" + p.getProperty("record")
                    + "); broken: block entity gone, the diamond dropped";
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
