/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/VesselItem.java @ c3b4d9536 (appendHoverText: the contents label and
 *       the fluid line)
 */
package dev.tfp.paper.vessel;

import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.paper.alloy.AlloyPdc;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The tooltip line of a vessel that still holds a single {@code tfp:fluid} (an item from before PAR-METAL-22; a vessel with
 * {@code tfp:alloy} is shown by AlloyTooltipContributor). The vessel's alloy on its tooltip: TFC's dark green "Contents:" label, then the fluid as "Copper 100 mB". Runs on Netty
 * threads over the packet copy of the stack ({@code tfp:fluid}); the label and fluid names come from TFC's lang files.
 */
public final class VesselTooltipContributor implements TooltipContributor {
    public static final String ID = "vessel";
    static final String LABEL_KEY = "tfc.tooltip.small_vessel.contents";

    private final FluidLang lang;

    public VesselTooltipContributor(FluidLang lang) {
        this.lang = Objects.requireNonNull(lang, "lang");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_CONTENTS;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        if (!VesselItems.isVesselId(item.customId())) {
            return;
        }
        if (item.values().compound(AlloyPdc.KEY) != null) {
            return; // the mixed alloy: AlloyTooltipContributor shows it, once
        }
        TooltipData fluid = item.values().compound(FluidPdc.KEY);
        if (fluid == null) {
            return;
        }
        Number version = fluid.number("tfp:v");
        String id = fluid.string("tfp:id");
        Number amount = fluid.number("tfp:amount");
        if (version == null || version.intValue() > FluidPdc.VERSION || id == null || id.isEmpty() || amount == null
                || amount.intValue() <= 0) {
            return;
        }
        String label = lang.get(context.locale(), LABEL_KEY);
        lines.accept(Component.text(label == null ? "Contents:" : label, NamedTextColor.DARK_GREEN));
        lines.accept(line(context.locale(), id, amount.intValue()));
    }

    Component line(String locale, String fluidId, int amount) {
        return Component.text().append(name(locale, fluidId)).append(Component.text(" " + amount + " mB")).build();
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
