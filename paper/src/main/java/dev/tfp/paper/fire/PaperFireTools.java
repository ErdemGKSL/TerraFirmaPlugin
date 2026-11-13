/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/FirestarterItem.java @ c3b4d9536 (onUseTick),
 *       src/main/java/net/dries007/tfc/common/items/FlintAndPyriteItem.java @ c3b4d9536 (useOn)
 */
package dev.tfp.paper.fire;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

/** {@link FireTools} on a Paper server. */
public final class PaperFireTools implements FireTools {
    /** Vanilla's default block interaction range, when the attribute is missing. */
    static final double DEFAULT_RANGE = 4.5;

    @Override
    public Optional<Target> target(Player player) {
        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        RayTraceResult hit = player.rayTraceBlocks(range != null ? range.getValue() : DEFAULT_RANGE,
            FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            return Optional.empty();
        }
        return Optional.of(new Target(hit.getHitBlock(), hit.getHitBlockFace()));
    }

    @Override
    public void damage(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        if (held != null && !held.getType().isAir()) {
            player.damageItemStack(hand, 1);
        }
    }

    @Override
    public void offhandCooldown(Player player, int ticks) {
        ItemStack off = player.getInventory().getItemInOffHand();
        if (!off.getType().isAir()) {
            player.setCooldown(off, ticks);
        }
    }

    @Override
    public void strikeSound(Block block) {
        // TFC: level.playSound(player, pos, FLINTANDSTEEL_USE, BLOCKS, 1.0, random * 0.4 + 0.8); the vanilla client does
        // not predict our item, so the player hears it too
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_FLINTANDSTEEL_USE,
            SoundCategory.BLOCKS, 1.0f, ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f);
    }
}
