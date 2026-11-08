/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/HotWaterBlock.java @ c3b4d9536 (entityInside), with
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java (onLivingBreathe) and TFCBubbleColumnBlock.java
 */
package dev.tfp.paper.worldwater.hot;

import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * PAR-FLUID-04: hot spring healing and TFC's airless bubble columns for players, run every {@link #PERIOD} ticks on each
 * player's own thread (the blocks read are those in and next to the player's box, owned by that thread).
 *
 * <p>Healing ({@link HotSpringRules#heal}): the spring water blocks the box touches, each with TFC's per-tick 1 in 10 roll,
 * {@link #PERIOD} ticks at a time. Spectators are not healed (vanilla calls no {@code entityInside} for them), dead players
 * neither. Air: with {@code bubbleColumnProvidesAir} off, a player whose eyes are in a drag-down bubble column loses air as
 * under water and drowns, unless invulnerable (creative, spectator) or with water breathing or conduit power; vanilla's own
 * air gain there is cancelled by {@link HotSpringModule}.
 */
public final class HotSpringTracker {
    /** Ticks between two runs per player. */
    public static final long PERIOD = 10;

    /** How drowning damage is dealt (the server: vanilla {@code minecraft:drown}; MockBukkit has no damage source). */
    @FunctionalInterface
    public interface Hurt {
        void hurt(Player player, double amount);
    }

    private final SpringWater springs;
    private final Hurt drown;
    private final double healAmount;
    private final boolean bubbleColumnProvidesAir;
    /** The random source of the calling thread (TFC's {@code level.random}). */
    private final Supplier<RandomGenerator> random;

    public HotSpringTracker(SpringWater springs, Hurt drown, double healAmount, boolean bubbleColumnProvidesAir,
            Supplier<RandomGenerator> random) {
        this.springs = springs;
        this.drown = drown;
        this.healAmount = healAmount;
        this.bubbleColumnProvidesAir = bubbleColumnProvidesAir;
        this.random = random;
    }

    /** Player thread: one period of healing and bubble column air. */
    public void tick(Player player) {
        ThreadAsserts.owned(player);
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        heal(player);
        air(player);
    }

    /** Player thread: the spring water rolls of one period. */
    void heal(Player player) {
        AttributeInstance maxAttr = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? 20.0 : maxAttr.getValue();
        double health = player.getHealth();
        if (health >= max || healAmount <= 0) {
            return;
        }
        int blocks = springBlocksTouched(player);
        if (blocks == 0) {
            return;
        }
        double healed = HotSpringRules.heal(health, max, blocks, (int) PERIOD, healAmount, random.get());
        if (healed > health) {
            player.heal(healed - health);
        }
    }

    /** Player thread: the spring water blocks inside the player's box. */
    int springBlocksTouched(Player player) {
        return springs.blocksIn(player.getWorld(), player.getBoundingBox());
    }

    /** Player thread: vanilla's underwater air loss in a drag-down bubble column, when those give no air. */
    void air(Player player) {
        if (bubbleColumnProvidesAir || !inTfcBubbleColumn(player) || !breathes(player)) {
            return;
        }
        int respiration = respiration(player);
        int air = player.getRemainingAir();
        RandomGenerator r = random.get();
        for (int i = 0; i < PERIOD; i++) {
            HotSpringRules.Air next = HotSpringRules.air(air, respiration, r);
            air = next.air();
            if (next.drowned()) {
                player.setRemainingAir(air);
                drown.hurt(player, HotSpringRules.DROWN_DAMAGE);
                if (player.isDead()) {
                    return;
                }
            }
        }
        player.setRemainingAir(air);
    }

    /** Whether the bubble columns give no air for this player now: off by the setting, eyes in a drag column, breathing. */
    boolean holdsBreath(Player player) {
        return !bubbleColumnProvidesAir && player.getGameMode() != GameMode.SPECTATOR && inTfcBubbleColumn(player)
                && breathes(player);
    }

    /** TFC's bubble columns stand above TFC magma and always drag down: vanilla drag columns over the magma placeholder. */
    static boolean inTfcBubbleColumn(Player player) {
        Location eye = player.getEyeLocation();
        Block block = eye.getBlock();
        if (block.getType() != Material.BUBBLE_COLUMN) {
            return false;
        }
        BlockData data = block.getBlockData();
        return data instanceof BubbleColumn column && column.isDrag();
    }

    /** Vanilla: invulnerable players (creative, spectator) and water breathers keep their air. */
    static boolean breathes(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.isInvulnerable()) {
            return false;
        }
        return !player.hasPotionEffect(PotionEffectType.WATER_BREATHING)
                && !player.hasPotionEffect(PotionEffectType.CONDUIT_POWER);
    }

    static int respiration(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet == null ? 0 : helmet.getEnchantmentLevel(Enchantment.RESPIRATION);
    }
}
