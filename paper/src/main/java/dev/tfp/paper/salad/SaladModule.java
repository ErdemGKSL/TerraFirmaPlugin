/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/outputs/EmptyBowlModifier.java @ c3b4d9536 (the bowl stored on the
 *       input)
 */
package dev.tfp.paper.salad;

import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.food.FoodDefinitionsCache;
import dev.tfp.paper.food.ItemFoodHooks;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ModifierExtensions;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-14: salads on the module SPI. Needs the GUI framework (its click routing), the tags and the recipe runtime
 * before it in the service file. The salad screen has no art yet (PAR-PROC-36), so it opens through its own
 * plain-title {@link GuiService}; the shared {@code GuiListener} routes its clicks like every other screen's. Also
 * registers the {@code tfc:empty_bowl} output modifier (TFC {@code EmptyBowlModifier}: the barrel's bowl cleaning gives
 * back the bowl stored on the salad).
 */
public final class SaladModule implements FeatureModule {
    public static final String ID = "salad";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        context.services().require(GuiService.class); // the shared click routing must be on
        GuiService plain = new GuiService(context.plugin().getServer(), context.scheduler(), System::currentTimeMillis, false);
        Tags tags = context.services().require(Tags.class);
        ItemFoodHooks hooks = context.services().require(ItemFoodHooks.class);
        FoodDefinitionsCache definitions = context.services().require(FoodDefinitionsCache.class);
        CustomItems items = context.integrations().customItems();
        SaladMenu.Services services = new SaladMenu.Services(plain, context.scheduler(), context.messages(),
            stack -> tags.is(stack, SaladMenu.SALAD_BOWLS), stack -> tags.is(stack, SaladMenu.USABLE_IN_SALAD),
            stack -> food(stack, tags::itemId, definitions), hooks::isRotten, tags::itemId,
            (id, amount) -> create(items, id, amount), stack -> hooks.stamp(stack, List.of()));
        Function<String, Optional<ItemStack>> bowls = id -> create(items, id, 1);
        context.listen(new SaladListener(services, bowls));
        context.services().get(Recipes.class).ifPresent(r -> r.extend(ModifierExtensions.Kind.EMPTY_BOWL,
            (stack, input, inputs, modifier) -> SaladPdc.bowl(input).flatMap(bowls)));
    }

    /**
     * TFC {@code FoodCapability.get(stack).getData()}: the stack's own food data for a dynamic food, else its item's food
     * definition; empty when it is not food.
     */
    static Optional<FoodData> food(ItemStack stack, Function<ItemStack, Optional<String>> ids, Supplier<FoodDefinitions> defs) {
        Optional<FoodData> dynamic = SaladPdc.food(stack);
        if (dynamic.isPresent()) {
            return dynamic;
        }
        return ids.apply(stack).flatMap(id -> defs.get().get(id));
    }

    /** A stack of {@code amount} of {@code id}: a CraftEngine item, else a vanilla material. */
    static Optional<ItemStack> create(CustomItems items, String id, int amount) {
        if (items.exists(id)) {
            return items.create(id, amount);
        }
        Material m = Material.matchMaterial(id);
        return m == null || m.isAir() || !m.isItem() ? Optional.empty() : Optional.of(new ItemStack(m, amount));
    }
}
