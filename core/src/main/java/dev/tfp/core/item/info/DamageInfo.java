/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/PhysicalDamageType.java @ c3b4d9536
 * (addTooltipInfo, calculatePercentageForDisplay)
 */
package dev.tfp.core.item.info;

import dev.tfp.core.health.DamageResistances;
import dev.tfp.core.health.IncomingDamage;
import dev.tfp.core.health.PhysicalDamageType;
import dev.tfp.core.health.PhysicalResistance;
import dev.tfp.core.tag.TagLookup;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * TFC {@code PhysicalDamageType.addTooltipInfo}: the "Deals X Damage" line of a weapon (its {@code deals_*_damage}
 * item tag, piercing before slashing before crushing as TFC checks them) and the "Resistances: ..." line of an armour
 * piece ({@code item_damage_resistance}, shown as slashing, piercing, crushing). Pure and thread-safe.
 */
public final class DamageInfo {
    private DamageInfo() {}

    public static final String DEALS_KEY = "tooltip.deals_damage.";
    public static final String RESISTS_KEY = "tooltip.resists_damage";
    public static final String IMMUNE_KEY = "tooltip.immune_to_damage";

    /**
     * One resistance as TFC shows it: {@code String.format("%.0f%%", (1 - e^(-0.01 r)) * 100)}, or "Immune" when the
     * share is at least 0.999999.
     *
     * @param immune  whether the line reads {@value #IMMUNE_KEY}
     * @param percent the formatted percentage ({@code "26%"}); empty when immune
     */
    public record Share(boolean immune, String percent) {
        /** TFC {@code calculatePercentageForDisplay}, in TFC's float arithmetic. */
        public static Share of(float resistance) {
            float multiplier = 1 - (float) Math.pow(Math.E, -0.01 * resistance);
            if (multiplier >= 0.999999) {
                return new Share(true, "");
            }
            return new Share(false, String.format(Locale.ROOT, "%.0f%%", multiplier * 100));
        }
    }

    /** The message key of the weapon line of {@code itemId}, empty when it deals no typed damage. */
    public static Optional<String> dealsKey(String itemId, TagLookup tags) {
        return itemId == null ? Optional.empty()
                : IncomingDamage.typeForItem(itemId, tags).map(DamageInfo::dealsKey);
    }

    /** {@code tooltip.deals_damage.<type>}. */
    public static String dealsKey(PhysicalDamageType type) {
        return DEALS_KEY + type.serializedName();
    }

    /** The three shares of {@code itemId}'s resistance in TFC's line order (slashing, piercing, crushing), or empty. */
    public static Optional<List<Share>> resistances(String itemId, DamageResistances tables, TagLookup tags) {
        return tables.item(itemId, tags).map(DamageInfo::shares);
    }

    /** The three shares of {@code r} in TFC's line order: slashing, piercing, crushing. */
    public static List<Share> shares(PhysicalResistance r) {
        return List.of(Share.of(r.slashing()), Share.of(r.piercing()), Share.of(r.crushing()));
    }
}
