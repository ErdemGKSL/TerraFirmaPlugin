/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BellowsBlockEntity.java @ c3b4d9536 (onRightClick, doPush)
 */
package dev.tfp.core.charcoalforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.charcoalforge.BellowsRules.Crank;
import dev.tfp.core.charcoalforge.BellowsRules.Outcome;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The bellows press cadence, the crank and receiver rules, and the air intake cap. */
class BellowsRulesTest {
    @Test
    void pressNeedsTwentyTicksSinceTheLastPush() {
        assertThat(BellowsRules.press(19, 0, Crank.NONE, List.of()).outcome()).isEqualTo(Outcome.PASS);
        BellowsRules.Press at20 = BellowsRules.press(20, 0, Crank.NONE, List.of());
        assertThat(at20.outcome()).isEqualTo(Outcome.PUSHED);
        assertThat(at20.lastPushed()).isEqualTo(20L);
        assertThat(at20.fedConsumers()).isZero();
    }

    @Test
    void pressIsPassedToTheCrankWhenTurning() {
        BellowsRules.Press out = BellowsRules.press(100, 0, Crank.TURNING, List.of(true));
        assertThat(out.outcome()).isEqualTo(Outcome.PASS);
        assertThat(out.lastPushed()).isZero();
        assertThat(out.fedConsumers()).isZero();
    }

    @Test
    void pressFailsWhenACrankIsIdle() {
        BellowsRules.Press out = BellowsRules.press(100, 0, Crank.IDLE, List.of(true));
        assertThat(out.outcome()).isEqualTo(Outcome.FAIL);
        assertThat(out.lastPushed()).isZero();
    }

    @Test
    void pushWithNoConsumersStillPushes() {
        BellowsRules.Press out = BellowsRules.press(100, 0, Crank.NONE, List.of());
        assertThat(out.outcome()).isEqualTo(Outcome.PUSHED);
        assertThat(out.lastPushed()).isEqualTo(100L);
    }

    @Test
    void pushIsRefusedWhenNoConsumerAcceptsAir() {
        BellowsRules.Press out = BellowsRules.press(100, 0, Crank.NONE, List.of(false, false));
        assertThat(out.outcome()).isEqualTo(Outcome.REFUSED);
        assertThat(out.lastPushed()).isZero();
        assertThat(out.fedConsumers()).isZero();
    }

    @Test
    void pushFeedsEveryAcceptingConsumer() {
        BellowsRules.Press out = BellowsRules.press(100, 0, Crank.NONE, List.of(true, false, true));
        assertThat(out.outcome()).isEqualTo(Outcome.PUSHED);
        assertThat(out.fedConsumers()).isEqualTo(2);
        assertThat(out.lastPushed()).isEqualTo(100L);
    }

    @Test
    void refusedPressKeepsTheCooldownOpen() {
        BellowsRules.Press refused = BellowsRules.press(100, 0, Crank.NONE, List.of(false));
        BellowsRules.Press next = BellowsRules.press(101, refused.lastPushed(), Crank.NONE, List.of(true));
        assertThat(next.outcome()).isEqualTo(Outcome.PUSHED);
    }

    @Test
    void intakeAirAddsAndCapsAtTheDeviceMaximum() {
        assertThat(BellowsRules.intakeAir(0, 200)).isEqualTo(200);
        assertThat(BellowsRules.intakeAir(500, 200)).isEqualTo(600);
        assertThat(BellowsRules.intakeAir(600, 0)).isEqualTo(600);
        assertThatThrownBy(() -> BellowsRules.intakeAir(601, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BellowsRules.intakeAir(0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
