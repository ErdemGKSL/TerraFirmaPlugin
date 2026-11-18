/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536 (convertTo:
 *       the fuel slots and the burn move to the new block entity),
 *       src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java @ c3b4d9536 (handleCooking: the
 *       cooked item replaces the input in its slot with FoodTraits.WOOD_GRILLED)
 */
package dev.tfp.itest.cases;

import dev.tfp.core.calendar.TimeCommand;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.core.firepit.FirepitData;
import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.fuel.FireStart;
import dev.tfp.core.grill.GrillSim;
import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.grill.GrillConversion;
import dev.tfp.paper.grill.GrillDevice;
import dev.tfp.paper.grill.GrillEffects;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import java.lang.reflect.Proxy;
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
 * {@code grill.cook} (PAR-HEAT-24): the loaded {@code tfp:grill} cooks raw beef with TFC's wood grilled trait on a real
 * server. A {@code tfp:firepit} is placed on a stone floor with two stick bundles (fuel slots 0 and 1) and lit through
 * {@link FireStarting#start} with a strong start, as in {@link FirepitCookCase}. {@link GrillConversion#toGrill} (what
 * the wrought iron grill click does, TFC {@code convertTo}) turns it into a lit {@code tfp:grill} that keeps the fuel
 * left in the slots and the burn; raw beef goes into grill slot 4 ({@link DeviceService#edit}: the self-test server has
 * no player to click), the calendar moves {@value #SKIP_TICKS} ticks, the device is caught up, and slot 4 must hold one
 * cooked beef carrying {@code tfp:wood_grilled}. The firepit cooks the beef about 200 ticks after lighting; the second
 * bundle keeps the grill burning past that. The skip stays well under 900 ticks: the fire climbs about 1 C a tick to
 * 900 C, and cooked meat left on the grill at 900 C burns away (TFC's {@code burn_meat} heating recipe, no output), as
 * the in-game check saw after a one-hour skip.
 */
public final class GrillCookCase implements SelfTestCase {
    static final String STICK_BUNDLE = FirepitCookCase.STICK_BUNDLE;
    static final String BEEF = FirepitCookCase.BEEF;
    static final String COOKED_BEEF = FirepitCookCase.COOKED_BEEF;
    /** Chunks east of spawn: away from firepit.cook (56), device.roundtrip (48) and ce.block_roundtrip (40). */
    static final int CHUNK_OFFSET = 60;
    static final int SKIP_TICKS = 500;
    private static final Duration STEP = Duration.ofSeconds(20);

    @Override
    public String name() {
        return "grill.cook: a lit firepit turned into tfp:grill keeps its fuel and grills raw beef with tfp:wood_grilled (PAR-HEAT-24)";
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
        if (services == null || services.get(DeviceService.class).isEmpty() || services.get(FireStarting.class).isEmpty()
                || services.get(Recipes.class).isEmpty()) {
            throw new Skip("the device, fire starting or recipe module is not enabled");
        }
        DeviceService devices = services.require(DeviceService.class);
        FireStarting fire = services.require(FireStarting.class);
        Recipes recipes = services.require(Recipes.class);
        return OnThread.script("grill-cook", () -> {
            OnThread.awaitPackRebuild(tfp, CraftEngineBlockRoundTripCase.REBUILD);
            CustomBlocks blocks = tfp.integrations().customBlocks();
            CustomItems items = tfp.integrations().customItems();
            for (String id : List.of(FirepitDevice.ID, GrillDevice.ID)) {
                if (!blocks.exists(id)) {
                    throw new AssertionError(id + " is not a loaded CraftEngine block"
                            + " (configuration/devices/" + id.substring(id.indexOf(':') + 1) + ".yml missing from the installed content?)");
                }
            }
            DeviceKind<FirepitData> firepit = kind(devices, FirepitDevice.ID);
            DeviceKind<FirepitData> grill = kind(devices, GrillDevice.ID);
            recipes.rebuild().get(STEP.toSeconds(), TimeUnit.SECONDS);
            if (!OnThread.poll(STEP, () -> tfp.clock().get().isSet())) {
                throw new AssertionError("the calendar was never published");
            }
            ItemStack fuel = items.create(STICK_BUNDLE, 1)
                    .orElseThrow(() -> new AssertionError(STICK_BUNDLE + " is not a CraftEngine item"));
            ItemStack beef = items.create(BEEF, 1).orElseThrow(() -> new AssertionError(BEEF + " is not a CraftEngine item"));
            return cook(tfp, devices, fire, firepit, grill, blocks, items, fuel, beef);
        });
    }

    @SuppressWarnings("unchecked")
    private static DeviceKind<FirepitData> kind(DeviceService devices, String id) {
        return (DeviceKind<FirepitData>) devices.kinds().all().stream().filter(k -> k.id().equals(id))
                .findFirst().orElseThrow(() -> new AssertionError("no device kind " + id + ": its module is not enabled"));
    }

    /** No world effects for the self-test's own conversion: the registered grill device draws and sounds its block. */
    private static GrillEffects quietEffects() {
        return (GrillEffects) Proxy.newProxyInstance(GrillEffects.class.getClassLoader(), new Class<?>[] {GrillEffects.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "quiet grill effects";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    }

    private static String cook(TfpPlugin tfp, DeviceService devices, FireStarting fire, DeviceKind<FirepitData> firepit,
            DeviceKind<FirepitData> grill, CustomBlocks blocks, CustomItems items, ItemStack fuel, ItemStack beef)
            throws Exception {
        Scheduler s = tfp.scheduler();
        World world = Bukkit.getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int cx = (spawn.getBlockX() >> 4) + CHUNK_OFFSET;
        int cz = spawn.getBlockZ() >> 4;
        int x = (cx << 4) + 8;
        int z = (cz << 4) + 8;
        List<String> log = new ArrayList<>();
        GrillConversion conversion = new GrillConversion(devices, blocks, quietEffects());
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
                BlockStateRef unlit = BlockStateRef.of(FirepitDevice.ID).with(FirepitDevice.LIT, "false")
                        .with(FirepitDevice.SMOKE_LEVEL, "0").with(FirepitDevice.AXIS, "x");
                check(blocks.place(b, unlit, false), "place(" + unlit + ") returned false");
                Optional<DeviceRecord<FirepitData>> filled = devices.edit(b, firepit, (r, now) -> r
                        .withSlot(FirepitSim.SLOT_FUEL_CONSUME, devices.stacks().encode(fuel))
                        .withSlot(FirepitSim.SLOT_FUEL_CONSUME + 1, devices.stacks().encode(fuel)));
                check(filled.isPresent(), "the firepit at " + x + "," + y[0] + "," + z + " has no device record");
                return null;
            });
            log.add("firepit at " + x + "," + y[0] + "," + z + " with two stick bundles");

            Block b = world.getBlockAt(x, y[0], z);
            CompletableFuture<FireStarting.Result> lit = new CompletableFuture<>();
            fire.start(b, BlockFace.UP, null, ItemStack.empty(),
                    FireStarting.Starter.fixed(FireStart.Strength.STRONG, FireStart.NO_FIREPIT), lit::complete);
            FireStarting.Result result = OnThread.await(lit, STEP, "the fire start");
            check(result == FireStarting.Result.LIT, "the strong start on the firepit gave " + result + ", expected LIT");

            // the wrought iron grill on the lit firepit: TFC convertTo keeps the fuel and the burn
            String converted = OnThread.region(s, world, cx, cz, STEP, () -> {
                check(devices.catchUp(b).isPresent(), "the catch-up found no firepit");
                DeviceRecord<FirepitData> before = record(devices, firepit, b);
                check(before.state().lit(), "the firepit is not lit after the start: " + before.state());
                int fuelBefore = fuelCount(before);
                check(conversion.toGrill(b), "GrillConversion.toGrill refused the lit firepit");
                BlockStateRef state = blocks.stateAt(b).orElseThrow(() -> new AssertionError("no custom block after the conversion"));
                check(state.id().equals(GrillDevice.ID), "the block is " + state.id() + ", expected " + GrillDevice.ID);
                check("true".equals(state.properties().get(FirepitDevice.LIT)), "the grill block is not lit: " + state);
                DeviceRecord<FirepitData> after = record(devices, grill, b);
                check(after.state().lit(), "the grill record is not lit: " + after.state());
                check(fuelCount(after) == fuelBefore, "the grill holds " + fuelCount(after) + " fuel stacks, the firepit had "
                        + fuelBefore);
                check(after.state().heat().burnTicks() == before.state().heat().burnTicks(), "the burn ticks went from "
                        + before.state().heat().burnTicks() + " to " + after.state().heat().burnTicks());
                Optional<DeviceRecord<FirepitData>> withBeef = devices.edit(b, grill, (r, now) -> r
                        .withSlot(GrillSim.SLOT_EXTRA_INPUT_START, devices.stacks().encode(beef)));
                check(withBeef.isPresent(), "the grill has no device record for the beef");
                return "grill: lit, " + fuelBefore + " fuel stack(s), " + after.state().heat().burnTicks() + " burn ticks, "
                        + after.state().temperature() + " C";
            });
            log.add(converted);

            long before = tfp.clock().get().playerTicks();
            OnThread.global(s, STEP, () -> tfp.calendar().skip(TimeCommand.add(TimeCommand.Unit.TICKS, SKIP_TICKS)));
            long skipped = tfp.clock().get().playerTicks() - before;
            check(skipped >= SKIP_TICKS, "the calendar skip moved " + skipped + " player ticks, expected " + SKIP_TICKS);
            String done = OnThread.region(s, world, cx, cz, STEP, () -> {
                check(devices.catchUp(b).isPresent(), "the catch-up found no device");
                DeviceRecord<FirepitData> r = record(devices, grill, b);
                ItemBlob slot = r.slot(GrillSim.SLOT_EXTRA_INPUT_START);
                check(!slot.isEmpty(), "grill slot 4 is empty");
                ItemStack cooked = devices.stacks().decode(slot);
                String id = items.idOf(cooked).orElse(cooked.getType().getKey().toString());
                check(COOKED_BEEF.equals(id), "grill slot 4 holds " + id + ", expected " + COOKED_BEEF);
                check(cooked.getAmount() == 1, "grill slot 4 holds " + cooked.getAmount() + " items");
                List<String> traits = FoodPdc.read(cooked).map(FoodState::traits).orElse(List.of()).stream()
                        .map(FoodTrait::id).toList();
                check(traits.contains(FoodTrait.WOOD_GRILLED.id()), "the cooked beef has the traits " + traits + ", expected "
                        + FoodTrait.WOOD_GRILLED.id());
                return "+" + skipped + " ticks: " + COOKED_BEEF + " in grill slot 4 with " + traits;
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
                                item.remove(); // the slots, ash and grill the removed block dropped
                            }
                        }
                        if (floor[0] != null) {
                            world.getBlockAt(x, y[0] - 1, z).setBlockData(floor[0], false);
                        }
                        return null;
                    });
                } catch (Exception | AssertionError e) {
                    tfp.getLogger().warning("grill.cook cleanup failed: " + e);
                }
            }
            OnThread.global(s, STEP, () -> {
                world.setChunkForceLoaded(cx, cz, false);
                return null;
            });
        }
    }

    private static int fuelCount(DeviceRecord<FirepitData> r) {
        int n = 0;
        for (int i = FirepitSim.SLOT_FUEL_CONSUME; i <= FirepitSim.SLOT_FUEL_INPUT; i++) {
            n += r.slot(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    private static DeviceRecord<FirepitData> record(DeviceService devices, DeviceKind<FirepitData> kind, Block b) {
        DeviceService.Inspection i = devices.inspect(b)
                .orElseThrow(() -> new AssertionError("the block at " + b.getX() + "," + b.getY() + "," + b.getZ()
                        + " is no device"));
        if (!i.problem().isEmpty() || i.record().isEmpty() || !i.kindId().equals(kind.id())) {
            throw new AssertionError("device " + i.kindId() + " (expected " + kind.id() + "): " + i.problem());
        }
        @SuppressWarnings("unchecked")
        DeviceRecord<FirepitData> r = (DeviceRecord<FirepitData>) i.record().get();
        return r;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
