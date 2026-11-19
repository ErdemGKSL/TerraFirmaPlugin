/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onProjectileImpact, onFireStop),
 *       src/main/java/net/dries007/tfc/util/events/DouseFireEvent.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (enableFireArrowSpreading)
 */
package dev.tfp.paper.fire.spread;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.util.BoundingBox;

/**
 * PAR-HEAT-13: TFC's {@code onProjectileImpact}. A burning projectile that hits a block starts a fire there (strong,
 * like TFC's {@code startFire} without a player) when {@code enableFireArrowSpreading} is on; a plain water potion
 * douses the fires, lit candles and lit campfires of its (inflated) box and puts out burning entities in it.
 * Torches, jack-o'-lanterns and the TFC devices have their own douse in the items that add them (PAR-HEAT-22, -27).
 *
 * <p>Runs on the projectile's region thread; each block of the douse box is changed on its own region.
 */
public final class FireSpreadListener implements Listener {
    /** Starts a fire at {@code block}'s {@code face}, as {@code FireStarting.start} does for a strong start. */
    @FunctionalInterface
    public interface Igniter {
        void ignite(Block block, BlockFace face);
    }

    private final Scheduler scheduler;
    private final BooleanSupplier arrowSpreading;
    private final Igniter igniter;

    public FireSpreadListener(Scheduler scheduler, BooleanSupplier arrowSpreading, Igniter igniter) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.arrowSpreading = Objects.requireNonNull(arrowSpreading, "arrowSpreading");
        this.igniter = Objects.requireNonNull(igniter, "igniter");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof ThrownPotion potion && isWater(potion)) {
            douse(potion, projectile instanceof LingeringPotion);
        }
        if (arrowSpreading.getAsBoolean() && event.getHitBlock() != null && projectile.getFireTicks() > 0) {
            Block hit = event.getHitBlock();
            BlockFace face = event.getHitBlockFace() == null ? BlockFace.UP : event.getHitBlockFace();
            igniter.ignite(hit, face);
        }
    }

    /** TFC: potion contents are the water potion and carry no effects. */
    static boolean isWater(ThrownPotion potion) {
        return potion.getItem().getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() == PotionType.WATER
            && !meta.hasCustomEffects();
    }

    private void douse(ThrownPotion potion, boolean lingering) {
        double hx = lingering ? 4 : 2;
        BoundingBox box = potion.getBoundingBox().expand(hx, 2, hx);
        for (Entity e : potion.getWorld().getNearbyEntities(box)) {
            if (e instanceof LivingEntity living && living.getFireTicks() > 0 && !living.isDead()) {
                living.setFireTicks(0);
            }
        }
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block b = potion.getWorld().getBlockAt(x, y, z);
                    if (!b.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    if (scheduler.isOwner(b.getWorld(), x >> 4, z >> 4)) {
                        extinguish(b);
                    } else {
                        scheduler.region(b.getWorld(), x >> 4, z >> 4, () -> extinguish(b));
                    }
                }
            }
        }
    }

    /** TFC {@code onFireStop} for the vanilla blocks: fire is removed, a lit candle or campfire goes out. */
    static boolean extinguish(Block block) {
        Material type = block.getType();
        if (type == Material.FIRE || type == Material.SOUL_FIRE) {
            block.setType(Material.AIR, false);
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 1f, 1f);
            return true;
        }
        BlockData data = block.getBlockData();
        boolean candle = Tag.CANDLES.isTagged(type) || Tag.CANDLE_CAKES.isTagged(type);
        if ((candle || Tag.CAMPFIRES.isTagged(type)) && data instanceof Lightable light && light.isLit()) {
            light.setLit(false);
            block.setBlockData(light);
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 1f, 1f);
            return true;
        }
        return false;
    }
}
