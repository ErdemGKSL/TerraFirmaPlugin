/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (constructor: input and
 *       handstone in on the sides, output out below)
 */
package dev.tfp.itest.cases;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.automation.DeviceAutomation;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.quern.QuernDevice;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.inventory.ItemStack;

/**
 * {@code device.automation} (PAR-INFRA-60): real vanilla hoppers feed and empty a {@code tfp:quern}. A hopper beside the
 * quern pointing into it holds bones: they reach the input slot (TFC: the sides insert). Bone meal put in the output slot
 * ({@link DeviceService#edit}: no player grinds here) is pulled by a hopper below. Runs on the quern's region thread
 * (Paper and Folia).
 */
public final class DeviceAutomationCase implements SelfTestCase {
    /** Chunks east of spawn: away from the other device cases (40..56). */
    static final int CHUNK_OFFSET = 64;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "device.automation: hoppers feed and empty a tfp:quern by side (PAR-INFRA-60)";
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
                || services.get(DeviceAutomation.class).isEmpty()) {
            throw new Skip("the device or device automation module is not enabled");
        }
        DeviceService devices = services.require(DeviceService.class);
        return OnThread.script("device-automation", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            CustomBlocks blocks = tfp.integrations().customBlocks();
            if (!blocks.exists(QuernDevice.ID)) {
                throw new AssertionError(QuernDevice.ID + " is not a loaded CraftEngine block");
            }
            return run(tfp, devices, blocks, kind(devices));
        });
    }

    @SuppressWarnings("unchecked")
    private static DeviceKind<Object> kind(DeviceService devices) {
        return (DeviceKind<Object>) devices.kinds().all().stream().filter(k -> k.id().equals(QuernDevice.ID))
                .findFirst().orElseThrow(() -> new AssertionError("no device kind " + QuernDevice.ID));
    }

    private static String run(TfpPlugin tfp, DeviceService devices, CustomBlocks blocks, DeviceKind<Object> kind)
            throws Exception {
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
        try {
            OnThread.region(s, world, cx, cz, STEP, () -> {
                y[0] = CraftEngineBlockRoundTripCase.freeY(world, blocks, x, world.getHighestBlockYAt(x, z) + 3, z, log);
                Block q = world.getBlockAt(x, y[0], z);
                world.getBlockAt(x, y[0] - 2, z).setType(Material.STONE, false);
                check(blocks.place(q, BlockStateRef.of(QuernDevice.ID).with(QuernDevice.HAS_HANDSTONE, "false"), false),
                        "placing the quern failed");
                check(devices.edit(q, kind, (r, now) -> r).isPresent(), "the quern has no device record");
                Block side = q.getRelative(BlockFace.WEST);
                side.setType(Material.HOPPER, false);
                Hopper data = (Hopper) side.getBlockData();
                data.setFacing(BlockFace.EAST);
                side.setBlockData(data, false);
                ((org.bukkit.block.Hopper) side.getState(false)).getInventory().addItem(new ItemStack(Material.BONE, 4));
                Block below = q.getRelative(BlockFace.DOWN);
                below.setType(Material.HOPPER, false);
                return null;
            });
            log.add("quern at " + x + "," + y[0] + "," + z + ", hopper west with 4 bones, hopper below");
            boolean fed = OnThread.poll(STEP, () -> regionGet(s, world, cx, cz, () -> {
                DeviceRecord<Object> r = devices.edit(world.getBlockAt(x, y[0], z), kind, (rec, now) -> rec)
                        .orElseThrow();
                ItemStack in = devices.stacks().decode(r.slot(QuernDevice.SLOT_INPUT));
                return in.getType() == Material.BONE && in.getAmount() == 4;
            }));
            check(fed, "the side hopper did not move the 4 bones into the quern's input slot");
            log.add("input filled from the side");
            OnThread.region(s, world, cx, cz, STEP, () -> devices.edit(world.getBlockAt(x, y[0], z), kind,
                    (r, now) -> r.withSlot(QuernDevice.SLOT_OUTPUT,
                            devices.stacks().encode(new ItemStack(Material.BONE_MEAL, 2)))));
            boolean emptied = OnThread.poll(STEP, () -> regionGet(s, world, cx, cz, () -> {
                Block below = world.getBlockAt(x, y[0] - 1, z);
                return ((org.bukkit.block.Hopper) below.getState(false)).getInventory()
                        .contains(Material.BONE_MEAL, 2);
            }));
            check(emptied, "the hopper below did not pull the 2 bone meal from the output slot");
            log.add("output emptied downwards");
            return String.join("; ", log);
        } finally {
            if (y[0] != Integer.MIN_VALUE) {
                try {
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        Block q = world.getBlockAt(x, y[0], z);
                        q.getRelative(BlockFace.WEST).setType(Material.AIR, false);
                        q.getRelative(BlockFace.DOWN).setType(Material.AIR, false);
                        blocks.remove(q);
                        for (var e : world.getNearbyEntities(q.getLocation().add(0.5, 0.5, 0.5), 2, 2, 2)) {
                            if (e instanceof org.bukkit.entity.Item item) {
                                item.remove();
                            }
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("device.automation cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static boolean regionGet(Scheduler s, World world, int cx, int cz, java.util.concurrent.Callable<Boolean> body) {
        try {
            return OnThread.region(s, world, cx, cz, STEP, body);
        } catch (Exception e) {
            return false;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
