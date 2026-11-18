/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.hitbox.HitboxAllocation.Assignment;
import dev.tfp.datagen.hitbox.HitboxAllocation.Demand;
import dev.tfp.datagen.hitbox.HitboxAllocation.Mode;
import dev.tfp.datagen.hitbox.HitboxAllocation.Result;
import dev.tfp.datagen.hitbox.HitboxCarrier.Choice;
import dev.tfp.datagen.hitbox.HitboxCarrier.TfcState;
import dev.tfp.datagen.hitbox.VanillaCandidates.Presence;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-26: the candle, candle cake and jack-o'-lantern carriers. Each takes its vanilla twin's state of the same
 * properties with the vanilla look (TFC draws vanilla's models on blocks that extend vanilla's candle classes), the twin
 * blocks are reserved for these classes, and adding them moves no other carrier.
 */
class CandleCarrierTest {

    @Test
    void everyCandleTakesItsColoursVanillaStateOfTheSameProperties() {
        for (String id : HitboxDemands.candleIds(HitboxDemands.CANDLE_ID)) {
            String twin = HitboxDemands.twin(id);
            for (int n = 1; n <= 4; n++) {
                for (boolean lit : new boolean[] {false, true}) {
                    for (boolean wet : new boolean[] {false, true}) {
                        String app = HitboxDemands.candleAppearance(n, lit, wet);
                        Assignment a = HitboxAllocation.agreed(id, app);
                        assertThat(a.state()).as(id + "#" + app).isEqualTo(twin + "[" + app + "]");
                        assertThat(a.mode()).isEqualTo(Mode.VANILLA);
                        assertThat(a.hideTarget()).isNull();
                        assertThat(a.carrier().score()).as(id + "#" + app).isEqualTo(1.0);
                        assertThat(a.demand().light()).isEqualTo(lit ? 3 * n + 2 : 0);
                    }
                }
            }
        }
        assertThat(HitboxDemands.twin("tfp:candle")).isEqualTo("minecraft:candle");
        assertThat(HitboxDemands.twin("tfp:candle/light_blue")).isEqualTo("minecraft:light_blue_candle");
        assertThat(HitboxDemands.twin("tfp:candle_cake/red")).isEqualTo("minecraft:red_candle_cake");
        assertThat(HitboxDemands.twin("tfp:candle/oak")).isNull();
        assertThat(HitboxDemands.twin(HitboxDemands.TORCH_ID)).isNull();
    }

    @Test
    void candleCakesAndTheJackOLanternTakeTheirTwins() {
        for (String id : HitboxDemands.candleIds(HitboxDemands.CANDLE_CAKE_ID)) {
            for (boolean lit : new boolean[] {false, true}) {
                Assignment a = HitboxAllocation.agreed(id, "lit=" + lit);
                assertThat(a.state()).isEqualTo(HitboxDemands.twin(id) + "[lit=" + lit + "]");
                assertThat(a.mode()).isEqualTo(Mode.VANILLA);
                assertThat(a.carrier().score()).as(id).isEqualTo(1.0);
            }
        }
        for (String f : HitboxDemands.FACINGS) {
            Assignment a = HitboxAllocation.agreed(HitboxDemands.JACK_O_LANTERN_ID, f);
            assertThat(a.state()).isEqualTo("minecraft:jack_o_lantern[facing=" + f + "]");
            assertThat(a.mode()).isEqualTo(Mode.VANILLA);
            assertThat(a.hideTarget()).isNull();
        }
    }

