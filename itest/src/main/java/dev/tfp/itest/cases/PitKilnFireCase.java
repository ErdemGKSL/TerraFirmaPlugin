/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (tryLight, serverTick:
 *       the burn time pitKilnTicks, cookContents, convertPitKilnToPlacedItem)
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.kiln.PitKilnLayers;
import dev.tfp.core.kiln.PitKilnRules;
import dev.tfp.core.kiln.PitKilnState;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.kiln.PitKilnDevice;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.placed.PlacedItemDevice;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

/**
 * {@code kiln.fire} (PAR-POT-14): an unfired vessel in a built, lit pit kiln becomes a fired vessel when the calendar
 * has advanced {@code pit-kiln.ticks}.
 *
 * <ol>
 *   <li>A stone floor and four stone sides hold a {@code tfp:pit_kiln} at stage 15 (the full eight logs; the straw and
 *       logs themselves are not needed: lighting burns them) with an unfired vessel in content slot 0.</li>
 *   <li>A strong fire start on the kiln ({@link FireStarting}, TFC's {@code StartFireEvent}) lights it: the block is at
 *       stage 16 and fire stands above.</li>
 *   <li>The calendar moves past the burn time; the kiln clock hands the kiln to its region, which fires the contents
 *       ({@code PitKilns.evaluate}). The kiln is gone and a fired vessel is left in its place: in the
 *       {@code tfp:placed_item} block when that block is loaded, else (PAR-POT-13 not yet in the pack) as a dropped item.</li>
 * </ol>
 * The start runs without a player (the self-test server has none).
 */
public final class PitKilnFireCase implements SelfTestCase {
    static final String UNFIRED = "tfp:ceramic/unfired_vessel";
    static final String FIRED = "tfp:ceramic/vessel";
    /** Chunks east of spawn: away from forge.continuous (64), firepit.cook (56), device.roundtrip (48). */
    static final int CHUNK_OFFSET = 72;
    private static final Duration STEP = Duration.ofSeconds(20);
    private static final Duration FIRE_WAIT = Duration.ofSeconds(30);

