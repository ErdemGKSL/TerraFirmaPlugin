/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.collapse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.world.BlockPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M3-PHYS-01 determinism: the same inputs give the same decision, whatever the instance, insertion order or sampling. */
class CollapseDeterminismTest {

    /** Returns the same float for every {@code nextFloat} and the same int (capped to the bound) for every {@code nextInt}. */
    static final class Constant implements CollapseRandom {
        private final float value;
        private final int integer;

        Constant(float value, int integer) {
            this.value = value;
            this.integer = integer;
        }

        @Override
        public float nextFloat() {
            return value;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(integer, bound - 1);
        }
    }

    /** A raw block at the origin (the mined block) and one raw block at (-2, 0, -2), over air. */
    private static Map<BlockPos, Character> oneRawInBox() {
        Map<BlockPos, Character> blocks = new LinkedHashMap<>();
        blocks.put(CollapseGoldenTest.ORIGIN, 'R');
        blocks.put(new BlockPos(-2, 0, -2), 'R');
        return blocks;
    }

    @Test
    void fixedDrawsGiveHandDerivedDecisions() {
        CollapseRules rules = CollapseGoldenTest.rules();
        CollapseView view = CollapseGoldenTest.view(oneRawInBox());
        BlockPos only = new BlockPos(-2, 0, -2);

        // nextFloat 0 < 0.1: a real collapse. The radii draw 2, 1, 2 (nextInt 0). The first startable block in the box is
        // (-2, 0, -2): raw over air, and the air below can be fallen through.
        assertThat(rules.trigger(view, CollapseGoldenTest.ORIGIN, new Constant(0.0f, 0)))
            .isEqualTo(new TriggerDecision.Real(only));

        // nextFloat 0.2: not real (0.2 >= 0.1), then fake (0.2 < 0.35). One startable block, so the display is that block.
        assertThat(rules.trigger(view, CollapseGoldenTest.ORIGIN, new Constant(0.2f, 0)))
            .isEqualTo(new TriggerDecision.Fake(List.of(only), List.of(only)));

        // nextFloat 0.9: neither real nor fake, so nothing, and no radius is drawn.
        assertThat(rules.trigger(view, CollapseGoldenTest.ORIGIN, new Constant(0.9f, 0)))
            .isEqualTo(new TriggerDecision.None());
    }

    @Test
    void sameInputsGiveSameDecisionAcrossInstancesAndInsertionOrders() {
        CollapseRules first = CollapseGoldenTest.rules();
        CollapseRules second = CollapseGoldenTest.rules();
        for (int c = 0; c < 40; c++) {
            CollapseGoldenTest.Lcg a = new CollapseGoldenTest.Lcg(1000003L * c + 29);
            CollapseGoldenTest.Lcg b = new CollapseGoldenTest.Lcg(1000003L * c + 29);
            String viaFirst = CollapseGoldenTest.format(
                first.trigger(CollapseGoldenTest.view(scriptedWorld(c, false)), CollapseGoldenTest.ORIGIN, a));
            String viaSecond = CollapseGoldenTest.format(
                second.trigger(CollapseGoldenTest.view(scriptedWorld(c, true)), CollapseGoldenTest.ORIGIN, b));
            assertThat(viaSecond).as("case %d", c).isEqualTo(viaFirst);
        }
    }

    @Test
    void zeroChancesNeverTrigger() {
        CollapseRules never = new CollapseRules(CollapseGoldenTest.kinds(), CollapseGoldenTest.supports(), 0.0, 0.0);
        for (int c = 0; c < 40; c++) {
            CollapseGoldenTest.Lcg rng = new CollapseGoldenTest.Lcg(1000003L * c + 29);
            assertThat(never.trigger(CollapseGoldenTest.view(scriptedWorld(c, false)), CollapseGoldenTest.ORIGIN, rng))
                .isEqualTo(new TriggerDecision.None());
        }
    }

    @Test
    void chancesOutsideZeroToOneAreRejected() {
        assertThatThrownBy(() -> new CollapseRules(CollapseGoldenTest.kinds(), CollapseGoldenTest.supports(), 1.5, 0.35))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CollapseRules(CollapseGoldenTest.kinds(), CollapseGoldenTest.supports(), 0.1, -0.1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /** The scripted world of the golden trigger test, optionally inserted in reverse order. */
    private static Map<BlockPos, Character> scriptedWorld(int c, boolean reversed) {
        String lower = "RRSOPBRRRP";
        String upper = "AAAARSOLLP";
        int[] beamAt40 = {0, 0, 0, 1};
        CollapseGoldenTest.Lcg rng = new CollapseGoldenTest.Lcg(1000003L * c + 17);
        List<BlockPos> order = new ArrayList<>();
        Map<BlockPos, Character> cells = new HashMap<>();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    int r = rng.nextInt(40);
                    char code = r < beamAt40[c % 4] ? (r % 2 == 0 ? 'K' : 'Y') : (dy <= -2 ? lower : upper).charAt(r % 10);
                    BlockPos p = new BlockPos(dx, dy, dz);
                    order.add(p);
                    cells.put(p, code);
                }
            }
        }
        cells.put(CollapseGoldenTest.ORIGIN, c % 2 == 0 ? 'R' : 'O');
        if (reversed) {
            Collections.reverse(order);
        }
        Map<BlockPos, Character> out = new LinkedHashMap<>();
        for (BlockPos p : order) {
            out.put(p, cells.get(p));
        }
        return out;
    }
}
