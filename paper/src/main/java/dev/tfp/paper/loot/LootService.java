/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-INFRA-09: TFC loot tables evaluated on Paper. Decide async, apply sync: {@link #dropBlock} runs on the block's region
 * thread at break time and only snapshots (the {@link LootContext} the caller built, plus {@link LootStackHooks#capture});
 * the table is rolled on the CPU pool ({@link #roll}, pure); the stacks are created and dropped back on the block's region.
 */
public final class LootService {
    private final Supplier<LootTables> tables;
    private final CustomItems items;
    private final Function<ItemStack, Optional<String>> itemId;
    private final BiPredicate<String, String> itemInTag;
    private final Scheduler scheduler;
    private final Supplier<LootStackHooks> hooks;
    private final Supplier<RandomGenerator> random;
    private final Logger logger;
    private final Set<String> unknownItems = ConcurrentHashMap.newKeySet();

    /**
     * @param tables the current table set (swapped atomically on load)
     * @param items CraftEngine items (creating our drops)
     * @param itemId the item id of a stack (CraftEngine id first, else the vanilla key)
     * @param itemInTag {@code (itemId, tag)} → membership, any thread ({@code Tags.is(ITEM, id, tag)})
     * @param hooks the item-data hooks, looked up at drop time
     * @param random a fresh generator per evaluation
     */
    public LootService(Supplier<LootTables> tables, CustomItems items, Function<ItemStack, Optional<String>> itemId,
            BiPredicate<String, String> itemInTag, Scheduler scheduler, Supplier<LootStackHooks> hooks,
            Supplier<RandomGenerator> random, Logger logger) {
        this.tables = Objects.requireNonNull(tables, "tables");
        this.items = Objects.requireNonNull(items, "items");
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        this.itemInTag = Objects.requireNonNull(itemInTag, "itemInTag");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.random = Objects.requireNonNull(random, "random");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public LootTables tables() {
        return tables.get();
    }

    /** Whether a TFC table drives the drops of {@code blockId} (any thread). */
    public boolean handlesBlock(String blockId) {
        return tables().forBlock(blockId).isPresent();
    }

    /** A snapshot of {@code stack} as a loot tool: item id, enchantment levels, tag test. Owner thread of the stack. */
    public LootContext.Tool tool(ItemStack stack) {
        Optional<String> id = stack == null ? Optional.empty() : itemId.apply(stack);
        if (id.isEmpty()) {
            return LootContext.Tool.EMPTY;
        }
        Map<String, Integer> enchantments = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> e : stack.getEnchantments().entrySet()) {
            enchantments.put(e.getKey().getKey().toString(), e.getValue());
        }
        String item = id.get();
        return LootContext.Tool.of(item, enchantments, tag -> itemInTag.test(item, tag));
    }

    /** Rolls {@code table} (pure, any thread). */
    public List<LootDrop> roll(LootTable table, LootContext context) {
        return LootEvaluator.evaluate(table, context, LootRandom.of(random.get()));
    }

    /**
     * Turns drops into real stacks, split to the item's max stack size, with the data ops applied. Region thread. An item
     * id that is neither a loaded CraftEngine item nor a vanilla material is logged once and skipped.
     */
    public List<ItemStack> stacks(List<LootDrop> drops, Object captured) {
        LootStackHooks h = hooks.get();
        List<ItemStack> out = new ArrayList<>();
        for (LootDrop drop : drops) {
            Optional<ItemStack> base = create(drop.itemId());
            if (base.isEmpty()) {
                if (unknownItems.add(drop.itemId())) {
                    logger.warning("Loot names an unknown item " + drop.itemId() + "; it is not dropped");
                }
                continue;
            }
            ItemStack stack = base.get();
            for (LootFunction.StackOp op : drop.ops()) {
                stack = h.apply(stack, op, captured);
            }
            int max = Math.max(1, stack.getMaxStackSize());
            int left = drop.count();
            while (left > 0) {
                int n = Math.min(left, max);
                ItemStack part = stack.clone();
                part.setAmount(n);
                out.add(part);
                left -= n;
            }
        }
        return out;
    }

    private Optional<ItemStack> create(String id) {
        if (items.exists(id)) {
            return items.create(id, 1);
        }
        Material material = Material.matchMaterial(id);
        if (material == null || !material.isItem() || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new ItemStack(material));
    }

    /**
     * Rolls {@code table} for the block at {@code block} and drops the result there. Call on the block's region thread
     * while the block still exists (the hooks capture its data); returns at once. The future completes after the drop.
     */
    public CompletableFuture<List<ItemStack>> dropBlock(Block block, LootTable table, LootContext context) {
        Object captured = hooks.get().capture(block);
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        CompletableFuture<List<ItemStack>> done = new CompletableFuture<>();
        CompletableFuture.supplyAsync(() -> roll(table, context), scheduler.cpuExecutor()).whenComplete((drops, err) -> {
            if (err != null) {
                logger.warning("Cannot roll loot table " + table.id() + ": " + err);
                done.completeExceptionally(err);
                return;
            }
            if (drops.isEmpty()) {
                done.complete(List.of());
                return;
            }
            scheduler.region(at, () -> {
                try {
                    List<ItemStack> stacks = stacks(drops, captured);
                    for (ItemStack stack : stacks) {
                        at.getWorld().dropItemNaturally(at, stack);
                    }
                    done.complete(stacks);
                } catch (RuntimeException e) {
                    done.completeExceptionally(e);
                    throw e;
                }
            });
        });
        return done;
    }

    /**
     * The {@code explosion_radius} of an explosion with Paper's drop {@code yield}: vanilla sets the yield to
     * {@code 1 / radius} when drops decay, else 1 (no radius parameter). Null when drops do not decay.
     */
    public static Float radiusFromYield(float yield) {
        return yield > 0f && yield < 1f ? 1f / yield : null;
    }
}
