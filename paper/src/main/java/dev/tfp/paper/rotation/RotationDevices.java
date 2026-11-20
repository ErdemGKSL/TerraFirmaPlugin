/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/RotatingBlockEntity.java @ c3b4d9536 (network actions),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/ClutchBlock.java @ c3b4d9536 (neighborChanged, getStateForPlacement),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/GearBoxBlock.java @ c3b4d9536 (useItemOn),
 *       src/main/java/net/dries007/tfc/common/blocks/rotation/CreativeRotationBlock.java @ c3b4d9536 (useWithoutItem)
 */
package dev.tfp.paper.rotation;

import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.rotation.CreativeMotor;
import dev.tfp.core.rotation.GearBoxRules;
import dev.tfp.core.rotation.MechanicalBlock;
import dev.tfp.core.rotation.RotationAxis;
import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.device.DeviceContext;
import dev.tfp.paper.device.DeviceHandler;
import dev.tfp.paper.device.DeviceKind;
import dev.tfp.paper.device.DeviceResult;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * The device kinds of PAR-MECH-01's blocks. Every callback runs on the block's region thread and only reads the block,
 * then hands its network state to the {@link RotationService} (TFC's {@code performNetworkAction}); a change of the block's
 * own state (the clutch's {@code powered}, a gearbox face) goes through {@link Ops#setState}, applied on the next tick.
 */
public final class RotationDevices {
    /** TFC {@code TFCTags.Items.TOOLS_HAMMER} ({@code c:tools/hammer}). */
    public static final String HAMMERS = "c:tools/hammer";

    /** The world side, region thread of the block. */
    public interface Ops {
        /** TFC {@code level.hasNeighborSignal(pos)}. */
        boolean hasNeighborSignal(Block block);

        /** Writes the block's own new state on the next tick, if the block still has {@code expected}. */
        void setState(Block block, BlockStateRef expected, BlockStateRef next);

        /** TFC {@code Helpers.playPlaceSound} of the block. */
        void placeSound(Block block);

        /** TFC {@code Helpers.playSound(level, pos, SoundEvents.ITEM_BREAK)}. */
        void failSound(Block block);

        /** The face of {@code block} the player's look ray hits (TFC {@code BlockHitResult.getDirection()}). */
        default Optional<Direction> hitFace(Player player, Block block) {
            return RotationDevices.hitFace(player, block);
        }
    }

    /** Lists a rotation block in its chunk's index (so the chunk load re-adds it) or drops it. Region thread. */
    public interface Index {
        void track(Block block);

        void untrack(Block block);
    }

    private final RotationService networks;
    private final Ops ops;
    private final Index index;
    private final Predicate<ItemStack> hammer;

    public RotationDevices(RotationService networks, Ops ops, Index index, Predicate<ItemStack> hammer) {
        this.networks = Objects.requireNonNull(networks, "networks");
        this.ops = Objects.requireNonNull(ops, "ops");
        this.index = Objects.requireNonNull(index, "index");
        this.hammer = Objects.requireNonNull(hammer, "hammer");
    }

    /** The five kinds, one per id of {@link RotationBlockStates#KINDS}. */
    public List<DeviceKind<RotationState>> kinds() {
        List<DeviceKind<RotationState>> out = new ArrayList<>();
        for (String id : RotationBlockStates.KINDS) {
            out.add(DeviceKind.builder(id, RotationState.CODEC, RotationState.FRESH)
                .removal(DeviceKind.RemoveBehaviour.NOOP) // TFC: no inventory (GearBoxBlock InventoryRemoveBehavior.NOOP)
                .handler(new Handler())
                .build());
        }
        return List.copyOf(out);
    }

    static BlockPos pos(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    /** Hands the block's current state to the network (TFC ADD / ADD_SOURCE / UPDATE). Region thread. */
    void submit(Block block, BlockStateRef state, int step) {
        RotationBlockStates.parse(state, step).ifPresent(s -> networks.put(block.getWorld().getUID(), pos(block), s));
    }

    private final class Handler implements DeviceHandler<RotationState> {
        @Override
        public DeviceResult<RotationState> placed(DeviceContext<RotationState> ctx) {
            index.track(ctx.block());
            BlockStateRef state = ctx.state();
            if (RotationBlockStates.block(state.id()).orElse(null) == MechanicalBlock.CLUTCH) {
                // TFC ClutchBlock.getStateForPlacement: powered = hasNeighborSignal.
                boolean signal = ops.hasNeighborSignal(ctx.block());
                if (signal != Boolean.parseBoolean(state.properties().get(RotationBlockStates.POWERED))) {
                    BlockStateRef next = state.with(RotationBlockStates.POWERED, Boolean.toString(signal));
                    ops.setState(ctx.block(), state, next);
                    state = next;
                }
            }
            submit(ctx.block(), state, ctx.deviceState().step());
            return DeviceResult.pass();
        }

        @Override
        public DeviceResult<RotationState> neighbourChanged(DeviceContext<RotationState> ctx) {
            BlockStateRef state = ctx.state();
            if (RotationBlockStates.block(state.id()).orElse(null) == MechanicalBlock.CLUTCH) {
                // TFC ClutchBlock.neighborChanged: cycle POWERED when the signal differs, then updateConnections.
                boolean signal = ops.hasNeighborSignal(ctx.block());
                if (signal != Boolean.parseBoolean(state.properties().get(RotationBlockStates.POWERED))) {
                    BlockStateRef next = state.with(RotationBlockStates.POWERED, Boolean.toString(signal));
                    ops.setState(ctx.block(), state, next);
                    state = next;
                }
            }
            submit(ctx.block(), state, ctx.deviceState().step());
            return DeviceResult.pass();
        }

        @Override
        public DeviceResult<RotationState> useWithItem(DeviceContext<RotationState> ctx) {
            BlockStateRef state = ctx.state();
            if (RotationBlockStates.block(state.id()).orElse(null) != MechanicalBlock.GEAR_BOX || ctx.player() == null
                || !hammer.test(ctx.item())) {
                return DeviceResult.pass();
            }
            Optional<Direction> hit = ops.hitFace(ctx.player(), ctx.block());
            if (hit.isEmpty()) {
                return DeviceResult.pass();
            }
            GearBoxRules.Toggle t = GearBoxRules.toggle(RotationBlockStates.faces(state), hit.get(), ctx.player().isSneaking());
            if (t.outcome() == GearBoxRules.Outcome.REFUSED) {
                ops.failSound(ctx.block());
                return DeviceResult.consume(); // TFC ItemInteractionResult.FAIL: nothing else happens
            }
            BlockStateRef next = RotationBlockStates.withFaces(state, t.faces());
            ops.setState(ctx.block(), state, next);
            ops.placeSound(ctx.block());
            submit(ctx.block(), next, 0);
            return DeviceResult.consume();
        }

        @Override
        public DeviceResult<RotationState> use(DeviceContext<RotationState> ctx) {
            BlockStateRef state = ctx.state();
            if (RotationBlockStates.block(state.id()).orElse(null) != MechanicalBlock.CREATIVE_MOTOR || ctx.player() == null) {
                submit(ctx.block(), state, ctx.deviceState().step());
                return DeviceResult.pass();
            }
            Optional<Direction> hit = ops.hitFace(ctx.player(), ctx.block());
            RotationAxis axis;
            try {
                axis = RotationAxis.parse(state.properties().get(RotationBlockStates.AXIS));
            } catch (RuntimeException e) {
                return DeviceResult.pass();
            }
            if (hit.isEmpty() || CreativeMotor.stepChange(axis, hit.get()) == 0) {
                return DeviceResult.pass(); // TFC InteractionResult.PASS
            }
            int step = CreativeMotor.click(ctx.deviceState().step(), axis, hit.get());
            submit(ctx.block(), state, step);
            DeviceRecord<RotationState> next = ctx.record().withState(new RotationState(step));
            return DeviceResult.consume(next);
        }

        @Override
        public void removed(DeviceContext<RotationState> ctx) {
            index.untrack(ctx.block());
            networks.remove(ctx.block().getWorld().getUID(), pos(ctx.block()));
        }
    }

    /** The face of {@code block} the player's look ray hits (TFC {@code BlockHitResult.getDirection()}). */
    static Optional<Direction> hitFace(Player player, Block block) {
        RayTraceResult r = player.rayTraceBlocks(8.0);
        if (r == null || r.getHitBlock() == null || !r.getHitBlock().equals(block) || r.getHitBlockFace() == null) {
            return Optional.empty();
        }
        return Optional.of(direction(r.getHitBlockFace()));
    }

    static Direction direction(BlockFace face) {
        return switch (face) {
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            default -> throw new IllegalArgumentException("not a block face: " + face);
        };
    }
}
