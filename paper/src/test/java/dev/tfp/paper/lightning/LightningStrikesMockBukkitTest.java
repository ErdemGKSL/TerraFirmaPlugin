/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.lightning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.lightning.LightningRules.Config;
import dev.tfp.core.lightning.LightningRules.Draws;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.LightningStrike;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** PAR-CLIM-08: the strike listener on MockBukkit with seeded draws and a fake CraftEngine. */
class LightningStrikesMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:wood/stripped_log/oak", "tfp:wood/log/oak");
    private World world;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("strikes");
    }

    private static Draws fixed(float roll, int cell) {
        return new Draws() {
            @Override
            public float nextFloat() {
                return roll;
            }

            @Override
            public int nextInt(int bound) {
                return cell;
            }
        };
    }

    private LightningStrikeEvent strike(LightningStrikes l, int x, int y, int z) {
        LightningStrike bolt = world.strikeLightningEffect(new Location(world, x, y, z));
        LightningStrikeEvent e = new LightningStrikeEvent(world, bolt, LightningStrikeEvent.Cause.COMMAND);
        l.onStrike(e);
        return e;
    }

    @Test
    void disabledLightningIsCancelled() {
        LightningStrikes l = new LightningStrikes(new Config(false, true), blocks, () -> fixed(0f, 0), (b, t) -> t.run());
        assertThat(strike(l, 0, 70, 0).isCancelled()).isTrue();
    }

    @Test
    void strikeStripsVanillaAndCustomLogsKeepingTheAxis() {
        Block vanilla = world.getBlockAt(2, 70, 0);
        vanilla.setType(Material.OAK_LOG);
        Orientable o = (Orientable) vanilla.getBlockData();
        o.setAxis(org.bukkit.Axis.X);
        vanilla.setBlockData(o);
        Block custom = world.getBlockAt(0, 71, 3);
        blocks.place(custom, BlockStateRef.of("tfp:wood/log/oak").with("axis", "z"), false);
        Block stone = world.getBlockAt(1, 70, 1);
        stone.setType(Material.STONE);
        LightningStrikes l = new LightningStrikes(Config.DEFAULTS, blocks, () -> fixed(0.1f, 0), (b, t) -> t.run());
        LightningStrikeEvent e = strike(l, 0, 70, 0);
        assertThat(e.isCancelled()).isFalse();
        assertThat(vanilla.getType()).isEqualTo(Material.STRIPPED_OAK_LOG);
        assertThat(((Orientable) vanilla.getBlockData()).getAxis()).isEqualTo(org.bukkit.Axis.X);
        assertThat(blocks.stateAt(custom)).hasValueSatisfying(s -> {
            assertThat(s.id()).isEqualTo("tfp:wood/stripped_log/oak");
            assertThat(s.properties()).containsEntry("axis", "z");
        });
        assertThat(stone.getType()).isEqualTo(Material.STONE);
    }

    @Test
    void failedRollOrOutOfRadiusStripsNothing() {
        Block far = world.getBlockAt(6, 70, 0);
        far.setType(Material.OAK_LOG);
        Block near = world.getBlockAt(1, 70, 0);
        near.setType(Material.OAK_LOG);
        new LightningStrikes(Config.DEFAULTS, blocks, () -> fixed(0.5f, 0), (b, t) -> t.run()).onStrike(
            new LightningStrikeEvent(world, world.strikeLightningEffect(new Location(world, 0, 70, 0)),
                LightningStrikeEvent.Cause.COMMAND));
        assertThat(near.getType()).isEqualTo(Material.OAK_LOG);
        strike(new LightningStrikes(Config.DEFAULTS, blocks, () -> fixed(0f, 0), (b, t) -> t.run()), 0, 70, 0);
        assertThat(near.getType()).isEqualTo(Material.STRIPPED_OAK_LOG);
        assertThat(far.getType()).as("six blocks away is outside radius 5").isEqualTo(Material.OAK_LOG);
    }

    @Test
    void stripOptionOffLeavesLogs() {
        Block near = world.getBlockAt(1, 70, 0);
        near.setType(Material.OAK_LOG);
        strike(new LightningStrikes(new Config(true, false), blocks, () -> fixed(0f, 0), (b, t) -> t.run()), 0, 70, 0);
        assertThat(near.getType()).isEqualTo(Material.OAK_LOG);
    }

    @Test
    void configFileParsing() {
        assertThat(LightningModule.parse(LightningModule.DEFAULT_TEXT, null)).isEqualTo(Config.DEFAULTS);
        assertThat(LightningModule.parse("enableLightning: false\nenableLightningStrippingLogs: false\n", null))
            .isEqualTo(new Config(false, false));
        assertThat(LightningModule.parse("enableLightning: [", null)).isEqualTo(Config.DEFAULTS);
    }
}
