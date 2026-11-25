/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.worldgen.chunk.ChunkData;
import dev.tfp.core.worldgen.volcano.VolcanicFeatures;
import dev.tfp.core.worldgen.volcano.VolcanicKind;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import java.util.EnumSet;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * PAR-WORLD-68: the real terrain fill over the chunks that hold volcanic blocks never throws, whatever CraftEngine knows (a
 * thrown fill clears the chunk and refuses it, so a headless client cannot join the world).
 */
class VolcanoChunkFillTest {
    private static final long SEED = -5793902121713522665L;

    @BeforeAll
    static void mock() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmock() {
        MockBukkit.unmock();
    }

    private static BlockVisuals visuals(Predicate<String> knows) {
        return new BlockVisuals() {
            @Override
            public boolean isLoaded() {
                return true;
            }

            @Override
            public Optional<BlockData> blockData(BlockStateRef state) {
                return knows.test(state.toString()) ? Optional.of(Material.STONE.createBlockData()) : Optional.empty();
            }

            @Override
            public long generation() {
                return 1;
            }

            @Override
            public void onReload(Runnable listener) {}
        };
    }

    /** A chunk of the real world height (-64 to 320), which throws outside of it like the server's chunk data. */
    private static ChunkGenerator.ChunkData chunkData() {
        final Map<Long, BlockData> blocks = new HashMap<>();
        final BlockData air = Material.AIR.createBlockData();
        return (ChunkGenerator.ChunkData) Proxy.newProxyInstance(VolcanoChunkFillTest.class.getClassLoader(),
            new Class<?>[] {ChunkGenerator.ChunkData.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getMinHeight":
                        return -64;
                    case "getMaxHeight":
                        return 320;
                    case "setBlock", "getType", "getBlockData": {
                        final int x = (Integer) args[0];
                        final int y = (Integer) args[1];
                        final int z = (Integer) args[2];
                        if (x < 0 || x > 15 || z < 0 || z > 15 || y < -64 || y >= 320) {
                            throw new IllegalArgumentException("Coordinates are out-of-bounds: " + x + " " + y + " " + z);
                        }
                        final long key = ((long) y << 8) | (x << 4) | z;
                        if (method.getName().equals("setBlock")) {
                            blocks.put(key, args[3] instanceof BlockData d ? d : ((Material) args[3]).createBlockData());
                            return null;
                        }
                        final BlockData at = blocks.getOrDefault(key, air);
                        return method.getName().equals("getType") ? at.getMaterial() : at;
                    }
                    default:
                        throw new UnsupportedOperationException(method.getName());
                }
            });
    }

    /** Fills every chunk of the square that holds a volcanic block (found with the terrain's own view). */
    private static void scan(Predicate<String> knows, int radius) throws ReflectiveOperationException {
        final TfcTerrain terrain;
        try {
            terrain = new TfcTerrain(SEED);
        } catch (IllegalArgumentException e) {
            Assumptions.abort("no generated content (run tools/datagen): " + e.getMessage());
            return;
        }
        final var palette = terrain.palette(visuals(knows));
        assertThat(palette.resolved()).as("%s", palette.refusal()).isTrue();
        final var field = TfcTerrain.class.getDeclaredField("volcanicFeatures");
        field.setAccessible(true);
        final VolcanicFeatures features = (VolcanicFeatures) field.get(terrain);
        final Set<VolcanicKind> kinds = EnumSet.noneOf(VolcanicKind.class);
        int filled = 0;
        for (int cx = -radius; cx < radius; cx++) {
            for (int cz = -radius; cz < radius; cz++) {
                final TfcTerrain.Columns columns = terrain.columns(cx, cz);
                final ChunkData climate = terrain.chunkData(cx, cz).modifyBaseGroundwater(columns.heights());
                final TfcTerrain.Ground view = terrain.new Ground(cx, cz, columns, climate, -64, 320);
                final int[] count = {0};
                features.place(cx, cz, view, b -> {
                    count[0]++;
                    kinds.add(b.kind());
                });
                if (count[0] == 0) {
                    continue;
                }
                filled++;
                final ChunkGenerator.ChunkData data = chunkData();
                terrain.fill(data, cx, cz, palette);
            }
        }
        System.out.println("volcano chunks filled: " + filled + ", kinds: " + kinds);
        assertThat(filled).as("chunks with volcanic blocks").isPositive();
    }

    @Test
    void fillsVolcanoChunksWhileCraftEngineKnowsEverything() throws ReflectiveOperationException {
        scan(id -> true, 40);
    }

    @Test
    void fillsVolcanoChunksWithoutTheMagmaAndGroundcoverBlocks() throws ReflectiveOperationException {
        scan(id -> !id.startsWith("tfp:rock/magma/") && !id.startsWith("tfp:groundcover/"), 40);
    }
}
