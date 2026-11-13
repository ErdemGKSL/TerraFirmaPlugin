/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536
 */
package dev.tfp.paper.heat;

import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.heat.HeatState;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * PAR-HEAT-03: hot item entities cool in water and snow and on snow blocks and ice (TFC
 * {@code ForgeEventHandler.onItemExpire}). Like TFC they despawn at the normal lifespan even while still hot (TFC stops
 * extending once {@code entity.lifespan >= lifespan}); only an item that reached 0 °C before that age gets one normal
 * lifespan from the check at which it cooled ({@code setExtraLife(lifespan)}).
 *
 * <p>Lazy: nothing ticks. When an item entity is touched (picked up by a player or mob, pulled by a hopper, about to
 * despawn), the checks TFC would have run since the last evaluation are replayed by {@link HotItemCooling} from a
 * snapshot of the two blocks it reads, the stack's heat record is rewritten, and the melt or evaporation of the surface
 * is handed to the owning region (re-validated there). The entity keeps how far it was evaluated in its PDC, so a span is
 * never replayed twice. One fizz sound and one smoke particle mark the evaluation that cooled the item (TFC plays them on
 * every check).
 *
 * <p>Held hot items do not burn the player: TFC has no such rule (no tongs, no burn in ForgeEventHandler or the player
 * tick at the pinned commit), so neither do we.
 */
public final class HotItemListener implements Listener {
    /** Entity PDC: the entity's ticks-lived count up to which its checks were replayed. */
    public static final NamespacedKey CHECKED = new NamespacedKey("tfp", "hot_item_checked");
    /** Entity PDC: the ticks-lived count at which the item reached 0 °C (TFC: its normal lifespan starts then). */
    public static final NamespacedKey COLD_AT = new NamespacedKey("tfp", "hot_item_cold_at");
    /** TFC {@code getEntityLifespan}: the vanilla item lifespan a cold item gets after it cooled. */
    public static final int LIFESPAN = 6000;

    private final LongSupplier now;
    private final HeatConfig config;
    private final ItemHeatHooks.Capacities capacities;
    private final Scheduler scheduler;

    /**
     * @param now        the calendar tick every heat record is stamped with
     * @param capacities the heat capacity of a stack (0 or less: cannot be heated)
     * @param scheduler  where block changes are applied (region of the block); null applies them inline (tests)
     */
    public HotItemListener(LongSupplier now, HeatConfig config, ItemHeatHooks.Capacities capacities,
            Scheduler scheduler) {
        this.now = Objects.requireNonNull(now, "now");
        this.config = Objects.requireNonNull(config, "config");
        this.capacities = Objects.requireNonNull(capacities, "capacities");
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        evaluate(event.getItem(), ThreadLocalRandom.current());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        evaluate(event.getItem(), ThreadLocalRandom.current());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (keepAlive(event.getEntity(), ThreadLocalRandom.current())) {
            event.setCancelled(true); // the server resets the item's age: another full lifespan before the next check
        }
    }

    /**
     * Evaluates the item and answers whether it must not despawn yet: it reached 0 °C at an age below {@link #LIFESPAN}
     * and less than {@link #LIFESPAN} ticks ago (TFC: {@code setExtraLife(lifespan)} once cold). An item that is still hot
     * at the despawn event despawns, as in TFC, whose {@code onItemExpire} returns early once
     * {@code entity.lifespan >= lifespan} (the 10-tick extensions add up to the normal lifespan at age 6000).
     */
    public boolean keepAlive(Item item, RandomGenerator random) {
        if (!HotItemCooling.COOL_HOT_ITEM_ENTITIES) {
            return false;
        }
        evaluate(item, random);
        Integer coldAt = item.getPersistentDataContainer().get(COLD_AT, PersistentDataType.INTEGER);
        return coldAt != null && coldAt < LIFESPAN && item.getTicksLived() - coldAt < LIFESPAN;
    }

