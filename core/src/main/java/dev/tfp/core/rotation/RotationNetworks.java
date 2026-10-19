/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/rotation/RotationNetworkManager.java @ c3b4d9536 (addSource, add, update, remove),
 *       src/main/java/net/dries007/tfc/util/rotation/RotationNetwork.java @ c3b4d9536 (updateOnAdd, updateAfterAdd, updateNetwork,
 *       removeNetwork), src/main/java/net/dries007/tfc/util/rotation/AxleNode.java @ c3b4d9536 (rotation, update),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/GearBoxBlockEntity.java @ c3b4d9536 (rotation override)
 */
package dev.tfp.core.rotation;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

/**
 * The rotation networks of one world, as a pure, single-owner data structure. Placed nodes form directed trees: each network
 * has one source and every other member points one face back toward it. A node joins when it connects to exactly one network,
 * with a handedness that agrees with every other face it connects on; the network then reaches out to any disconnected nodes
 * next to it ({@code updateAfterAdd}). Removing or changing a node re-derives the network from its source ({@code updateNetwork}).
 *
 * <p>Rotations are not stored per node. A node's incoming rotation is derived from the chain of parents back to its source, so
 * {@link #setSourceSpeed} is visible at once on every sink, as TFC's live {@code SourceNode} rotation is.
 *
 * <p>Mutations return an {@link Outcome}: whether the node was accepted, and the positions whose block must break. The caller
 * (the Paper glue, later) breaks those blocks and calls {@link #remove} for them, which re-derives the affected network. This
 * class is not thread-safe; a single owner applies the changes, see {@code paper-folia-threading} "decide async, apply sync".
 *
 * <p><strong>Divergences from TFC</strong> (also in {@code docs/porting/ledger.md}):
 * <ul>
 *     <li>{@link #load} inserts sources first, then the other nodes, each group in (x, y, z) order. TFC inserts in event order
 *         (chunk loads and placement), so with cycles the membership could depend on that order. The port gives the same result
 *         for any input order.</li>
 *     <li>A rejected node is never left half-added. TFC's {@code add} can leave a node in one network's map when a later
 *         network then rejects it; here a rejection changes nothing except a broken update, which is detached as TFC's update does.</li>
 *     <li>The broken list holds only positions still disconnected after the operation. TFC breaks an axle the moment its
 *         update fails, even if another path later connects it.</li>
 *     <li>Networks are iterated in ascending id order. TFC iterates a hash map, so the order of its checks is not defined.</li>
 * </ul>
 */
public final class RotationNetworks {
    /** TFC {@code Node.NO_NETWORK}: the node is not in any network. */
    public static final long NO_NETWORK = -1L;

    /** TFC {@code AxleNode.MAX_AXLE_LENGTH}: an axle that would be the sixth in a row fails. */
    static final int MAX_AXLE_LENGTH = 5;

    /** Load order: sources first, then the rest, each in (x, y, z) order. See the class Javadoc divergence. */
    private static final Comparator<RotationNode> LOAD_ORDER = Comparator
        .comparing((RotationNode n) -> n.kind() != NodeKind.SOURCE)
        .thenComparingInt(n -> n.pos().x())
        .thenComparingInt(n -> n.pos().y())
        .thenComparingInt(n -> n.pos().z());

    /**
     * The result of a mutation.
     *
     * @param accepted whether the node that was placed or changed is now in place (a rejected source or node is not stored)
     * @param broken   the positions whose block must break, in the order they were found, without duplicates
     */
    public record Outcome(boolean accepted, List<BlockPos> broken) {
        public Outcome {
            broken = List.copyOf(new LinkedHashSet<>(broken));
        }
    }

    /** A placed node. Mutable, private: the network state the algorithms walk. */
    private static final class Placed {
        RotationNode spec;
        /** The network this node belongs to, or {@link #NO_NETWORK}. */
        long network = NO_NETWORK;
        /** TFC {@code Node.source()}: the face, outgoing from this node, toward its parent. Null for a source or a disconnected node. */
        Direction parent;

        Placed(RotationNode spec) {
            this.spec = spec;
        }
    }

    /** A network: its id, its source position, and its non-source members. */
    private static final class Network {
        final long id;
        final BlockPos source;
        final Set<BlockPos> members = new HashSet<>();

        Network(long id, BlockPos source) {
            this.id = id;
            this.source = source;
        }
    }

