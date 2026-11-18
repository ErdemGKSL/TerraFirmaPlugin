/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/misc/TFCFallingBlockEntity.java @ c3b4d9536
 */
package dev.tfp.paper.falling;

import dev.tfp.core.falling.FallCell;
import dev.tfp.core.falling.FallRules;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.BlockVisuals;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.loot.LootContext;
import dev.tfp.paper.loot.LootService;
import dev.tfp.paper.loot.LootTable;
import dev.tfp.paper.neighbour.WorldBlocks;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

/**
 * The server {@link FallWorld}: cells from the live block data ({@code isFaceSturdy(UP, FULL)}, {@code isReplaceable},
 * the hardness of vanilla blocks; custom blocks are always breakable), toughness from {@link Tags}, survival from the
 * carrier's {@code isSupported}, drops from {@link LootService} (TFC tables with an empty tool) or vanilla's own.
 */
public final class BukkitFallWorld implements FallWorld {
    private static final double FOOTPRINT_MIN = 0.01;
    private static final double FOOTPRINT_MAX = 0.99;

    private final Tags tags;
    private final CustomBlocks custom;
    private final BlockVisuals visuals;
    private final LootService loot;
    private final GameRule<Boolean> tileDrops = rule("block_drops", "doTileDrops");
    private final GameRule<Boolean> entityDrops = rule("entity_drops", "doEntityDrops");

    public BukkitFallWorld(Tags tags, CustomBlocks custom, BlockVisuals visuals, LootService loot) {
        this.tags = tags;
        this.custom = custom;
        this.visuals = visuals;
        this.loot = loot;
    }

    /** A boolean game rule by its 1.21.11+ key or its older name; null when the server has neither. */
    @SuppressWarnings("unchecked")
    static GameRule<Boolean> rule(String key, String legacyName) {
        for (GameRule<?> r : GameRule.values()) {
            if (r.getType() != Boolean.class) {
                continue;
            }
            String k = r.getKey().getKey();
            if (k.equals(key) || r.getName().equals(key) || k.equalsIgnoreCase(legacyName)
                    || r.getName().equals(legacyName)) {
                return (GameRule<Boolean>) r;
            }
        }
        return null;
    }

    private static boolean on(World world, GameRule<Boolean> rule) {
        return rule == null || !Boolean.FALSE.equals(world.getGameRuleValue(rule));
    }

    @Override
    public FallCell cell(Block block) {
        Material type = block.getType();
        boolean air = type.isAir() || type == Material.WATER || type == Material.LAVA;
        BlockData data = block.getBlockData();
        boolean customBlock = custom.stateAt(block).isPresent();
        boolean unbreakable = !customBlock && (type == Material.STRUCTURE_VOID || type.getHardness() < 0f);
        int toughness = type == Material.BEDROCK ? FallRules.BEDROCK_TOUGHNESS
                : FallRules.toughness(false, tags.is(block, FallRules.TOUGHNESS_3), tags.is(block, FallRules.TOUGHNESS_2),
                        tags.is(block, FallRules.TOUGHNESS_1));
        return new FallCell(air, data.isFaceSturdy(BlockFace.UP, BlockSupport.FULL), toughness, unbreakable,
                block.isReplaceable());
    }

    @Override
    public int toughness(BlockStateRef falling) {
        return FallRules.toughness("minecraft:bedrock".equals(falling.id()), tags.is(falling, FallRules.TOUGHNESS_3),
                tags.is(falling, FallRules.TOUGHNESS_2), tags.is(falling, FallRules.TOUGHNESS_1));
    }

    @Override
    public boolean survives(BlockStateRef falling, Block at) {
        return blockData(falling).map(d -> d.isSupported(at)).orElse(true);
    }

    @Override
    public boolean isMovingPiston(Block block) {
        return block.getType() == Material.MOVING_PISTON;
    }

    @Override
    public double[][] collisionHeights(Block block) {
        List<double[]> out = new ArrayList<>();
        for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
            if (box.getMaxX() > FOOTPRINT_MIN && box.getMinX() < FOOTPRINT_MAX && box.getMaxZ() > FOOTPRINT_MIN
                    && box.getMinZ() < FOOTPRINT_MAX) {
                out.add(new double[] {box.getMinY(), box.getMaxY()});
            }
        }
        return out.toArray(new double[0][]);
    }

    @Override
    public Optional<BlockData> blockData(BlockStateRef state) {
        if ("minecraft".equals(state.namespace())) {
            try {
                return Optional.of(Bukkit.createBlockData(state.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return visuals.blockData(state);
    }

    @Override
    public BlockStateRef stateAt(Block block) {
        return WorldBlocks.stateOf(block, custom);
    }

    @Override
    public void destroy(Block block) {
        World world = block.getWorld();
        BlockStateRef state = stateAt(block);
        world.playEffect(block.getLocation().add(0.5, 0.5, 0.5), Effect.STEP_SOUND, block.getBlockData());
        if (on(world, tileDrops)) {
            Optional<LootTable> table = loot.tables().forBlock(state.id());
            if (table.isPresent()) {
                loot.dropBlock(block, table.get(), LootContext.builder().block(state.id(), state.properties())
                        .tool(LootContext.Tool.EMPTY).build());
            } else if ("minecraft".equals(state.namespace())) {
                block.breakNaturally(false); // vanilla's own drops with no tool
                return;
            }
        }
        if (!custom.remove(block)) {
            block.setType(Material.AIR);
        }
    }

    @Override
    public void remove(Block block) {
        if (!custom.remove(block)) {
            block.setType(Material.AIR, true);
        }
    }

    @Override
    public void hurt(LivingEntity target, float amount, FallingBlock source) {
        target.damage(amount, DamageSource.builder(DamageType.FALLING_BLOCK).withDirectEntity(source).build());
    }

    @Override
    public boolean place(Block block, BlockStateRef state) {
        if ("minecraft".equals(state.namespace())) {
            Optional<BlockData> data = blockData(state);
            data.ifPresent(d -> block.setBlockData(d, true));
            return data.isPresent();
        }
        return custom.place(block, state, true);
    }

    @Override
    public void dropAsItem(Location at, BlockStateRef state) {
        World world = at.getWorld();
        if (!on(world, entityDrops)) {
            return;
        }
        Optional<LootTable> table = loot.tables().forBlock(state.id());
        List<ItemStack> stacks;
        if (table.isPresent()) {
            stacks = loot.stacks(loot.roll(table.get(), LootContext.builder().block(state.id(), state.properties())
                    .tool(LootContext.Tool.EMPTY).build()), null);
        } else {
            Optional<BlockData> data = blockData(state);
            Material item = data.map(BlockData::getPlacementMaterial).orElse(Material.AIR);
            stacks = item.isItem() && !item.isAir() ? List.of(new ItemStack(item)) : List.of();
        }
        for (ItemStack stack : stacks) {
            world.dropItemNaturally(at, stack);
        }
    }
}
