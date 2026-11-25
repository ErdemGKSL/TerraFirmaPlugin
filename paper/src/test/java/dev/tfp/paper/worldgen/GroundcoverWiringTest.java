/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.deposit.DepositBlock;
import dev.tfp.core.worldgen.deposit.DepositKind;
import dev.tfp.core.worldgen.deposit.DepositMaterial;
import dev.tfp.core.worldgen.deposit.Deposits;
import dev.tfp.core.worldgen.groundcover.Groundcover;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/** M2-GEN-11: the generator's groundcover and deposit blocks, and the rule that writes them into one chunk. */
class GroundcoverWiringTest {
    @Test
    void everyKindHasAPlaceholder() {
        for (Groundcover.Kind kind : Groundcover.Kind.values()) {
            assertThat(GroundcoverBlocks.placeholder(kind, false)).as(kind.name()).isNotNull();
            assertThat(GroundcoverBlocks.placeholder(kind, true)).as(kind.name()).isNotNull();
        }
        // The small ore indicator must not look like a loose rock
        assertThat(GroundcoverBlocks.placeholder(Groundcover.Kind.SMALL_ORE, false))
            .isNotEqualTo(GroundcoverBlocks.placeholder(Groundcover.Kind.LOOSE_ROCK, false));
        for (Deposits.Feature feature : Deposits.FEATURES) {
            for (DepositKind kind : feature.disc().replace().values()) {
                final Material m = GroundcoverBlocks.placeholder(new DepositBlock(0, 0, 0, kind, DepositMaterial.RAW_ROCK,
                    feature.disc().mineral(), feature.feature()));
                assertThat(m).as(feature.feature()).isNotNull().isNotIn(Material.GRAVEL, Material.DIRT, Material.STONE);
            }
        }
    }

    /** PAR-WORLD-32: the CraftEngine blocks of PAR-WORLD-31 the terrain writes. */
    @Test
    void ceIdsNameThePar31Blocks() {
        assertThat(GroundcoverBlocks.ceId(new Groundcover(0, 64, 0, Groundcover.Kind.MOSSY_LOOSE_ROCK, "tfp:rock/mossy_loose/chert", 2,
            false))).isEqualTo("tfp:rock/mossy_loose/chert[count=2]");
        assertThat(GroundcoverBlocks.ceId(new Groundcover(0, 64, 0, Groundcover.Kind.SMALL_ORE, "tfp:ore/small_hematite", 1, false)))
            .isEqualTo("tfp:ore/small_hematite");
        assertThat(GroundcoverBlocks.ceId(new DepositBlock(0, 60, 0, DepositKind.ORE_DEPOSIT, DepositMaterial.GRAVEL, "cassiterite",
            "tfc:deposit/cassiterite"), "granite")).isEqualTo("tfp:deposit/cassiterite/granite");
        for (DepositKind kind : DepositKind.values()) {
            if (kind != DepositKind.ORE_DEPOSIT) {
                assertThat(GroundcoverBlocks.ceId(new DepositBlock(0, 60, 0, kind, DepositMaterial.DIRT, "clay", "f"), "granite"))
                    .as(kind.name()).isNull();
            }
        }
        assertThat(GroundcoverBlocks.ceId(new dev.tfp.core.worldgen.groundcover.Boulders.Block(0, 70, 0, 1,
            dev.tfp.core.worldgen.groundcover.Boulders.Material.MOSSY_COBBLE, "f"), List.of("granite", "diorite")))
            .isEqualTo("tfp:rock/mossy_cobble/diorite");
        // MOLLISOL keeps the block (only the soil variant changes)
        assertThat(GroundcoverBlocks.placeholder(new DepositBlock(0, 60, 0, DepositKind.MOLLISOL, DepositMaterial.DIRT, "mollisol",
            "f"))).isNull();
    }

    private static Groundcover cover(int x, int y, int z) {
        return new Groundcover(x, y, z, Groundcover.Kind.LOOSE_ROCK, "tfp:rock/loose/granite", 1, false);
    }

    @Test
    void writesOnlyFreePositionsOfItsChunkAndTheFirstClaimWins() {
        final Map<String, Groundcover> world = new HashMap<>();
        final GroundcoverBlocks.Target target = new GroundcoverBlocks.Target() {
            @Override
            public boolean free(int localX, int y, int localZ) {
                // y 70 is solid ground in this test
                return y != 70 && !world.containsKey(localX + "," + y + "," + localZ);
            }

            @Override
            public void place(int localX, int y, int localZ, Groundcover cover) {
                assertThat(localX).isBetween(0, 15);
                assertThat(localZ).isBetween(0, 15);
                world.put(localX + "," + y + "," + localZ, cover);
            }
        };
        final Groundcover first = cover(-30, 65, 17);
        final Groundcover second = new Groundcover(-30, 65, 17, Groundcover.Kind.SMALL_ORE, "tfp:ore/small_malachite", 1, false);
        final List<Groundcover> covers = List.of(
            first, second,
            cover(-33, 65, 17),   // other chunk (x)
            cover(-20, 65, 32),   // other chunk (z)
            cover(-29, 70, 20),   // not free
            cover(-29, -64, 20),  // at the bottom of the world
            cover(-29, 320, 20),  // above the top
            cover(-17, 66, 31));
        final int written = GroundcoverBlocks.write(covers, -2, 1, -64, 320, target);
        assertThat(written).isEqualTo(2);
        assertThat(world).hasSize(2);
        assertThat(world.get((-30 & 15) + ",65," + (17 & 15))).isEqualTo(first);
        assertThat(world.get((-17 & 15) + ",66," + (31 & 15))).isNotNull();
    }
}
