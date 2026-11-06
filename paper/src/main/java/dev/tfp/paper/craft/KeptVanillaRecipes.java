/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.craft;

import dev.tfp.core.craft.AdvancedCraftGrid;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The vanilla crafting recipes TFC keeps (it neither overrides nor disables them) whose ingredients name a vanilla item
 * tag TFC's data extends with its own items. In TFC those recipes accept TFC's items through the tag: its planks are in
 * {@code #minecraft:planks}, so two TFC planks make four sticks. The server matches them only by the vanilla base material
 * of our items, so our runtime matches them by tag membership instead ({@link CraftingTable#find}).
 *
 * <p>The list is every {@code minecraft:crafting_*} recipe of the vanilla 1.21.11 data that TFC's data does not replace
 * under its {@code minecraft:} id and that names a TFC-extended vanilla tag directly or through a nested tag. All of them
 * name {@code #minecraft:planks} directly. {@code minecraft:wooden_spear} reaches it through
 * {@code #minecraft:wooden_tool_materials} and is left out, because TFC removes every wooden tool. Each entry restates the
 * vanilla recipe (pattern, key, result) as written in the vanilla data.
 */
final class KeptVanillaRecipes {
    private static final Ingredient PLANKS = new Ingredient.Tag("minecraft:planks");

    private KeptVanillaRecipes() {
    }

    /** The recipes, keyed by id. */
    static final Map<String, CraftRecipe> ALL = List.of(
            shaped("minecraft:stick", 4, Map.of('#', PLANKS), "#", "#"),
            shaped("minecraft:note_block", 1, Map.of('#', PLANKS, 'X', item("minecraft:redstone")),
                    "###", "#X#", "###"),
            shaped("minecraft:jukebox", 1, Map.of('#', PLANKS, 'X', item("minecraft:diamond")), "###", "#X#", "###"),
            shaped("minecraft:loom", 1, Map.of('#', PLANKS, '@', item("minecraft:string")), "@@", "##"),
            shaped("minecraft:cartography_table", 1, Map.of('#', PLANKS, '@', item("minecraft:paper")),
                    "@@", "##", "##"),
            shaped("minecraft:beehive", 1, Map.of('P', PLANKS, 'H', item("minecraft:honeycomb")), "PPP", "HHH", "PPP"),
            shaped("minecraft:grindstone", 1, Map.of('#', PLANKS, '-', item("minecraft:stone_slab"),
                    'I', item("minecraft:stick")), "I-I", "# #"))
            .stream().collect(Collectors.toUnmodifiableMap(CraftRecipe::id, Function.identity()));

    private static Ingredient item(String id) {
        return new Ingredient.Item(id);
    }

    private static CraftRecipe shaped(String id, int count, Map<Character, Ingredient> key, String... pattern) {
        List<String> shrunk = AdvancedCraftGrid.shrink(List.of(pattern));
        return new CraftRecipe(id, CraftRecipe.Kind.SHAPED, shrunk.getFirst().length(), shrunk.size(),
                AdvancedCraftGrid.cells(shrunk, key::get), 0, Optional.empty(),
                Optional.of(new ItemStackTemplate(id, count, Optional.empty())), Optional.empty(), Optional.empty());
    }
}
