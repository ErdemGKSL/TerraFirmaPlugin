/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.fire;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.paper.testing.MockServerTest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-05 fixer pass: the real {@link PaperFireWorld} fire tail (TFC {@code StartFireEvent.startFire} @ c3b4d9536 with
 * vanilla's {@code BaseFireBlock.canBePlacedAt}) and its fluid gate. The fire lands on the targeted face of a full block
 * (one above for the top face, the adjacent cell for a side face), never in the player's cell unless that is the cell the
 * face points into; non-flammable non-full targets get nothing; flammable replaceables are burnt away; a cancelled
 * {@link BlockIgniteEvent} changes nothing.
 */
class PaperFireWorldMockBukkitTest extends MockServerTest {
    private World world;
    private Player player;
    /** Full cubes in this test. */
    private static final Set<Material> FULL = EnumSet.of(Material.STONE, Material.OAK_LOG);

    /**
     * The vanilla shape facts of the blocks used here (MockBukkit implements neither collision shapes nor
     * {@code isFaceSturdy} / {@code isSupported}); the placement logic under test is the real one.
     */
    private static final PaperFireWorld.Shapes SHAPES = new PaperFireWorld.Shapes() {
        @Override
        public boolean fullCollision(Block block) {
            return FULL.contains(block.getType());
        }

        @Override
        public boolean replaceableOrNoCollision(Block block) {
            return switch (block.getType()) {
                case AIR, SHORT_GRASS, SNOW -> true;
                default -> false;
            };
        }

        @Override
        public boolean sturdyTop(Block block) {
            return FULL.contains(block.getType());
        }

        @Override
        public boolean survives(BlockData fire, Block pos) {
            // FireBlock.canSurvive: a sturdy floor or a flammable neighbour
            if (sturdyTop(pos.getRelative(BlockFace.DOWN))) {
                return true;
            }
            for (BlockFace f : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                    BlockFace.UP, BlockFace.DOWN)) {
                if (pos.getRelative(f).getType().isFlammable()) {
                    return true;
                }
            }
            return false;
        }
    };

    private final PaperFireWorld fire = new PaperFireWorld(SHAPES);
    private final List<BlockIgniteEvent> ignites = new ArrayList<>();
    private boolean cancelIgnite;

    @BeforeEach
    void setUp() {
        world = server.addSimpleWorld("w");
        player = server.addPlayer();
        // two blocks away from the targets, as the PAR-HEAT-18 steps ask
        player.teleport(new Location(world, 3.5, 65, 0.5));
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            void onIgnite(BlockIgniteEvent event) {
                ignites.add(event);
                event.setCancelled(cancelIgnite);
            }
        }, plugin);
        for (int x = -2; x <= 4; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, 63, z).setType(Material.STONE);
                for (int y = 64; y <= 66; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }
    }

    @Test
    void topFaceOfAFullBlockPutsTheFireOneBlockAbove() {
        Block ground = world.getBlockAt(0, 63, 0);
        assertThat(fire.placeFire(ground, BlockFace.UP, player)).isTrue();
        assertThat(ground.getType()).isEqualTo(Material.STONE);
        assertThat(world.getBlockAt(0, 64, 0).getType()).isEqualTo(Material.FIRE);
        assertThat(ignites).hasSize(1);
        assertThat(ignites.getFirst().getBlock()).isEqualTo(world.getBlockAt(0, 64, 0));
        assertThat(ignites.getFirst().getCause()).isEqualTo(BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL);
        assertThat(ignites.getFirst().getPlayer()).isSameAs(player);
    }

    @Test
    void sideFaceOfAFullBlockPutsTheFireInTheAdjacentCellNotInTheBlock() {
        Block log = world.getBlockAt(0, 64, 0);
        log.setType(Material.OAK_LOG);
        assertThat(fire.placeFire(log, BlockFace.EAST, player)).isTrue();
        assertThat(log.getType()).as("the targeted log stays a log (TFC, vanilla flint and steel)")
            .isEqualTo(Material.OAK_LOG);
        assertThat(world.getBlockAt(1, 64, 0).getType()).isEqualTo(Material.FIRE);
        assertThat(world.getBlockAt(3, 65, 0).getType()).as("not at the player").isEqualTo(Material.AIR);
    }

    @Test
    void aSnowLayerTargetGetsNoFire() {
        Block snow = world.getBlockAt(0, 64, 0);
        snow.setType(Material.SNOW);
        assertThat(fire.placeFire(snow, BlockFace.UP, player)).isFalse();
        assertThat(snow.getType()).isEqualTo(Material.SNOW);
        assertThat(world.getBlockAt(0, 65, 0).getType()).isEqualTo(Material.AIR);
        assertThat(ignites).isEmpty();
    }

    @Test
    void aFlammableReplaceableTargetIsBurntAwayAndReplacedByFire() {
        Block grass = world.getBlockAt(0, 64, 0);
        grass.setType(Material.SHORT_GRASS);
        assertThat(fire.placeFire(grass, BlockFace.UP, player)).isTrue();
        assertThat(grass.getType()).isEqualTo(Material.FIRE);
        assertThat(world.getBlockAt(0, 65, 0).getType()).isEqualTo(Material.AIR);
    }

    @Test
    void aCancelledIgniteChangesNothing() {
        cancelIgnite = true;
        Block grass = world.getBlockAt(0, 64, 0);
        grass.setType(Material.SHORT_GRASS);
        assertThat(fire.placeFire(grass, BlockFace.UP, player)).isFalse();
        assertThat(grass.getType()).isEqualTo(Material.SHORT_GRASS);
        Block ground = world.getBlockAt(1, 63, 0);
        assertThat(fire.placeFire(ground, BlockFace.UP, player)).isFalse();
        assertThat(world.getBlockAt(1, 64, 0).getType()).isEqualTo(Material.AIR);
        assertThat(ignites).hasSize(2);
    }

    @Test
    void anOccupiedTargetCellGetsNoFire() {
        world.getBlockAt(0, 64, 0).setType(Material.STONE);
        assertThat(fire.placeFire(world.getBlockAt(0, 63, 0), BlockFace.UP, player)).isFalse();
        assertThat(ignites).isEmpty();
    }

    @Test
    void fluidGateSeesWaterWaterloggedAndFluidStateOnlyBlocks() {
        Block b = world.getBlockAt(0, 64, 0);
        b.setType(Material.WATER);
        assertThat(fire.hasFluid(b)).isTrue();
        b.setType(Material.OAK_SLAB);
        Waterlogged slab = (Waterlogged) b.getBlockData();
        slab.setWaterlogged(true);
        b.setBlockData(slab);
        assertThat(fire.hasFluid(b)).isTrue();
        for (Material m : List.of(Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP, Material.KELP_PLANT,
                Material.BUBBLE_COLUMN)) {
            b.setType(m);
            assertThat(fire.hasFluid(b)).as(m.name()).isTrue();
        }
        b.setType(Material.AIR);
        assertThat(fire.hasFluid(b)).isFalse();
        b.setType(Material.STONE);
        assertThat(fire.hasFluid(b)).isFalse();
    }
}
