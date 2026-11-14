/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/FirepitBlockEntity.java @ c3b4d9536 (handleCooking,
 *       mergeOutputStack: the cooked item goes to output slot 5 and the input is cleared)
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.fuel.FireStart;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * {@code firepit.cook} (PAR-HEAT-19): the loaded {@code tfp:firepit} cooks raw beef on a real server. The firepit is
 * placed through our CraftEngine adapter on a stone floor, a stick bundle goes into fuel slot 0 and raw beef into the
 * input slot 4 ({@link DeviceService#edit}: the self-test server has no player to click), it is lit through
 * {@link FireStarting#start} with a strong start (the firepit's {@code FireLightable}, TFC {@code onFireStart}), the
 * calendar moves {@value #SKIP_TICKS} ticks, the device is caught up, and the beef must be cooked in output slot 5 with
 * the input empty. {@code FirepitSim} cooks it 202 ticks after lighting (900 C stick bundle, beef capacity 1, recipe
 * 200 C), so {@value #SKIP_TICKS} ticks leave a margin, rain (burn twice as fast) included.
 *
 * <p>The fuel is TFC's stick bundle, not a log: the TFC logs ({@code tfp:wood/log/*}) are not CraftEngine items yet and
 * a vanilla {@code minecraft:oak_log} is no TFC fuel ({@code tfc:fuel/oak_logs} names {@code #tfc:oak_logs}).
 */
public final class FirepitCookCase implements SelfTestCase {
    static final String STICK_BUNDLE = "tfp:stick_bundle";
    static final String BEEF = "tfp:food/beef";
    static final String COOKED_BEEF = "tfp:food/cooked_beef";
    /** Chunks east of spawn: away from device.roundtrip (48) and ce.block_roundtrip (40). */
    static final int CHUNK_OFFSET = 56;
    static final int SKIP_TICKS = 1_000;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "firepit.cook: a lit tfp:firepit cooks raw beef into output slot 5 (PAR-HEAT-19)";
    }

    @Override
    public Duration timeout() {
        return CraftEngineBlockRoundTripCase.REBUILD.plusSeconds(60 + 8 * STEP.toSeconds());
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("CraftEngine")) {
            throw new Skip("CraftEngine is not installed or its adapter is disabled");
        }
        ModuleServices services = tfp.moduleServices();
        if (services == null || services.get(DeviceService.class).isEmpty() || services.get(FireStarting.class).isEmpty()
                || services.get(Recipes.class).isEmpty()) {
            throw new Skip("the device, fire starting or recipe module is not enabled");
        }
        DeviceService devices = services.require(DeviceService.class);
        FireStarting fire = services.require(FireStarting.class);
        Recipes recipes = services.require(Recipes.class);
        return OnThread.script("firepit-cook", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            CustomBlocks blocks = tfp.integrations().customBlocks();
            CustomItems items = tfp.integrations().customItems();
            if (!blocks.exists(FirepitDevice.ID)) {
                throw new AssertionError(FirepitDevice.ID + " is not a loaded CraftEngine block"
                        + " (configuration/devices/firepit.yml missing from the installed content?)");
            }
            DeviceKind<FirepitData> kind = kind(devices);
            // the heating recipes are indexed lazily: build the index before the catch-up looks the beef up
            recipes.rebuild().get(STEP.toSeconds(), TimeUnit.SECONDS);
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            ItemStack fuel = items.create(STICK_BUNDLE, 1)
                    .orElseThrow(() -> new AssertionError(STICK_BUNDLE + " is not a CraftEngine item"));
            ItemStack beef = items.create(BEEF, 1).orElseThrow(() -> new AssertionError(BEEF + " is not a CraftEngine item"));
            return cook(tfp, devices, fire, kind, blocks, items, fuel, beef);
        });
    }

    @SuppressWarnings("unchecked")
    private static DeviceKind<FirepitData> kind(DeviceService devices) {
        return (DeviceKind<FirepitData>) devices.kinds().all().stream().filter(k -> k.id().equals(FirepitDevice.ID))
                .findFirst().orElseThrow(() -> new AssertionError("no device kind " + FirepitDevice.ID
                        + ": the firepit module is not enabled"));
    }

    private static String cook(TfpPlugin tfp, DeviceService devices, FireStarting fire, DeviceKind<FirepitData> kind,
            CustomBlocks blocks, CustomItems items, ItemStack fuel, ItemStack beef) throws Exception {
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
            // place on a stone floor (TFC BottomSupportedDeviceBlock), fill fuel slot 0 and the input
            OnThread.region(s, world, cx, cz, STEP, () -> {
                y[0] = CraftEngineBlockRoundTripCase.freeY(world, blocks, x, world.getHighestBlockYAt(x, z) + 2, z, log);
                Block below = world.getBlockAt(x, y[0] - 1, z);
                floor[0] = below.getBlockData();
                below.setType(Material.STONE, false);
                Block b = world.getBlockAt(x, y[0], z);
                BlockStateRef unlit = BlockStateRef.of(FirepitDevice.ID).with(FirepitDevice.LIT, "false")
                        .with(FirepitDevice.SMOKE_LEVEL, "0").with(FirepitDevice.AXIS, "x");
                check(blocks.place(b, unlit, false), "place(" + unlit + ") returned false");
                Optional<DeviceRecord<FirepitData>> filled = devices.edit(b, kind, (r, now) -> r
                        .withSlot(FirepitSim.SLOT_FUEL_CONSUME, devices.stacks().encode(fuel))
                        .withSlot(FirepitSim.SLOT_ITEM_INPUT, devices.stacks().encode(beef)));
                check(filled.isPresent(), "the firepit at " + x + "," + y[0] + "," + z + " has no device record");
                check(!filled.get().state().lit(), "the placed firepit is already lit");
                return null;
            });
            log.add("placed at " + x + "," + y[0] + "," + z + " with a stick bundle and raw beef");

            // light it: a strong start on the firepit asks its FireLightable (TFC onFireStart -> light)
            Block b = world.getBlockAt(x, y[0], z);
            CompletableFuture<FireStarting.Result> lit = new CompletableFuture<>();
            fire.start(b, BlockFace.UP, null, ItemStack.empty(),
                    FireStarting.Starter.fixed(FireStart.Strength.STRONG, FireStart.NO_FIREPIT), lit::complete);
            FireStarting.Result result = OnThread.await(lit, STEP, "the fire start");
            check(result == FireStarting.Result.LIT, "the strong start on the firepit gave " + result + ", expected LIT");
            DeviceRecord<FirepitData> burning = OnThread.region(s, world, cx, cz, STEP, () -> {
                DeviceRecord<FirepitData> r = record(devices, kind, b);
                check(r.state().lit(), "not lit after the start: " + r.state());
                check(r.slot(FirepitSim.SLOT_FUEL_CONSUME).isEmpty(), "the stick bundle was not taken as fuel");
                check("true".equals(blocks.stateAt(b).map(st -> st.properties().get(FirepitDevice.LIT)).orElse(null)),
                        "the block state is not lit: " + blocks.stateAt(b));
                return r;
            });
            log.add("lit, " + burning.state().temperature() + " C");

            // the calendar moves on; the catch-up replays the burn and the cooking
            long before = tfp.clock().get().playerTicks();
            OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, SKIP_TICKS)));
            long skipped = tfp.clock().get().playerTicks() - before;
            check(skipped >= SKIP_TICKS, "the calendar skip moved " + skipped + " player ticks, expected " + SKIP_TICKS);
            String done = OnThread.region(s, world, cx, cz, STEP, () -> {
                check(devices.catchUp(b).isPresent(), "the catch-up found no device");
                DeviceRecord<FirepitData> r = record(devices, kind, b);
                ItemBlob input = r.slot(FirepitSim.SLOT_ITEM_INPUT);
                ItemBlob output = r.slot(FirepitSim.SLOT_OUTPUT_1);
                check(input.isEmpty(), "the input slot still holds " + id(devices, items, input));
                check(!output.isEmpty(), "output slot 5 is empty");
                String out = id(devices, items, output);
                check(COOKED_BEEF.equals(out), "output slot 5 holds " + out + ", expected " + COOKED_BEEF);
                check(devices.stacks().decode(output).getAmount() == 1, "output slot 5 holds "
                        + devices.stacks().decode(output).getAmount() + " items");
                return "+" + skipped + " ticks: " + COOKED_BEEF + " in output slot 5, input empty, firepit at "
                        + r.state().temperature() + " C";
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
                                item.remove(); // the slots and ash the removed firepit dropped
                            }
                        }
                        if (floor[0] != null) {
                            world.getBlockAt(x, y[0] - 1, z).setBlockData(floor[0], false);
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("firepit.cook cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static DeviceRecord<FirepitData> record(DeviceService devices, DeviceKind<FirepitData> kind, Block b) {
        DeviceService.Inspection i = devices.inspect(b)
                .orElseThrow(() -> new AssertionError("the block at " + b.getX() + "," + b.getY() + "," + b.getZ()
                        + " is no device"));
        if (!i.problem().isEmpty() || i.record().isEmpty() || !i.kindId().equals(kind.id())) {
            throw new AssertionError("device " + i.kindId() + ": " + i.problem());
        }
        @SuppressWarnings("unchecked")
        DeviceRecord<FirepitData> r = (DeviceRecord<FirepitData>) i.record().get();
        return r;
    }

    private static String id(DeviceService devices, CustomItems items, ItemBlob blob) {
        ItemStack stack = devices.stacks().decode(blob);
        return items.idOf(stack).orElse(stack.getType().getKey().toString());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
