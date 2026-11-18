/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/LandslideRecipe.java @ c3b4d9536 (getRecipe, canLandslide)
 */
package dev.tfp.paper.landslide;

import dev.tfp.core.falling.FallCell;
import dev.tfp.core.falling.FallRules;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.falling.FallWorld;
import dev.tfp.paper.falling.FallingBlocks;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.recipe.ItemIngredientContext;
import dev.tfp.paper.recipe.RecipeEntry;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.RecipeTypes;
import dev.tfp.paper.tags.Tags;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;

/**
 * The server {@link LandslideWorld}: landslide recipes from the loaded recipe index ({@code tfc:landslide}, the result
 * per block id cached until the index is rebuilt), the {@code #tfp:can_landslide} and {@code #tfp:supports_landslide}
 * tags from {@link Tags}, sturdy faces from the live server state, toughness and breakability from the falling-block
 * world (PAR-PHYS-01), and the falling block from {@link FallingBlocks}.
 */
public final class BukkitLandslideWorld implements LandslideWorld {
    public static final String CAN_LANDSLIDE = "tfp:can_landslide";
    public static final String SUPPORTS_LANDSLIDE = "tfp:supports_landslide";

    private static final Map<Direction, BlockFace> FACES = Map.of(Direction.DOWN, BlockFace.DOWN, Direction.UP,
            BlockFace.UP, Direction.NORTH, BlockFace.NORTH, Direction.SOUTH, BlockFace.SOUTH, Direction.WEST,
            BlockFace.WEST, Direction.EAST, BlockFace.EAST);

    private record Recipes(RecipeIndex index, TagLookup tags, Map<String, Optional<Optional<BlockStateRef>>> byId) {}

    private final Tags tags;
    private final Supplier<RecipeIndex> recipes;
    private final CustomBlocks custom;
    private final FallWorld fall;
    private final FallingBlocks falling;
    private final Function<Block, Set<Direction>> sturdyFaces;
    private volatile Recipes cache;

    public BukkitLandslideWorld(Tags tags, Supplier<RecipeIndex> recipes, CustomBlocks custom, FallWorld fall,
            FallingBlocks falling) {
        this(tags, recipes, custom, fall, falling, BukkitLandslideWorld::sturdyFaces);
    }

    /** @param sturdyFaces the sturdy faces of a block (tests replace the server's {@code isFaceSturdy}) */
    BukkitLandslideWorld(Tags tags, Supplier<RecipeIndex> recipes, CustomBlocks custom, FallWorld fall,
            FallingBlocks falling, Function<Block, Set<Direction>> sturdyFaces) {
        this.sturdyFaces = sturdyFaces;
        this.tags = tags;
        this.recipes = recipes;
        this.custom = custom;
        this.fall = fall;
        this.falling = falling;
    }

    private static boolean airOrFluid(Material type) {
        return type.isAir() || type == Material.WATER || type == Material.LAVA;
    }

    private String id(Block block) {
        return custom.stateAt(block).map(BlockStateRef::id).orElseGet(() -> block.getType().getKey().toString());
    }

    /**
     * The landslide recipe accepting {@code id}: empty when none; inside, the recipe's result, or empty when the recipe
     * keeps the block (no output).
     */
    private Optional<Optional<BlockStateRef>> recipe(String id) {
        RecipeIndex index = recipes.get();
        Recipes c = cache;
        if (c == null || c.index != index) {
            c = new Recipes(index, ItemIngredientContext.lookup(tags.snapshot()), new ConcurrentHashMap<>());
            cache = c;
        }
        Recipes current = c;
        return current.byId.computeIfAbsent(id, k -> current.index
                .firstForBlock(RecipeTypes.LANDSLIDE, k, current.tags, r -> true)
                .map(BukkitLandslideWorld::result));
    }

    private static Optional<BlockStateRef> result(RecipeEntry r) {
        return r.recipe().get("result", dev.tfp.core.recipe.block.BlockStateRef.class)
                .map(s -> BlockStateRef.of(s.block(), s.properties()));
    }

    @Override
    public boolean canLandslide(Block block) {
        if (airOrFluid(block.getType())) {
            return false;
        }
        String id = id(block);
        return recipe(id).isPresent() || tags.is(TagType.BLOCK, id, CAN_LANDSLIDE);
    }

    @Override
    public SlideState.Cell cell(Block block) {
        if (airOrFluid(block.getType())) {
            return SlideState.Cell.AIR;
        }
        BlockStateRef state = fall.stateAt(block);
        Set<Direction> sturdy = sturdyFaces.apply(block);
        FallCell f = fall.cell(block);
        Optional<Optional<BlockStateRef>> recipe = recipe(state.id());
        String result = null;
        if (recipe.isPresent()) {
            result = recipe.get().orElse(state).toString();
        } else if (tags.is(state, CAN_LANDSLIDE)) {
            result = state.toString();
        }
        return new SlideState.Cell(state.id(), sturdy, f.toughness(), f.unbreakable(), tags.is(state, SUPPORTS_LANDSLIDE),
                result);
    }

    /** {@code isFaceSturdy(face, FULL)} of the live server state, for each face. */
    static Set<Direction> sturdyFaces(Block block) {
        BlockData data = block.getBlockData();
        Set<Direction> sturdy = EnumSet.noneOf(Direction.class);
        for (Map.Entry<Direction, BlockFace> e : FACES.entrySet()) {
            if (data.isFaceSturdy(e.getValue(), BlockSupport.FULL)) {
                sturdy.add(e.getKey());
            }
        }
        return sturdy;
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
                FallRules.LANDSLIDE_DAMAGE_PER_BLOCK, FallRules.LANDSLIDE_MAX_DAMAGE).isPresent();
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
    public void slideSound(Block block) {
        // TFC's tfc:block.dirt_slide_short is not in our pack: the vanilla gravel break sound stands in (ledger)
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_GRAVEL_BREAK,
                SoundCategory.BLOCKS, 0.4f, 1.0f);
    }
}
