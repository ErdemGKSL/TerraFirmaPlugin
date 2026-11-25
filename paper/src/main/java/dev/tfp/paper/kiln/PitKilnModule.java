/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart: a strong start on a kiln at stage 15
 *       tries to light it),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (pitKilnTicks 7200 >= 20, pitKilnTemperature
 *       1400 >= 0),
 *       src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536 (PIT_KILN_STRAW, PIT_KILN_4_STRAW, PIT_KILN_LOGS)
 */
package dev.tfp.paper.kiln;

import dev.tfp.core.catchup.LazyBlockIndex;
import dev.tfp.core.kiln.PitKilnLayers;
import dev.tfp.core.kiln.PitKilnRules;
import dev.tfp.core.kiln.PitKilnState;
import dev.tfp.core.placed.PlacedState;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.advancement.criteria.AdvancementTriggerEvent;
import dev.tfp.paper.advancement.criteria.CriteriaMap;
import dev.tfp.paper.catchup.CatchUpModule;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.fire.FireStarting;
import dev.tfp.paper.heat.HeatingService;
import dev.tfp.paper.heat.ItemHeatDefinitionsCache;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.placed.PaperPlacedItemEffects;
import dev.tfp.paper.placed.PlacedItemDevice;
import dev.tfp.paper.placed.PlacedItemEffects;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-POT-03: the pit kiln. Registers the device kind {@value PitKilnDevice#ID} ({@link PitKilnDevice}), the click
 * listener ({@link PitKilnListener}: straw on a placed item, straw, logs and take-back on a kiln), the
 * {@code FireLightable} (a strong fire start on a kiln with its eight logs), the {@link KilnClock} task and the chunk
 * hooks (a lit kiln catches up on chunk load). Needs the tags, device, catch-up, heating, fire and placed item modules
 * before it in the service file. The CraftEngine block is {@code configuration/kiln/pit_kiln.yml} (PAR-POT-14).
 *
 * <p>Config, read from the plugin's {@code config.yml} (shipped with the defaults since PAR-POT-14):
 * {@code pit-kiln.ticks} (TFC {@code pitKilnTicks}, default 7200, at least 20) and
 * {@code pit-kiln.temperature} (TFC {@code pitKilnTemperature}, default 1400, at least 0).
 */
public final class PitKilnModule implements FeatureModule {
    public static final String ID = "pit_kiln";
    static final String TICKS_KEY = "pit-kiln.ticks";
    static final String TEMPERATURE_KEY = "pit-kiln.temperature";
    static final String STRAW_TAG = "tfc:pit_kiln_straw";
    static final String STRAW_4_TAG = "tfc:pit_kiln_4_straw";
    static final String LOGS_TAG = "tfc:pit_kiln_logs";

    private volatile Scheduler.TaskHandle task;

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
        CustomItems items = context.integrations().customItems();
        Scheduler scheduler = context.scheduler();
        PitKilnRules.Config config = config(config(context.config().file()), context.logger());
        KilnClock clock = new KilnClock();
        PlacedItemEffects contents = new PaperPlacedItemEffects(stack -> items.idOf(stack).isEmpty() && stack.getType().isBlock());
        PitKilns kilns = new PitKilns(new PitKilns.Services(blocks, devices, () -> placedKind(devices),
            stack -> strawValue(tags, stack), stack -> tags.is(stack, LOGS_TAG),
            new PaperKilnItems(heating, ItemHeatHooks.Capacities.byId(items::idOf, heatDefinitions)),
            new PaperKilnStructure(), config, () -> context.clock().get().playerTicks(),
            new PaperKilnEffects(() -> context.integrations().blockVisuals()), contents, clock, scheduler));
        DeviceKind<PitKilnState> kind = devices.register(PitKilnDevice.kind(contents, kilns::later, clock));
        kilns.kind(kind);
        context.services().get(FireStarting.class).ifPresent(fire -> fire.register(ctx -> {
            boolean lit = ctx.strong() && kilns.stageAt(ctx.block()) == PitKilnLayers.LOG_END && kilns.tryLight(ctx.block());
            if (lit) {
                AdvancementTriggerEvent.publish(ctx.player(), CriteriaMap.PIT_KILN); // TFC LIT(pit_kiln)
            }
            return lit;
        }));
        context.listen(new PitKilnListener(kilns, blocks, scheduler));
        Optional<CatchUpService> catchUp = context.services().get(CatchUpService.class);
        blocks.onChunkLoad(chunk -> catchUp.ifPresent(c -> chunkLoaded(chunk, c, kilns, scheduler)));
        blocks.onChunkUnload(chunk -> clock.untrackChunk(chunk.getWorld(), chunk.getX(), chunk.getZ()));
        task = scheduler.globalRepeating(KilnClock.PERIOD_TICKS, KilnClock.PERIOD_TICKS,
            () -> period(clock, kilns, scheduler, context.clock().get().playerTicks()));
    }

    @Override
    public void disable() {
        Scheduler.TaskHandle t = task;
        task = null;
        if (t != null) {
            t.cancel();
        }
    }

    /** The due kilns go to their region threads. Global thread. */
    static void period(KilnClock clock, PitKilns kilns, Scheduler scheduler, long now) {
        for (Block block : clock.due(now)) {
            scheduler.region(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, () -> kilns.evaluate(block));
        }
    }

    /** The lit kilns of a loaded chunk are evaluated: one whose time is up fires, the others are listed on the clock. */
    static void chunkLoaded(Chunk chunk, CatchUpService catchUp, PitKilns kilns, Scheduler scheduler) {
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
            if (kilns.stageAt(block) == PitKilnLayers.LIT) {
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> kilns.evaluate(block));
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Optional<DeviceKind<PlacedState>> placedKind(DeviceService devices) {
        return devices.kinds().find(PlacedItemDevice.ID).filter(k -> k.id().equals(PlacedItemDevice.ID))
            .map(k -> (DeviceKind<PlacedState>) k);
    }

    /** TFC {@code PitKilnBlock.strawValue(item)}: the 4-straw tag first. */
    static int strawValue(Tags tags, ItemStack stack) {
        return PitKilnLayers.strawValue(tags.is(stack, STRAW_4_TAG), tags.is(stack, STRAW_TAG));
    }

    static YamlConfiguration config(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file.toFile());
    }

    /** TFC's config with its ranges; an out-of-range value falls back to the default with a warning. */
    static PitKilnRules.Config config(YamlConfiguration yaml, Logger logger) {
        long ticks = yaml.getLong(TICKS_KEY, PitKilnRules.DEFAULT_DURATION_TICKS);
        double temperature = yaml.getDouble(TEMPERATURE_KEY, PitKilnRules.DEFAULT_MAX_TEMPERATURE);
        if (ticks < 20 || ticks > Integer.MAX_VALUE) {
            logger.warning(TICKS_KEY + ": " + ticks + " is outside 20.." + Integer.MAX_VALUE + "; using "
                + PitKilnRules.DEFAULT_DURATION_TICKS);
            ticks = PitKilnRules.DEFAULT_DURATION_TICKS;
        }
        if (!(temperature >= 0) || temperature > Integer.MAX_VALUE) {
            logger.warning(TEMPERATURE_KEY + ": " + temperature + " is outside 0.." + Integer.MAX_VALUE + "; using "
                + PitKilnRules.DEFAULT_MAX_TEMPERATURE);
            temperature = PitKilnRules.DEFAULT_MAX_TEMPERATURE;
        }
        return new PitKilnRules.Config(ticks, (float) temperature);
    }
}
