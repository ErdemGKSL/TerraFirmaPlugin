/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/FissureFeature.java @ c3b4d9536, with
 * src/main/java/net/dries007/tfc/world/feature/FissureConfig.java (the defaults and Decoration.getState),
 * src/main/java/net/dries007/tfc/util/collections/IWeighted.java (get draws one double, singletons included) and
 * src/main/resources/data/tfc/worldgen/configured_feature/{volcano,topaz_volcano,diamond_volcano}_fissure.json
 */
package dev.tfp.core.worldgen.volcano;

import dev.tfp.core.worldgen.chunk.PlaceholderRocks;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.List;
import java.util.Set;

/**
 * TFC's fissures: a chain of short vertical tubes of a fluid with rock walls, each piece one step sideways from the last,
 * kept within a radius of a center, down to a minimum depth. Used by the volcano fissures (lava in raw basalt) and by the
 * hot springs (their fluid in hardened rock). Pure: it reads and writes a {@link VolcanoLevel} only.
 */
final class Fissure {
    /** The horizontal directions in vanilla's {@code Direction.Plane.HORIZONTAL} order: north, east, south, west. */
    private static final int[][] HORIZONTAL = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
    /** The raw rocks the gem decorations replace: the igneous and metamorphic ones (13 entries in each TFC config). */
    private static final Set<String> DECORATED = Set.of("rhyolite", "basalt", "andesite", "dacite", "granite", "diorite",
        "gabbro", "quartzite", "slate", "phyllite", "schist", "gneiss", "marble");
    private static final boolean[] DECORATED_ROCK = decoratedRocks();
    /** The rock index of raw basalt, the wall of TFC's volcano fissures. */
    static final int BASALT = PlaceholderRocks.names().indexOf("basalt");

    private Fissure() {}

    private static boolean[] decoratedRocks() {
        final List<String> names = PlaceholderRocks.names();
        final boolean[] out = new boolean[names.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = DECORATED.contains(names.get(i));
        }
        return out;
    }

    /** {@code FissureConfig.Decoration}: per step of a tube, {@code count} tries of 1 in {@code rarity} within {@code radius}. */
    record Decoration(Gem gem, int rarity, int radius, int count) {
        /** {@code getState}: the gem ore of the raw rock there (one double drawn), or -1 for a block it does not replace. */
        int rockFor(VolcanoLevel level, int x, int y, int z, XoroshiroRandomSource random) {
            final int rock = level.rawRock(x, y, z);
            if (rock >= 0 && DECORATED_ROCK[rock]) {
                random.nextDouble(); // IWeighted.get(random) of the singleton list
                return rock;
            }
            return -1;
        }
    }

    /** A placed block: its kind and rock. */
    record State(VolcanoKind kind, int rock) {
        static final State LAVA = new State(VolcanoKind.LAVA, -1);
        static final State AIR = new State(VolcanoKind.AIR, -1);
        static final State SPRING_WATER = new State(VolcanoKind.SPRING_WATER, -1);
    }

    /**
     * {@code FissureFeature.place} of the volcano fissures: {@code 1 + nextInt(count)} fissures, each starting at the origin
     * and wandering around a center within {@code radius} of it, at the {@code WORLD_SURFACE_WG} height there.
     */
    static void place(VolcanoLevel level, XoroshiroRandomSource rand, int[] pos, State fluid, State wall, int count, int radius,
                      int minDepth, int minPieces, int maxPieces, int maxPieceLength, Decoration decoration) {
        final VolcanoLevel.Pos mutablePos = new VolcanoLevel.Pos();
        final int placeCount = 1 + rand.nextInt(count);
        for (int i = 0; i < placeCount; i++) {
            mutablePos.setWithOffset(pos, rand.nextInt(radius) - rand.nextInt(radius), 0, rand.nextInt(radius) - rand.nextInt(radius));
            mutablePos.y = level.view().worldSurface(mutablePos.x, mutablePos.z);
            placeFissure(level, pos, mutablePos.immutable(), mutablePos, rand, fluid, wall, minPieces, maxPieces, maxPieceLength,
                minDepth, radius, decoration);
        }
    }

