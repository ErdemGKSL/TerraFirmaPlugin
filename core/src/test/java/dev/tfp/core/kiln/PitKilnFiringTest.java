/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (serverTick, cookContents,
 *       updateCache)
 */
package dev.tfp.core.kiln;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.placed.PlacedState;
import dev.tfp.core.persist.DataMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link PitKilnFiring} (lazy, once) against a per-tick copy of TFC's {@code cookContents} with the cached recipes of
 * {@code updateCache} (on lighting and on the last tick), on random recipe chains, kilns and failures; plus the stored
 * state codec and the layer helpers TFC's {@code useItemOn} uses.
 */
class PitKilnFiringTest {
    /** A stack: an item id ("" = empty), its temperature and the tick it was set. */
    record Stack(String id, float temperature, long tick) {
        static final Stack EMPTY = new Stack("", 0f, 0L);
    }

    record Recipe(float temperature, String result) {
    }

    static final class World implements PitKilnFiring.Items<Stack> {
        final Map<String, Recipe> recipes = new HashMap<>();
        final Set<String> heatable;

        World(Set<String> heatable) {
            this.heatable = heatable;
        }

        @Override
        public boolean isEmpty(Stack stack) {
            return stack.id().isEmpty();
        }

        @Override
        public boolean heatable(Stack stack) {
            return heatable.contains(stack.id());
        }

        @Override
        public float recipeTemperature(Stack stack) {
            Recipe r = recipes.get(stack.id());
            return r == null ? Float.NaN : r.temperature();
        }

        @Override
        public Stack withTemperature(Stack stack, float temperature, long tick) {
            return new Stack(stack.id(), temperature, tick);
        }

        @Override
        public Stack convert(Stack stack) {
            return assemble(recipes.get(stack.id()), stack);
        }

        static Stack assemble(Recipe r, Stack input) {
            return r.result().isEmpty() ? Stack.EMPTY : new Stack(r.result(), input.temperature(), input.tick());
        }

        /** TFC, one tick at a time: the cache on lighting, re-cached on the last tick of a kiln that fires. */
        Stack stepped(Stack stack, long lit, PitKilnRules.Outcome outcome, PitKilnRules.Config cfg) {
            Recipe cached = recipes.get(stack.id());
            long end = lit + cfg.durationTicks();
            Stack s = stack;
            for (long t = lit; t <= outcome.cookedThrough(); t++) {
                if (t >= end) {
                    cached = recipes.get(s.id()); // updateCache when remainingTicks <= 0
                }
                if (isEmpty(s) || !heatable(s)) {
                    continue;
                }
                float temperature = PitKilnRules.temperatureAt(t, lit, cfg);
                s = new Stack(s.id(), temperature, t);
                if (cached != null && cached.temperature() <= temperature) {
                    s = assemble(cached, s);
                }
            }
            return s;
        }
    }

    private static final List<String> IDS = List.of("clay_vessel", "vessel", "ingot", "ore", "brick", "stick");

    @Test
    void lazyEqualsSteppedOnRandomChains() {
        Random random = new Random(0x7F1B_0C03L);
        int compared = 0;
        for (int world = 0; world < 120; world++) {
            Set<String> heatable = new java.util.HashSet<>();
            for (String id : IDS) {
                if (random.nextInt(5) != 0) {
                    heatable.add(id);
                }
            }
            World w = new World(heatable);
            for (String id : IDS) {
                if (random.nextInt(3) != 0) {
                    String result = random.nextInt(4) == 0 ? "" : IDS.get(random.nextInt(IDS.size()));
                    w.recipes.put(id, new Recipe(random.nextInt(1600), result));
                }
            }
            for (int kiln = 0; kiln < 20; kiln++) {
                long duration = 20 + random.nextInt(600);
                PitKilnRules.Config cfg = new PitKilnRules.Config(duration, random.nextInt(1500));
                long lit = random.nextInt(50_000);
                long failed = random.nextInt(3) == 0 ? lit + random.nextInt((int) duration + 10) : PitKilnRules.NEVER;
                long now = lit + random.nextInt((int) duration + 30);
                PitKilnRules.Outcome outcome = PitKilnRules.evaluate(true, lit, now, cfg, failed);
                for (String id : IDS) {
                    Stack start = new Stack(id, 0f, 0L);
                    Stack lazy = PitKilnFiring.resolve(start, lit, outcome, cfg, w);
                    Stack stepped = w.stepped(start, lit, outcome, cfg);
                    assertThat(lazy).as("world %d kiln %d %s %s", world, kiln, id, outcome).isEqualTo(stepped);
                    compared++;
                }
            }
        }
        assertThat(compared).isEqualTo(120 * 20 * IDS.size());
    }

