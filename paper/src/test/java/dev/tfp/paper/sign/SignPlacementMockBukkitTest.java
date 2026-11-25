/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sign;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-DECO-25: the placement rotation of standing and ceiling hanging signs, waterlogging kept. */
class SignPlacementMockBukkitTest extends MockServerTest {
    static final String STANDING = "tfp:wood/sign/oak";
    static final String HANGING = "tfp:wood/hanging_sign/copper/oak";
    static final String WALL_HANGING = "tfp:wood/wall_hanging_sign/copper/oak";

    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known(STANDING, HANGING, WALL_HANGING);
    private World world;
    private SignPlacementListener listener;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("signs");
        world.getChunkAt(0, 0).load();
        listener = new SignPlacementListener(blocks, new SignScheduler(), (b, s) -> blocks.place(b, s, false),
            b -> b.getType() == Material.STONE);
    }

    private Block put(int x, int y, String state) {
        Block b = world.getBlockAt(x, y, 0);
        blocks.place(b, BlockStateRef.parse(state), true);
        return b;
    }

    @Test
    void aStandingSignTakesTheFacingOfThePlacerAndKeepsItsWater() {
        Block b = put(0, 64, STANDING + "[rotation=0,waterlogged=true]");
        listener.placed(b, 0f, false);
        BlockStateRef s = blocks.stateAt(b).orElseThrow();
        assertThat(s.properties()).containsEntry("rotation", "8").containsEntry("waterlogged", "true");
        listener.placed(b, 90f, false);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("rotation", "12");
    }

    @Test
    void aCeilingSignHangsPlainBelowASolidBlockAndInAVWithNothingAbove() {
        Block b = put(0, 64, HANGING + "[rotation=0,attached=false,waterlogged=false]");
        b.getRelative(0, 1, 0).setType(Material.STONE);
        listener.placed(b, 0f, false);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("rotation", "8").containsEntry("attached", "false");
        b.getRelative(0, 1, 0).setType(Material.AIR);
        listener.placed(b, 0f, false);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("attached", "true");
    }

    @Test
    void belowAHangingSignOnTheSameAxisItHangsPlain() {
        Block above = put(3, 65, WALL_HANGING + "[facing=north,waterlogged=false]");
        Block b = put(3, 64, HANGING + "[rotation=0,attached=false,waterlogged=false]");
        listener.placed(b, 0f, false);
        assertThat(above.getY()).isEqualTo(65);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).containsEntry("attached", "false").containsEntry("rotation", "8");
        listener.placed(b, 90f, false);
        assertThat(blocks.stateAt(b).orElseThrow().properties()).as("across the sign above").containsEntry("attached", "true");
    }

    @Test
    void otherBlocksAreLeftAlone() {
        Block b = put(0, 64, WALL_HANGING + "[facing=east,waterlogged=false]");
        BlockStateRef before = blocks.stateAt(b).orElseThrow();
        listener.placed(b, 33f, false);
        assertThat(blocks.stateAt(b).orElseThrow()).isEqualTo(before);
        assertThat(List.of(SignPlacementListener.STANDING, SignPlacementListener.HANGING)).hasSize(2);
    }
}
