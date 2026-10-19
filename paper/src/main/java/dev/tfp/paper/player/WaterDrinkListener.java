/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onPlayerRightClickBlockLowestPriority,
 * onPlayerRightClickEmpty)
 */
package dev.tfp.paper.player;

import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/**
 * Right-click on a water source with an empty main hand drinks (M3-SURV-03). TFC drinks from the fluid the player
 * looks at (the source-only ray, reach of 4.5 blocks); a click that interacts with a block does not drink.
 *
 * <p>Only the vanilla water source is checked: {@code Material.WATER} at level 0. Salt water and river water have no
 * block here yet. The work is on the player's thread: the thirst clock is evaluated, then the drink is applied, both
 * through {@link PlayerDataStore#update}.
 */
public final class WaterDrinkListener implements Listener {
    /** TFC's hand reach for the source ray (the vanilla survival block reach). */
    static final double REACH = 4.5;

    private final PlayerDataStore data;
    private final LongSupplier playerTicks;
    private final HudSnapshotCache hud;

    /**
     * @param playerTicks the calendar's player tick ({@code ClockSnapshot.playerTicks}), any thread
     * @param hud          the ambient temperature for the heat share of the decay
     */
    public WaterDrinkListener(PlayerDataStore data, LongSupplier playerTicks, HudSnapshotCache hud) {
        this.data = data;
        this.playerTicks = playerTicks;
        this.hud = hud;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!EquipmentSlot.HAND.equals(event.getHand())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        if (held != null && !held.getType().isAir()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        // TFC: a click that interacts with a block (a chest, an anvil) is not a drink. Only air or the water itself
        // can be the clicked block of a drink.
        if (clicked != null && !clicked.getType().isAir() && !isPlainWaterSource(clicked)) {
            return;
        }
        Player player = event.getPlayer();
        ThreadAsserts.owned(player);
        UUID id = player.getUniqueId();
        if (!data.isLoaded(id)) {
            return;
        }
        Block source = sourceInView(player);
        if (source == null) {
            return;
        }
        if (!isPlainWaterSource(source)) {
            return; // not water: nothing changes, and no decay is written
        }
        long now = playerTicks.getAsLong();
        float temperature = (float) hud.snapshot(id).ambientTemperature();
        data.update(id, d -> WaterDrink.drink(d, now, temperature, true));
    }

    /** The first block the player looks at, counting only fluid sources, within {@link #REACH}; or null. */
    private static Block sourceInView(Player player) {
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), REACH,
                FluidCollisionMode.SOURCE_ONLY, false);
        return hit == null ? null : hit.getHitBlock();
    }

    /** A vanilla water block that is a source (level 0). Waterlogged blocks are another material and do not count. */
    static boolean isPlainWaterSource(Block block) {
        return block.getType() == Material.WATER && block.getBlockData() instanceof Levelled levelled
                && levelled.getLevel() == 0;
    }
}
