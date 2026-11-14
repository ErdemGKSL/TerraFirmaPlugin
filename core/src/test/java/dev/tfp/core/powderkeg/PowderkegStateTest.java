/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.powderkeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.gui.ChestGeometry;
import org.junit.jupiter.api.Test;

/** PAR-PROC-18: the keg's state machine (TFC useItemOn, toggleSeal, setLit, serverTick) and the screen layout. */
class PowderkegStateTest {

    @Test
    void clickOrderIsTfcs() {
        PowderkegState s = PowderkegState.FRESH;
        assertThat(s.click(true, true)).isEqualTo(PowderkegState.Click.TOGGLE_SEAL);
        assertThat(s.click(true, false)).isEqualTo(PowderkegState.Click.OPEN);
        assertThat(s.click(false, true)).isEqualTo(PowderkegState.Click.OPEN);
        assertThat(s.withSealed(true).lit(0, 80).click(true, true)).isEqualTo(PowderkegState.Click.EXTINGUISH);
    }

    @Test
    void sealingChecksTheSignalAndUnsealingPutsOutTheFuse() {
        PowderkegState.Toggle seal = PowderkegState.FRESH.toggleSeal();
        assertThat(seal.state().sealed()).isTrue();
        assertThat(seal.checkSignal()).isTrue();
        assertThat(seal.extinguished()).isFalse();
        assertThat(seal.state().canModify()).isFalse();

        PowderkegState.Toggle unseal = seal.state().lit(100, 80).toggleSeal();
        assertThat(unseal.state()).isEqualTo(PowderkegState.FRESH);
        assertThat(unseal.extinguished()).isTrue();
        assertThat(unseal.checkSignal()).isFalse();
    }

    @Test
    void lazyFuseIsDueAfterFuseTicksLikeTfcsCountdown() {
        PowderkegState lit = PowderkegState.FRESH.withSealed(true).lit(1000, 80);
        // TFC: fuse = 80 when lit, --fuse each tick after, explode when <= 0: 80 ticks after lighting
        int tick = 0;
        int fuse = 80;
        while (true) {
            tick++;
            if (--fuse <= 0) {
                break;
            }
        }
        assertThat(lit.due(1000 + tick - 1)).isFalse();
        assertThat(lit.due(1000 + tick)).isTrue();
        assertThat(lit.unlit().due(5000)).isFalse();
        // relighting resets the fuse (TFC setLit always sets fuse)
        assertThat(lit.lit(1050, 80).fuseEnd()).isEqualTo(1130);
    }

    @Test
    void lightingRules() {
        PowderkegState open = PowderkegState.FRESH;
        PowderkegState sealed = open.withSealed(true);
        assertThat(open.lightsFromSignal(true)).isFalse();
        assertThat(sealed.lightsFromSignal(true)).isTrue();
        assertThat(sealed.lightsFromSignal(false)).isFalse();
        assertThat(sealed.lit(0, 80).lightsFromSignal(true)).isFalse();
        assertThat(sealed.lightsFromFire(true)).isTrue();
        assertThat(sealed.lightsFromFire(false)).isFalse();
        assertThat(open.lightsFromFire(true)).isFalse();
        assertThat(open.lightsFromExplosion()).isTrue();
        assertThat(sealed.lit(0, 80).lightsFromExplosion()).isFalse();
    }

    @Test
    void codecRoundTripsAndRefusesInconsistentFuse() {
        PowderkegState s = PowderkegState.FRESH.withSealed(true).lit(123456789L, 80);
        assertThat(PowderkegState.CODEC.decode(PowderkegState.CODEC.encode(s))).isEqualTo(s);
        assertThat(PowderkegState.CODEC.decode(PowderkegState.CODEC.encode(PowderkegState.FRESH))).isEqualTo(PowderkegState.FRESH);
        assertThatThrownBy(() -> new PowderkegState(false, true, PowderkegState.NO_FUSE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void screenSlotsLandOnTheChestGridInTfcOrder() {
        // TFC slot x * 3 + y at (25 + 18x, 19 + 18y) moved by (1, -1): chest columns 1..4, rows 0..2
        assertThat(PowderkegScreenLayout.CHEST_SLOTS).containsExactly(1, 10, 19, 2, 11, 20, 3, 12, 21, 4, 13, 22);
        assertThat(PowderkegScreenLayout.tfcSlot(12)).isEqualTo(7);
        assertThat(PowderkegScreenLayout.tfcSlot(0)).isEqualTo(-1);
        ChestGeometry chest = PowderkegScreenLayout.CHEST;
        // the seal sprite is centred on its button slot
        int sx = chest.slotX(PowderkegScreenLayout.SEAL_SLOT);
        int sy = chest.slotY(PowderkegScreenLayout.SEAL_SLOT);
        assertThat(PowderkegScreenLayout.SEAL.x()).isEqualTo(sx - 2);
        assertThat(PowderkegScreenLayout.SEAL.y()).isEqualTo(sy - 2);
        assertThat(PowderkegScreenLayout.CHEST_SLOTS).doesNotContain(PowderkegScreenLayout.SEAL_SLOT);
        assertThat(PowderkegScreenLayout.frames(true)).containsEntry("seal", 0).containsEntry("disabled", 0);
        assertThat(PowderkegScreenLayout.frames(false)).containsEntry("seal", 1).containsEntry("disabled", -1);
        assertThat(PowderkegScreenLayout.DY_PLAYER).isEqualTo(1);
    }
}
