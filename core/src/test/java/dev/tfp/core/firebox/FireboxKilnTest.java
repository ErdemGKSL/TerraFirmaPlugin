/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.firebox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.firebox.FireboxKiln.Dir;
import dev.tfp.core.firebox.FireboxKiln.Pos;
import dev.tfp.core.gui.ChestGeometry;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** PAR-HEAT-12: TFC's kiln flood fill, the screen layout and the slot cascade. */
class FireboxKilnTest {
    /** A world of insulating walls and air; everything not a wall is interior air. */
    record Walls(Set<Pos> walls, Set<Pos> solidNonInsulating) implements FireboxKiln.World {
        @Override
        public boolean exterior(Pos pos, Dir moved) {
            return walls.contains(pos);
        }

        @Override
        public boolean interior(Pos pos) {
            return !walls.contains(pos) && !solidNonInsulating.contains(pos);
        }
    }

    /** A closed box of walls around the interior x 0..w-1, y 1..h, z 0..d-1, the firebox at (0, 0, 0) in the floor. */
    static Walls chamber(int w, int h, int d) {
        Set<Pos> walls = new HashSet<>();
        for (int x = -1; x <= w; x++) {
            for (int y = 0; y <= h + 1; y++) {
                for (int z = -1; z <= d; z++) {
                    boolean inside = x >= 0 && x < w && y >= 1 && y <= h && z >= 0 && z < d;
                    if (!inside) {
                        walls.add(new Pos(x, y, z));
                    }
                }
            }
        }
        walls.remove(new Pos(0, 0, 0)); // the firebox
        return new Walls(walls, new HashSet<>());
    }

    @Test
    void closedChamberFloods() {
        List<Pos> filled = FireboxKiln.floodfill(chamber(3, 3, 3), new Pos(0, 0, 0), 500f);
        assertThat(filled).hasSize(27).doesNotHaveDuplicates().contains(new Pos(0, 1, 0), new Pos(2, 3, 2));
    }

    @Test
    void coldFireboxFloodsNothing() {
        assertThat(FireboxKiln.floodfill(chamber(3, 3, 3), new Pos(0, 0, 0), 0f)).isEmpty();
    }

    @Test
    void aHoleOrABlockingEdgeEmptiesTheKiln() {
        Walls open = chamber(3, 3, 3);
        open.walls().remove(new Pos(1, 4, 1)); // a hole in the roof: the fill runs out into the open sky
        assertThat(FireboxKiln.floodfill(open, new Pos(0, 0, 0), 500f)).isEmpty();
        Walls blocked = chamber(3, 3, 3);
        blocked.walls().remove(new Pos(3, 2, 1));
        blocked.solidNonInsulating().add(new Pos(3, 2, 1)); // a full block that is not insulation
        assertThat(FireboxKiln.floodfill(blocked, new Pos(0, 0, 0), 500f)).hasSizeLessThan(27);
    }

    @Test
    void tooManyBlocksEmptiesTheKiln() {
        // TFC counts the capacity down per block taken and gives up when it reaches 0: 63 blocks fit in 64, 64 do not
        assertThat(FireboxKiln.floodfill(chamber(3, 3, 7), new Pos(0, 0, 0), 500f)).hasSize(63);
        assertThat(FireboxKiln.floodfill(chamber(4, 4, 4), new Pos(0, 0, 0), 500f)).isEmpty();
        assertThat(FireboxKiln.floodfill(chamber(5, 5, 5), new Pos(0, 0, 0), 500f)).isEmpty();
        assertThat(FireboxKiln.floodfill(chamber(5, 5, 5), new Pos(0, 0, 0), 800f)).hasSize(125);
    }

    @Test
    void screenLayoutPutsTheSlotsOnTheChestGrid() {
        assertThat(FireboxScreenLayout.DY).isEqualTo(-1);
        assertThat(FireboxScreenLayout.DY_PLAYER).isEqualTo(1);
        assertThat(FireboxScreenLayout.CHEST_SLOTS).containsExactly(3, 4, 5, 6, 12, 13, 14, 15, 21, 22, 23, 24, 30, 31,
            32, 33);
        ChestGeometry chest = FireboxScreenLayout.CHEST;
        for (int i = 0; i < FireboxSim.SLOTS; i++) {
            int slot = FireboxScreenLayout.chestSlot(i);
            assertThat(chest.slotX(slot)).isEqualTo(62 + 18 * (i % 4));
            assertThat(chest.slotY(slot)).isEqualTo(19 + 18 * (i / 4) + FireboxScreenLayout.DY);
            assertThat(FireboxScreenLayout.tfcSlot(slot)).isEqualTo(i);
        }
        assertThat(chest.hotbarY() - (142 + FireboxScreenLayout.TFC_INVENTORY_OFFSET)).isEqualTo(FireboxScreenLayout.DY_PLAYER);
        assertThat(FireboxScreenLayout.frames(0f)).containsEntry(FireboxScreenLayout.THERMOMETER_LAYER, 0);
        assertThat(FireboxScreenLayout.frames(1600f)).containsEntry(FireboxScreenLayout.THERMOMETER_LAYER, 51);
    }

    @Test
    void statusAsTfc() {
        assertThat(FireboxScreenLayout.status(FireboxData.FRESH, 10)).isEqualTo(FireboxScreenLayout.Status.NONE);
        FireboxData hot = new FireboxData(10, 1350f, 1350f, 0, true, 0, 5L);
        assertThat(FireboxScreenLayout.status(hot, 10)).isEqualTo(FireboxScreenLayout.Status.NO_HEAT);
        FireboxData kiln = new FireboxData(10, 1350f, 1350f, 0, true, 20, 5L);
        assertThat(FireboxScreenLayout.status(kiln, 10)).isEqualTo(FireboxScreenLayout.Status.TIME_TO_HEAT);
        assertThat(FireboxScreenLayout.status(kiln, 5 + FireboxSim.timeToHeat(20, 1350f)))
            .isEqualTo(FireboxScreenLayout.Status.HEATED);
    }

    @Test
    void cascadeMovesFuelDown() {
        List<Optional<String>> slots = List.of(Optional.empty(), Optional.of("a"), Optional.empty(), Optional.of("b"));
        assertThat(FireboxScreenLayout.cascade(slots, Optional::isEmpty, Optional.empty()))
            .containsExactly(Optional.of("a"), Optional.of("b"), Optional.empty(), Optional.empty());
    }
}
