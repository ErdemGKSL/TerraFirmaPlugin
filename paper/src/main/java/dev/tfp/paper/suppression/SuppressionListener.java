/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.suppression;

import dev.tfp.core.visuals.Suppression;
import dev.tfp.core.visuals.Suppression.Path;
import dev.tfp.core.visuals.Suppression.Verdict;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

/**
 * M1-INST-04: keeps repurposed vanilla blocks from being created in TFC worlds (ARCHITECTURE.md §9.1).
 * Thin wrapper: each handler turns the event into a {@link Path} + states and applies the
 * {@link Suppression} verdict. Covered paths: lava + water and other block formation, concrete powder
 * hardening, copper oxidation, scraping/waxing and other entity changes, coral death, spreading, bone meal,
 * placement (incl. a wet sponge drying in an ultrawarm world), drops and generated loot, crafting and
 * smelting results. <b>Best effort:</b> dispensers (any repurposed block item is not dispensed) and
 * {@code /setblock}/{@code /fill} typed by a player or the console; other commands, functions, structure
 * blocks and plugins placing blocks are not seen.
 *
 * <p>Events run on the region thread that owns the block (Folia) or the main thread; handlers only read the
 * event and the immutable rule set, so they are thread-safe.
 */
public final class SuppressionListener implements Listener {
    private final Suppression rules;
    private final Predicate<World> tfcWorld;
    private final Logger log;
    private final AtomicLong suppressed = new AtomicLong();

    /**
     * @param tfcWorld which worlds are TFC worlds (suppression applies only there)
     */
    public SuppressionListener(Suppression rules, Predicate<World> tfcWorld, Logger log) {
        this.rules = rules;
        this.tfcWorld = tfcWorld;
        this.log = log;
    }

    /** Events changed or cancelled so far. */
    public long suppressedCount() {
        return suppressed.get();
    }

    // ---- block creation -----------------------------------------------------------------------------

    /** Lava + water, concrete powder, copper oxidation, mud → clay, ... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (!tfcWorld.test(event.getBlock().getWorld())) {
            return;
        }
        String from = event.getBlock().getBlockData().getAsString();
        String to = event.getNewState().getBlockData().getAsString();
        apply(rules.decide(Suppression.classifyForm(from, to), from, to), event, event.getNewState());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (tfcWorld.test(event.getBlock().getWorld())) {
            apply(rules.decide(Path.SPREAD, event.getSource().getBlockData().getAsString(),
                    event.getNewState().getBlockData().getAsString()), event, event.getNewState());
        }
    }

    /** Coral death. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (tfcWorld.test(event.getBlock().getWorld())) {
            apply(rules.decide(Path.FADE, event.getBlock().getBlockData().getAsString(),
                    event.getNewState().getBlockData().getAsString()), event, event.getNewState());
        }
    }

    /** Bone meal (moss): drops only the repurposed blocks from the growth. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (!tfcWorld.test(event.getBlock().getWorld())) {
            return;
        }
        for (Iterator<BlockState> it = event.getBlocks().iterator(); it.hasNext();) {
            BlockState s = it.next();
            if (rules.decide(Path.FERTILIZE, null, s.getBlockData().getAsString()) != Verdict.ALLOW) {
                it.remove();
                suppressed.incrementAndGet();
            }
        }
    }

    /** Axe scraping, honeycomb waxing, endermen, falling blocks. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (tfcWorld.test(event.getBlock().getWorld())) {
            apply(rules.decide(Path.ENTITY_CHANGE, event.getBlock().getBlockData().getAsString(),
                    event.getBlockData().getAsString()), event, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        World world = event.getBlock().getWorld();
        if (!tfcWorld.test(world)) {
            return;
        }
        BlockData placed = event.getBlockPlaced().getBlockData();
        // A wet sponge dries into a sponge at once in an ultrawarm dimension (no event of its own). The
        // ultrawarm flag is deprecated API; the Nether environment is the vanilla ultrawarm dimension.
        String to = placed.getMaterial() == Material.WET_SPONGE && world.getEnvironment() == World.Environment.NETHER
                ? "minecraft:sponge" : placed.getAsString();
        apply(rules.decide(Path.PLACE, event.getBlockReplacedState().getBlockData().getAsString(), to), event, null);
    }

    /** Best effort: a dispenser never ejects or places a repurposed block item. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (tfcWorld.test(event.getBlock().getWorld()) && repurposed(event.getItem())) {
            apply(rules.decide(Path.DISPENSE, null, id(event.getItem())), event, null);
        }
    }

    // ---- items that would place them: drops, loot, crafting, smelting -------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (tfcWorld.test(event.getBlock().getWorld())) {
            for (Iterator<Item> it = event.getItems().iterator(); it.hasNext();) {
                if (repurposed(it.next().getItemStack())) {
                    it.remove();
                    suppressed.incrementAndGet();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (tfcWorld.test(event.getWorld()) && event.getLoot().removeIf(this::repurposed)) {
            suppressed.incrementAndGet();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && repurposed(result) && event.getView().getPlayer() instanceof Player p
                && tfcWorld.test(p.getWorld())) {
            event.getInventory().setResult(null);
            suppressed.incrementAndGet();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (tfcWorld.test(event.getBlock().getWorld()) && repurposed(event.getResult())) {
            event.setCancelled(true);
            suppressed.incrementAndGet();
        }
    }

    // ---- commands (best effort) ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (tfcWorld.test(event.getPlayer().getWorld())) {
            refuse(event.getMessage(), event, msg -> event.getPlayer().sendMessage(msg));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        if (Bukkit.getWorlds().stream().anyMatch(tfcWorld)) {
            refuse(event.getCommand(), event, msg -> event.getSender().sendMessage(msg));
        }
    }

    private void refuse(String command, Cancellable event, java.util.function.Consumer<String> reply) {
        Optional<String> block = rules.commandPlaces(command);
        if (block.isPresent()) {
            event.setCancelled(true);
            suppressed.incrementAndGet();
            reply.accept(block.get() + " is used for TerraFirmaPlugin visuals and cannot be placed in TFC worlds.");
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private boolean repurposed(ItemStack stack) {
        return stack != null && stack.getType().isBlock() && rules.suppressedItem(id(stack));
    }

    private static String id(ItemStack stack) {
        return stack.getType().getKey().toString();
    }

    private void apply(Verdict v, Cancellable event, BlockState newState) {
        switch (v) {
            case Verdict.Allow a -> {
            }
            case Verdict.Cancel c -> {
                event.setCancelled(true);
                suppressed.incrementAndGet();
            }
            case Verdict.Replace r -> {
                Optional<BlockData> data = blockData(r.state());
                if (newState != null && data.isPresent()) {
                    newState.setBlockData(data.get());
                } else {
                    event.setCancelled(true);
                }
                suppressed.incrementAndGet();
            }
        }
    }

    private Optional<BlockData> blockData(String state) {
        try {
            return Optional.of(Bukkit.createBlockData(state));
        } catch (IllegalArgumentException e) {
            Material m = Material.matchMaterial(state);
            if (m != null && m.isBlock()) {
                return Optional.of(m.createBlockData());
            }
            log.warning("Suppression: unknown replacement block " + state + "; cancelling instead");
            return Optional.empty();
        }
    }
}
