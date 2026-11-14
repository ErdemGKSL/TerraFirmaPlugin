/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.food;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTFloat;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.packet.ItemDecorationHandler.PacketStack;
import dev.tfp.paper.salad.SaladPdc;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/**
 * PAR-PROC-36: a salad's own food data ({@code tfp:dynamic_food}) drives the rot checks (decay modifier 4.0 instead of
 * the definition's 4.5) and the food tooltip (its mixed nutrients, then TFC's {@code "1 x <name>"} ingredient lines).
 */
class DynamicFoodTest extends MockServerTest {
    private static final Messages MESSAGES = new Messages(DynamicFoodTest.class.getClassLoader());
    private static final String SALAD = "tfp:food/vegetables_salad";
    private static final FoodData DEFINITION = new FoodData(4, 0f, 0f, 0, 0f, 0f, 0f, 0f, 0f, 4.5f);
    private static final FoodData MIX = new FoodData(4, 7.5f, 0.75f, 0, 0f, 0.5625f, 0.75f, 0f, 0f, 4.0f);
    private static final FoodDefinitions DEFS = new FoodDefinitions(Map.of(SALAD, DEFINITION));
    private static final NamespacedKey ID = new NamespacedKey("test", "id");

    private static ItemStack stack(boolean dynamic) {
        ItemStack s = new ItemStack(Material.BOWL);
        s.editMeta(m -> m.getPersistentDataContainer().set(ID, PersistentDataType.STRING, SALAD));
        if (dynamic) {
            SaladPdc.write(s, MIX, List.of("tfp:food/carrot"), "minecraft:bowl");
        }
        return s;
    }

