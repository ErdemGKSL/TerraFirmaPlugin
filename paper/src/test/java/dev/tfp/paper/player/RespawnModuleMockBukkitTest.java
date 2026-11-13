/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.config.ConfigService;
import dev.tfp.paper.integration.HudSnapshotCache;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-SURV-09: a respawn after a death resets thirst and nutrition per the rules; the extra-peaceful step. */
class RespawnModuleMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000013a1");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    final long[] now = {0L};
    PlayerDataStore store;
    PlayerMock alex;
    HudSnapshotCache hud;

    @BeforeEach
    void setUpPlayer() {
        store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        alex.setGameMode(GameMode.SURVIVAL);
        hud = new HudSnapshotCache(() -> true);
        store.update(ALEX, d -> d.withThirstClock(12f, 100L, 3L).withNutrition(List.of(RespawnRulesTest.BREAD))
                .withIntoxicationTick(90_000L).withChiselMode("tfp:stair"));
        ThirstEffects.write(alex, new dev.tfp.core.thirst.ThirstEffect(0, 600));
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    private RespawnModule module(RespawnRules rules) {
        RespawnModule m = new RespawnModule(rules, store, () -> now[0], hud);
        server.getPluginManager().registerEvents(m, plugin);
        return m;
    }

    private void respawn(PlayerRespawnEvent.RespawnReason reason) {
        server.getPluginManager().callEvent(new PlayerRespawnEvent(alex, alex.getLocation(), false, false, false,
                reason));
    }

    @Test
    void respawnAfterDeathShowsFullThirstAndKeepsNutritionByDefault() {
        module(RespawnRules.DEFAULT);
        now[0] = 5_000L;
        respawn(PlayerRespawnEvent.RespawnReason.DEATH);
        PlayerData d = store.get(ALEX).orElseThrow();
        assertThat(d.thirst()).isEqualTo(PlayerData.MAX_THIRST);
        assertThat(d.thirstTick()).isEqualTo(5_000L);
        assertThat(d.intoxicationTick()).isEqualTo(PlayerData.NEW.intoxicationTick());
        assertThat(d.chiselMode()).isEqualTo(PlayerData.DEFAULT_CHISEL_MODE);
        assertThat(d.nutrition()).containsExactly(RespawnRulesTest.BREAD);
        assertThat(ThirstEffects.read(alex)).as("the thirst effect dies with the player").isEmpty();
    }

    @Test
    void respawnWithoutKeepNutritionHasDefaultNutrition() {
        module(new RespawnRules(false, false));
        respawn(PlayerRespawnEvent.RespawnReason.DEATH);
        PlayerData d = store.get(ALEX).orElseThrow();
        assertThat(d.thirst()).isEqualTo(PlayerData.MAX_THIRST);
        assertThat(d.nutrition()).isEmpty();
    }

    /** PAR-SURV-14: keep-nutrition-after-death false in config.yml and a reload: the next respawn has default nutrition. */
    @Test
    void configReloadWithoutKeepNutritionResetsNutritionOnTheNextRespawn() throws IOException {
        Path file = dir.resolve("config/config.yml");
        ConfigService config = new ConfigService(file, () -> shippedConfig());
        config.load();
        RespawnModule m = new RespawnModule(RespawnModule.configRules(() -> config.current().survival(), k -> null),
                store, () -> now[0], hud);
        server.getPluginManager().registerEvents(m, plugin);
        assertThat(m.rules()).isEqualTo(RespawnRules.DEFAULT);
        respawn(PlayerRespawnEvent.RespawnReason.DEATH);
        assertThat(store.get(ALEX).orElseThrow().nutrition()).as("TFC default: kept").containsExactly(RespawnRulesTest.BREAD);

        Files.writeString(file, Files.readString(file)
                .replace("keep-nutrition-after-death: true", "keep-nutrition-after-death: false"));
        assertThat(config.load().applied()).isTrue(); // what /tfp reload config does
        assertThat(m.rules()).isEqualTo(new RespawnRules(false, false));
        respawn(PlayerRespawnEvent.RespawnReason.DEATH);
        PlayerData d = store.get(ALEX).orElseThrow();
        assertThat(d.nutrition()).as("default nutrition").isEmpty();
        assertThat(d.thirst()).isEqualTo(PlayerData.MAX_THIRST);
    }

    private static String shippedConfig() {
        try (InputStream in = RespawnModuleMockBukkitTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void leavingTheEndKeepsEverything() {
        module(new RespawnRules(false, false));
        PlayerData before = store.get(ALEX).orElseThrow();
        respawn(PlayerRespawnEvent.RespawnReason.END_PORTAL);
        assertThat(store.get(ALEX).orElseThrow()).isEqualTo(before);
        assertThat(ThirstEffects.read(alex)).isPresent();
    }

    @Test
    void extraPeacefulRegeneratesHealthFoodAndThirst() {
        RespawnModule m = module(new RespawnRules(true, true));
        alex.getWorld().setDifficulty(Difficulty.PEACEFUL);
        alex.getWorld().setGameRule(RespawnModule.NATURAL_REGENERATION, false); // as in TFC: our step is the only refill
        alex.setHealth(10);
        alex.setFoodLevel(15);
        now[0] = 100L;
        assertThat(m.peacefulStep(alex, true)).isEqualTo(new RespawnRules.PeacefulStep(1f, 1, 5f));
        assertThat(alex.getHealth()).isEqualTo(11.0);
        assertThat(alex.getFoodLevel()).isEqualTo(16);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(17f);

        m.peacefulStep(alex, false);
        assertThat(alex.getHealth()).as("heal only every 20 ticks").isEqualTo(11.0);
        assertThat(alex.getFoodLevel()).isEqualTo(17);
    }

    @Test
    void extraPeacefulLeavesTheFoodToVanillaWhileNaturalRegenerationIsOn() {
        assertThat(RespawnModule.NATURAL_REGENERATION).isNotNull();
        RespawnModule m = module(new RespawnRules(true, true));
        alex.getWorld().setDifficulty(Difficulty.PEACEFUL);
        alex.setFoodLevel(15); // full health: no heal (MockBukkit does not implement Player.heal)
        now[0] = 100L;

        alex.getWorld().setGameRule(RespawnModule.NATURAL_REGENERATION, true);
        assertThat(m.peacefulStep(alex, false)).as("vanilla's peaceful refill already adds TFC's +1")
                .isEqualTo(new RespawnRules.PeacefulStep(0f, 0, 5f));
        assertThat(alex.getFoodLevel()).isEqualTo(15);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(17f);

        alex.getWorld().setGameRule(RespawnModule.NATURAL_REGENERATION, false);
        assertThat(m.peacefulStep(alex, false)).as("as in TFC, the step is the only refill")
                .isEqualTo(new RespawnRules.PeacefulStep(0f, 1, 5f));
        assertThat(alex.getFoodLevel()).isEqualTo(16);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(22f);
    }

    @Test
    void extraPeacefulIsOffByDefaultOutsidePeacefulAndInCreative() {
        RespawnModule off = module(RespawnRules.DEFAULT);
        alex.getWorld().setDifficulty(Difficulty.PEACEFUL);
        alex.setHealth(10);
        assertThat(off.peacefulStep(alex, true).isNone()).isTrue();

        RespawnModule on = new RespawnModule(new RespawnRules(true, true), store, () -> now[0], hud);
        alex.getWorld().setDifficulty(Difficulty.NORMAL);
        assertThat(on.peacefulStep(alex, true).isNone()).isTrue();
        alex.getWorld().setDifficulty(Difficulty.PEACEFUL);
        alex.setGameMode(GameMode.CREATIVE);
        assertThat(on.peacefulStep(alex, true).isNone()).isTrue();
        assertThat(alex.getHealth()).isEqualTo(10.0);
    }
}
