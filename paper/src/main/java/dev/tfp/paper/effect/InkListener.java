/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/TFCSquid.java @ c3b4d9536 (hurt, getInkEffect),
 * src/main/java/net/dries007/tfc/common/entities/aquatic/Octopoteuthis.java @ c3b4d9536 (getInkEffect)
 */
package dev.tfp.paper.effect;

import dev.tfp.core.aquatic.AquaticSpecies;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * PAR-DATA-07: a squid or octopoteuthis that is hurt by a player squirts ink. TFC (TFCSquid.hurt): after a successful hurt,
 * when the last attacker is a player closer than 8 blocks (distance squared below 64) and {@code random.nextInt(3) == 0},
 * the player gets the ink effect (octopoteuthis: glow ink) for 100 ticks. A vanilla client cannot show TFC's ink overlay or
 * icon, so the effect rides a vanilla carrier: {@link #INK_CARRIER} for ink, {@link #GLOW_INK_CARRIER} for glow ink
 * (NP-CLIENT-02; the glow arrow of PAR-TOOL-10 uses the same glow carrier).
 */
public final class InkListener implements Listener {
    /** TFC's ink duration in ticks. */
    public static final int INK_TICKS = 100;
    /** TFC's range test: squared distance below 64. */
    public static final double RANGE_SQUARED = 64.0;
    /** The chance denominator: {@code nextInt(3) == 0}. */
    public static final int CHANCE_BOUND = 3;
    public static final PotionEffectType INK_CARRIER = PotionEffectType.DARKNESS;
    public static final PotionEffectType GLOW_INK_CARRIER = PotionEffectType.GLOWING;

    private final Function<Entity, Optional<AquaticSpecies>> speciesOf;
    private final Random random;

    /**
     * @param speciesOf the aquatic species of a mob
     * @param random    the source of the one {@code nextInt(3)} draw per qualifying hurt
     */
    public InkListener(Function<Entity, Optional<AquaticSpecies>> speciesOf, Random random) {
        this.speciesOf = speciesOf;
        this.random = random;
    }

    /** The carrier effect type of a species, or empty when the species does not ink. */
    public static Optional<PotionEffectType> carrierOf(AquaticSpecies species) {
        return switch (species) {
            case SQUID -> Optional.of(INK_CARRIER);
            case OCTOPOTEUTHIS -> Optional.of(GLOW_INK_CARRIER);
            default -> Optional.empty();
        };
    }

    /** TFC's gate: a player closer than 8 blocks and a one in three roll. */
    public static boolean inks(double distanceSquared, int roll) {
        return distanceSquared < RANGE_SQUARED && roll == 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) {
            return;
        }
        Optional<PotionEffectType> carrier = speciesOf.apply(event.getEntity()).flatMap(InkListener::carrierOf);
        if (carrier.isEmpty()) {
            return;
        }
        Player player = attacker(event.getDamager());
        if (player == null || !player.getWorld().equals(event.getEntity().getWorld())) {
            return;
        }
        if (inks(player.getLocation().distanceSquared(event.getEntity().getLocation()), random.nextInt(CHANCE_BOUND))) {
            player.addPotionEffect(new PotionEffect(carrier.get(), INK_TICKS, 0));
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
