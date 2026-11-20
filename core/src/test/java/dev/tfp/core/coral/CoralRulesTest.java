/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/coral/Coral.java @ c3b4d9536 (golden rows),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCCoralPlantBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/plant/coral/CoralWallFanBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/plant/coral/TFCSeaPickleBlock.java @ c3b4d9536,
 *       src/main/resources/data/tfc/loot_table/blocks/coral @ c3b4d9536 (the dropped item of each table)
 */
package dev.tfp.core.coral;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.coral.CoralRules.Colour;
import dev.tfp.core.coral.CoralRules.Coral;
import dev.tfp.core.coral.CoralRules.Type;
import dev.tfp.core.world.Direction;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** PAR-WORLD-25: the coral and sea pickle rules against TFC's blocks and loot tables. */
class CoralRulesTest {
    @Test
    void thirtyIdsInRegistrationOrderAsTfcNamesThem() {
        final List<String> ids = CoralRules.all().stream().map(Coral::id).toList();
        assertThat(ids).hasSize(30).doesNotHaveDuplicates();
        assertThat(ids.subList(0, 6)).containsExactly("tfp:coral/tube_dead_coral", "tfp:coral/tube_coral",
            "tfp:coral/tube_dead_coral_fan", "tfp:coral/tube_coral_fan", "tfp:coral/tube_dead_coral_wall_fan",
            "tfp:coral/tube_coral_wall_fan");
        assertThat(ids.getLast()).isEqualTo("tfp:coral/horn_coral_wall_fan");
        for (Coral c : CoralRules.all()) {
            assertThat(Coral.parse(c.id())).contains(c);
        }
        assertThat(Coral.parse("tfp:coral/tube_coral_block")).isEmpty();
        assertThat(Coral.parse("minecraft:tube_coral")).isEmpty();
        assertThat(Coral.parse(CoralRules.SEA_PICKLE)).isEmpty();
    }

    /** {@code Coral.BlockType}: dead block, items, lootFrom and the tool rule of every type (TFC's factory lambdas). */
    @Test
    void blockTypesFollowCoralJava() {
        // type, dead, needsItem, loot table item, requiresCorrectToolForDrops
        final Object[][] golden = {
            {Type.DEAD_CORAL, Type.DEAD_CORAL, true, "dead_coral", true},
            {Type.CORAL, Type.DEAD_CORAL, true, "coral", false},
            {Type.DEAD_CORAL_FAN, Type.DEAD_CORAL_FAN, false, "dead_coral_fan", true},
            {Type.CORAL_FAN, Type.DEAD_CORAL_FAN, false, "coral_fan", false},
            {Type.DEAD_CORAL_WALL_FAN, Type.DEAD_CORAL_WALL_FAN, false, "dead_coral_fan", true},
            {Type.CORAL_WALL_FAN, Type.DEAD_CORAL_WALL_FAN, false, "coral_fan", false},
        };
        assertThat(golden.length).isEqualTo(Type.values().length);
        for (Object[] row : golden) {
            final Type t = (Type) row[0];
            assertThat(t.dead()).as(t + " dead").isEqualTo(row[1]);
            assertThat(t.needsItem()).as(t + " item").isEqualTo(row[2]);
            assertThat(t.lootFrom().serial()).as(t + " loot").isEqualTo(row[3]);
            assertThat(t.requiresCorrectTool()).as(t + " tool").isEqualTo(row[4]);
            assertThat(t.living()).isEqualTo(!t.serial().startsWith("dead_"));
        }
        assertThat(new Coral(Colour.FIRE, Type.CORAL_WALL_FAN).lootFrom().id()).isEqualTo("tfp:coral/fire_coral_fan");
        assertThat(CoralRules.dieInto(new Coral(Colour.HORN, Type.CORAL_WALL_FAN)).id())
            .isEqualTo("tfp:coral/horn_dead_coral_wall_fan");
    }

    /** {@code scanForWater}: own fluid, else any of the six neighbours; unknown neighbours defer the answer. */
    @Test
    void scanForWaterTruthTable() {
        final Boolean[] dry = {false, false, false, false, false, false};
        assertThat(CoralRules.scanForWater(true, dry)).contains(true);
        assertThat(CoralRules.scanForWater(false, dry)).contains(false);
        for (Direction d : Direction.values()) {
            final Boolean[] one = dry.clone();
            one[d.ordinal()] = true;
            assertThat(CoralRules.scanForWater(false, one)).as(d.name()).contains(true);
            final Boolean[] unknown = dry.clone();
            unknown[d.ordinal()] = null;
            assertThat(CoralRules.scanForWater(false, unknown)).as(d.name()).isEqualTo(Optional.empty());
            assertThat(CoralRules.scanForWater(true, unknown)).contains(true);
        }
        final Boolean[] wetAndUnknown = {null, true, false, false, false, false};
        assertThat(CoralRules.scanForWater(false, wetAndUnknown)).contains(true);
    }

    /** {@code 60 + random.nextInt(40)}: one draw, 60..99. */
    @Test
    void dieDelayIsOneDrawOfForty() {
        final Random a = new Random(42);
        final Random b = new Random(42);
        IntStream.range(0, 2000).forEach(i -> {
            final int d = CoralRules.dieDelay(a);
            assertThat(d).isEqualTo(60 + b.nextInt(40)).isBetween(60, 99);
        });
    }

    /** Standing corals stand on a sturdy top; a wall fan hangs on the sturdy face of the block behind it. */
    @Test
    void supportSides() {
        for (Type t : Type.values()) {
            for (Direction f : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                if (t.wall()) {
                    assertThat(CoralRules.support(t, f)).isEqualTo(f.opposite());
                    assertThat(CoralRules.supportFace(t, f)).isEqualTo(f);
                } else {
                    assertThat(CoralRules.support(t, f)).isEqualTo(Direction.DOWN);
                    assertThat(CoralRules.supportFace(t, f)).isEqualTo(Direction.UP);
                }
            }
        }
        assertThat(CoralRules.canSurvive(true)).isTrue();
        assertThat(CoralRules.canSurvive(false)).isFalse();
    }

    /** {@code TFCBlocks.SEA_PICKLE}: light {@code isDead ? 0 : 3 + 3 * pickles}; support, placement and drops. */
    @Test
    void seaPickle() {
        assertThat(IntStream.rangeClosed(1, 4).map(n -> CoralRules.seaPickleLight(n, true)).toArray())
            .containsExactly(6, 9, 12, 15);
        assertThat(IntStream.rangeClosed(1, 4).map(n -> CoralRules.seaPickleLight(n, false)).toArray())
            .containsExactly(0, 0, 0, 0);
        assertThat(CoralRules.seaPickleDead(false)).isTrue();
        assertThat(CoralRules.seaPickleCanSurvive(true, false)).isTrue();
        assertThat(CoralRules.seaPickleCanSurvive(false, true)).isTrue();
        assertThat(CoralRules.seaPickleCanSurvive(false, false)).isFalse();
        assertThat(IntStream.rangeClosed(1, 4).map(CoralRules::seaPickleAdd).toArray()).containsExactly(2, 3, 4, 4);
        assertThat(CoralRules.seaPickleAccepts(3)).isTrue();
        assertThat(CoralRules.seaPickleAccepts(4)).isFalse();
        assertThat(IntStream.rangeClosed(1, 4).map(CoralRules::seaPickleDrops).toArray()).containsExactly(1, 2, 3, 4);
    }
}
