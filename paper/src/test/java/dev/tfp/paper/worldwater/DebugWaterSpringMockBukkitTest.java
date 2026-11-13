/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.ChunkWater;
import dev.tfp.core.worldwater.WaterKind;
import dev.tfp.core.worldwater.WaterTerrain;
import dev.tfp.paper.boot.TfpCommands.Reply;
import dev.tfp.paper.testing.MockServerTest;
import dev.tfp.paper.worldwater.hot.SpringWater;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-FLUID-14: {@code /tfp debug water} names the water of a hot spring pool spring water. */
class DebugWaterSpringMockBukkitTest extends MockServerTest {
    private static final long SEED = 1234L;
    private static final int POOL_Y = 70;
    /** Index of the water kind in the {@code debug.water.done} arguments. */
    private static final int KIND_ARG = 4;

    private World world;
    private PlayerMock player;
    private WorldWaterService service;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("springs");
        player = server.addPlayer("Bot");
        service = new WorldWaterService(Runnable::run, Logger.getLogger("test"), w -> Optional.empty());
        service.registerTerrain(world.getUID(), Optional.of(new WaterTerrain(SEED)));
    }

    private static SpringWater springs(int biome) {
        return new SpringWater((w, cx, cz) -> Optional.of(ChunkWater.uniform(cx, cz, biome, Flow.NONE, POOL_Y)));
    }

    /** A 5x5 pool one block deep at {@link #POOL_Y} (above sea level) on {@code bed}, the player standing in its middle. */
    private void poolOn(Material bed) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, POOL_Y - 1, z).setType(bed);
                world.getBlockAt(x, POOL_Y, z).setType(Material.WATER);
                world.getBlockAt(x, POOL_Y + 1, z).setType(Material.AIR);
            }
        }
        player.teleport(new Location(world, 0.5, POOL_Y, 0.5));
    }

    /** The debug answer once the chunk water is cached (the first read only queues it). */
    private Reply describe(SpringWater springs) {
        WorldWaterModule.describe(player, service, springs);
        Reply reply = WorldWaterModule.describe(player, service, springs);
        assertThat(reply.ok()).isTrue();
        assertThat(reply.key()).isEqualTo("debug.water.done");
        return reply;
    }

    private String chunkKind() {
        WaterKind kind = service.chunk(world, 0, 0).orElseThrow().kind(0, POOL_Y, 0);
        return kind.name().toLowerCase(Locale.ROOT);
    }

    @Test
    void aGravelPoolInAnActiveSpringChunkIsSpringWater() {
        poolOn(Material.GRAVEL);
        Reply reply = describe(springs(TfcBiomes.OLD_MOUNTAINS));
        assertThat(reply.args().get(KIND_ARG)).isEqualTo("spring");
        assertThat(reply.args()).hasSize(8);
    }

    @Test
    void stoneBeddedWaterKeepsTheChunkAnswer() {
        poolOn(Material.STONE);
        Reply reply = describe(springs(TfcBiomes.OLD_MOUNTAINS));
        assertThat(reply.args().get(KIND_ARG)).isEqualTo(chunkKind()).isNotEqualTo("spring");
    }

    @Test
    void aGravelPoolAwayFromSpringBiomesKeepsTheChunkAnswer() {
        poolOn(Material.GRAVEL);
        Reply reply = describe(springs(TfcBiomes.PLAINS));
        assertThat(reply.args().get(KIND_ARG)).isEqualTo(chunkKind()).isNotEqualTo("spring");
    }

    @Test
    void withoutTheHotSpringServiceTheAnswerIsUnchanged() {
        poolOn(Material.GRAVEL);
        Reply reply = describe(null);
        assertThat(reply.args().get(KIND_ARG)).isEqualTo(chunkKind()).isNotEqualTo("spring");
        assertThat(WorldWaterModule.describe(player, service).args()).isEqualTo(reply.args());
    }
}
