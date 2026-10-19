/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.AddTrimCommand.ArmorPiece;
import dev.tfp.core.command.AddTrimCommand.Failure;
import dev.tfp.core.command.AddTrimCommand.Lookup;
import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import dev.tfp.core.metal.Metal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@code /tfp trim} from TFC {@code AddTrimCommand.java}: the held-item form (lines 74 to 89, material then pattern),
 * and the armour-set form (lines 55 to 70). The lookups are the registry's answers, passed in by the glue.
 */
class AddTrimCommandTest {
    static final String MATERIAL = "minecraft:redstone";
    static final String PATTERN = "minecraft:sentry_armor_trim_smithing_template";

    /** AddTrimCommand.java lines 42-45: the held-item form takes a material and a pattern. */
    @Test
    void parsesTheHeldItemForm() {
        assertThat(AddTrimCommand.parse(List.of("redstone", "sentry_armor_trim_smithing_template")))
            .isEqualTo(new Parsed.Ok<>(new AddTrimCommand.Request(MATERIAL, PATTERN, Optional.empty())));
    }

    /** AddTrimCommand.java: the set form adds a metal (EnumArgument, so the name is matched without case). */
    @Test
    void parsesTheArmourSetForm() {
        assertThat(AddTrimCommand.parse(List.of("redstone", "sentry_armor_trim_smithing_template", "bronze")))
            .isEqualTo(new Parsed.Ok<>(new AddTrimCommand.Request(MATERIAL, PATTERN, Optional.of(Metal.BRONZE))));
    }

    @Test
    void shapeErrors() {
        assertThat(AddTrimCommand.parse(List.of("redstone")))
            .isEqualTo(new Parsed.Refused<>(Error.MISSING_ARGUMENT, ""));
        assertThat(AddTrimCommand.parse(List.of("redstone", "Pattern")))
            .isEqualTo(new Parsed.Refused<>(Error.BAD_SYNTAX, "Pattern"));
        assertThat(AddTrimCommand.parse(List.of("redstone", "pattern", "unobtainium")))
            .isEqualTo(new Parsed.Refused<>(Error.UNKNOWN_VALUE, "unobtainium"));
        assertThat(AddTrimCommand.parse(List.of("a", "b", "bronze", "extra")))
            .isEqualTo(new Parsed.Refused<>(Error.TOO_MANY_ARGUMENTS, "extra"));
    }

    /** AddTrimCommand.java lines 78-85: the material is checked before the pattern. */
    @Test
    void materialIsCheckedBeforePattern() {
        assertThat(AddTrimCommand.trimFailure(new Lookup(true, true))).isEmpty();
        assertThat(AddTrimCommand.trimFailure(new Lookup(false, true))).contains(Failure.BAD_MATERIAL);
        assertThat(AddTrimCommand.trimFailure(new Lookup(true, false))).contains(Failure.BAD_TEMPLATE);
        assertThat(AddTrimCommand.trimFailure(new Lookup(false, false))).contains(Failure.BAD_MATERIAL);
    }

    /** AddTrimCommand.java lines 55-58: a metal without all its parts gives nothing and reports NOT_ARMOR. */
    @Test
    void metalWithoutAllPartsIsNotArmour() {
        var set = AddTrimCommand.armorSet(false, new Lookup(true, true));
        assertThat(set.pieces()).isEmpty();
        assertThat(set.failure()).contains(Failure.NOT_ARMOR);
    }

    /**
     * AddTrimCommand.java lines 63 to 68: the four pieces in TFC's order, each trimmed, and given.
     */
    @Test
    void armourSetHasFourPiecesInTfcOrder() {
        var set = AddTrimCommand.armorSet(true, new Lookup(true, true));
        assertThat(set.pieces()).containsExactly(ArmorPiece.HELMET, ArmorPiece.CHESTPLATE, ArmorPiece.GREAVES, ArmorPiece.BOOTS);
        assertThat(set.failure()).isEmpty();
    }

    /**
     * AddTrimCommand.java lines 63 to 68 with a bad material: TFC's loop ignores addTrim's result, so all four pieces are
     * still given, untrimmed, and the failure is reported once per piece. The set keeps the four pieces and the failure.
     */
    @Test
    void failedTrimStillGivesTheUntrimmedSet() {
        var set = AddTrimCommand.armorSet(true, new Lookup(false, true));
        assertThat(set.pieces()).hasSize(4);
        assertThat(set.failure()).contains(Failure.BAD_MATERIAL);
    }
}
