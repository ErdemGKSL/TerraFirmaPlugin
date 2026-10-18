/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/ChannelFlow.java @ c3b4d9536 (the neighbour rule of findAdjacent, the graph and
 *       heuristic of fromCrucible, the A* search, and the flow links and origin link; the block, block entity, fluid and
 *       item handling stay in the platform glue)
 */
package dev.tfp.core.crucible;

import dev.tfp.core.world.BlockPos;
import dev.tfp.core.world.Direction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The pure channel flow search of the crucible: which channels and molds a crucible feeds, the route from each mold back to
 * the channel the crucible pours into, and the flow link of every channel and mold on those routes.
 *
 * <p>The world is a {@link Cells} grid of facts. TFC reads blocks and block entities; here the caller classifies each cell as
 * {@link Cell}, and says whether an open mold can take the fluid. Nothing else is read, so the search runs on any thread and
 * returns the same plan for the same facts.
 *
 * <p>Rules, with TFC's line numbers in {@code ChannelFlow.java @ c3b4d9536}:
 * <ul>
 *   <li>Neighbours (lines 214-248): from a channel, each direction except {@code UP} is searched in TFC's
 *       {@link Direction} order (DOWN, NORTH, SOUTH, WEST, EAST). Horizontal rays look one block out, a DOWN ray looks up to
 *       {@link #MAX_DROP} blocks. A ray stops at the first non-air cell; it reports that cell only if it is an open channel
 *       (channel search) or an open mold (mold search). A channel with flow and a mold with a source block the ray without
 *       being reported.</li>
 *   <li>Flood (lines 69-96): a FIFO of channels from the origin; a channel gets the next index when it is first popped; its
 *       channel neighbours are queued, its mold neighbours are collected in discovery order.</li>
 *   <li>Outlets (lines 105-121): the discovered molds that {@code accepts} takes, in discovery order. No outlet means no plan.
 *       TFC iterates a {@code HashSet<BlockPos>} here, so its order follows {@code BlockPos.hashCode()}. That order is not
 *       specified, so the port pins discovery order. The order only decides which route a channel shared by two outlets
 *       keeps (the last route written wins), so the pinned order is a fixed choice, not a different rule.</li>
 *   <li>Graph (lines 130-160): every edge costs one hop, whatever its length in blocks, so the search minimises hops. The
 *       heuristic of an edge is the straight-line distance between its ends (set once per pair, both ways). Every edge is
 *       directed from the channel that found it: a drop goes from the upper channel to the lower one, a horizontal step goes
 *       both ways. Nothing rises.</li>
 *   <li>A* (lines 265-344): the nodes are the channels (indices in flood order) then the outlets. The next node is the
 *       unvisited node with the least priority, ties going to the lower index. Its priority is its hop count plus
 *       {@code heuristic[node][goal]}, which is non-zero only when the node touches the goal. The search stops when the
 *       goal is the next node popped. TFC's search has no node cap; the only bound on the drop is {@link #MAX_DROP}.</li>
 *   <li>Links (lines 168-190): for each route, the cell nearer the mold gets the direction of the cell nearer the origin
 *       and the distance to it. Each channel gets one {@code flows} count for every route that passes through it as the
 *       nearer cell. Routes are written in outlet order, so a channel shared by two routes keeps the link of the last one.</li>
 *   <li>Origin (lines 206-211): the origin's link points at the crucible, one block away.</li>
 * </ul>
 *
 * <p>Divergence: TFC's DOWN loop is {@code for (byte i = 1; i < maxDistance + 1; i++)} with {@code maxDistance} =
 * {@code Byte.MAX_VALUE}. The byte counter wraps after 127 and the loop keeps going, sweeping back up through offsets -128
 * to 0 (128 blocks above the channel, up to the channel itself). A channel or mold found there can be linked, and the channel
 * itself is added as its own neighbour at offset 0 when nothing else stops the scan. This port looks exactly
 * {@link #MAX_DROP} blocks down and no further.
 */
public final class ChannelFlow {
    /** TFC's {@code Byte.MAX_VALUE}: the longest drop a channel searches for an outlet, in blocks. */
    public static final int MAX_DROP = 127;

    private ChannelFlow() {
    }

    /** What a cell holds, as far as the flow search is concerned. */
    public enum Cell {
        /** Air. A ray passes through it. */
        AIR,
        /** Any other non-air block that is neither a channel nor a mold. A ray stops at it. */
        SOLID,
        /** A channel with no flow. Joins the net. */
        CHANNEL,
        /** A channel that already has flow. A ray stops at it and it is not joined. */
        LINKED_CHANNEL,
        /** A mold table with no source. Found by the mold search; it may be an outlet. */
        MOLD,
        /** A mold table that already has a source. A ray stops at it and it is not an outlet. */
        LINKED_MOLD
    }

    /** The cell kind at a position. Positions with no entry in the caller's grid are {@link Cell#AIR} or solid, as the caller decides. */
    @FunctionalInterface
    public interface Cells {
        Cell at(BlockPos pos);
    }

    /** Whether an open mold can take the crucible's fluid: TFC's {@code removeIf} test (empty output and a fill that would succeed). */
    @FunctionalInterface
    public interface MoldAcceptance {
        boolean accepts(BlockPos mold);
    }

    /**
     * The flow into one cell: the direction the fluid comes from and the distance in blocks to the cell it comes from.
     *
     * @param from     the direction from this cell toward the cell that feeds it (the origin's link points at the crucible)
     * @param distance the blocks between the two cells: 1 for a horizontal step, more for a drop
     */
    public record Link(Direction from, int distance) {
    }

    /**
     * One route from an outlet back to the origin.
     *
     * @param outlet the mold the route ends at
     * @param cells  the cells on the route, outlet first and the origin last (TFC's order: goal to start)
     */
    public record Path(BlockPos outlet, List<BlockPos> cells) {
    }

    /**
     * The plan of one crucible pour.
     *
     * @param channels the channels in flood order (index 0 is the origin)
     * @param outlets  the accepted molds in discovery order
     * @param paths    one route per outlet, in outlet order
     * @param links    the link of each channel and outlet on a route, in the order the routes first set them
     * @param flows    how many routes pass through each channel as its nearer cell (the origin included)
     * @param origin   the origin's link to the crucible
     */
    public record Plan(List<BlockPos> channels, List<BlockPos> outlets, List<Path> paths, Map<BlockPos, Link> links,
                       Map<BlockPos, Integer> flows, Link origin) {
        public Plan {
            channels = List.copyOf(channels);
            outlets = List.copyOf(outlets);
            paths = List.copyOf(paths);
            links = Collections.unmodifiableMap(new LinkedHashMap<>(links));
            flows = Collections.unmodifiableMap(new HashMap<>(flows));
        }
    }

    /**
     * Plans the flow of one crucible pour through a channel.
     *
     * @param cells    the cell kinds of the world
     * @param accepts  which discovered molds can take the fluid
     * @param origin   the channel the crucible pours into (TFC's {@code originChannel})
     * @param crucible the crucible, which must touch the origin
     * @return the plan, or empty when no accepted mold is reachable (TFC returns without changing anything)
     * @throws IllegalArgumentException if the crucible does not touch the origin
     */
    public static Optional<Plan> plan(Cells cells, MoldAcceptance accepts, BlockPos origin, BlockPos crucible) {
        Direction towardCrucible = unit(crucible.x() - origin.x(), crucible.y() - origin.y(), crucible.z() - origin.z());
        if (towardCrucible == null) {
            throw new IllegalArgumentException("The crucible " + crucible + " must touch the origin channel " + origin);
        }

        // Flood: lines 69-96
        List<BlockPos> channels = new ArrayList<>();
        Map<BlockPos, Integer> channelIndex = new HashMap<>();
        Map<BlockPos, List<BlockPos>> neighbours = new HashMap<>();
        Set<BlockPos> foundMolds = new LinkedHashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        pending.add(origin);
        while (!pending.isEmpty()) {
            BlockPos current = pending.pollFirst();
            if (channelIndex.containsKey(current)) {
                continue;
            }
            channelIndex.put(current, channels.size());
            channels.add(current);

            List<BlockPos> adjacentChannels = findAdjacent(cells, current, false);
            List<BlockPos> adjacentMolds = findAdjacent(cells, current, true);
            pending.addAll(adjacentChannels);
            foundMolds.addAll(adjacentMolds);

            List<BlockPos> both = new ArrayList<>(adjacentChannels);
            both.addAll(adjacentMolds);
            neighbours.put(current, both);
        }

        // Outlets: lines 105-121
        List<BlockPos> outlets = new ArrayList<>();
        for (BlockPos mold : foundMolds) {
            if (accepts.accepts(mold)) {
                outlets.add(mold);
            }
        }
        if (outlets.isEmpty()) {
            return Optional.empty();
        }

        // Graph and heuristic: lines 123-160. Channels take indices 0..c-1, outlets c..n-1.
        int c = channels.size();
        int n = c + outlets.size();
        List<BlockPos> nodes = new ArrayList<>(channels);
        nodes.addAll(outlets);
        Map<BlockPos, Integer> ids = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ids.put(nodes.get(i), i);
        }
        int[][] graph = new int[n][n];
        double[][] heuristic = new double[n][n];
        for (int x = 0; x < c; x++) {
            BlockPos channel = channels.get(x);
            for (BlockPos neighbour : neighbours.get(channel)) {
                Integer y = ids.get(neighbour);
                if (y == null) {
                    continue;
                }
                graph[x][y] = 1;
                if (heuristic[x][y] == 0) {
                    heuristic[x][y] = heuristic[y][x] = Math.sqrt(distSqr(channel, neighbour));
                }
            }
        }

        // Routes and links: lines 163-190
        Map<BlockPos, Link> links = new LinkedHashMap<>();
        Map<BlockPos, Integer> flows = new HashMap<>();
        List<Path> paths = new ArrayList<>();
        for (int k = 0; k < outlets.size(); k++) {
            List<Integer> route = shortestRoute(graph, heuristic, 0, c + k);
            List<BlockPos> routeCells = new ArrayList<>(route.size());
            for (int id : route) {
                routeCells.add(nodes.get(id));
            }
            paths.add(new Path(outlets.get(k), List.copyOf(routeCells)));

            for (int i = 0; i < routeCells.size() - 1; i++) {
                BlockPos current = routeCells.get(i);
                BlockPos source = routeCells.get(i + 1);
                int rx = source.x() - current.x();
                int ry = source.y() - current.y();
                int rz = source.z() - current.z();
                int distance = Math.abs(rx + ry + rz);
                Direction from = unit(rx / distance, ry / distance, rz / distance);
                links.put(current, new Link(from, distance));
                flows.merge(source, 1, Integer::sum);
            }
        }

        return Optional.of(new Plan(channels, outlets, paths, links, flows, new Link(towardCrucible, 1)));
    }

    /**
     * TFC's {@code findAdjacent} (lines 214-248): the first open channel (channel search) or open mold (mold search) on each
     * ray, in {@link Direction} order, skipping {@code UP}. The DOWN ray looks up to {@link #MAX_DROP} blocks, the others one.
     */
    static List<BlockPos> findAdjacent(Cells cells, BlockPos current, boolean findMolds) {
        List<BlockPos> adjacent = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (dir == Direction.UP) {
                continue;
            }
            int maxDistance = dir == Direction.DOWN ? MAX_DROP : 1;
            for (int i = 1; i <= maxDistance; i++) {
                BlockPos relative = current.offset(dir.dx() * i, dir.dy() * i, dir.dz() * i);
                Cell cell = cells.at(relative);
                if (findMolds && cell == Cell.MOLD) {
                    adjacent.add(relative);
                    break;
                } else if (!findMolds && cell == Cell.CHANNEL) {
                    adjacent.add(relative);
                    break;
                } else if (cell != Cell.AIR) {
                    break;
                }
            }
        }
        return adjacent;
    }

    /**
     * TFC's {@code aStar} (lines 265-344), with its arrays and its tie-breaks kept. Returns the route from {@code goal} back to
     * {@code start}, both included.
     */
    private static List<Integer> shortestRoute(int[][] graph, double[][] heuristic, int start, int goal) {
        int n = graph.length;
        int[] distances = new int[n];
        Arrays.fill(distances, Integer.MAX_VALUE);
        distances[start] = 0;

        int[] parent = new int[n];

        double[] priorities = new double[n];
        Arrays.fill(priorities, Integer.MAX_VALUE);
        priorities[start] = heuristic[start][goal];

        boolean[] visited = new boolean[n];

        while (true) {
            double lowestPriority = Integer.MAX_VALUE;
            int lowestPriorityIndex = -1;
            for (int i = 0; i < n; i++) {
                if (priorities[i] < lowestPriority && !visited[i]) {
                    lowestPriority = priorities[i];
                    lowestPriorityIndex = i;
                }
            }

            if (lowestPriorityIndex == -1) {
                throw new IllegalStateException("Illegal graph: no connection between the origin and the outlet");
            } else if (lowestPriorityIndex == goal) {
                List<Integer> route = new ArrayList<>();
                int currentIndex = lowestPriorityIndex;
                while (currentIndex != start) {
                    route.add(currentIndex);
                    currentIndex = parent[currentIndex];
                }
                route.add(start);
                return route;
            }

            for (int i = 0; i < n; i++) {
                if (graph[lowestPriorityIndex][i] != 0 && !visited[i]) {
                    if (distances[lowestPriorityIndex] + graph[lowestPriorityIndex][i] < distances[i]) {
                        distances[i] = distances[lowestPriorityIndex] + graph[lowestPriorityIndex][i];
                        parent[i] = lowestPriorityIndex;
                        priorities[i] = distances[i] + heuristic[i][goal];
                    }
                }
            }

            visited[lowestPriorityIndex] = true;
        }
    }

    private static double distSqr(BlockPos a, BlockPos b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** The face whose unit offset is exactly (dx, dy, dz), or null when there is none. */
    private static Direction unit(int dx, int dy, int dz) {
        for (Direction d : Direction.values()) {
            if (d.dx() == dx && d.dy() == dy && d.dz() == dz) {
                return d;
            }
        }
        return null;
    }
}
