/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onLivingHurt),
 * src/main/java/net/dries007/tfc/util/PhysicalDamageType.java @ c3b4d9536 (getTypeForSource, getTypeForItem, calculateMultiplier)
 */
package dev.tfp.core.health;

import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * TFC {@code ForgeEventHandler.onLivingHurt} as a pure rule: the incoming damage of one hit, scaled by the attacker's
 * forging bonus, the physical resistance multiplier of the target, and (players only) divided by the health modifier.
 * Pure and thread-safe; the glue snapshots the hit into a {@link Hit} on the target's thread.
 */
public final class IncomingDamage {
    private IncomingDamage() {}

    /** TFC's item tags for the weapon's type ({@code TFCTags.Items.DEALS_*_DAMAGE}), checked in this order. */
    public static final String ITEM_PIERCING = "tfp:deals_piercing_damage";
    public static final String ITEM_SLASHING = "tfp:deals_slashing_damage";
    public static final String ITEM_CRUSHING = "tfp:deals_crushing_damage";
    /** TFC's entity type tags for the attacker's type ({@code TFCTags.Entities.DEALS_*_DAMAGE}; empty at c3b4d9536). */
    public static final String ENTITY_PIERCING = "tfp:deals_piercing_damage";
    public static final String ENTITY_SLASHING = "tfp:deals_slashing_damage";
    public static final String ENTITY_CRUSHING = "tfp:deals_crushing_damage";

    /**
     * One hit as seen on the target's thread.
     *
     * @param amount          the incoming damage (vanilla HP)
     * @param damageTypeId    the vanilla damage type id ({@code minecraft:mob_attack}, ...)
     * @param attackerType    the entity type id of the source's causing entity ({@code getEntity}), or empty
     * @param weaponId        the item id in the causing entity's main hand when it is a living entity holding something
     * @param forgingBonus    the damage multiplier of the weapon's forging bonus (1 without one)
     * @param targetType      the target's entity type id
     * @param armorIds        the item ids of the target's worn armour (empty slots left out)
     * @param healthModifier  the target player's health modifier, or empty when the target is not a player
     */
    public record Hit(float amount, String damageTypeId, Optional<String> attackerType, Optional<String> weaponId,
            float forgingBonus, String targetType, List<String> armorIds, Optional<Float> healthModifier) {
        public Hit {
            Objects.requireNonNull(damageTypeId, "damageTypeId");
            Objects.requireNonNull(attackerType, "attackerType");
            Objects.requireNonNull(weaponId, "weaponId");
            Objects.requireNonNull(targetType, "targetType");
            armorIds = List.copyOf(armorIds);
            Objects.requireNonNull(healthModifier, "healthModifier");
        }
    }

    /**
     * The outcome of one hit.
     *
     * @param amount     the damage to apply
     * @param type       the physical type of the source, or empty
     * @param resistance the summed resistance
     * @param multiplier the physical multiplier ({@code e^(-0.01 x resistance)})
     * @param unchanged  true for {@link Float#MAX_VALUE} (the kill command), which TFC leaves alone
     */
    public record Result(float amount, Optional<PhysicalDamageType> type, float resistance, float multiplier,
            boolean unchanged) {
    }

    /** TFC {@code getTypeForItem}: the weapon's type from its item tags. */
    public static Optional<PhysicalDamageType> typeForItem(String itemId, TagLookup tags) {
        if (tags.contains(TagType.ITEM, ITEM_PIERCING, itemId)) {
            return Optional.of(PhysicalDamageType.PIERCING);
        }
        if (tags.contains(TagType.ITEM, ITEM_SLASHING, itemId)) {
            return Optional.of(PhysicalDamageType.SLASHING);
        }
        if (tags.contains(TagType.ITEM, ITEM_CRUSHING, itemId)) {
            return Optional.of(PhysicalDamageType.CRUSHING);
        }
        return Optional.empty();
    }

    /**
     * TFC {@code getTypeForSource}: the damage type's tags first, then the causing entity's main-hand weapon, then the
     * causing entity's type tags. TFC's {@code bypasses_damage_resistances} tag is empty at c3b4d9536, so no source
     * skips the typing.
     */
    public static Optional<PhysicalDamageType> typeForSource(String damageTypeId, Optional<String> attackerType,
            Optional<String> weaponId, TagLookup tags) {
        Optional<PhysicalDamageType> byTag = PhysicalDamageType.fromVanillaDamageType(damageTypeId);
        if (byTag.isPresent() || attackerType.isEmpty()) {
            return byTag;
        }
        if (weaponId.isPresent()) {
            Optional<PhysicalDamageType> byWeapon = typeForItem(weaponId.get(), tags);
            if (byWeapon.isPresent()) {
                return byWeapon;
            }
        }
        String attacker = attackerType.get();
        if (tags.contains(TagType.ENTITY_TYPE, ENTITY_PIERCING, attacker)) {
            return Optional.of(PhysicalDamageType.PIERCING);
        }
        if (tags.contains(TagType.ENTITY_TYPE, ENTITY_SLASHING, attacker)) {
            return Optional.of(PhysicalDamageType.SLASHING);
        }
        if (tags.contains(TagType.ENTITY_TYPE, ENTITY_CRUSHING, attacker)) {
            return Optional.of(PhysicalDamageType.CRUSHING);
        }
        return Optional.empty();
    }

    /** TFC {@code onLivingHurt}: forging bonus, physical multiplier, then the player's health modifier. */
    public static Result apply(Hit hit, DamageResistances resistances, TagLookup tags) {
        if (hit.amount() == Float.MAX_VALUE) {
            return new Result(hit.amount(), Optional.empty(), 0f, 1f, true);
        }
        float amount = hit.amount() * hit.forgingBonus();
        Optional<PhysicalDamageType> type = typeForSource(hit.damageTypeId(), hit.attackerType(), hit.weaponId(), tags);
        PhysicalResistance entity = resistances.entity(hit.targetType(), tags).orElse(null);
        List<PhysicalResistance> armor = new ArrayList<>(hit.armorIds().size());
        for (String id : hit.armorIds()) {
            resistances.item(id, tags).ifPresent(armor::add);
        }
        float resistance = PhysicalResistances.resistance(type, entity, armor);
        float multiplier = PhysicalResistances.multiplier(resistance);
        amount *= multiplier;
        if (hit.healthModifier().isPresent()) {
            amount /= hit.healthModifier().get();
        }
        return new Result(amount, type, resistance, multiplier, false);
    }
}
