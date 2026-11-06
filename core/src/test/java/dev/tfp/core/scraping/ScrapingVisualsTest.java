/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.scraping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-PROC-05: the names datagen and the Paper side share for the scraping surface look. */
class ScrapingVisualsTest {

    @Test
    void namesFollowTheRecipeTexture() {
        assertThat(ScrapingVisuals.key("tfp:item/hide/medium/soaked")).isEqualTo("hide/medium/soaked");
        assertThat(ScrapingVisuals.key("tfp:block/unrefined_paper")).isEqualTo("unrefined_paper");
        assertThat(ScrapingVisuals.itemModel("tfp:item/hide/large/sheepskin")).isEqualTo("tfp:scraping/hide/large/sheepskin");
        assertThat(ScrapingVisuals.texture("tfp:block/paper")).isEqualTo("tfp:scraping/paper");
        assertThat(ScrapingVisuals.cellParent(5)).isEqualTo("tfp:block/scraping/cell_5");
        assertThat(ScrapingVisuals.tile("tfp:block/paper", 15)).isEqualTo("tfp:block/scraping/paper/cell_15");
        assertThatThrownBy(() -> ScrapingVisuals.key("tfp:gui/x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScrapingVisuals.tile("tfp:block/paper", 16)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cellsAreColumnPlusFourTimesRow() {
        assertThat(ScrapingVisuals.column(6)).isEqualTo(2);
        assertThat(ScrapingVisuals.row(6)).isEqualTo(1);
    }

    @Test
    void flagIIsCellIScraped() {
        List<Boolean> flags = ScrapingVisuals.flags((1 << 0) | (1 << 9) | (1 << 15));
        assertThat(flags).hasSize(16);
        for (int i = 0; i < 16; i++) {
            assertThat(flags.get(i)).as("cell " + i).isEqualTo(i == 0 || i == 9 || i == 15);
        }
    }
}
