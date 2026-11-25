/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.command;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.command.PlayerCommand;
import dev.tfp.core.command.PlayerCommand.Request;
import dev.tfp.core.command.PlayerCommand.Stat;
import dev.tfp.core.command.PlayerCommand.Verb;
import dev.tfp.core.food.NutritionConfig;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.player.PlayerData;
import dev.tfp.paper.player.PlayerDataFiles;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.testing.MockServerTest;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-DATA-01: {@code /tfp player} on a MockBukkit player with stored data. */
class PlayerCommandsMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1c1");

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    final long[] now = {500_000L};
    PlayerDataStore store;
    PlayerMock alex;
    PlayerCommands commands;

    @BeforeEach
    void setUpPlayer() {
        store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        alex.setGameMode(GameMode.SURVIVAL);
        commands = new PlayerCommands(new QueueScheduler(), store, () -> now[0], id -> 20.0);
        // the stored clock is stamped now, so no decay is caught up
        store.update(ALEX, d -> d.withThirstClock(80f, now[0], 0L));
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    private Reply run(Verb verb, Stat stat, int value) {
        return commands.run(alex, new Request(verb, stat, value)).join();
    }

    @Test
    void queriesAnswerWithTfcsValues() {
        alex.setFoodLevel(14);
        alex.setSaturation(3.5f);
        assertThat(run(Verb.QUERY, Stat.HUNGER, 0)).isEqualTo(Reply.ok("command.data.player.query_hunger", 14));
        assertThat(run(Verb.QUERY, Stat.SATURATION, 0)).isEqualTo(Reply.ok("command.data.player.query_saturation", "3.5"));
        assertThat(run(Verb.QUERY, Stat.WATER, 0)).isEqualTo(Reply.ok("command.data.player.query_water", "80.0"));
    }

    @Test
    void nutritionQueryIsTheCoresPercentOfEachNutrient() {
        PlayerData stored = store.get(ALEX).orElseThrow();
        int[] expected = PlayerCommand.nutritionPercents(PlayerCommands.nutritionOf(stored)
                .levels(alex.getFoodLevel(), NutritionConfig.DEFAULT).values());
        Reply reply = run(Verb.QUERY, Stat.NUTRITION, 0);
        assertThat(reply.key()).isEqualTo("command.data.player.query_nutrition");
        assertThat(reply.args()).containsExactly(expected[0], expected[1], expected[2], expected[3], expected[4]);
    }

    @Test
    void setResetAndAddHunger() {
        alex.setFoodLevel(10);
        run(Verb.SET, Stat.HUNGER, 7);
        assertThat(alex.getFoodLevel()).isEqualTo(7);
        run(Verb.ADD, Stat.HUNGER, -3);
        assertThat(alex.getFoodLevel()).isEqualTo(4);
        run(Verb.ADD, Stat.HUNGER, 20);
        assertThat(alex.getFoodLevel()).as("clamped to 20 (ledger divergence)").isEqualTo(20);
        run(Verb.ADD, Stat.HUNGER, -20);
        run(Verb.ADD, Stat.HUNGER, -20);
        assertThat(alex.getFoodLevel()).isZero();
        run(Verb.RESET, Stat.HUNGER, PlayerCommand.RESET_HUNGER);
        assertThat(alex.getFoodLevel()).isEqualTo(20);
    }

    @Test
    void setResetAndAddSaturation() {
        alex.setSaturation(2f);
        run(Verb.SET, Stat.SATURATION, 9);
        assertThat(alex.getSaturation()).isEqualTo(9f);
        run(Verb.ADD, Stat.SATURATION, -12);
        assertThat(alex.getSaturation()).as("never below zero").isZero();
        run(Verb.RESET, Stat.SATURATION, PlayerCommand.RESET_SATURATION);
        assertThat(alex.getSaturation()).isEqualTo(5f);
    }

    @Test
    void waterIsStoredAndTheClockStartsAtNow() {
        run(Verb.SET, Stat.WATER, 30);
        PlayerData d = store.get(ALEX).orElseThrow();
        assertThat(d.thirst()).isEqualTo(30f);
        run(Verb.ADD, Stat.WATER, 50);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(80f);
        run(Verb.ADD, Stat.WATER, 100);
        assertThat(store.get(ALEX).orElseThrow().thirst()).as("capped at the maximum").isEqualTo(PlayerData.MAX_THIRST);
        run(Verb.SET, Stat.WATER, 0);
        run(Verb.RESET, Stat.WATER, PlayerCommand.RESET_WATER);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(100f);
    }

    @Test
    void waterQueryBringsTheLazyClockToNow() {
        now[0] += 24_000L * 2; // two days of exhaustion-free ticks: only passive loss, never below 0
        Reply reply = run(Verb.QUERY, Stat.WATER, 0);
        float shown = Float.parseFloat((String) reply.args().getFirst());
        assertThat(shown).isBetween(0f, 80f);
        assertThat(store.get(ALEX).orElseThrow().thirst()).isEqualTo(shown);
    }

    @Test
    void creativeModeKeepsTheStoredWaterWhatItWas() {
        alex.setGameMode(GameMode.CREATIVE);
        now[0] += 24_000L * 10;
        assertThat(run(Verb.QUERY, Stat.WATER, 0)).isEqualTo(Reply.ok("command.data.player.query_water", "80.0"));
    }

    @Test
    void aPlayerWithoutLoadedDataIsRefusedAsTfcDoes() {
        PlayerMock bob = new PlayerMock(server, "Bob", UUID.fromString("00000000-0000-0000-0000-00000000b0b0"));
        server.addPlayer(bob);
        store.unload(bob.getUniqueId()).join();
        assertThat(commands.run(bob, new Request(Verb.QUERY, Stat.HUNGER, 0)).join())
                .isEqualTo(Reply.error("command.data.player.no_data"));
        assertThat(List.of(bob.getFoodLevel())).hasSize(1);
    }
}
