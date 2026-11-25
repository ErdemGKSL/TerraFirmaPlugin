/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scribing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.testing.MockServerTest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** PAR-PROC-15: the item rules of the scribing table: renaming, clearing, and the ink used by a take. */
class ScribingTableTest extends MockServerTest {
    private static String name(ItemStack stack) {
        return PlainTextComponentSerializer.plainText().serialize(stack.getItemMeta().customName());
    }

    @Test
    void aTypedNameRenamesACopyAndKeepsTheStack() {
        ItemStack input = new ItemStack(Material.IRON_INGOT, 3);
        ItemStack result = ScribingTable.result(input, "Ingot of Doom").orElseThrow();
        assertThat(name(result)).isEqualTo("Ingot of Doom");
        assertThat(result.getAmount()).isEqualTo(3);
        assertThat(input.getItemMeta().hasCustomName()).as("the input is untouched").isFalse();
    }

    @Test
    void aBlankTextClearsTheNameAndGivesNothingForAnUnnamedItem() {
        ItemStack named = new ItemStack(Material.IRON_INGOT);
        named.editMeta(m -> m.customName(Component.text("Doom")));
        ItemStack cleared = ScribingTable.result(named, "").orElseThrow();
        assertThat(cleared.getItemMeta().hasCustomName()).isFalse();
        assertThat(ScribingTable.result(new ItemStack(Material.IRON_INGOT), "")).isEmpty();
        assertThat(ScribingTable.result(named, "Doom")).as("the same name is no result").isEmpty();
    }

    @Test
    void noInputGivesNoResult() {
        assertThat(ScribingTable.result(ItemStack.empty(), "x")).isEmpty();
        assertThat(ScribingTable.result(null, "x")).isEmpty();
    }

    @Test
    void aTakeUsesOneInkAndLeavesTheRestOfTheStack() {
        assertThat(ScribingTable.useInk(new ItemStack(Material.BLACK_DYE, 3)).getAmount()).isEqualTo(2);
        assertThat(ScribingTable.useInk(new ItemStack(Material.BLACK_DYE, 1)).getType()).isEqualTo(Material.AIR);
        assertThat(ScribingTable.useInk(null).getType()).isEqualTo(Material.AIR);
    }
}
