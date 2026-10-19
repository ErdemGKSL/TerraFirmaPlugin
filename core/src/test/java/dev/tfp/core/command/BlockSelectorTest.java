/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.BlockSelector.Kind;
import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The block argument of {@code /tfp count} (TFC {@code CountBlockCommand.java}, line 34: {@code BlockPredicateArgument}).
 * The forms are Brigadier's: an id, an id with a state filter, or a tag with a {@code #}.
 */
class BlockSelectorTest {
    @Test
    void bareIdDefaultsToMinecraftNamespace() {
        assertThat(BlockSelector.parse("stone"))
            .isEqualTo(new Parsed.Ok<>(new BlockSelector(Kind.BLOCK, "minecraft:stone", Map.of())));
    }

    @Test
    void stateFilterIsParsedSortedByName() {
        var selector = ((Parsed.Ok<BlockSelector>) BlockSelector.parse("tfc:wood[leaves=true,axis=y]")).value();
        assertThat(selector.kind()).isEqualTo(Kind.BLOCK);
        assertThat(selector.name()).isEqualTo("tfc:wood");
        assertThat(selector.properties().keySet()).containsExactly("axis", "leaves");
    }

    @Test
    void hashNamesATag() {
        assertThat(BlockSelector.parse("#tfc:ore_bearing"))
            .isEqualTo(new Parsed.Ok<>(new BlockSelector(Kind.TAG, "tfc:ore_bearing", Map.of())));
    }

    @Test
    void nbtAndBadFiltersAreRefused() {
        assertThat(BlockSelector.parse("chest{Items:[]}")).isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "chest{Items:[]}"));
        assertThat(BlockSelector.parse("stone[axis]")).isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "stone[axis]"));
        assertThat(BlockSelector.parse("stone[a=b,a=c]")).isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "stone[a=b,a=c]"));
        assertThat(BlockSelector.parse("#tfc:x[a=b]")).isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "#tfc:x[a=b]"));
        assertThat(BlockSelector.parse("#")).isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "#"));
    }

    @Test
    void idMatchesOnlyWithTheRequiredProperties() {
        var log = new BlockSelector(Kind.BLOCK, "tfc:wood", Map.of("axis", "y"));
        assertThat(log.matches("tfc:wood", Set.of(), Map.of("axis", "y", "leaves", "false"))).isTrue();
        assertThat(log.matches("tfc:wood", Set.of(), Map.of("axis", "x"))).isFalse();
        assertThat(log.matches("tfc:leaves", Set.of(), Map.of("axis", "y"))).isFalse();
        var any = new BlockSelector(Kind.BLOCK, "minecraft:stone", Map.of());
        assertThat(any.matches("minecraft:stone", Set.of(), Map.of("anything", "x"))).isTrue();
    }

    @Test
    void tagMatchesTheBlocksTags() {
        var ore = new BlockSelector(Kind.TAG, "tfc:ore_bearing", Map.of());
        assertThat(ore.matches("tfc:rock/ore/x", Set.of("tfc:ore_bearing"), Map.of())).isTrue();
        assertThat(ore.matches("tfc:rock/raw/x", Set.of("tfc:rock"), Map.of())).isFalse();
    }
}
