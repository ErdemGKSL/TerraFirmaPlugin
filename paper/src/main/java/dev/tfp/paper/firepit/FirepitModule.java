/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BottomSupportedDeviceBlock.java @ c3b4d9536 (canSurvive),
 *       src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (placing the firepit),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: strong starts light a firepit),
 *       src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536 (FIREPIT_FUEL, SOLID_TOP_FACE)
 */
package dev.tfp.paper.firepit;

import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.fire.FirepitFactory;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.weather.PrecipitationService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-06: the firepit. Registers the {@link FirepitDevice} kind {@code tfp:firepit}, its {@code FireLightable}
 * (a strong start lights it) and the {@link FirepitFactory} that builds one from a stick and log pile. Needs the tags,
 * device, heating and fire modules before it in the service file; the rain comes from PAR-CLIM-02's
 * {@link PrecipitationService} when it runs (never raining without it).
 */
public final class FirepitModule implements FeatureModule {
    public static final String ID = "firepit";
    public static final String FIREPIT_FUEL = "tfc:firepit_fuel";
    public static final String SOLID_TOP_FACE = "tfc:solid_top_face";
    public static final String SHOVELS = "minecraft:shovels";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService devices = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        HeatingService heating = context.services().require(HeatingService.class);
        ItemHeatDefinitionsCache heatDefinitions = context.services().require(ItemHeatDefinitionsCache.class);
        CustomBlocks blocks = context.integrations().customBlocks();
        Supplier<FirepitFuels> fuels = fuels(() -> context.registryService().registries());
        ItemHeatHooks.Capacities capacities = ItemHeatHooks.Capacities.byId(tags::itemId, heatDefinitions);
        PaperFirepitEffects effects = new PaperFirepitEffects(context.scheduler(), blocks,
            () -> context.integrations().blockVisuals(), devices::neighbourChanged);
        FirepitDevice device = new FirepitDevice(new FirepitDevice.Services(
            stack -> tags.itemId(stack).flatMap(id -> fuels.get().get(id)),
            stack -> tags.is(stack, FIREPIT_FUEL),
            capacities::of,
            cooker(heating),
            stack -> douse(stack, tags),
            block -> raining(context, block),
            block -> canSurvive(block, tags),
            n -> context.integrations().customItems().create(FirepitDevice.WOOD_ASH, n).orElse(ItemStack.empty()),
            ThreadLocalRandom::current, HeatConfig.DEFAULT, effects), devices.stacks());
        devices.register(device.kind());
        context.services().get(FireStarting.class).ifPresent(fire -> {
            fire.register(ctx -> ctx.strong() && blocks.stateAt(ctx.block()).map(s -> s.id().equals(FirepitDevice.ID)).orElse(false)
                && device.light(ctx.block(), devices::neighbourChanged));
            fire.firepitFactory(factory(device, blocks, tags, devices));
        });
    }

    /** TFC's {@code StartFireEvent} firepit: placed unlit on the axis, the logs put in, then lit. Region thread. */
    static FirepitFactory factory(FirepitDevice device, CustomBlocks blocks, Tags tags, DeviceService devices) {
        return new FirepitFactory() {
            @Override
            public boolean canSurvive(Block at) {
                // without the CraftEngine block (not loaded until PAR-HEAT-19) no firepit can stand: TFC's fire fallback
                return blocks.exists(FirepitDevice.ID) && FirepitModule.canSurvive(at, tags);
            }

            @Override
            public boolean build(Block at, Axis axis, List<ItemStack> logs, Player player) {
                BlockStateRef state = BlockStateRef.of(FirepitDevice.ID).with(FirepitDevice.LIT, "false")
                    .with(FirepitDevice.SMOKE_LEVEL, "0").with(FirepitDevice.AXIS, axis == Axis.Z ? "z" : "x");
                if (!blocks.place(at, state, true)) {
                    return false;
                }
                device.build(at, logs, devices::neighbourChanged);
                return true;
            }
        };
    }

    /** The heating recipes as the firepit uses them. */
    static FirepitDevice.Cooker cooker(HeatingService heating) {
        return new FirepitDevice.Cooker() {
            @Override
            public float recipeTemperature(ItemStack stack) {
                return heating.recipe(stack).map(r -> r.temperature()).orElse(Float.NaN);
            }

            @Override
            public Optional<ItemStack> convert(ItemStack heated, float before) {
                return heating.convertIfReady(heated, before).map(HeatingService.Conversion::item);
            }
        };
    }

    /** TFC {@code SHOVEL_DOUSE} (vanilla and TFC shovels) and our water bucket. */
    static FirepitEffects.Douse douse(ItemStack stack, Tags tags) {
        if (stack.getType() == Material.WATER_BUCKET) {
            return FirepitEffects.Douse.WATER;
        }
        return tags.is(stack, SHOVELS) ? FirepitEffects.Douse.SHOVEL : FirepitEffects.Douse.NONE;
    }

    /** TFC {@code BottomSupportedDeviceBlock.canSurvive}: a sturdy top face or {@code #tfc:solid_top_face} below. */
    static boolean canSurvive(Block at, Tags tags) {
        Block below = at.getRelative(BlockFace.DOWN);
        return below.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL) || tags.is(below, SOLID_TOP_FACE);
    }

    /** TFC {@code level.isRainingAt(pos)} at the firepit: PAR-CLIM-02's rain behind vanilla's sky and height gates. */
    static boolean raining(ModuleContext context, Block block) {
        World w = block.getWorld();
        return context.services().get(PrecipitationService.class)
            .map(p -> p.isRainingAt(w, new BlockPos(block.getX(), block.getY(), block.getZ()),
                PrecipitationService.rainReaches(w, block.getX(), block.getY(), block.getZ())))
            .orElse(false);
    }

    /** The fuel index, rebuilt when the registries change version. Thread-safe. */
    static Supplier<FirepitFuels> fuels(Supplier<Registries> registries) {
        record Entry(long version, FirepitFuels fuels) {
        }
        AtomicReference<Entry> cache = new AtomicReference<>(new Entry(0, FirepitFuels.EMPTY));
        return () -> {
            Registries r = registries.get();
            if (r == null) {
                return FirepitFuels.EMPTY;
            }
            long version = r.version();
            Entry e = cache.get();
            if (e.version() == version) {
                return e.fuels();
            }
            FirepitFuels f = version == 0 ? FirepitFuels.EMPTY : FirepitFuels.fromRegistry(r.get());
            cache.set(new Entry(version, f));
            return f;
        };
    }
}
