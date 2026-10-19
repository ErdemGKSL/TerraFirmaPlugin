/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** M3-HEAT-01: the heat item PDC codec and the lazy temperature, level and tooltip line on MockBukkit item meta. */
class ItemHeatHooksMockBukkitTest extends MockServerTest {
    private static final HeatConfig CFG = HeatConfig.DEFAULT;

    private static ItemStack ingot() {
        return new ItemStack(Material.IRON_INGOT);
    }

    @Test
    void roundTripKeepsStateAndForeignData() {
        ItemStack stack = ingot();
        NamespacedKey foreign = new NamespacedKey("other", "x");
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(foreign, PersistentDataType.STRING, "keep");
        stack.setItemMeta(meta);

        assertThat(HeatPdc.write(stack, new HeatState(850.5f, 12_345L))).isTrue();

        assertThat(HeatPdc.read(stack)).contains(new HeatState(850.5f, 12_345L));
        assertThat(stack.getItemMeta().getPersistentDataContainer().get(foreign, PersistentDataType.STRING))
            .isEqualTo("keep");
    }

    @Test
    void anUnheatedStackReadsEmptyAndIsCold() {
        ItemHeatHooks hooks = new ItemHeatHooks(() -> 0L, CFG, ItemHeatHooks.Capacities.UNIT);
        assertThat(HeatPdc.read(ingot())).isEmpty();
        assertThat(HeatPdc.has(ingot())).isFalse();
        assertThat(hooks.temperature(ingot())).isZero();
        assertThat(hooks.level(ingot())).isEmpty();
        assertThat(hooks.tooltipKey(ingot())).isEmpty();
    }

    @Test
    void writingNullRemovesTheEntry() {
        ItemStack stack = ingot();
        HeatPdc.write(stack, new HeatState(500f, 1L));
        assertThat(HeatPdc.write(stack, null)).isTrue();
        assertThat(HeatPdc.has(stack)).isFalse();
    }

    @Test
    void aNewerVersionEntryIsNotOverwritten() {
        ItemStack stack = ingot();
        ItemMeta meta = stack.getItemMeta();
        var heat = meta.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        heat.set(new NamespacedKey("tfp", "v"), PersistentDataType.INTEGER, HeatPdc.VERSION + 1);
        meta.getPersistentDataContainer().set(NamespacedKey.fromString(HeatPdc.KEY),
            PersistentDataType.TAG_CONTAINER, heat);
        stack.setItemMeta(meta);

        assertThat(HeatPdc.read(stack)).isEmpty();
        assertThat(HeatPdc.write(stack, new HeatState(500f, 1L))).isFalse();
    }

    @Test
    void theTemperatureCoolsLazilyAndTheLevelFollows() {
        AtomicLong now = new AtomicLong(1_000L);
        ItemHeatHooks hooks = new ItemHeatHooks(now::get, CFG, ItemHeatHooks.Capacities.UNIT);
        ItemStack stack = ingot();
        HeatPdc.write(stack, new HeatState(1_000f, 1_000L));

        assertThat(hooks.temperature(stack)).isEqualTo(1_000f);
        assertThat(hooks.level(stack)).contains(HeatLevel.ORANGE);

        now.set(1_000L + 60_000L);
        assertThat(hooks.temperature(stack)).isLessThan(1_000f);
    }

    @Test
    void theCapacityComesFromTheItemHeatDefinitionsAndAnItemWithoutOneStaysCold() {
        var defs = new ItemHeatDefinitions(java.util.Map.of("tfp:metal/ingot/copper",
            new ItemHeatDefinitions.HeatData(2.857143f, 648f, 864f)));
        var byId = ItemHeatHooks.Capacities.byId(
            s -> s.getType() == Material.COPPER_INGOT ? java.util.Optional.of("tfp:metal/ingot/copper")
                : s.getType() == Material.IRON_INGOT ? java.util.Optional.of("tfp:not_heatable") : java.util.Optional.empty(),
            () -> defs);
        ItemHeatHooks hooks = new ItemHeatHooks(() -> 5L, CFG, byId);
        ItemStack copper = new ItemStack(Material.COPPER_INGOT);
        ItemStack unknown = new ItemStack(Material.IRON_INGOT);
        ItemStack noId = new ItemStack(Material.STICK);
        for (ItemStack s : new ItemStack[] {copper, unknown, noId}) {
            HeatPdc.write(s, new HeatState(900f, 5L));
        }

        assertThat(byId.of(copper)).isEqualTo(2.857143f);
        assertThat(hooks.temperature(copper)).isEqualTo(900f);
        assertThat(hooks.temperature(unknown)).as("no definition: cannot be heated").isZero();
        assertThat(hooks.temperature(noId)).as("no custom id").isZero();
    }

    @Test
    void theTooltipLineIsTranslatedInEnglishAndTurkish() {
        ItemHeatHooks hooks = new ItemHeatHooks(() -> 5L, CFG, ItemHeatHooks.Capacities.UNIT);
        ItemStack stack = ingot();
        HeatPdc.write(stack, new HeatState(1_000f, 5L));
        Messages messages = new Messages(getClass().getClassLoader());

        assertThat(hooks.tooltipKey(stack)).contains("heat.level.orange");
        assertThat(hooks.tooltipLine(stack, messages, Locale.forLanguageTag("en-US"))).contains("Orange");
        assertThat(hooks.tooltipLine(stack, messages, Locale.forLanguageTag("tr-TR"))).contains("Turuncu");
    }
}
