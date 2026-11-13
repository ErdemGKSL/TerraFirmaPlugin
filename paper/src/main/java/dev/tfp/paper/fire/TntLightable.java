/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onFireStart, the TntBlock branch)
 */
package dev.tfp.paper.fire;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.TNTPrimeEvent;

/**
 * TFC's TNT branch of {@code onFireStart}: any start, weak or strong, primes the TNT ({@code tnt.onCaughtFire}) and
 * removes the block. A {@link TNTPrimeEvent} (cause PLAYER, or FIRE without a player) lets protection plugins refuse it.
 */
public final class TntLightable implements FireLightable {
    @Override
    public boolean light(FireStartContext ctx) {
        Block block = ctx.block();
        if (block.getType() != Material.TNT) {
            return false;
        }
        TNTPrimeEvent prime = new TNTPrimeEvent(block,
            ctx.player() != null ? TNTPrimeEvent.PrimeCause.PLAYER : TNTPrimeEvent.PrimeCause.FIRE, ctx.player(), null);
        if (!prime.callEvent()) {
            return true; // TFC cancels the event for TNT whatever happens to it
        }
        block.setType(Material.AIR, true);
        Location at = block.getLocation().add(0.5, 0, 0.5);
        block.getWorld().spawn(at, TNTPrimed.class, t -> {
            if (ctx.player() != null) {
                t.setSource(ctx.player());
            }
        });
        return true;
    }
}
