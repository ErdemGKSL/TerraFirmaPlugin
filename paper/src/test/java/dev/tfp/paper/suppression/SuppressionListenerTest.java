/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.suppression;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.visuals.Suppression;
import dev.tfp.core.visuals.SuppressionSet;
import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * M1-INST-04 glue on MockBukkit: the listener applies the core verdicts to real Bukkit events in a test
 * world. MockBukkit has no fluid physics, so the lava + water case fires the {@link BlockFormEvent} the
 * server would fire and then applies it the way Paper's {@code handleBlockFormEvent} does
 * ({@code newState.update(true)} unless cancelled).
 */
class SuppressionListenerTest extends MockServerTest {
    static final String JSON = """
            {"suppress": [
              {"block": "minecraft:cobblestone", "creation_paths": ["item", "lava_water"]},
              {"block": "minecraft:obsidian", "creation_paths": ["item", "lava_water"]},
              {"block": "minecraft:white_concrete", "creation_paths": ["concrete_powder_water", "item"]},
              {"block": "minecraft:exposed_copper", "creation_paths": ["copper_weathering_scraping_waxing", "item"]},
              {"block": "minecraft:dead_brain_coral_block", "creation_paths": ["coral_death", "item"]},
              {"block": "minecraft:grass_block", "creation_paths": ["block_spread", "item"]},
              {"block": "minecraft:moss_block", "creation_paths": ["item", "moss_bonemeal"]}
            ]}""";
    static final String MAPPINGS = """
            block_state_mappings:
              minecraft:cobblestone: minecraft:stone
              minecraft:obsidian: minecraft:stone
            """;

    World tfc;
    World vanilla;
    SuppressionListener listener;

    @BeforeEach
    void register() {
        tfc = server.addSimpleWorld("tfc");
        vanilla = server.addSimpleWorld("vanilla");
        listener = new SuppressionListener(new Suppression(SuppressionSet.parse(JSON, MAPPINGS)), w -> w.getName().equals("tfc"),
                Logger.getLogger("SuppressionListenerTest"));
        server.getPluginManager().registerEvents(listener, plugin);
    }

    /** Fires a form event for {@code block} turning into {@code result} and applies it like the server. */
    BlockFormEvent form(Block block, Material result) {
        BlockState newState = block.getState();
        newState.setType(result);
        BlockFormEvent e = new BlockFormEvent(block, newState);
        server.getPluginManager().callEvent(e);
        if (!e.isCancelled()) {
            e.getNewState().update(true);
        }
        return e;
    }

    @Test
    void lavaMeetingWaterFormsStoneInsteadOfCobblestone() {
        Block b = tfc.getBlockAt(0, 64, 0);
        b.setType(Material.LAVA);
        BlockFormEvent e = form(b, Material.COBBLESTONE);
        assertThat(e.isCancelled()).isFalse();
        assertThat(b.getType()).isEqualTo(Material.STONE);

        Block source = tfc.getBlockAt(1, 64, 0);
        source.setType(Material.LAVA);
        form(source, Material.OBSIDIAN);
        assertThat(source.getType()).isEqualTo(Material.STONE);
        assertThat(listener.suppressedCount()).isEqualTo(2);
    }

    @Test
    void vanillaWorldsAreLeftAlone() {
        Block b = vanilla.getBlockAt(0, 64, 0);
        b.setType(Material.LAVA);
        form(b, Material.COBBLESTONE);
        assertThat(b.getType()).isEqualTo(Material.COBBLESTONE);
        assertThat(listener.suppressedCount()).isZero();
    }

    @Test
    void stoneFromLavaIsAllowed() {
        Block b = tfc.getBlockAt(0, 64, 0);
        b.setType(Material.WATER);
        assertThat(form(b, Material.STONE).isCancelled()).isFalse();
        assertThat(b.getType()).isEqualTo(Material.STONE);
    }

    @Test
    void concretePowderDoesNotHarden() {
        Block b = tfc.getBlockAt(0, 64, 0);
        b.setType(Material.WHITE_CONCRETE_POWDER);
        assertThat(form(b, Material.WHITE_CONCRETE).isCancelled()).isTrue();
        assertThat(b.getType()).isEqualTo(Material.WHITE_CONCRETE_POWDER);
    }

