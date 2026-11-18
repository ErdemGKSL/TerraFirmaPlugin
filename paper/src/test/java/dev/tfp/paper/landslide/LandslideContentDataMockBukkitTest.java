/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.landslide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.falling.FallCell;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
import dev.tfp.core.world.Direction;
import dev.tfp.paper.falling.FallWorld;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.FakeCustomBlocks;
import dev.tfp.paper.integration.FakeCustomItems;
import dev.tfp.paper.recipe.RecipeIndex;
import dev.tfp.paper.recipe.RecipeModule;
import dev.tfp.paper.recipe.TagMembers;
import dev.tfp.paper.tags.ServerTags;
import dev.tfp.paper.tags.TagRules;
import dev.tfp.paper.tags.Tags;
import dev.tfp.paper.testing.MockServerTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * PAR-PHYS-03 against the generated content data: {@link BukkitLandslideWorld} finds TFC's landslide recipes and tags as
 * datagen exported them (grass and farmland slide as dirt, gravel slides as itself through {@code #can_landslide},
 * vanilla dirt has its own recipe, stone and air do not slide, farmland supports a landslide).
 */
class LandslideContentDataMockBukkitTest extends MockServerTest {
    private static final String GRASS = "tfp:grass/fluvisol";
    private static final String FARMLAND = "tfp:farmland/fluvisol";
    private static final String GRAVEL = "tfp:rock/gravel/granite";
    private static final String CLAY_GRASS = "tfp:kaolin_clay_grass";

    private final FakeCustomBlocks custom = new FakeCustomBlocks().known(GRASS, FARMLAND, GRAVEL, CLAY_GRASS);

    private BukkitLandslideWorld load() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path root = Path.of(configured);
        Registries registries = new Registries();
        registries.reload(Runnable::run, () -> {
            try {
                return DataLoader.standard(KnownIds.fromDataRoot(root)).load(List.of(root));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).join();
        TagRules rules = TagRules.load(root);
        Tags tags = new Tags(registries, new FakeCustomItems(), custom, ServerTags.BUKKIT, rules, () -> rules,
                Runnable::run, err -> {
                    throw new AssertionError(err);
                });
        RecipeIndex index = RecipeIndex.build(registries.get(),
                new TagMembers(tags.snapshot(), RecipeModule.vanillaIds()));
        return new BukkitLandslideWorld(tags, () -> index, custom, new Fall(), null,
                block -> EnumSet.allOf(Direction.class));
    }

    private Block at(WorldMock world, int x, String id) {
        Block block = world.getBlockAt(x, 100, 0);
        block.setType(Material.DIRT);
        assertThat(custom.place(block, BlockStateRef.of(id), false)).isTrue();
        return block;
    }

    @Test
    void recipesAndTagsComeFromTheExportedData() {
        BukkitLandslideWorld landslide = load();
        WorldMock world = server.addSimpleWorld("landslide-data");
        Block grass = at(world, 0, GRASS);
        Block farmland = at(world, 2, FARMLAND);
        Block gravel = at(world, 4, GRAVEL);
        Block clayGrass = at(world, 6, CLAY_GRASS);
        Block dirt = world.getBlockAt(8, 100, 0);
        dirt.setType(Material.DIRT);
        Block stone = world.getBlockAt(10, 100, 0);
        stone.setType(Material.STONE);
        Block air = world.getBlockAt(12, 100, 0);

        assertThat(landslide.canLandslide(grass)).isTrue();
        assertThat(landslide.canLandslide(farmland)).isTrue();
        assertThat(landslide.canLandslide(gravel)).as("#can_landslide fallback").isTrue();
        assertThat(landslide.canLandslide(clayGrass)).isTrue();
        assertThat(landslide.canLandslide(dirt)).as("minecraft:dirt has its own recipe").isTrue();
        assertThat(landslide.canLandslide(stone)).isFalse();
        assertThat(landslide.canLandslide(air)).isFalse();

        assertThat(landslide.cell(grass).result()).isEqualTo("tfp:dirt/fluvisol");
        assertThat(landslide.cell(farmland).result()).isEqualTo("tfp:dirt/fluvisol");
        assertThat(landslide.cell(farmland).supports()).as("#supports_landslide via #farmlands").isTrue();
        assertThat(landslide.cell(grass).supports()).isFalse();
        assertThat(landslide.cell(gravel).result()).as("slides as itself").isEqualTo(GRAVEL);
        assertThat(landslide.cell(clayGrass).result()).isEqualTo("tfp:red_kaolin_clay");
        assertThat(landslide.cell(dirt).result()).isEqualTo("minecraft:dirt");
        assertThat(landslide.cell(stone).result()).isNull();
        assertThat(landslide.cell(air)).isEqualTo(SlideState.Cell.AIR);
    }

    /** Only the reads {@link BukkitLandslideWorld#cell} uses. */
    private final class Fall implements FallWorld {
        @Override
        public FallCell cell(Block block) {
            return new FallCell(false, true, 0, false, false);
        }

        @Override
        public int toughness(BlockStateRef falling) {
            return 0;
        }

        @Override
        public boolean survives(BlockStateRef falling, Block at) {
            return true;
        }

        @Override
        public boolean isMovingPiston(Block block) {
            return false;
        }

        @Override
        public double[][] collisionHeights(Block block) {
            return new double[0][];
        }

        @Override
        public Optional<BlockData> blockData(BlockStateRef state) {
            return Optional.empty();
        }

        @Override
        public BlockStateRef stateAt(Block block) {
            return custom.stateAt(block).orElseGet(() -> BlockStateRef.of(block.getType().getKey().toString()));
        }

        @Override
        public void destroy(Block block) {
        }

        @Override
        public void remove(Block block) {
        }

        @Override
        public void hurt(LivingEntity target, float amount, FallingBlock source) {
        }

        @Override
        public boolean place(Block block, BlockStateRef state) {
            return false;
        }

        @Override
        public void dropAsItem(Location at, BlockStateRef state) {
        }
    }
}
