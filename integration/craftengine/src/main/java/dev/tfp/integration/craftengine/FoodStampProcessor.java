/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.craftengine;

import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.clock.GlobalClockHolder;
import dev.tfp.core.food.FoodConfig;
import dev.tfp.core.food.FoodDefinitions;
import dev.tfp.core.food.FoodTrait;
import dev.tfp.paper.food.FoodPdc;
import dev.tfp.paper.food.FoodStamp;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.processor.ItemProcessor;
import net.momirealms.craftengine.core.item.processor.ItemProcessorFactory;
import org.bukkit.inventory.ItemStack;

/**
 * The {@code tfp:food_stamp} item data processor (M3-SURV-01, {@code craftengine-items} skill): dates a food
 * item when CraftEngine builds it (give command, loot, recipe result, API), as TFC does when a food stack is
 * created. Item YAML:
 * <pre>
 * data:
 *   tfp:food_stamp: {}                        # plain food
 *   tfp:food_stamp: { traits: [tfp:wild] }    # starting traits, applied in order
 * </pre>
 * Non-constant, so it runs on every build, not once at load. The work is {@link FoodStamp}: the rounded
 * current player tick (one volatile read of the published clock) plus the traits, written into the item PDC
 * through {@link FoodPdc}. Stacks that already carry a food entry are left alone. Before the calendar has
 * published its first reading the item stays undated (no fake tick-0 date) and the build is otherwise
 * unchanged.
 *
 * <p>Threads: CraftEngine builds on whatever thread asks for the item; the stack is new and not shared yet,
 * and the clock read is lock-free, so any thread is fine.
 */
final class FoodStampProcessor implements ItemProcessor {
    static final String PATH = "food_stamp";

    private final GlobalClockHolder clock;
    private final Supplier<FoodDefinitions> foods;
    private final List<FoodTrait> traits;

    FoodStampProcessor(GlobalClockHolder clock, List<FoodTrait> traits) {
        this(clock, () -> FoodDefinitions.EMPTY, traits);
    }

    FoodStampProcessor(GlobalClockHolder clock, Supplier<FoodDefinitions> foods, List<FoodTrait> traits) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.foods = Objects.requireNonNull(foods, "foods");
        this.traits = List.copyOf(traits);
    }

    /** The factory for {@code tfp:food_stamp}; parses {@code traits} once at config load. */
    static ItemProcessorFactory<FoodStampProcessor> factory(GlobalClockHolder clock) {
        return factory(clock, () -> FoodDefinitions.EMPTY);
    }

    /** As {@link #factory(GlobalClockHolder)}, with the food definitions that give each item's decay modifier. */
    static ItemProcessorFactory<FoodStampProcessor> factory(GlobalClockHolder clock, Supplier<FoodDefinitions> foods) {
        return value -> new FoodStampProcessor(clock, foods, parseTraits(value.value()));
    }

    /** {@code {}}, {@code null} or {@code {traits: [ids]}}; anything else is a configuration error. */
    static List<FoodTrait> parseTraits(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("tfp:food_stamp expects a map such as {} or {traits: [tfp:wild]}, got " + raw);
        }
        Object list = map.get("traits");
        if (list == null) {
            return List.of();
        }
        if (!(list instanceof List<?> ids)) {
            throw new IllegalArgumentException("tfp:food_stamp traits must be a list of trait ids, got " + list);
        }
        List<FoodTrait> out = new ArrayList<>(ids.size());
        for (Object id : ids) {
            out.add(FoodPdc.DEFAULT_TRAITS.apply(String.valueOf(id)));
        }
        return out;
    }

    List<FoodTrait> traits() {
        return traits;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public void apply(ItemBuildContext context) {
        Item item = item(context);
        if (item == null || !(item.platformItem() instanceof ItemStack stack)) {
            return;
        }
        // M3-SURV-02: the item's food-definition decay modifier (1 without a definition). It matters while the
        // starting traits are applied: TFC adds no trait to a food that is already rotten (e.g. rotten flesh).
        float decay = foods.get().decayModifier(item.id().toString());
        stamp(stack, clock.get(), traits, decay);
    }

    /**
     * {@code context.item()} through a method handle: {@link ItemBuildContext}'s supertypes implement
     * CraftEngine's relocated Adventure {@code Pointered}, which the plugin jar we compile against does not
     * contain (CraftEngine downloads it at runtime), so javac cannot resolve a direct call.
     */
    private static Item item(ItemBuildContext context) {
        try {
            return (Item) ItemHandle.ITEM.invoke((Object) context);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("ItemBuildContext.item() failed", t);
        }
    }

    /** Lazy holder: resolved on the first build, inside the server where CraftEngine's libraries exist. */
    private static final class ItemHandle {
        static final MethodHandle ITEM;

        static {
            try {
                ITEM = MethodHandles.publicLookup()
                        .findVirtual(ItemBuildContext.class, "item", MethodType.methodType(Item.class))
                        .asType(MethodType.methodType(Item.class, Object.class));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("CraftEngine ItemBuildContext.item() not found", e);
            }
        }
    }

    /** The platform-free part of {@link #apply}, for tests. @return true if the stack was dated */
    static boolean stamp(ItemStack stack, ClockSnapshot now, List<FoodTrait> traits) {
        return stamp(stack, now, traits, FoodDefinitions.UNKNOWN_DECAY_MODIFIER);
    }

    /** @param foodDecayModifier the item's food-definition decay modifier */
    static boolean stamp(ItemStack stack, ClockSnapshot now, List<FoodTrait> traits, float foodDecayModifier) {
        if (now.sequence() == 0) {
            return false; // calendar not loaded yet
        }
        return FoodStamp.stamp(stack, now.playerTicks(), traits, foodDecayModifier, FoodConfig.DEFAULT);
    }
}
