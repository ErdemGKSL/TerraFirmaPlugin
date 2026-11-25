/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pump;

import dev.tfp.core.machine.PumpRules;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.DebugTree;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * PAR-MECH-09: the steel pipe and the steel pump. Registers their device kinds, starts every pump (placed, or in a chunk that
 * loads) on a {@link PumpRules#FLOW_PERIOD_TICKS}-tick region task, and adds {@code /tfp debug pump}: what the pump the player
 * looks at would do this run if its crankshaft turned. A pump is powered by the {@link ShaftSource} service the crankshaft
 * module (PAR-MECH-05) provides; without it no pump runs. Needs the device module before it in the service file.
 */
public final class PumpModule implements FeatureModule {
    public static final String ID = "pump";
    /** How far {@code /tfp debug pump} looks. */
    static final int REACH = 8;

    private Scheduler scheduler;
    private CustomBlocks blocks;
    private PumpService service;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        DeviceService deviceService = context.services().require(DeviceService.class);
        scheduler = context.scheduler();
        blocks = context.integrations().customBlocks();
        PumpChunkIndex index = new PumpChunkIndex(context.chunkData());
        BlockWriter writer = BlockWriter.visuals(() -> context.integrations().blockVisuals());
        service = new PumpService(scheduler, blocks, writer,
            () -> context.services().get(ShaftSource.class));
        PumpDevices devices = new PumpDevices(scheduler, blocks, writer, service, index);
        devices.kinds().forEach(deviceService::register);
        context.services().provide(PumpService.class, service);
        context.listen(devices.new Placement());
        blocks.onChunkLoad(chunk -> {
            for (BlockPos pos : index.positions(chunk)) {
                Block block = chunk.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
                Optional<BlockStateRef> state = blocks.stateAt(block);
                if (state.isPresent() && state.get().id().equals(PumpBlockStates.PUMP)) {
                    service.start(block);
                } else {
                    index.untrack(block);
                }
            }
        });
        blocks.onChunkUnload(chunk -> index.positions(chunk).forEach(pos -> service.stop(chunk.getWorld().getUID(), pos)));
    }

    @Override
    public void debugCommands(DebugTree tree) {
        tree.add("pump", r -> r.executes(tree.player((player, ctx) -> {
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

    /** Player thread: the pump looked at, as one run would see it with a turning crankshaft (nothing is written). */
    Reply report(Player player) {
        Block target = player.getTargetBlockExact(REACH);
        Optional<BlockStateRef> state = target == null ? Optional.empty() : blocks.stateAt(target);
        if (state.isEmpty() || !state.get().id().equals(PumpBlockStates.PUMP)) {
            return Reply.error("debug.pump.none", REACH);
        }
        BlockPos pos = PumpDevices.pos(target);
        Direction facing = PumpBlockStates.facing(state.get());
        PumpWorld reader = new PumpWorld(scheduler, blocks, (b, n) -> { }, target.getWorld());
        Optional<String> fluid = PumpRules.sourceFluid(reader, pos);
        int changes = PumpRules.tick(reader, pos, facing, Rotation.of(facing.opposite(), 1f), 0L).size();
        Rotation shaft = service.shaft(target, facing);
        return Reply.ok("debug.pump.done", pos.x(), pos.y(), pos.z(), PumpBlockStates.name(facing), fluid.orElse("-"), changes,
            shaft == null ? "-" : Float.toString(shaft.speed()));
    }
}
