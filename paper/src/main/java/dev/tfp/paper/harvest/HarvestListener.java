/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/loot_table/blocks/plant/*.json @ c3b4d9536
 */
package dev.tfp.paper.harvest;

import dev.tfp.core.gather.GatherYield;
import dev.tfp.core.gather.HarvestRules;
import dev.tfp.core.gather.HarvestTable;
import dev.tfp.core.gather.HarvestTables;
import dev.tfp.core.gather.HeldTool;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-GATHER-02: breaking a TFC plant, leaf block, twig, fallen leaves or the groundcover stick yields TFC's loot table
 * ({@link HarvestRules}) for the held tool: sharp tools and shears cut plants (straw from grass, ...), silk touch keeps
 * leaves, and nothing drops in creative. The pure table is evaluated inline on the block's region thread (a handful of
 * conditions, no scan). Blocks another loot runtime already drives ({@code handledElsewhere}, the generic loot tables of
 * PAR-INFRA-09) are skipped so a block never drops twice.
 */
public final class HarvestListener implements Listener {
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Function<ItemStack, HeldTool> tools;
    private final Predicate<String> handledElsewhere;
    private final DoubleSupplier rolls;

    public HarvestListener(CustomBlocks blocks, CustomItems items, Function<ItemStack, HeldTool> tools,
            Predicate<String> handledElsewhere, DoubleSupplier rolls) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.items = Objects.requireNonNull(items, "items");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.handledElsewhere = Objects.requireNonNull(handledElsewhere, "handledElsewhere");
        this.rolls = Objects.requireNonNull(rolls, "rolls");
    }

    /** The harvest table of one of our block ids ({@code tfp:} maps to TFC's {@code tfc:}). */
    static Optional<HarvestTable> tableOf(String ourId) {
        return ourId.startsWith("tfp:") ? HarvestTables.find("tfc:" + ourId.substring(4)) : Optional.empty();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || handledElsewhere.test(state.get().id())) {
            return;
        }
        Optional<HarvestTable> table = tableOf(state.get().id());
        if (table.isEmpty()) {
            return;
        }
        // we own the drops of this block: the vanilla/CraftEngine ones are never wanted
        event.setDropItems(false);
        boolean creative = event.getPlayer().getGameMode() == GameMode.CREATIVE;
        HeldTool tool = tools.apply(event.getPlayer().getInventory().getItemInMainHand());
        List<GatherYield> drops = HarvestRules.breakDrops(table.get(), tool, state.get().properties(), rolls, creative);
        for (GatherYield y : drops) {
            ItemStack stack = stackOf(y);
            if (stack != null) {
                block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), stack);
            }
        }
    }

    private ItemStack stackOf(GatherYield y) {
        String id = y.itemId().replaceFirst("^tfc:", "tfp:");
        if (id.startsWith("minecraft:")) {
            Material m = Material.matchMaterial(id);
            return m == null ? null : new ItemStack(m, y.count());
        }
        return items.create(id, y.count()).orElse(null);
    }
}
