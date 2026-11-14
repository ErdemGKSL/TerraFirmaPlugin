/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (startFire: the firepit pile),
 *       src/main/java/net/dries007/tfc/common/items/FirestarterItem.java @ c3b4d9536 (a strong start)
 */
package dev.tfp.itest.cases;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.fuel.FireStartPlan;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireModule;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * {@code fire.start} (PAR-HEAT-18, the deferred in-game check of PAR-HEAT-05): TFC's {@code StartFireEvent.startFire}
 * on a stick and log pile, through the {@link FireStarting} service and PAR-HEAT-06's firepit factory.
 *
 * <ol>
 *   <li>Three sticks and one log lie on a stone block. A strong start with a firepit base of 1.0 (always builds) on the
 *       top face gives {@code FIREPIT}: a {@code tfp:firepit} block above the stone, lit, with the log in a fuel slot
 *       (or already burning), and no item entities left on the ground.</li>
 *   <li>A fresh pile and a WEAK start (no lightable takes it): {@code WEAK}, nothing changes.</li>
 *   <li>The same pile lit as flint and pyrite with a forced strong plan ({@code FireStartPlan(STRONG, 1.0, 0f)}):
 *       {@code FIREPIT} again.</li>
 * </ol>
 * The starts run without a player (the self-test server has none): the axis is X, as TFC's dispenser path.
 */
