/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.effect;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.aquatic.AquaticSpecies;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-DATA-07: ink and glow ink from hurting a squid or octopoteuthis. */
class InkListenerMockBukkitTest extends MockServerTest {
    private World world;
    private PlayerMock player;
    private Cow mob;
    private AquaticSpecies species = AquaticSpecies.SQUID;
    private int roll = 0;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        player.teleport(new Location(world, 0, 64, 0));
        mob = world.spawn(new Location(world, 3, 64, 0), Cow.class);
        // a Random whose nextInt(3) is the scripted roll
        Random scripted = new Random() {
            @Override
            public int nextInt(int bound) {
                return roll;
            }
        };
        server.getPluginManager().registerEvents(
            new InkListener(e -> e == mob ? Optional.of(species) : Optional.empty(), scripted), plugin);
    }

    private void hit() {
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(player, mob, DamageCause.ENTITY_ATTACK, 4.0));
    }

    @Test
    void theGateIsTfcsRangeAndOneInThree() {
        assertThat(InkListener.inks(63.9, 0)).isTrue();
        assertThat(InkListener.inks(64.0, 0)).isFalse();
        assertThat(InkListener.inks(1.0, 1)).isFalse();
        assertThat(InkListener.inks(1.0, 2)).isFalse();
        assertThat(InkListener.INK_TICKS).isEqualTo(100);
    }

    @Test
    void aSquidHitWithTheRollGivesInkForOneHundredTicks() {
        hit();
        var effect = player.getPotionEffect(InkListener.INK_CARRIER);
        assertThat(effect).isNotNull();
        assertThat(effect.getDuration()).isEqualTo(100);
    }

    @Test
    void anOctopoteuthisGivesGlowInk() {
        species = AquaticSpecies.OCTOPOTEUTHIS;
        hit();
        assertThat(player.hasPotionEffect(PotionEffectType.GLOWING)).isTrue();
        assertThat(player.hasPotionEffect(PotionEffectType.DARKNESS)).isFalse();
    }

    @Test
    void aMissedRollFarPlayerOrOtherSpeciesGivesNothing() {
        roll = 1;
        hit();
        assertThat(player.getActivePotionEffects()).isEmpty();
        roll = 0;
        player.teleport(new Location(world, 20, 64, 0));
        hit();
        assertThat(player.getActivePotionEffects()).isEmpty();
        player.teleport(new Location(world, 0, 64, 0));
        species = AquaticSpecies.COD;
        hit();
        assertThat(player.getActivePotionEffects()).isEmpty();
    }
}
