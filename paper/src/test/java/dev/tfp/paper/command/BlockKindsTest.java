/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.ClearWorldCommand.Kind;
import org.junit.jupiter.api.Test;

/** PAR-DATA-01: the clearworld kinds of the generated block ids. */
class BlockKindsTest {
    @Test
    void everyFamilyOfTfcsPresetsHasItsKind() {
        assertThat(BlockKinds.ofCustom("tfp:rock/raw/granite")).isEqualTo(Kind.RAW_ROCK);
        assertThat(BlockKinds.ofCustom("tfp:rock/hardened/basalt")).isEqualTo(Kind.HARDENED_ROCK);
        assertThat(BlockKinds.ofCustom("tfp:rock/gravel/chert")).isEqualTo(Kind.GRAVEL_ROCK);
        assertThat(BlockKinds.ofCustom("tfp:dirt/silt")).isEqualTo(Kind.DIRT);
        assertThat(BlockKinds.ofCustom("tfp:grass/loam")).isEqualTo(Kind.GRASS);
        assertThat(BlockKinds.ofCustom("tfp:sand/yellow")).isEqualTo(Kind.SAND);
        assertThat(BlockKinds.ofCustom("tfp:ore/rich_native_copper/granite")).isEqualTo(Kind.ORE);
    }

    @Test
    void everythingElseIsOther() {
        assertThat(BlockKinds.ofCustom("tfp:rock/bricks/granite")).isEqualTo(Kind.OTHER);
        assertThat(BlockKinds.ofCustom("tfp:clay/loam")).isEqualTo(Kind.OTHER);
        assertThat(BlockKinds.ofCustom("minecraft:stone")).isEqualTo(Kind.OTHER);
    }
}
