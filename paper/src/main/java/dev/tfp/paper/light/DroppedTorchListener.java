/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TorchItem.java @ c3b4d9536 (onEntityItemUpdate),
 *       src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (canFluidExtinguishFire)
 */
package dev.tfp.paper.light;

import dev.tfp.paper.light.DroppedTorches.Outcome;
import dev.tfp.paper.light.DroppedTorches.Place;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-HEAT-22: dropped torch items (TFC {@code TorchItem.onEntityItemUpdate}) go out in water and light the
 * {@code #tfc:lit_by_dropped_torch} blocks they lie on or in (leaves, thatch, log piles, pit kilns).
 *
 * <p>Lazy: nothing runs per tick. A torch item is evaluated when it is picked up (by a player, a mob or a hopper) and by
 * one coarse check every {@link #PERIOD_TICKS} ticks while it exists ({@link Tracker}, the entity's own scheduler). Each
 * evaluation replays the ticks since the last one with {@link DroppedTorches#replay}. Where the item is now is assumed for
 * the whole span only when the previous evaluation saw the same place; an item that just arrived there replays one tick
 * (the arrival), so a torch thrown into water is not charged for the ticks it spent in the air. The entity keeps how far
 * it was evaluated, and where, in its PDC.
 *
 * <p>Results are applied on the item's owner thread: the stack shrinks or the item is removed; the burnt-out loot, the
 * extinguish sound and the strong fire go through {@link Effects}. No game-mode rule: an item entity has no game mode,
 * and TFC does the same for a torch dropped by a player in any mode.
 */
public final class DroppedTorchListener implements Listener {
    /** Entity PDC: the ticks-lived count up to which the item was evaluated. */
    public static final NamespacedKey CHECKED = new NamespacedKey("tfp", "dropped_torch_checked");
    /** Entity PDC: where it was at that evaluation ({@code PLACE@x,y,z}). */
    public static final NamespacedKey WHERE = new NamespacedKey("tfp", "dropped_torch_where");
    /** Server ticks between the coarse checks of one dropped torch item. */
    public static final long PERIOD_TICKS = 10;
    /** TFC {@code #tfc:lit_by_dropped_torch}. */
    public static final String LIT_BY_DROPPED_TORCH = "tfc:lit_by_dropped_torch";

    /** What an evaluation asks of the world. Owner thread of the item. */
    public interface Effects {
        /** Rolls the torch's loot table {@code count} times with {@code burnt_out} set and drops the result at {@code at}. */
        void burntOut(Location at, int count);

        /** TFC {@code Helpers.playSound(level, pos, FIRE_EXTINGUISH)}. */
        void extinguishSound(Location at);

        /** TFC {@code StartFireEvent.startFire(level, pos, state, UP, null, EMPTY, STRONG, -1)}. */
        void strongFire(Block block);
    }

    /** Starts the coarse periodic check of a dropped torch item (once per entity). */
    @FunctionalInterface
    public interface Tracker {
        void track(Item item);
    }

    private final Predicate<ItemStack> torch;
    private final Predicate<Block> litByDroppedTorch;
    private final Effects effects;
    private final Tracker tracker;

    /**
     * @param torch             whether a stack is the lit torch item ({@code tfp:torch})
     * @param litByDroppedTorch whether a block is in {@code #tfc:lit_by_dropped_torch}
     */
    public DroppedTorchListener(Predicate<ItemStack> torch, Predicate<Block> litByDroppedTorch, Effects effects,
            Tracker tracker) {
        this.torch = Objects.requireNonNull(torch, "torch");
        this.litByDroppedTorch = Objects.requireNonNull(litByDroppedTorch, "litByDroppedTorch");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        if (isTorch(event.getEntity())) {
            tracker.track(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (e instanceof Item item && isTorch(item)) {
                tracker.track(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (evaluate(event.getItem(), ThreadLocalRandom.current()).filter(Outcome::removed).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (evaluate(event.getItem(), ThreadLocalRandom.current()).filter(Outcome::removed).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Whether {@code item} is a dropped lit torch. */
    public boolean isTorch(Item item) {
        ItemStack stack = item.getItemStack();
        return stack.getAmount() > 0 && torch.test(stack);
    }

    /**
     * Replays the item's ticks since its last evaluation and applies the result. Owner thread of the item.
     *
     * @return the replay, or empty when nothing was replayed (not a torch, removed, no tick elapsed)
     */
    public Optional<Outcome> evaluate(Item item, RandomGenerator random) {
        if (!item.isValid() || !isTorch(item)) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        int lived = item.getTicksLived();
        Integer checked = pdc.get(CHECKED, PersistentDataType.INTEGER);
        if (checked != null && lived <= checked) {
            return Optional.empty();
        }
        Location loc = item.getLocation();
        Block at = loc.getBlock();
        Place place;
        Block target;
        if (extinguishesFire(at.getBlockData())) {
            place = Place.FLUID;
            target = at;
        } else {
            boolean empty = at.getType().isAir(); // TFC level.isEmptyBlock(pos)
            target = empty ? at.getRelative(0, -1, 0) : at;
            place = litByDroppedTorch.test(target) ? (empty ? Place.LIT_BELOW : Place.LIT_INSIDE) : Place.OTHER;
        }
        String where = place + "@" + target.getX() + "," + target.getY() + "," + target.getZ();
        boolean same = checked != null && where.equals(pdc.get(WHERE, PersistentDataType.STRING));
        pdc.set(CHECKED, PersistentDataType.INTEGER, lived);
        pdc.set(WHERE, PersistentDataType.STRING, where);
        if (place == Place.OTHER) {
            return Optional.empty();
        }
        int from = same ? checked : lived - 1;
        ItemStack stack = item.getItemStack();
        Outcome out = DroppedTorches.replay(stack.getAmount(), place, from, lived, random);
        apply(item, stack, loc, target, out);
        return Optional.of(out);
    }

    private void apply(Item item, ItemStack stack, Location loc, Block target, Outcome out) {
        if (out.lit()) {
            effects.strongFire(target);
            item.remove(); // TFC itemEntity.kill()
            return;
        }
        if (out.extinguished() == 0) {
            return;
        }
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        effects.burntOut(center, out.extinguished());
        effects.extinguishSound(loc);
        if (out.left() == 0) {
            item.remove(); // TFC itemEntity.discard()
        } else {
            stack.setAmount(out.left());
            item.setItemStack(stack);
        }
    }

    /**
     * TFC {@code FluidHelpers.canFluidExtinguishFire(stateAt.getFluidState().getType())}: any fluid below 400 K, which on
     * a vanilla server is water (source, flowing, bubble columns, the always-waterlogged plants and any waterlogged
     * block). Lava is not.
     */
    public static boolean extinguishesFire(BlockData data) {
        Material type = data.getMaterial();
        return type == Material.WATER || type == Material.BUBBLE_COLUMN || type == Material.KELP
            || type == Material.KELP_PLANT || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS
            || (data instanceof Waterlogged w && w.isWaterlogged());
    }
}
