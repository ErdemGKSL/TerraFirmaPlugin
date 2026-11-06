/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.player.MountHudService.MountReading;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Minecart;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-HUD-09: the mount reading (TFC {@code renderMountHealth} values) and when it is published. */
class MountHudServiceTest extends MockServerTest {
    MountHudService service;

    @AfterEach
    void clear() {
        if (service != null) {
            try {
                service.disable();
            } catch (org.mockbukkit.mockbukkit.exception.UnimplementedOperationException e) {
                // MockBukkit cannot cancel a global repeating task; the readings are cleared before the cancel.
            }
        }
    }

    /** TFC {@code renderHealthBar}, modifier 1: (health + absorption) x 50 over max x 50, NaN absorption as 0. */
    @Test
    void readingIsTfcTextScale() {
        assertThat(MountHudService.of(16, 0, 22)).isEqualTo(new MountReading(800, 1100));
        assertThat(MountHudService.of(16, 4, 22)).isEqualTo(new MountReading(1000, 1100));
        assertThat(MountHudService.of(22, 4, 22).health()).as("not capped, as TFC").isEqualTo(1300);
        assertThat(MountHudService.of(10, Double.NaN, 20)).isEqualTo(new MountReading(500, 1000));
        assertThat(MountHudService.of(10, 0, 0)).isEqualTo(MountReading.NONE);
        assertThat(MountHudService.of(10, 0, Double.NaN)).isEqualTo(MountReading.NONE);
    }

    @Test
    void notLivingOrNoVehicleReadsNone() {
        World world = server.addSimpleWorld("w");
        Minecart cart = world.spawn(new Location(world, 0, 64, 0), Minecart.class);
        assertThat(MountHudService.of(cart)).isEqualTo(MountReading.NONE);
        assertThat(MountHudService.of((org.bukkit.entity.Entity) null)).isEqualTo(MountReading.NONE);
    }

    @Test
    void ridingPublishesTheHorseAndDismountHidesIt() {
        service = new MountHudService(new ServerScheduler(plugin, 1));
        server.getPluginManager().registerEvents(service, plugin);
        World world = server.addSimpleWorld("w");
        PlayerMock bot = server.addPlayer("Bot");
        Horse horse = world.spawn(new Location(world, 0, 64, 0), Horse.class);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(22);
        horse.setHealth(22);
        assertThat(MountHudService.reading(bot.getUniqueId())).isEqualTo(MountReading.NONE);

        service.onMount(new EntityMountEvent(bot, horse));
        assertThat(MountHudService.reading(bot.getUniqueId())).as("inside the event").isEqualTo(new MountReading(1100, 1100));

        horse.addPassenger(bot);
        horse.setHealth(16);
        service.onDamage(new EntityDamageEvent(horse, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), 6));
        server.getScheduler().performOneTick();
        assertThat(MountHudService.reading(bot.getUniqueId())).as("next tick after the damage")
            .isEqualTo(new MountReading(800, 1100));

        horse.removePassenger(bot);
        service.onDismount(new EntityDismountEvent(bot, horse));
        assertThat(MountHudService.reading(bot.getUniqueId())).isEqualTo(MountReading.NONE);
        server.getScheduler().performOneTick();
        assertThat(MountHudService.reading(bot.getUniqueId())).as("not riding on the next tick").isEqualTo(MountReading.NONE);
    }

    @Test
    void throttleRereadsRidersAndQuitClears() {
        service = new MountHudService(new ServerScheduler(plugin, 1));
        World world = server.addSimpleWorld("w");
        PlayerMock bot = server.addPlayer("Bot");
        Horse horse = world.spawn(new Location(world, 0, 64, 0), Horse.class);
        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);
        horse.setHealth(20);
        horse.addPassenger(bot);
        service.start(() -> List.of(bot));
        server.getScheduler().performOneTick();
        assertThat(MountHudService.reading(bot.getUniqueId())).as("already riding at enable").isEqualTo(new MountReading(1000, 1000));

        horse.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30);
        server.getScheduler().performTicks(MountHudService.PERIOD_TICKS + 1);
        assertThat(MountHudService.reading(bot.getUniqueId()).maxHealth()).isEqualTo(1500);

        service.onQuit(new PlayerQuitEvent(bot, net.kyori.adventure.text.Component.empty(),
            PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertThat(MountHudService.reading(bot.getUniqueId())).isEqualTo(MountReading.NONE);
    }

    @Test
    void disableClearsEveryReading() {
        service = new MountHudService(new ServerScheduler(plugin, 1));
        World world = server.addSimpleWorld("w");
        PlayerMock bot = server.addPlayer("Bot");
        Horse horse = world.spawn(new Location(world, 0, 64, 0), Horse.class);
        service.onMount(new EntityMountEvent(bot, horse));
        assertThat(MountHudService.reading(bot.getUniqueId()).maxHealth()).isPositive();
        service.disable();
        assertThat(MountHudService.reading(bot.getUniqueId())).isEqualTo(MountReading.NONE);
    }
}