    /** A node reached by a walk, and the rotation it receives. */
    private record Visit(BlockPos pos, Rotation incoming) {}

    /** A neighbour that connects toward a node, and the rotation it emits toward that node. */
    private record Candidate(Direction face, Rotation emitted) {}

    /** The network a node joins, its parent face, and the rotation it receives. */
    private record Join(long network, Direction parent, Rotation incoming) {}

    /** The decision for a node: accepted or rejected, and the join if it connects to a network. */
    private record Verdict(boolean accepted, Join join) {
        static final Verdict REJECT = new Verdict(false, null);
        static final Verdict DISCONNECTED = new Verdict(true, null);
    }

    private final Map<BlockPos, Placed> placed = new HashMap<>();
    private final TreeMap<Long, Network> networks = new TreeMap<>();
    private long nextNetworkId = 0;

    /**
     * Places a whole set of nodes, as on a region or chunk load. Sources go first, then the other nodes, each group in
     * (x, y, z) order, so the result does not depend on the order of {@code nodes}.
     *
     * @return the positions to break, from every rejected source or node, in the order they were found
     * @throws IllegalArgumentException if two nodes share a position
     */
    public List<BlockPos> load(Collection<RotationNode> nodes) {
        List<RotationNode> sorted = new ArrayList<>(nodes);
        Set<BlockPos> seen = new HashSet<>();
        for (RotationNode node : sorted) {
            if (!seen.add(node.pos())) {
                throw new IllegalArgumentException("Two nodes at " + node.pos());
            }
        }
        sorted.sort(LOAD_ORDER);
        List<BlockPos> broken = new ArrayList<>();
        for (RotationNode node : sorted) {
            Outcome outcome = node.kind() == NodeKind.SOURCE ? addSource(node) : add(node);
            broken.addAll(outcome.broken());
        }
        return List.copyOf(new LinkedHashSet<>(broken));
    }

    /**
     * TFC {@code RotationNetworkManager.addSource}. The source is rejected if any of its faces meets a node of an existing
     * network that connects back toward it. Otherwise it starts a new network and reaches out to the disconnected nodes beside it.
     *
     * @throws IllegalArgumentException if {@code source} is not a {@link NodeKind#SOURCE} or the position is already placed
     */
    public Outcome addSource(RotationNode source) {
        if (source.kind() != NodeKind.SOURCE) {
            throw new IllegalArgumentException("addSource needs a source: " + source.pos());
        }
        BlockPos pos = source.pos();
        if (placed.containsKey(pos)) {
            throw new IllegalArgumentException("Already placed at " + pos + "; remove it first");
        }
        for (Direction face : source.connections()) {
            Placed adjacent = placed.get(pos.offset(face));
            if (adjacent != null
                && adjacent.network != NO_NETWORK
                && adjacent.spec.connections().contains(face.opposite())) {
                return new Outcome(false, List.of(pos));
            }
        }
        long id = nextNetworkId++;
        Placed entry = new Placed(source);
        entry.network = id;
        placed.put(pos, entry);
        Network network = new Network(id, pos);
        networks.put(id, network);
        List<BlockPos> broken = pullIn(network, pos, source.sourceRotation());
        return new Outcome(true, unclaimed(broken));
    }

    /**
     * TFC {@code RotationNetworkManager.add}: places a new non-source node. It joins the one network it connects to, or is
     * stored disconnected if it touches none. If it touches two networks, or two faces that disagree, or would be an axle of
     * length six, it is rejected and its block must break.
     *
     * @throws IllegalArgumentException if {@code node} is a source
     */
    public Outcome add(RotationNode node) {
        return place(node);
    }

    /**
     * TFC {@code RotationNetworkManager.update}: replaces the connections of a placed node and re-derives what changed. A node
     * that is not placed is added. A rejected update keeps the node stored, disconnected, and re-derives its old network.
     *
     * @throws IllegalArgumentException if {@code node} is a source
     */
    public Outcome update(RotationNode node) {
        return place(node);
    }