    @Test
    void theTwinsMatchTfcsShapesAndLightAndAreReserved() {
        VanillaCandidates v = VanillaCandidates.load();
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (String version : v.versions()) {
            for (String id : HitboxDemands.candleIds(HitboxDemands.CANDLE_ID)) {
                String twin = HitboxDemands.twin(id);
                for (int n = 1; n <= 4; n++) {
                    TfcShapes.Row r = rows.get(TfcShapes.candleKey(n, true, false));
                    VanillaCandidates.VState s = v.get(version, twin + "[" + HitboxDemands.candleAppearance(n, true, false) + "]");
                    assertThat(s.selection()).as(version + " " + id + " " + n).isEqualTo(r.selection());
                    assertThat(s.collision()).isEqualTo(r.collision());
                    // vanilla 3 * candles, TFC 3 * candles + 2 (the twin skips the light filter; ledger divergence)
                    assertThat(s.light()).isEqualTo(3 * n);
                }
            }
            for (String id : HitboxDemands.candleIds(HitboxDemands.CANDLE_CAKE_ID)) {
                VanillaCandidates.VState s = v.get(version, HitboxDemands.twin(id) + "[lit=true]");
                assertThat(s.selection()).isEqualTo(rows.get("tfc:candle_cake[lit=true]").selection());
                assertThat(s.light()).isEqualTo(HitboxDemands.CANDLE_CAKE_LIGHT);
            }
            assertThat(v.get(version, "minecraft:jack_o_lantern[facing=north]").light()).isEqualTo(HitboxDemands.JACK_O_LANTERN_LIGHT);
        }
        for (String c : HitboxDemands.CANDLE_COLORS) {
            assertThat(v.policy("minecraft:" + c + "_candle").presence()).isEqualTo(Presence.ABSENT);
            assertThat(v.policy("minecraft:" + c + "_candle").onlyClass()).isEqualTo("candle");
            assertThat(v.policy("minecraft:" + c + "_candle_cake").onlyClass()).isEqualTo("candle_cake");
        }
        assertThat(v.policy("minecraft:candle_cake").onlyClass()).isEqualTo("candle_cake");
        assertThat(v.policy("minecraft:jack_o_lantern").onlyClass()).isEqualTo("jack_o_lantern");
        assertThat(rows.get(TfcShapes.candleKey(1, false, true)).properties()).containsEntry("waterlogged", "true");
        assertThat(TfcState.of(TfcShapes.candleKey(1, false, true)).waterlogged()).isTrue();
        assertThat(TfcState.of(TfcShapes.candleKey(1, false, false)).waterlogged()).isFalse();
    }

    @Test
    void withoutTheTwinTheLitCandleHasNoCarrierAndATwinRejectsOtherBlocks() {
        // the plain selector keeps rejecting vanilla's lit candles (client particles; light 3n against TFC's 3n + 2)
        TfcState lit = TfcState.of(TfcShapes.candleKey(4, true, false)).withLight(HitboxDemands.candleLight(4));
        Choice plain = HitboxCarrier.select(lit, Profile.LEVEL_0, VersionFamily.V1_21);
        assertThat(plain.ranked()).noneMatch(s -> s.carrier().contains("lit=true"));        Choice twin = HitboxCarrier.select(lit.withTwin("minecraft:red_candle"), Profile.LEVEL_0, VersionFamily.V1_21);
        assertThat(twin.chosen().carrier()).isEqualTo("minecraft:red_candle[candles=4,lit=true,waterlogged=false]");
        assertThat(twin.ranked()).allMatch(s -> s.block().equals("minecraft:red_candle"));
    }

    @Test
    void addingTheCandlesMovesNoOtherCarrier() {
        List<Demand> without = new ArrayList<>();
        for (Demand d : HitboxDemands.all()) {
            if (HitboxDemands.twin(d.blockId()) == null) {
                without.add(d);
            }
        }
        for (VersionFamily f : VersionFamily.values()) {
            Result before = HitboxAllocation.allocate(without, Profile.defaultFor(f), f, HitboxCarrier.standardContext());
            Result after = HitboxAllocation.standard().get(f.id() + "/" + Profile.defaultFor(f).id());
            for (Assignment a : before.assignments()) {
                Assignment b = after.get(a.demand().blockId(), a.demand().appearance());
                assertThat(b.state()).as(f.id() + " " + a.demand().id()).isEqualTo(a.state());
                assertThat(b.mode()).isEqualTo(a.mode());
            }
        }
    }
}
