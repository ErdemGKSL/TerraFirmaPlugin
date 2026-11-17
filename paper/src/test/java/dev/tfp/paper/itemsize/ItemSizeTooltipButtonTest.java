/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.itemsize;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.item.ItemSizeDefinitions;
import dev.tfp.core.item.ItemSizeLimits;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-56: GUI button icons (firepit thermometer, pot, grill, barrel, powderkeg, knapping, tabs) show only their own
 * tooltip, like TFC's screens ({@code FirepitScreen.renderTooltip}); every other item keeps the size/weight line.
 */
class ItemSizeTooltipButtonTest extends MockServerTest {
    private ItemSizeTooltipContributor contributor;

    @BeforeEach
    void setUp() {
        TagLookup tags = new TagLookup() {
            @Override
            public boolean contains(TagType type, String tag, String id) {
                return false;
            }

            @Override
            public boolean exists(TagType type, String tag) {
                return true;
            }
        };
        ItemSizes sizes = new ItemSizes(() -> new ItemSizeDefinitions(List.of()), () -> tags, s -> Optional.empty(),
                id -> false, ItemSizeLimits.DEFAULT);
        contributor = new ItemSizeTooltipContributor(sizes, (locale, key, args) -> Map.of(
                "item_size.weight.very_light", "Very Light", "item_size.size.very_small", "Very Small")
                .getOrDefault(key, key));
    }

    private List<String> lines(TooltipItem item) {
        List<Component> lines = new ArrayList<>();
        contributor.contribute(item, new TooltipContext("en_us", ClockSnapshot.UNSET), lines::add);
        return lines.stream().map(l -> PlainTextComponentSerializer.plainText().serialize(l)).toList();
    }

    @Test
    void buttonIconGetsNoSizeLine() {
        TooltipItem button = new TooltipItem(null, "minecraft:paper", 1,
                TooltipData.of(Map.of(ItemSizeTooltipContributor.BUTTON_KEY, (byte) 1)));
        assertThat(ItemSizeTooltipContributor.isButton(button)).isTrue();
        assertThat(lines(button)).isEmpty();
    }

    @Test
    void sameMaterialWithoutMarkerKeepsTheLine() {
        TooltipItem paper = new TooltipItem(null, "minecraft:paper", 1, TooltipData.EMPTY);
        assertThat(ItemSizeTooltipContributor.isButton(paper)).isFalse();
        assertThat(lines(paper)).containsExactly("⚖ Very Light ⇲ Very Small");
        TooltipItem otherValues = new TooltipItem(null, "minecraft:paper", 1,
                TooltipData.of(Map.of("tfp:other", (byte) 1)));
        assertThat(lines(otherValues)).containsExactly("⚖ Very Light ⇲ Very Small");
    }

    @Test
    void keyMatchesTheMarkerGuiButtonsWrites() {
        ItemStack icon = GuiButtons.icon(false, "Very Hot***");
        assertThat(GuiButtons.isButton(icon)).isTrue();
        assertThat(ItemSizeTooltipContributor.BUTTON_KEY).isEqualTo("tfp:gui_button");
        assertThat(icon.getItemMeta().getPersistentDataContainer().getKeys())
                .extracting(Object::toString).contains(ItemSizeTooltipContributor.BUTTON_KEY);
        assertThat(icon.getItemMeta().getPersistentDataContainer().get(GuiButtons.MARKER, PersistentDataType.BYTE))
                .isEqualTo((byte) 1);
    }
}
