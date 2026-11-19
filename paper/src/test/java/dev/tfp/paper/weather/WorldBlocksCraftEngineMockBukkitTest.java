/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-04: {@link WorldBlocks} writes and reads the four weather blocks through the CraftEngine adapter. */
class WorldBlocksCraftEngineMockBukkitTest extends MockServerTest {
    private final List<BlockStateRef> stateChanges = new ArrayList<>();
    private FakeCustomBlocks custom;
    private World world;
    private WorldBlocks blocks;

    @AfterEach
    void restoreParser() {
        WorldBlocks.parser = Bukkit::createBlockData;
    }

    @BeforeEach
    void setUpWorld() {
        WorldBlocks.parser = WorldBlocksCraftEngineMockBukkitTest::state;
        world = server.addSimpleWorld("tfp_snow_ce");
        world.getChunkAt(0, 0);
        custom = new FakeCustomBlocks().known(WorldBlocks.SNOW_PILE, WorldBlocks.ICE_PILE, WorldBlocks.SEA_ICE,
                WorldBlocks.ICICLE);
        BlockVisuals visuals = new BlockVisuals() {
            @Override
            public boolean isLoaded() {
                return true;
            }

            @Override
            public Optional<BlockData> blockData(BlockStateRef state) {
                stateChanges.add(state);
                return Optional.of(Material.OAK_LEAVES.createBlockData());
            }

            @Override
            public long generation() {
                return 1;
            }

            @Override
            public void onReload(Runnable listener) {
            }
        };
        blocks = new WorldBlocks(world, () -> new WorldBlocks.Custom(custom, () -> visuals));
    }

