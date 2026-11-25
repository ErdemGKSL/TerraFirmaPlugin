/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/outputs/AddGlassModifier.java @ c3b4d9536,
 * file: src/main/java/net/dries007/tfc/common/recipes/outputs/AddPowderModifier.java @ c3b4d9536
 */
package dev.tfp.paper.glass;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.glass.GlassOperation;
import dev.tfp.core.glass.GlassRecipes;
import dev.tfp.core.glass.GlassRules;
import dev.tfp.core.glass.GlassState;
import dev.tfp.paper.bowl.BowlGlass;
import dev.tfp.paper.heat.ItemHeatHooks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ModifierExtensions;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-16: glassworking with the blowpipe. Provides the {@link BowlGlass} service of the powder bowl, so it is listed
 * before the bowl module in the service file; registers the {@code tfc:add_glass} and {@code tfc:add_powder} recipe
 * modifiers; listens for the blowpipe's right click and for a batch put on a blowpipe. Needs the tags, heat and recipe
 * modules before it. The blowpipe, tool and batch items are CraftEngine items by datagen (PAR-PROC-52).
 */
public final class GlassModule implements FeatureModule {
    public static final String ID = "glass";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        Tags tags = context.services().require(Tags.class);
        ItemHeatHooks heat = context.services().require(ItemHeatHooks.class);
        Recipes recipes = context.services().require(Recipes.class);
        CustomItems items = context.integrations().customItems();
        GlassWork work = new GlassWork(new GlassWork.Services(stack -> tags.itemId(stack).orElse(""), heat::temperature,
            id -> create(items, id, 1), out -> result(items, out), GlassRecipes.ALL,
            () -> ThreadLocalRandom.current().nextDouble(), context.messages()));
        context.services().provide(BowlGlass.class, new GlassBowl());
        context.listen(new GlassListener(work));
        recipes.extend(ModifierExtensions.Kind.ADD_GLASS, (stack, input, inputs, modifier) -> {
            if (input == null || input.isEmpty()) {
                return Optional.of(stack);
            }
            ItemStack out = stack.clone();
            GlassPdc.write(out, GlassRules.withBatch(GlassState.EMPTY, work.tfcId(input)));
            return Optional.of(out);
        });
        recipes.extend(ModifierExtensions.Kind.ADD_POWDER, (stack, input, inputs, modifier) -> {
            for (ItemStack in : inputs) {
                Optional<GlassOperation> op = GlassOperation.byPowder(work.tfcId(in));
                if (op.isPresent()) {
                    ItemStack out = stack.clone();
                    GlassPdc.read(out).ifPresent(state -> GlassPdc.write(out, GlassRules.apply(state, op.get())));
                    return Optional.of(out);
                }
            }
            return Optional.of(stack);
        });
    }

    /** A stack of {@code id} (a {@code tfp:} id or a vanilla one); null when it does not exist. */
    static ItemStack create(CustomItems items, String id, int count) {
        if (id.startsWith("minecraft:")) {
            Material material = Material.matchMaterial(id);
            return material == null ? null : new ItemStack(material, count);
        }
        return items.create(id, count).orElse(null);
    }

    /** The stack of a recipe result ({@code tfc:} ids are ours). */
    static ItemStack result(CustomItems items, CraftOutput out) {
        return create(items, GlassPdc.toOurs(out.item()), out.count());
    }
}
