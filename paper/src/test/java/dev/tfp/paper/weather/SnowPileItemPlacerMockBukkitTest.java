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
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockBreakEvent;
import dev.tfp.paper.scheduler.Scheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-13: the snow item over a plant places a pile that remembers it, and breaking the pile gives it back. */
class SnowPileItemPlacerMockBukkitTest extends MockServerTest {
    private FakeCustomBlocks custom;
    private World world;
    private WorldBlocks access;
    private SnowPileItemPlacer placer;
    private WeatherBlocksModule.PileBreaks breaks;
    private final java.util.List<BlockStateRef> asked = new java.util.ArrayList<>();
    private final java.util.Set<Material> pileable = java.util.EnumSet.of(Material.FERN, Material.SHORT_GRASS);

    private static BlockData state(String text) {
        return (BlockData) java.lang.reflect.Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[] {BlockData.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getAsString" -> text;
                    case "getMaterial" -> Material.matchMaterial(text.substring(0, text.indexOf('[') < 0 ? text.length()
                            : text.indexOf('[')));
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> text.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @BeforeEach
    void setUpWorld() {
        WorldBlocks.parser = SnowPileItemPlacerMockBukkitTest::state;
        world = server.addSimpleWorld("tfp_snow_item");
        world.getChunkAt(0, 0);
        custom = new FakeCustomBlocks().known(WorldBlocks.SNOW_PILE, WorldBlocks.ICE_PILE);
        BlockVisuals visuals = new BlockVisuals() {
            @Override
            public boolean isLoaded() {
                return true;
            }

            @Override
            public Optional<BlockData> blockData(BlockStateRef ref) {
                asked.add(ref);
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
        access = new WorldBlocks(world, () -> new WorldBlocks.Custom(custom, () -> visuals));
        placer = new SnowPileItemPlacer(w -> access, b -> pileable.contains(b.getType()));
        Scheduler now = (Scheduler) java.lang.reflect.Proxy.newProxyInstance(Scheduler.class.getClassLoader(),
                new Class<?>[] {Scheduler.class}, (proxy, method, args) -> {
                    if (method.getName().equals("region") && args.length == 4) {
                        ((Runnable) args[3]).run();
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        breaks = new WeatherBlocksModule.PileBreaks(custom, now, w -> access);
    }

    @AfterEach
    void restoreParser() {
        WorldBlocks.parser = Bukkit::createBlockData;
    }

    private Block at(BlockPos p) {
        return world.getBlockAt(p.x(), p.y(), p.z());
    }

    @Test
    void aPileOverAFernStoresTheFernAndBreakingItGivesTheFernBack() {
        BlockPos pos = new BlockPos(2, 70, 2);
        at(pos).setBlockData(Material.FERN.createBlockData(), false);
        assertThat(placer.placePile(at(pos))).isTrue();
        assertThat(custom.stateAt(at(pos))).contains(BlockStateRef.of("tfp:snow_pile", Map.of("layers", "1")));
        assertThat(access.read(pos).pile().ground().getMaterial()).isEqualTo(Material.FERN);
        at(pos).setType(Material.AIR);
        breaks.onBreak(new BlockBreakEvent(at(pos), server.addPlayer()));
        assertThat(custom.stateAt(at(pos))).isEmpty();
        assertThat(at(pos).getType()).isEqualTo(Material.FERN);
    }

    @Test
    void thePileAbsorbsAPileableBlockAboveAndNothingElse() {
        BlockPos pos = new BlockPos(4, 70, 4);
        at(pos).setBlockData(Material.FERN.createBlockData(), false);
        at(pos.offset(0, 1, 0)).setBlockData(Material.SHORT_GRASS.createBlockData(), false);
        assertThat(placer.placePile(at(pos))).isTrue();
        assertThat(at(pos.offset(0, 1, 0)).getType()).isEqualTo(Material.AIR);
        assertThat(access.read(pos).pile().above().getMaterial()).isEqualTo(Material.SHORT_GRASS);

        BlockPos other = new BlockPos(8, 70, 8);
        at(other).setBlockData(Material.FERN.createBlockData(), false);
        at(other.offset(0, 1, 0)).setType(Material.STONE);
        assertThat(placer.placePile(at(other))).isTrue();
        assertThat(at(other.offset(0, 1, 0)).getType()).isEqualTo(Material.STONE);
        assertThat(access.read(other).pile().above()).isNull();
    }

    @Test
    void addingALayerKeepsTheMemoryAndStopsAtEight() {
        BlockPos pos = new BlockPos(6, 70, 6);
        at(pos).setBlockData(Material.FERN.createBlockData(), false);
        placer.placePile(at(pos));
        for (int layers = 2; layers <= 8; layers++) {
            assertThat(placer.addLayer(at(pos))).isTrue();
            // the same block changes state through its visual (CraftEngine maps it); the fake does not, so follow it by hand
            BlockStateRef want = BlockStateRef.of("tfp:snow_pile", Map.of("layers", Integer.toString(layers)));
            assertThat(asked.getLast()).isEqualTo(want);
            custom.remove(at(pos));
            custom.place(at(pos), want, false);
        }
        assertThat(placer.addLayer(at(pos))).isFalse();
        assertThat(access.read(pos).pile().ground().getMaterial()).isEqualTo(Material.FERN);
        assertThat(placer.addLayer(at(new BlockPos(1, 70, 1)))).isFalse(); // no pile there
    }
}
