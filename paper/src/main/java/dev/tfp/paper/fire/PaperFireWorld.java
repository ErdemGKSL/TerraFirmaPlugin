/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (the item box, the fire tail)
 */
package dev.tfp.paper.fire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.util.BoundingBox;

/**
 * {@link FireWorld} on a Paper server. The fire tail follows TFC's {@code startFire}, vanilla's
 * {@code BaseFireBlock.canBePlacedAt} / {@code getState} and {@code FireBlock.getStateForPlacement}; before it changes
 * anything it calls a {@link BlockIgniteEvent} (cause {@code FLINT_AND_STEEL}) so protection plugins can refuse it.
 */
public final class PaperFireWorld implements FireWorld {
    /** The faces {@code FireBlock.getStateForPlacement} sets when the fire has no floor. */
    private static final BlockFace[] FIRE_FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
        BlockFace.UP};

    /**
     * Blocks whose fluid state is always water although they are neither a liquid nor {@link Waterlogged} (vanilla
     * {@code LiquidBlockContainer}s with a fixed {@code Fluids.WATER} source state).
     */
    private static final Set<Material> ALWAYS_WATER = EnumSet.of(Material.SEAGRASS, Material.TALL_SEAGRASS,
        Material.KELP, Material.KELP_PLANT, Material.BUBBLE_COLUMN);

    @Override
    public boolean hasFluid(Block block) {
        // TFC: !state.getFluidState().isEmpty()
        return block.isLiquid() || ALWAYS_WATER.contains(block.getType())
            || block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    @Override
    public List<Item> groundItems(Block at) {
        // TFC: new AABB(x - 0.5, y, z - 0.5, x + 1.5, y + 1, z + 1.5)
        BoundingBox box = new BoundingBox(at.getX() - 0.5, at.getY(), at.getZ() - 0.5, at.getX() + 1.5, at.getY() + 1,
            at.getZ() + 1.5);
        Collection<Entity> found = at.getWorld().getNearbyEntities(box, e -> e instanceof Item && e.isValid());
        List<Item> items = new ArrayList<>(found.size());
        for (Entity e : found) {
            items.add((Item) e);
        }
        return items;
    }

    @Override
    public void remove(Item item) {
        item.remove();
    }

    /**
     * The block-shape reads of the fire tail. A seam only because MockBukkit does not implement collision shapes, face
     * sturdiness or {@code isSupported}; on a server it is always {@link #BUKKIT}.
     */
    interface Shapes {
        /** {@code isCollisionShapeFullBlock}. */
        boolean fullCollision(Block block);

        /** TFC's destroy test: {@code canBeReplaced() || getCollisionShape().isEmpty()}. */
        boolean replaceableOrNoCollision(Block block);

        /** {@code isFaceSturdy(level, below, UP)}. */
        boolean sturdyTop(Block block);

        /** {@code fireState.canSurvive(level, pos)}. */
        boolean survives(BlockData fire, Block pos);
    }

    /** The real reads. */
    static final Shapes BUKKIT = new Shapes() {
        @Override
        public boolean fullCollision(Block block) {
            Collection<BoundingBox> boxes = block.getCollisionShape().getBoundingBoxes();
            if (boxes.size() != 1) {
                return false;
            }
            BoundingBox b = boxes.iterator().next();
            return b.getMinX() <= 0 && b.getMinY() <= 0 && b.getMinZ() <= 0 && b.getMaxX() >= 1 && b.getMaxY() >= 1
                && b.getMaxZ() >= 1;
        }

        @Override
        public boolean replaceableOrNoCollision(Block block) {
            return block.isReplaceable() || block.getCollisionShape().getBoundingBoxes().isEmpty();
        }

        @Override
        public boolean sturdyTop(Block block) {
            return block.getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
        }

        @Override
        public boolean survives(BlockData fire, Block pos) {
            return fire.isSupported(pos);
        }
    };

    private final Shapes shapes;

    public PaperFireWorld() {
        this(BUKKIT);
    }

    PaperFireWorld(Shapes shapes) {
        this.shapes = shapes;
    }

    @Override
    public boolean placeFire(Block clicked, BlockFace face, Player player) {
        // TFC startFire: a full-collision target lights the cell its clicked face points into (the top face of the
        // ground lights the cell above; a side face lights the neighbour on that side, which is the player's own cell
        // when they stand against it, as with vanilla flint and steel); a flammable non-solid target is replaced.
        Block pos = clicked;
        boolean destroy = false;
        if (shapes.fullCollision(clicked)) {
            pos = clicked.getRelative(face);
        } else if (clicked.getType().isFlammable() && shapes.replaceableOrNoCollision(clicked)) {
            destroy = true;
        }
        // BaseFireBlock.canBePlacedAt: air (after the destroy) and the fire state can survive (portals are not lit)
        if (!destroy && !pos.getType().isAir()) {
            return false;
        }
        BlockData fire = fireState(pos);
        if (!shapes.survives(fire, pos)) {
            return false;
        }
        BlockIgniteEvent ignite = new BlockIgniteEvent(pos, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, player);
        if (!ignite.callEvent()) {
            return false;
        }
        if (destroy) {
            // level.destroyBlock(pos, false): the break effect, no drops
            clicked.getWorld().playEffect(clicked.getLocation(), Effect.STEP_SOUND, clicked.getBlockData());
            clicked.setType(Material.AIR, true);
        }
        // setBlock(pos, BaseFireBlock.getState(level, pos), 11)
        pos.setBlockData(fire, true);
        return true;
    }

    /** {@code BaseFireBlock.getState}: soul fire on soul fire bases, else fire with faces where it has no floor. */
    BlockData fireState(Block pos) {
        Block below = pos.getRelative(BlockFace.DOWN);
        if (Tag.SOUL_FIRE_BASE_BLOCKS.isTagged(below.getType())) {
            return Material.SOUL_FIRE.createBlockData();
        }
        BlockData fire = Material.FIRE.createBlockData();
        if (!below.getType().isFlammable() && !shapes.sturdyTop(below) && fire instanceof MultipleFacing facing) {
            for (BlockFace f : FIRE_FACES) {
                facing.setFace(f, pos.getRelative(f).getType().isFlammable());
            }
        }
        return fire;
    }
}
