/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (getCrankBlockEntity,
 *       isConnectedToNetwork), src/main/java/net/dries007/tfc/common/blockentities/rotation/PumpBlockEntity.java @ c3b4d9536
 *       (getCrankShaftAt(level, pos, face.getOpposite()))
 */
package dev.tfp.paper.crankshaft;

import dev.tfp.core.charcoalforge.BellowsRules;
import dev.tfp.core.crankshaft.CrankshaftRules;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.bellows.BellowsConsumers;
import dev.tfp.paper.bellows.BellowsDevice;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pump.ShaftSource;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * PAR-MECH-05: the crankshaft. Registers its device kind, provides the pump's {@link ShaftSource} and the bellows' crank lookup
 * ({@link BellowsDevice#crank}), and runs the powered bellows ({@link CrankshaftService}). The rotation a crank receives comes
 * from the {@link CrankRotation} service (the rotation network's crankshaft sink, PAR-MECH-21); the block itself is
 * {@code tfp:crankshaft} (PAR-MECH-21's CraftEngine block). Needs the device, tags and bellows modules before it.
 */
public final class CrankshaftModule implements FeatureModule {
    public static final String ID = "crankshaft";

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
        CrankshaftService service = new CrankshaftService(scheduler, blocks,
            () -> context.services().get(CrankRotation.class).orElse(CrankRotation.NONE),
            () -> context.services().get(BellowsConsumers.class), CrankshaftService.SOUNDS);
        CrankshaftChunkIndex index = new CrankshaftChunkIndex(context.chunkData());
        CrankshaftDevices devices = new CrankshaftDevices(service, index, blocks, scheduler,
            stack -> tags.is(stack, CrankshaftRules.STEEL_RODS));
        deviceService.register(devices.kind());
        context.services().provide(CrankshaftService.class, service);
        context.services().provide(ShaftSource.class, shaftSource(service, blocks, scheduler));
        context.services().get(BellowsDevice.class).ifPresent(b -> b.crank(crank(service, blocks, scheduler)));
        blocks.onChunkLoad(chunk -> chunkLoaded(chunk, blocks, index, service));
        blocks.onChunkUnload(chunk -> index.positions(chunk).forEach(pos -> service.stop(chunk.getWorld().getUID(), pos)));
        // Chunks loaded before this module enabled get no chunk load event (as PAR-MECH-11 for the rotation blocks).
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

    /** TFC PumpBlockEntity: the crank behind the pump's {@code side}, its node's rotation, null without one in a network. */
    static ShaftSource shaftSource(CrankshaftService service, CustomBlocks blocks, Scheduler scheduler) {
        return (pump, side) -> CrankshaftBlocks.crankAt(pump, side, blocks, scheduler).flatMap(service::rotation).orElse(null);
    }

    /** TFC BellowsBlockEntity: the crank behind the bellows (its facing's opposite): none, idle, or turning. */
    static java.util.function.Function<Block, BellowsRules.Crank> crank(CrankshaftService service, CustomBlocks blocks,
            Scheduler scheduler) {
        return bellows -> {
            Optional<Direction> facing = blocks.stateAt(bellows).map(BlockStateRef::properties)
                .flatMap(p -> CrankshaftBlocks.horizontal(p.get(CrankshaftService.BELLOWS_FACING)));
            if (facing.isEmpty()) {
                return BellowsRules.Crank.NONE;
            }
            Optional<Block> base = CrankshaftBlocks.crankAt(bellows, facing.get().opposite(), blocks, scheduler);
            if (base.isEmpty()) {
                return BellowsRules.Crank.NONE;
            }
            return service.rotation(base.get()).isPresent() ? BellowsRules.Crank.TURNING : BellowsRules.Crank.IDLE;
        };
    }

    /** Starts the listed bases of a loaded chunk and forgets stale entries. Region thread of the chunk. */
    static void chunkLoaded(Chunk chunk, CustomBlocks blocks, CrankshaftChunkIndex index, CrankshaftService service) {
        World world = chunk.getWorld();
        for (BlockPos pos : index.positions(chunk)) {
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            boolean base = blocks.stateAt(block).flatMap(CrankshaftBlocks::piece)
                .filter(p -> p.part() == CrankshaftRules.Part.BASE).isPresent();
            if (base) {
                service.start(block);
            } else {
                index.untrack(block);
            }
        }
    }
}
