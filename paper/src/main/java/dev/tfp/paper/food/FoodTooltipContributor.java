/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/IFood.java @ c3b4d9536 (addTooltipInfo),
 * src/main/java/net/dries007/tfc/common/component/food/FoodTrait.java @ c3b4d9536 (addTooltipInfo),
 * src/main/java/net/dries007/tfc/common/Lore.java @ c3b4d9536 (SEALED, UNSEALED)
 */
package dev.tfp.paper.food;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.food.Nutrient;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.paper.tooltip.FoodExpiryContributor;
import dev.tfp.paper.tooltip.TooltipContext;
import dev.tfp.paper.tooltip.TooltipContributor;
import dev.tfp.paper.tooltip.TooltipData;
import dev.tfp.paper.tooltip.TooltipItem;
import dev.tfp.paper.tooltip.TooltipStyles;
import dev.tfp.paper.tooltip.TooltipText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * PAR-SURV-02: the whole food tooltip of TFC {@code IFood.addTooltipInfo}, replacing the expiry-only default
 * ({@link FoodExpiryContributor}, same id {@value FoodExpiryContributor#ID}):
 * <ol>
 *   <li>the expiry line (rotten in red / never expires in gold / "Expires on: ... (in ...)" in dark green), delegated to
 *       the default contributor, plus TFC's rare "Ewwww" line under a rotten stack;</li>
 *   <li>unless rotten, the nutrition block: "Nutrition:", saturation (x5, %), water (%) in grey, then each nutrient
 *       above zero in its TFC colour, one decimal. TFC hides the block behind Shift; a vanilla client sends no key state
 *       for tooltips, so it is always shown (ledger divergence);</li>
 *   <li>each applied trait (Salted, Brined...), red when the trait speeds up decay.</li>
 * </ol>
 * Fruit preserve jars ({@code tfp:jar/<fruit>} and {@code ..._unsealed}) also get TFC's grey italic "Sealed" /
 * "Unsealed" item lore line, which their CraftEngine items do not carry.
 *
 * <p>Netty thread, immutable inputs only (see {@link TooltipContributor}). Nutrition values are the item's food
 * definition (TFC {@code getData()} of a non-dynamic food).
 */
public final class FoodTooltipContributor implements TooltipContributor {
    private static final String JAR_PREFIX = "tfp:jar/";
    private static final String UNSEALED_SUFFIX = "_unsealed";
    /** TFC {@code Nutrient} colours, in {@link Nutrient#VALUES} order. */
    private static final List<NamedTextColor> NUTRIENT_COLOURS = List.of(NamedTextColor.GOLD, NamedTextColor.GREEN,
            NamedTextColor.DARK_GREEN, NamedTextColor.RED, NamedTextColor.DARK_PURPLE);

    private final FoodExpiryContributor expiry;
    private final Supplier<FoodDefinitions> definitions;
    private final Function<String, FoodTrait> traits;
    private final FoodConfig config;
    private final TooltipText text;

    public FoodTooltipContributor(Supplier<FoodDefinitions> definitions, Function<String, FoodTrait> traits,
            FoodConfig config, TooltipStyles styles, TooltipText text) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.traits = Objects.requireNonNull(traits, "traits");
        this.config = Objects.requireNonNull(config, "config");
        this.text = Objects.requireNonNull(text, "text");
        this.expiry = new FoodExpiryContributor(definitions, traits, config, styles, text);
    }

    @Override
    public String id() {
        return FoodExpiryContributor.ID;
    }

    @Override
    public int order() {
        return ORDER_FOOD;
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        String locale = context.locale();
        String id = item.customId();
        if (id != null && id.startsWith(JAR_PREFIX)) {
            String key = id.endsWith(UNSEALED_SUFFIX) ? "tooltip.food.unsealed" : "tooltip.food.sealed";
            lines.accept(Component.text(text.get(locale, key), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        }
        Optional<FoodState> read = state(item);
        if (read.isEmpty()) {
            return;
        }
        ClockSnapshot clock = context.clock();
        FoodDefinitions defs = definitions.get();
        FoodState state = read.get().sanitize(defs.decayModifier(item.id()), clock.playerTicks(), config);
        boolean rotten = state.creationDate() == FoodDecay.ROTTEN_FLAG;

        expiry.contribute(item, context, lines);
        if (rotten && rottenSpecial(item)) {
            lines.accept(Component.text(text.get(locale, "tooltip.food.rotten_special"), NamedTextColor.RED));
        }

        if (!rotten) {
            defs.get(item.id()).ifPresent(data -> nutrition(data, locale, lines));
        }

        for (FoodTrait trait : state.traits()) {
            String key = traitKey(trait.id());
            String name = text.get(locale, key);
            if (name.equals(key)) {
                continue; // TFC: a trait without a translation key shows nothing
            }
            Component line = Component.text(name);
            lines.accept(trait.decayModifier() > 1 ? line.color(NamedTextColor.RED) : line);
        }
    }

    /** TFC's nutrition lines (shown as if Shift were held). Nothing when the food has no nutrition at all. */
    private void nutrition(FoodData data, String locale, Consumer<Component> lines) {
        List<Component> out = new ArrayList<>(7);
        float saturation = data.saturation();
        if (saturation > 0) {
            // 100% saturation means a full hunger bar of saturation
            out.add(Component.text(text.get(locale, "tooltip.nutrition.saturation", (int) (saturation * 5)),
                    NamedTextColor.GRAY));
        }
        int water = (int) data.water();
        if (water > 0) {
            out.add(Component.text(text.get(locale, "tooltip.nutrition.water", water), NamedTextColor.GRAY));
        }
        for (Nutrient nutrient : Nutrient.VALUES) {
            float value = nutrient.of(data);
            if (value > 0) {
                out.add(Component.text(" - " + text.get(locale, "tooltip.nutrient." + nutrient.serializedName())
                        + ": " + String.format(Locale.ROOT, "%.1f", value), NUTRIENT_COLOURS.get(nutrient.ordinal())));
            }
        }
        if (!out.isEmpty()) {
            lines.accept(Component.text(text.get(locale, "tooltip.nutrition"), NamedTextColor.GRAY));
            out.forEach(lines);
        }
    }

    /** The lang key of a trait: {@code tfp:salted} → {@code tooltip.food_trait.salted}. */
    static String traitKey(String traitId) {
        int colon = traitId.indexOf(':');
        return "tooltip.food_trait." + (colon < 0 ? traitId : traitId.substring(colon + 1));
    }

    /**
     * TFC shows "Ewwww..." under one rotten stack in 256 ({@code (stack.hashCode() * 1928634918231L) & 0xFF}, an
     * identity hash). Here the hash is of the item id and count, so the choice is stable per packet.
     */
    static boolean rottenSpecial(TooltipItem item) {
        long hash = Objects.hash(item.id(), item.count());
        return ((hash * 1928634918231L) & 0xFF) == 0;
    }

    /** The {@code tfp:food} record of the item (same reading as {@link FoodExpiryContributor}). */
    private Optional<FoodState> state(TooltipItem item) {
        TooltipData food = item.values().compound(FoodPdc.KEY);
        if (food == null) {
            return Optional.empty();
        }
        Number version = food.number("tfp:v");
        Number created = food.number("tfp:created");
        if (version == null || version.intValue() > FoodPdc.VERSION || created == null) {
            return Optional.empty();
        }
        List<String> ids = food.strings("tfp:traits");
        List<FoodTrait> list = new ArrayList<>();
        if (ids != null) {
            for (String t : ids) {
                list.add(traits.apply(t));
            }
        }
        return Optional.of(new FoodState(created.longValue(), list));
    }
}