    private Block at(BlockPos pos) {
        return world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    /** Block data of a state string that MockBukkit cannot build: only the material and the state string are read. */
    private static BlockData state(String text) {
        Material material = Material.matchMaterial(text.substring(0, text.contains("[") ? text.indexOf('[') : text.length()));
        return (BlockData) java.lang.reflect.Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[] {BlockData.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAsString" -> text;
                    case "getMaterial" -> material;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> text.hashCode();
                    case "toString" -> text;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static BlockData snow(int layers) {
        return state("minecraft:snow[layers=" + layers + "]");
    }

    @Test
    void aPileOverAFernIsPlacedAsTheCraftEngineBlockAndMeltsBackToTheFern() {
        BlockPos pos = new BlockPos(3, 70, 5);
        BlockPos above = pos.offset(0, 1, 0);
        BlockData fern = Material.FERN.createBlockData();
        BlockData grass = Material.SHORT_GRASS.createBlockData();
        at(pos).setBlockData(fern, false);
        at(above).setBlockData(grass, false);

        blocks.write(pos, new SnowBlock(snow(1), new SnowBlock.Pile(fern, grass), null));

        assertThat(custom.stateAt(at(pos))).contains(BlockStateRef.of("tfp:snow_pile", Map.of("layers", "1")));
        assertThat(at(above).getType()).isEqualTo(Material.AIR); // the pile absorbed the top half
        SnowBlock read = blocks.read(pos);
        assertThat(read.data().getAsString()).isEqualTo(snow(1).getAsString());
        assertThat(read.pile().ground().getAsString()).isEqualTo(fern.getAsString());
        assertThat(read.pile().above().getAsString()).isEqualTo(grass.getAsString());

        // melt: the covered block and the absorbed top half come back, the CraftEngine block and its memory are gone
        blocks.write(pos, new SnowBlock(fern, null, grass));
        assertThat(custom.stateAt(at(pos))).isEmpty();
        assertThat(at(pos).getBlockData().getAsString()).isEqualTo(fern.getAsString());
        assertThat(at(above).getBlockData().getAsString()).isEqualTo(grass.getAsString());
        assertThat(blocks.read(pos).data().getAsString()).isEqualTo(fern.getAsString());
        assertThat(blocks.read(pos).pile()).isNull();
        assertThat(world.getChunkAt(0, 0).getPersistentDataContainer().getKeys()).isEmpty();
    }

    @Test
    void aPileWithMoreLayersChangesTheStateOfTheSameBlock() {
        BlockPos pos = new BlockPos(1, 70, 1);
        BlockData fern = Material.FERN.createBlockData();
        at(pos).setBlockData(fern, false);
        blocks.write(pos, new SnowBlock(snow(1), new SnowBlock.Pile(fern, null), null));
        stateChanges.clear();

        blocks.write(pos, new SnowBlock(snow(3), new SnowBlock.Pile(fern, null), null));

        assertThat(stateChanges).containsExactly(BlockStateRef.of("tfp:snow_pile", Map.of("layers", "3")));
        // the fake keeps the old state because the real server state is what the adapter changes; the pile memory stays
        assertThat(blocks.read(pos).pile().ground().getAsString()).isEqualTo(fern.getAsString());
    }

    @Test
    void anIcePileSeaIceAndIciclesMapToTheirBlocks() {
        BlockPos ice = new BlockPos(2, 70, 2);
        BlockData water = Material.WATER.createBlockData();
        blocks.write(ice, new SnowBlock(Material.ICE.createBlockData(), new SnowBlock.Pile(water, null), null));
        assertThat(custom.stateAt(at(ice))).contains(BlockStateRef.of("tfp:ice_pile"));
        SnowBlock readIce = blocks.read(ice);
        assertThat(readIce.data().getMaterial()).isEqualTo(Material.ICE);
        assertThat(readIce.pile().ground().getMaterial()).isEqualTo(Material.WATER);

        // plain lake ice has no pile and stays vanilla ice; blue ice is the sea ice placeholder
        BlockPos lake = new BlockPos(4, 70, 4);
        blocks.write(lake, SnowBlock.of(Material.ICE.createBlockData()));
        assertThat(custom.stateAt(at(lake))).isEmpty();
        assertThat(at(lake).getType()).isEqualTo(Material.ICE);
        BlockPos sea = new BlockPos(6, 70, 6);
        blocks.write(sea, SnowBlock.of(Material.BLUE_ICE.createBlockData()));
        assertThat(custom.stateAt(at(sea))).contains(BlockStateRef.of("tfp:sea_ice"));
        assertThat(blocks.read(sea).data().getMaterial()).isEqualTo(Material.BLUE_ICE);

        BlockData tip = state(SnowService.ICICLE_TIP);
        BlockData pillar = state("minecraft:pointed_dripstone[thickness=frustum,vertical_direction=down]");
        BlockPos pos = new BlockPos(8, 70, 8);
        blocks.write(pos, SnowBlock.of(tip));
        assertThat(custom.stateAt(at(pos))).contains(BlockStateRef.of("tfp:icicle", Map.of("tip", "true")));
        assertThat(SnowService.isDownIcicle(blocks.read(pos).data())).isTrue();
        assertThat(blocks.read(pos).data().getAsString()).contains("thickness=tip");
        blocks.write(pos.offset(0, 1, 0), SnowBlock.of(pillar));
        assertThat(custom.stateAt(at(pos.offset(0, 1, 0)))).contains(BlockStateRef.of("tfp:icicle", Map.of("tip", "false")));
        assertThat(blocks.read(pos.offset(0, 1, 0)).data().getAsString()).contains("thickness=frustum");

        // an icicle taken away by the weather leaves air and no CraftEngine block
        blocks.write(pos, SnowBlock.of(Material.AIR.createBlockData()));
        assertThat(custom.stateAt(at(pos))).isEmpty();
    }

    @Test
    void withoutCraftEngineTheVanillaPlaceholdersAreWritten() {
        WorldBlocks plain = new WorldBlocks(world, () -> null);
        BlockPos pos = new BlockPos(9, 70, 9);
        BlockData fern = Material.FERN.createBlockData();
        at(pos).setBlockData(fern, false);
        plain.write(pos, new SnowBlock(Material.SNOW.createBlockData(), new SnowBlock.Pile(fern, null), null));
        assertThat(at(pos).getType()).isEqualTo(Material.SNOW);
        assertThat(plain.read(pos).pile().ground().getAsString()).isEqualTo(fern.getAsString());
    }

    @Test
    void aBlockTheCraftEngineAdapterDoesNotKnowFallsBackToThePlaceholder() {
        FakeCustomBlocks none = new FakeCustomBlocks();
        WorldBlocks missing = new WorldBlocks(world, () -> new WorldBlocks.Custom(none, () -> null));
        BlockPos pos = new BlockPos(10, 70, 10);
        missing.write(pos, SnowBlock.of(Material.BLUE_ICE.createBlockData()));
        assertThat(at(pos).getType()).isEqualTo(Material.BLUE_ICE);
        assertThat(none.placed()).isEmpty();
    }
}
