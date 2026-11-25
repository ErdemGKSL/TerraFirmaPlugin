/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.quern.powered;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.quern.QuernState;
import dev.tfp.core.rotation.Rotation;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceInventory;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.quern.QuernDevice;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.rotation.RotationBlockStates;
import dev.tfp.paper.rotation.RotationService;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/** The server side of {@link PoweredQuernAccess}: the device service, the rotation actor, the region scheduler. */
final class PaperPoweredAccess implements PoweredQuernAccess {
    private final Scheduler scheduler;
    private final DeviceService devices;
    private final CustomBlocks blocks;
    private final RotationService networks;
    private final Recipes recipes;
    private final PoweredQuernIndex index;
    private final Function<java.util.UUID, World> worlds;

    PaperPoweredAccess(Scheduler scheduler, DeviceService devices, CustomBlocks blocks, RotationService networks, Recipes recipes,
            PoweredQuernIndex index, Function<java.util.UUID, World> worlds) {
        this.scheduler = scheduler;
        this.devices = devices;
        this.blocks = blocks;
        this.networks = networks;
        this.recipes = recipes;
        this.index = index;
        this.worlds = worlds;
    }

    private Optional<Block> block(Key key) {
        World world = worlds.apply(key.world());
        return world == null ? Optional.empty() : Optional.of(world.getBlockAt(key.x(), key.y(), key.z()));
    }

    @SuppressWarnings("unchecked")
    private Optional<DeviceKind<QuernState>> kind() {
        return devices.kinds().find(QuernDevice.ID).map(k -> (DeviceKind<QuernState>) k);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<View> view(Key key) {
        Optional<Block> block = block(key);
        Optional<DeviceKind<QuernState>> kind = kind();
        if (block.isEmpty() || kind.isEmpty()) {
            return Optional.empty();
        }
        Optional<DeviceService.Inspection> at = devices.inspect(block.get());
        if (at.isEmpty() || !QuernDevice.ID.equals(at.get().kindId()) || at.get().record().isEmpty()) {
            return Optional.empty();
        }
        DeviceRecord<QuernState> record = (DeviceRecord<QuernState>) at.get().record().get();
        DeviceInventory<QuernState> inv = new DeviceInventory<>(kind.get(), record, devices.stacks());
        ItemStack input = inv.get(QuernDevice.SLOT_INPUT);
        boolean grindable = !StackCodec.isEmpty(input) && recipes.first(RecipeTypes.QUERN, input).isPresent();
        QuernState state = record.state();
        return Optional.of(new View(at.get().now(), state.started(), state.started() ? state.finishTick() : 0,
            !StackCodec.isEmpty(inv.get(QuernDevice.SLOT_HANDSTONE)), grindable));
    }

    @Override
    public CompletableFuture<Optional<Float>> speed(Key key) {
        Optional<Block> block = block(key);
        if (block.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        // the block above is in the same chunk column, so the quern's region owns it too
        Block above = block.get().getRelative(0, 1, 0);
        Optional<BlockStateRef> state = blocks.stateAt(above);
        BlockPos abovePos = new BlockPos(key.x(), key.y() + 1, key.z());
        boolean connects = state.flatMap(s -> RotationBlockStates.parse(s, 0))
            .map(m -> m.node(abovePos).connections().contains(Direction.DOWN)).orElse(false);
        if (!connects) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return networks.report(key.world(), abovePos)
            .thenApply(r -> r.flatMap(rep -> rep.target().incoming()).map(Rotation::speed));
    }

    @Override
    public void settle(Key key) {
        block(key).ifPresent(devices::neighbourChanged);
    }

    @Override
    public void setFinish(Key key, long finishTick) {
        Optional<Block> block = block(key);
        Optional<DeviceKind<QuernState>> kind = kind();
        if (block.isPresent() && kind.isPresent()) {
            devices.edit(block.get(), kind.get(), (record, now) -> record.withState(new QuernState(PoweredQuern.startFor(finishTick))));
        }
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
