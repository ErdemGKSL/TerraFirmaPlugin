/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (vanilla rule overrides)
 */
package dev.tfp.core.vanilla;

import java.util.function.Function;

/**
 * PAR-DATA-10: TFC's "Mechanics - Vanilla Changes" server options, with TFC's defaults. Immutable. Until config.yml
 * carries them (each key is added with its consumer, docs/config.md), they are read from JVM properties
 * {@code tfp.vanilla.<name>}.
 *
 * @param bonemeal             {@code enableVanillaBonemeal} (false): bone meal's instant growth
 * @param skeletonHorses       {@code enableVanillaSkeletonHorseSpawning} (false): thunderstorm trap horses
 * @param golems               {@code enableVanillaGolems} (false): iron and snow golems
 * @param monsters             {@code enableVanillaMonsters} (true): natural monster spawns at all
 * @param monstersOnSurface    {@code enableVanillaMonstersOnSurface} (false): monsters on the surface, in light
 * @param eggThrowing          {@code enableVanillaEggThrowing} (false)
 * @param milkClearsEffects    {@code enableVanillaDrinkingMilkClearsPotionEffects} (false)
 * @param mobEnchantments      {@code enableVanillaMobsSpawningWithEnchantments} (false): enchanted spawn gear
 * @param mobEquipment         {@code enableVanillaMobsSpawningWithVanillaEquipment} (false): vanilla armour and held items
 * @param chickenJockies       {@code enableChickenJockies} (false)
 * @param dispenserLighting    {@code dispenserEnableLighting} (true): dispensers light fires and devices
 */
public record VanillaRules(boolean bonemeal, boolean skeletonHorses, boolean golems, boolean monsters,
        boolean monstersOnSurface, boolean eggThrowing, boolean milkClearsEffects, boolean mobEnchantments,
        boolean mobEquipment, boolean chickenJockies, boolean dispenserLighting) {
    /** TFC's defaults. */
    public static final VanillaRules DEFAULT = new VanillaRules(false, false, false, true, false, false, false, false,
            false, false, true);
    public static final String PREFIX = "tfp.vanilla.";

    /** The defaults with the JVM properties applied (absent or blank = the default). */
    public static VanillaRules fromProperties(Function<String, String> properties) {
        VanillaRules d = DEFAULT;
        return new VanillaRules(bool(properties, "bonemeal", d.bonemeal), bool(properties, "skeletonHorses", d.skeletonHorses),
                bool(properties, "golems", d.golems), bool(properties, "monsters", d.monsters),
                bool(properties, "monstersOnSurface", d.monstersOnSurface), bool(properties, "eggThrowing", d.eggThrowing),
                bool(properties, "milkClearsEffects", d.milkClearsEffects),
                bool(properties, "mobEnchantments", d.mobEnchantments), bool(properties, "mobEquipment", d.mobEquipment),
                bool(properties, "chickenJockies", d.chickenJockies),
                bool(properties, "dispenserLighting", d.dispenserLighting));
    }

    /** The armour slots of TFC's {@code MobMixin.getEquipmentForSlot}, each with the item tag its replacement comes from. */
    public enum ArmorSlot {
        FEET("tfc:mob_feet_armor"), LEGS("tfc:mob_leg_armor"), CHEST("tfc:mob_chest_armor"), HEAD("tfc:mob_head_armor");

        private final String tag;

        ArmorSlot(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public static final String DISABLED_HELD_ITEMS = "tfc:disabled_monster_held_items";
    public static final String SKELETON_WEAPONS = "tfc:skeleton_weapons";

    /**
     * TFC {@code MobMixin.getEquipmentForSlot}: with vanilla equipment off, an armour piece a mob spawns with is replaced by
     * a random member of the slot's TFC tag, or by nothing when the tag is empty (the vanilla piece is never kept).
     *
     * @param members the tag members (any order); {@code pick} maps a size to an index in {@code [0, size)}
     * @return the replacement id, or empty for none
     */
    public static java.util.Optional<String> randomMember(java.util.List<String> members,
            java.util.function.IntUnaryOperator pick) {
        return members.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(members.get(pick.applyAsInt(members.size())));
    }

    private static boolean bool(Function<String, String> p, String name, boolean fallback) {
        String v = p.apply(PREFIX + name);
        return v == null || v.isBlank() ? fallback : Boolean.parseBoolean(v.trim());
    }

    /**
     * TFC {@code onLivingSpawnCheck}: whether a natural-type monster spawn is denied. With monsters off every spawn is
     * denied; with them on and the surface off, a spawn is denied in light (raw brightness not 0; slimes exempt), at or above
     * the surface, or on a block outside {@code tfc:monster_spawns_on}.
     *
     * @param rawBrightness    the larger of block light and raw sky light at the spawn
     * @param atOrAboveSurface the spawn y is at or above the world-surface heightmap
     * @param groundSpawnable  the block below is in {@code tfc:monster_spawns_on}
     */
    public boolean deniesMonster(boolean slime, int rawBrightness, boolean atOrAboveSurface, boolean groundSpawnable) {
        if (!monsters) {
            return true;
        }
        if (monstersOnSurface) {
            return false;
        }
        return (!slime && rawBrightness != 0) || atOrAboveSurface || !groundSpawnable;
    }
}
