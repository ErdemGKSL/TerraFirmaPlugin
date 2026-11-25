/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.waterwheel;

import dev.tfp.core.waterwheel.WaterWheelSample;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.worldwater.WorldWaterService;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

/**
 * PAR-MECH-03: the water wheels. Registers their device kind, samples every placed or loaded wheel every 40 ticks
 * ({@link WaterWheelService}) and reports its speed to the {@link WaterWheelNetwork} service (provided by the rotation module,
 * PAR-MECH-17). Reads which TFC water a block stands for from {@link WorldWaterService} when the world water module runs.
 * Needs the device module before it in the service file.
 */
public final class WaterWheelModule implements FeatureModule {
    public static final String ID = "water_wheel";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService deviceService = context.services().require(DeviceService.class);
        Scheduler scheduler = context.scheduler();
        CustomBlocks blocks = context.integrations().customBlocks();
        WaterWheelChunkIndex index = new WaterWheelChunkIndex(context.chunkData());
        AtomicReference<DeviceKind<WaterWheelSample>> kind = new AtomicReference<>();
        WaterWheelService.Records records = new WaterWheelService.Records() {
            @Override
            public WaterWheelSample load(Block block) {
                return deviceService.inspect(block).flatMap(DeviceService.Inspection::record)
                    .map(r -> r.state() instanceof WaterWheelSample s ? s : null).orElse(WaterWheelSample.FRESH);
            }

            @Override
            public void save(Block block, WaterWheelSample next) {
                deviceService.edit(block, kind.get(), (r, now) -> r.withState(next));
            }
        };
        WaterWheelService service = new WaterWheelService(scheduler, blocks,
            WaterWheelCells.live(scheduler, () -> context.services().get(WorldWaterService.class)), records,
            () -> context.services().get(WaterWheelNetwork.class).orElse(WaterWheelNetwork.NONE));
        WaterWheelDevices devices = new WaterWheelDevices(service, index, (block, expected, next) ->
            scheduler.regionDelayed(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 1, () -> {
                if (blocks.stateAt(block).filter(expected::equals).isPresent()) {
                    context.integrations().blockVisuals().blockData(next).ifPresent(d -> block.setBlockData(d, false));
                }
            }));
        kind.set(devices.kind());
        deviceService.register(devices.kind());
        context.services().provide(WaterWheelService.class, service);
        blocks.onChunkLoad(chunk -> chunkLoaded(chunk, blocks, index, service));
        blocks.onChunkUnload(chunk -> index.positions(chunk).forEach(pos -> service.stop(chunk.getWorld().getUID(), pos)));
        // Chunks loaded before this module enabled (spawn chunks) get no chunk load event: start their wheels once the server
        // is up, on each chunk's region thread (as PAR-MECH-11 does for the rotation blocks).
        scheduler.global(() -> context.plugin().getServer().getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                int cx = chunk.getX();
                int cz = chunk.getZ();
                scheduler.region(world, cx, cz, () -> {
                    if (world.isChunkLoaded(cx, cz)) {
                        chunkLoaded(world.getChunkAt(cx, cz), blocks, index, service);
                    }
                });
            }
        }));
    }

    /** Starts the listed wheels of a loaded chunk and forgets stale entries. Region thread of the chunk. */
    static void chunkLoaded(Chunk chunk, CustomBlocks blocks, WaterWheelChunkIndex index, WaterWheelService service) {
        for (BlockPos pos : index.positions(chunk)) {
            Block block = chunk.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
            Optional<BlockStateRef> state = blocks.stateAt(block);
            if (state.isPresent() && WaterWheelBlocks.isWheel(state.get().id())) {
                service.start(block);
            } else {
                index.untrack(block);
            }
        }
    }
}
