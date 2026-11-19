/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (slowEntityInsideBlocks, slowEntityInBlock)
 */
package dev.tfp.paper.leaves;

import dev.tfp.core.leaves.LeafRules;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;

/**
 * PAR-GATHER-04: the movement slowdown of leaves, fallen leaves and (through {@link SlowBlocks}) plants. TFC multiplies the
 * entity's velocity each tick by the slowest factor of the blocks its box touches; a vanilla client predicts its own movement,
 * so a server-side velocity change would rubber-band. Instead the slowest factor becomes a transient multiplier on the
 * player's movement speed attribute, which the client applies itself (ledger divergence); in the air TFC's gentler
 * {@code 0.25 (f + 3)} is used. Evaluated only when the player enters another block or leaves the ground, so a walk costs a
 * few block reads per step. Spectators and flying players are not slowed.
 */
public final class SlowdownListener implements Listener {
    /** Sets the player's slowdown multiplier ({@code 1} removes it). */
    @FunctionalInterface
    public interface Effect {
        void apply(Player player, double multiplier);
    }

    private final CustomBlocks blocks;
    private final SlowBlocks slow;
    private final Effect effect;

    public SlowdownListener(CustomBlocks blocks, SlowBlocks slow, Effect effect) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.slow = Objects.requireNonNull(slow, "slow");
        this.effect = Objects.requireNonNull(effect, "effect");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Player player = event.getPlayer();
        boolean ground = player.isOnGround();
        if (to == null || (!event.hasChangedBlock() && !groundChanged(player, ground))) {
            return;
        }
        update(player, to, ground);
    }

    private final java.util.Map<java.util.UUID, Boolean> lastGround = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean groundChanged(Player player, boolean ground) {
        Boolean before = lastGround.put(player.getUniqueId(), ground);
        return before == null || before != ground;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastGround.remove(event.getPlayer().getUniqueId());
    }

    /** Recomputes and applies the multiplier for a player standing at {@code at}. Region thread of the player. */
    void update(Player player, Location at, boolean ground) {
        lastGround.put(player.getUniqueId(), ground);
        if (player.getGameMode() == GameMode.SPECTATOR || player.isFlying()) {
            effect.apply(player, 1.0);
            return;
        }
        double half = player.getWidth() / 2;
        float factor = factorAt(at.getWorld(), new BoundingBox(at.getX() - half, at.getY(), at.getZ() - half, at.getX() + half,
                at.getY() + player.getHeight(), at.getZ() + half));
        effect.apply(player, factor < LeafRules.NO_SLOW ? LeafRules.horizontalFactor(factor, ground) : 1.0);
    }

    /** The slowest factor among the blocks the box touches ({@code BlockPos.containing(min + 1e-7)} to {@code max - 1e-7}). */
    float factorAt(World world, BoundingBox box) {
        int x0 = (int) Math.floor(box.getMinX() + 1.0E-7);
        int y0 = (int) Math.floor(box.getMinY() + 1.0E-7);
        int z0 = (int) Math.floor(box.getMinZ() + 1.0E-7);
        int x1 = (int) Math.floor(box.getMaxX() - 1.0E-7);
        int y1 = (int) Math.floor(box.getMaxY() - 1.0E-7);
        int z1 = (int) Math.floor(box.getMaxZ() - 1.0E-7);
        float factor = LeafRules.NO_SLOW;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4) || !Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        continue;
                    }
                    Optional<BlockStateRef> state = blocks.stateAt(block);
                    if (state.isPresent()) {
                        factor = Math.min(factor, slow.factor(state.get()));
                    }
                }
            }
        }
        return factor;
    }
}
