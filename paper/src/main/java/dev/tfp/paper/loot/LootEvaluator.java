/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates a {@link LootTable} the way vanilla 1.21.1 does ({@code LootTable.getRandomItemsRaw}, {@code LootPool},
 * {@code CompositeEntryBase}, {@code LootPoolSingletonContainer}): pool conditions, rolls, weighted pick among the expanded
 * entries (one {@code nextInt} only when more than one candidate), entry functions, pool functions, table functions, in
 * that order, with random draws in vanilla's order. Pure: no Bukkit, any thread. Luck is 0 (TFC's tables use neither
 * quality nor bonus rolls).
 */
public final class LootEvaluator {
    private LootEvaluator() {}

    /** The drops of {@code table}; stacks whose count ended at 0 or below are left out. */
    public static List<LootDrop> evaluate(LootTable table, LootContext context, LootRandom random) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");
        List<LootDrop> out = new ArrayList<>();
        for (LootTable.Pool pool : table.pools()) {
            if (!LootCondition.all(pool.conditions(), context, random)) {
                continue;
            }
            int rolls = pool.rolls().getInt(context, random)
                    + (int) Math.floor(pool.bonusRolls().getFloat(context, random) * LUCK);
            for (int i = 0; i < rolls; i++) {
                Stack stack = pick(pool, context, random);
                if (stack == null) {
                    continue;
                }
                apply(pool.functions(), stack, context, random);
                apply(table.functions(), stack, context, random);
                if (stack.count > 0) {
                    out.add(new LootDrop(stack.itemId, stack.count, stack.ops));
                }
            }
        }
        return List.copyOf(out);
    }

    private static final float LUCK = 0f;

    /** A stack under construction. */
    private static final class Stack {
        final String itemId;
        int count = 1;
        final List<LootFunction.StackOp> ops = new ArrayList<>();

        Stack(String itemId) {
            this.itemId = itemId;
        }
    }

    /** {@code LootPool.addRandomItem}: one roll; null when nothing (or an empty entry) was picked. */
    private static Stack pick(LootTable.Pool pool, LootContext context, LootRandom random) {
        List<LootEntry.Singleton> candidates = new ArrayList<>();
        int total = 0;
        for (LootEntry entry : pool.entries()) {
            List<LootEntry.Singleton> expanded = new ArrayList<>();
            expand(entry, context, random, expanded);
            for (LootEntry.Singleton s : expanded) {
                int weight = weight(s);
                if (weight > 0) {
                    candidates.add(s);
                    total += weight;
                }
            }
        }
        if (total == 0 || candidates.isEmpty()) {
            return null;
        }
        LootEntry.Singleton chosen;
        if (candidates.size() == 1) {
            chosen = candidates.getFirst();
        } else {
            int left = random.nextInt(total);
            chosen = null;
            for (LootEntry.Singleton s : candidates) {
                left -= weight(s);
                if (left < 0) {
                    chosen = s;
                    break;
                }
            }
            if (chosen == null) {
                return null;
            }
        }
        return create(chosen, context, random);
    }

    private static int weight(LootEntry.Singleton s) {
        return Math.max((int) Math.floor(s.weight() + s.quality() * LUCK), 0);
    }

    /** {@code LootPoolEntryContainer.expand}: whether the entry (or a child) expanded into {@code out}. */
    private static boolean expand(LootEntry entry, LootContext context, LootRandom random, List<LootEntry.Singleton> out) {
        if (!LootCondition.all(entry.conditions(), context, random)) {
            return false;
        }
        return switch (entry) {
            case LootEntry.Singleton s -> {
                out.add(s);
                yield true;
            }
            case LootEntry.Composite c -> switch (c.kind()) {
                case ALTERNATIVES -> {
                    for (LootEntry child : c.children()) {
                        if (expand(child, context, random, out)) {
                            yield true;
                        }
                    }
                    yield false;
                }
                case GROUP -> {
                    for (LootEntry child : c.children()) {
                        expand(child, context, random, out);
                    }
                    yield true;
                }
                case SEQUENCE -> {
                    for (LootEntry child : c.children()) {
                        if (!expand(child, context, random, out)) {
                            yield false;
                        }
                    }
                    yield true;
                }
            };
        };
    }

    private static Stack create(LootEntry.Singleton entry, LootContext context, LootRandom random) {
        return switch (entry) {
            case LootEntry.Empty e -> null;
            case LootEntry.Item item -> {
                Stack stack = new Stack(item.name());
                apply(item.functions(), stack, context, random);
                yield stack;
            }
        };
    }

    private static void apply(List<LootFunction> functions, Stack stack, LootContext context, LootRandom random) {
        for (LootFunction function : functions) {
            if (!LootCondition.all(function.conditions(), context, random)) {
                continue;
            }
            run(function, stack, context, random);
        }
    }

    private static void run(LootFunction function, Stack stack, LootContext context, LootRandom random) {
        switch (function) {
            case LootFunction.SetCount set -> {
                int base = set.add() ? Math.max(stack.count, 0) : 0;
                stack.count = base + set.count().getInt(context, random);
            }
            case LootFunction.ExplosionDecay decay -> {
                Float radius = context.explosionRadius();
                if (radius != null) {
                    float chance = 1.0f / radius;
                    int count = Math.max(stack.count, 0);
                    int kept = 0;
                    for (int i = 0; i < count; i++) {
                        if (random.nextFloat() <= chance) {
                            kept++;
                        }
                    }
                    stack.count = kept;
                }
            }
            case LootFunction.LimitCount limit -> {
                int value = Math.max(stack.count, 0);
                if (limit.min() != null) {
                    value = Math.max(limit.min().getInt(context, random), value);
                }
                if (limit.max() != null) {
                    value = Math.min(limit.max().getInt(context, random), value);
                }
                stack.count = value;
            }
            case LootFunction.Data data -> stack.ops.add(data.op());
        }
    }
}
