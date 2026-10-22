/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.livestock.AnimalState;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** M5-ENT-01: the animal record PDC codec (on an item meta container; the entity container has the same API). */
class AnimalPdcMockBukkitTest extends MockServerTest {
    private static final NamespacedKey ROOT = NamespacedKey.fromString(AnimalPdc.KEY);

    private static PersistentDataContainer container() {
        ItemMeta meta = new ItemStack(Material.STONE).getItemMeta();
        return meta.getPersistentDataContainer();
    }

    private static AnimalRecord sample() {
        AnimalState s = new AnimalState(false, 0.25f, 1_000L, -5_000_000L, 9_000_000L, 2_000L, AnimalState.NEVER, 7,
                true, 42L);
        return new AnimalRecord("tfc:cow", s, 13, "spotted");
    }

    @Test
    void roundTripKeepsEveryFieldAndOtherKeys() {
        PersistentDataContainer pdc = container();
        NamespacedKey foreign = new NamespacedKey("other", "x");
        pdc.set(foreign, PersistentDataType.INTEGER, 7);

        assertThat(AnimalPdc.write(pdc, sample())).isTrue();

        assertThat(AnimalPdc.has(pdc)).isTrue();
        assertThat(AnimalPdc.read(pdc)).contains(sample());
        assertThat(pdc.get(foreign, PersistentDataType.INTEGER)).isEqualTo(7);
        PersistentDataContainer nested = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        assertThat(nested.get(new NamespacedKey("tfp", "birthtick"), PersistentDataType.LONG)).isEqualTo(-5_000_000L);
        assertThat(nested.get(new NamespacedKey("tfp", "species"), PersistentDataType.STRING)).isEqualTo("tfc:cow");

        AnimalRecord wild = new AnimalRecord("tfc:deer", AnimalState.spawned(true, 10, 10), 0, "");
        AnimalPdc.write(pdc, wild);
        assertThat(AnimalPdc.read(pdc)).contains(wild);
        assertThat(AnimalPdc.write(pdc, null)).isTrue();
        assertThat(AnimalPdc.has(pdc)).isFalse();
        assertThat(pdc.get(foreign, PersistentDataType.INTEGER)).isEqualTo(7);
    }

    @Test
    void absentOrMalformedReadsEmpty() {
        PersistentDataContainer pdc = container();
        assertThat(AnimalPdc.read(pdc)).isEmpty();
        PersistentDataContainer partial = pdc.getAdapterContext().newPersistentDataContainer();
        partial.set(new NamespacedKey("tfp", "v"), PersistentDataType.INTEGER, 1);
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, partial);
        assertThat(AnimalPdc.read(pdc)).isEmpty();
    }

    @Test
    void newerVersionReadsEmptyAndIsNotOverwritten() {
        PersistentDataContainer pdc = container();
        AnimalPdc.write(pdc, sample());
        PersistentDataContainer nested = pdc.get(ROOT, PersistentDataType.TAG_CONTAINER);
        nested.set(new NamespacedKey("tfp", "v"), PersistentDataType.INTEGER, AnimalRecord.VERSION + 1);
        pdc.set(ROOT, PersistentDataType.TAG_CONTAINER, nested);

        assertThat(AnimalPdc.read(pdc)).isEmpty();
        assertThat(AnimalPdc.write(pdc, sample().withState(sample().state().withUses(9)))).isFalse();
        assertThat(AnimalPdc.write(pdc, null)).isFalse();
        assertThat(pdc.get(ROOT, PersistentDataType.TAG_CONTAINER).get(new NamespacedKey("tfp", "uses"),
                PersistentDataType.INTEGER)).isEqualTo(7);
    }
}
