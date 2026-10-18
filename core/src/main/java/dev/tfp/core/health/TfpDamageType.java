/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/TFCDamageTypes.java @ c3b4d9536 (damage types and their exhaustion, burning data)
 */
package dev.tfp.core.health;

import java.util.Locale;

/**
 * TFC's own damage types ({@code TFCDamageTypes} with the generated {@code damage_type/} data). Ids are {@code tfp:*}
 * here; the messages use the {@code tfp.} key prefix.
 *
 * <p>Exhaustion is the vanilla damage-type field (food exhaustion per hit, in vanilla's units); {@code burning} is the
 * {@code effects} field. Both are the values from TFC's JSON, copied at c3b4d9536.
 *
 * @param exhaustion the food exhaustion added by one hit
 * @param burning    whether the hit sets the target on fire
 */
public enum TfpDamageType {
    /** Grilled food and cooking surfaces. */
    GRILL(0.1f, true),
    /** Boiling in a pot. */
    POT(0.1f, true),
    /** Thirst damage ({@code PlayerInfo.tick}, every 100 ticks at zero thirst). */
    DEHYDRATION(0f, false),
    /** Plucking a living animal. */
    PLUCK(0f, false),
    /** Caught in a lobster trap. */
    LOBSTER_TRAP(0f, false);

    private final float exhaustion;
    private final boolean burning;

    TfpDamageType(float exhaustion, boolean burning) {
        this.exhaustion = exhaustion;
        this.burning = burning;
    }

    /** The resource id, {@code tfp:} plus the lower-case name. */
    public String id() {
        return "tfp:" + name().toLowerCase(Locale.ROOT);
    }

    public float exhaustion() {
        return exhaustion;
    }

    public boolean burning() {
        return burning;
    }
}
