/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.feature;

import dev.tfp.core.fluid.TfcFluid;
import dev.tfp.core.fluid.TfcFluids;
import dev.tfp.core.item.feature.FeatureLine;
import dev.tfp.core.item.feature.LinePart;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Turns a core {@link FeatureLine} into the chat component a vanilla client shows (PAR-INFRA-50), in the viewer's
 * language: TFC keys and names from {@link FeatureLang}, translated on the server because a vanilla client cannot
 * resolve them; vanilla keys ({@code container.shulkerBox.more}, {@code item.minecraft.*}) are left to the client as
 * translatable components. Minecraft's {@code %s} / {@code %1$s} / {@code %%} format; the {@code §} codes of TFC's lang
 * strings are parsed per literal piece and do not reach the arguments, as on TFC's client. Any thread.
 */
public final class FeatureLineRenderer {
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s|%%");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final FeatureLang lang;
    private final Function<String, String> vanillaItemKey;

    /**
     * @param vanillaItemKey the client translation key of a {@code minecraft:} item id ({@code item.minecraft.egg},
     *                       {@code block.minecraft.dirt}), or {@code null} when it is not an item
     */
    public FeatureLineRenderer(FeatureLang lang, Function<String, String> vanillaItemKey) {
        this.lang = Objects.requireNonNull(lang, "lang");
        this.vanillaItemKey = Objects.requireNonNull(vanillaItemKey, "vanillaItemKey");
    }

    /** The whole line with TFC's line style. */
    public Component render(FeatureLine line, String locale) {
        List<Component> parts = new ArrayList<>(line.parts().size());
        for (LinePart part : line.parts()) {
            parts.add(part(part, locale));
        }
        TextComponent.Builder out = Component.text();
        out.append(parts);
        switch (line.color()) {
            case GRAY -> out.color(NamedTextColor.GRAY);
            case DARK_GREEN -> out.color(NamedTextColor.DARK_GREEN);
            case AQUA -> out.color(NamedTextColor.AQUA);
            case NONE -> {
                // the pipeline's white base style
            }
        }
        if (line.italic()) {
            out.decoration(TextDecoration.ITALIC, true);
        }
        return out.build();
    }

    Component part(LinePart part, String locale) {
        return switch (part) {
            case LinePart.Literal l -> Component.text(l.text());
            case LinePart.Key k -> key(k, locale);
            case LinePart.ItemName n -> itemName(n.itemId(), locale);
            case LinePart.FluidName f -> fluidName(f.fluidId(), locale);
        };
    }

    private Component key(LinePart.Key key, String locale) {
        List<Component> args = new ArrayList<>(key.args().size());
        for (LinePart arg : key.args()) {
            args.add(part(arg, locale));
        }
        String pattern = lang.get(locale, key.key());
        if (pattern == null) {
            return Component.translatable(key.key(), args);
        }
        return format(pattern, args);
    }

    /** {@code ItemStack.getHoverName()} by id: TFC's item or block name, a client translation for vanilla. */
    Component itemName(String itemId, String locale) {
        int colon = itemId.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : itemId.substring(0, colon);
        String path = itemId.substring(colon + 1);
        if (namespace.equals("minecraft")) {
            String key = vanillaItemKey.apply(itemId);
            return key == null ? Component.text(itemId) : Component.translatable(key);
        }
        String dotted = path.replace('/', '.');
        String name = lang.get(locale, "item.tfc." + dotted);
        if (name == null) {
            name = lang.get(locale, "block.tfc." + dotted);
        }
        return name == null ? Component.text(itemId) : LEGACY.deserialize(name);
    }

    /** {@code FluidStack.getHoverName()} by id. */
    Component fluidName(String fluidId, String locale) {
        Optional<TfcFluid> fluid = TfcFluids.get(fluidId);
        String key = fluid.map(TfcFluid::descriptionId)
                .orElse("fluid." + fluidId.replace(':', '.').replace('/', '.'));
        String name = lang.get(locale, key);
        if (name != null) {
            return Component.text(name);
        }
        return fluid.isPresent() || fluidId.startsWith("minecraft:") ? Component.translatable(key)
                : Component.text(fluidId);
    }

    /** Minecraft's translation format over components; literal pieces parsed as legacy {@code §} text. */
    static Component format(String pattern, List<Component> args) {
        List<Component> parts = new ArrayList<>();
        Matcher m = ARG.matcher(pattern);
        int last = 0;
        int next = 0;
        while (m.find()) {
            if (m.start() > last) {
                parts.add(LEGACY.deserialize(pattern.substring(last, m.start())));
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
            parts.add(LEGACY.deserialize(pattern.substring(last)));
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return Component.text().append(parts).build();
    }
}
