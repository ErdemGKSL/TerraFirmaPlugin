/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/size/ItemSizeManager.java @ c3b4d9536 (getDefinition default rules)
 */
package dev.tfp.core.item;

import java.util.Set;

/**
 * The {@link ItemSizing.ItemClass} of a vanilla item, from its id, the way TFC's {@code ItemSizeManager} sees the
 * vanilla 1.21.1 item classes: {@code TieredItem} (swords, pickaxes, axes, shovels, hoes) or {@code BucketItem} (the
 * empty, water, lava and mob buckets; not milk, not the powder snow bucket, which is a {@code BlockItem}) is a tool;
 * {@code ArmorItem} (helmets, chestplates, leggings, boots, the turtle helmet) or {@code AnimalArmorItem} (horse and
 * wolf armour) is armour; any other item that places a block is a block item. Pure.
 */
public final class VanillaItemClasses {
    private static final Set<String> BUCKET_ITEMS = Set.of("bucket", "water_bucket", "lava_bucket", "cod_bucket",
            "salmon_bucket", "pufferfish_bucket", "tropical_fish_bucket", "axolotl_bucket", "tadpole_bucket");
    private static final String[] TIERED_SUFFIXES = {"_sword", "_pickaxe", "_axe", "_shovel", "_hoe"};
    private static final String[] ARMOR_SUFFIXES = {"_helmet", "_chestplate", "_leggings", "_boots", "_horse_armor"};

    private VanillaItemClasses() {
    }

    /**
     * @param id        the vanilla id ({@code minecraft:iron_pickaxe}; a bare path is accepted)
     * @param blockItem whether the item places a block (vanilla {@code BlockItem})
     */
    public static ItemSizing.ItemClass of(String id, boolean blockItem) {
        String path = id == null ? "" : id.substring(id.indexOf(':') + 1);
        if (BUCKET_ITEMS.contains(path) || endsWithAny(path, TIERED_SUFFIXES)) {
            return ItemSizing.ItemClass.TOOL;
        }
        if (path.equals("wolf_armor") || endsWithAny(path, ARMOR_SUFFIXES)) {
            return ItemSizing.ItemClass.ARMOR;
        }
        return blockItem ? ItemSizing.ItemClass.BLOCK : ItemSizing.ItemClass.OTHER;
    }

    private static boolean endsWithAny(String path, String[] suffixes) {
        for (String s : suffixes) {
            if (path.endsWith(s)) {
                return true;
            }
        }
        return false;
    }
}
