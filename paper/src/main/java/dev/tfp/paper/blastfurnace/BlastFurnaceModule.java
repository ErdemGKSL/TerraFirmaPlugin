/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start on an unlit blast
 *       furnace lights it),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (blastFurnaceCapacity 4,
 *       blastFurnaceFluidCapacity 10000, blastFurnaceFuelConsumptionMultiplier 4, blastFurnaceMaxChimneyHeight 5)
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceRules;
import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.crucible.CrucibleRecipe;
import dev.tfp.core.firepit.FirepitFuels;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.bellows.BellowsConsumers;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.firebox.HeatConsumers;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-15: the blast furnace. Registers the device kind {@code tfp:blast_furnace}, the fire lightable (a strong start
 * on an unlit blast furnace lights it from its fuel), the bellows consumer (TFC's blast furnace is an
 * {@code IBellowsConsumer}: it takes air only with a tuyere), the screen ({@link BlastFurnaceScreens}), the intake after a
 * thrown item ({@link BlastFurnaceDropListener}) and a chunk-load callback for hot furnaces (a burn that ended while unloaded
 * shows on load). Needs the tags, device, heating, recipe, fire, firebox, crucible and bellows modules before it in the
 * service file.
 *
 * <p>Config ({@code config.yml}): {@code blast-furnace.capacity} (items per level of chimney, TFC
 * {@code blastFurnaceCapacity}, 4), {@code blast-furnace.max-chimney-height} (TFC {@code blastFurnaceMaxChimneyHeight}, 5),
 * {@code blast-furnace.fluid-capacity} (TFC {@code blastFurnaceFluidCapacity}, 10000 mB) and
 * {@code blast-furnace.fuel-consumption-multiplier} (TFC {@code blastFurnaceFuelConsumptionMultiplier}, 4).
 */
public final class BlastFurnaceModule implements FeatureModule {
    public static final String ID = "blastfurnace";
    public static final String CAPACITY_KEY = "blast-furnace.capacity";
    public static final String HEIGHT_KEY = "blast-furnace.max-chimney-height";
    public static final String FLUID_KEY = "blast-furnace.fluid-capacity";
    public static final String MULTIPLIER_KEY = "blast-furnace.fuel-consumption-multiplier";
    public static final String FUEL_TAG = "tfc:blast_furnace_fuel";
    public static final String TUYERE_TAG = "tfc:blast_furnace_tuyeres";

