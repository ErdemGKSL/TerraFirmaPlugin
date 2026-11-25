/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SewingTableContainer.java @ c3b4d9536 (isWool, isBurlap, isString,
 *       isNeedle),
 *       src/main/java/net/dries007/tfc/common/recipes/SewingRecipe.java @ c3b4d9536 (the squares and stitches grids)
 */
package dev.tfp.paper.sewing;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.craft.SewingRecipe;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.sewing.SewingSession.ItemKind;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-PROC-07: the sewing table screen ({@link SewingListener}, {@link SewingMenu}). The blocks
 * {@code tfp:wood/sewing_table/<wood>} are written by datagen (PAR-PROC-49). Needs the GUI, tag and recipe modules before
 * it in the service file.
 */
public final class SewingModule implements FeatureModule {
    public static final String ID = "sewing";
    /** TFC {@code TFCTags.Items.SEWING_LIGHT_CLOTH}, {@code SEWING_DARK_CLOTH}, {@code SEWING_NEEDLES}; {@code Tags.Items.STRINGS}. */
    public static final String LIGHT_CLOTH = "tfc:sewing_light_cloth";
    public static final String DARK_CLOTH = "tfc:sewing_dark_cloth";
    public static final String NEEDLES = "tfc:sewing_needles";
    public static final String STRINGS = "c:strings";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        GuiService gui = context.services().require(GuiService.class);
        Tags tags = context.services().require(Tags.class);
        Recipes recipes = context.services().require(Recipes.class);
        var blocks = context.integrations().customBlocks();
        SewingMenu.Services services = new SewingMenu.Services(gui, context.scheduler(), context.messages(),
            () -> entries(recipes.index().all(RecipeTypes.SEWING)), stack -> kind(tags, stack),
            template -> recipes.modifiers().create(template), block -> SewingListener.isTable(blocks, block));
        context.listen(new SewingListener(services, blocks));
    }

    /** TFC's four tag checks, in the order the slots use them. */
    static ItemKind kind(Tags tags, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemKind.OTHER;
        }
        if (tags.is(stack, LIGHT_CLOTH)) {
            return ItemKind.WOOL;
        }
        if (tags.is(stack, DARK_CLOTH)) {
            return ItemKind.BURLAP;
        }
        if (tags.is(stack, STRINGS)) {
            return ItemKind.STRING;
        }
        if (tags.is(stack, NEEDLES)) {
            return ItemKind.NEEDLE;
        }
        return ItemKind.OTHER;
    }

    /** The loaded {@code tfc:sewing} recipes as the core's recipe and the stack template. */
    @SuppressWarnings("unchecked")
    static List<SewingMenu.Entry> entries(List<RecipeEntry> loaded) {
        List<SewingMenu.Entry> out = new ArrayList<>();
        for (RecipeEntry entry : loaded) {
            List<String> squares = (List<String>) entry.recipe().require("squares", List.class);
            List<String> stitches = (List<String>) entry.recipe().require("stitches", List.class);
            ItemStackTemplate result = entry.recipe().require("result", ItemStackTemplate.class);
            out.add(new SewingMenu.Entry(new SewingRecipe(entry.id(), String.join("", squares), String.join("", stitches),
                new CraftOutput(result.id(), result.count())), result));
        }
        return out;
    }
}
