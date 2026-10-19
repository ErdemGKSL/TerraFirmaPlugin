/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import dev.tfp.core.metal.Metal;
import org.junit.jupiter.api.Test;

/**
 * The shared argument types of the command ports. The ranges are the ones in TFC's {@code IntegerArgumentType.integer}
 * calls (for example {@code PlayerCommand.java} lines 52 and 80, {@code CountBlockCommand.java} line 33).
 */
class CommandArgsTest {
    @Test
    void integerInsideTheRangeIsOk() {
        assertThat(CommandArgs.integer("20", 0, 20)).isEqualTo(new Parsed.Ok<>(20));
        assertThat(CommandArgs.integer("-20", -20, 20)).isEqualTo(new Parsed.Ok<>(-20));
    }

    /** TFC {@code IntegerArgumentType.integer(0, 20)}, PlayerCommand.java line 52: 21 is refused. */
    @Test
    void integerOutsideTheRangeIsRefused() {
        assertThat(CommandArgs.integer("21", 0, 20)).isEqualTo(new Parsed.Refused<>(Error.OUT_OF_RANGE, "21"));
        assertThat(CommandArgs.integer("-1", 0, 20)).isEqualTo(new Parsed.Refused<>(Error.OUT_OF_RANGE, "-1"));
    }

    @Test
    void notAnIntegerIsRefused() {
        assertThat(CommandArgs.integer("ten", 0, 20)).isEqualTo(new Parsed.Refused<>(Error.NOT_AN_INTEGER, "ten"));
        assertThat(CommandArgs.integer("2.5", 0, 20)).isEqualTo(new Parsed.Refused<>(Error.NOT_AN_INTEGER, "2.5"));
    }

    @Test
    void emptyRangeIsAProgrammingError() {
        assertThatThrownBy(() -> CommandArgs.integer("1", 2, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    /** TFC {@code EnumArgument}: a constant by name, here compared without case (AddTrimCommand.java line 46). */
    @Test
    void enumValueIsByNameWithoutCase() {
        assertThat(CommandArgs.enumValue(Metal.class, "bronze")).isEqualTo(new Parsed.Ok<>(Metal.BRONZE));
        assertThat(CommandArgs.enumValue(Metal.class, "BRONZE")).isEqualTo(new Parsed.Ok<>(Metal.BRONZE));
        assertThat(CommandArgs.enumValue(Metal.class, "unobtainium"))
            .isEqualTo(new Parsed.Refused<>(Error.UNKNOWN_VALUE, "unobtainium"));
    }

    /** TFC {@code ItemArgument}: a bare path is in the minecraft namespace; components are dropped. */
    @Test
    void itemIdDefaultsTheNamespaceAndDropsComponents() {
        assertThat(CommandArgs.itemId("iron_ingot")).isEqualTo(new Parsed.Ok<>("minecraft:iron_ingot"));
        assertThat(CommandArgs.itemId("tfc:metal/bronze_ingot[custom_name=\"x\"]"))
            .isEqualTo(new Parsed.Ok<>("tfc:metal/bronze_ingot"));
        assertThat(CommandArgs.itemId("minecraft:stick{Count:2}")).isEqualTo(new Parsed.Ok<>("minecraft:stick"));
    }

    @Test
    void itemIdOutsideTheSyntaxIsRefused() {
        assertThat(CommandArgs.itemId("Iron_Ingot")).isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "Iron_Ingot"));
        assertThat(CommandArgs.itemId("[x]")).isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "[x]"));
        assertThat(CommandArgs.itemId("a:b:c")).isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "a:b:c"));
    }
}
