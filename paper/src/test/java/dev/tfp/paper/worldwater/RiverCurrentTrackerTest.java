/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.fluid.RiverWaterRules;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-FLUID-03: the server-side river current on boats and items. */
class RiverCurrentTrackerTest extends MockServerTest {
    /** Water at y 62 (top 62.889 with air above) everywhere, nothing else. */
    private static final RiverCurrentTracker.Blocks SURFACE = (x, y, z) -> y == 62 ? 8f / 9f : -1f;

    /** A boat's box (1.375 x 0.5625) floating with its bottom at 62.4. */
    private static BoundingBox boat(double x, double z) {
        return new BoundingBox(x - 0.6875, 62.4, z - 0.6875, x + 0.6875, 62.9625, z + 0.6875);
    }

    @Test
    void aBoatInARiverDriftsDownstream() {
        RiverCurrentTracker.Flows east = (x, y, z) -> Optional.of(Flow.EEE);
        double x = 0.5;
        double vx = 0;
        // a boat in water keeps 90 % of its velocity per tick (vanilla's water friction); the current adds each tick
        for (int tick = 0; tick < 100; tick++) {
            RiverWaterRules.Vector change = RiverCurrentTracker.push(boat(x, 0.5), vx, 0, SURFACE, east);
            assertThat(change.z()).isCloseTo(0, within(1e-6));
            vx = (vx + change.x()) * 0.9;
            x += vx;
        }
        assertThat(vx).as("terminal drift speed").isCloseTo(0.014 * 0.9 / 0.1, within(1e-3));
        assertThat(x).as("downstream (east)").isGreaterThan(8);
    }

    @Test
    void stillWaterAndUnknownFlowsDoNotPush() {
        RiverCurrentTracker.Flows still = (x, y, z) -> Optional.of(Flow.NONE);
        assertThat(RiverCurrentTracker.push(boat(0.5, 0.5), 0, 0, SURFACE, still))
                .isEqualTo(new RiverWaterRules.Vector(0, 0, 0));
        RiverCurrentTracker.Flows unknown = (x, y, z) -> Optional.empty();
        assertThat(RiverCurrentTracker.push(boat(0.5, 0.5), 0, 0, SURFACE, unknown))
                .isEqualTo(new RiverWaterRules.Vector(0, 0, 0));
        RiverCurrentTracker.Blocks dry = (x, y, z) -> -1f;
        assertThat(RiverCurrentTracker.push(boat(0.5, 0.5), 0, 0, dry, (x, y, z) -> Optional.of(Flow.EEE)))
                .isEqualTo(new RiverWaterRules.Vector(0, 0, 0));
    }

    @Test
    void halfInARiverHalfInStillWaterStillPushesAtFullScale() {
        // the boat straddles x = 0: west half in still water, east half in a river flowing south
        RiverCurrentTracker.Flows half = (x, y, z) -> Optional.of(x >= 0 ? Flow.SSS : Flow.NONE);
        RiverWaterRules.Vector change = RiverCurrentTracker.push(boat(0, 0.5), 0.1, 0, SURFACE, half);
        // non-players are normalized: the full water motion scale toward +z (south)
        assertThat(change.z()).isCloseTo(0.014, within(1e-4));
        assertThat(change.x()).isCloseTo(0, within(1e-4));
    }

    @Test
    void waterHeightsOfVanillaBlocks() {
        Levelled source = (Levelled) Material.WATER.createBlockData();
        assertThat(RiverCurrentTracker.height(source)).isEqualTo(8f / 9f);
        Levelled flowing = (Levelled) Material.WATER.createBlockData();
        flowing.setLevel(3);
        assertThat(RiverCurrentTracker.height(flowing)).isEqualTo(5f / 9f);
        flowing.setLevel(8);
        assertThat(RiverCurrentTracker.height(flowing)).isEqualTo(8f / 9f);
        assertThat(RiverCurrentTracker.height(Material.STONE.createBlockData())).isNegative();
        assertThat(RiverCurrentTracker.height(Material.AIR.createBlockData())).isNegative();
    }

    @Test
    void itemsArePushedButPlayersAreNot() {
        WorldMock world = server.addSimpleWorld("w");
        Item item = world.dropItem(new Location(world, 0.5, 63, 0.5), new ItemStack(Material.STICK));
        assertThat(RiverCurrentTracker.pushable(item)).isTrue();
        PlayerMock player = server.addPlayer();
        assertThat(RiverCurrentTracker.pushable(player)).isFalse();
        item.setGravity(false);
        assertThat(RiverCurrentTracker.pushable(item)).as("no gravity: not moved by fluids").isFalse();
    }
}