    @Test
    void copperDoesNotOxidise() {
        Block b = tfc.getBlockAt(0, 64, 0);
        b.setType(Material.COPPER_BLOCK);
        assertThat(form(b, Material.EXPOSED_COPPER).isCancelled()).isTrue();
        assertThat(b.getType()).isEqualTo(Material.COPPER_BLOCK);
    }

    @Test
    void coralDeathAndGrassSpreadAreCancelled() {
        Block coral = tfc.getBlockAt(0, 64, 0);
        coral.setType(Material.BRAIN_CORAL_BLOCK);
        BlockState dead = coral.getState();
        dead.setType(Material.DEAD_BRAIN_CORAL_BLOCK);
        BlockFadeEvent fade = new BlockFadeEvent(coral, dead);
        server.getPluginManager().callEvent(fade);
        assertThat(fade.isCancelled()).isTrue();

        Block dirt = tfc.getBlockAt(2, 64, 0);
        dirt.setType(Material.DIRT);
        Block grass = tfc.getBlockAt(3, 64, 0);
        grass.setType(Material.GRASS_BLOCK);
        BlockState spread = dirt.getState();
        spread.setType(Material.GRASS_BLOCK);
        BlockSpreadEvent s = new BlockSpreadEvent(dirt, grass, spread);
        server.getPluginManager().callEvent(s);
        assertThat(s.isCancelled()).isTrue();
    }

    @Test
    void bonemealDropsOnlyRepurposedBlocks() {
        Block root = tfc.getBlockAt(0, 64, 0);
        BlockState moss = tfc.getBlockAt(1, 64, 0).getState();
        moss.setType(Material.MOSS_BLOCK);
        BlockState carpet = tfc.getBlockAt(1, 65, 0).getState();
        carpet.setType(Material.MOSS_CARPET);
        List<BlockState> grown = new ArrayList<>(List.of(moss, carpet));
        BlockFertilizeEvent e = new BlockFertilizeEvent(root, null, grown);
        server.getPluginManager().callEvent(e);
        assertThat(e.getBlocks()).extracting(BlockState::getType).containsExactly(Material.MOSS_CARPET);
    }

    @Test
    void consoleSetblockOfARepurposedBlockIsRefused() {
        ServerCommandEvent bad = new ServerCommandEvent(server.getConsoleSender(), "setblock 0 64 0 minecraft:cobblestone");
        server.getPluginManager().callEvent(bad);
        assertThat(bad.isCancelled()).isTrue();
        ServerCommandEvent ok = new ServerCommandEvent(server.getConsoleSender(), "setblock 0 64 0 minecraft:stone");
        server.getPluginManager().callEvent(ok);
        assertThat(ok.isCancelled()).isFalse();
    }

    @Test
    void generatedLootLosesRepurposedBlocks() {
        List<ItemStack> loot = new ArrayList<>(List.of(new ItemStack(Material.COBBLESTONE, 3), new ItemStack(Material.FLINT)));
        org.bukkit.event.world.LootGenerateEvent e = new org.bukkit.event.world.LootGenerateEvent(tfc, null, null,
                null, null, loot, false);
        server.getPluginManager().callEvent(e);
        assertThat(e.getLoot()).extracting(ItemStack::getType).containsExactly(Material.FLINT);
    }

    @Test
    void placingAndScrapingIntoRepurposedBlocksAreCancelled() {
        org.bukkit.entity.Player player = server.addPlayer();
        player.teleport(tfc.getSpawnLocation());
        Block at = tfc.getBlockAt(0, 70, 0);
        BlockState replaced = at.getState();
        at.setType(Material.COBBLESTONE);
        org.bukkit.event.block.BlockPlaceEvent place = new org.bukkit.event.block.BlockPlaceEvent(at, replaced,
                tfc.getBlockAt(0, 69, 0), new ItemStack(Material.COBBLESTONE), player, true,
                org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(place);
        assertThat(place.isCancelled()).isTrue();

        Block copper = tfc.getBlockAt(5, 70, 0);
        copper.setType(Material.WEATHERED_COPPER);
        org.bukkit.event.entity.EntityChangeBlockEvent scrape = new org.bukkit.event.entity.EntityChangeBlockEvent(player,
                copper, Material.EXPOSED_COPPER.createBlockData());
        server.getPluginManager().callEvent(scrape);
        assertThat(scrape.isCancelled()).isTrue();
    }
}
