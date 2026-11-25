/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weathering;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.weathering.WeatheringExposure;
import dev.tfp.core.weathering.WeatheringRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.worldgen.Mth;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.climate.ChunkClimateSource;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.windmill.WindmillWind;
import dev.tfp.paper.worldgen.WorldSettingsLookup;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * PAR-METAL-17: wires {@link WeatheringService}: the placement listener, the chunk load hook, the sweep, and
 * {@code /tfp debug weathering} (lists the block looked at and gives its chunk a pass now).
 */
public final class WeatheringModule implements FeatureModule {
    public static final String ID = "weathering";
    /** How far {@code /tfp debug weathering} looks. */
    static final int REACH = 8;

    private Scheduler scheduler;
    private CustomBlocks blocks;
    private WeatheringService service;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        scheduler = context.scheduler();
        blocks = context.integrations().customBlocks();
        WeatheringService.Writer writer = (block, next) -> {
            Optional<org.bukkit.block.data.BlockData> data = context.integrations().blockVisuals().blockData(next);
            if (data.isPresent()) {
                block.setBlockData(data.get(), false);
            } else {
                blocks.place(block, next, false);
            }
        };
        service = new WeatheringService(scheduler, blocks, writer, new WeatheringChunk(context.chunkData()), context.clock(),
            WeatheringModule::climate, 3);
        context.services().provide(WeatheringService.class, service);
        context.listen(new Placement());
        blocks.onChunkLoad(service::onLoad);
        blocks.onChunkUnload(service::onUnload);
        scheduler.globalRepeating(WeatheringService.SWEEP_PERIOD, WeatheringService.SWEEP_PERIOD, () -> {
            Map<UUID, World> worlds = new HashMap<>();
            context.plugin().getServer().getWorlds().forEach(w -> worlds.put(w.getUID(), w));
            service.sweep(worlds);
        });
    }

    /** TFC {@code climateModifier} at the block, from the plugin's climate model; 0 outside the overworld. */
    static float climate(World world, BlockPos pos, ClockSnapshot clock, WeatheringService.WindInputs wind) {
        if (world.getEnvironment() != World.Environment.NORMAL || !clock.isSet()) {
            return 0f;
        }
        try {
            long seed = world.getSeed();
            OverworldClimate climate = WorldSettingsLookup.climate(world);
            ChunkClimate chunk = ChunkClimateSource.climateAt(seed, pos.x(), pos.z());
            long ticks = clock.calendarTicks();
            int days = clock.daysInMonth();
            float rainfall = climate.instantRainfall(chunk, ticks, days);
            // TFC WeatherHelpers.isPrecipitating, as WindmillWind
            boolean raining = climate.rainIntensity(ticks) - Mth.clampedMap(rainfall, OverworldClimate.MIN_AVERAGE_RAINFALL,
                OverworldClimate.MAX_AVERAGE_RAINFALL, 1f, 0f) > 0;
            float windLength = WindmillWind.wind(new WindmillWind.Capture(seed, true, pos, wind.ocean(), wind.daytime(), clock));
            return WeatheringExposure.climateModifier(climate.instantTemperature(pos, chunk, ticks, days), chunk.averageRainfall(),
                windLength, raining);
        } catch (RuntimeException e) {
            return 0f; // no TFC climate for this world
        }
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("weathering", r -> r.executes(tree.player((player, ctx) -> {
            CompletableFuture<Reply> out = new CompletableFuture<>();
            boolean scheduled = scheduler.entity(player, () -> {
                try {
                    out.complete(report(player));
                } catch (RuntimeException e) {
                    out.completeExceptionally(e);
                }
            }, () -> out.complete(Reply.error("command.player_not_found", player.getName())));
            if (!scheduled) {
                out.complete(Reply.error("command.player_not_found", player.getName()));
            }
            return out;
        })));
    }

    /** Player thread: lists the block looked at, then hands its chunk a pass on its region. */
    Reply report(Player player) {
        Block target = player.getTargetBlockExact(REACH);
        Optional<BlockStateRef> state = target == null ? Optional.empty() : blocks.stateAt(target);
        Optional<WeatheringIds.Kind> kind = state.flatMap(s -> WeatheringIds.parse(s.id()));
        if (kind.isEmpty()) {
            return Reply.error("debug.weathering.none", REACH);
        }
        scheduler.region(target.getLocation(), () -> {
            service.track(target);
            service.pass(target.getChunk(), 1L);
        });
        return Reply.ok("debug.weathering.done", target.getX(), target.getY(), target.getZ(), state.get().id(),
            kind.get().age().ordinal(), WeatheringRules.Age.values().length - 1);
    }

    /** Lists a weathering block as soon as it is placed. */
    public final class Placement implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block block = event.getBlockPlaced();
            scheduler.region(block.getLocation(), () -> {
                if (service.weathers(block)) {
                    service.track(block);
                }
            });
        }
    }
}
