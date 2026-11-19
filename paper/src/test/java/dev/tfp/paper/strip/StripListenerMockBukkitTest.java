/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.strip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** PAR-GATHER-08 on MockBukkit with a fake CraftEngine: iron axe stands in for any {@code #minecraft:axes} item. */
class StripListenerMockBukkitTest extends MockServerTest {
    private final FakeCustomBlocks blocks = new FakeCustomBlocks().known("tfp:wood/stripped_log/oak", "tfp:wood/log/oak",
            "tfp:wood/wood/birch", "tfp:wood/stripped_wood/birch", "tfp:golden_bamboo_block");
    private StripListener listener;
    private World world;
    private Block block;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("strip");
        listener = new StripListener(blocks, s -> s.getType() == Material.IRON_AXE, (p, hand) -> {
            ItemStack held = p.getInventory().getItem(hand);
            Damageable meta = (Damageable) held.getItemMeta();
            meta.setDamage(meta.getDamage() + 1);
            held.setItemMeta(meta);
        });
        block = world.getBlockAt(0, 64, 0);
        player = server.addPlayer();
    }

    private PlayerInteractEvent click(ItemStack item) {
        PlayerInteractEvent e = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, block, BlockFace.UP,
                EquipmentSlot.HAND);
        listener.onInteract(e);
        return e;
    }

    private static int damage(ItemStack s) {
        return ((Damageable) s.getItemMeta()).getDamage();
    }

    @Test
    void axeStripsKeepingAxisAndLosesOneDurability() {
        blocks.place(block, BlockStateRef.of("tfp:wood/log/oak").with("axis", "x"), false);
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        player.getInventory().setItemInMainHand(axe);
        player.setGameMode(GameMode.SURVIVAL);
        PlayerInteractEvent e = click(axe);
        axe = player.getInventory().getItemInMainHand();
        assertThat(blocks.stateAt(block)).hasValueSatisfying(s -> {
            assertThat(s.id()).isEqualTo("tfp:wood/stripped_log/oak");
            assertThat(s.properties()).containsEntry("axis", "x");
        });
        assertThat(damage(axe)).isEqualTo(1);
        assertThat(e.useInteractedBlock()).isEqualTo(org.bukkit.event.Event.Result.DENY);
    }

    @Test
    void creativeKeepsDurability() {
        blocks.place(block, BlockStateRef.of("tfp:wood/wood/birch").with("axis", "y"), false);
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        player.getInventory().setItemInMainHand(axe);
        player.setGameMode(GameMode.CREATIVE);
        click(axe);
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo("tfp:wood/stripped_wood/birch");
        assertThat(damage(axe)).isZero();
    }

    @Test
    void goldenBambooStripsToVanillaStrippedBamboo() {
        blocks.place(block, BlockStateRef.of("tfp:golden_bamboo_block").with("axis", "z"), false);
        player.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        click(new ItemStack(Material.IRON_AXE));
        assertThat(block.getType()).isEqualTo(Material.STRIPPED_BAMBOO_BLOCK);
        assertThat(((org.bukkit.block.data.Orientable) block.getBlockData()).getAxis()).isEqualTo(org.bukkit.Axis.Z);
    }

    @Test
    void nonAxeAdventureAndOtherBlocksDoNothing() {
        blocks.place(block, BlockStateRef.of("tfp:wood/log/oak").with("axis", "y"), false);
        click(new ItemStack(Material.STICK));
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo("tfp:wood/log/oak");
        player.setGameMode(GameMode.ADVENTURE);
        ItemStack axe = new ItemStack(Material.IRON_AXE);
        player.getInventory().setItemInMainHand(axe);
        click(axe);
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo("tfp:wood/log/oak");
        assertThat(damage(player.getInventory().getItemInMainHand())).isZero();
        player.setGameMode(GameMode.SURVIVAL);
        blocks.place(block, BlockStateRef.of("tfp:wood/stripped_log/oak").with("axis", "y"), false);
        click(axe);
        assertThat(blocks.stateAt(block).orElseThrow().id()).isEqualTo("tfp:wood/stripped_log/oak");
        assertThat(damage(axe)).isZero();
    }
}
