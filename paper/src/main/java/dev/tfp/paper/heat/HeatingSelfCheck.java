/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/item/HeatingItemTest.java @ c3b4d9536 (testCookMeat)
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatLevel;
import dev.tfp.core.heat.HeatState;
import dev.tfp.core.heat.ItemHeatDefinitions;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.recipe.TagMembers;
import dev.tfp.paper.tags.TagSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.LongSupplier;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-04: TFC's {@code HeatingItemTest.testCookMeat} as a check over live services, for the MockBukkit test on the
 * datagen output and the in-server self-test {@code heat.cook_meat}: every item of TFC's raw meats tag
 * ({@code #c:foods/raw_meat}) outside the minecraft namespace is heatable, reads back
 * {@code Heat.maxVisibleTemperature()} after being set to it, and has a heating recipe that fires at that temperature.
 */
public final class HeatingSelfCheck {
    /** TFC {@code TFCTags.Items.RAW_MEATS} ({@code Tags.Items.FOODS_RAW_MEAT}). */
    public static final String RAW_MEATS = "c:foods/raw_meat";

    /**
     * The services the check reads.
     *
     * @param vanillaIds every vanilla id of the server, to expand the tag
     */
    public record Services(HeatingService heating, ItemHeatHooks heat, ItemHeatDefinitions definitions,
            CustomItems items, TagSnapshot tags, Map<TagType, ? extends Collection<String>> vanillaIds, LongSupplier now) {
        public Services {
            Objects.requireNonNull(heating, "heating");
            Objects.requireNonNull(heat, "heat");
            Objects.requireNonNull(definitions, "definitions");
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(tags, "tags");
            Objects.requireNonNull(vanillaIds, "vanillaIds");
            Objects.requireNonNull(now, "now");
        }
    }

    /**
     * The outcome.
     *
     * @param meats    the non-vanilla raw meats checked, sorted
     * @param problems one line per failed assertion (TFC's messages), empty when all passed
     */
    public record Result(List<String> meats, List<String> problems) {
        public Result {
            meats = List.copyOf(meats);
            problems = List.copyOf(problems);
        }

        public boolean passed() {
            return problems.isEmpty() && !meats.isEmpty();
        }
    }

    private HeatingSelfCheck() {
    }

    /** Runs the check; creates stacks, so it runs where ItemStacks may be made (any server thread). */
    public static Result run(Services s) {
        Set<String> meats = new TreeSet<>(new TagMembers(s.tags(), s.vanillaIds()).members(TagType.ITEM, RAW_MEATS));
        meats.removeIf(id -> id.startsWith("minecraft:"));
        List<String> problems = new ArrayList<>();
        if (meats.isEmpty()) {
            problems.add("no non-vanilla items in #" + RAW_MEATS);
        }
        long now = s.now().getAsLong();
        for (String id : meats) {
            Optional<ItemStack> created = s.items().create(id, 1);
            if (created.isEmpty()) {
                problems.add("Item does not exist: " + id);
                continue;
            }
            ItemStack stack = created.get();
            if (!(s.definitions().heatCapacity(id) > 0f)) {
                problems.add("Meat not heatable: " + id);
                continue;
            }
            HeatPdc.write(stack, HeatState.cold(now).set(HeatLevel.MAX_VISIBLE, now));
            float read = s.heat().temperature(stack);
            if (read != HeatLevel.MAX_VISIBLE) {
                problems.add("Heat not achieved for stack: " + id + " (" + read + ")");
            }
            if (s.heating().recipe(stack).isEmpty()) {
                problems.add("No heating recipe found for meat: " + id);
            } else if (s.heating().convertIfReady(stack).isEmpty()) {
                problems.add("Heating recipe does not fire at max visible temperature: " + id);
            }
        }
        return new Result(List.copyOf(meats), problems);
    }
}
