/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/PowderKegExplosion.java @ c3b4d9536 (the explosion at the block's corner,
 *       BlockInteraction.DESTROY, finalizeExplosion: the explosion_proof / powderkeg_breaking_blocks filter, drops
 *       without the explosion radius, i.e. every block drops),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PowderkegBlock.java @ c3b4d9536 (onBlockExploded: an
 *       exploded keg is lit, not broken; canDropFromExplosion false)
 */
package dev.tfp.paper.powderkeg;

import dev.tfp.core.powderkeg.PowderkegRules;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * PAR-PROC-18: the powderkeg's explosion and what any explosion does to a keg.
 *
 * <ul>
 *   <li>{@link #explode}: the server's own explosion of the keg's strength at the block's corner (TFC builds its
 *       {@code PowderKegExplosion} at the integer block position), no fire. While it runs (synchronously, on the
 *       region thread) this thread is marked, so the explode event it fires is recognised as a keg's: its block list
 *       is filtered by TFC's tags ({@link PowderkegRules#breaks}) and its yield set to 1 (TFC builds the loot without
 *       the explosion radius, so every broken block drops). The server's explode event is what the collapse runner
 *       (PAR-PHYS-05) listens to, as TFC's {@code onExplosionDetonate} sees every explosion.</li>
 *   <li>Every explosion (TNT, creepers, kegs): a powderkeg in its block list is taken out of the list and lit
 *       (TFC {@code onBlockExploded}), so chains of kegs go off one fuse after another.</li>
 * </ul>
 */
public final class PowderkegBlasts implements Listener {
    /**
     * What the blasts read.
     *
     * @param isKeg       whether the block is a powderkeg (region thread)
     * @param inTag       whether the block is in a block tag (region thread)
     * @param onlyNatural TFC {@code powderKegOnlyBreaksNaturalBlocks}
     * @param lightLater  lights the exploded keg at the block, queued on its region (TFC {@code onBlockExploded})
     * @param remove      turns the keg into air without a drop of the keg (region thread)
     * @param boom        the server explosion: world, x, y, z, power (region thread)
     */
    public record Env(Predicate<Block> isKeg, BiPredicate<Block, String> inTag, boolean onlyNatural,
            Consumer<Block> lightLater, Predicate<Block> remove, Boom boom) {
        public Env {
            Objects.requireNonNull(isKeg, "isKeg");
            Objects.requireNonNull(inTag, "inTag");
            Objects.requireNonNull(lightLater, "lightLater");
            Objects.requireNonNull(remove, "remove");
            Objects.requireNonNull(boom, "boom");
        }
    }

    /** {@code World.createExplosion} at a location. */
    @FunctionalInterface
    public interface Boom {
        void explode(Location at, float power);
    }

    private final Env env;
    /** Set while a keg's explosion runs on this thread. */
    private final ThreadLocal<Boolean> active = new ThreadLocal<>();

    public PowderkegBlasts(Env env) {
        this.env = Objects.requireNonNull(env, "env");
    }

    /**
     * The keg at {@code block} becomes air and explodes with {@code strength} (TFC {@code serverTick}'s
     * {@code setBlock(AIR)} then {@code explode}). Nothing happens when the keg is gone meanwhile. Region thread.
     */
    public void explode(Block block, int strength) {
        if (!env.isKeg().test(block)) {
            return;
        }
        env.remove().test(block);
        if (strength <= 0) {
            return; // a radius-0 explosion breaks nothing (only TFC's sound and particles; the server skips it)
        }
        active.set(Boolean.TRUE);
        try {
            env.boom().explode(new Location(block.getWorld(), block.getX(), block.getY(), block.getZ()), strength);
        } finally {
            active.remove();
        }
    }

    /** True while a keg's explosion runs on this thread. */
    boolean active() {
        return Boolean.TRUE.equals(active.get());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (filter(event.blockList()) && active()) {
            event.setYield(1f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (filter(event.blockList()) && active()) {
            event.setYield(1f);
        }
    }

    /** Lights and keeps every keg in {@code blocks}; for a keg's own explosion drops what TFC's tags spare. */
    boolean filter(List<Block> blocks) {
        boolean ours = active();
        for (Iterator<Block> it = blocks.iterator(); it.hasNext();) {
            Block b = it.next();
            if (env.isKeg().test(b)) {
                it.remove();
                env.lightLater().accept(b);
            } else if (ours && !PowderkegRules.breaks(env.onlyNatural(), env.inTag().test(b, PowderkegRules.CANNOT_BREAK_TAG),
                    env.onlyNatural() && env.inTag().test(b, PowderkegRules.CAN_BREAK_TAG))) {
                it.remove();
            }
        }
        return true;
    }
}
