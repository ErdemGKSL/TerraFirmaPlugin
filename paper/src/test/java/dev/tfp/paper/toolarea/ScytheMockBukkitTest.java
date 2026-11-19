/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.toolarea;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-TOOL-06 on MockBukkit: the scythe cuts the 3x3x3 cube of scythe-mineable blocks around the broken one. */
class ScytheMockBukkitTest extends MockServerTest {
    private PlayerMock player;
    private ScytheListener listener;
    private Block origin;

    @BeforeEach
    void setUpScythe() {
        player = server.addPlayer();
        var world = server.addSimpleWorld("scythe");
        // a golden hoe stands in as "tfp:metal/scythe/steel"; short grass is the scythe-mineable block
        listener = new ScytheListener(stack -> stack.getType() == Material.GOLDEN_HOE ? Optional.of("tfp:metal/scythe/steel")
            : Optional.empty(), block -> block.getType() == Material.SHORT_GRASS);
        origin = world.getBlockAt(10, 64, 10);
        for (int x = 8; x <= 12; x++) {
            for (int z = 8; z <= 12; z++) {
                world.getBlockAt(x, 63, z).setType(Material.DIRT);
                world.getBlockAt(x, 64, z).setType(Material.SHORT_GRASS);
            }
        }
        player.getInventory().setItemInMainHand(new ItemStack(Material.GOLDEN_HOE));
    }

    private int grass() {
        int n = 0;
        for (int x = 8; x <= 12; x++) {
            for (int z = 8; z <= 12; z++) {
                if (origin.getWorld().getBlockAt(x, 64, z).getType() == Material.SHORT_GRASS) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void cutsTheNeighbourhoodOfThePlantBroken() {
        assertThat(grass()).isEqualTo(25);
        origin.setType(Material.AIR); // the broken block itself (vanilla removes it)
        listener.onBreak(new BlockBreakEvent(origin, player));
        assertThat(grass()).isEqualTo(25 - 9); // the 3x3 around the origin, the origin included
        assertThat(origin.getWorld().getBlockAt(8, 64, 8).getType()).isEqualTo(Material.SHORT_GRASS);
        assertThat(origin.getWorld().getBlockAt(10, 63, 10).getType()).isEqualTo(Material.DIRT); // not scythe-mineable
    }

    @Test
    void otherToolsAndSpectatorsCutNothing() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_HOE));
        origin.setType(Material.AIR);
        listener.onBreak(new BlockBreakEvent(origin, player));
        assertThat(grass()).isEqualTo(24);
        player.getInventory().setItemInMainHand(new ItemStack(Material.GOLDEN_HOE));
        player.setGameMode(GameMode.SPECTATOR);
        listener.onBreak(new BlockBreakEvent(origin, player));
        assertThat(grass()).isEqualTo(24);
    }

    @Test
    void creativeCutsToo() {
        player.setGameMode(GameMode.CREATIVE);
        origin.setType(Material.AIR);
        listener.onBreak(new BlockBreakEvent(origin, player));
        assertThat(grass()).isEqualTo(25 - 9);
    }

    @Test
    void idsAreScythes() {
        assertThat(ScytheListener.isScythe("tfp:metal/scythe/bronze")).isTrue();
        assertThat(ScytheListener.isScythe("tfp:metal/scythe_blade/bronze")).isFalse();
        assertThat(ScytheListener.isScythe("tfp:metal/knife/bronze")).isFalse();
    }
}
