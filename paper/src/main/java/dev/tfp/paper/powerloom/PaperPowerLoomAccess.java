/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.powerloom;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.powerloom.PowerLoomFacing;
import dev.tfp.core.powerloom.PowerLoomState;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.powerloom.PowerLoomIndex.Key;
import dev.tfp.paper.rotation.RotationBlockStates;
import dev.tfp.paper.rotation.RotationService;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;

/** The server side of {@link PowerLoomAccess}: the device service, the rotation actor, the region scheduler. */
final class PaperPowerLoomAccess implements PowerLoomAccess {
    private final Scheduler scheduler;
    private final DeviceService devices;
    private final CustomBlocks blocks;
    private final RotationService networks;
    private final PowerLoomDevice device;
    private final PowerLoomIndex index;
    private final Function<UUID, World> worlds;

    PaperPowerLoomAccess(Scheduler scheduler, DeviceService devices, CustomBlocks blocks, RotationService networks,
            PowerLoomDevice device, PowerLoomIndex index, Function<UUID, World> worlds) {
        this.scheduler = scheduler;
        this.devices = devices;
        this.blocks = blocks;
        this.networks = networks;
        this.device = device;
        this.index = index;
        this.worlds = worlds;
    }

    private Optional<Block> block(Key key) {
        World world = worlds.apply(key.world());
        return world == null ? Optional.empty() : Optional.of(world.getBlockAt(key.x(), key.y(), key.z()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<View> view(Key key) {
        Optional<Block> block = block(key);
        if (block.isEmpty()) {
            return Optional.empty();
        }
        Optional<DeviceService.Inspection> at = devices.inspect(block.get());
        if (at.isEmpty() || !PowerLoomDevice.ID.equals(at.get().kindId()) || at.get().record().isEmpty()) {
            return Optional.empty();
        }
        PowerLoomState state = ((DeviceRecord<PowerLoomState>) at.get().record().get()).state();
        return Optional.of(new View(state.active(), state.pushes()));
    }

    @Override
    public CompletableFuture<Optional<Float>> speed(Key key) {
        Optional<Block> block = block(key);
        if (block.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Optional<BlockStateRef> state = blocks.stateAt(block.get());
        Optional<Direction> face = state.flatMap(s -> PowerLoomFacing.sinkFace(s.properties().get(PowerLoomDevice.FACING)));
        if (face.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        // the neighbour may be in the next chunk; loaded neighbours share a region in practice, one that does not is not read
        Block side = block.get().getRelative(face.get().dx(), face.get().dy(), face.get().dz());
        if (!scheduler.isOwner(side.getWorld(), side.getX() >> 4, side.getZ() >> 4)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        BlockPos sidePos = new BlockPos(side.getX(), side.getY(), side.getZ());
        boolean connects = blocks.stateAt(side).flatMap(s -> RotationBlockStates.parse(s, 0))
            .map(m -> m.node(sidePos).connections().contains(face.get().opposite())).orElse(false);
        if (!connects) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return networks.report(key.world(), sidePos)
            .thenApply(r -> r.flatMap(rep -> rep.target().incoming()).map(Rotation::speed));
    }

    @Override
    public void apply(Key key, float speed) {
        Optional<Block> block = block(key);
        DeviceKind<PowerLoomState> kind = device.kind();
        block.ifPresent(b -> devices.edit(b, kind, (record, now) -> record.state().speed() == speed ? record
            : record.withState(record.state().withSpeed(speed))));
    }

    @Override
    public void weave(Key key) {
        block(key).ifPresent(b -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            float pitch = 1 + (r.nextFloat() - r.nextFloat()) / 16;
            b.getWorld().playSound(new org.bukkit.Location(b.getWorld(), b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5),
                Sound.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS, 1f, pitch);
        });
    }

    @Override
    public void later(Key key, long delayTicks, Runnable task) {
        World world = worlds.apply(key.world());
        if (world != null) {
            scheduler.regionDelayed(world, key.x() >> 4, key.z() >> 4, delayTicks, task);
        }
    }

    @Override
    public void onRegion(Key key, Runnable task) {
        World world = worlds.apply(key.world());
        if (world != null) {
            scheduler.region(world, key.x() >> 4, key.z() >> 4, task);
        }
    }

    @Override
    public void track(Key key) {
        block(key).ifPresent(b -> index.track(b.getChunk(), key.x(), key.y(), key.z()));
    }

    @Override
    public void untrack(Key key) {
        block(key).ifPresent(b -> index.untrack(b.getChunk(), key.x(), key.y(), key.z()));
    }
}
