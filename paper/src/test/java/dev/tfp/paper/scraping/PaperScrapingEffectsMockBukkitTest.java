/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scraping;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.scraping.ScrapingState;
import dev.tfp.paper.testing.MockServerTest;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** PAR-PROC-05: the surface display's tints, marker and orientation. */
class PaperScrapingEffectsMockBukkitTest extends MockServerTest {

    @Test
    void theTintsAreTfcsColourOneAndTwoWhiteWithoutADye() {
        // MockBukkit's meta keeps neither item_model nor custom_model_data; the in-game check shows the item itself
        ScrapingState s = new ScrapingState((1 << 3) | (1 << 10), true, DyeColor.RED.ordinal(), ScrapingState.NO_COLOR,
            "tfp:scraping/hide/medium/soaked");
        assertThat(PaperScrapingEffects.colors(s)).containsExactly(DyeColor.RED.getColor(), Color.WHITE);
        assertThat(PaperScrapingEffects.colors(ScrapingState.FRESH)).containsExactly(Color.WHITE, Color.WHITE);
    }

    @Test
    void theMarkerIsTheBlockPosition() {
        var world = server.addSimpleWorld("marker");
        assertThat(PaperScrapingEffects.marker(world.getBlockAt(3, 70, -7))).isEqualTo("3,70,-7");
    }

    private static final ScrapingState LOOK = new ScrapingState(1, false, ScrapingState.NO_COLOR, ScrapingState.NO_COLOR,
        "tfp:scraping/paper/unrefined");

    private static List<ItemDisplay> displaysAt(Block block) {
        return block.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
            .filter(d -> PaperScrapingEffects.marker(block).equals(
                d.getPersistentDataContainer().get(PaperScrapingEffects.MARKER, PersistentDataType.STRING)))
            .toList();
    }

    @Test
    void theDisplayIsTurnedSoCellZeroIsTheNorthWestCorner() {
        // The item display renderer turns the model by pi around Y; yaw 180 (FIXED billboard: rotationYXZ(-yaw)) cancels it,
        // so ScrapingArt's cell 0 at model (0, 0) is drawn at the block's min x / min z, where ScrapingAim puts cell 0.
        var world = server.addSimpleWorld("orientation");
        Block block = world.getBlockAt(2, 65, 5);
        new PaperScrapingEffects().show(block, LOOK);
        var shown = displaysAt(block);
        assertThat(shown).hasSize(1);
        ItemDisplay d = shown.getFirst();
        assertThat(d.getYaw()).isEqualTo(PaperScrapingEffects.SURFACE_YAW).isIn(180f, -180f);
        assertThat(d.getPitch()).isZero();
        assertThat(d.getItemDisplayTransform()).isEqualTo(ItemDisplay.ItemDisplayTransform.NONE);
        assertThat(d.getLocation().getX()).isEqualTo(2.5);
        assertThat(d.getLocation().getZ()).isEqualTo(5.5);
    }

    @Test
    void anExistingDisplayIsReusedTurnedAndDuplicatesGo() {
        var world = server.addSimpleWorld("reuse");
        Block block = world.getBlockAt(0, 65, 0);
        for (int i = 0; i < 2; i++) {
            world.spawn(new Location(world, 0.5, 65.5, 0.5), ItemDisplay.class, d -> d.getPersistentDataContainer()
                .set(PaperScrapingEffects.MARKER, PersistentDataType.STRING, "0,65,0"));
        }
        new PaperScrapingEffects().show(block, LOOK);
        var shown = displaysAt(block).stream().filter(Entity::isValid).toList();
        assertThat(shown).hasSize(1);
        assertThat(shown.getFirst().getYaw()).isIn(180f, -180f);
    }
}
