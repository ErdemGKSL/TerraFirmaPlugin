/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.scraping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.craft.ScrapingRules;
import dev.tfp.core.persist.DataMap;
import org.junit.jupiter.api.Test;

/** PAR-PROC-05: the scraping device state (TFC ScrapingBlockEntity positions/colours + the waxed property). */
class ScrapingStateTest {

    @Test
    void sixteenDistinctClicksCompleteAndARepeatChangesNothing() {
        ScrapingState s = ScrapingState.FRESH.withLook("tfp:scraping/hide/medium/soaked");
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 4; x++) {
                ScrapingState.Scraped step = s.scrape((x + 0.5) / 4, (z + 0.5) / 4);
                assertThat(step.step().changed()).isTrue();
                assertThat(step.step().complete()).isEqualTo(x == 3 && z == 3);
                s = step.state();
                ScrapingState.Scraped again = s.scrape((x + 0.5) / 4, (z + 0.5) / 4);
                assertThat(again.step().changed()).as("TFC onClicked returns false on a scraped cell").isFalse();
                assertThat(again.state()).isSameAs(s);
            }
        }
        assertThat(s.complete()).isTrue();
        assertThat(s.mask()).isEqualTo(ScrapingRules.COMPLETE);
        assertThat(s.look()).as("TFC keeps the cached textures after completion").isEqualTo("tfp:scraping/hide/medium/soaked");
    }

    @Test
    void theCellBitIsXPlusFourZ() {
        // TFC: positions |= 1 << (xPos + zPos * 4)
        assertThat(ScrapingState.FRESH.scrape(0.8, 0.3).state().mask()).isEqualTo(1 << (3 + 4));
        assertThat(ScrapingState.FRESH.scrape(0.0, 0.99).state().mask()).isEqualTo(1 << 12);
    }

    @Test
    void dyeFillsColourOneThenTwoThenRefuses() {
        ScrapingState s = ScrapingState.FRESH.waxedState();
        assertThat(s.waxed()).isTrue();
        s = s.dye(14).orElseThrow();
        assertThat(s.color1()).isEqualTo(14);
        assertThat(s.color2()).isEqualTo(ScrapingState.NO_COLOR);
        s = s.dye(11).orElseThrow();
        assertThat(s.color2()).isEqualTo(11);
        assertThat(s.dye(0)).isEmpty();
        assertThatThrownBy(() -> ScrapingState.FRESH.dye(16)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void codecRoundTripsAndReadsOldDefaults() {
        ScrapingState s = new ScrapingState(0xA5A5, true, 3, 15, "tfp:scraping/paper");
        assertThat(ScrapingState.CODEC.decode(ScrapingState.CODEC.encode(s))).isEqualTo(s);
        DataMap minimal = new DataMap().putInt("v", 1).putInt("mask", 7);
        assertThat(ScrapingState.CODEC.decode(minimal)).isEqualTo(new ScrapingState(7, false, -1, -1, ""));
    }

    @Test
    void refusesBadValues() {
        assertThatThrownBy(() -> new ScrapingState(1 << 16, false, -1, -1, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScrapingState(0, false, -2, -1, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScrapingState(0, false, -1, -1, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
