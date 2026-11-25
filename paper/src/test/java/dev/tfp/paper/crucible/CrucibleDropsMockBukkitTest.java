/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.crucible;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.paper.alloy.AlloyPdc;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.testing.MockServerTest;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** PAR-METAL-23: the crucible item dropped by a broken crucible carries its alloy for the tooltip. */
class CrucibleDropsMockBukkitTest extends MockServerTest {
    private static final CustomItems ITEMS = new CustomItems() {
        @Override
        public boolean exists(String id) {
            return true;
        }

        @Override
        public Optional<ItemStack> create(String id, int amount) {
            return Optional.empty();
        }

        @Override
        public Optional<String> idOf(ItemStack stack) {
            return Optional.of(stack.getType() == Material.CAULDRON ? CrucibleDevice.ID : "other");
        }
    };

    @Test
    void theDroppedCrucibleGetsTheAlloyOfTheRemovedOneOnce() {
        WorldMock world = server.addSimpleWorld("w");
        Block block = world.getBlockAt(3, 70, 4);
        FluidAlloy alloy = FluidAlloy.empty().fill("tfc:metal/copper", 90, 4000).alloy();
        CrucibleDrops.expect(block, alloy);
        CrucibleDrops drops = new CrucibleDrops(ITEMS);
        Item other = world.dropItem(block.getLocation(), new ItemStack(Material.STONE));
        drops.onSpawn(new ItemSpawnEvent(other));
        assertThat(AlloyPdc.read(other.getItemStack()).isEmpty()).isTrue();
        Item item = world.dropItem(block.getLocation(), new ItemStack(Material.CAULDRON));
        drops.onSpawn(new ItemSpawnEvent(item));
        assertThat(AlloyPdc.read(item.getItemStack()).amount()).isEqualTo(90);
        Item again = world.dropItem(block.getLocation(), new ItemStack(Material.CAULDRON));
        drops.onSpawn(new ItemSpawnEvent(again));
        assertThat(AlloyPdc.read(again.getItemStack()).isEmpty()).isTrue();
    }
}
