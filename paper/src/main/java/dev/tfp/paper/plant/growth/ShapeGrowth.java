/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TFCVineBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeFlowerBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/plant/GrowingBranchingCactusBlock.java @ c3b4d9536 (randomTick dispatch)
 */
package dev.tfp.paper.plant.growth;

import dev.tfp.core.plant.PlantGrowth;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * PAR-FARM-29: the random ticks of the shapes that are not columns: the jungle vine, the giant kelp flower and the saguaro top.
 * {@link #run} plays the (capped) random ticks one block had while its chunk was away, each through the pure rule of the shape,
 * then refreshes the connections of the pipe plants (giant kelp, saguaro) around the blocks it wrote, which TFC's
 * {@code updateShape} does on every block change.
 */
public final class ShapeGrowth {
    /** The most random ticks one block plays per sweep: the shapes cap themselves (five vines in reach, age 5, heights 3 and 7). */
    public static final int MAX_TICKS = 8;
    /** The most blocks one saguaro sweep follows (its new tops included). */
    static final int MAX_CACTUS_STEPS = 48;
    /** The sample instants of the temperature over the elapsed time. */
    static final int TEMPERATURE_SAMPLES = 8;

    /** What a block grows as. */
    public enum Shape { VINE, KELP_FLOWER, CACTUS_TOP }

    private ShapeGrowth() {
    }

    /** The shape of a custom block id, when it is one of these. */
    public static Optional<Shape> shape(String id) {
        return switch (id) {
            case VineGrowth.ID -> Optional.of(Shape.VINE);
            case KelpTreeGrowth.FLOWER -> Optional.of(Shape.KELP_FLOWER);
            case CactusGrowth.TOP -> Optional.of(Shape.CACTUS_TOP);
            default -> Optional.empty();
        };
    }

    /** Whether a flower or a top still ticks ({@code isRandomlyTicking}): the kelp flower below age 5. */
    public static boolean ticks(BlockStateRef state) {
        return !state.id().equals(KelpTreeGrowth.FLOWER) || KelpTreeGrowth.intProp(state, "age") < PlantGrowth.KELP_FLOWER_MAX_AGE;
    }

    /**
     * Plays up to {@code ticks} random ticks (at most {@link #MAX_TICKS}) of the block at the position.
     *
     * @param warm the vine's temperature gate over the elapsed time: of {@link #TEMPERATURE_SAMPLES} sample instants, how many had
     *             the instant temperature above 0 C (a tick passes with that share; ignored for the other shapes)
     * @return the number of blocks written
     */
    public static int run(GrowthWorld world, int x, int y, int z, int ticks, int warm, RandomGenerator random) {
        Optional<BlockStateRef> state = world.custom(x, y, z);
        if (state.isEmpty()) {
            return 0;
        }
        Optional<Shape> shape = shape(state.get().id());
        if (shape.isEmpty()) {
            return 0;
        }
        Recorder recorder = new Recorder(world);
        int n = Math.min(ticks, MAX_TICKS);
        switch (shape.get()) {
            case VINE -> {
                for (int i = 0; i < n; i++) {
                    if (random.nextInt(TEMPERATURE_SAMPLES) < warm) {
                        VineGrowth.tick(recorder, x, y, z, random);
                    }
                }
            }
            case KELP_FLOWER -> {
                BlockStateRef flower = state.get();
                for (int i = 0; i < n; i++) {
                    if (KelpTreeGrowth.tick(recorder, x, y, z, flower, random)) {
                        break; // the flower became a body or a dead flower
                    }
                }
            }
            case CACTUS_TOP -> cactus(recorder, x, y, z, n, random);
        }
        for (int[] at : new ArrayList<>(recorder.written)) {
            PipeRefresh.around(world, at[0], at[1], at[2]);
        }
        return recorder.written.size();
    }

    /** One saguaro top: its ticks, then the tops it grew (the new trunk top and the arm) with the ticks left. */
    private static void cactus(GrowthWorld w, int x, int y, int z, int ticks, RandomGenerator random) {
        record Item(int x, int y, int z, int left) {
        }
        ArrayDeque<Item> queue = new ArrayDeque<>();
        queue.add(new Item(x, y, z, ticks));
        int steps = 0;
        while (!queue.isEmpty() && steps++ < MAX_CACTUS_STEPS) {
            Item item = queue.poll();
            for (int t = 0; t < item.left(); t++) {
                Optional<BlockStateRef> top = CactusGrowth.topAt(w, item.x(), item.y(), item.z());
                if (top.isEmpty()) {
                    break;
                }
                if (CactusGrowth.tick(w, item.x(), item.y(), item.z(), top.get(), random)
                    && CactusGrowth.topAt(w, item.x(), item.y(), item.z()).isEmpty()) {
                    int left = item.left() - t - 1;
                    if (left > 0) {
                        List<int[]> next = new ArrayList<>();
                        next.add(new int[] {item.x(), item.y() + 1, item.z()});
                        for (Face face : Face.HORIZONTAL) {
                            next.add(new int[] {item.x() + 2 * face.dx, item.y(), item.z() + 2 * face.dz});
                        }
                        for (int[] at : next) {
                            if (CactusGrowth.topAt(w, at[0], at[1], at[2]).isPresent()) {
                                queue.add(new Item(at[0], at[1], at[2], left));
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    /** A {@link GrowthWorld} that remembers where the rules wrote. */
    private static final class Recorder implements GrowthWorld {
        private final GrowthWorld delegate;
        final List<int[]> written = new ArrayList<>();

        Recorder(GrowthWorld delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<BlockStateRef> custom(int x, int y, int z) {
            return delegate.custom(x, y, z);
        }

        @Override
        public boolean air(int x, int y, int z) {
            return delegate.air(x, y, z);
        }

        @Override
        public boolean water(int x, int y, int z) {
            return delegate.water(x, y, z);
        }

        @Override
        public boolean sturdy(int x, int y, int z, Face face) {
            return delegate.sturdy(x, y, z, face);
        }

        @Override
        public boolean tagged(int x, int y, int z, String tag) {
            return delegate.tagged(x, y, z, tag);
        }

        @Override
        public boolean is(int x, int y, int z, String key) {
            return delegate.is(x, y, z, key);
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            return delegate.loaded(x, y, z);
        }

        @Override
        public int minY() {
            return delegate.minY();
        }

        @Override
        public int maxY() {
            return delegate.maxY();
        }

        @Override
        public boolean set(int x, int y, int z, BlockStateRef state) {
            boolean ok = delegate.set(x, y, z, state);
            if (ok) {
                written.add(new int[] {x, y, z});
            }
            return ok;
        }
    }
}
