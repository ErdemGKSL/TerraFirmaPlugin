/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/CollapseRecipe.java @ c3b4d9536 (getRecipe, canCollapse, the tag checks)
 */
package dev.tfp.paper.collapse;

import dev.tfp.core.falling.FallCell;
import dev.tfp.core.falling.FallRules;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.paper.falling.FallWorld;
import dev.tfp.paper.falling.FallingBlocks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.tags.Tags;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

/**
 * The server {@link CollapseWorld}: collapse recipes from the loaded recipe index ({@code tfc:collapse}, the result per
 * block id cached until the index is rebuilt), the collapse tags from {@link Tags}, full blocks from the live collision
 * shape, sturdy tops, toughness and breakability from the falling-block world (PAR-PHYS-01), and the falling block from
 * {@link FallingBlocks}.
 */
public final class BukkitCollapseWorld implements CollapseWorld {
    public static final String CAN_TRIGGER = "tfp:can_trigger_collapse";
    public static final String CAN_START = "tfp:can_start_collapse";
    public static final String CAN_COLLAPSE = "tfp:can_collapse";
    public static final String NOT_SOLID_SUPPORTING = "tfp:not_solid_supporting";

    private record Recipes(RecipeIndex index, TagLookup tags, Map<String, Optional<Optional<BlockStateRef>>> byId) {}

    private final Tags tags;
    private final Supplier<RecipeIndex> recipes;
    private final CustomBlocks custom;
    private final FallWorld fall;
    private final FallingBlocks falling;
    private final Predicate<Block> fullBlock;
    private volatile Recipes cache;

    public BukkitCollapseWorld(Tags tags, Supplier<RecipeIndex> recipes, CustomBlocks custom, FallWorld fall,
            FallingBlocks falling) {
        this(tags, recipes, custom, fall, falling, BukkitCollapseWorld::isCollisionShapeFullBlock);
    }

    /** @param fullBlock TFC {@code isCollisionShapeFullBlock} (tests replace the server's collision shapes) */
    BukkitCollapseWorld(Tags tags, Supplier<RecipeIndex> recipes, CustomBlocks custom, FallWorld fall,
            FallingBlocks falling, Predicate<Block> fullBlock) {
        this.tags = tags;
        this.recipes = recipes;
        this.custom = custom;
        this.fall = fall;
        this.falling = falling;
        this.fullBlock = fullBlock;
    }

    /** The collision shape is exactly one unit cube. */
    static boolean isCollisionShapeFullBlock(Block block) {
        Collection<BoundingBox> boxes = block.getCollisionShape().getBoundingBoxes();
        if (boxes.size() != 1) {
            return false;
        }
        BoundingBox b = boxes.iterator().next();
        return b.getMinX() <= 0 && b.getMinY() <= 0 && b.getMinZ() <= 0 && b.getMaxX() >= 1 && b.getMaxY() >= 1
                && b.getMaxZ() >= 1;
    }

    private static boolean airOrFluid(Material type) {
        return type.isAir() || type == Material.WATER || type == Material.LAVA;
    }

    private String id(Block block) {
        return custom.stateAt(block).map(BlockStateRef::id).orElseGet(() -> block.getType().getKey().toString());
    }

    /** The collapse recipe accepting {@code id}: empty when none; inside, the result, or empty when it keeps the block. */
    private Optional<Optional<BlockStateRef>> recipe(String id) {
        RecipeIndex index = recipes.get();
        Recipes c = cache;
        if (c == null || c.index != index) {
            c = new Recipes(index, ItemIngredientContext.lookup(tags.snapshot()), new ConcurrentHashMap<>());
            cache = c;
        }
        Recipes current = c;
        return current.byId.computeIfAbsent(id, k -> current.index
                .firstForBlock(RecipeTypes.COLLAPSE, k, current.tags, r -> true)
                .map(BukkitCollapseWorld::result));
    }

    private static Optional<BlockStateRef> result(RecipeEntry r) {
        return r.recipe().get("result", dev.tfp.core.recipe.block.BlockStateRef.class)
                .map(s -> BlockStateRef.of(s.block(), s.properties()));
    }

    @Override
    public boolean canTrigger(Block block) {
        return !airOrFluid(block.getType()) && tags.is(TagType.BLOCK, id(block), CAN_TRIGGER);
    }

    @Override
    public CollapseState.Cell cell(Block block) {
        if (airOrFluid(block.getType())) {
            return CollapseState.Cell.AIR;
        }
        BlockStateRef state = fall.stateAt(block);
        FallCell f = fall.cell(block);
        Optional<Optional<BlockStateRef>> recipe = recipe(state.id());
        String rubble = null;
        if (recipe.isPresent()) {
            rubble = recipe.get().orElse(state).toString();
        } else if (tags.is(state, CAN_COLLAPSE)) {
            rubble = state.toString(); // TFC's fallback recipe keeps the block
        }
        return new CollapseState.Cell(state.id(), state.toString(), fullBlock.test(block), f.sturdy(), f.toughness(),
                f.unbreakable(), tags.is(state, CAN_START), tags.is(state, CAN_TRIGGER),
                tags.is(state, NOT_SOLID_SUPPORTING), rubble);
    }

    @Override
    public boolean canFall(String state) {
        try {
            return fall.blockData(BlockStateRef.parse(state)).isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean spawnFalling(Block at, String state) {
        return falling.spawn(at.getWorld(), at.getX(), at.getY(), at.getZ(), BlockStateRef.parse(state),
                FallRules.COLLAPSE_DAMAGE_PER_BLOCK, FallRules.COLLAPSE_MAX_DAMAGE).isPresent();
    }

    @Override
    public void remove(Block block) {
        fall.remove(block);
    }

    @Override
    public void destroy(Block block) {
        fall.destroy(block);
    }

    @Override
    public void sound(Block block, CollapseSound sound) {
        // TFC's tfc:random.rock_slide_* sounds are not in our pack: vanilla stone and gravel sounds stand in (ledger)
        Sound vanilla = sound == CollapseSound.SHORT ? Sound.BLOCK_GRAVEL_BREAK : Sound.BLOCK_STONE_BREAK;
        float pitch = sound == CollapseSound.SHORT ? 1.0f : 0.5f;
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), vanilla, SoundCategory.BLOCKS, sound.volume,
                pitch);
    }

    @Override
    public void fakeStart(Block block) {
        if (!airOrFluid(block.getType())) {
            block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getBlockData());
        }
    }
}
