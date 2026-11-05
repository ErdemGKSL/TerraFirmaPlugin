/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.food;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTList;
import com.github.retrooper.packetevents.protocol.nbt.NBTLong;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.packet.ItemDecorationHandler.PacketStack;
import dev.tfp.paper.tooltip.FoodExpiryContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/**
 * PAR-SURV-02: the full food tooltip on NBT-level stacks (the wire layout of a CraftEngine item with our
 * {@code tfp:food} record), against TFC {@code IFood.addTooltipInfo}, {@code FoodTrait.addTooltipInfo} and the TFC food
 * data of {@code tfc:food/mutton} and {@code tfc:food/cooked_mutton} at c3b4d9536 (Shift held: nutrition shown).
 */
class FoodTooltipContributorTest {
    private static final Messages MESSAGES = new Messages(FoodTooltipContributorTest.class.getClassLoader());
    /** TFC data/tfc/tfc/food/mutton.json and cooked_mutton.json. */
    private static final FoodDefinitions DEFS = new FoodDefinitions(Map.of(
            "tfp:food/mutton", new FoodData(4, 0f, 0f, 0, 0f, 0f, 0f, 1.5f, 0f, 3.0f),
            "tfp:food/cooked_mutton", new FoodData(4, 0f, 2.0f, 0, 0f, 0f, 0f, 2.5f, 0f, 2.25f),
            "tfp:food/fruit_mix", new FoodData(4, 10f, 0.2f, 0, 1.25f, 0.9f, 0.3f, 0f, 0.5f, 1f)));

    /** One stack's parts as the packet layer sees them. */
    private static final class Access implements PacketStack.Access {
        NBTCompound data;
        int amount = 1;
        List<Component> lore = new ArrayList<>();

        @Override
        public String material() {
            return "minecraft:paper";
        }