    /** {@code FissureFeature.placeFissure}, draw for draw, reading the scratch position as TFC does. */
    static void placeFissure(VolcanoLevel level, int[] startPos, int[] centerPos, VolcanoLevel.Pos mutablePos,
                             XoroshiroRandomSource random, State insideState, State wallState, int minPieces, int maxPieces,
                             int maxPieceLength, int minDepth, int radius, Decoration decoration) {
        final int pieces = minPieces + random.nextInt(maxPieces - minPieces);
        int[] topPos = startPos.clone();
        for (int i = 0; i < pieces; i++) {
            // Tube
            final int pieceDepth = 1 + random.nextInt(maxPieceLength);
            for (int dy = 1; dy <= pieceDepth; dy++) {
                // Cut off before running into rivers
                if (level.isAir(mutablePos.x, mutablePos.y - 2, mutablePos.z)) {
                    return;
                }
                set(level, mutablePos.setWithOffset(topPos, 0, -dy, 0), insideState);
                set(level, mutablePos.setWithOffset(topPos, -1, -dy, 0), wallState);
                set(level, mutablePos.setWithOffset(topPos, 1, -dy, 0), wallState);
                set(level, mutablePos.setWithOffset(topPos, 0, -dy, -1), wallState);
                set(level, mutablePos.setWithOffset(topPos, 0, -dy, 1), wallState);

                if (decoration != null) {
                    // At each step, place count / rarity blocks (average) within radius, and +/- 2 blocks vertically
                    for (int j = 0; j < decoration.count(); j++) {
                        if (random.nextInt(decoration.rarity()) == 0) {
                            final int ox = random.nextInt(decoration.radius()) - random.nextInt(decoration.radius());
                            final int oy = random.nextInt(3) - random.nextInt(3) - dy;
                            final int oz = random.nextInt(decoration.radius()) - random.nextInt(decoration.radius());
                            mutablePos.setWithOffset(topPos, ox, oy, oz);
                            final int rock = decoration.rockFor(level, mutablePos.x, mutablePos.y, mutablePos.z, random);
                            if (rock >= 0) {
                                level.set(mutablePos.x, mutablePos.y, mutablePos.z, VolcanoKind.GEM_ORE, rock, decoration.gem());
                            }
                        }
                    }
                }
            }

            // Branch: topPos is now above the branch, ready for the next tube
            final int branch = randomBoundedDirection(random, centerPos, topPos, radius);
            final int[] d = HORIZONTAL[branch];
            topPos = mutablePos.setWithOffset(topPos, 0, -pieceDepth, 0).move(d[0], d[1], d[2]).immutable();

            // Place the joining pieces
            set(level, mutablePos.set(topPos[0], topPos[1], topPos[2]), insideState);
            set(level, mutablePos.setWithOffset(topPos, 0, 1, 0), wallState);
            final int opposite = (branch + 2) & 3;
            for (int dir = 0; dir < 4; dir++) {
                if (dir != opposite) { // Fill all sides except the one we came from
                    final int[] h = HORIZONTAL[dir];
                    set(level, mutablePos.setWithOffset(topPos, h[0], h[1], h[2]), wallState);
                }
            }

            if (topPos[1] < minDepth) {
                break;
            }
        }
    }

    private static void set(VolcanoLevel level, VolcanoLevel.Pos p, State state) {
        level.set(p.x, p.y, p.z, state.kind(), state.rock(), null);
    }

    /** The index in {@link #HORIZONTAL} of a random direction, turned back toward the center past {@code radius}. */
    private static int randomBoundedDirection(XoroshiroRandomSource random, int[] center, int[] target, int radius) {
        final int direction = random.nextInt(4); // Direction.Plane.HORIZONTAL.getRandomDirection
        final int distX = target[0] - center[0];
        final int distZ = target[2] - center[2];
        return switch (direction) {
            case 1 -> distX > radius ? 3 : direction; // east, else west
            case 3 -> distX < -radius ? 1 : direction; // west, else east
            case 0 -> distZ < -radius ? 2 : direction; // north, else south
            case 2 -> distZ > radius ? 0 : direction; // south, else north
            default -> direction;
        };
    }
}
