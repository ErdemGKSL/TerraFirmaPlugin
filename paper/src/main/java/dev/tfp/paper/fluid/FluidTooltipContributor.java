/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536 (appendHoverText),
 *       src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java @ c3b4d9536 (fluidUnitsAndCapacityOf)
 */
package dev.tfp.paper.fluid;

import dev.tfp.core.fluid.FluidContainerKind;
import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.paper.itemdata.FluidPdc;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

/**
 * PAR-FLUID-01: TFC's fluid line under a filled fluid container ({@code FluidContainerItem.appendHoverText}):
 * {@code "<amount> / <capacity> mB of <fluid>"} ({@code tfc.tooltip.fluid_units_and_capacity_of}), unstyled. The pattern
 * and the TFC fluid names come from TFC's lang files in the viewer's language ({@link FluidLang}); vanilla fluids
 * (water, lava, milk) are client translations, as TFC shows them. Capacity is the container's TFC default.
 *
 * <p>Netty thread, immutable inputs only (see {@link TooltipContributor}).
 */
public final class FluidTooltipContributor implements TooltipContributor {
    public static final String ID = "fluid";
    static final String PATTERN_KEY = "tfc.tooltip.fluid_units_and_capacity_of";
    static final String DEFAULT_PATTERN = "%s / %s mB of %s";
    /** English names of the vanilla keys a vanilla client lacks (NeoForge's milk fluid type). */
    private static final Map<String, String> CLIENT_FALLBACKS = Map.of("fluid_type.minecraft.milk", "Milk");
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    private final FluidLang lang;

    public FluidTooltipContributor(FluidLang lang) {
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
        Optional<FluidContainerKind> kind = FluidContainerItems.kindOf(item.customId());
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
        lines.accept(line(context.locale(), amount.intValue(), kind.get().defaultCapacity(), id));
    }

    /** The whole line for {@code amount} of {@code fluidId} in a container of {@code capacity}. */
    Component line(String locale, int amount, int capacity, String fluidId) {
        String pattern = lang.get(locale, PATTERN_KEY);
        return format(pattern == null ? DEFAULT_PATTERN : pattern,
                List.of(Component.text(amount), Component.text(capacity), name(locale, fluidId)));
    }

    /** TFC {@code FluidStack.getHoverName()}: the fluid type's name. */
    Component name(String locale, String fluidId) {
        Optional<TfcFluid> fluid = TfcFluids.get(fluidId);
        if (fluid.isEmpty()) {
            return Component.text(fluidId);
        }
        String key = fluid.get().descriptionId();
        String text = lang.get(locale, key);
        if (text != null) {
            return Component.text(text);
        }
        String fallback = CLIENT_FALLBACKS.get(key);
        return fallback != null ? Component.translatable(key, fallback) : Component.translatable(key);
    }

    /** Minecraft's {@code %s} / {@code %1$s} / {@code %%} translation format over components. */
    static Component format(String pattern, List<Component> args) {
        TextComponent.Builder out = Component.text();
        Matcher m = ARG.matcher(pattern);
        int last = 0;
        int next = 0;
        List<Component> parts = new ArrayList<>();
        while (m.find()) {
            if (m.start() > last) {
                parts.add(Component.text(pattern.substring(last, m.start())));
            }
            if (m.group().equals("%%")) {
                parts.add(Component.text("%"));
            } else {
                int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                if (index >= 0 && index < args.size()) {
                    parts.add(args.get(index));
                }
            }
            last = m.end();
        }
        if (last < pattern.length()) {
            parts.add(Component.text(pattern.substring(last)));
        }
        return out.append(parts).build();
    }
}