    /**
     * TFC {@code RotationNetworkManager.remove}. Removing a source removes its whole network, and the members are left disconnected.
     * Removing any other node re-derives its network from the source.
     *
     * @return the positions to break, from axles that can no longer connect
     */
    public List<BlockPos> remove(BlockPos pos) {
        Placed entry = placed.remove(pos);
        if (entry == null || entry.network == NO_NETWORK) {
            return List.of();
        }
        Network network = networks.get(entry.network);
        if (network.source.equals(pos)) {
            for (BlockPos member : network.members) {
                Placed memberEntry = placed.get(member);
                memberEntry.network = NO_NETWORK;
                memberEntry.parent = null;
            }
            networks.remove(network.id);
            return List.of();
        }
        network.members.remove(pos);
        return unclaimed(prune(network));
    }

    /**
     * Sets the speed of a placed source. Every rotation derived from it changes at once, as TFC's live source rotation does.
     *
     * @throws IllegalArgumentException if {@code pos} is not a placed source
     */
    public void setSourceSpeed(BlockPos pos, float speed) {
        Placed entry = placed.get(pos);
        if (entry == null || entry.spec.kind() != NodeKind.SOURCE) {
            throw new IllegalArgumentException("No placed source at " + pos);
        }
        entry.spec = entry.spec.withSourceSpeed(speed);
    }

    /** The network a placed node belongs to, or empty if it is not placed or disconnected. */
    public OptionalLong networkOf(BlockPos pos) {
        Placed entry = placed.get(pos);
        if (entry == null || entry.network == NO_NETWORK) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(entry.network);
    }

    /**
     * TFC {@code Node.rotation()}: the rotation a connected node receives, from its source. For a source, its own rotation.
     * Empty if the node is not placed or disconnected.
     */
    public Optional<Rotation> incoming(BlockPos pos) {
        Placed entry = placed.get(pos);
        if (entry == null || entry.network == NO_NETWORK) {
            return Optional.empty();
        }
        return Optional.of(incomingOf(pos));
    }

    /**
     * TFC {@code Node.rotation(Direction)}: the rotation a connected node emits on one of its faces. Empty if the node is not
     * placed or disconnected.
     *
     * @throws IllegalStateException if the node is a sink, which never emits (TFC {@code SinkNode.rotation} throws too)
     */
    public Optional<Rotation> emitted(BlockPos pos, Direction exit) {
        Placed entry = placed.get(pos);
        if (entry == null || entry.network == NO_NETWORK) {
            return Optional.empty();
        }
        return Optional.of(emit(entry.spec, incomingOf(pos), entry.parent, exit));
    }

    /** The number of networks currently in the world. */
    public int networkCount() {
        return networks.size();
    }

    // ---------------------------------------------------------------- mutation

    private Outcome place(RotationNode node) {
        if (node.kind() == NodeKind.SOURCE) {
            throw new IllegalArgumentException("A source is placed with addSource: " + node.pos());
        }
        BlockPos pos = node.pos();
        Placed existing = placed.get(pos);
        long origin = existing == null ? NO_NETWORK : existing.network;
        Verdict verdict = decide(node, origin);

        if (!verdict.accepted()) {
            if (existing == null) {
                return new Outcome(false, List.of(pos));
            }
            // TFC update(): the node is removed from its origin and the origin is re-derived. It stays stored, disconnected.
            existing.spec = node;
            detach(pos, existing);
            List<BlockPos> broken = new ArrayList<>();
            broken.add(pos);
            if (origin != NO_NETWORK) {
                broken.addAll(prune(networks.get(origin)));
            }
            return new Outcome(false, unclaimed(broken));
        }

        Placed entry = existing;
        if (entry == null) {
            entry = new Placed(node);
            placed.put(pos, entry);
        }
        entry.spec = node;

        if (verdict.join() == null) {
            detach(pos, entry);
            List<BlockPos> broken = origin == NO_NETWORK ? List.of() : prune(networks.get(origin));
            return new Outcome(true, unclaimed(broken));
        }

        Join join = verdict.join();
        Network network = networks.get(join.network());
        entry.network = network.id;
        entry.parent = join.parent();
        network.members.add(pos);
        List<BlockPos> broken = new ArrayList<>(pullIn(network, pos, join.incoming()));
        if (origin != NO_NETWORK) {
            broken.addAll(prune(network));
        }
        return new Outcome(true, unclaimed(broken));
    }

