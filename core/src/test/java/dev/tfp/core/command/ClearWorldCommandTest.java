/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.command.ClearWorldCommand.Kind;
import dev.tfp.core.command.ClearWorldCommand.Preset;
import dev.tfp.core.command.ClearWorldCommand.Request;
import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code /tfp clearworld} from TFC {@code ClearWorldCommand.java}: the radius (line 49), the preset (line 50, default
 * ALL at line 53), the preset sets (lines 97 to 125) and the sweep (lines 71 to 86).
 */
class ClearWorldCommandTest {
    /** ClearWorldCommand.java lines 49-53: the preset defaults to ALL when it is left out. */
    @Test
    void presetDefaultsToAll() {
        assertThat(ClearWorldCommand.parse(List.of("5"))).isEqualTo(new Parsed.Ok<>(new Request(5, Preset.ALL)));
        assertThat(ClearWorldCommand.parse(List.of("5", "earth"))).isEqualTo(new Parsed.Ok<>(new Request(5, Preset.EARTH)));
    }

    @Test
    void radiusAndPresetAreValidated() {
        assertThat(ClearWorldCommand.parse(List.of("0"))).isEqualTo(new Parsed.Refused<>(Error.OUT_OF_RANGE, "0"));
        assertThat(ClearWorldCommand.parse(List.of("250"))).isEqualTo(new Parsed.Ok<>(new Request(250, Preset.ALL)));
        assertThat(ClearWorldCommand.parse(List.of("5", "bogus"))).isEqualTo(new Parsed.Refused<>(Error.UNKNOWN_VALUE, "bogus"));
        assertThat(ClearWorldCommand.parse(List.of())).isEqualTo(new Parsed.Refused<>(Error.MISSING_ARGUMENT, ""));
    }

    /**
     * ClearWorldCommand.java lines 97-125: ALL takes every block but air (line 80 checks air first). RAW_ROCK takes only
     * raw rock. EARTH takes the raw, hardened and gravel rock, dirt, grass and sand (lines 104-111). NOT_ORE takes
     * everything but vein ore (lines 114-124).
     */
    @Test
    void presetSetsAreTfcs() {
        assertThat(Preset.ALL.removes(Kind.AIR)).isFalse();
        assertThat(Preset.ALL.removes(Kind.OTHER)).isTrue();
        assertThat(Preset.RAW_ROCK.removes(Kind.RAW_ROCK)).isTrue();
        assertThat(Preset.RAW_ROCK.removes(Kind.GRAVEL_ROCK)).isFalse();
        for (Kind kind : List.of(Kind.RAW_ROCK, Kind.HARDENED_ROCK, Kind.GRAVEL_ROCK, Kind.DIRT, Kind.GRASS, Kind.SAND)) {
            assertThat(Preset.EARTH.removes(kind)).as(kind.name()).isTrue();
        }
        assertThat(Preset.EARTH.removes(Kind.ORE)).isFalse();
        assertThat(Preset.EARTH.removes(Kind.OTHER)).isFalse();
        assertThat(Preset.NOT_ORE.removes(Kind.ORE)).isFalse();
        assertThat(Preset.NOT_ORE.removes(Kind.OTHER)).isTrue();
        assertThat(Preset.NOT_ORE.removes(Kind.AIR)).isFalse();
    }

    /**
     * ClearWorldCommand.java lines 71-86 on a 3 by 3 square with surface height 2 everywhere except the centre column
     * at (1,1), whose surface is 1. Every column has a block at height 0 and at height 1 (raw rock), except the centre
     * column's height 0, which is ore. The sweep runs x then z then y, from the bottom up to the surface.
     */
    @Test
    void sweepRemovesTheMatchingBlocksUpToTheSurface() {
        List<String> removed = new ArrayList<>();
        int count = ClearWorldCommand.sweep(1, Preset.ALL, 0,
            (dx, dz) -> (dx == 1 && dz == 1) ? 1 : 2,
            (dx, y, dz) -> y == 0 ? (dx == 0 && dz == 0 ? Kind.ORE : Kind.OTHER) : Kind.RAW_ROCK,
            (dx, y, dz) -> removed.add(dx + "," + y + "," + dz));
        assertThat(count).isEqualTo(17);
        assertThat(removed).hasSize(17).startsWith("-1,0,-1", "-1,1,-1");
        assertThat(removed).doesNotContain("1,1,1");
    }

    /** The same grid with NOT_ORE: the one ore block at (0,0,0) stays, the other 16 blocks go. */
    @Test
    void notOreKeepsTheOreBlock() {
        List<String> removed = new ArrayList<>();
        int count = ClearWorldCommand.sweep(1, Preset.NOT_ORE, 0,
            (dx, dz) -> (dx == 1 && dz == 1) ? 1 : 2,
            (dx, y, dz) -> y == 0 ? (dx == 0 && dz == 0 ? Kind.ORE : Kind.OTHER) : Kind.RAW_ROCK,
            (dx, y, dz) -> removed.add(dx + "," + y + "," + dz));
        assertThat(count).isEqualTo(16);
        assertThat(removed).doesNotContain("0,0,0");
    }

    /** The same grid with RAW_ROCK: only the raw rock at height 1 in the eight columns that reach it. */
    @Test
    void rawRockPresetTakesOnlyRawRock() {
        int count = ClearWorldCommand.sweep(1, Preset.RAW_ROCK, 0,
            (dx, dz) -> (dx == 1 && dz == 1) ? 1 : 2,
            (dx, y, dz) -> y == 0 ? Kind.OTHER : Kind.RAW_ROCK,
            (dx, y, dz) -> { });
        assertThat(count).isEqualTo(8);
    }

    @Test
    void airIsNeverRemoved() {
        int count = ClearWorldCommand.sweep(1, Preset.ALL, 0, (dx, dz) -> 3, (dx, y, dz) -> Kind.AIR, (dx, y, dz) -> { });
        assertThat(count).isZero();
    }

    @Test
    void radiusOutsideTheRangeIsAProgrammingError() {
        assertThatThrownBy(() -> ClearWorldCommand.sweep(0, Preset.ALL, 0, (dx, dz) -> 1, (dx, y, dz) -> Kind.OTHER, (dx, y, dz) -> { }))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
