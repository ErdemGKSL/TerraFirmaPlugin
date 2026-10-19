/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/AddTrimCommand.java @ c3b4d9536
 */
package dev.tfp.core.command;

import dev.tfp.core.command.CommandArgs.Error;
import dev.tfp.core.command.CommandArgs.Parsed;
import dev.tfp.core.metal.Metal;
import java.util.List;
import java.util.Optional;

/**
 * {@code /tfp trim <material> <pattern> [metal]} (TFC {@code AddTrimCommand}, M6-CMD-01). Without a metal the trim is
 * added to the item in the main hand; with a metal, four trimmed armour pieces of that metal (helmet, chestplate,
 * greaves, boots) are given to the player.
 *
 * <p>The rule: the material is checked first, then the pattern, and the trim is added only when both are known trim
 * items (the registry lookups are glue, passed in as a {@link Lookup}). A metal without all its armour parts is not
 * armour: nothing is given.
 *
 * <p>TFC's form with a metal does not stop at the first failure. When the lookups fail, it still gives all four pieces,
 * untrimmed, and reports the failure once per piece. {@link #armorSet} reproduces that, so the glue sends one message
 * per piece in {@link ArmorSet#pieces()}.
 */
public final class AddTrimCommand {
    private AddTrimCommand() {
    }

    /** The armour pieces of a metal set, in TFC's order. */
    public enum ArmorPiece {
        HELMET, CHESTPLATE, GREAVES, BOOTS
    }

    /** Why a trim or a set is refused, TFC's three failures. */
    public enum Failure {
        /** The metal does not have all its armour parts. */
        NOT_ARMOR,
        /** The material item is not a trim material. */
        BAD_MATERIAL,
        /** The pattern item is not a trim pattern. */
        BAD_TEMPLATE
    }

    /**
     * The registry lookups of the two items, from the glue ({@code TrimMaterials.getFromIngredient} and
     * {@code TrimPatterns.getFromTemplate} on each item's default instance).
     *
     * @param materialFound whether the material item is a trim material
     * @param patternFound  whether the pattern item is a trim pattern
     */
    public record Lookup(boolean materialFound, boolean patternFound) {
    }

    /**
     * One parsed line.
     *
     * @param material the material item id, with its namespace
     * @param pattern  the pattern item id, with its namespace
     * @param metal    the metal of the armour set, or empty for the trim of the held item
     */
    public record Request(String material, String pattern, Optional<Metal> metal) {
        public Request {
            if (material == null || pattern == null || metal == null) {
                throw new IllegalArgumentException("material, pattern and metal are required");
            }
        }
    }

    /**
     * The armour set of a metal.
     *
     * @param pieces  the pieces to give, in TFC's order; empty when the metal is not armour
     * @param failure the failure to report: {@link Failure#NOT_ARMOR} (no pieces), or the lookup failure, which is
     *                reported once per piece with the pieces untrimmed; empty when every piece is trimmed
     */
    public record ArmorSet(List<ArmorPiece> pieces, Optional<Failure> failure) {
        public ArmorSet {
            pieces = List.copyOf(pieces);
        }
    }

    /**
     * Parses the words after {@code trim}: {@code <material> <pattern>}, optionally followed by a {@code <metal>} name.
     *
     * @param args the words after the command name, never null
     */
    public static Parsed<Request> parse(List<String> args) {
        String materialWord = CommandArgs.word(args, 0);
        String patternWord = CommandArgs.word(args, 1);
        if (materialWord == null || patternWord == null) {
            return new Parsed.Refused<>(Error.MISSING_ARGUMENT, "");
        }
        final String materialId;
        switch (CommandArgs.itemId(materialWord)) {
            case Parsed.Refused<String> refused -> {
                return new Parsed.Refused<>(refused.error(), refused.token());
            }
            case Parsed.Ok<String> ok -> materialId = ok.value();
        }
        final String patternId;
        switch (CommandArgs.itemId(patternWord)) {
            case Parsed.Refused<String> refused -> {
                return new Parsed.Refused<>(refused.error(), refused.token());
            }
            case Parsed.Ok<String> ok -> patternId = ok.value();
        }
        String metalWord = CommandArgs.word(args, 2);
        Optional<Metal> metal = Optional.empty();
        if (metalWord != null) {
            switch (CommandArgs.enumValue(Metal.class, metalWord)) {
                case Parsed.Refused<Metal> refused -> {
                    return new Parsed.Refused<>(refused.error(), refused.token());
                }
                case Parsed.Ok<Metal> ok -> metal = Optional.of(ok.value());
            }
        }
        Parsed.Refused<Request> extra = CommandArgs.endOfLine(args, 3);
        if (extra != null) {
            return extra;
        }
        return new Parsed.Ok<>(new Request(materialId, patternId, metal));
    }

    /**
     * The failure of the trim of one item, TFC's {@code addTrim} checks in order: the material, then the pattern.
     *
     * @return the failure, or empty when the trim may be added
     */
    public static Optional<Failure> trimFailure(Lookup lookup) {
        if (!lookup.materialFound()) {
            return Optional.of(Failure.BAD_MATERIAL);
        }
        if (!lookup.patternFound()) {
            return Optional.of(Failure.BAD_TEMPLATE);
        }
        return Optional.empty();
    }

    /**
     * TFC {@code spawnSet}: a metal without all its parts is refused, with no pieces. Otherwise the four pieces are
     * given, trimmed when {@link #trimFailure} is empty and untrimmed (with that failure) when it is not.
     *
     * @param metalHasAllParts TFC's {@code Metal.allParts()}, from the glue's metal data
     * @param lookup           the registry lookups of the two items
     */
    public static ArmorSet armorSet(boolean metalHasAllParts, Lookup lookup) {
        if (!metalHasAllParts) {
            return new ArmorSet(List.of(), Optional.of(Failure.NOT_ARMOR));
        }
        return new ArmorSet(List.of(ArmorPiece.values()), trimFailure(lookup));
    }
}
