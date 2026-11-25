/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.triphammer;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.rotation.RotationService;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * PAR-MECH-07: the trip hammer. Registers its device kind ({@link TripHammerDevice}), runs the hammers
 * ({@link TripHammerService}) from the rotation of the bladed axle above ({@link RotationService}, looked up lazily) onto the
 * metal anvil in front ({@link AnvilRemote}, provided by the anvil module, PAR-METAL-28), restarts the listed hammers when their
 * chunk loads and stops them when it unloads. The block itself is {@code tfp:trip_hammer} (PAR-MECH-27). Needs the device and
 * tags modules before it.
 */
public final class TripHammerModule implements FeatureModule {
    public static final String ID = "trip_hammer";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService deviceService = context.services().require(DeviceService.class);
        Tags tags = context.services().require(Tags.class);
        Scheduler scheduler = context.scheduler();
        CustomBlocks blocks = context.integrations().customBlocks();
        TripHammerService service = new TripHammerService(scheduler, blocks,
            () -> context.services().get(RotationService.class),
            () -> context.services().get(AnvilRemote.class).orElse(AnvilRemote.NONE), TripHammerService.LIVE);
        TripHammerChunkIndex index = new TripHammerChunkIndex(context.chunkData());
        TripHammerDevice device = new TripHammerDevice(service, index, blocks, scheduler,
            stack -> tags.is(stack, TripHammerBlocks.TRIP_HAMMERS), writer(() -> context.integrations().blockVisuals()));
        service.bind(deviceService, deviceService.register(device.kind()));
        context.services().provide(TripHammerService.class, service);
        context.listen(device.new Placement());
        blocks.onChunkLoad(chunk -> chunkLoaded(chunk, blocks, index, service));
        blocks.onChunkUnload(chunk -> index.positions(chunk).forEach(pos -> service.stop(chunk.getWorld().getUID(), pos)));
        // Chunks loaded before this module enabled get no chunk load event.
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

    /** The live state writer: the block data of the state, set without physics. */
    static BiConsumer<Block, BlockStateRef> writer(Supplier<BlockVisuals> visuals) {
        return (block, state) -> {
            Optional<BlockData> data = visuals.get().blockData(state);
            data.ifPresent(d -> block.setBlockData(d, false));
        };
    }

    /** Starts the listed hammers of a loaded chunk and forgets stale entries. Region thread of the chunk. */
    static void chunkLoaded(Chunk chunk, CustomBlocks blocks, TripHammerChunkIndex index, TripHammerService service) {
        World world = chunk.getWorld();
        for (BlockPos pos : index.positions(chunk)) {
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (blocks.stateAt(block).flatMap(TripHammerBlocks::facing).isPresent()) {
                service.start(block);
            } else {
                index.untrack(block);
            }
        }
    }
}
