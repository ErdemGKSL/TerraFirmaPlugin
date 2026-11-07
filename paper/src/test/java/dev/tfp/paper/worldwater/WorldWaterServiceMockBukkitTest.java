/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.groundcover.SaltWater;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.ChunkWater;
import dev.tfp.core.worldwater.WaterKind;
import dev.tfp.core.worldwater.WaterTerrain;
import dev.tfp.core.worldwater.WorldWaterRules;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import java.util.logging.Logger;
import dev.tfp.paper.boot.TfpCommands.Reply;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-FLUID-03: salt classification by biome, and the TFC water of a world of our generator. */
class WorldWaterServiceMockBukkitTest extends MockServerTest {
    private static final long SEED = 1234L;
    private static final int Y = WorldWaterRules.SEA_LEVEL_Y - 1;

    private static WorldWaterService inline() {
        return new WorldWaterService(Runnable::run, Logger.getLogger("test"), w -> Optional.empty());
    }

    private Block water(World world, int x, int z, Biome biome) {
        Block block = world.getBlockAt(x, Y, z);
        block.setType(Material.WATER);
        block.setBiome(biome);
        return block;
    }

    @Test
    void outsideTfcWorldsTheVanillaBiomeDecidesSalt() {
        WorldMock world = server.addSimpleWorld("plain");
        WorldWaterService service = inline();
        service.register(world);
        assertThat(service.isTfcWorld(world)).isFalse();
        assertThat(service.kindOf(water(world, 0, 0, Biome.OCEAN))).isEqualTo(WaterKind.SALT);
        assertThat(service.kindOf(water(world, 1, 0, Biome.DEEP_COLD_OCEAN))).isEqualTo(WaterKind.SALT);
        assertThat(service.kindOf(water(world, 2, 0, Biome.BEACH))).isEqualTo(WaterKind.SALT);
        assertThat(service.kindOf(water(world, 3, 0, Biome.PLAINS))).isEqualTo(WaterKind.FRESH);
        assertThat(service.kindOf(water(world, 4, 0, Biome.RIVER))).isEqualTo(WaterKind.FRESH);
        assertThat(service.riverFlow(world, 4, Y, 0)).contains(Flow.NONE);
        assertThat(service.cachedChunks()).isZero();
    }

    @Test
    void inATfcWorldTheTfcBiomeAndRiverDecide() {
        WorldMock world = server.addSimpleWorld("tfc");
        WaterTerrain terrain = new WaterTerrain(SEED);
        WorldWaterService service = inline();
        service.registerTerrain(world.getUID(), Optional.of(terrain));
        assertThat(service.isTfcWorld(world)).isTrue();

        int[] salt = null;
        int[] river = null;
        Flow riverFlow = null;
        for (int cx = -24; cx <= 24 && (salt == null || river == null); cx += 4) {
            for (int cz = -24; cz <= 24; cz += 4) {
                ChunkWater chunk = terrain.chunk(cx, cz);
                for (int i = 0; i < 256; i++) {
                    int x = i & 15;
                    int z = i >> 4;
                    if (salt == null && SaltWater.isSalty(chunk.biome(x, z))) {
                        salt = new int[] {(cx << 4) + x, (cz << 4) + z};
                    }
                    if (river == null && chunk.kind(x, Y, z) == WaterKind.RIVER) {
                        river = new int[] {(cx << 4) + x, (cz << 4) + z};
                        riverFlow = chunk.flow(x, z);
                    }
                }
            }
        }
        assertThat(salt).isNotNull();
        assertThat(river).isNotNull();

        // the vanilla biome of the block does not matter in a TFC world: the TFC biome of the seed decides
        Block saltBlock = water(world, salt[0], salt[1], Biome.PLAINS);
        assertThat(service.knownKind(world, salt[0], Y, salt[1], Biome.PLAINS)).as("first read queues the chunk")
                .isEmpty();
        assertThat(service.kindOf(saltBlock)).isEqualTo(WaterKind.SALT);
        assertThat(service.riverFlow(world, salt[0], Y, salt[1])).contains(Flow.NONE);

        Block riverBlock = water(world, river[0], river[1], Biome.OCEAN);
        service.chunk(world, river[0] >> 4, river[1] >> 4);
        assertThat(service.kindOf(riverBlock)).isEqualTo(WaterKind.RIVER);
        assertThat(service.riverFlow(world, river[0], Y, river[1])).contains(riverFlow);
        assertThat(riverFlow).isNotEqualTo(Flow.NONE);

        service.unregister(world);
        assertThat(service.isTfcWorld(world)).isFalse();
        assertThat(service.cachedChunks()).isZero();
    }

    @Test
    void anUncachedTfcChunkAnswersFromTheTfcBiomeWithoutFlow() {
        WorldMock world = server.addSimpleWorld("pending");
        WaterTerrain terrain = new WaterTerrain(SEED);
        // a CPU pool that never runs: nothing gets cached
        WorldWaterService service = new WorldWaterService(r -> { }, Logger.getLogger("test"), w -> Optional.empty());
        service.registerTerrain(world.getUID(), Optional.of(terrain));
        Block block = water(world, 0, 0, Biome.OCEAN);
        WaterKind expected = SaltWater.isSalty(terrain.biomeAt(0, 0)) ? WaterKind.SALT : WaterKind.FRESH;
        assertThat(service.kindOf(block)).isEqualTo(expected);
        assertThat(service.riverFlow(world, 0, Y, 0)).isEmpty();
    }

    @Test
    void debugDescribesTheWaterAtThePlayer() {
        WorldMock world = server.addSimpleWorld("dbg");
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, 0.5, Y, 0.5));
        water(world, 0, 0, Biome.OCEAN);
        WorldWaterService service = inline();
        Reply vanilla = WorldWaterModule.describe(player, service);
        assertThat(vanilla.key()).isEqualTo("debug.water.vanilla");
        assertThat(vanilla.args()).contains("salt");
        service.registerTerrain(world.getUID(), Optional.of(new WaterTerrain(SEED)));
        assertThat(WorldWaterModule.describe(player, service).key()).as("first read queues the chunk")
                .isEqualTo("debug.water.pending");
        Reply done = WorldWaterModule.describe(player, service);
        assertThat(done.ok()).isTrue();
        assertThat(done.key()).isEqualTo("debug.water.done");
        assertThat(done.args()).hasSize(8);
    }
}
