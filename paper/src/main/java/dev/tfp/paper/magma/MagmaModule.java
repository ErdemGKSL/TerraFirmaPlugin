/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.magma;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.module.FeatureModule;
import dev.tfp.paper.module.ModuleContext;
import dev.tfp.paper.pipeline.CommitBudget;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * PAR-WORLD-45: the hot floor of TFC's per-rock magma blocks. The CraftEngine block's server state is not vanilla magma, so
 * vanilla's {@code stepOn} never fires: every {@link #PERIOD} ticks (vanilla's damage cadence for the same hit, the 10-tick
 * invulnerability window) the player's own thread reads the block under the player and applies the rule of {@link MagmaRules}.
 *
 * <p>Threads: the global scheduler hands each player one {@link #tick} on the player's thread, which owns the blocks around
 * the player.
 */
public final class MagmaModule implements FeatureModule {
    public static final String ID = "magma";
    /** Ticks between two checks. */
    public static final long PERIOD = 10;
    /** Vanilla's {@code getBlockPosBelowThatAffectsMyMovement} offset. */
    private static final double BELOW = 0.500001;

    private volatile Scheduler scheduler;
    private volatile CustomBlocks blocks;
    private volatile Scheduler.TaskHandle task;
    /** PAR-WORLD-54: the drag-down bubble columns above the magma blocks. */
    private volatile MagmaColumnKeeper columns;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(ModuleContext context) {
        scheduler = context.scheduler();
        blocks = context.integrations().customBlocks();
        columns = new MagmaColumnKeeper(context::regions, blocks, scheduler.cpuExecutor(),
                new CommitBudget(MagmaColumnKeeper.BUDGET_NANOS, System::nanoTime));
        context.listen(columns);
        task = scheduler.globalRepeating(PERIOD, PERIOD, this::tickAll);
    }

    @Override
    public void disable() {
        final Scheduler.TaskHandle t = task;
        if (t != null) {
            t.cancel();
        }
    }

    /** Global thread: hands each player's check to the player's thread. */
    private void tickAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.entity(player, () -> tick(player), null);
        }
    }

    /** Player thread: damages a player standing on a magma block; forms the bubble columns under a swimming player. */
    @SuppressWarnings("deprecation")
    void tick(Player player) {
        final MagmaColumnKeeper c = columns;
        if (c != null) {
            c.nearPlayer(player);
        }
        final Location at = player.getLocation().subtract(0, BELOW, 0);
        final Block below = at.getBlock();
        final Optional<BlockStateRef> state = blocks.stateAt(below);
        if (state.isEmpty() || !MagmaRules.isMagma(state.get().id())) {
            return;
        }
        final ItemStack boots = player.getInventory().getBoots();
        final boolean frostWalker = boots != null && boots.getEnchantmentLevel(Enchantment.FROST_WALKER) > 0;
        final boolean fireImmune = player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE) || player.isInvulnerable();
        if (MagmaRules.hurts(player.getGameMode(), player.isOnGround(), player.isSneaking(), frostWalker, fireImmune)) {
            player.damage(MagmaRules.DAMAGE, DamageSource.builder(DamageType.HOT_FLOOR).build());
        }
    }
}
