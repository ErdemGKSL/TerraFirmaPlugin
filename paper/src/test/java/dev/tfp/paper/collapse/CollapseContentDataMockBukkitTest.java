/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.collapse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.tfp.core.collapse.SupportTable;
import dev.tfp.core.falling.FallCell;
import dev.tfp.core.registry.DataLoader;
import dev.tfp.core.registry.KnownIds;
import dev.tfp.core.registry.Registries;
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
 * PAR-PHYS-02 against the generated content data: {@link BukkitCollapseWorld} finds TFC's collapse tags and recipes as
 * datagen exported them (raw rock triggers, starts and collapses into cobble; rich ore collapses into normal ore, normal
 * into poor, poor into cobble; smooth rock is not solid supporting; stone bricks do not collapse), and
 * {@link CollapseSupports} loads TFC's one support definition (the horizontal beams, 2 up, 2 down, 4 sideways).
 */
class CollapseContentDataMockBukkitTest extends MockServerTest {
    private static final String RAW = "tfp:rock/raw/granite";
    private static final String COBBLE = "tfp:rock/cobble/granite";
    private static final String SMOOTH = "tfp:rock/smooth/granite";
    private static final String RICH = "tfp:ore/rich_hematite/granite";
    private static final String NORMAL = "tfp:ore/normal_hematite/granite";
    private static final String POOR = "tfp:ore/poor_hematite/granite";
    private static final String BRICKS = "tfp:rock/bricks/granite";

    private final FakeCustomBlocks custom = new FakeCustomBlocks().known(RAW, COBBLE, SMOOTH, RICH, NORMAL, POOR, BRICKS);
    private Registries registries;

    private BukkitCollapseWorld load() {
        String configured = System.getProperty("tfp.contentData");
        assumeTrue(configured != null && Files.isDirectory(Path.of(configured)), "no generated content");
        Path root = Path.of(configured);
        registries = new Registries();
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
        return new BukkitCollapseWorld(tags, () -> index, custom, new Fall(), null, block -> true);
    }

    private Block at(WorldMock world, int x, String id) {
        Block block = world.getBlockAt(x, 100, 0);
        block.setType(Material.STONE);
        assertThat(custom.place(block, BlockStateRef.of(id), false)).isTrue();
        return block;
    }

    @Test
    void tagsAndRecipesComeFromTheExportedData() {
        BukkitCollapseWorld collapse = load();
        WorldMock world = server.addSimpleWorld("collapse-data");
        Block raw = at(world, 0, RAW);
        Block smooth = at(world, 2, SMOOTH);
        Block rich = at(world, 4, RICH);
        Block normal = at(world, 6, NORMAL);
        Block poor = at(world, 8, POOR);
        Block bricks = at(world, 10, BRICKS);
        Block air = world.getBlockAt(12, 100, 0);

        assertThat(collapse.canTrigger(raw)).isTrue();
        assertThat(collapse.canTrigger(rich)).as("#c:ores").isTrue();
        assertThat(collapse.canTrigger(bricks)).isFalse();
        assertThat(collapse.canTrigger(air)).isFalse();

        CollapseState.Cell rawCell = collapse.cell(raw);
        assertThat(rawCell.canStart()).isTrue();
        assertThat(rawCell.canTrigger()).isTrue();
        assertThat(rawCell.rubble()).isEqualTo(COBBLE);
        assertThat(rawCell.id()).isEqualTo(RAW);
        assertThat(collapse.cell(smooth).rubble()).isEqualTo(COBBLE);
        assertThat(collapse.cell(smooth).notSolidSupporting()).isTrue();
        assertThat(collapse.cell(rich).rubble()).isEqualTo(NORMAL);
        assertThat(collapse.cell(normal).rubble()).isEqualTo(POOR);
        assertThat(collapse.cell(poor).rubble()).isEqualTo(COBBLE);
        assertThat(collapse.cell(bricks).rubble()).isNull();
        assertThat(collapse.cell(air)).isEqualTo(CollapseState.Cell.AIR);
    }

    @Test
    void theSupportTableLoadsTheBeamDefinition() {
        load();
        CollapseSupports supports = new CollapseSupports(() -> registries);
        SupportTable table = supports.get();
        Optional<SupportTable.Support> beam = table.get("tfp:wood/horizontal_support/oak");
        assertThat(beam).isPresent();
        assertThat(beam.get().up()).isEqualTo(2);
        assertThat(beam.get().down()).isEqualTo(2);
        assertThat(beam.get().horizontal()).isEqualTo(4);
        assertThat(table.rangeHorizontal()).isEqualTo(4);
        assertThat(supports.get()).as("cached per registry version").isSameAs(table);
        SupportTable none = new SupportTable(List.of());
        supports.override(none);
        assertThat(supports.get()).isSameAs(none);
    }

    /** Only the reads {@link BukkitCollapseWorld#cell} uses. */
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
