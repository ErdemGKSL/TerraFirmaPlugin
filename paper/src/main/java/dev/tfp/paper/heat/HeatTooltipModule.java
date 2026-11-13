/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.heat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.recipe.Recipes;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.tooltip.TooltipPipeline;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.tooltip.TooltipText;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;

/**
 * PAR-HEAT-02: TFC's heat and metal tooltip lines. Replaces the tooltip module's default heat line with
 * {@link HeatTooltipLine} (adds TFC's " - Danger!!" suffix from the heating recipes) and adds the metal content line
 * {@link HeatTooltipMeltsInto} ("Melts into 100 mB of Copper (at ...)"). Listed after the {@code tags},
 * {@code tooltip} and {@code recipe} modules in the service file, whose services it requires. Fluid names come from
 * TFC's lang files in the plugin jar (all of TFC's languages), as in the fluid container tooltip.
 */
public final class HeatTooltipModule implements FeatureModule {
    public static final String ID = "heat_tooltip";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        TooltipText text = context.messages()::get;
        ItemHeatDefinitionsCache definitions = context.services().require(ItemHeatDefinitionsCache.class);
        Recipes recipeService = context.services().require(Recipes.class);
        Tags tags = context.services().require(Tags.class);
        HeatTooltipRecipes recipes = new HeatTooltipRecipes(
                id -> recipeService.index().byItem(RecipeTypes.HEATING, id),
                () -> ItemIngredientContext.lookup(tags.snapshot()));

        ClassLoader loader = context.plugin().getClass().getClassLoader();
        FluidLang lang = FluidLang.fromJar(loader);
        if (!lang.load(FluidLang.FALLBACK)) {
            context.logger().warning("heat_tooltip: " + FluidLang.JAR_DIR + FluidLang.FALLBACK
                    + ".json is missing; metal lines use fluid ids");
        }
        Logger logger = context.logger();
        context.scheduler().io(() -> {
            for (String locale : locales(name -> loader.getResourceAsStream(FluidLang.JAR_DIR + name))) {
                try {
                    lang.load(locale);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "heat_tooltip: cannot load the " + locale + " fluid names", e);
                }
            }
        });

        TooltipPipeline pipeline = context.services().require(TooltipPipeline.class);
        TooltipStyles styles = TooltipStyles.TFC_DEFAULTS;
        pipeline.add(new HeatTooltipLine(definitions, HeatConfig.DEFAULT, styles.heat(), text, recipes));
        pipeline.add(new HeatTooltipMeltsInto(definitions, HeatConfig.DEFAULT, styles.heat(), text, recipes,
                fluidNames(lang)));
    }

    /** TFC {@code FluidStack.getHoverName()} from TFC's lang: the fluid's name, else a client translation of its key. */
    static BiFunction<String, String, Component> fluidNames(FluidLang lang) {
        return (locale, fluidId) -> {
            Optional<TfcFluid> fluid = TfcFluids.get(fluidId);
            if (fluid.isEmpty()) {
                return Component.text(fluidId);
            }
            String key = fluid.get().descriptionId();
            String name = lang.get(locale, key);
            return name != null ? Component.text(name) : Component.translatable(key);
        };
    }

    /** The locales datagen lists in {@code content/lang/index.json}; empty when it is missing. */
    static List<String> locales(java.util.function.Function<String, InputStream> opener) {
        List<String> out = new ArrayList<>();
        try (InputStream in = opener.apply("index.json")) {
            if (in == null) {
                return out;
            }
            JsonObject index = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (JsonElement e : index.getAsJsonArray("locales")) {
                out.add(e.getAsJsonObject().get("locale").getAsString());
            }
        } catch (IOException | RuntimeException e) {
            return out;
        }
        return out;
    }
}
