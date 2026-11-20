/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (appendHoverText)
 */
package dev.tfp.paper.mold;

import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.paper.fluid.FluidLang;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/**
 * The fluid of a mold on its tooltip: "Copper 100 / 100 mB" (TFC {@code Tooltips.fluidUnitsAndCapacityOf}). Runs on Netty
 * threads: reads the item's data only.
 */
public final class MoldTooltipContributor implements TooltipContributor {
    public static final String ID = "mold";

    private final FluidLang lang;

    public MoldTooltipContributor(FluidLang lang) {
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
        var kind = MoldItems.kindOf(item.customId());
        if (kind.isEmpty()) {
            return;
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
        lines.accept(line(context.locale(), id, amount.intValue(), kind.get().defaultCapacity()));
    }

    Component line(String locale, String fluidId, int amount, int capacity) {
        Component name = TfcFluids.get(fluidId).map(f -> {
            String text = lang.get(locale, f.descriptionId());
            return text != null ? Component.text(text) : (Component) Component.translatable(f.descriptionId());
        }).orElse(Component.text(fluidId));
        return Component.text().append(name).append(Component.text(" " + amount + " / " + capacity + " mB")).build();
    }
}