    private volatile Scheduler.TaskHandle sweep;
    private final java.util.List<AutoCloseable> closers = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devs = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        HeatingService heating = ctx.services().require(HeatingService.class);
        ItemHeatDefinitionsCache definitions = ctx.services().require(ItemHeatDefinitionsCache.class);
        HeatConsumers consumers = ctx.services().require(HeatConsumers.class);
        Recipes recipes = ctx.services().require(Recipes.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        Scheduler sch = ctx.scheduler();
        BlastFurnaceRules.Config config = config(config(ctx.config().file()), ctx.logger());
        Supplier<BlastFurnaceRules.Config> cfg = () -> config;
        Supplier<FirepitFuels> fuels = fuels(() -> ctx.registryService().registries());
        BlastFurnaceProbe probe = new PaperBlastFurnaceProbe(blocks, tags, sch);
        PaperBlastFurnaceEffects effects = new PaperBlastFurnaceEffects(sch, blocks, () -> ctx.integrations().blockVisuals(),
            () -> ctx.clock().get().playerTicks(), devs::neighbourChanged);
        sweep = effects.start();
        BlastFurnaceBook book = new BlastFurnaceBook(recipes);
        BlastFurnaceDevice device = new BlastFurnaceDevice(new BlastFurnaceDevice.Services(
            stack -> tags.itemId(stack),
            stack -> melt(heating, tags, stack),
            stack -> tags.itemId(stack).flatMap(id -> fuels.get().get(id)),
            stack -> tags.is(stack, FUEL_TAG),
            stack -> tags.is(stack, TUYERE_TAG),
            ItemHeatHooks.Capacities.byId(tags::itemId, definitions),
            HeatConfig.DEFAULT, book::get, cfg, probe, effects,
            new PaperBlastFurnaceBelow(devs, blocks, consumers, sch), blocks::stateAt), devs.stacks());
        devs.register(device.kind());
        ctx.services().provide(BlastFurnaceDevice.class, device);

        ctx.services().get(BellowsConsumers.class).ifPresentOrElse(bellows -> {
            try {
                closers.add(bellows.register(BlastFurnaceDevice.ID, new BellowsConsumers.Consumer() {
                    @Override
                    public boolean canAcceptAir(Block block) {
                        return device.canAcceptAir(devs, block);
                    }

                    @Override
                    public void intakeAir(Block block, int amount) {
                        device.intakeAir(devs, block, amount);
                    }
                }));
            } catch (IllegalArgumentException e) {
                ctx.logger().warning("blastfurnace: " + e.getMessage());
            }
        }, () -> ctx.logger().warning("blastfurnace: no BellowsConsumers; bellows do not blow into blast furnaces"));

        BlastFurnaceScreens screens = new BlastFurnaceScreens(device, devs, sch, ctx.messages());
        device.screen((player, block) -> sch.entityDelayed(player, 1, () -> {
            if (player.isOnline()) {
                screens.open(player, block);
            }
        }, () -> { }));
        ctx.listen(screens);
        ctx.services().get(FireStarting.class).ifPresent(fire -> fire.register(fc -> fc.strong()
            && lightable(blocks, devs, device, fc.block())));
        Optional<CatchUpService> catchUp = ctx.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, blocks, devs, sch)));
        ctx.listen(new BlastFurnaceDropListener(blocks, devs, sch, cfg));
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle s = sweep;
        if (s != null) {
            s.cancel();
        }
        sweep = null;
        for (AutoCloseable c : closers) {
            try {
                c.close();
            } catch (Exception e) {
                // closing a registration of a stopping module: nothing to do
            }
        }
        closers.clear();
    }

    /** TFC {@code onFireStart}: a strong start on an unlit blast furnace lights it when it holds fuel. */
    static boolean lightable(CustomBlocks blocks, DeviceService devices, BlastFurnaceDevice device, Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !state.get().id().equals(BlastFurnaceDevice.ID) || BlastFurnaceDevice.lit(state.get())) {
            return false;
        }
        return device.lightAt(devices, block);
    }

    /** The heating recipe of a stack as the rules see it. */
    static Optional<CrucibleRecipe> melt(HeatingService heating, Tags tags, ItemStack stack) {
        return heating.recipe(stack).map(r -> {
            String fluid = r.resultFluid().map(f -> f.fluid()).orElse("");
            int amount = r.resultFluid().map(f -> f.amount()).orElse(0);
            return new CrucibleRecipe(tags.itemId(stack).orElse(stack.getType().getKey().toString()), r.temperature(), fluid,
                amount, r.useDurability(), r.hasItemResult());
        });
    }

    /** Hot furnaces of a loaded chunk get a callback: one whose fuel ran out meanwhile goes out, the rest re-queue. */
    static void chunkLoaded(Chunk chunk, CatchUpService catchUp, CustomBlocks blocks, DeviceService devices, Scheduler scheduler) {
        Optional<LazyBlockIndex> index = catchUp.index(chunk);
        if (index.isEmpty()) {
            return;
        }
        World world = chunk.getWorld();
        for (Map.Entry<BlockPos, String> e : index.get().entries().entrySet()) {
            if (!CatchUpModule.DEVICE_KIND.equals(e.getValue())) {
                continue;
            }
            BlockPos p = e.getKey();
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            boolean isFurnace = blocks.stateAt(block).map(s -> s.id().equals(BlastFurnaceDevice.ID)).orElse(false);
            if (isFurnace) {
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> devices.neighbourChanged(block));
            }
        }
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

    static YamlConfiguration config(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    /** TFC's config with its ranges; an out-of-range value falls back to the default with a warning. */
    static BlastFurnaceRules.Config config(YamlConfiguration yaml, Logger logger) {
        int capacity = yaml.getInt(CAPACITY_KEY, BlastFurnaceRules.DEFAULT_CAPACITY_PER_LEVEL);
        int height = yaml.getInt(HEIGHT_KEY, BlastFurnaceRules.DEFAULT_MAX_CHIMNEY_HEIGHT);
        int fluid = yaml.getInt(FLUID_KEY, BlastFurnaceRules.DEFAULT_FLUID_CAPACITY);
        int multiplier = yaml.getInt(MULTIPLIER_KEY, BlastFurnaceRules.DEFAULT_FUEL_MULTIPLIER);
        if (capacity < 1 || height < 1 || capacity * (long) height > BlastFurnaceDevice.GROUP) {
            logger.warning(CAPACITY_KEY + " x " + HEIGHT_KEY + ": " + capacity + " x " + height + " must be 1.. and at most "
                + BlastFurnaceDevice.GROUP + " items; using " + BlastFurnaceRules.DEFAULT_CAPACITY_PER_LEVEL + " x "
                + BlastFurnaceRules.DEFAULT_MAX_CHIMNEY_HEIGHT);
            capacity = BlastFurnaceRules.DEFAULT_CAPACITY_PER_LEVEL;
            height = BlastFurnaceRules.DEFAULT_MAX_CHIMNEY_HEIGHT;
        }
        if (fluid < 1) {
            logger.warning(FLUID_KEY + ": " + fluid + " is below 1; using " + BlastFurnaceRules.DEFAULT_FLUID_CAPACITY);
            fluid = BlastFurnaceRules.DEFAULT_FLUID_CAPACITY;
        }
        if (multiplier < 1) {
            logger.warning(MULTIPLIER_KEY + ": " + multiplier + " is below 1; using " + BlastFurnaceRules.DEFAULT_FUEL_MULTIPLIER);
            multiplier = BlastFurnaceRules.DEFAULT_FUEL_MULTIPLIER;
        }
        return new BlastFurnaceRules.Config(capacity, height, fluid, multiplier);
    }

    static List<String> keys() {
        return List.of(CAPACITY_KEY, HEIGHT_KEY, FLUID_KEY, MULTIPLIER_KEY);
    }
}
