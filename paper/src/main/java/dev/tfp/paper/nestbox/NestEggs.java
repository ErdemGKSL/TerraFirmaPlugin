/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/livestock/OviparousAnimal.java @ c3b4d9536 (makeEgg),
 *       src/main/java/net/dries007/tfc/common/component/EggComponent.java @ c3b4d9536 (of, hatch)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.livestock.products.EggRules;
import dev.tfp.paper.tooltip.feature.EggPdc;
import dev.tfp.paper.tooltip.feature.FeatureData;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-FAUNA-07: the egg item the nest box lays and hatches. TFC's {@code EggComponent} holds the chick's entity; here the
 * egg's {@link EggPdc} record (fertilized, hatch day) is joined by a {@code tfp:egg_chick} record: the carrier species
 * and the familiarity the chick is born with. An egg without it (the hen's ground laying of PAR-FAUNA-06) hatches as a
 * chicken with TFC's offspring familiarity of zero; see the ledger. Call on the stack's owning thread.
 */
public final class NestEggs {
    /** The species of an egg that names none. */
    public static final String DEFAULT_SPECIES = "tfc:chicken";

    private static final NamespacedKey CHICK = new NamespacedKey("tfp", "egg_chick");
    private static final NamespacedKey SPECIES = new NamespacedKey("tfp", "species");
    private static final NamespacedKey FAMILIARITY = new NamespacedKey("tfp", "familiarity");

    /** What the egg hatches into. */
    public record Chick(String species, float familiarity) {
    }

    private NestEggs() {
    }

    /** TFC {@code EggComponent.of(baby, day)} on a new egg item (fertilized): the hatch day and the chick. */
    public static ItemStack fertile(String species, EggRules.FertileEgg egg) {
        ItemStack stack = new ItemStack(Material.EGG);
        EggPdc.write(stack, new FeatureData.Egg(true, egg.hatchDay()));
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer chick = meta.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        chick.set(SPECIES, PersistentDataType.STRING, species);
        chick.set(FAMILIARITY, PersistentDataType.FLOAT, egg.hatchling().familiarity());
        meta.getPersistentDataContainer().set(CHICK, PersistentDataType.TAG_CONTAINER, chick);
        stack.setItemMeta(meta);
        return stack;
    }

    /** An egg with no component: what an unfertilized hen lays (TFC {@code new ItemStack(Items.EGG)}). */
    public static ItemStack plain() {
        return new ItemStack(Material.EGG);
    }

    /** TFC {@code stack.has(TFCComponents.EGG)}. */
    public static boolean isEggItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && EggPdc.read(stack).isPresent();
    }

    /** The chick a fertile egg hatches, the default species and no familiarity when the egg names none. */
    public static Chick chickOf(ItemStack stack) {
        if (stack != null && stack.hasItemMeta()) {
            PersistentDataContainer c = stack.getItemMeta().getPersistentDataContainer().get(CHICK, PersistentDataType.TAG_CONTAINER);
            if (c != null) {
                String species = c.get(SPECIES, PersistentDataType.STRING);
                Float familiarity = c.get(FAMILIARITY, PersistentDataType.FLOAT);
                return new Chick(species == null ? DEFAULT_SPECIES : species, familiarity == null ? 0f : familiarity);
            }
        }
        return new Chick(DEFAULT_SPECIES, 0f);
    }

    public static Optional<FeatureData.Egg> record(ItemStack stack) {
        return EggPdc.read(stack);
    }
}
