/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start on an unlit bloomery
 *       lights it),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (bloomeryCapacity 16 >= 1,
 *       bloomeryMaxChimneyHeight 3 >= 1)
 */
package dev.tfp.paper.bloomery;

import dev.tfp.core.bloomery.BloomeryRules;
import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.heat.HeatPdc;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
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
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-14: the bloomery and the bloom. Registers the device kinds {@code tfp:bloomery} and {@code tfp:bloom}, the fire
 * lightable (a strong start on an unlit bloomery lights it from the contents), the breaking of a bloom
 * ({@link BloomListener}), the intake after a thrown item ({@link BloomeryDropListener}) and a chunk-load callback for lit
 * bloomeries (a burn that ended while unloaded shows on load). Needs the tags, device, heating, recipe and fire modules
 * before it in the service file. TFC's bloomery is not a bellows consumer, so none is registered.
 *
 * <p>Config ({@code config.yml}): {@code bloomery.capacity} (items per level of chimney, TFC {@code bloomeryCapacity}, 16)
 * and {@code bloomery.max-chimney-height} (TFC {@code bloomeryMaxChimneyHeight}, 3).
 */
public final class BloomeryModule implements FeatureModule {
    public static final String ID = "bloomery";
    public static final String CAPACITY_KEY = "bloomery.capacity";
    public static final String HEIGHT_KEY = "bloomery.max-chimney-height";
    /** The most chimney levels the config may ask for (TFC has no upper bound; a chimney this tall is not playable). */
    static final int MAX_HEIGHT = 16;

    private volatile Scheduler.TaskHandle sweep;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext ctx) {
        DeviceService devs = ctx.services().require(DeviceService.class);
        Tags tags = ctx.services().require(Tags.class);
        HeatingService heating = ctx.services().require(HeatingService.class);
        Recipes recipes = ctx.services().require(Recipes.class);
        CustomBlocks blocks = ctx.integrations().customBlocks();
        CustomItems items = ctx.integrations().customItems();
        Scheduler sch = ctx.scheduler();
        BloomeryRules.Config config = config(config(ctx.config().file()), ctx.logger());
        Supplier<BloomeryRules.Config> cfg = () -> config;
        BloomeryProbe probe = new PaperBloomeryProbe(blocks, tags, sch);
        BloomeryBook book = new BloomeryBook(recipes);

        BloomDevice[] bloomRef = new BloomDevice[1];
        PaperBloomeryEffects effects = new PaperBloomeryEffects(sch, blocks, items,
            () -> ctx.integrations().blockVisuals(), () -> ctx.clock().get().playerTicks(), devs::neighbourChanged,
            (inside, item, count) -> bloomRef[0].seed(devs, inside, item, count));
        sweep = effects.start();
        BloomDevice bloom = new BloomDevice(devs.stacks(), effects, probe);
        bloomRef[0] = bloom;
        BloomeryDevice device = new BloomeryDevice(new BloomeryDevice.Services(outputs(tags, heating, items),
            book::get, cfg, probe, effects, blocks::stateAt), devs.stacks());
        devs.register(device.kind());
        devs.register(bloom.kind());
        ctx.services().provide(BloomeryDevice.class, device);
        ctx.services().provide(BloomDevice.class, bloom);
        ctx.services().get(FireStarting.class).ifPresent(fire -> fire.register(fc -> fc.strong()
            && lightable(blocks, devs, device, fc.block())));
        Optional<CatchUpService> catchUp = ctx.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, blocks, devs, sch)));
        ctx.listen(new BloomListener(blocks, devs, bloom));
        ctx.listen(new BloomeryDropListener(blocks, devs, sch, cfg));
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle s = sweep;
        if (s != null) {
            s.cancel();
        }
        sweep = null;
    }

    /** TFC {@code onFireStart}: a strong start on an unlit bloomery lights it when a recipe matches its contents. */
    static boolean lightable(CustomBlocks blocks, DeviceService devices, BloomeryDevice device, Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !state.get().id().equals(BloomeryDevice.ID) || BloomeryDevice.lit(state.get())) {
            return false;
        }
        return device.lightAt(devices, block);
    }

    /** The item side of the device on the live plugin. */
    static BloomeryDevice.Outputs outputs(Tags tags, HeatingService heating, CustomItems items) {
        return new BloomeryDevice.Outputs() {
            @Override
            public Optional<String> itemId(ItemStack stack) {
                return tags.itemId(stack);
            }

            @Override
            public BloomeryDevice.Melt melt(ItemStack stack) {
                return heating.recipe(stack).flatMap(r -> HeatingService.fluid(r, stack))
                    .map(f -> new BloomeryDevice.Melt(f.fluid(), f.amount())).orElse(BloomeryDevice.Melt.NONE);
            }

            @Override
            public Optional<ItemStack> create(String id) {
                return items.create(id, 1);
            }

            @Override
            public Optional<Float> meltTemperature(ItemStack stack) {
                return heating.recipe(stack).map(r -> r.temperature());
            }

            @Override
            public void heat(ItemStack stack, float temperature, long now) {
                HeatPdc.write(stack, HeatState.cold(now).set(temperature, now));
            }
        };
    }

    /** Lit bloomeries of a loaded chunk get a callback: one whose burn ended meanwhile completes, the rest re-queue. */
    static void chunkLoaded(Chunk chunk, CatchUpService catchUp, CustomBlocks blocks, DeviceService devices,
            Scheduler scheduler) {
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
            boolean lit = blocks.stateAt(block).map(s -> s.id().equals(BloomeryDevice.ID) && BloomeryDevice.lit(s)).orElse(false);
            if (lit) {
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> devices.neighbourChanged(block));
            }
        }
    }

    static YamlConfiguration config(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    /** TFC's config with its ranges; an out-of-range value falls back to the default with a warning. */
    static BloomeryRules.Config config(YamlConfiguration yaml, Logger logger) {
        int capacity = yaml.getInt(CAPACITY_KEY, BloomeryRules.DEFAULT_CAPACITY_PER_LEVEL);
        int height = yaml.getInt(HEIGHT_KEY, BloomeryRules.DEFAULT_MAX_CHIMNEY_HEIGHT);
        if (capacity < 1 || capacity > BloomeryDevice.SLOTS) {
            logger.warning(CAPACITY_KEY + ": " + capacity + " is outside 1.." + BloomeryDevice.SLOTS + "; using "
                + BloomeryRules.DEFAULT_CAPACITY_PER_LEVEL);
            capacity = BloomeryRules.DEFAULT_CAPACITY_PER_LEVEL;
        }
        if (height < 1 || height > MAX_HEIGHT) {
            logger.warning(HEIGHT_KEY + ": " + height + " is outside 1.." + MAX_HEIGHT + "; using "
                + BloomeryRules.DEFAULT_MAX_CHIMNEY_HEIGHT);
            height = BloomeryRules.DEFAULT_MAX_CHIMNEY_HEIGHT;
        }
        return new BloomeryRules.Config(capacity, height);
    }

    static List<String> keys() {
        return List.of(CAPACITY_KEY, HEIGHT_KEY);
    }
}
