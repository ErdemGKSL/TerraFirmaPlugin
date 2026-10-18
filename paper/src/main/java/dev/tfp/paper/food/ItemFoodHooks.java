/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/food/FoodCapability.java @ c3b4d9536 (isRotten, hasTrait, addTooltipInfo)
 */
package dev.tfp.paper.food;

import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodStage;
import dev.tfp.core.food.FoodState;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.paper.i18n.Messages;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import org.bukkit.inventory.ItemStack;

/**
 * The Paper implementation of the food queries ({@link FoodHooks}) on Bukkit item stacks, plus what the
 * event glue needs: the stage, the tooltip line and the stack rule at the current player tick.
 *
 * <p>Lazy: nothing ticks. "Now" is one volatile read of the published clock per call
 * ({@code ClockSnapshot.playerTicks}, TFC {@code Calendars.get().getTicks()}), and every derived value comes
 * from the stamped creation date ({@link FoodPdc}). Thread-safe; a stack is read on the thread that owns it.
 */
public final class ItemFoodHooks implements FoodHooks<ItemStack> {
    /**
     * The item's food-data {@code decay_modifier} (TFC {@code FoodData.decayModifier}). The food definition
     * lookup by ingredient belongs to M3-SURV-02; until then {@link #UNIT} (1) is used for every food.
     */
    @FunctionalInterface
    public interface DecayModifiers {
        DecayModifiers UNIT = stack -> 1f;

        float of(ItemStack stack);
    }

    /** Message keys of the tooltip line, by stage. */
    public static final String KEY_FRESH = "food.stage.fresh";
    public static final String KEY_ROTTEN = "food.stage.rotten";
    public static final String KEY_NEVER_ROTS = "food.stage.never_rots";

    private final LongSupplier now;
    private final FoodConfig config;
    private final DecayModifiers modifiers;

    /** @param now the current player tick (read once per call) */
    public ItemFoodHooks(LongSupplier now, FoodConfig config, DecayModifiers modifiers) {
        this.now = Objects.requireNonNull(now, "now");
        this.config = Objects.requireNonNull(config, "config");
        this.modifiers = Objects.requireNonNull(modifiers, "modifiers");
    }

    public long now() {
        return now.getAsLong();
    }

    public FoodConfig config() {
        return config;
    }

    public float decayModifier(ItemStack stack) {
        return modifiers.of(stack);
    }

    /** Stamps a newly made food at the current tick ({@link FoodStamp}). */
    public boolean stamp(ItemStack stack, List<FoodTrait> traits) {
        return stack != null && FoodStamp.stamp(stack, now(), traits, modifiers.of(stack), config);
    }

    /** The stage at the current tick, or empty for an undated stack (not a food). */
    public Optional<FoodStage> stage(ItemStack stack) {
        Optional<FoodState> s = FoodPdc.read(stack);
        return s.map(state -> state.stage(modifiers.of(stack), now(), config));
    }

    @Override
    public boolean isRotten(ItemStack stack) {
        return stage(stack).map(FoodStage::isRotten).orElse(false);
    }

    /** Accepts TFC ids ({@code tfc:salted}) as well as ours ({@code tfp:salted}). */
    @Override
    public boolean hasTrait(ItemStack stack, String trait) {
        String id = trait.startsWith("tfc:") ? "tfp:" + trait.substring(4) : trait;
        Optional<FoodState> s = FoodPdc.read(stack);
        if (s.isEmpty()) {
            return false;
        }
        for (FoodTrait t : s.get().traits()) {
            if (t.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The tooltip message key for the stack's stage, or empty for an undated stack. */
    public Optional<String> tooltipKey(ItemStack stack) {
        return stage(stack).map(ItemFoodHooks::key);
    }

    /** The translated tooltip line (FRESH, ROTTEN or NEVER_ROTS) for {@code locale}, or empty for an undated stack. */
    public Optional<String> tooltipLine(ItemStack stack, Messages messages, Locale locale) {
        return tooltipKey(stack).map(k -> messages.get(locale, k));
    }

    static String key(FoodStage stage) {
        return switch (stage) {
            case FRESH -> KEY_FRESH;
            case ROTTEN -> KEY_ROTTEN;
            case NEVER_ROTS -> KEY_NEVER_ROTS;
        };
    }

    /** {@link FoodStacks#canMerge} at the current tick. */
    public boolean canMerge(ItemStack a, ItemStack b) {
        return FoodStacks.canMerge(a, b, a == null ? 1f : modifiers.of(a), now(), config);
    }
}
