/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.scraping;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.scraping.ScrapingState;
import dev.tfp.paper.testing.MockServerTest;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

/** PAR-PROC-05: the surface display's tints and marker. */
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
}
