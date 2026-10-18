/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (getCompost, AdditionType,
 * the tag order: poisons, browns low/medium/high, greens low/medium/high)
 */
package dev.tfp.core.compost;

import java.util.Set;

/**
 * What a held item adds to a composter. The item tags are data, so the Paper glue passes the set of tiers the item is in and
 * {@link #classify} picks the first match in TFC's order: poisons, then browns, then greens. The "rotten" flag of the stack
 * halves the high-tier amounts, as in TFC.
 *
 * @param kind   the kind of addition
 * @param amount the compost the item adds, {@code 0} unless the kind is green or brown
 */
public record CompostAddition(Kind kind, int amount) {

    /** The kinds of addition. */
    public enum Kind {
        /** Not compost; the composter passes the use on to the block. */
        NOTHING,
        GREEN,
        BROWN,
        /** Poison: rots the bin. */
        POISON
    }

    /** The composter tags, in the order TFC checks them. */
    public enum Tier {
        POISON,
        BROWN_LOW,
        BROWN_MEDIUM,
        BROWN_HIGH,
        GREEN_LOW,
        GREEN_MEDIUM,
        GREEN_HIGH
    }

    /** The addition of an item that is in no composter tag. */
    public static final CompostAddition NOTHING = new CompostAddition(Kind.NOTHING, 0);

    public CompostAddition {
        if (amount < 0 || amount > CompostRules.MAX_AMOUNT) {
            throw new IllegalArgumentException("amount must be 0..16: " + amount);
        }
        if (kind == Kind.NOTHING || kind == Kind.POISON) {
            if (amount != 0) {
                throw new IllegalArgumentException(kind + " adds no amount: " + amount);
            }
        } else if (amount == 0) {
            throw new IllegalArgumentException(kind + " needs an amount");
        }
    }

    /**
     * The addition of an item in the given tiers. The first tier in TFC's order wins, so an item that is both a brown and a
     * green adds brown. A rotten item adds 2 instead of 4 for the high tiers.
     *
     * @param tiers  the composter tiers the item is in
     * @param rotten whether the stack is rotten
     * @return the addition
     */
    public static CompostAddition classify(Set<Tier> tiers, boolean rotten) {
        if (tiers.contains(Tier.POISON)) {
            return new CompostAddition(Kind.POISON, 0);
        }
        if (tiers.contains(Tier.BROWN_LOW)) {
            return new CompostAddition(Kind.BROWN, 1);
        }
        if (tiers.contains(Tier.BROWN_MEDIUM)) {
            return new CompostAddition(Kind.BROWN, 2);
        }
        if (tiers.contains(Tier.BROWN_HIGH)) {
            return new CompostAddition(Kind.BROWN, rotten ? 2 : 4);
        }
        if (tiers.contains(Tier.GREEN_LOW)) {
            return new CompostAddition(Kind.GREEN, 1);
        }
        if (tiers.contains(Tier.GREEN_MEDIUM)) {
            return new CompostAddition(Kind.GREEN, 2);
        }
        if (tiers.contains(Tier.GREEN_HIGH)) {
            return new CompostAddition(Kind.GREEN, rotten ? 2 : 4);
        }
        return NOTHING;
    }
}
