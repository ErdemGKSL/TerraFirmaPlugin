/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/Lifecycle.java @ c3b4d9536 (active, advanceTowards)
 */
package dev.tfp.core.berry;

/**
 * The lifecycle stage of a seasonal plant: dormant, healthy, flowering or fruiting. A stage moves one step per update towards the
 * stage the month expects ({@link #advanceTowards}); fruit can only be picked while fruiting.
 */
public enum Lifecycle {
    DORMANT,
    HEALTHY,
    FLOWERING,
    FRUITING;

    private static final Lifecycle[] VALUES = values();

    /** Every stage but dormant grows, flowers or fruits. */
    public boolean active() {
        return this != DORMANT;
    }

    /**
     * TFC {@code Lifecycle.advanceTowards}: one stage towards {@code other} when it is greater, straight to {@code other} when it is
     * lower, and unchanged otherwise.
     */
    public Lifecycle advanceTowards(Lifecycle other) {
        if (other.ordinal() < ordinal()) {
            return other;
        }
        if (other.ordinal() > ordinal() && this != FRUITING) {
            return VALUES[ordinal() + 1];
        }
        return this;
    }
}
