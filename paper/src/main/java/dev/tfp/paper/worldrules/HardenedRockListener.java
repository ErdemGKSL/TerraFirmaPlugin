/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFluidPlaceBlock)
 */
package dev.tfp.paper.worldrules;

import dev.tfp.core.worldrules.WorldRules;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

/**
 * Lava + water forms TFC's hardened rock ({@link WorldRules#hardenedRock}). The vanilla result is read at {@code LOW},
 * before the suppression listener ({@code HIGH}) turns cobblestone or basalt into stone, and the CraftEngine block's
 * server data replaces the event's new state at {@code HIGHEST} unless something cancelled the formation. The decision
 * is a table lookup on the event's own snapshot; the write is the event's own block change on the owning region thread.
 * Without the CraftEngine block (not loaded) the vanilla (or suppressed) result stands.
 */
final class HardenedRockListener implements Listener {
    private final Function<String, Optional<BlockData>> customData;
    /** The decision taken at LOW for the event in flight on this thread (events of one thread never interleave). */
    private final ThreadLocal<Decision> pending = new ThreadLocal<>();

    private record Decision(BlockFormEvent event, String id) {
    }

    HardenedRockListener(Function<String, Optional<BlockData>> customData) {
        this.customData = customData;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void decide(BlockFormEvent event) {
        pending.remove();
        Material at = event.getBlock().getType();
        if (at != Material.LAVA && at != Material.WATER) {
            return; // not a fluid interaction (snow, ice, concrete, ...)
        }
        WorldRules.hardenedRock(event.getNewState().getType().getKey().toString())
                .ifPresent(id -> pending.set(new Decision(event, id)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void apply(BlockFormEvent event) {
        Decision d = pending.get();
        pending.remove();
        if (d == null || d.event() != event) {
            return;
        }
        customData.apply(d.id()).ifPresent(data -> event.getNewState().setBlockData(data));
    }
}
