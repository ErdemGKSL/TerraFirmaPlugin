/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/FallenLeavesBlock.java @ c3b4d9536 (the LAYERS property that sets the
 * fallen leaves count), with the loot tables of src/main/resources/data/tfc/loot_table/blocks/{plant,wood/leaves,
 * wood/twig,wood/fallen_leaves,groundcover/stick}
 */
package dev.tfp.core.gather;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * The yields of TFC's sticks, twigs, fallen leaves, leaves and plants when they are harvested. Pure and thread-safe.
 *
 * <p>The yields are the loot tables evaluated the way Minecraft evaluates them: entries are rolled in file order, a
 * {@code random_chance} consumes one roll from the injected source, an alternatives group yields from its first child whose
 * conditions pass, and a {@code set_count} with a uniform range consumes one roll. The source is injected so that a
 * seeded source gives the same yields on every call.
 *
 * <p>TFC's rules, all checked against the pinned commit:
 * <ul>
 *   <li>No correct tool is required to break any of these blocks. Their loot tables gate on the tool through the
 *       conditions only: leaves yield their own item only with shears or silk touch, and sticks drop from leaves with a
 *       sharp tool at 20% or otherwise at 5%, never with shears or silk touch.</li>
 *   <li>A twig yields its twig once. A fallen leaves block yields as many fallen leaves as its layer count.</li>
 *   <li>A creative player gets no drops from a broken block ({@code Block.playerWillDestroy}); see {@link #breakDrops}.</li>
 * </ul>
 */
public final class HarvestRules {
    private HarvestRules() {}

    /** The loot table of a block id, or throws when no harvest table is known for it. */
    public static HarvestTable table(String blockId) {
        return HarvestTables.find(blockId).orElseThrow(() -> new IllegalArgumentException("no harvest table for " + blockId));
    }

    /**
     * The items a block yields when it is harvested with the given held tool and block state. Equivalent to
     * {@link #drops(HarvestTable, HeldTool, Map, DoubleSupplier)} on {@link #table}.
     */
    public static List<GatherYield> dropsOf(String blockId, HeldTool tool, Map<String, String> state, DoubleSupplier rolls) {
        return drops(table(blockId), tool, state, rolls);
    }

    /**
     * The items a table yields. {@code state} holds the block state properties (for example {@code layers=3}); a property that
     * is missing from it matches no value. {@code rolls} gives values in {@code [0, 1)}, drawn in evaluation order.
     */
    public static List<GatherYield> drops(HarvestTable table, HeldTool tool, Map<String, String> state, DoubleSupplier rolls) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rolls, "rolls");
        Context context = new Context(tool, Map.copyOf(state), rolls);
        List<GatherYield> out = new ArrayList<>();
        for (LootEntry entry : table.entries()) {
            expand(entry, context, out);
        }
        return List.copyOf(out);
    }

    /**
     * The items a block yields when a player breaks it. In creative mode the vanilla break drops nothing, so this returns none;
     * otherwise it is {@link #drops}.
     */
    public static List<GatherYield> breakDrops(HarvestTable table, HeldTool tool, Map<String, String> state, DoubleSupplier rolls, boolean creative) {
        if (creative) {
            return List.of();
        }
        return drops(table, tool, state, rolls);
    }

    private record Context(HeldTool tool, Map<String, String> state, DoubleSupplier rolls) {}

    /** Expands one entry into {@code out}; returns whether it yielded. */
    private static boolean expand(LootEntry entry, Context context, List<GatherYield> out) {
        return switch (entry) {
            case LootEntry.Item item -> {
                if (!passes(item.conditions(), context)) {
                    yield false;
                }
                int count = 1;
                for (LootEntry.SetCount set : item.counts()) {
                    if (passes(set.conditions(), context)) {
                        count = count(set, context);
                    }
                }
                out.add(new GatherYield(item.itemId(), count));
                yield true;
            }
            case LootEntry.Alternatives alternatives -> {
                if (!passes(alternatives.conditions(), context)) {
                    yield false;
                }
                for (LootEntry child : alternatives.children()) {
                    if (expand(child, context, out)) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    private static int count(LootEntry.SetCount set, Context context) {
        if (set.min() == set.max()) {
            return set.min();
        }
        int span = set.max() - set.min() + 1;
        int offset = (int) Math.floor(context.rolls().getAsDouble() * span);
        return Math.min(set.min() + offset, set.max());
    }

    private static boolean passes(List<HarvestCondition> conditions, Context context) {
        for (HarvestCondition condition : conditions) {
            if (!test(condition, context)) {
                return false;
            }
        }
        return true;
    }

    private static boolean test(HarvestCondition condition, Context context) {
        return switch (condition) {
            case HarvestCondition.HasTool has -> context.tool().has(has.tag());
            case HarvestCondition.Chance chance -> context.rolls().getAsDouble() < chance.chance();
            case HarvestCondition.StateIs state -> state.properties().entrySet().stream()
                    .allMatch(e -> e.getValue().equals(context.state().get(e.getKey())));
            case HarvestCondition.AnyOf any -> any.terms().stream().anyMatch(term -> test(term, context));
            case HarvestCondition.AllOf all -> all.terms().stream().allMatch(term -> test(term, context));
            case HarvestCondition.Not not -> !test(not.term(), context);
        };
    }
}
