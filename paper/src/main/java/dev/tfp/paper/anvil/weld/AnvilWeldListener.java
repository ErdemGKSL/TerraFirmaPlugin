/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/AnvilBlock.java @ c3b4d9536 (the hammer branch of interactWithAnvil)
 */
package dev.tfp.paper.anvil.weld;

import dev.tfp.paper.anvil.AnvilDevice;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-METAL-06: a sneaking right click on an anvil with a hammer in hand welds (TFC {@code interactWithAnvil}, hammer branch).
 * A weld attempt that was made (success or a missing requirement) consumes the click; nothing to weld lets it pass. The
 * other hand's event of the same click is cancelled. Runs before the device's own sneaking click (which passes a hammer).
 */
public final class AnvilWeldListener implements Listener {
    private final AnvilWelding welding;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final Predicate<ItemStack> hammer;
    private final IntSupplier tick;
    private final Map<UUID, Integer> handled = new ConcurrentHashMap<>();

    public AnvilWeldListener(AnvilWelding welding, CustomBlocks blocks, Scheduler scheduler, Predicate<ItemStack> hammer, IntSupplier tick) {
        this.welding = Objects.requireNonNull(welding, "welding");
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.hammer = Objects.requireNonNull(hammer, "hammer");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        int now = tick.getAsInt();
        Integer last = handled.get(player.getUniqueId());
        if (last != null && last == now) {
            event.setCancelled(true); // the other hand of a click already used here
            return;
        }
        if (!player.isSneaking() || event.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        ItemStack held = event.getItem();
        if (held == null || held.isEmpty() || !hammer.test(held)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            return;
        }
        boolean anvil = blocks.stateAt(block).map(BlockStateRef::id)
            .filter(id -> id.startsWith(AnvilDevice.METAL_ID + "/") || id.startsWith(AnvilDevice.ROCK_ID + "/")).isPresent();
        if (!anvil || welding.attempt(player, block) == Welder.Result.PASS) {
            return;
        }
        handled.put(player.getUniqueId(), now);
        event.setCancelled(true);
    }

    /** Forgets the player's last click (quit). */
    public void forget(UUID player) {
        handled.remove(player);
    }
}