    /**
     * Replays the cooling checks of {@code item} since its last evaluation and writes the result into its stack.
     * Must run on the item's owner thread.
     *
     * @return the replay, or empty when nothing was replayed (not a heated item, cold, or no whole check elapsed)
     */
    public Optional<HotItemCooling.Outcome> evaluate(Item item, RandomGenerator random) {
        if (!HotItemCooling.COOL_HOT_ITEM_ENTITIES) {
            return Optional.empty();
        }
        ItemStack stack = item.getItemStack();
        Optional<HeatState> record = HeatPdc.read(stack);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        float capacity = capacities.of(stack);
        if (!(capacity > 0f)) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        int lived = item.getTicksLived();
        int checked = Math.min(lived, pdc.getOrDefault(CHECKED, PersistentDataType.INTEGER, 0));
        int span = lived - checked;
        if (span < HotItemCooling.TICKS_BEFORE_ITEM_COOL) {
            return Optional.empty();
        }
        long tick = now.getAsLong();
        long spanStart = tick - span;
        float start = record.get().temperatureAt(spanStart, capacity, config);
        if (!(start > 0f)) {
            pdc.set(CHECKED, PersistentDataType.INTEGER, lived);
            return Optional.empty();
        }
        Location loc = item.getLocation();
        Block block = loc.getBlock();
        HotItemCooling.Column column = column(block.getBlockData(), block.getRelative(0, -1, 0).getType());
        HotItemCooling.Outcome out = HotItemCooling.replay(start, capacity, span, column, config, random);
        if (out.checks() == 0) {
            return Optional.empty();
        }
        long stamp = Math.max(record.get().lastTick(), spanStart + out.ticksConsumed());
        HeatPdc.write(stack, new HeatState(out.temperature(), stamp));
        item.setItemStack(stack);
        pdc.set(CHECKED, PersistentDataType.INTEGER, checked + (int) out.ticksConsumed());
        if (out.zeroAtCheck() > 0) {
            pdc.set(COLD_AT, PersistentDataType.INTEGER,
                    checked + (int) (out.zeroAtCheck() * HotItemCooling.TICKS_BEFORE_ITEM_COOL));
        }
        if (!out.changes().isEmpty()) {
            applyLater(block.getWorld(), block.getX(), block.getY(), block.getZ(), out.changes());
        }
        if (out.surfaceCooled()) {
            World world = loc.getWorld();
            // TFC Helpers.playSound(level, pos, ITEM_COOL): random/fizz, BLOCKS, volume 1 + r, pitch r + 0.7 + 0.3
            world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1f + random.nextFloat(),
                    random.nextFloat() + 0.7f + 0.3f);
            world.spawnParticle(Particle.SMOKE, loc.getX(), loc.getY(), loc.getZ(), 1, 0d, 0d, 0d, 1d);
        }
        return Optional.of(out);
    }

    /** The rule's view of the item's block and the block below it. */
    public static HotItemCooling.Column column(BlockData at, Material below) {
        Material type = at.getMaterial();
        HotItemCooling.At a;
        int layers = 0;
        if (type == Material.WATER) {
            a = HotItemCooling.At.WATER_BLOCK;
        } else if (type == Material.BUBBLE_COLUMN || type == Material.KELP || type == Material.KELP_PLANT
                || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS
                || (at instanceof Waterlogged w && w.isWaterlogged())) {
            a = HotItemCooling.At.WATERLOGGED;
        } else if (type == Material.SNOW) {
            a = HotItemCooling.At.SNOW;
            layers = at instanceof Snow s ? s.getLayers() : 1;
        } else {
            a = HotItemCooling.At.OTHER;
        }
        HotItemCooling.Below b = switch (below) {
            case SNOW_BLOCK -> HotItemCooling.Below.SNOW_BLOCK;
            case ICE, FROSTED_ICE -> HotItemCooling.Below.ICE;
            case BLUE_ICE -> HotItemCooling.Below.SEA_ICE;
            case PACKED_ICE -> HotItemCooling.Below.PACKED_ICE;
            default -> HotItemCooling.Below.OTHER;
        };
        return new HotItemCooling.Column(a, layers, b);
    }

    private void applyLater(World world, int x, int y, int z, List<HotItemCooling.Change> changes) {
        Runnable apply = () -> apply(world, x, y, z, changes);
        if (scheduler == null) {
            apply.run();
        } else {
            scheduler.region(world, x >> 4, z >> 4, apply);
        }
    }

    /** Applies the changes where the live block still is what the replay started from (region thread). */
    static void apply(World world, int x, int y, int z, List<HotItemCooling.Change> changes) {
        for (HotItemCooling.Change c : changes) {
            Block block = world.getBlockAt(x, y + c.dy(), z);
            if (!matches(block.getType(), c.expected())) {
                continue;
            }
            switch (c.result()) {
                case AIR -> block.setType(Material.AIR);
                case WATER -> block.setType(Material.WATER);
                case SNOW -> {
                    BlockData data = Material.SNOW.createBlockData();
                    if (data instanceof Snow s) {
                        s.setLayers(Math.max(s.getMinimumLayers(), Math.min(s.getMaximumLayers(), c.layers())));
                    }
                    block.setBlockData(data);
                }
            }
        }
    }

    private static boolean matches(Material type, HotItemCooling.Expect expected) {
        return switch (expected) {
            case WATER_BLOCK -> type == Material.WATER;
            case SNOW -> type == Material.SNOW;
            case SNOW_BLOCK -> type == Material.SNOW_BLOCK;
            case ICE -> type == Material.ICE || type == Material.FROSTED_ICE;
            case SEA_ICE -> type == Material.BLUE_ICE;
            case PACKED_ICE -> type == Material.PACKED_ICE;
        };
    }
}
