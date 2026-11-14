/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/IFood.java @ c3b4d9536 (addTooltipInfo),
 * src/main/java/net/dries007/tfc/common/component/food/FoodTrait.java @ c3b4d9536 (addTooltipInfo),
 * src/main/java/net/dries007/tfc/common/Lore.java @ c3b4d9536 (SEALED, UNSEALED),
 * src/main/java/net/dries007/tfc/common/component/food/FoodComponent.java @ c3b4d9536 (getData of a dynamic food),
 * src/main/java/net/dries007/tfc/common/component/item/ItemListComponent.java @ c3b4d9536 (addTooltipInfo),
 * src/main/java/net/dries007/tfc/util/tooltip/Tooltips.java @ c3b4d9536 (countOfItem)
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
import java.util.Map;
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
 *   <li>each applied trait (Salted, Brined...), red when the trait speeds up decay;</li>
 *   <li>PAR-PROC-36: the ingredients of a salad ({@code tfp:ingredients}), one grey {@code "1 x <name>"} line each
 *       (TFC {@code ItemListComponent.addTooltipInfo}, which {@code ClientForgeEventHandler} adds right after the food
 *       tooltip). The name is the client's translation of the item's lang key.</li>
 * </ol>
 * Fruit preserve jars ({@code tfp:jar/<fruit>} and {@code ..._unsealed}) also get TFC's grey italic "Sealed" /
 * "Unsealed" item lore line, which their CraftEngine items do not carry.
 *
 * <p>Netty thread, immutable inputs only (see {@link TooltipContributor}). Nutrition values and the decay modifier
 * are the stack's own food data when it is a dynamic food ({@code tfp:dynamic_food}, a salad: TFC {@code getData()} of
 * a dynamic food), else the item's food definition.
 */
public final class FoodTooltipContributor implements TooltipContributor {
    private static final String JAR_PREFIX = "tfp:jar/";
    private static final String UNSEALED_SUFFIX = "_unsealed";
    /** TFC {@code Nutrient} colours, in {@link Nutrient#VALUES} order. */
    private static final List<NamedTextColor> NUTRIENT_COLOURS = List.of(NamedTextColor.GOLD, NamedTextColor.GREEN,
            NamedTextColor.DARK_GREEN, NamedTextColor.RED, NamedTextColor.DARK_PURPLE);

    /** The salad PDC keys ({@code SaladPdc}) as the packet layer sees them. */
    static final String DYNAMIC_FOOD = "tfp:dynamic_food";
    static final String INGREDIENTS = "tfp:ingredients";
    /** {@code SaladPdc.VERSION} (pinned by {@code DynamicFoodTest.thePacketKeysMatchSaladPdc}). */
    static final int DYNAMIC_VERSION = 1;

    private final FoodExpiryContributor expiry;
    private final TooltipStyles styles;
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
        this.styles = Objects.requireNonNull(styles, "styles");
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
        Optional<FoodData> dynamic = dynamicFood(item.values());
        float decay = dynamic.map(FoodData::decayModifier).orElseGet(() -> defs.decayModifier(item.id()));
        FoodState state = read.get().sanitize(decay, clock.playerTicks(), config);
        boolean rotten = state.creationDate() == FoodDecay.ROTTEN_FLAG;

        if (dynamic.isPresent()) {
            // the expiry line with the stack's own decay modifier (a salad's 4.0, the definition says 4.5)
            FoodDefinitions own = new FoodDefinitions(Map.of(item.id(), dynamic.get()));
            new FoodExpiryContributor(() -> own, traits, config, styles, text).contribute(item, context, lines);
        } else {
            expiry.contribute(item, context, lines);
        }
        if (rotten && rottenSpecial(item)) {
            lines.accept(Component.text(text.get(locale, "tooltip.food.rotten_special"), NamedTextColor.RED));
        }

        if (!rotten) {
            dynamic.or(() -> defs.get(item.id())).ifPresent(data -> nutrition(data, locale, lines));
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

        List<String> ingredients = item.values().strings(INGREDIENTS);
        if (ingredients != null) {
            for (String ingredient : ingredients) {
                // TFC stores each ingredient with count 1 (copyWithCount(1)), so the line reads "1 x <name>"
                lines.accept(Component.text("1 x ", NamedTextColor.GRAY)
                        .append(Component.translatable(itemNameKey(ingredient))));
            }
        }
    }

    /**
     * The stack's dynamic food data ({@code tfp:dynamic_food}, the {@code SaladPdc} layout), or empty when absent,
     * malformed or of a newer version.
     */
    static Optional<FoodData> dynamicFood(TooltipData values) {
        TooltipData f = values.compound(DYNAMIC_FOOD);
        if (f == null) {
            return Optional.empty();
        }
        Number v = f.number("tfp:v");
        if (v == null || v.intValue() > DYNAMIC_VERSION) {
            return Optional.empty();
        }
        return Optional.of(new FoodData((int) num(f, "tfp:hunger"), num(f, "tfp:water"), num(f, "tfp:saturation"),
                (int) num(f, "tfp:intoxication"), num(f, "tfp:grain"), num(f, "tfp:fruit"), num(f, "tfp:vegetables"),
                num(f, "tfp:protein"), num(f, "tfp:dairy"), num(f, "tfp:decay")));
    }

    private static float num(TooltipData data, String key) {
        Number n = data.number(key);
        return n == null ? 0f : n.floatValue();
    }

    /**
     * The client lang key of an item's name: {@code tfp:food/carrot} → {@code item.tfp.food.carrot} (our generated
     * CraftEngine items' {@code item_lang_key}), {@code minecraft:carrot} → {@code item.minecraft.carrot}.
     */
    static String itemNameKey(String itemId) {
        int colon = itemId.indexOf(':');
        String ns = colon < 0 ? "minecraft" : itemId.substring(0, colon);
        String path = colon < 0 ? itemId : itemId.substring(colon + 1);
        return "item." + ns + "." + path.replace('/', '.');
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