    private static Optional<String> idOf(ItemStack s) {
        return Optional.ofNullable(s.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
    }

    @Test
    void rotChecksUseTheSaladsOwnDecayModifier() {
        ItemFoodHooks.DecayModifiers byId = ItemFoodHooks.DecayModifiers.byId(DynamicFoodTest::idOf, () -> DEFS);
        assertThat(byId.of(stack(true))).isEqualTo(4.0f);
        assertThat(byId.of(stack(false))).isEqualTo(4.5f);
        assertThat(byId.of(null)).isEqualTo(FoodDefinitions.UNKNOWN_DECAY_MODIFIER);

        // a salad made at tick 0 with 4.0 lives 22 days / 4 = 5.5 days; with 4.5 only ~4.9 days
        ItemFoodHooks hooks = new ItemFoodHooks(() -> 0L, FoodConfig.DEFAULT, byId);
        ItemStack salad = stack(true);
        ItemStack plain = stack(false);
        assertThat(hooks.stamp(salad, List.of())).isTrue();
        assertThat(hooks.stamp(plain, List.of())).isTrue();
        long between = (FoodDecay.rottenDate(0L, 1f / 4.5f) + FoodDecay.rottenDate(0L, 1f / 4.0f)) / 2;
        ItemFoodHooks later = new ItemFoodHooks(() -> between, FoodConfig.DEFAULT, byId);
        assertThat(later.isRotten(plain)).isTrue();
        assertThat(later.isRotten(salad)).isFalse();
    }

    // --- tooltip, on the wire layout of the stack PDC ---

    private static final class Access implements PacketStack.Access {
        NBTCompound data;
        List<Component> lore = new ArrayList<>();

        @Override
        public String material() {
            return "minecraft:paper";
        }

        @Override
        public int amount() {
            return 1;
        }

        @Override
        public NBTCompound customData() {
            return data;
        }

        @Override
        public void customData(NBTCompound data) {
            this.data = data;
        }

        @Override
        public List<Component> lore() {
            return List.copyOf(lore);
        }

        @Override
        public void lore(List<Component> lines) {
            lore = new ArrayList<>(lines);
        }
    }

    private static Access saladOnTheWire(boolean dynamic) {
        Access a = new Access();
        a.data = new NBTCompound();
        a.data.setTag("craftengine:id", new NBTString(SALAD));
        NBTCompound pdc = new NBTCompound();
        NBTCompound food = new NBTCompound();
        food.setTag("tfp:v", new NBTInt(1));
        food.setTag("tfp:created", new NBTLong(0L));
        food.setTag("tfp:traits", NBTList.createStringList());
        pdc.setTag(FoodPdc.KEY, food);
        if (dynamic) {
            NBTCompound f = new NBTCompound();
            f.setTag("tfp:v", new NBTInt(1));
            f.setTag("tfp:hunger", new NBTInt(4));
            f.setTag("tfp:water", new NBTFloat(7.5f));
            f.setTag("tfp:saturation", new NBTFloat(0.75f));
            f.setTag("tfp:intoxication", new NBTInt(0));
            f.setTag("tfp:grain", new NBTFloat(0f));
            f.setTag("tfp:fruit", new NBTFloat(0.5625f));
            f.setTag("tfp:vegetables", new NBTFloat(0.75f));
            f.setTag("tfp:protein", new NBTFloat(0f));
            f.setTag("tfp:dairy", new NBTFloat(0f));
            f.setTag("tfp:decay", new NBTFloat(4.0f));
            pdc.setTag("tfp:dynamic_food", f);
            NBTList<NBTString> ingredients = NBTList.createStringList();
            ingredients.addTag(new NBTString("tfp:food/carrot"));
            ingredients.addTag(new NBTString("tfp:food/red_apple"));
            pdc.setTag("tfp:ingredients", ingredients);
        }
        a.data.setTag("PublicBukkitValues", pdc);
        return a;
    }

    private static List<Component> decorate(Access a, long now) {
        ClockSnapshot clock = new ClockSnapshot(1L, now, now, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
        TooltipPipeline p = new TooltipPipeline(() -> clock, null);
        p.add(new FoodTooltipContributor(() -> DEFS, FoodPdc.DEFAULT_TRAITS, FoodConfig.DEFAULT,
                TooltipStyles.TFC_DEFAULTS, MESSAGES::get));
        p.decorate(new PacketStack(a), p.context("en_us"));
        return a.lore;
    }

    private static List<String> plain(List<Component> lore) {
        return lore.stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
    }

    @Test
    void theTooltipShowsTheSaladsNutrientsAndIngredients() {
        List<Component> lore = decorate(saladOnTheWire(true), 0L);
        List<String> text = plain(lore);
        assertThat(text.get(0)).startsWith("Expires on: ");
        assertThat(text.subList(1, 6)).containsExactly("Nutrition:", " - Saturation: 3%", " - Water: 7%",
                " - Fruit: 0.6", " - Vegetables: 0.8");
        assertThat(lore).hasSize(8);
        for (int i = 6; i < 8; i++) {
            Component line = lore.get(i);
            assertThat(line.color()).isEqualTo(NamedTextColor.GRAY);
            assertThat(PlainTextComponentSerializer.plainText().serialize(line)).startsWith("1 x ");
        }
        assertThat(((TranslatableComponent) lore.get(6).children().getFirst()).key()).isEqualTo("item.tfp.food.carrot");
        assertThat(((TranslatableComponent) lore.get(7).children().getFirst()).key())
                .isEqualTo("item.tfp.food.red_apple");
    }

    @Test
    void theExpiryLineUsesTheSaladsOwnDecayModifier() {
        // between the two rotten dates: rotten with the definition's 4.5, fresh with the salad's 4.0
        long between = (FoodDecay.rottenDate(0L, 1f / 4.5f) + FoodDecay.rottenDate(0L, 1f / 4.0f)) / 2;
        assertThat(plain(decorate(saladOnTheWire(true), between)).getFirst()).startsWith("Expires on: ");
        assertThat(plain(decorate(saladOnTheWire(false), between)).getFirst()).isEqualTo("Rotten!");
    }

    @Test
    void aSaladWithoutDynamicDataShowsTheDefinition() {
        List<String> text = plain(decorate(saladOnTheWire(false), 0L));
        assertThat(text).hasSize(1); // the definition has no nutrients, so no Nutrition block
        assertThat(text.getFirst()).startsWith("Expires on: ");
    }

    @Test
    void dynamicFoodReading() {
        assertThat(FoodTooltipContributor.dynamicFood(TooltipData.EMPTY)).isEmpty();
        assertThat(FoodTooltipContributor.dynamicFood(TooltipData.of(Map.of("tfp:dynamic_food",
                Map.of("tfp:v", 2))))).as("newer version").isEmpty();
        assertThat(FoodTooltipContributor.dynamicFood(TooltipData.of(Map.of("tfp:dynamic_food", Map.of("tfp:v", 1,
                "tfp:hunger", 4, "tfp:vegetables", 0.75f, "tfp:decay", 4.0f))))).contains(
                new FoodData(4, 0f, 0f, 0, 0f, 0f, 0.75f, 0f, 0f, 4.0f));
        assertThat(FoodTooltipContributor.itemNameKey("tfp:food/red_apple")).isEqualTo("item.tfp.food.red_apple");
        assertThat(FoodTooltipContributor.itemNameKey("minecraft:carrot")).isEqualTo("item.minecraft.carrot");
    }

    @Test
    void thePacketKeysMatchSaladPdc() {
        assertThat(FoodTooltipContributor.DYNAMIC_FOOD).isEqualTo(SaladPdc.FOOD.asString());
        assertThat(FoodTooltipContributor.INGREDIENTS).isEqualTo(SaladPdc.INGREDIENTS.asString());
        assertThat(FoodTooltipContributor.DYNAMIC_VERSION).isEqualTo(SaladPdc.VERSION);
    }
}