    /**
     * TFC {@code RotationNetwork.updateOnAdd} and the checks in {@code RotationNetworkManager.add/update}, as a decision with no
     * side effects. Groups the neighbours that connect toward {@code node} by network, in face order; then accepts only if they
     * are a single network, which is the node's origin when it has one, and their faces agree on handedness and the rotation is
     * valid for the node.
     */
    private Verdict decide(RotationNode node, long origin) {
        TreeMap<Long, List<Candidate>> groups = new TreeMap<>();
        for (Direction face : node.connections()) {
            Placed neighbour = placed.get(node.pos().offset(face));
            if (neighbour == null || neighbour.network == NO_NETWORK) {
                continue;
            }
            Direction toward = face.opposite();
            // TFC: the neighbour must connect back, and must not be fed by this node (its source face is not toward us).
            if (!neighbour.spec.connections().contains(toward) || neighbour.parent == toward) {
                continue;
            }
            Rotation emitted = emit(neighbour.spec, incomingOf(neighbour.spec.pos()), neighbour.parent, toward);
            groups.computeIfAbsent(neighbour.network, k -> new ArrayList<>()).add(new Candidate(face, emitted));
        }

        if (groups.isEmpty()) {
            return Verdict.DISCONNECTED;
        }
        // A node that touches another network than its origin is a connection to two networks: TFC FAIL_CONNECTED_TO_OTHER_NETWORK.
        if (origin != NO_NETWORK && !groups.containsKey(origin)) {
            return Verdict.REJECT;
        }
        if (groups.size() > 1) {
            return Verdict.REJECT;
        }

        long networkId = groups.firstKey();
        List<Candidate> candidates = groups.firstEntry().getValue();
        Candidate pending = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            Candidate other = candidates.get(i);
            // TFC: the rotation this node would pass on toward the second face must match the neighbour's handedness there.
            Rotation through = emit(node, pending.emitted(), pending.face(), other.face());
            if (through.direction() != other.emitted().direction()) {
                return Verdict.REJECT;
            }
        }
        if (!accepts(node.kind(), pending.emitted())) {
            return Verdict.REJECT;
        }
        return new Verdict(true, new Join(networkId, pending.face(), pending.emitted()));
    }

    /** Removes a node from its network and the stored state, keeping the record stored as disconnected. */
    private void detach(BlockPos pos, Placed entry) {
        if (entry.network != NO_NETWORK) {
            Network network = networks.get(entry.network);
            if (network != null) {
                network.members.remove(pos);
            }
        }
        entry.network = NO_NETWORK;
        entry.parent = null;
    }

    /**
     * TFC {@code RotationNetwork.updateAfterAdd}: from {@code start}, joins every disconnected neighbour that connects back,
     * and carries the rotation outward. A neighbour that cannot take the rotation (a sixth axle) is left out and reported.
     */
    private List<BlockPos> pullIn(Network network, BlockPos start, Rotation startIncoming) {
        List<BlockPos> failed = new ArrayList<>();
        Deque<Visit> queue = new ArrayDeque<>();
        queue.add(new Visit(start, startIncoming));
        while (!queue.isEmpty()) {
            Visit current = queue.poll();
            Placed currentEntry = placed.get(current.pos());
            for (Direction face : currentEntry.spec.connections()) {
                // TFC: do not go back toward the source.
                if (face == currentEntry.parent) {
                    continue;
                }
                BlockPos nextPos = current.pos().offset(face);
                Placed next = placed.get(nextPos);
                Direction toward = face.opposite();
                if (next == null || next.network != NO_NETWORK || !next.spec.connections().contains(toward)) {
                    continue;
                }
                Rotation exit = emit(currentEntry.spec, current.incoming(), currentEntry.parent, face);
                if (!accepts(next.spec.kind(), exit)) {
                    failed.add(nextPos);
                    continue;
                }
                next.network = network.id;
                next.parent = toward;
                network.members.add(nextPos);
                queue.add(new Visit(nextPos, exit));
            }
        }
        return failed;
    }

    /**
     * TFC {@code RotationNetwork.updateNetwork}: walks from the source over the members only, in face order, carrying the
     * rotation. Members not reached (because the walk cannot reach them, or an axle fails on the way) are detached.
     *
     * @return the positions that failed to take the rotation, for {@link #unclaimed}
     */
    private List<BlockPos> prune(Network network) {
        Set<BlockPos> unvisited = new HashSet<>(network.members);
        List<BlockPos> failed = new ArrayList<>();
        Deque<Visit> queue = new ArrayDeque<>();
        Placed sourceEntry = placed.get(network.source);
        queue.add(new Visit(network.source, sourceEntry.spec.sourceRotation()));
        while (!queue.isEmpty()) {
            Visit current = queue.poll();
            Placed currentEntry = placed.get(current.pos());
            for (Direction face : currentEntry.spec.connections()) {
                BlockPos nextPos = current.pos().offset(face);
                if (!unvisited.contains(nextPos)) {
                    continue;
                }
                Placed next = placed.get(nextPos);
                Direction toward = face.opposite();
                if (!next.spec.connections().contains(toward)) {
                    continue;
                }
                Rotation exit = emit(currentEntry.spec, current.incoming(), currentEntry.parent, face);
                if (!accepts(next.spec.kind(), exit)) {
                    failed.add(nextPos);
                    continue;
                }
                unvisited.remove(nextPos);
                next.parent = toward;
                queue.add(new Visit(nextPos, exit));
            }
        }
        for (BlockPos stranded : unvisited) {
            detach(stranded, placed.get(stranded));
        }
        return failed;
    }

    /** The failed positions that are still disconnected, in the order given. See the class Javadoc divergence. */
    private List<BlockPos> unclaimed(List<BlockPos> failed) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : failed) {
            Placed entry = placed.get(pos);
            if (entry != null && entry.network == NO_NETWORK) {
                out.add(pos);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- rotation

    /**
     * The rotation a node receives. A source's own rotation. Otherwise, the rotation its parent emits toward it, derived from the
     * chain of parents down from the source. The caller guarantees the node is connected.
     */
    private Rotation incomingOf(BlockPos pos) {
        Placed entry = placed.get(pos);
        Network network = networks.get(entry.network);
        // The chain from pos up to (not including) the source, nearest the source last.
        Deque<BlockPos> chain = new ArrayDeque<>();
        BlockPos cursor = pos;
        while (!cursor.equals(network.source)) {
            chain.push(cursor);
            cursor = cursor.offset(placed.get(cursor).parent);
        }
        Placed sourceEntry = placed.get(network.source);
        if (chain.isEmpty()) {
            return sourceEntry.spec.sourceRotation();
        }
        Placed current = sourceEntry;
        Rotation incoming = null;
        Direction incomingFace = null;
        while (!chain.isEmpty()) {
            BlockPos child = chain.pop();
            Placed childEntry = placed.get(child);
            Direction toChild = childEntry.parent.opposite();
            incoming = emit(current.spec, incoming, incomingFace, toChild);
            incomingFace = childEntry.parent;
            current = childEntry;
        }
        return incoming;
    }

    /** Whether a node of this kind can take this incoming rotation. Only an axle has a limit (TFC {@code AxleNode.update}). */
    private static boolean accepts(NodeKind kind, Rotation incoming) {
        return kind != NodeKind.AXLE || incoming.axleLength() != MAX_AXLE_LENGTH;
    }

    /**
     * TFC {@code Node.rotation(sourceRotation, sourceDirection, exitDirection)} for each kind. {@code sourceFace} is the node's
     * own face toward its source, outgoing; {@code exit} is the face the rotation leaves on.
     */
    static Rotation emit(RotationNode node, Rotation incoming, Direction sourceFace, Direction exit) {
        return switch (node.kind()) {
            case SOURCE -> node.sourceRotation();
            // TFC AxleNode.rotation: the same handedness and speed, one more axle counted.
            case AXLE -> new Rotation(incoming.direction(), incoming.speed(), incoming.axleLength() + 1);
            case GEAR_BOX -> gearbox(incoming, sourceFace, exit);
            case SINK -> throw new IllegalStateException("A sink never emits a rotation: " + node.pos());
        };
    }

    /**
     * TFC {@code GearBoxBlockEntity} rotation: a face on the same axis as the source flips the handedness; a perpendicular face
     * keeps it, reversed when the source face is the one the rotation turns about. Speed passes unchanged.
     */
    private static Rotation gearbox(Rotation incoming, Direction sourceFace, Direction exit) {
        // TFC line 60-62: same axis as the source face.
        if (sourceFace == exit || sourceFace == exit.opposite()) {
            return Rotation.of(incoming.direction().opposite(), incoming.speed());
        }
        // TFC line 68-70: perpendicular, with the convention reversed when the source face matches the rotation direction.
        Direction output = sourceFace == incoming.direction() ? exit.opposite() : exit;
        return Rotation.of(output, incoming.speed());
    }
}
