/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BloomeryBlockEntity.java @ c3b4d9536 (addItemsFromWorld, light,
 *       completeRecipe),
 *       src/generated/resources/data/tfc/recipe/bloomery/raw_iron_bloom.json @ c3b4d9536,
 *       src/generated/resources/data/tfc/recipe/anvil/refined_iron_bloom.json @ c3b4d9536
 */
package dev.tfp.itest.cases;

import dev.tfp.core.bloomery.BloomData;
import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.bloomery.BloomDevice;
import dev.tfp.paper.bloomery.BloomeryDevice;
import dev.tfp.paper.bloomery.BloomeryWorld;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
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
import org.bukkit.inventory.ItemStack;

/**
 * {@code bloomery.bloom} (PAR-METAL-14): the wrought iron path up to the anvil. A three-high stone chimney with a
 * {@code tfp:bloomery} gate is built, four cast iron ingots and eight charcoal are dropped into it as item entities, the
 * bloomery takes them, a strong fire start lights it, the calendar moves past the 14400 tick burn, and a
 * {@code tfp:bloom} of four raw iron blooms stands on the inside block. The anvil recipe that turns the raw bloom into the
 * refined bloom must be in the recipe index.
 */
public final class BloomeryCase implements SelfTestCase {
    static final String CHARCOAL = "minecraft:charcoal";
    static final String INGOT = "tfp:metal/ingot/cast_iron";
    static final String RAW_BLOOM = "tfp:raw_iron_bloom";
    static final String REFINED_BLOOM = "tfp:refined_iron_bloom";
    /** Chunks east of spawn: away from forge.continuous (64), firepit.cook (56), device.roundtrip (48), ce.block_roundtrip (40). */
    static final int CHUNK_OFFSET = 72;
    static final int INGOTS = 4;
    static final int CHARCOALS = 8;
    static final int BURN_TICKS = 14_400;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "bloomery.bloom: ingots and charcoal in a lit tfp:bloomery become a tfp:bloom of raw iron blooms (PAR-METAL-14)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(60 + 10 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        ModuleServices services = tfp.moduleServices();
        if (services == null || services.get(DeviceService.class).isEmpty() || services.get(BloomeryDevice.class).isEmpty()
                || services.get(BloomDevice.class).isEmpty()) {
            throw new Skip("the device or bloomery module is not enabled");
        }
        return OnThread.script("bloomery-bloom", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            return run(tfp, services);
        });
    }

    private static String run(TfpPlugin tfp, ModuleServices services) throws Exception {
        DeviceService devices = services.require(DeviceService.class);
        BloomeryDevice device = services.require(BloomeryDevice.class);
        BloomDevice bloom = services.require(BloomDevice.class);
        CustomBlocks blocks = tfp.integrations().customBlocks();
        CustomItems items = tfp.integrations().customItems();
        for (String id : List.of(BloomeryDevice.ID, BloomDevice.ID, BloomeryWorld.MOLTEN)) {
            if (!blocks.exists(id)) {
                throw new AssertionError(id + " is not a loaded CraftEngine block");
            }
        }
        ItemStack ingot = items.create(INGOT, INGOTS).orElseThrow(() -> new AssertionError(INGOT + " is not a CraftEngine item"));
        ItemStack coal = items.create(CHARCOAL, CHARCOALS).orElse(new ItemStack(Material.CHARCOAL, CHARCOALS));
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
            Block[] at = new Block[2]; // [inside, gate]
            OnThread.region(s, world, cx, cz, STEP, () -> {
                y[0] = CraftEngineBlockRoundTripCase.freeY(world, blocks, x, world.getHighestBlockYAt(x, z) + 6, z, log);
                Block inside = world.getBlockAt(x, y[0], z);
                // the gate faces south: the inside block is one north of it
                Block gate = inside.getRelative(0, 0, 1);
                at[0] = inside;
                at[1] = gate;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 3; dz++) {
                        for (int dy = -1; dy <= 5; dy++) {
                            world.getBlockAt(x + dx, y[0] + dy, z + dz).setType(Material.AIR, false);
                        }
                    }
                }
                wall(inside, 0, -1, 0);
                wall(inside, 0, -1, 1);
                wall(inside, -1, 0, 0);
                wall(inside, 1, 0, 0);
                wall(inside, 0, 0, -1);
                wall(inside, -1, 0, 1);
                wall(inside, 1, 0, 1);
                for (int level = 1; level <= 3; level++) {
                    wall(inside, 1, level, 0);
                    wall(inside, -1, level, 0);
                    wall(inside, 0, level, -1);
                    wall(inside, 0, level, 1);
                }
                check(blocks.place(gate, BloomeryDevice.state("south", false, false), false), "place(bloomery) returned false");
                inside.getWorld().dropItem(inside.getLocation().add(0.5, 1.5, 0.5), ingot);
                inside.getWorld().dropItem(inside.getLocation().add(0.5, 1.5, 0.5), coal);
                return null;
            });
            log.add("built a 3-high chimney at " + x + "," + y[0] + "," + z + " and dropped " + INGOTS + " ingots and "
                    + CHARCOALS + " charcoal into it");

            Block gate = at[1];
            Block inside = at[0];
            int units = poll(tfp, world, cx, cz, () -> {
                devices.neighbourChanged(gate);
                return device.contents(devices.edit(gate, device.kind(), (r, now) -> r)
                        .orElseThrow(() -> new AssertionError("no bloomery record"))).size();
            }, INGOTS + CHARCOALS, "the bloomery took the items");
            log.add("took " + units + " units");

            check(OnThread.region(s, world, cx, cz, STEP, () -> device.lightAt(devices, gate)), "the bloomery did not light");
            log.add("lit");

            long before = tfp.clock().get().playerTicks();
            OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, BURN_TICKS + 100)));
            long skipped = tfp.clock().get().playerTicks() - before;
            check(skipped >= BURN_TICKS, "the calendar skip moved " + skipped + " player ticks, expected " + BURN_TICKS);
            poll(tfp, world, cx, cz, () -> {
                devices.neighbourChanged(gate);
                return blocks.stateAt(inside).map(BlockStateRef::id).filter(BloomDevice.ID::equals).isPresent() ? 1 : 0;
            }, 1, "the bloom block");
            OnThread.region(s, world, cx, cz, STEP, () -> {
                DeviceRecord<BloomData> r = bloom.read(devices, inside)
                    .orElseThrow(() -> new AssertionError("the bloom has no record"));
                check(r.state().equals(BloomData.made(INGOTS)), "the bloom is " + r.state() + ", expected " + INGOTS + " blooms");
                ItemStack held = devices.stacks().decode(r.slot(0));
                check(!held.isEmpty(), "the bloom holds no item");
                check(items.idOf(held).filter(RAW_BLOOM::equals).isPresent(), "the bloom holds " + items.idOf(held));
                check(blocks.stateAt(gate).map(BloomeryDevice::lit).filter(l -> !l).isPresent(), "the bloomery is still lit");
                return null;
            });
            log.add("+" + skipped + " ticks: a bloom of " + INGOTS + " " + RAW_BLOOM);

            Recipes recipes = services.require(Recipes.class);
            check(!recipes.index().byItem(RecipeTypes.ANVIL, RAW_BLOOM).isEmpty(),
                "no anvil recipe takes " + RAW_BLOOM + " (the refined bloom)");
            check(recipes.index().byItem(RecipeTypes.ANVIL, RAW_BLOOM).stream()
                .anyMatch(e -> e.recipe().toString().contains(REFINED_BLOOM)), "no anvil recipe makes " + REFINED_BLOOM);
            log.add("anvil recipe " + RAW_BLOOM + " -> " + REFINED_BLOOM);
            return String.join("; ", log);
        } finally {
            if (y[0] != Integer.MIN_VALUE) {
                try {
                    OnThread.region(s, world, cx, cz, STEP, () -> {
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dz = -2; dz <= 3; dz++) {
                                for (int dy = -1; dy <= 5; dy++) {
                                    Block b = world.getBlockAt(x + dx, y[0] + dy, z + dz);
                                    blocks.remove(b);
                                    b.setType(Material.AIR, false);
                                }
                            }
                        }
                        for (var e : world.getNearbyEntities(new Location(world, x + 0.5, y[0] + 0.5, z + 0.5), 4, 6, 4)) {
                            if (e instanceof org.bukkit.entity.Item item) {
                                item.remove();
                            }
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("bloomery.bloom cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static void wall(Block inside, int dx, int dy, int dz) {
        inside.getRelative(dx, dy, dz).setType(Material.STONE, false);
    }

    /** Polls {@code read} on the region until it reaches {@code want}; fails after about {@link #STEP}. */
    private static int poll(TfpPlugin tfp, World world, int cx, int cz, java.util.concurrent.Callable<Integer> read, int want,
            String what) throws Exception {
        long deadline = System.nanoTime() + STEP.toNanos();
        int last = -1;
        while (System.nanoTime() < deadline) {
            last = OnThread.region(tfp.scheduler(), world, cx, cz, STEP, read);
            if (last >= want) {
                return last;
            }
            Thread.sleep(500);
        }
        throw new AssertionError(what + ": " + last + " of " + want + " after " + STEP.toSeconds() + " s");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