    @Test
    void unfiredVesselBecomesAVesselAtTheTfcDefaults() {
        World w = new World(Set.of("clay_vessel", "vessel"));
        w.recipes.put("clay_vessel", new Recipe(1399f, "vessel")); // TFC: ceramic/unfired_vessel at 1399 (Pottery)
        PitKilnRules.Config cfg = PitKilnRules.Config.DEFAULT;
        long lit = 1000;
        PitKilnRules.Outcome fired = PitKilnRules.evaluate(true, lit, lit + 7200, cfg, PitKilnRules.NEVER);
        assertThat(fired.phase()).isEqualTo(PitKilnRules.Phase.FIRED);
        Stack out = PitKilnFiring.resolve(new Stack("clay_vessel", 0f, 0L), lit, fired, cfg, w);
        assertThat(out).isEqualTo(new Stack("vessel", 1400f, lit + 7200));
        // Still lit one hour in: 1400 x 1.125 x 1/6 = 262.5 degrees, not converted.
        PitKilnRules.Outcome early = PitKilnRules.evaluate(true, lit, lit + 1200, cfg, PitKilnRules.NEVER);
        assertThat(PitKilnFiring.resolve(new Stack("clay_vessel", 0f, 0L), lit, early, cfg, w))
            .isEqualTo(new Stack("clay_vessel", 262.5f, lit + 1200));
    }

    @Test
    void itemsWithoutHeatAreNeverTouched() {
        World w = new World(Set.of());
        w.recipes.put("stick", new Recipe(0f, ""));
        PitKilnRules.Outcome fired = PitKilnRules.evaluate(true, 0, 7200, PitKilnRules.Config.DEFAULT, PitKilnRules.NEVER);
        Stack stick = new Stack("stick", 0f, 0L);
        assertThat(PitKilnFiring.resolve(stick, 0, fired, PitKilnRules.Config.DEFAULT, w)).isSameAs(stick);
    }

    @Test
    void aFailureOnTheLightingTickCooksNothing() {
        World w = new World(Set.of("brick"));
        w.recipes.put("brick", new Recipe(0f, "vessel"));
        PitKilnRules.Outcome failed = PitKilnRules.evaluate(true, 50, 50, PitKilnRules.Config.DEFAULT, 50);
        Stack brick = new Stack("brick", 0f, 0L);
        assertThat(PitKilnFiring.resolve(brick, 50, failed, PitKilnRules.Config.DEFAULT, w)).isSameAs(brick);
    }

    @Test
    void stateCodecRoundTripsWithTfcKeys() {
        PitKilnState s = new PitKilnState(new PlacedState(true, 15f, 30f, 45f, 60f), true, 123_456_789L);
        DataMap map = PitKilnState.CODEC.encode(s);
        assertThat(PitKilnState.CODEC.decode(map)).isEqualTo(s);
        assertThat(PitKilnState.FRESH.light(7).lit()).isTrue();
        assertThat(PitKilnState.FRESH.light(7).litTick()).isEqualTo(7);
        assertThat(PitKilnState.strawSlot(0)).isEqualTo(4);
        assertThat(PitKilnState.logSlot(7)).isEqualTo(19);
        assertThat(PitKilnState.SLOTS).isEqualTo(20);
    }

    @Test
    void layerHelpersFollowUseItemOn() {
        // convertPlacedItemToPitKiln: stage = strawValue - 1, straw in slot stage
        assertThat(PitKilnLayers.stageFromPlaced(1)).isZero();
        assertThat(PitKilnLayers.stageFromPlaced(4)).isEqualTo(3);
        // addStraw(held, stage + strawValue); addLog(held, stage - LOG_START + 1)
        assertThat(PitKilnLayers.strawSlotAfterAdd(0, 1)).isEqualTo(1);
        assertThat(PitKilnLayers.strawSlotAfterAdd(3, 4)).isEqualTo(7);
        assertThat(PitKilnLayers.logSlotAfterAdd(7)).isZero();
        assertThat(PitKilnLayers.logSlotAfterAdd(14)).isEqualTo(7);
        assertThat(PitKilnLayers.strawValue(true, true)).isEqualTo(4);
        assertThat(PitKilnLayers.strawValue(false, true)).isEqualTo(1);
        assertThat(PitKilnLayers.strawValue(false, false)).isZero();
        // empty hand: logs from LOG_START, straw below; the last straw turns the kiln back into a placed item
        assertThat(PitKilnLayers.take(15)).contains(new PitKilnLayers.Take(true, 7));
        assertThat(PitKilnLayers.take(8)).contains(new PitKilnLayers.Take(true, 0));
        assertThat(PitKilnLayers.take(7)).contains(new PitKilnLayers.Take(false, 7));
        assertThat(PitKilnLayers.take(PitKilnLayers.LIT)).isEmpty();
        assertThat(PitKilnLayers.stageAfterTake(15, 1)).isEqualTo(14);
        assertThat(PitKilnLayers.stageAfterTake(5, 4)).isEqualTo(1);
        assertThat(PitKilnLayers.stageAfterTake(0, 1)).isEqualTo(-1);
        assertThat(PitKilnLayers.stageAfterTake(3, 4)).isEqualTo(-1);
        assertThat(PitKilnLayers.stageAfterTake(4, 0)).isEqualTo(4); // an empty slot removes nothing
        assertThat(PitKilnLayers.contentsVisible(9)).isTrue();
        assertThat(PitKilnLayers.contentsVisible(10)).isFalse();
    }
}
