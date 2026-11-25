/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CharcoalForgeBlockEntity.java @ c3b4d9536 (serverTick: addTemp
 *       on every input item with the forge temperature)
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.charcoalforge.CharcoalForgeData;
import dev.tfp.core.charcoalforge.CharcoalForgeSim;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.heat.HeatState;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.charcoalforge.CharcoalForgeDevice;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.scheduler.Scheduler;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * {@code forge.continuous} (PAR-METAL-08): an ingot left in a lit {@code tfp:charcoal_forge} reaches the forge temperature
 * with no click. The forge is placed through our CraftEngine adapter on a stone floor, a charcoal goes into fuel slot 0 and
 * a copper ingot into input slot 5 ({@link DeviceService#edit}: the self-test server has no player to click), the forge is
 * lit ({@link CharcoalForgeDevice#lightAt}), the calendar moves {@value #SKIP_TICKS} ticks (60 s), the device is caught up,
 * and the ingot's heat record must equal the forge temperature (1200 C after 1200 ticks of warming at one degree a tick).
 */
public final class CharcoalForgeContinuousCase implements SelfTestCase {
    static final String CHARCOAL = "minecraft:charcoal";
    static final String INGOT = "tfp:metal/ingot/copper";
    /** Chunks east of spawn: away from firepit.cook (56), device.roundtrip (48) and ce.block_roundtrip (40). */
    static final int CHUNK_OFFSET = 64;
    static final int SKIP_TICKS = 1_200;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "forge.continuous: a lit tfp:charcoal_forge heats an ingot to its temperature with no click (PAR-METAL-08)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(60 + 6 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        ModuleServices services = tfp.moduleServices();
        if (services == null || services.get(DeviceService.class).isEmpty()
                || services.get(CharcoalForgeDevice.class).isEmpty()) {
            throw new Skip("the device or charcoal forge module is not enabled");
        }
        DeviceService devices = services.require(DeviceService.class);
        CharcoalForgeDevice device = services.require(CharcoalForgeDevice.class);
        return OnThread.script("forge-continuous", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            CustomBlocks blocks = tfp.integrations().customBlocks();
            CustomItems items = tfp.integrations().customItems();
            if (!blocks.exists(CharcoalForgeDevice.ID)) {
                throw new AssertionError(CharcoalForgeDevice.ID + " is not a loaded CraftEngine block");
            }
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            ItemStack fuel = items.create(CHARCOAL, 1).orElse(new ItemStack(Material.CHARCOAL));
            ItemStack ingot = items.create(INGOT, 1)
                    .orElseThrow(() -> new AssertionError(INGOT + " is not a CraftEngine item"));
            return heat(tfp, devices, device, blocks, fuel, ingot);
        });
    }

    private static String heat(TfpPlugin tfp, DeviceService devices, CharcoalForgeDevice device, CustomBlocks blocks,
            ItemStack fuel, ItemStack ingot) throws Exception {
        Scheduler s = tfp.scheduler();
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
        BlockData[] floor = {null};
        try {
            OnThread.region(s, world, cx, cz, STEP, () -> {
                y[0] = CraftEngineBlockRoundTripCase.freeY(world, blocks, x, world.getHighestBlockYAt(x, z) + 2, z, log);
                Block below = world.getBlockAt(x, y[0] - 1, z);
                floor[0] = below.getBlockData();
                below.setType(Material.STONE, false);
                Block b = world.getBlockAt(x, y[0], z);
                check(blocks.place(b, CharcoalForgeDevice.state(0), false), "place(charcoal_forge) returned false");
                Optional<DeviceRecord<CharcoalForgeData>> filled = devices.edit(b, device.kind(), (r, now) -> r
                        .withSlot(CharcoalForgeSim.SLOT_FUEL_MIN, devices.stacks().encode(fuel))
                        .withSlot(CharcoalForgeSim.SLOT_INPUT_MIN, devices.stacks().encode(ingot)));
                check(filled.isPresent(), "the forge at " + x + "," + y[0] + "," + z + " has no device record");
                check(device.lightAt(devices, b), "the forge did not light from its charcoal");
                return null;
            });
            log.add("placed at " + x + "," + y[0] + "," + z + " with charcoal and a copper ingot, lit");

            long before = tfp.clock().get().playerTicks();
            OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, SKIP_TICKS)));
            long skipped = tfp.clock().get().playerTicks() - before;
            check(skipped >= SKIP_TICKS, "the calendar skip moved " + skipped + " player ticks, expected " + SKIP_TICKS);
            Block b = world.getBlockAt(x, y[0], z);
            String done = OnThread.region(s, world, cx, cz, STEP, () -> {
                check(devices.catchUp(b).isPresent(), "the catch-up found no device");
                DeviceRecord<CharcoalForgeData> r = devices.edit(b, device.kind(), (rec, now) -> rec)
                        .orElseThrow(() -> new AssertionError("no forge record"));
                ItemStack held = devices.stacks().decode(r.slot(CharcoalForgeSim.SLOT_INPUT_MIN));
                check(!held.isEmpty(), "the ingot is gone");
                HeatState heat = HeatPdc.read(held).orElseThrow(() -> new AssertionError("the ingot has no heat record"));
                float forge = r.state().temperature();
                check(forge >= 1000f, "the forge only reached " + forge + " C");
                check(Math.abs(heat.temperature() - forge) <= 1f,
                        "the ingot is at " + heat.temperature() + " C, the forge at " + forge + " C");
                return "+" + skipped + " ticks: ingot " + heat.temperature() + " C, forge " + forge + " C";
            });
            log.add(done);
            return String.join("; ", log);
        } finally {
            if (y[0] != Integer.MIN_VALUE) {
                try {
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        blocks.remove(world.getBlockAt(x, y[0], z));
                        for (var e : world.getNearbyEntities(new Location(world, x + 0.5, y[0] + 0.5, z + 0.5), 2, 2, 2)) {
                            if (e instanceof org.bukkit.entity.Item item) {
                                item.remove();
                            }
                        }
                        if (floor[0] != null) {
                            world.getBlockAt(x, y[0] - 1, z).setBlockData(floor[0], false);
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("forge.continuous cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
