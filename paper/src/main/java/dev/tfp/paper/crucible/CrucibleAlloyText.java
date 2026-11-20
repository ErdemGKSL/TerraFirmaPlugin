/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/CrucibleScreen.java @ c3b4d9536 (renderBg: the title and the
 *       "units (percent)" lines)
 */
package dev.tfp.paper.crucible;

import dev.tfp.core.alloy.AlloyDisplay;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.paper.fluid.FluidLang;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * PAR-METAL-12: the text TFC's crucible screen draws over its panel: the metal the alloy makes, underlined, then for each
 * metal "name:" and "  N mB (P%)" (TFC {@code tfc.tooltip.crucible_content_line}), here as the lines of a tooltip. Thread-safe
 * (reads the immutable alloy recipes and the fluid names).
 */
public final class CrucibleAlloyText {
    private final FluidLang lang;
    private final Supplier<List<AlloyRecipe>> recipes;

    public CrucibleAlloyText(FluidLang lang, Supplier<List<AlloyRecipe>> recipes) {
        this.lang = Objects.requireNonNull(lang, "lang");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
    }

    /** The lines for {@code alloy}: the title first, then the metals; empty for an empty alloy. */
    public List<Component> lines(String locale, FluidAlloy alloy) {
        if (alloy.isEmpty()) {
            return List.of();
        }
        AlloyDisplay.View view = AlloyDisplay.of(alloy, recipes.get());
        List<Component> out = new ArrayList<>();
        out.add(name(locale, view.result()).decorate(TextDecoration.UNDERLINED));
        for (AlloyDisplay.Entry e : view.entries()) {
            out.add(Component.text().append(name(locale, e.fluid())).append(Component.text(":")).build());
            out.add(Component.text("  " + Math.round(e.amount()) + " mB (" + e.percent() + "%)", NamedTextColor.DARK_GRAY));
        }
        return out;
    }

    private Component name(String locale, String fluidId) {
        Optional<TfcFluid> fluid = TfcFluids.get(fluidId);
        if (fluid.isEmpty()) {
            return Component.text(fluidId);
        }
        String key = fluid.get().descriptionId();
        String text = lang.get(locale, key);
        return text != null ? Component.text(text) : Component.translatable(key);
    }
}
