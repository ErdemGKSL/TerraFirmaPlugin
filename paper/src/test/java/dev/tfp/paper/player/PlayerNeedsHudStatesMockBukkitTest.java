/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-HUD-19: the bar states published from the player's thread: the health glow (hurt with saturation, the hurt time
 * after a hit, Regeneration), absorption in the fill and the text, and the thirst overheat level from the ambient
 * temperature.
 */
class PlayerNeedsHudStatesMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e6");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    PlayerMock alex;
    HudSnapshotCache hud;
    PlayerNeedsHudService needs;

    @BeforeEach
    void setUpPlayer() {
        PlayerDataStore store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        alex.setGameMode(GameMode.SURVIVAL);
        hud = new HudSnapshotCache(() -> true);
        needs = new PlayerNeedsHudService(store, new ServerScheduler(plugin, 1), hud, () -> 0L);
        needs.nanoClock(nanos::get);
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    HudSnapshot published() {
        return hud.snapshot(ALEX);
    }

    @Test
    void hurtWithSaturationGlowsAndFullHealthDoesNot() {
        alex.setHealth(20);
        alex.setSaturation(5f);
        needs.publish(alex);
        assertThat(published().healthGlow(nanos.get())).as("full health").isFalse();

        alex.setHealth(14);
        needs.publish(alex);
        assertThat(published().healthGlow(nanos.get())).as("hurt with saturation").isTrue();

        alex.setSaturation(0f);
        needs.publish(alex);
        assertThat(published().healthGlow(nanos.get())).as("hurt without saturation").isFalse();
    }

    @Test
    void regenerationGlowsAtFullHealth() {
        alex.setHealth(20);
        alex.setSaturation(0f);
        alex.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 0));
        needs.publish(alex);
        assertThat(published().barStates().steadyGlow()).isTrue();
        assertThat(published().healthGlow(nanos.get())).isTrue();
    }

    /** A hit stamps TFC's 10-tick hurt time: the glow shows at once and ends by itself, with no further publish. */
    @Test
    void aHitGlowsForTheHurtTimeOnlyAndSurvivesTheNextPublish() {
        alex.setHealth(20);
        alex.setSaturation(0f);
        needs.publish(alex);
        needs.onDamage(new EntityDamageEvent(alex, EntityDamageEvent.DamageCause.FALL,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 2.0));
        assertThat(published().healthGlow(nanos.get())).as("right after the hit").isTrue();

        // The next reading (the republish after the hit) keeps the stamp.
        alex.setHealth(18);
        needs.publish(alex);
        assertThat(published().healthGlow(nanos.get() + PlayerNeedsHudService.HURT_NANOS - 1)).isTrue();
        assertThat(published().healthGlow(nanos.get() + PlayerNeedsHudService.HURT_NANOS)).as("hurt time over").isFalse();
    }

    @Test
    void absorptionIsInTheShownHealthAndFillsTheSurplusBar() {
        alex.setHealth(20);
        alex.setAbsorptionAmount(4);
        needs.publish(alex);
        HudSnapshot s = published();
        double perHp = s.maxHealth() / 20;
        assertThat(s.barStates().absorption()).isCloseTo(4 * perHp, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(s.healthWithAbsorption()).isCloseTo(24 * perHp, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(s.barStates().surplus()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void overheatLevelFollowsThePublishedAmbientTemperature() {
        hud.put(ALEX, published().withAmbientTemperature(30));
        needs.publish(alex);
        assertThat(published().barStates().thirstOverheat()).isEqualTo(3);
        hud.put(ALEX, published().withAmbientTemperature(15));
        needs.publish(alex);
        assertThat(published().barStates().thirstOverheat()).isZero();
    }
}
