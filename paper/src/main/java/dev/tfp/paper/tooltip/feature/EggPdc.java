/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/EggComponent.java @ c3b4d9536 (fertilized, hatchDay)
 */
package dev.tfp.paper.tooltip.feature;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The item PDC codec of TFC's egg component ({@value FeatureData#EGG}, layout in {@link FeatureData}): whether the
 * egg is fertilized and the calendar day it hatches. Written by {@code /tfp debug egg} now and by the nest box
 * (PAR-FAUNA-06) later; TFC's entity snapshot of the hatchling is not part of it yet. Call on the stack's owning thread.
 */
public final class EggPdc {
    private static final NamespacedKey ROOT = Objects.requireNonNull(NamespacedKey.fromString(FeatureData.EGG));
    private static final NamespacedKey V = Objects.requireNonNull(NamespacedKey.fromString(FeatureData.V));
    private static final NamespacedKey FERTILIZED =
            Objects.requireNonNull(NamespacedKey.fromString(FeatureData.FERTILIZED));
    private static final NamespacedKey HATCH_DAY = Objects.requireNonNull(NamespacedKey.fromString(FeatureData.HATCH_DAY));

    private EggPdc() {
    }

    /** Writes the record; {@code null} removes it. @return false when the stack has no meta or a newer record */
    public static boolean write(ItemStack stack, FeatureData.Egg egg) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        PersistentDataContainer existing = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        Integer v = existing == null ? null : existing.get(V, PersistentDataType.INTEGER);
        if (v != null && v > FeatureData.VERSION) {
            return false;
        }
        if (egg == null) {
            pdc.remove(ROOT);
        } else {
            PersistentDataContainer r = pdc.getAdapterContext().newPersistentDataContainer();
            r.set(V, PersistentDataType.INTEGER, FeatureData.VERSION);
            r.set(FERTILIZED, PersistentDataType.BOOLEAN, egg.fertilized());
            r.set(HATCH_DAY, PersistentDataType.LONG, egg.hatchDay());
            pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, r);
        }
        stack.setItemMeta(meta);
        return true;
    }

    /** The record of {@code stack}, or empty. */
    public static Optional<FeatureData.Egg> read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer r = stack.getItemMeta().getPersistentDataContainer()
                .get(ROOT, PersistentDataType.TAG_CONTAINER);
        if (r == null) {
            return Optional.empty();
        }
        Integer v = r.get(V, PersistentDataType.INTEGER);
        if (v == null || v > FeatureData.VERSION) {
            return Optional.empty();
        }
        Boolean fertilized = r.get(FERTILIZED, PersistentDataType.BOOLEAN);
        Long day = r.get(HATCH_DAY, PersistentDataType.LONG);
        return Optional.of(new FeatureData.Egg(Boolean.TRUE.equals(fertilized), day == null ? 0 : day));
    }
}
