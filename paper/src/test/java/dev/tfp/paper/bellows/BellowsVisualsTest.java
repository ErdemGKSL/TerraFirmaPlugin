/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.bellows;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.bellows.BellowsMotion;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.testing.MockServerTest;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/** PAR-METAL-26: the pressed levels follow {@link BellowsMotion#extension}; a push morphs the block through them. */
class BellowsVisualsTest extends MockServerTest {
    @Test
    void levelsAreTheNearestOfFiveEvenExtensions() {
        assertThat(BellowsVisuals.LEVELS).isEqualTo(5); // datagen's BellowsAppearance.LEVELS: pressed 0..4
        assertThat(BellowsVisuals.PROPERTY).isEqualTo("pressed");
        assertThat(BellowsVisuals.level(BellowsMotion.MIN_EXTENSION)).isZero();
        assertThat(BellowsVisuals.level(0.25f)).isEqualTo(1);
        assertThat(BellowsVisuals.level(0.375f)).isEqualTo(2);
        assertThat(BellowsVisuals.level(0.5f)).isEqualTo(3);
        assertThat(BellowsVisuals.level(BellowsMotion.MAX_EXTENSION)).isEqualTo(4);
        assertThat(BellowsVisuals.level(9f)).as("clamped").isEqualTo(4);
        assertThat(BellowsVisuals.level(0f)).as("clamped").isZero();
    }

    @Test
    void theScheduleRisesToTheFullPressAtTickTenAndFallsBackToZero() {
        List<BellowsVisuals.Step> steps = BellowsVisuals.schedule();
        assertThat(steps).isNotEmpty();
        int shown = 0;
        int last = 0;
        for (BellowsVisuals.Step s : steps) {
            assertThat(s.delay()).isBetween(1, BellowsMotion.STROKE_TICKS).isGreaterThan(last);
            assertThat(s.level()).isNotEqualTo(shown).isBetween(0, BellowsVisuals.MAX_LEVEL);
            // the level at every tick is TFC's stroke
            last = s.delay();
            shown = s.level();
        }
        assertThat(shown).as("the stroke ends released").isZero();
        assertThat(steps.getLast().delay()).isLessThanOrEqualTo(BellowsMotion.STROKE_TICKS);
        // replaying the schedule gives the nearest level of the stroke at every tick
        int current = 0;
        for (int t = 1; t <= BellowsMotion.STROKE_TICKS; t++) {
            for (BellowsVisuals.Step s : steps) {
                if (s.delay() == t) {
                    current = s.level();
                }
            }
            assertThat(current).as("tick " + t).isEqualTo(BellowsVisuals.level(BellowsMotion.extension(t, 0)));
        }
        assertThat(steps).extracting(BellowsVisuals.Step::level).contains(BellowsVisuals.MAX_LEVEL);
        BellowsVisuals.Step peak = steps.stream().filter(s -> s.level() == BellowsVisuals.MAX_LEVEL).findFirst().orElseThrow();
        assertThat(peak.delay()).as("full press near the middle of the stroke").isBetween(8, 10);
    }

    @Test
    void aPushSchedulesEveryMorphOnTheRegionOfTheBellowsAndWritesTheLevels() {
        World world = server.addSimpleWorld("bellows-visuals");
        Block block = world.getBlockAt(5, 64, 5);
        block.setType(Material.STONE);
        List<Long> delays = new ArrayList<>();
        List<Runnable> tasks = new ArrayList<>();
        Scheduler scheduler = (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> {
                if (method.getName().equals("regionDelayed")) {
                    delays.add((Long) args[3]);
                    tasks.add((Runnable) args[4]);
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        BlockStateRef[] state = {BlockStateRef.parse("tfp:bellows[facing=north,pressed=0]")};
        List<BlockStateRef> asked = new ArrayList<>();
        BellowsVisuals visuals = new BellowsVisuals(scheduler, b -> Optional.of(state[0]), s -> {
            asked.add(s);
            state[0] = s;
            return Optional.of(Material.DIRT.createBlockData());
        });
        visuals.press(block);
        assertThat(delays).containsExactlyElementsOf(BellowsVisuals.schedule().stream().map(s -> (long) s.delay()).toList());
        tasks.forEach(Runnable::run);
        assertThat(asked).hasSameSizeAs(BellowsVisuals.schedule());
        assertThat(asked).allMatch(s -> s.properties().get("facing").equals("north"));
        assertThat(state[0].properties().get("pressed")).isEqualTo("0");
        assertThat(block.getType()).isEqualTo(Material.DIRT);
    }

    @Test
    void aReplacedBlockIsNotTouched() {
        World world = server.addSimpleWorld("bellows-visuals-gone");
        Block block = world.getBlockAt(1, 64, 1);
        block.setType(Material.STONE);
        BellowsVisuals visuals = new BellowsVisuals(scheduler(),
            b -> Optional.of(BlockStateRef.parse("tfp:other[pressed=0]")), s -> Optional.of(Material.DIRT.createBlockData()));
        visuals.show(block, 3);
        BellowsVisuals none = new BellowsVisuals(scheduler(), b -> Optional.empty(), s -> Optional.of(Material.DIRT.createBlockData()));
        none.show(block, 3);
        assertThat(block.getType()).isEqualTo(Material.STONE);
    }

    private Scheduler scheduler() {
        return (Scheduler) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Scheduler.class},
            (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });
    }
}
