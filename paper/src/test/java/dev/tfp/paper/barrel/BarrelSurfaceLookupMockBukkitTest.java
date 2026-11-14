/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.paper.barrel.gui.BarrelFluidVisuals;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/**
 * PAR-PROC-38 in-game finding: the fluid surface display stands at the barrel block's bottom face (y = block y; the plane's
 * height is in its transformation). A display has a zero-size bounding box, and the server's entity lookup takes only boxes
 * that overlap the search box strictly, so a search over the block's own box never found it: every surface update spawned
 * another display (four stacked planes in one barrel after a fill and a few tap settles). The lookup box is centred on the
 * bottom face.
 */
class BarrelSurfaceLookupMockBukkitTest extends MockServerTest {

    @Test
    void theSearchBoxStrictlyContainsTheDisplayOrigin() {
        var world = server.addSimpleWorld("surface");
        Block block = world.getBlockAt(4, 70, -3);
        BoundingBox box = PaperBarrelEffects.surfaceSearch(block);
        BoundingBox origin = BoundingBox.of(new Location(world, 4.5, 70, -2.5), 0, 0, 0);
        // the strict test the server's entity lookup applies to a display's zero-size box
        assertThat(box.overlaps(origin)).isTrue();
        assertThat(BoundingBox.of(block).overlaps(origin)).as("the old search box").isFalse();
        // the display of the barrel above or below lies outside
        assertThat(box.overlaps(BoundingBox.of(new Location(world, 4.5, 71, -2.5), 0, 0, 0))).isFalse();
        assertThat(box.overlaps(BoundingBox.of(new Location(world, 4.5, 69, -2.5), 0, 0, 0))).isFalse();
    }

    @Test
    void aDisplayAtTheBottomFaceIsFound() {
        var world = server.addSimpleWorld("found");
        Block block = world.getBlockAt(0, 65, 0);
        world.spawn(new Location(world, 0.5, 65, 0.5), ItemDisplay.class, d -> d.getPersistentDataContainer()
            .set(PaperBarrelEffects.SURFACE, PersistentDataType.STRING, PaperBarrelEffects.marker(block)));
        assertThat(PaperBarrelEffects.surfaces(block)).hasSize(1);
    }

    @Test
    void successiveUpdatesKeepOnePlaneAtTfcsHeight() {
        var world = server.addSimpleWorld("updates");
        Block block = world.getBlockAt(2, 66, 2);
        PaperBarrelEffects effects = new PaperBarrelEffects(new BarrelTestScheduler(),
            new dev.tfp.paper.integration.FakeCustomBlocks(), () -> null);
        effects.surface(block, new FluidAmount("minecraft:water", 720), 10_000);
        effects.surface(block, new FluidAmount("minecraft:water", 1000), 10_000);
        var planes = PaperBarrelEffects.surfaces(block);
        assertThat(planes).as("the stacking bug: one display, updated in place").hasSize(1);
        assertThat(planes.getFirst().getTransformation().getTranslation().y())
            .isEqualTo(BarrelFluidVisuals.surface(1000, 10_000).y());
        effects.surface(block, FluidAmount.EMPTY, 10_000);
        assertThat(PaperBarrelEffects.surfaces(block)).isEmpty();
    }
}
