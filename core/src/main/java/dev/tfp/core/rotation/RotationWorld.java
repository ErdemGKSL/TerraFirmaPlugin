/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/RotationNetworkManager.java @ c3b4d9536 (performAction),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/RotatingBlockEntity.java @ c3b4d9536 (performNetworkAction)
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The rotation networks of one world, driven by block states: TFC's {@code RotatingBlockEntity.performNetworkAction}
 * (ADD, ADD_SOURCE, UPDATE, REMOVE) without block entities. A block that is loaded or placed is {@link #put}; one that is
 * broken or unloaded is {@link #remove}d. Single owner, not thread-safe (the Paper glue runs it on one serial worker).
 *
 * <p>TFC keeps an {@code invalid} flag on a block entity whose action failed and does nothing more for it until the
 * scheduled tick breaks the block. Here a failed block is remembered the same way ({@link #isInvalid}) until it is removed.
 */
public final class RotationWorld {
    private final RotationNetworks networks = new RotationNetworks();
    private final Map<BlockPos, MechanicalBlock.State> states = new HashMap<>();
    private final Map<BlockPos, Boolean> invalid = new HashMap<>();

    /**
     * Adds or changes the block at {@code pos}. An unchanged state does nothing. A source whose only change is its speed (the
     * motor's step, the water wheel's or windmill's reported speed) changes the live source speed (TFC's {@code setSpeed}
     * each tick) and keeps its network. A source that turns into another block, or the reverse, is removed first.
     *
     * @return the positions whose block must break (TFC: marked invalid, destroyed on the scheduled tick)
     */
    public List<BlockPos> put(BlockPos pos, MechanicalBlock.State state) {
        if (invalid.containsKey(pos)) {
            // TFC performNetworkAction: an invalid block entity does nothing, it breaks soon.
            states.put(pos, state);
            return List.of();
        }
        MechanicalBlock.State old = states.get(pos);
        if (state.equals(old)) {
            return List.of();
        }
        List<BlockPos> broken = new ArrayList<>();
        if (old != null && old.block().kind() == NodeKind.SOURCE && old.block() == state.block()
            && old.axis() == state.axis()) {
            states.put(pos, state);
            if (networks.networkOf(pos).isPresent()) {
                networks.setSourceSpeed(pos, state.sourceSpeed());
            }
            return List.of();
        }
        if (old != null && (old.block().kind() == NodeKind.SOURCE || state.block().kind() == NodeKind.SOURCE
            || old.block() == MechanicalBlock.CREATIVE_MOTOR)) {
            broken.addAll(networks.remove(pos));
            old = null;
        }
        states.put(pos, state);
        RotationNode node = state.node(pos);
        RotationNetworks.Outcome outcome = node.kind() == NodeKind.SOURCE
            ? networks.addSource(node)
            : (old == null ? networks.add(node) : networks.update(node));
        broken.addAll(outcome.broken());
        return mark(broken);
    }

    /** TFC REMOVE: the block was broken or its chunk unloaded. */
    public List<BlockPos> remove(BlockPos pos) {
        invalid.remove(pos);
        if (states.remove(pos) == null) {
            return List.of();
        }
        return mark(networks.remove(pos));
    }

    private List<BlockPos> mark(List<BlockPos> broken) {
        List<BlockPos> out = List.copyOf(new LinkedHashSet<>(broken));
        for (BlockPos p : out) {
            if (states.containsKey(p)) {
                invalid.put(p, Boolean.TRUE);
            }
        }
        return out;
    }

    /** TFC {@code isInvalidInNetwork}: the block failed and waits to be broken. */
    public boolean isInvalid(BlockPos pos) {
        return invalid.containsKey(pos);
    }

    /** The state stored at {@code pos}. */
    public Optional<MechanicalBlock.State> state(BlockPos pos) {
        return Optional.ofNullable(states.get(pos));
    }

    /** Every stored block, for a chunk unload. */
    public List<BlockPos> positions() {
        return List.copyOf(states.keySet());
    }

    /** The number of networks. */
    public int networkCount() {
        return networks.networkCount();
    }

    /**
     * What a debug readout shows for one block.
     *
     * @param pos      the block
     * @param block    which block
     * @param network  its network id, empty when disconnected
     * @param parent   its face toward the source, empty for a source or a disconnected block
     * @param incoming the rotation it receives (a source: its own), empty when disconnected
     * @param invalid  whether it failed and waits to break
     */
    public record Readout(BlockPos pos, MechanicalBlock block, OptionalLong network, Optional<Direction> parent,
                          Optional<Rotation> incoming, boolean invalid) {
        /** The speed in revolutions per minute, 0 when disconnected. */
        public float rpm() {
            return incoming.map(r -> CreativeMotor.rpm(r.speed())).orElse(0f);
        }
    }

    /** The readouts of every block of {@code network}, in (x, y, z) order. */
    public List<Readout> members(long network) {
        List<Readout> out = new ArrayList<>();
        networks.nodes().forEach((pos, id) -> {
            if (id == network) {
                readout(pos).ifPresent(out::add);
            }
        });
        out.sort(java.util.Comparator.comparingInt((Readout r) -> r.pos().x()).thenComparingInt(r -> r.pos().y())
            .thenComparingInt(r -> r.pos().z()));
        return out;
    }

    /** The readout of {@code pos}, empty when no block is stored there. */
    public Optional<Readout> readout(BlockPos pos) {
        MechanicalBlock.State state = states.get(pos);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(new Readout(pos, state.block(), networks.networkOf(pos), networks.parentOf(pos),
            networks.incoming(pos), invalid.containsKey(pos)));
    }
}
