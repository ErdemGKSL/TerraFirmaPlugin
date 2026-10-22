/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/IsIsolatedCondition.java @ c3b4d9536
 * (also IsBurntOutCondition, IsSluiceCondition, IsMaleCondition, NotPredatedCondition and AlwaysTrueCondition)
 */
package dev.tfp.paper.loot;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A loot condition: the vanilla ones TFC's tables use plus TFC's own ({@code tfc:is_isolated}, {@code tfc:is_burnt_out},
 * {@code tfc:is_sluice}, {@code tfc:is_male}, {@code tfc:not_predated}, {@code tfc:always_true}). {@link #test} draws from
 * the random source only for {@code random_chance}, {@code table_bonus} and {@code survives_explosion} (with a radius).
 */
public sealed interface LootCondition {

    boolean test(LootContext context, LootRandom random);

    static boolean all(List<LootCondition> conditions, LootContext context, LootRandom random) {
        for (LootCondition condition : conditions) {
            if (!condition.test(context, random)) {
                return false;
            }
        }
        return true;
    }

    /** {@code minecraft:survives_explosion}: without a radius always; else {@code nextFloat() <= 1 / radius}. */
    record SurvivesExplosion() implements LootCondition {
        @Override
        public boolean test(LootContext context, LootRandom random) {
            Float radius = context.explosionRadius();
            if (radius == null) {
                return true;
            }
            float chance = 1.0f / radius;
            return random.nextFloat() <= chance;
        }
    }

    /**
     * {@code minecraft:random_chance}: {@code nextFloat() < chance}. As in vanilla, {@code chance} is evaluated first (it may
     * draw itself), then the float is drawn.
     */
    record RandomChance(LootNumber chance) implements LootCondition {
        public RandomChance {
            Objects.requireNonNull(chance, "chance");
        }

        @Override
        public boolean test(LootContext context, LootRandom random) {
            float threshold = chance.getFloat(context, random);
            return random.nextFloat() < threshold;
        }
    }

    /**
     * {@code minecraft:match_tool}. {@code items} lists item ids or one {@code #tag} (null: any item); {@code enchantments}
     * are the {@code minecraft:enchantments} sub-predicates, each of which must be contained in the tool's enchantments.
     * Without a tool parameter, or with an empty hand and an item test, it fails.
     */
    record MatchTool(List<String> items, List<EnchantmentTest> enchantments) implements LootCondition {
        public MatchTool {
            items = items == null ? null : List.copyOf(items);
            enchantments = List.copyOf(enchantments);
        }

        @Override
        public boolean test(LootContext context, LootRandom random) {
            LootContext.Tool tool = context.tool();
            if (tool == null) {
                return false;
            }
            if (items != null) {
                if (tool.isEmpty()) {
                    return false;
                }
                boolean any = false;
                for (String item : items) {
                    if (item.startsWith("#") ? tool.inTag().test(item.substring(1)) : item.equals(tool.itemId())) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    return false;
                }
            }
            for (EnchantmentTest test : enchantments) {
                if (!test.containedIn(tool.enchantments())) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * One vanilla {@code EnchantmentPredicate}: with ids, some listed enchantment has a non-zero level inside the bounds;
     * without ids but with bounds, some enchantment's level is inside them; with neither, the tool has any enchantment.
     */
    record EnchantmentTest(List<String> ids, IntRange levels) {
        public EnchantmentTest {
            ids = ids == null ? null : List.copyOf(ids);
        }

        boolean containedIn(Map<String, Integer> enchantments) {
            if (ids != null) {
                for (String id : ids) {
                    int level = enchantments.getOrDefault(id, 0);
                    if (level != 0 && (levels == null || levels.contains(level))) {
                        return true;
                    }
                }
                return false;
            }
            if (levels != null) {
                return enchantments.values().stream().anyMatch(levels::contains);
            }
            return !enchantments.isEmpty();
        }
    }

    /** An inclusive integer range; a null bound is open. */
    record IntRange(Integer min, Integer max) {
        boolean contains(int value) {
            return (min == null || value >= min) && (max == null || value <= max);
        }
    }

    /**
     * {@code minecraft:block_state_property}: the block is {@code block} and every listed property matches (an exact
     * value, or an inclusive numeric range). A property the state lacks fails.
     */
    record BlockStateProperty(String block, Map<String, ValueMatch> properties) implements LootCondition {
        public BlockStateProperty {
            Objects.requireNonNull(block, "block");
            properties = Map.copyOf(properties);
        }

        @Override
        public boolean test(LootContext context, LootRandom random) {
            if (context.blockId() == null || !block.equals(context.blockId())) {
                return false;
            }
            for (Map.Entry<String, ValueMatch> e : properties.entrySet()) {
                String value = context.blockState().get(e.getKey());
                if (value == null || !e.getValue().matches(value)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** One property matcher of {@link BlockStateProperty}: {@code exact}, or a range {@code [min, max]} (either open). */
    record ValueMatch(String exact, String min, String max) {
        boolean matches(String value) {
            if (exact != null) {
                return exact.equals(value);
            }
            try {
                int v = Integer.parseInt(value);
                return (min == null || v >= Integer.parseInt(min)) && (max == null || v <= Integer.parseInt(max));
            } catch (NumberFormatException e) {
                return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
            }
        }
    }

    /** {@code minecraft:inverted}. */
    record Inverted(LootCondition term) implements LootCondition {
        public Inverted {
            Objects.requireNonNull(term, "term");
        }

        @Override
        public boolean test(LootContext context, LootRandom random) {
            return !term.test(context, random);
        }
    }

    /** {@code minecraft:any_of}: short-circuits in order. */
    record AnyOf(List<LootCondition> terms) implements LootCondition {
        public AnyOf {
            terms = List.copyOf(terms);
        }

        @Override
        public boolean test(LootContext context, LootRandom random) {
            for (LootCondition term : terms) {
                if (term.test(context, random)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** {@code minecraft:all_of}: short-circuits in order. */
    record AllOf(List<LootCondition> terms) implements LootCondition {
        public AllOf {
            terms = List.copyOf(terms);
        }

        @Override
        public boolean test(LootContext context, LootRandom random) {
            return all(terms, context, random);
        }
    }

    /** {@code minecraft:table_bonus}: {@code nextFloat() < chances[min(level, last)]}, level of the tool's enchantment. */
    record TableBonus(String enchantment, List<Float> chances) implements LootCondition {
        public TableBonus {
            Objects.requireNonNull(enchantment, "enchantment");
            chances = List.copyOf(chances);
            if (chances.isEmpty()) {
                throw new IllegalArgumentException("table_bonus needs at least one chance");
            }
        }

        @Override
        public boolean test(LootContext context, LootRandom random) {
            int level = context.tool() == null ? 0 : context.tool().level(enchantment);
            float chance = chances.get(Math.min(level, chances.size() - 1));
            return random.nextFloat() < chance;
        }
    }

    /** {@code minecraft:killed_by_player}: the {@code last_damage_player} parameter is present. */
    record KilledByPlayer() implements LootCondition {
        @Override
        public boolean test(LootContext context, LootRandom random) {
            return context.killedByPlayer();
        }
    }

    /** {@code minecraft:damage_source_properties} with {@code tags}: every listed tag is (or is not) on the damage type. */
    record DamageSourceTags(Map<String, Boolean> expected) implements LootCondition {
        public DamageSourceTags {
            expected = Map.copyOf(expected);
        }

        @Override
        public boolean test(LootContext context, LootRandom random) {
            if (context.damageTags() == null) {
                return false;
            }
            for (Map.Entry<String, Boolean> e : expected.entrySet()) {
                if (context.damageTags().contains(e.getKey()) != e.getValue()) {
                    return false;
                }
            }
            return true;
        }
    }

    /** {@code tfc:is_isolated}: the {@code tfc:isolated} parameter is present. */
    record IsIsolated() implements LootCondition {
        @Override
        public boolean test(LootContext context, LootRandom random) {
            return context.isolated();
        }
    }

    /** {@code tfc:is_burnt_out}: the {@code tfc:burnt_out} parameter is present. */
    record IsBurntOut() implements LootCondition {
        @Override
        public boolean test(LootContext context, LootRandom random) {
            return context.burntOut();
        }
    }

    /** {@code tfc:is_sluice}: the {@code tfc:sluice} parameter is present. */
    record IsSluice() implements LootCondition {
        @Override
        public boolean test(LootContext context, LootRandom random) {
            return context.sluice();
        }
    }

    /** {@code tfc:is_male}: the killed entity is a male TFC animal. */
    record IsMale() implements LootCondition {
        @Override
        public boolean test(LootContext context, LootRandom random) {
            return context.thisAnimal() != null && context.thisAnimal().male();
        }
    }

    /** {@code tfc:not_predated}: no attacker, or a player, or anything outside the land/ocean predator tags. */
    record NotPredated() implements LootCondition {
        @Override
        public boolean test(LootContext context, LootRandom random) {
            return context.attacker() != LootContext.Attacker.PREDATOR;
        }
    }

    /** {@code tfc:always_true}. */
    record AlwaysTrue() implements LootCondition {
        @Override
        public boolean test(LootContext context, LootRandom random) {
            return true;
        }
    }
}
