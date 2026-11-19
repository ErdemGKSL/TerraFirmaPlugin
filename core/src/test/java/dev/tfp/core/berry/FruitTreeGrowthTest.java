/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.FruitTreeGrowth.Placement;
import dev.tfp.core.berry.FruitTreeGrowth.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/** PAR-FARM-08: the growing branch's geometry, a literal port checked on small terrains with scripted draws. */
class FruitTreeGrowthTest {
    /** A terrain: offsets in {@code solid} are not free, in {@code body} are the tree's body; the ground is the plane y = -1 by default. */
    static final class Grid implements FruitTreeGrowth.Terrain {
        final Set<String> solid = new HashSet<>();
        final Set<String> body = new HashSet<>();
        boolean heightOk = true;

        static String k(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        Grid ground() {
            return ground(-1);
        }

        Grid ground(int y) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    solid.add(k(x, y, z));
                }
            }
            return this;
        }

        /** A two block trunk under the branch, on ground at y = -3. */
        Grid trunk() {
            ground(-3);
            body.add(k(0, -1, 0));
            body.add(k(0, -2, 0));
            return this;
        }

        @Override
        public boolean canGrowInto(int dx, int dy, int dz) {
            return !solid.contains(k(dx, dy, dz)) && !body.contains(k(dx, dy, dz));
        }

        @Override
        public boolean isBody(int dx, int dy, int dz) {
            return body.contains(k(dx, dy, dz));
        }

        @Override
        public boolean plantable(int dx, int dy, int dz) {
            return solid.contains(k(dx, dy, dz)) && dy == -1;
        }

        @Override
        public boolean aboveInBuildHeight() {
            return heightOk;
        }
    }

    /** Scripted draws: the ints for nextInt(4) and the booleans in order. */
    static RandomGenerator script(int[] ints, boolean... bools) {
        return new RandomGenerator() {
            int i;
            int b;

            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return ints[i++ % ints.length] % bound;
            }

            @Override
            public boolean nextBoolean() {
                return bools[b++ % bools.length];
            }
        };
    }

    @Test
    void aBranchOnTheGroundGrowsUpwardAsBodyPlusGrowing() {
        List<Placement> p = FruitTreeGrowth.grow(new Grid().ground(), 0, 2, script(new int[] {0}, false));
        assertThat(p).containsExactly(new Placement(0, 0, 0, Type.BODY, 0, 0), new Placement(0, 1, 0, Type.GROWING, 0, 2));
    }

    @Test
    void aBlockedSkyDoesNothing() {
        Grid g = new Grid().ground();
        g.solid.add(Grid.k(0, 1, 0));
        assertThat(FruitTreeGrowth.grow(g, 0, 2, script(new int[] {0}, false))).isEmpty();
        Grid h = new Grid().ground();
        h.heightOk = false;
        assertThat(FruitTreeGrowth.grow(h, 0, 2, script(new int[] {0}, false))).isEmpty();
    }

    @Test
    void stageThreeOnlyTurnsIntoABody() {
        assertThat(FruitTreeGrowth.grow(new Grid().ground(), 3, 3, script(new int[] {0}, false)))
            .containsExactly(new Placement(0, 0, 0, Type.BODY, 3, 0));
    }

    @Test
    void aTallTrunkForksSidewaysOnTheDrawnDirections() {
        Grid g = new Grid().trunk();
        // saplings 2, stage 0: two forks. Draws: east (1) then north (0); the first fork's boolean is true (double), the second false.
        List<Placement> p = FruitTreeGrowth.grow(g, 0, 2, script(new int[] {1, 0}, true, false));
        assertThat(p).containsExactly(
            new Placement(0, 0, 0, Type.BODY, 0, 0), new Placement(1, 0, 0, Type.GROWING, 1, 2),
            new Placement(0, 0, -1, Type.GROWING, 1, 2),
            new Placement(0, 0, 0, Type.BODY, 0, 0));
    }

    @Test
    void aForkNeedsFreeGroundBelowItsSide() {
        Grid g = new Grid().trunk();
        g.solid.add(Grid.k(1, -1, 0)); // east of the branch: the block under the fork target is solid
        List<Placement> p = FruitTreeGrowth.grow(g, 0, 1, script(new int[] {1}, false));
        assertThat(p).containsExactly(new Placement(0, 0, 0, Type.BODY, 0, 0));
    }

    @Test
    void stageTwoDoesNotFork() {
        Grid g = new Grid().trunk();
        assertThat(FruitTreeGrowth.grow(g, 2, 4, script(new int[] {0}, false))).isEmpty();
    }

    @Test
    void theDrawOrderIsOneDirectionPerTryAndOneBooleanPerFork() {
        Grid g = new Grid().trunk();
        int[] ints = {0, 0, 2}; // north twice (already used, skipped), then south
        List<Placement> p = FruitTreeGrowth.grow(g, 0, 2, script(ints, false, false));
        assertThat(p).extracting(Placement::dz).contains(-1, 1);
        assertThat(p.stream().filter(x -> x.type() == Type.GROWING).count()).isEqualTo(2);
    }

    @Test
    void leavesGoOnEmptySidesExceptBelowAndOnlyAboveTreeParts() {
        boolean[] air = new boolean[1];
        FruitTreeGrowth.LeafTerrain open = new FruitTreeGrowth.LeafTerrain() {
            @Override
            public boolean isAir(int dx, int dy, int dz) {
                return !(dx == 1 && dy == 0 && dz == 0); // east is taken
            }

            @Override
            public boolean airLeavesOrBranch(int dx, int dy, int dz) {
                return true;
            }
        };
        assertThat(FruitTreeGrowth.leafOffsets(open)).hasSize(4).noneMatch(o -> o[1] == -1);
        FruitTreeGrowth.LeafTerrain solidBelow = new FruitTreeGrowth.LeafTerrain() {
            @Override
            public boolean isAir(int dx, int dy, int dz) {
                return true;
            }

            @Override
            public boolean airLeavesOrBranch(int dx, int dy, int dz) {
                return false;
            }
        };
        assertThat(FruitTreeGrowth.leafOffsets(solidBelow)).isEmpty();
        assertThat(air).hasSize(1);
    }

    @Test
    void sameDrawsSamePlacements() {
        Grid g = new Grid().trunk();
        assertThat(FruitTreeGrowth.grow(g, 0, 3, script(new int[] {1, 2, 3}, true, false, true)))
            .isEqualTo(FruitTreeGrowth.grow(g, 0, 3, script(new int[] {1, 2, 3}, true, false, true)));
    }
}
