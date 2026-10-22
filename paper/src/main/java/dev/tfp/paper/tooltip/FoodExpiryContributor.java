/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/IFood.java @ c3b4d9536 (addTooltipInfo, the expiry part)
 */
package dev.tfp.paper.tooltip;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDecay;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.paper.food.FoodPdc;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The expiry line of a dated food (the first part of TFC {@code IFood.addTooltipInfo}): "Rotten!" in red once it is
 * rotten, "Never expires" in gold for a never-decaying food, otherwise the {@link FoodExpiryStyle} text in dark green.
 * The transient and invisible never-decay flags show nothing, as in TFC.
 *
 * <p>Evaluated lazily from the stack's {@code tfp:food} record ({@link FoodPdc}) at the packet's clock reading. Trait
 * and nutrition lines, and TFC's rare "Ewwww" line for a rotten stack, belong to PAR-SURV-02, which may replace this
 * contributor (same {@link #ID}). Player ticks become calendar ticks at TFC's default rate of one.
 */
public final class FoodExpiryContributor implements TooltipContributor {
    public static final String ID = "food";

    private final Supplier<FoodDefinitions> definitions;
    private final Function<String, FoodTrait> traits;
    private final FoodConfig config;
    private final TooltipStyles styles;
    private final TooltipText text;

    public FoodExpiryContributor(Supplier<FoodDefinitions> definitions, Function<String, FoodTrait> traits,
            FoodConfig config, TooltipStyles styles, TooltipText text) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.traits = Objects.requireNonNull(traits, "traits");
        this.config = Objects.requireNonNull(config, "config");
        this.styles = Objects.requireNonNull(styles, "styles");
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER_FOOD;
    }

    /** The {@code tfp:food} record of the item, or empty when it has none or one this version cannot read. */
    Optional<FoodState> state(TooltipItem item) {
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
            for (String id : ids) {
                list.add(traits.apply(id));
            }
        }
        return Optional.of(new FoodState(created.longValue(), list));
    }

    @Override
    public void contribute(TooltipItem item, TooltipContext context, Consumer<Component> lines) {
        Optional<FoodState> read = state(item);
        if (read.isEmpty()) {
            return;
        }
        ClockSnapshot clock = context.clock();
        long now = clock.playerTicks();
        float foodModifier = definitions.get().decayModifier(item.id());
        FoodState state = read.get().sanitize(foodModifier, now, config);
        long created = state.creationDate();
        float modifier = state.decayDateModifier(foodModifier, config);
        String locale = context.locale();
        if (created == FoodDecay.ROTTEN_FLAG) {
            lines.accept(Component.text(text.get(locale, "food.stage.rotten"), NamedTextColor.RED));
        } else if (created == FoodDecay.NEVER_DECAY_FLAG
                || (created != FoodDecay.TRANSIENT_NEVER_DECAY_FLAG && modifier == Float.POSITIVE_INFINITY)) {
            lines.accept(Component.text(text.get(locale, "food.stage.never_rots"), NamedTextColor.GOLD));
        } else if (created != FoodDecay.TRANSIENT_NEVER_DECAY_FLAG && created != FoodDecay.INVISIBLE_NEVER_DECAY_FLAG) {
            long left = FoodDecay.rottenDate(created, modifier) - now; // player ticks
            String date = TimeDeltaStyle.timeAndDate(clock.calendarTicks() + left, clock.daysInMonth(), locale, text);
            String delta = styles.timeDelta().timeDelta(left, clock.daysInMonth(), locale, text);
            styles.foodExpiry().text(date, delta, locale, text)
                    .ifPresent(s -> lines.accept(Component.text(s, NamedTextColor.DARK_GREEN)));
        }
    }
}
