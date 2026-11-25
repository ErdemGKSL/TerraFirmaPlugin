/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.lobstertrap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-FAUNA-37: TFC getStateForPlacement of the lobster trap: facing opposite the placer, fluid of the replaced block. */
class LobsterTrapPlacementMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(LobsterTrapDevice.ID, "tfp:other");
    private LobsterTrapPlacement placement;
    private World world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("trap");
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> {
                if (method.getName().equals("region")) {
                    ((Runnable) args[args.length - 1]).run();
                }
                return null;
            });
        placement = new LobsterTrapPlacement(scheduler, blocks, (block, next) -> blocks.place(block, next, false));
    }

    private Block trap() {
        Block b = world.getBlockAt(2, 64, 2);
        blocks.place(b, BlockStateRef.of(LobsterTrapDevice.ID).with("facing", "north").with("trap", "baited")
            .with("fluid", "empty"), false);
        return b;
    }

    @Test
    void thePlacedTrapTakesTheFacingAndTheWaterOfItsCell() {
        Block b = trap();
        placement.placed(b, true, "east");
        BlockStateRef s = blocks.stateAt(b).orElseThrow();
        assertThat(s.properties()).containsEntry("facing", "east").containsEntry("fluid", "water").containsEntry("trap", "baited");
        placement.placed(b, false, "south");
        s = blocks.stateAt(b).orElseThrow();
        assertThat(s.properties()).containsEntry("facing", "south").containsEntry("fluid", "empty");
    }

    @Test
    void aBlockThatIsNoTrapIsLeftAlone() {
        Block b = world.getBlockAt(4, 64, 4);
        BlockStateRef other = BlockStateRef.of("tfp:other");
        blocks.place(b, other, false);
        placement.placed(b, true, "east");
        assertThat(blocks.stateAt(b)).contains(other);
    }

    @Test
    void theFacingIsOppositeThePlacersHorizontalDirection() {
        assertThat(LobsterTrapPlacement.facing(0f)).isEqualTo("north"); // faces south
        assertThat(LobsterTrapPlacement.facing(90f)).isEqualTo("east"); // faces west
        assertThat(LobsterTrapPlacement.facing(180f)).isEqualTo("south"); // faces north
        assertThat(LobsterTrapPlacement.facing(-90f)).isEqualTo("west"); // faces east
        assertThat(LobsterTrapPlacement.facing(-179f)).isEqualTo("south");
        assertThat(LobsterTrapPlacement.facing(44f)).isEqualTo("north");
        assertThat(LobsterTrapPlacement.facing(46f)).isEqualTo("east");
    }

    @Test
    void onlyAWaterSourceFillsTheTrap() {
        Levelled source = (Levelled) Material.WATER.createBlockData();
        assertThat(LobsterTrapPlacement.isWaterSource(source)).isTrue();
        Levelled flowing = (Levelled) Material.WATER.createBlockData();
        flowing.setLevel(3);
        assertThat(LobsterTrapPlacement.isWaterSource(flowing)).isFalse();
        assertThat(LobsterTrapPlacement.isWaterSource(Material.AIR.createBlockData())).isFalse();
        assertThat(LobsterTrapPlacement.isWaterSource(Material.LAVA.createBlockData())).isFalse();
    }
}