        @Override
        public int amount() {
            return amount;
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

    private static Access custom(String id) {
        Access a = new Access();
        a.data = new NBTCompound();
        a.data.setTag("craftengine:id", new NBTString(id));
        a.data.setTag("PublicBukkitValues", new NBTCompound());
        return a;
    }

    /** Adds the {@code tfp:food} record (FoodPdc layout). */
    private static Access food(String id, long created, String... traits) {
        Access a = custom(id);
        NBTCompound food = new NBTCompound();
        food.setTag("tfp:v", new NBTInt(1));
        food.setTag("tfp:created", new NBTLong(created));
        NBTList<NBTString> list = NBTList.createStringList();
        for (String t : traits) {
            list.addTag(new NBTString(t));
        }
        food.setTag("tfp:traits", list);
        a.data.getCompoundTagOrNull("PublicBukkitValues").setTag(FoodPdc.KEY, food);
        return a;
    }

    private static List<Component> decorate(Access a, long now, String locale) {
        ClockSnapshot clock = new ClockSnapshot(1L, now, now, ClockSnapshot.DEFAULT_DAYS_IN_MONTH, true);
        TooltipPipeline p = new TooltipPipeline(() -> clock, null);
        p.add(new FoodExpiryContributor(() -> DEFS, FoodPdc.DEFAULT_TRAITS, FoodConfig.DEFAULT,
                TooltipStyles.TFC_DEFAULTS, MESSAGES::get));
        p.add(new FoodTooltipContributor(() -> DEFS, FoodPdc.DEFAULT_TRAITS, FoodConfig.DEFAULT,
                TooltipStyles.TFC_DEFAULTS, MESSAGES::get));
        assertThat(p.ids()).as("replaces the expiry-only default").containsExactly(FoodExpiryContributor.ID);
        p.decorate(new PacketStack(a), p.context(locale));
        return a.lore;
    }

    private static List<String> plain(List<Component> lore) {
        return lore.stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
    }

    @Test
    void aFreshFoodShowsExpiryThenNutritionThenTraitsInTfcsColours() {
        List<Component> lore = decorate(food("tfp:food/cooked_mutton", 0L, "tfp:salted"), 24_000L, "en_us");
        List<String> text = plain(lore);
        assertThat(text).hasSize(5);
        assertThat(text.get(0)).startsWith("Expires on: ").contains(" (in ");
        assertThat(lore.get(0).color()).isEqualTo(NamedTextColor.DARK_GREEN);
        assertThat(text.subList(1, 5)).containsExactly("Nutrition:", " - Saturation: 10%", " - Protein: 2.5", "Salted");
        assertThat(lore.get(1).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(lore.get(2).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(lore.get(3).color()).isEqualTo(NamedTextColor.RED);
        assertThat(lore.get(4).color()).as("salted slows decay: default colour").isEqualTo(NamedTextColor.WHITE);
        assertThat(lore).allSatisfy(c -> assertThat(c.decoration(TextDecoration.ITALIC))
                .isEqualTo(TextDecoration.State.FALSE));
    }

    @Test
    void everyNutrientUsesTfcsColourAndOneDecimalWithWaterAfterSaturation() {
        List<Component> lore = decorate(food("tfp:food/fruit_mix", 0L), 0L, "en_us");
        assertThat(plain(lore).subList(1, lore.size())).containsExactly("Nutrition:", " - Saturation: 1%",
                " - Water: 10%", " - Grain: 1.3", " - Fruit: 0.9", " - Vegetables: 0.3", " - Dairy: 0.5");
        assertThat(lore.subList(4, lore.size()).stream().map(Component::color).toList()).containsExactly(
                NamedTextColor.GOLD, NamedTextColor.GREEN, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_PURPLE);
    }

    @Test
    void aRottenFoodShowsRottenAndItsTraitsButNoNutrition() {
        // raw mutton, decay modifier 3: 22 days / 3 ≈ 7.3 days, so day 25 is long rotten
        Access a = food("tfp:food/mutton", 0L, "tfp:wood_grilled", "tfp:charcoal_grilled", "tfp:burnt_to_a_crisp");
        TooltipItem item = new TooltipItem("tfp:food/mutton", "minecraft:paper", 1, TooltipData.EMPTY);
        assertThat(FoodTooltipContributor.rottenSpecial(item)).as("this stack draws no 'Ewwww' line").isFalse();
        List<Component> lore = decorate(a, 600_000L, "en_us");
        assertThat(plain(lore)).containsExactly("Rotten!", "Wood Grilled", "Charcoal Grilled", "Burnt to a crisp!");
        assertThat(lore.stream().map(Component::color).toList()).containsExactly(NamedTextColor.RED,
                NamedTextColor.WHITE, NamedTextColor.RED, NamedTextColor.RED);
    }

    @Test
    void oneRottenStackIn256AlsoShowsTfcsEwwwwLine() {
        int count = 1;
        while (!FoodTooltipContributor.rottenSpecial(
                new TooltipItem("tfp:food/mutton", "minecraft:paper", count, TooltipData.EMPTY))) {
            count++;
        }
        Access a = food("tfp:food/mutton", 0L);
        a.amount = count;
        assertThat(plain(decorate(a, 600_000L, "en_us"))).containsExactly("Rotten!",
                "Ewwww, are you really thinking of eating that? It looks disgusting");
    }

    @Test
    void sealedJarsGetTfcsGreyItalicLoreAndUnknownTraitsShowNothing() {
        List<Component> sealed = decorate(custom("tfp:jar/banana"), 0L, "en_us");
        assertThat(plain(sealed)).containsExactly("Sealed");
        assertThat(sealed.get(0).color()).isEqualTo(NamedTextColor.GRAY);
        assertThat(sealed.get(0).decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.TRUE);
        assertThat(plain(decorate(custom("tfp:jar/banana_unsealed"), 0L, "en_us"))).containsExactly("Unsealed");
        // a food without a definition has no nutrition lines; an unknown trait has no translation, so no line
        List<String> lines = plain(decorate(food("tfp:food/unknown", 0L, "other:mystery"), 0L, "en_us"));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).startsWith("Expires on: ");
    }

    @Test
    void theLinesAreInTheClientLanguage() {
        List<String> text = plain(decorate(food("tfp:food/cooked_mutton", 0L, "tfp:salted"), 24_000L, "tr_tr"));
        assertThat(text.subList(1, 5)).containsExactly("Besin değeri:", " - Doyuruculuk: 10%", " - Protein: 2.5",
                "Tuzlanmış");
    }
}
