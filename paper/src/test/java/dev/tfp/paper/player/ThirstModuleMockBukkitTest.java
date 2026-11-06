/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.thirst.ThirstEffect;
import dev.tfp.core.thirst.ThirstStage;
import dev.tfp.core.weather.PrecipitationType;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import net.kyori.adventure.text.TranslatableComponent;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-SURV-04: the lack-of-thirst effects per stage (TFC {@code PlayerInfo.tick}), dehydration damage and its death
 * message, peaceful and creative, the thirst effect's drain and rain drinking through {@link ThirstModule#read}.
 */
class ThirstModuleMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000054a4");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    final long[] now = {0L};
    final int[] hits = {0};
    PlayerDataStore store;
    PlayerMock alex;
    HudSnapshotCache hud;
    ThirstModule module;

    @BeforeEach
    void setUpPlayer() {
        store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        alex.setGameMode(GameMode.SURVIVAL);
        alex.getWorld().setDifficulty(Difficulty.NORMAL);
        hud = new HudSnapshotCache(() -> true);
        // MockBukkit implements no damage with a source: the hit is the plain one, and its amount is recorded.
        module = new ThirstModule(store, () -> now[0], hud, new ServerScheduler(plugin, 1), (p, amount) -> {
            hits[0]++;
            p.damage(amount);
        });
        server.getPluginManager().registerEvents(module, plugin);
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    private void thirst(float value) {
        store.update(ALEX, d -> d.withThirstClock(value, now[0], 0L));
    }

    private float thirst() {
        return store.get(ALEX).orElseThrow().thirst();
    }

    private void temperature(double celsius) {
        HudSnapshot s = hud.snapshot(ALEX);
        hud.publish(alex, new HudSnapshot(s.health(), s.maxHealth(), s.thirst(), s.hunger(), s.heldHeat(), celsius,
                PrecipitationType.NONE, s.air(), s.armor(), true));
    }

    private static int amplifier(PotionEffect effect) {
        return effect == null ? -1 : effect.getAmplifier();
    }

    @Test
    void everyStageGivesTfcsSlowdownAndOnlyDehydrationHurts() {
        float[] levels = {20f, 19.9f, 10f, 9.9f, 0.01f, 0f};
        int[] expected = {-1, 0, 0, 1, 1, 1};
        for (int i = 0; i < levels.length; i++) {
            alex.clearActivePotionEffects();
            alex.setHealth(20);
            thirst(levels[i]);
            module.read(alex, true);
            PotionEffect slow = alex.getPotionEffect(PotionEffectType.SLOWNESS);
            PotionEffect fatigue = alex.getPotionEffect(PotionEffectType.MINING_FATIGUE);
            assertThat(amplifier(slow)).as("slowness at " + levels[i]).isEqualTo(expected[i]);
            assertThat(amplifier(fatigue)).as("mining fatigue at " + levels[i]).isEqualTo(expected[i]);
            if (slow != null) {
                assertThat(slow.getDuration()).isEqualTo(ThirstModule.SLOWDOWN_TICKS);
                assertThat(slow.hasParticles()).isFalse();
                assertThat(slow.hasIcon()).as("TFC: visible=false, so no icon").isFalse();
                assertThat(slow.isAmbient()).isFalse();
            }
            boolean hurts = ThirstStage.of(levels[i], dev.tfp.core.thirst.ThirstConfig.DEFAULT).damages();
            assertThat(alex.getHealth()).as("health at " + levels[i]).isEqualTo(hurts ? 19.0 : 20.0);
            assertThat(hits[0]).as("hits after " + levels[i]).isEqualTo(hurts ? 1 : 0);
            hits[0] = 0;
        }
    }

    @Test
    void noEffectsOutsideTheHundredTickCheck() {
        thirst(0f);
        module.read(alex, false);
        assertThat(alex.getPotionEffect(PotionEffectType.SLOWNESS)).isNull();
        assertThat(alex.getHealth()).isEqualTo(20.0);
    }

    @Test
    void peacefulHasNoEffectsAndNoDamage() {
        alex.getWorld().setDifficulty(Difficulty.PEACEFUL);
        thirst(0f);
        module.read(alex, true);
        assertThat(alex.getPotionEffect(PotionEffectType.SLOWNESS)).isNull();
        assertThat(alex.getHealth()).isEqualTo(20.0);
    }

    @Test
    void creativeAndSpectatorHaveNoEffectsNoDamageAndNoDrain() {
        for (GameMode mode : new GameMode[] {GameMode.CREATIVE, GameMode.SPECTATOR}) {
            alex.setGameMode(mode);
            thirst(0f);
            ThirstEffects.write(alex, new ThirstEffect(1, 600));
            module.read(alex, true);
            assertThat(alex.getPotionEffect(PotionEffectType.SLOWNESS)).as(mode.name()).isNull();
            assertThat(alex.getHealth()).as(mode.name()).isEqualTo(20.0);
            assertThat(ThirstEffects.read(alex)).as("the effect still runs out").contains(new ThirstEffect(1, 580));
        }
        alex.setGameMode(GameMode.ADVENTURE);
        thirst(5f);
        module.read(alex, true);
        assertThat(amplifier(alex.getPotionEffect(PotionEffectType.SLOWNESS))).as("adventure is survival").isEqualTo(1);
    }

    @Test
    void dehydrationDamageIsStarvationWithTfcsDeathMessage() {
        PlayerDeathEvent[] death = {null};
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onDeath(PlayerDeathEvent event) {
                death[0] = event;
            }
        }, plugin);
        alex.setHealth(1);
        thirst(0f);
        module.read(alex, true);
        assertThat(alex.isDead()).isTrue();
        assertThat(death[0]).isNotNull();
        assertThat(death[0].deathMessage()).isInstanceOf(TranslatableComponent.class);
        assertThat(((TranslatableComponent) death[0].deathMessage()).key()).isEqualTo(ThirstModule.DEATH_KEY);
    }

    @Test
    void anOtherDeathKeepsItsMessage() {
        PlayerDeathEvent[] death = {null};
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onDeath(PlayerDeathEvent event) {
                death[0] = event;
            }
        }, plugin);
        alex.setHealth(1);
        alex.damage(5);
        assertThat(death[0]).isNotNull();
        if (death[0].deathMessage() instanceof TranslatableComponent t) {
            assertThat(t.key()).isNotEqualTo(ThirstModule.DEATH_KEY);
        }
    }

    @Test
    void theRealHitIsOnePointOfStarvation() {
        assertThat(ThirstModule.DEHYDRATION_DAMAGE).isEqualTo(1.0);
        assertThat(ThirstModule.STARVE).isNotNull();
    }

    @Test
    void aDeadPlayerIsSkipped() {
        thirst(50f);
        ThirstEffects.write(alex, new ThirstEffect(1, 600));
        alex.setHealth(0);
        module.read(alex, true);
        assertThat(ThirstEffects.read(alex)).contains(new ThirstEffect(1, 600));
    }

    @Test
    void theThirstEffectDrainsPerReadingAndRunsOut() {
        thirst(50f);
        ThirstEffects.write(alex, new ThirstEffect(1, 40));
        module.read(alex, false); // remaining 40..21: 25 applies once
        assertThat(thirst()).isEqualTo(50f - 0.04f);
        assertThat(ThirstEffects.read(alex)).contains(new ThirstEffect(1, 20));
        module.read(alex, false); // remaining 20..1: none
        assertThat(thirst()).isEqualTo(50f - 0.04f);
        assertThat(ThirstEffects.read(alex)).isEmpty();
    }

    @Test
    void theThirstEffectSurvivesInThePersistentDataAndCombines() {
        ThirstEffects.add(alex, new ThirstEffect(1, 300));
        ThirstEffects.add(alex, new ThirstEffect(1, 600));
        assertThat(ThirstEffects.read(alex)).contains(new ThirstEffect(1, 600));
        ThirstEffects.add(alex, new ThirstEffect(0, 6000));
        assertThat(ThirstEffects.read(alex)).contains(new ThirstEffect(1, 600));
        assertThat(ThirstEffects.apply(alex, WaterDrinkTest.THIRST)).isTrue();
        assertThat(ThirstEffects.apply(alex, new dev.tfp.core.thirst.Drinkable.Effect("tfp:unknown", 10, 0, 1f)))
                .isFalse();
        assertThat(ThirstEffects.apply(alex, new dev.tfp.core.thirst.Drinkable.Effect("minecraft:nausea", 100, 0, 1f)))
                .isTrue();
        assertThat(alex.getPotionEffect(PotionEffectType.NAUSEA)).isNotNull();
    }

    @Test
    void lookingUpInTheRainDrinksTwentyTicksPerReading() {
        temperature(15);
        alex.getWorld().setStorm(true);
        Location up = alex.getLocation().clone();
        up.setY(alex.getWorld().getHighestBlockYAt(up) + 1);
        up.setPitch(-90f);
        alex.teleport(up);
        assertThat(ThirstModule.openToSky(alex)).isTrue();
        thirst(50f);
        module.read(alex, false);
        assertThat(thirst()).isCloseTo(50f + 20 * (float) (5d / 24d), within(1e-3f));

        up.setPitch(0f);
        alex.teleport(up);
        thirst(50f);
        module.read(alex, false);
        assertThat(thirst()).as("not looking up").isEqualTo(50f);

        up.setPitch(-90f);
        alex.teleport(up);
        alex.getWorld().setStorm(false);
        module.read(alex, false);
        assertThat(thirst()).as("no rain").isEqualTo(50f);

        alex.getWorld().setStorm(true);
        temperature(-5);
        module.read(alex, false);
        assertThat(thirst()).as("snow").isEqualTo(50f);
    }
}
