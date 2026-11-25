/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.channel;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.channel.MoldTableRules.Click;
import org.junit.jupiter.api.Test;

/** PAR-METAL-13: TFC's {@code MoldTableBlockEntity.onRightClick} and {@code getAnalogOutputSignal}. */
class MoldTableRulesTest {
    @Test
    void anEmptyMoldSlotTakesTheHeldMold() {
        assertThat(MoldTableRules.click(false, false, false, true)).isEqualTo(Click.INSERT);
        assertThat(MoldTableRules.click(true, false, false, true)).isEqualTo(Click.INSERT);
        assertThat(MoldTableRules.click(false, false, true, false)).isEqualTo(Click.PASS);
        assertThat(MoldTableRules.click(false, false, false, false)).isEqualTo(Click.PASS);
    }

    @Test
    void sneakingWithAMoldSwapsOrTakesIt() {
        assertThat(MoldTableRules.click(true, true, false, true)).isEqualTo(Click.SWAP);
        assertThat(MoldTableRules.click(true, true, true, false)).isEqualTo(Click.TAKE_MOLD);
        assertThat(MoldTableRules.click(true, true, false, false)).isEqualTo(Click.TAKE_MOLD);
    }

    @Test
    void notSneakingWithAMoldTakesTheOutputOrPasses() {
        assertThat(MoldTableRules.click(false, true, true, true)).isEqualTo(Click.TAKE_OUTPUT);
        assertThat(MoldTableRules.click(false, true, false, true)).isEqualTo(Click.PASS);
    }

    @Test
    void theSignalIs15WithAnOutputAndScalesWithTheFill() {
        assertThat(MoldTableRules.signal(true, true, 0, 100)).isEqualTo(15);
        assertThat(MoldTableRules.signal(false, true, 0, 100)).isEqualTo(1);
        assertThat(MoldTableRules.signal(false, true, 100, 100)).isEqualTo(14);
        assertThat(MoldTableRules.signal(false, true, 50, 100)).isEqualTo(7);
        assertThat(MoldTableRules.signal(false, false, 0, 0)).isEqualTo(0);
    }
}
