/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCShieldItem.java @ c3b4d9536 (getDamageBlocked),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onShieldBlock),
 *       src/main/java/net/dries007/tfc/common/TFCTiers.java @ c3b4d9536 (attack damage bonus per metal)
 */
package dev.tfp.core.shield;

import java.util.Map;

/**
 * The share of a blocked hit a shield absorbs. TFC scales the blocked damage by {@code clampedMap(tierBonus, 0, 12, 0.25, 1)}
 * for its metal shields and by 0.25 for the vanilla shield; projectile hits are left as vanilla blocks them. Pure and
 * thread-safe.
 */
public final class ShieldRules {
    /** TFC's vanilla shield factor. */
    public static final float VANILLA_FACTOR = 0.25f;

    /** {@code TFCTiers} attack damage bonus per metal. */
    public static final Map<String, Float> TIER_BONUS = Map.ofEntries(
        Map.entry("copper", 3.25f), Map.entry("bronze", 4.0f), Map.entry("bismuth_bronze", 4.0f),
        Map.entry("black_bronze", 4.25f), Map.entry("wrought_iron", 4.75f), Map.entry("steel", 5.75f),
        Map.entry("black_steel", 7.0f), Map.entry("blue_steel", 9.0f), Map.entry("red_steel", 9.0f));

    private ShieldRules() {
    }

    /** {@code Mth.clampedMap(bonus, 0, 12, 0.25, 1)}. */
    public static float damageBlocked(float tierBonus) {
        float t = Math.max(0f, Math.min(1f, tierBonus / 12f));
        return 0.25f + (1f - 0.25f) * t;
    }

    /** The factor for a metal shield, or {@link #VANILLA_FACTOR} for an unknown metal. */
    public static float factorForMetal(String metal) {
        Float bonus = TIER_BONUS.get(metal);
        return bonus == null ? VANILLA_FACTOR : damageBlocked(bonus);
    }
}
