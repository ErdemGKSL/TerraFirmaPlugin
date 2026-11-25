/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/CrucibleBlock.java @ c3b4d9536 (the dropped item keeps the
 *       block entity's data, which the item tooltip reads)
 */
package dev.tfp.paper.crucible;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.paper.alloy.AlloyPdc;
import dev.tfp.paper.integration.CustomItems;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-23: a broken crucible keeps its record on the dropped item (the device framework's SAVE removal); this also
 * writes the alloy to the item as {@code tfp:alloy} ({@link AlloyPdc}) so the held crucible's tooltip lists its contents. The
 * device announces the alloy when it is removed; the item that spawns at the block within the window gets it. Region thread of
 * the block and the item.
 */
public final class CrucibleDrops implements Listener {
    private record Key(UUID world, int x, int y, int z) {
    }

    private record Pending(FluidAlloy alloy, long expiresNanos) {
    }

    /** How long a removed crucible waits for its dropped item (the same tick, in practice). */
    static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final Map<Key, Pending> PENDING = new ConcurrentHashMap<>();

    private final CustomItems items;

    public CrucibleDrops(CustomItems items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    /** The crucible at {@code block} was removed holding {@code alloy}. */
    static void expect(Block block, FluidAlloy alloy) {
        long now = System.nanoTime();
        PENDING.values().removeIf(p -> now - p.expiresNanos() > 0);
        if (!alloy.isEmpty()) {
            PENDING.put(key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()),
                new Pending(alloy, now + WINDOW_NANOS));
        }
    }

    private static Key key(UUID world, int x, int y, int z) {
        return new Key(world, x, y, z);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpawn(ItemSpawnEvent e) {
        if (PENDING.isEmpty() || !(e.getEntity() instanceof Item entity)) {
            return;
        }
        Location l = entity.getLocation();
        Key key = key(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        Pending p = PENDING.get(key);
        if (p == null) {
            return;
        }
        ItemStack stack = entity.getItemStack();
        if (!items.idOf(stack).filter(CrucibleDevice.ID::equals).isPresent() || !PENDING.remove(key, p)) {
            return;
        }
        if (System.nanoTime() - p.expiresNanos() <= 0 && AlloyPdc.write(stack, p.alloy())) {
            entity.setItemStack(stack);
        }
    }
}
