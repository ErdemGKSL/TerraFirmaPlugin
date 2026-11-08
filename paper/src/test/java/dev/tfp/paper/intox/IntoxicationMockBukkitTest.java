/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.intox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.intox.ChatSlur;
import dev.tfp.core.intox.Intoxicants;
import dev.tfp.core.intox.Intoxication;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.tag.TagRegistry;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.player.PlayerDataFiles;
import dev.tfp.paper.player.PlayerDataStore;
import dev.tfp.paper.scheduler.ServerScheduler;
import dev.tfp.paper.testing.MockServerTest;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * PAR-SURV-08: drinking adds TFC's intoxication into the player data, it wears off lazily with the clock, drunk chat
 * through the real {@link AsyncChatEvent} handler, a respawn after death is sober, and the debug readout.
 */
class IntoxicationMockBukkitTest extends MockServerTest {
    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1c0");
    static final Intoxicants BEER = new Intoxicants(List.of(
            new Intoxicants.Entry(new FluidIngredient.Single("minecraft:water"), 0),
            new Intoxicants.Entry(new FluidIngredient.Single("tfp:beer"), 4000)), TagRegistry.EMPTY);

    @TempDir
    Path dir;
    final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
    final long[] now = {1_000_000L};
    PlayerDataStore store;
    PlayerMock alex;
    IntoxicationService service;
    IntoxicationModule module;

    @BeforeEach
    void setUpPlayer() {
        store = new PlayerDataStore(new PlayerDataFiles(dir), io, Logger.getLogger("test"));
        store.loadBlocking(ALEX);
        alex = new PlayerMock(server, "Alex", ALEX);
        server.addPlayer(alex);
        alex.setGameMode(GameMode.SURVIVAL);
        service = new IntoxicationService(store, () -> now[0], () -> BEER, Intoxication.DEFAULT_MAX_TICKS);
        module = new IntoxicationModule(service, new ServerScheduler(plugin, 1), alwaysHit());
        server.getPluginManager().registerEvents(module, plugin);
    }

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    /** A random whose floats always hit and whose ints are 0: every change at the first letter. */
    static ChatSlur.Random alwaysHit() {
        return new ChatSlur.Random() {
            @Override
            public float nextFloat() {
                return 0f;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    private String chat(String message) {
        AsyncChatEvent event = new AsyncChatEvent(true, alex, Set.of(), ChatRenderer.defaultRenderer(),
                Component.text(message), Component.text(message), SignedMessage.system(message, null));
        module.onChat(event);
        return PlainTextComponentSerializer.plainText().serialize(event.message());
    }

    @Test
    void aJugOfBeerAddsFourThousandTicksAndWearsOffLazily() {
        assertThat(store.get(ALEX).orElseThrow().intoxicationTick()).isEqualTo(Intoxication.SOBER);
        assertThat(service.drink(ALEX, "tfp:beer")).isEqualTo(4000);
        assertThat(store.get(ALEX).orElseThrow().intoxicationTick()).isEqualTo(now[0] + 4000);
        assertThat(service.level(ALEX)).isEqualTo(4000f / 43200f);
        now[0] += 1000;
        assertThat(service.remaining(ALEX)).isEqualTo(3000);
        assertThat(service.drink(ALEX, "tfp:beer")).isEqualTo(4000);
        assertThat(service.remaining(ALEX)).as("added to what is left").isEqualTo(7000);
        now[0] += 7000;
        assertThat(service.level(ALEX)).isZero();
        assertThat(service.remaining(ALEX)).isZero();
        for (int i = 0; i < 20; i++) {
            service.drink(ALEX, "tfp:beer");
        }
        assertThat(service.remaining(ALEX)).as("TFC caps at 36 hours").isEqualTo(Intoxication.DEFAULT_MAX_TICKS);
        assertThat(service.level(ALEX)).isEqualTo(1f);
    }

    @Test
    void waterAndUnknownFluidsWriteNothing() {
        assertThat(service.drink(ALEX, "minecraft:water")).isZero();
        assertThat(service.drink(ALEX, "minecraft:lava")).isZero();
        assertThat(store.get(ALEX).orElseThrow().intoxicationTick()).isEqualTo(Intoxication.SOBER);
    }

    @Test
    void chatIsSlurredOnlyAboveTwentyPercent() {
        service.add(ALEX, 8640); // exactly 0.2
        assertThat(chat("hello there")).isEqualTo("hello there");
        service.add(ALEX, 1);
        // Every change hits at position 0: swap "he" -> "eh", repeat 'e' once (no change), replace it with 'a'.
        assertThat(chat("hello there")).isEqualTo(ChatSlur.slur("hello there", service.level(ALEX), alwaysHit()))
                .isEqualTo("ahllo atere");
        now[0] += 10_000;
        assertThat(chat("hello there")).as("worn off below 0.2").isEqualTo("hello there");
    }

    @Test
    void creativeChatIsSlurredAsInTfc() {
        alex.setGameMode(GameMode.CREATIVE);
        service.add(ALEX, Intoxication.DEFAULT_MAX_TICKS);
        assertThat(chat("hi")).isNotEqualTo("hi");
    }

    @Test
    void aRespawnAfterDeathIsSoberButLeavingTheEndIsNot() {
        service.add(ALEX, 20_000);
        server.getPluginManager().callEvent(new PlayerRespawnEvent(alex, alex.getLocation(), false, false, false,
                PlayerRespawnEvent.RespawnReason.END_PORTAL));
        assertThat(service.remaining(ALEX)).isEqualTo(20_000);
        server.getPluginManager().callEvent(new PlayerRespawnEvent(alex, alex.getLocation(), false, false, false,
                PlayerRespawnEvent.RespawnReason.DEATH));
        assertThat(store.get(ALEX).orElseThrow().intoxicationTick()).isEqualTo(Intoxication.SOBER);
    }

    @Test
    void aPlayerWithoutDataIsSoberAndAddsNothing() {
        UUID ghost = UUID.randomUUID();
        assertThat(service.level(ghost)).isZero();
        assertThat(service.add(ghost, 4000)).isEqualTo(Intoxication.SOBER);
        service.clear(ghost);
    }

    @Test
    void theDebugReadoutShowsLevelTicksAndSlurChance() {
        service.add(ALEX, 21_600);
        Reply r = module.debug(alex, p -> null).join();
        assertThat(r.ok()).isTrue();
        assertThat(r.key()).isEqualTo("debug.intox.state");
        assertThat(r.args()).containsExactly("Alex", "0.500", 21_600L, 43_200,
                String.format(java.util.Locale.ROOT, "%.3f", Intoxication.slurChance(0.5f)));
        Reply notAlcohol = module.debug(alex, p -> service.drink(p.getUniqueId(), "minecraft:water") == 0
                ? Reply.error("debug.intox.not_alcohol", "minecraft:water") : null).join();
        assertThat(notAlcohol.ok()).isFalse();
    }
}