public final class FireStartCase implements SelfTestCase {
    static final String LOG = "tfp:wood/log/oak";
    /** Chunks east of spawn: away from the device (48) and block (40) round trips. */
    static final int CHUNK_OFFSET = 56;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "fire.start: a strong start lights a stick and log pile into a lit firepit, a weak one does nothing "
                + "(PAR-HEAT-18)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(12 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        ModuleServices services = tfp.moduleServices();
        if (services == null) {
            throw new Skip("the feature modules are not enabled");
        }
        if (services.get(FireStarting.class).isEmpty() || services.get(DeviceService.class).isEmpty()
                || services.get(Tags.class).isEmpty()) {
            throw new Skip("the fire, device or tag module is not enabled");
        }
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine absent: the firepit block cannot be placed");
        }
        FireStarting fire = services.require(FireStarting.class);
        DeviceService devices = services.require(DeviceService.class);
        Tags tags = services.require(Tags.class);
        return OnThread.script("fire-start", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            CustomBlocks blocks = tfp.integrations().customBlocks();
            if (!blocks.exists(FirepitDevice.ID)) {
                throw new Skip(FirepitDevice.ID + " is not loaded in CraftEngine (PAR-HEAT-19 writes "
                        + "configuration/devices/firepit.yml): no firepit can stand, a strong start places fire");
            }
            ItemStack log = logStack(tfp, tags);
            return pile(tfp, fire, devices, blocks, log);
        });
    }

    /** One {@code #tfc:firepit_logs} log: TFC's oak log through CraftEngine, else vanilla's when the tag holds it. */
    static ItemStack logStack(TfpPlugin tfp, Tags tags) {
        Optional<ItemStack> tfc = tfp.integrations().customItems().create(LOG, 1);
        if (tfc.isPresent() && tags.is(tfc.get(), FireModule.FIREPIT_LOGS)) {
            return tfc.get();
        }
        ItemStack oak = new ItemStack(Material.OAK_LOG);
        if (tags.is(oak, FireModule.FIREPIT_LOGS)) {
            return oak;
        }
        throw new AssertionError("neither " + LOG + " nor minecraft:oak_log is in #" + FireModule.FIREPIT_LOGS);
    }

    private static String pile(TfpPlugin tfp, FireStarting fire, DeviceService devices, CustomBlocks blocks,
                               ItemStack log) throws Exception {
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        int x = (cx << 4) + 8;
        int z = (cz << 4) + 8;
        List<String> notes = new ArrayList<>();
        OnThread.await(world.getChunkAtAsync(cx, cz), STEP, "loading chunk " + cx + "," + cz);
        OnThread.global(s, STEP, () -> {
            world.setChunkForceLoaded(cx, cz, true);
            return null;
        });
        int[] gy = {Integer.MIN_VALUE};
        try {
            // the ground G (stone: a sturdy top face) with two cells of air above it
            OnThread.region(s, world, cx, cz, STEP, () -> {
                int y = Math.min(world.getHighestBlockYAt(x, z) + 1, world.getMaxHeight() - 4);
                gy[0] = y;
                world.getBlockAt(x, y, z).setType(Material.STONE, false);
                for (int dy = 1; dy <= 3; dy++) {
                    Block b = world.getBlockAt(x, y + dy, z);
                    if (blocks.stateAt(b).isPresent()) {
                        blocks.remove(b);
                    }
                    b.setType(Material.AIR, false);
                }
                clearItems(world, x, y + 1, z);
                return null;
            });
            int py = gy[0] + 1;

            // 1. strong start, firepit base 1.0: a lit firepit with the log
            drop(s, world, cx, cz, x, py, z, log);
            FireStarting.Result strong = start(s, world, cx, cz, fire, x, gy[0], z,
                    FireStarting.Starter.fixed(FireStart.Strength.STRONG, 1.0));
            check(strong == FireStarting.Result.FIREPIT, "strong start on the pile gave " + strong + ", not FIREPIT");
            notes.add("strong: " + OnThread.region(s, world, cx, cz, STEP,
                    () -> assertFirepit(devices, blocks, world, x, py, z)));

            // 2. a fresh pile and a weak start: nothing
            OnThread.region(s, world, cx, cz, STEP, () -> blocks.remove(world.getBlockAt(x, py, z)));
            OnThread.region(s, world, cx, cz, STEP, () -> {
                world.getBlockAt(x, py, z).setType(Material.AIR, false);
                clearItems(world, x, py, z);
                return null;
            });
            drop(s, world, cx, cz, x, py, z, log);
            FireStarting.Result weak = start(s, world, cx, cz, fire, x, gy[0], z,
                    FireStarting.Starter.fixed(FireStart.Strength.WEAK, 1.0));
            check(weak == FireStarting.Result.WEAK, "weak start on the pile gave " + weak + ", not WEAK");
            String unchanged = OnThread.region(s, world, cx, cz, STEP, () -> {
                Block p = world.getBlockAt(x, py, z);
                check(p.getType() == Material.AIR && blocks.stateAt(p).isEmpty(),
                        "after the weak start the pile cell holds " + p.getType() + " " + blocks.stateAt(p));
                int n = items(world, x, py, z).size();
                check(n == 2, "after the weak start " + n + " item entities lie on the ground, expected the 2 of the pile");
                return "nothing changed, " + n + " stacks on the ground";
            });
            notes.add("weak: " + unchanged);

            // 3. the same pile as flint and pyrite with a forced strong plan
            FireStarting.Result flint = start(s, world, cx, cz, fire, x, gy[0], z,
                    (raining, random) -> new FireStartPlan(FireStart.Strength.STRONG, 1.0, 0f));
            check(flint == FireStarting.Result.FIREPIT, "flint and pyrite (forced strong) gave " + flint + ", not FIREPIT");
            notes.add("flint and pyrite: " + OnThread.region(s, world, cx, cz, STEP,
                    () -> assertFirepit(devices, blocks, world, x, py, z)));
            return "pile at " + x + "," + py + "," + z + ": " + String.join("; ", notes);
        } finally {
            if (gy[0] != Integer.MIN_VALUE) {
                try {
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        Block p = world.getBlockAt(x, gy[0] + 1, z);
                        if (blocks.stateAt(p).isPresent()) {
                            blocks.remove(p);
                        }
                        p.setType(Material.AIR, false);
                        clearItems(world, x, gy[0] + 1, z);
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("fire.start cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    /** Drops 3 sticks and {@code log} at the centre of the pile cell, at rest. */
    private static void drop(Scheduler s, World world, int cx, int cz, int x, int y, int z, ItemStack log)
            throws Exception {
        OnThread.region(s, world, cx, cz, STEP, () -> {
            Location at = new Location(world, x + 0.5, y + 0.1, z + 0.5);
            for (ItemStack stack : List.of(new ItemStack(Material.STICK, 3), log.clone())) {
                Item item = world.dropItem(at, stack);
                item.setVelocity(new Vector());
                item.setPickupDelay(Integer.MAX_VALUE);
            }
            return null;
        });
    }

    /** Starts a fire on the top face of the ground at {@code x, gy, z} and waits for the region-thread result. */
    private static FireStarting.Result start(Scheduler s, World world, int cx, int cz, FireStarting fire, int x,
                                             int gy, int z, FireStarting.Starter starter) throws Exception {
        CompletableFuture<FireStarting.Result> result = new CompletableFuture<>();
        OnThread.region(s, world, cx, cz, STEP, () -> {
            fire.start(world.getBlockAt(x, gy, z), BlockFace.UP, null, new ItemStack(Material.STICK), starter,
                    result::complete);
            return null;
        });
        return OnThread.await(result, STEP, "the fire start");
    }

    /** The cell holds a lit tfp:firepit with the log in a fuel slot (or burning) and the ground items are gone. */
    @SuppressWarnings("unchecked")
    private static String assertFirepit(DeviceService devices, CustomBlocks blocks, World world, int x, int y, int z) {
        Block p = world.getBlockAt(x, y, z);
        String id = blocks.stateAt(p).map(st -> st.id()).orElse(p.getType().getKey().toString());
        check(id.equals(FirepitDevice.ID), "the pile cell holds " + id + ", not " + FirepitDevice.ID);
        DeviceService.Inspection i = devices.inspect(p)
                .orElseThrow(() -> new AssertionError("the firepit at " + x + "," + y + "," + z + " is no device"));
        check(i.record().isPresent(), "firepit record: " + i.problem());
        DeviceRecord<FirepitData> r = (DeviceRecord<FirepitData>) i.record().get();
        check(r.state().lit(), "the firepit is not lit: " + r.state());
        int fuel = 0;
        for (int slot = FirepitSim.SLOT_FUEL_CONSUME; slot <= FirepitSim.SLOT_FUEL_INPUT; slot++) {
            ItemBlob b = r.slot(slot);
            if (!b.isEmpty()) {
                fuel++;
            }
        }
        int burn = r.state().heat().burnTicks();
        check(fuel > 0 || burn > 0, "the log is neither in a fuel slot nor burning: " + r.state());
        int left = items(world, x, y, z).size();
        check(left == 0, left + " item entities are left on the ground after the firepit was built");
        return "lit " + FirepitDevice.ID + " (fuel slots " + fuel + ", burnTicks " + burn + "), ground clear";
    }

    /** TFC's scan box around the pile cell. Region thread. */
    private static List<Item> items(World world, int x, int y, int z) {
        BoundingBox box = new BoundingBox(x - 0.5, y, z - 0.5, x + 1.5, y + 1, z + 1.5);
        List<Item> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(box, e -> e instanceof Item && e.isValid())) {
            out.add((Item) e);
        }
        return out;
    }

    private static void clearItems(World world, int x, int y, int z) {
        for (Item item : items(world, x, y, z)) {
            item.remove();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