    @Override
    public String name() {
        return "kiln.fire: an unfired vessel in a lit tfp:pit_kiln is a fired vessel after pit-kiln.ticks (PAR-POT-14)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(60 + 6 * STEP.toSeconds() + FIRE_WAIT.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        ModuleServices services = tfp.moduleServices();
        if (services == null || services.get(DeviceService.class).isEmpty() || services.get(FireStarting.class).isEmpty()) {
            throw new Skip("the device or fire module is not enabled");
        }
        DeviceService devices = services.require(DeviceService.class);
        FireStarting fire = services.require(FireStarting.class);
        return OnThread.script("kiln-fire", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            CustomBlocks blocks = tfp.integrations().customBlocks();
            CustomItems items = tfp.integrations().customItems();
            if (!blocks.exists(PitKilnDevice.ID)) {
                throw new AssertionError(PitKilnDevice.ID + " is not a loaded CraftEngine block (configuration/kiln/pit_kiln.yml)");
            }
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            ItemStack unfired = items.create(UNFIRED, 1)
                    .orElseThrow(() -> new AssertionError(UNFIRED + " is not a CraftEngine item"));
            return fire(tfp, devices, fire, blocks, items, unfired);
        });
    }

    @SuppressWarnings("unchecked")
    private static String fire(TfpPlugin tfp, DeviceService devices, FireStarting fire, CustomBlocks blocks, CustomItems items,
            ItemStack unfired) throws Exception {
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
        List<BlockData> saved = new ArrayList<>();
        BlockFace[] sides = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        try {
            OnThread.region(s, world, cx, cz, STEP, () -> {
                y[0] = CraftEngineBlockRoundTripCase.freeY(world, blocks, x, world.getHighestBlockYAt(x, z) + 2, z, log);
                Block kilnBlock = world.getBlockAt(x, y[0], z);
                Block floor = kilnBlock.getRelative(BlockFace.DOWN);
                saved.add(floor.getBlockData());
                floor.setType(Material.STONE, false);
                for (BlockFace f : sides) {
                    Block side = kilnBlock.getRelative(f);
                    saved.add(side.getBlockData());
                    side.setType(Material.STONE, false);
                }
                check(blocks.place(kilnBlock, PitKilnDevice.state(PitKilnLayers.LOG_END), false),
                        "place(pit_kiln stage 15) returned false");
                DeviceKind<PitKilnState> kind = (DeviceKind<PitKilnState>) devices.kinds().find(PitKilnDevice.ID)
                        .orElseThrow(() -> new AssertionError("no device kind " + PitKilnDevice.ID));
                check(devices.edit(kilnBlock, kind, (r, now) -> r.withSlot(0, devices.stacks().encode(unfired))).isPresent(),
                        "the kiln at " + x + "," + y[0] + "," + z + " has no device record");
                return null;
            });
            log.add("kiln at stage 15 at " + x + "," + y[0] + "," + z + " with " + UNFIRED + " on a stone pit");

            // light it: a strong start, as TFC's StartFireEvent
            CompletableFuture<FireStarting.Result> result = new CompletableFuture<>();
            OnThread.region(s, world, cx, cz, STEP, () -> {
                fire.start(world.getBlockAt(x, y[0], z), BlockFace.UP, null, new ItemStack(Material.STICK),
                        FireStarting.Starter.fixed(FireStart.Strength.STRONG, 1.0), result::complete);
                return null;
            });
            FireStarting.Result started = OnThread.await(result, STEP, "the fire start");
            check(started == FireStarting.Result.LIT, "the strong start gave " + started + ", not LIT");
            String lit = OnThread.region(s, world, cx, cz, STEP, () -> {
                int stage = PitKilnDevice.stage(blocks.stateAt(world.getBlockAt(x, y[0], z)).orElse(null));
                check(stage == PitKilnLayers.LIT, "the lit kiln is at stage " + stage);
                Material above = world.getBlockAt(x, y[0] + 1, z).getType();
                check(above == Material.FIRE, "above the lit kiln there is " + above + ", not fire");
                return "lit: stage " + stage + ", fire above";
            });
            log.add(lit);

            // half the burn: still a kiln
            long burn = PitKilnRules.DEFAULT_DURATION_TICKS;
            skip(tfp, s, (int) (burn / 2));
            Thread.sleep(1_500);
            String half = OnThread.region(s, world, cx, cz, STEP, () -> blockId(blocks, world, x, y[0], z));
            check(PitKilnDevice.ID.equals(half), "half way through the burn the block is " + half);

            // the burn is over: the kiln clock hands it to the region, which fires it
            skip(tfp, s, (int) (burn / 2) + 200);
            boolean gone = OnThread.poll(FIRE_WAIT, () -> {
                try {
                    return !PitKilnDevice.ID.equals(OnThread.region(s, world, cx, cz, STEP, () -> blockId(blocks, world, x, y[0], z)));
                } catch (Exception e) {
                    return false;
                }
            });
            check(gone, "the kiln was still a pit kiln " + FIRE_WAIT.toSeconds() + " s after its burn time");
            log.add("burn over (+" + burn + " ticks)");
            log.add(OnThread.region(s, world, cx, cz, STEP, () -> fired(devices, blocks, items, world, x, y[0], z)));
            return String.join("; ", log);
        } finally {
            if (y[0] != Integer.MIN_VALUE) {
                try {
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        blocks.remove(world.getBlockAt(x, y[0], z));
                        world.getBlockAt(x, y[0] + 1, z).setType(Material.AIR, false);
                        for (var e : world.getNearbyEntities(new Location(world, x + 0.5, y[0] + 0.5, z + 0.5), 3, 3, 3)) {
                            if (e instanceof Item item) {
                                item.remove();
                            }
                        }
                        if (!saved.isEmpty()) {
                            world.getBlockAt(x, y[0] - 1, z).setBlockData(saved.get(0), false);
                            for (int i = 0; i < sides.length && i + 1 < saved.size(); i++) {
                                world.getBlockAt(x, y[0], z).getRelative(sides[i]).setBlockData(saved.get(i + 1), false);
                            }
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("kiln.fire cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static void skip(TfpPlugin tfp, Scheduler s, int ticks) throws Exception {
        long before = tfp.clock().get().playerTicks();
        OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, ticks)));
        long moved = tfp.clock().get().playerTicks() - before;
        check(moved >= ticks, "the calendar skip moved " + moved + " player ticks, expected " + ticks);
    }

    private static String blockId(CustomBlocks blocks, World world, int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        return blocks.stateAt(b).map(st -> st.id()).orElse(b.getType().getKey().toString());
    }

    /** A fired vessel is where the kiln was: in the placed item block, or on the ground when that block is not loaded. */
    @SuppressWarnings("unchecked")
    private static String fired(DeviceService devices, CustomBlocks blocks, CustomItems items, World world, int x, int y, int z) {
        Block b = world.getBlockAt(x, y, z);
        String id = blockId(blocks, world, x, y, z);
        List<String> found = new ArrayList<>();
        if (PlacedItemDevice.ID.equals(id)) {
            DeviceService.Inspection i = devices.inspect(b)
                    .orElseThrow(() -> new AssertionError("the placed item block has no device record"));
            check(i.record().isPresent(), "the placed item block has no record: " + i.problem());
            var rec = (dev.tfp.core.device.DeviceRecord<?>) i.record().get();
            for (int slot = 0; slot < 4; slot++) {
                ItemStack st = devices.stacks().decode(rec.slot(slot));
                if (!st.isEmpty()) {
                    found.add(items.idOf(st).orElse(st.getType().getKey().toString()));
                }
            }
        } else {
            for (var e : world.getNearbyEntities(new Location(world, x + 0.5, y + 0.5, z + 0.5), 3, 3, 3)) {
                if (e instanceof Item item) {
                    found.add(items.idOf(item.getItemStack()).orElse(item.getItemStack().getType().getKey().toString()));
                }
            }
        }
        check(found.contains(FIRED), "the kiln spot (" + id + ") holds " + found + ", expected " + FIRED);
        check(!found.contains(UNFIRED), "an unfired vessel is left: " + found);
        return "fired: " + found + " in " + (PlacedItemDevice.ID.equals(id) ? "the placed item block" : "dropped items (" + id + ")");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
