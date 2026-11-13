/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.itembar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.heat.HeatLevel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-14: TFC's heat bar widths ({@code BarSystem.HEAT}, copper ingot heating recipe at 1080 C from
 * {@code data/tfc/recipe/heating/metal/ingot/copper.json} @ c3b4d9536) and the {@code getCustomBar} choice.
 */
class ItemBarGoldenTest {
    /** A test stack: a temperature (null without heat state), a recipe temperature, a fertilized-egg flag. */
    record Stack(Float temperature, Float recipe, boolean egg) {
        Stack(Float temperature, Float recipe) {
            this(temperature, recipe, false);
        }
    }

    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);

    private static HeatBar<Stack> heat(AtomicBoolean enabled) {
        return new HeatBar<>(enabled::get, s -> Optional.ofNullable(s.temperature()),
                (s, t) -> Optional.ofNullable(s.recipe()), level -> 0x100 + level.ordinal());
    }

    /** A second bar after heat, as TFC's egg bar (PAR-FAUNA-23 adds the real one). */
    private static final ItemBar<Stack> EGG = new ItemBar<>() {
        @Override
        public String id() {
            return "egg";
        }

        @Override
        public boolean isBarVisible(Stack stack) {
            return stack.egg();
        }

        @Override
        public int barWidth(Stack stack) {
            return 4;
        }

        @Override
        public int barColor(Stack stack) {
            return 0xFFAA00;
        }
    };

    @Test
    void heatWidthsWithARecipe() {
        // TFC: Mth.clamp(Math.round(13f * t / 1080f), 1, 13), copper ingot.
        float[] temperatures = {1f, 40f, 41.6f, 42f, 83f, 540f, 700f, 1000f, 1038f, 1039f, 1080f, 1500f};
        int[] golden = {1, 1, 1, 1, 1, 7, 8, 12, 12, 13, 13, 13};
        for (int i = 0; i < temperatures.length; i++) {
            assertThat(HeatBar.width(temperatures[i], Optional.of(1080f))).as(temperatures[i] + " C")
                .isEqualTo(golden[i]);
        }
        assertThat(HeatBar.width(124.7f, Optional.of(1080f))).isEqualTo(2); // 1.5009 -> 2
        assertThat(HeatBar.width(124.5f, Optional.of(1080f))).isEqualTo(1); // 1.4986 -> 1
    }

    @Test
    void heatWidthsWithoutARecipeUseTheMaxVisibleTemperature() {
        assertThat(HeatLevel.MAX_VISIBLE).isEqualTo(1600f);
        assertThat(HeatBar.width(800f, Optional.empty())).isEqualTo(7); // 6.5 -> 7
        assertThat(HeatBar.width(400f, Optional.empty())).isEqualTo(3); // 3.25
        assertThat(HeatBar.width(1600f, Optional.empty())).isEqualTo(13);
        assertThat(HeatBar.width(5000f, Optional.empty())).as("clamped to 13").isEqualTo(13);
        assertThat(HeatBar.width(10f, Optional.empty())).as("clamped to 1").isEqualTo(1);
    }

    @Test
    void theHeatBarReadsTheStack() {
        HeatBar<Stack> bar = heat(ENABLED);
        Stack ingot = new Stack(540f, 1080f);
        assertThat(bar.isBarVisible(ingot)).isTrue();
        assertThat(bar.barWidth(ingot)).isEqualTo(7);
        assertThat(bar.barColor(ingot)).as("FAINT_RED's colour").isEqualTo(0x100 + HeatLevel.FAINT_RED.ordinal());
        assertThat(bar.barWidth(new Stack(null, 1080f))).as("no heat state").isZero();
        assertThat(bar.barColor(new Stack(0f, 1080f))).isZero();
    }

    @Test
    void aColdItemShowsNoBar() {
        HeatBar<Stack> bar = heat(ENABLED);
        assertThat(bar.isBarVisible(new Stack(0f, 1080f))).isFalse();
        assertThat(bar.isBarVisible(new Stack(null, null))).isFalse();
        assertThat(bar.isBarVisible(new Stack(0.01f, null))).as("any heat above 0").isTrue();
        ItemBarRegistry<Stack> bars = ItemBarRegistry.<Stack>of(List.of()).with(bar);
        assertThat(bars.shown(new Stack(0f, 1080f), false)).isEmpty();
    }

    @Test
    void theSettingHidesTheBar() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        ItemBarRegistry<Stack> bars = ItemBarRegistry.<Stack>of(List.of()).with(heat(enabled));
        assertThat(bars.shown(new Stack(540f, 1080f), false)).contains(new ShownBar(HeatBar.ID, 7, 0x103));
        enabled.set(false);
        assertThat(bars.shown(new Stack(540f, 1080f), false)).isEmpty();
    }

    @Test
    void theStacksOwnBarWins() {
        ItemBarRegistry<Stack> bars = ItemBarRegistry.<Stack>of(List.of()).with(heat(ENABLED));
        assertThat(bars.shown(new Stack(540f, 1080f), true)).as("a damaged hot tool keeps its durability bar").isEmpty();
    }

    @Test
    void anotherVisibleBarHidesBothAsInTfc() {
        ItemBarRegistry<Stack> bars = ItemBarRegistry.<Stack>of(List.of()).with(heat(ENABLED)).with(EGG);
        assertThat(bars.bars()).extracting(ItemBar::id).containsExactly("heat", "egg");
        assertThat(bars.shown(new Stack(null, null, true), false)).contains(new ShownBar("egg", 4, 0xFFAA00));
        assertThat(bars.shown(new Stack(540f, 1080f, false), false).map(ShownBar::id)).contains("heat");
        // Reset of heat still shows the egg bar and vice versa: TFC's getCustomBar returns null.
        assertThat(bars.shown(new Stack(540f, 1080f, true), false)).isEmpty();
    }

    @Test
    void anOverridingBarAlwaysWins() {
        ItemBar<Stack> over = new ItemBar<>() {
            @Override
            public String id() {
                return "over";
            }

            @Override
            public boolean isBarVisible(Stack stack) {
                return true;
            }

            @Override
            public int barWidth(Stack stack) {
                return 99;
            }

            @Override
            public int barColor(Stack stack) {
                return 1;
            }

            @Override
            public boolean overridesOtherBars() {
                return true;
            }
        };
        ItemBarRegistry<Stack> bars = ItemBarRegistry.<Stack>of(List.of(heat(ENABLED), over));
        assertThat(bars.shown(new Stack(540f, 1080f), true)).as("width clamped to 13")
            .contains(new ShownBar("over", 13, 1));
    }

    @Test
    void barIdsAreUnique() {
        ItemBarRegistry<Stack> bars = ItemBarRegistry.<Stack>of(List.of()).with(heat(ENABLED));
        assertThatThrownBy(() -> bars.with(heat(ENABLED))).isInstanceOf(IllegalArgumentException.class);
        assertThat(ItemBarRegistry.<Stack>of(List.of()).shown(new Stack(540f, 1080f), false)).isEmpty();
    }
}
