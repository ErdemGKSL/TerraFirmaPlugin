/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/CrucibleScreen.java @ c3b4d9536 (content lines) and common/items/VesselItem.java (appendHoverText)
 */
package dev.tfp.paper.alloy;

import dev.tfp.core.alloy.AlloyCodec;
import dev.tfp.core.alloy.AlloyDisplay;
import dev.tfp.core.alloy.AlloyRecipe;
import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.vessel.VesselItems;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The mixed alloy on a vessel or crucible tooltip: TFC's dark green "Contents:" label, the alloy it makes with its amount
 * ("Molten Bronze 1000 mB", "Molten Unknown ..." for a mix no recipe makes), then one line per metal with its amount and
 * share like TFC's crucible screen ("  900 mB (90.0%)"). Reads {@code tfp:alloy} of the packet copy of the stack, on Netty
 * threads. Only a vessel or a crucible shows it.
 */
public final class AlloyTooltipContributor implements TooltipContributor {
    public static final String ID = "alloy";
    public static final String CRUCIBLE_ID = "tfp:crucible";
    static final String LABEL_KEY = "tfc.tooltip.small_vessel.contents";

    private final FluidLang lang;
    private final Supplier<List<AlloyRecipe>> recipes;

    public AlloyTooltipContributor(FluidLang lang, Supplier<List<AlloyRecipe>> recipes) {
        this.lang = Objects.requireNonNull(lang, "lang");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_CONTENTS + 1;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        String id = item.customId();
        if (id == null || !(VesselItems.isVesselId(id) || id.equals(CRUCIBLE_ID))) {
            return;
        }
        TooltipData root = item.values().compound(AlloyPdc.KEY);
        if (root == null) {
            return;
        }
        Number version = root.number("tfp:v");
        Number amount = root.number("tfp:amount");
        List<String> stored = root.strings("tfp:s");
        if (version == null || version.intValue() > AlloyPdc.VERSION || amount == null || stored == null) {
            return;
        }
        FluidAlloy alloy = AlloyCodec.decode(amount.intValue(), stored);
        if (alloy.isEmpty()) {
            return;
        }
        AlloyDisplay.View view = AlloyDisplay.of(alloy, recipes.get());
        String label = lang.get(context.locale(), LABEL_KEY);
        lines.accept(Component.text(label == null ? "Contents:" : label, NamedTextColor.DARK_GREEN));
        lines.accept(Component.text().append(name(context.locale(), view.result())).append(Component.text(" " + view.amount() + " mB")).build());
        if (view.entries().size() > 1) {
            for (AlloyDisplay.Entry entry : view.entries()) {
                lines.accept(Component.text().append(Component.text("  ")).append(name(context.locale(), entry.fluid()))
                    .append(Component.text(" " + Math.round(entry.amount()) + " mB ("))
                    .append(Component.text(entry.percent() + "%", NamedTextColor.DARK_GREEN)).append(Component.text(")")).build());
            }
        }
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
