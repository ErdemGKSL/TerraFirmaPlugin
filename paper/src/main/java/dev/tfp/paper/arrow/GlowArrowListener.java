/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/GlowArrow.java @ c3b4d9536 (doPostHurtEffects: glow ink, 200 ticks)
 */
package dev.tfp.paper.arrow;

import java.util.Optional;
import java.util.function.Function;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * PAR-TOOL-10: an arrow that carries the CE item {@code tfp:glow_arrow} gives the living entity it hurts the glowing effect
 * for 200 ticks (TFC's glow ink effect, 10 s). The arrow is a vanilla arrow entity; its pickup stack is the glow arrow item.
 */
public final class GlowArrowListener implements Listener {
    public static final String ITEM = "tfp:glow_arrow";
    /** TFC's effect duration in ticks. */
    public static final int GLOW_TICKS = 200;

    private final Function<ItemStack, Optional<String>> idOf;

    /** @param idOf the custom item id of a stack */
    public GlowArrowListener(Function<ItemStack, Optional<String>> idOf) {
        this.idOf = idOf;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof AbstractArrow arrow && event.getEntity() instanceof LivingEntity target
                && idOf.apply(arrow.getItemStack()).filter(ITEM::equals).isPresent()) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, GLOW_TICKS, 0));
        }
    }
}
