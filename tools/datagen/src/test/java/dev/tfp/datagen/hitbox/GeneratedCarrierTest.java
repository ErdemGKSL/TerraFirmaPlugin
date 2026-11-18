/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation.Assignment;
import dev.tfp.datagen.hitbox.HitboxAllocation.Demand;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-INFRA-13 done criterion 1 on the output rather than the source text: every CraftEngine block datagen writes under
 * {@code craftengine/} that names an explicit visual state (an appearance {@code state:} that is a string, with or without
 * properties, e.g. a bare {@code minecraft:dirt_path}) takes it from the selector: the state equals
 * {@link HitboxAllocation#agreed} of the matching demand, and every {@code block_state_mappings} entry is the hide mapping
 * of such a carrier. Full cubes name a {@code tfp:visual/...} template instead (VisualAllocator's pass, checked by
 * {@code HitboxAllocationTest.neverSharesAStateWithTheFullCubeAllocation}); {@code auto_state} outside the visual
 * templates is refused unless the appearance is a declared full cube drawn from a CE pool (PAR-PROC-34: the
 * composter's stage 8, {@link HitboxDemands#FULL_CUBE_POOLS}) or the selector itself served the appearance's demand from
 * that pool ({@link HitboxAllocation.Mode#POOL}, PAR-PROC-37: the burning log pile, the fullest log piles and the
 * eight-layer charcoal pile).
 */
class GeneratedCarrierTest {

    @Test
    void everyExplicitStateInTheGeneratedYamlComesFromTheSelector() {
        SortedMap<String, byte[]> files = Datagen.generate(RealCheckout.open().root(), TfcCheckout.PINNED_COMMIT).tree().files();
        List<String> problems = new ArrayList<>();
        int states = 0;
        int pools = 0;
        java.util.SortedSet<String> checkedIds = new java.util.TreeSet<>();
        java.util.SortedSet<String> mappings = new java.util.TreeSet<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            if (!path.startsWith("craftengine/") || !path.endsWith(".yml")) {
                continue;
            }
            Object root = load(new String(e.getValue(), StandardCharsets.UTF_8));
            if (!(root instanceof Map<?, ?> top)) {
                continue;
            }
            if (top.get("blocks") instanceof Map<?, ?> blocks) {
                for (Map.Entry<?, ?> b : blocks.entrySet()) {
                    String id = String.valueOf(b.getKey());
                    if (!(b.getValue() instanceof Map<?, ?> block)) {
                        continue;
                    }
                    for (Map.Entry<String, Object> a : appearances(block).entrySet()) {
                        if (!(a.getValue() instanceof Map<?, ?> app)) {
                            continue;
                        }
                        if (app.containsKey("auto_state")) {
                            pools++;
                            checkPool(path, id, a.getKey(), app.get("auto_state"), problems);
                        }
                        if (app.get("state") instanceof String state) {
                            states++;
                            checkedIds.add(id);
                            check(path, id, a.getKey(), state, problems);
                        }
                    }
                }
            }
            if (top.get("block_state_mappings") instanceof Map<?, ?> m) {
                for (Map.Entry<?, ?> x : m.entrySet()) {
                    String from = String.valueOf(x.getKey());
                    String to = String.valueOf(x.getValue());
                    mappings.add(from + " -> " + to);
                    boolean fromSelector = HitboxDemands.all().stream()
                        .map(d -> HitboxAllocation.agreed(d.blockId(), d.appearance()))
                        .anyMatch(a -> a.state().equals(from) && to.equals(a.hideTarget()));
                    if (!fromSelector) {
                        problems.add(path + ": block_state_mappings " + from + " -> " + to + " is no selector hide mapping");
                    }
                }
            }
        }
        // Today: the forge (8 heat levels), the bellows (4 facings), the firepit, grill and pot (lit, unlit), the quern
        // (base, handstone; PAR-PROC-04); the scraping surface (base, PAR-PROC-29); the forge's, the quern's and the scraping
        // surface's hide mappings.
        // The exact sets are pinned so that a generator silently dropping out of craftengine/ fails here; a family item
        // that adds an explicit-state block adds its id to this list.
        // PAR-PROC-32: the upright barrel of every wood (unsealed, sealed; hitbox-only, the look is an item display).
        // PAR-PROC-34: the composter, 25 appearances (stage 0 empty, stages 1..8 x 3 types); the full-cube stage 8 takes
        // the solid pool (3 auto_state appearances, checked by checkPool), the other 22 explicit states.
        // PAR-PROC-37: the log pile (32 appearances: 24 explicit states, counts 13..16 of both axes on pools), the burning
        // log pile (32 leaves pool states) and the charcoal pile (7 explicit states, layer 8 from the solid pool).
        // PAR-PROC-38: 16 side appearances per barrel (4 facings x seal x rack) and the barrel rack's one.
        // PAR-HEAT-21: the torch, the wall torch (4 facings), the dead torch and the dead wall torch (4 facings).
        // PAR-PROC-39: the powderkeg (unsealed, sealed, lit).
        // PAR-HEAT-26: 17 candles x 16 states, 17 candle cakes x 2, the jack-o'-lantern's 4 facings (35 ids).
        assertThat(states).isGreaterThanOrEqualTo(83 + 24 + 7 + 20 * 16 + 1 + 10 + 3 + 17 * 16 + 17 * 2 + 4);
        assertThat(pools).as("auto_state appearances of declared pools").isEqualTo(3 + 8 + 32 + 1);
        java.util.SortedSet<String> expected = new java.util.TreeSet<>(
            List.of("tfp:bellows", "tfp:charcoal_forge", "tfp:firepit", "tfp:grill", "tfp:pot", "tfp:quern",
                "tfp:scraping", HitboxDemands.COMPOSTER_ID, HitboxDemands.LOG_PILE_ID, HitboxDemands.CHARCOAL_PILE_ID,
                HitboxDemands.BARREL_RACK_ID, HitboxDemands.TORCH_ID, HitboxDemands.WALL_TORCH_ID,
                HitboxDemands.DEAD_TORCH_ID, HitboxDemands.DEAD_WALL_TORCH_ID, HitboxDemands.POWDERKEG_ID,
                HitboxDemands.JACK_O_LANTERN_ID));
        HitboxDemands.BARREL_WOODS.forEach(w -> expected.add(HitboxDemands.barrelId(w)));
        expected.addAll(HitboxDemands.candleIds(HitboxDemands.CANDLE_ID));
        expected.addAll(HitboxDemands.candleIds(HitboxDemands.CANDLE_CAKE_ID));
        assertThat(expected).hasSize(36 + 35);
        assertThat(checkedIds).as("block ids whose explicit appearance states were checked")
            .containsExactlyElementsOf(expected);
        assertThat(mappings).as("generator block_state_mappings")
            .containsExactlyInAnyOrder("minecraft:snow[layers=6] -> minecraft:snow[layers=4]",
                "minecraft:snow[layers=7] -> minecraft:snow[layers=8]",
                // PAR-PROC-37: the two-layer charcoal pile's carrier.
                "minecraft:snow[layers=2] -> minecraft:snow[layers=3]",
                "minecraft:heavy_weighted_pressure_plate[power=0] -> minecraft:heavy_weighted_pressure_plate[power=1]",
                // PAR-HEAT-23: the firepit's four daylight detector carriers.
                "minecraft:daylight_detector[inverted=false,power=0] -> minecraft:daylight_detector[inverted=false,power=1]",
                "minecraft:daylight_detector[inverted=false,power=10] -> minecraft:daylight_detector[inverted=false,power=11]",
                "minecraft:daylight_detector[inverted=false,power=12] -> minecraft:daylight_detector[inverted=false,power=13]",
                "minecraft:daylight_detector[inverted=false,power=14] -> minecraft:daylight_detector[inverted=false,power=15]");
        assertThat(problems).as("take carriers from dev.tfp.datagen.hitbox.HitboxAllocation").isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSummaryCountsEveryCraftEngineState() {
        // PAR-PROC-37: the installer sizes CraftEngine's serverside-blocks from counts.ce_states (+25 %, rounded up to
        // 1000). With the three charcoal pit blocks our states plus CraftEngine's default pack passed its default 2000,
        // while the block count still asked for 2000. The text scan must equal a real YAML parse of every entry.
        Datagen.Result r = Datagen.generate(RealCheckout.open().root(), TfcCheckout.PINNED_COMMIT);
        int parsed = 0;
        for (Map.Entry<String, byte[]> e : r.tree().files().entrySet()) {
            String path = e.getKey();
            if (!path.startsWith("craftengine/") || !path.contains("/configuration/") || !path.endsWith(".yml")) {
                continue;
            }
            if (!(load(new String(e.getValue(), StandardCharsets.UTF_8)) instanceof Map<?, ?> top)) {
                continue;
            }
            for (Map.Entry<?, ?> section : top.entrySet()) {
                if (!String.valueOf(section.getKey()).split("#")[0].equals("blocks") || !(section.getValue() instanceof Map<?, ?> blocks)) {
                    continue;
                }
                for (Object b : blocks.values()) {
                    int n = 1;
                    if (b instanceof Map<?, ?> block && block.get("states") instanceof Map<?, ?> st
                            && st.get("properties") instanceof Map<?, ?> props) {
                        for (Object p : props.values()) {
                            Map<String, Object> prop = (Map<String, Object>) p;
                            String type = String.valueOf(prop.get("type"));
                            if (type.equals("boolean")) {
                                n *= 2;
                            } else if (type.equals("int")) {
                                String[] range = String.valueOf(prop.get("range")).split("~");
                                n *= Integer.parseInt(range[1]) - Integer.parseInt(range[0]) + 1;
                            } else if (prop.get("values") instanceof List<?> values) {
                                n *= values.size();
                            } else {
                                assertThat(type).isEqualTo("axis");
                                n *= 3;
                            }
                        }
                    }
                    parsed += n;
                }
            }
        }
        String summary = new String(r.tree().files().get("reports/datagen-summary.json"), StandardCharsets.UTF_8);
        int counted = com.google.gson.JsonParser.parseString(summary).getAsJsonObject().getAsJsonObject("counts")
            .get("ce_states").getAsInt();
        assertThat(counted).isEqualTo(parsed).isEqualTo(dev.tfp.datagen.Report.countCeStates(r.tree().files()));
        assertThat(counted).as("states, more than blocks").isGreaterThan(r.report().emittedCount() + 72);
        assertThat(dev.tfp.datagen.Report.countCeStates("blocks:\n  tfp:a:\n    states:\n      properties:\n        c:\n"
            + "          type: int\n          range: '1~16'\n        d:\n          type: string\n          values:\n"
            + "            - x\n            - z\n      variants: {}\n  tfp:b:\n    state:\n      template: t\nitems:\n  tfp:i: {}\n"))
            .isEqualTo(33);
    }

    @Test
    void theOutputGuardRefusesABareStatelessCarrier() {
        List<String> problems = new ArrayList<>();
        check("x.yml", "tfp:bellows", "north", "minecraft:mud", problems);
        check("x.yml", "tfp:unknown", "only", "minecraft:dirt_path", problems);
        check("x.yml", "tfp:bellows", "north", HitboxAllocation.agreed("tfp:bellows", "north").state(), problems);
        assertThat(problems).hasSize(2);
    }

    @Test
    void theOutputGuardTakesAutoStateOnlyForADeclaredFullCube() {
        List<String> problems = new ArrayList<>();
        // PAR-PROC-34: stage 8 of the composter is a full cube; the selector itself ranks the solid pool first for it.
        String row = HitboxDemands.composterKey(HitboxDemands.COMPOSTER_POOL_STAGE);
        for (dev.tfp.datagen.visuals.VersionFamily f : dev.tfp.datagen.visuals.VersionFamily.values()) {
            HitboxCarrier.Choice c = HitboxCarrier.select(HitboxCarrier.TfcState.of(row),
                dev.tfp.datagen.visuals.Profile.defaultFor(f), f);
            assertThat(c.chosen().carrier()).as(f.id()).isEqualTo(VanillaCandidates.POOL_PREFIX + HitboxDemands.COMPOSTER_POOL);
        }
        checkPool("x.yml", HitboxDemands.COMPOSTER_ID, "ready_8", "solid", problems);
        assertThat(problems).isEmpty();
        checkPool("x.yml", HitboxDemands.COMPOSTER_ID, "ready_8", "leaves", problems);
        checkPool("x.yml", HitboxDemands.COMPOSTER_ID, "ready_3", "solid", problems);
        checkPool("x.yml", "tfp:bellows", "north", "solid", problems);
        assertThat(problems).hasSize(3);
    }

    @Test
    void theOutputGuardTakesAutoStateForTheDeclaredCharcoalPitPools() {
        // PAR-PROC-37: the burning log pile is TFC's full cube with noOcclusion (leaves pool), the full log pile and the
        // eight-layer charcoal pile occlude (solid pool), 13..15 logs take the leaves pool; a two-log pile is no pool state.
        List<String> problems = new ArrayList<>();
        checkPool("x.yml", HitboxDemands.BURNING_LOG_PILE_ID, "x_7", "leaves", problems);
        checkPool("x.yml", HitboxDemands.LOG_PILE_ID, "z_16", "solid", problems);
        checkPool("x.yml", HitboxDemands.LOG_PILE_ID, "x_14", "leaves", problems);
        checkPool("x.yml", HitboxDemands.CHARCOAL_PILE_ID, "layers_8", "solid", problems);
        assertThat(problems).isEmpty();
        checkPool("x.yml", HitboxDemands.BURNING_LOG_PILE_ID, "x_7", "solid", problems);
        checkPool("x.yml", HitboxDemands.LOG_PILE_ID, "x_2", "solid", problems);
        checkPool("x.yml", HitboxDemands.CHARCOAL_PILE_ID, "layers_7", "solid", problems);
        assertThat(problems).hasSize(3);
    }

    @Test
    void theCharcoalPitDemandsArePinned() {
        // PAR-PROC-37: 32 + 32 + 8 server states: 24 log pile and 7 charcoal pile demands, each on a TFC row of
        // hitbox/tfc-shapes.json, the other 41 states on the declared pools.
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        long logPiles = HitboxDemands.all().stream().filter(d -> d.blockId().equals(HitboxDemands.LOG_PILE_ID)).count();
        long burning = HitboxDemands.all().stream().filter(d -> d.blockId().equals(HitboxDemands.BURNING_LOG_PILE_ID)).count();
        long charcoal = HitboxDemands.all().stream().filter(d -> d.blockId().equals(HitboxDemands.CHARCOAL_PILE_ID)).count();
        assertThat(List.of(logPiles, burning, charcoal)).containsExactly(24L, 0L, 7L);
        HitboxDemands.all().stream().filter(d -> d.blockId().contains("pile"))
            .forEach(d -> assertThat(rows).as(d.id()).containsKey(d.tfcKey()));
        int pooled = 0;
        for (String axis : TfcShapes.LOG_PILE_AXES) {
            for (int count = 1; count <= TfcShapes.LOG_PILE_MAX; count++) {
                String name = HitboxDemands.logPileAppearance(axis, count);
                assertThat(rows).containsKey(TfcShapes.logPileKey(axis, count)).containsKey(TfcShapes.burningKey(axis, count));
                assertThat(HitboxDemands.charcoalPitPool(HitboxDemands.BURNING_LOG_PILE_ID, name)).isEqualTo("leaves");
                String pool = HitboxDemands.charcoalPitPool(HitboxDemands.LOG_PILE_ID, name);
                assertThat(pool).as(name).isEqualTo(count == 16 ? "solid" : count >= 13 ? "leaves" : null);
                pooled += pool == null ? 0 : 1;
            }
        }
        assertThat(pooled).isEqualTo(8);
        // The daylight detector is crafted in TFC (CarrierPolicyRecipeTest): count 5 and three layers borrow the next row.
        assertThat(HitboxDemands.logPileDemandKey("x", 5)).isEqualTo(TfcShapes.logPileKey("x", 6));
        assertThat(HitboxDemands.charcoalDemandKey(3)).isEqualTo(HitboxDemands.charcoalPileKey(4));
        assertThat(HitboxDemands.logPileDemandKey("z", 7)).isEqualTo(TfcShapes.logPileKey("z", 7));
        // The two looks of one count differ by TFC's y 90 turn, so they never share a carrier.
        assertThat(HitboxDemands.logPileLook("x", 5, false)).isEqualTo("tfp:block/log_pile/log_pile_5@y90");
        assertThat(HitboxDemands.logPileLook("z", 5, true)).isEqualTo("tfp:block/log_pile/log_pile_5_burning");
        // Today's carriers (all profiles and families agree): the one- and two-log piles hitbox-only on a closed iron
        // trapdoor, a charcoal layer on snow[layers=2], the full pile and charcoal block on the solid pool.
        assertThat(HitboxAllocation.agreed(HitboxDemands.LOG_PILE_ID, "x_1").mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
        assertThat(HitboxAllocation.agreed(HitboxDemands.LOG_PILE_ID, "z_2").state()).startsWith("minecraft:iron_trapdoor[");
        assertThat(HitboxAllocation.agreed(HitboxDemands.CHARCOAL_PILE_ID, "layers_2").state()).isEqualTo("minecraft:snow[layers=2]");
        // The selector itself ranks CE's pools first for the full outlines: the opaque ones on solid, the burning pile
        // (noOcclusion) on leaves.
        for (dev.tfp.datagen.visuals.VersionFamily f : dev.tfp.datagen.visuals.VersionFamily.values()) {
            for (String[] k : new String[][] {{TfcShapes.logPileKey("x", 16), "solid"}, {TfcShapes.burningKey("z", 3), "leaves"}}) {
                HitboxCarrier.Choice c = HitboxCarrier.select(HitboxCarrier.TfcState.of(k[0]),
                    dev.tfp.datagen.visuals.Profile.defaultFor(f), f);
                assertThat(c.chosen().carrier()).as(k[0]).isEqualTo(VanillaCandidates.POOL_PREFIX + k[1]);
            }
        }
        for (Demand d : HitboxDemands.all()) {
            if (d.blockId().contains("pile")) {
                assertThat(HitboxAllocation.agreed(d.blockId(), d.appearance()).state()).as(d.id())
                    .doesNotStartWith("minecraft:daylight_detector").doesNotStartWith(VanillaCandidates.POOL_PREFIX);
            }
        }
    }

    @Test
    void theBarrelSideRackAndBarrelRackCarriersArePinned() {
        // PAR-PROC-38: the barrel on its side per axis and rack (TFC SHAPE_X / SHAPE_Z, with RACK_SHAPE racked) and the
        // barrel rack (RACK_SHAPE). TFC's side rows are in hitbox/tfc-shapes.json, but their best free carrier is a
        // farmland state, whose full bottom face, drawn empty, culls the ground's top face (a see-through gap beside the
        // side model in game): the side demands borrow the sealed upright row and share its transparent cactus carrier.
        // The rack takes a closed top iron trapdoor, hitbox-only (its look is TFC's model as an item display). Served last
        // (weight -2), so adding them moved no other block's carrier. Every wood agrees on one carrier per axis and rack.
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (String axis : HitboxDemands.BARREL_SIDE_AXES) {
            for (boolean rack : new boolean[] {false, true}) {
                String key = HitboxDemands.barrelSideKey(axis, rack);
                assertThat(rows).containsKey(key);
                String carrier = null;
                for (String w : HitboxDemands.BARREL_WOODS) {
                    Assignment a = HitboxAllocation.agreed(HitboxDemands.barrelId(w), HitboxDemands.barrelSideDemand(axis, rack));
                    assertThat(a.demand().tfcKey()).isEqualTo(HitboxDemands.BARREL_SIDE_BORROWED_ROW);
                    assertThat(a.mode()).as(key).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
                    assertThat(a.hideTarget()).isNull();
                    carrier = carrier == null ? a.state() : carrier;
                    assertThat(a.state()).isEqualTo(carrier);
                }
                assertThat(carrier).as(key).isEqualTo("minecraft:cactus[age=10]")
                    .isEqualTo(HitboxAllocation.agreed(HitboxDemands.barrelId("oak"), HitboxDemands.BARREL_SEALED).state());
                // the selector's own pick for TFC's row would be a farmland state (the one refused above)
                assertThat(HitboxCarrier.select(HitboxCarrier.TfcState.of(key), dev.tfp.datagen.visuals.Profile.defaultFor(
                    dev.tfp.datagen.visuals.VersionFamily.V1_21), dev.tfp.datagen.visuals.VersionFamily.V1_21).ranked())
                    .anyMatch(c -> c.carrier().startsWith("minecraft:farmland["));
            }
        }
        Assignment rack = HitboxAllocation.agreed(HitboxDemands.BARREL_RACK_ID, HitboxDemands.BARREL_RACK_APPEARANCE);
        assertThat(rows).containsKey("tfc:barrel_rack");
        assertThat(rack.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
        assertThat(rack.state()).isEqualTo(
            "minecraft:iron_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]");
        // the patterns match exactly the side appearances of their axis and rack
        assertThat(matches(HitboxDemands.barrelSideDemand("x", false), "x_east_unsealed")).isTrue();
        assertThat(matches(HitboxDemands.barrelSideDemand("x", false), "x_west_sealed")).isTrue();
        assertThat(matches(HitboxDemands.barrelSideDemand("x", false), "x_west_sealed_rack")).isFalse();
        assertThat(matches(HitboxDemands.barrelSideDemand("z", true), "z_north_unsealed_rack")).isTrue();
        assertThat(matches(HitboxDemands.barrelSideDemand("z", true), "x_east_unsealed_rack")).isFalse();
        assertThat(matches(HitboxDemands.barrelSideDemand("x", false), HitboxDemands.BARREL_SEALED)).isFalse();
    }

    @Test
    void theFirepitCarriersArePinned() {
        // PAR-HEAT-23: TFC's lit and unlit models per axis, each on its own daylight detector power (the only free states
        // scoring 0.5 or more for TFC's firepit outline). TFC crafts the detector, so each carrier is hidden as the next
        // power (its model depends only on inverted). Served last (weight -5): adding them moved no other carrier.
        Map<String, String> expected = Map.of(
            "lit_x", "minecraft:daylight_detector[inverted=false,power=0]",
            "lit_z", "minecraft:daylight_detector[inverted=false,power=10]",
            "unlit_x", "minecraft:daylight_detector[inverted=false,power=12]",
            "unlit_z", "minecraft:daylight_detector[inverted=false,power=14]");
        java.util.Set<String> carriers = new java.util.HashSet<>();
        for (boolean lit : new boolean[] {true, false}) {
            for (String axis : HitboxDemands.FIREPIT_AXES) {
                String app = HitboxDemands.firepitAppearance(lit, axis);
                Assignment a = HitboxAllocation.agreed(HitboxDemands.FIREPIT_ID, app);
                assertThat(a.mode()).as(app).isEqualTo(HitboxAllocation.Mode.MODEL);
                assertThat(a.state()).as(app).isEqualTo(expected.get(app));
                assertThat(a.demand().look()).as(app).isEqualTo(HitboxDemands.firepitLook(lit, axis));
                int power = Integer.parseInt(a.state().replaceAll(".*power=(\\d+)]", "$1"));
                assertThat(a.hideTarget()).as(app)
                    .isEqualTo("minecraft:daylight_detector[inverted=false,power=" + (power + 1) + "]");
                carriers.add(a.state());
            }
        }
        assertThat(carriers).hasSize(4);
        assertThat(HitboxDemands.firepitLook(true, "z")).isEqualTo("tfp:block/firepit_lit@y90");
        for (Demand d : HitboxDemands.all()) {
            if (!d.blockId().equals(HitboxDemands.FIREPIT_ID)) {
                assertThat(HitboxAllocation.agreed(d.blockId(), d.appearance()).state()).as(d.id()).isNotIn(carriers);
            }
        }
    }

    @Test
    void thePowderkegCarriersArePinned() {
        // PAR-PROC-39: TFC's three models, all on TFC's sealed outline (tfc:powderkeg[sealed=true] SHAPE 14x16x14). The
        // hollow sealed=false row's carrier was a composter level drawn empty, whose full bottom plate culled the floor's
        // top face (the world showed through the keg's 1 px inset in game), so no appearance takes that row. Each
        // appearance takes its own free cactus age with TFC's model (one look per carrier). Served last (weight -4):
        // adding the keg moved no other block's carrier.
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        assertThat(rows).containsKeys(HitboxDemands.POWDERKEG_ROW, "tfc:powderkeg[sealed=false]");
        Assignment unsealed = HitboxAllocation.agreed(HitboxDemands.POWDERKEG_ID, HitboxDemands.POWDERKEG_UNSEALED);
        Assignment sealed = HitboxAllocation.agreed(HitboxDemands.POWDERKEG_ID, HitboxDemands.POWDERKEG_SEALED);
        Assignment lit = HitboxAllocation.agreed(HitboxDemands.POWDERKEG_ID, HitboxDemands.POWDERKEG_LIT);
        for (Assignment a : List.of(unsealed, sealed, lit)) {
            assertThat(a.demand().tfcKey()).as(a.demand().id()).isEqualTo("tfc:powderkeg[sealed=true]");
            assertThat(a.mode()).as(a.demand().id()).isEqualTo(HitboxAllocation.Mode.MODEL);
            assertThat(a.state()).as(a.demand().id()).startsWith("minecraft:cactus[");
        }
        assertThat(unsealed.state()).isEqualTo("minecraft:cactus[age=13]");
        assertThat(sealed.state()).isEqualTo("minecraft:cactus[age=12]");
        assertThat(lit.state()).isEqualTo("minecraft:cactus[age=11]");
        assertThat(List.of(unsealed, sealed, lit)).extracting(Assignment::hideTarget).containsOnlyNulls();
        assertThat(HitboxDemands.powderkegModel(HitboxDemands.POWDERKEG_LIT)).isEqualTo("tfp:block/powderkeg_lit");
        // the keg's carriers are no other block's
        for (Demand d : HitboxDemands.all()) {
            if (!d.blockId().equals(HitboxDemands.POWDERKEG_ID)) {
                assertThat(HitboxAllocation.agreed(d.blockId(), d.appearance()).state()).as(d.id())
                    .isNotIn(unsealed.state(), sealed.state(), lit.state());
            }
        }
    }

    @Test
    void everyHitboxOnlyCarrierIsDrawnEmptyAndNeverOccursNaturally() {
        // PAR-PROC-38 review: a hitbox-only carrier must reach the client with no vanilla model. CraftEngine writes the
        // variant {"model": "minecraft:block/empty"} into assets/minecraft/blockstates/<block>.json for an appearance with
        // an explicit state and transparent: true (checked in a built pack: cactus age=10 and the rack's and the small log
        // piles' iron trapdoor states map to minecraft:block/empty). An appearance on such a state without transparent and
        // without a model keeps the vanilla look (a cactus top over the barrel), so every generated appearance on a
        // hitbox-only carrier must say transparent and bring no model. Drawn empty, every real instance of the carrier
        // state would be invisible too, so the carrier block must never occur in a TFC world (policy "absent") or have a
        // hide target.
        Map<String, Assignment> hitboxOnly = new java.util.TreeMap<>();
        VanillaCandidates v = HitboxCarrier.standardContext().vanilla();
        List<String> problems = new ArrayList<>();
        for (Demand d : HitboxDemands.all()) {
            Assignment a = HitboxAllocation.agreed(d.blockId(), d.appearance());
            if (a.mode() != HitboxAllocation.Mode.HITBOX_ONLY) {
                continue;
            }
            hitboxOnly.put(d.id(), a);
            String block = a.state().contains("[") ? a.state().substring(0, a.state().indexOf('[')) : a.state();
            VanillaCandidates.BlockPolicy p = v.policy(block);
            if (a.hideTarget() == null && (p == null || p.presence() != VanillaCandidates.Presence.ABSENT)) {
                problems.add(d.id() + ": hitbox-only carrier " + a.state() + " may occur in a TFC world and has no hide target");
            }
        }
        assertThat(hitboxOnly.values()).extracting(Assignment::state)
            .contains("minecraft:cactus[age=10]",
                "minecraft:iron_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]");
        SortedMap<String, byte[]> files = Datagen.generate(RealCheckout.open().root(), TfcCheckout.PINNED_COMMIT).tree().files();
        java.util.Set<String> drawnEmpty = new java.util.TreeSet<>();
        int checked = 0;
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (!e.getKey().startsWith("craftengine/") || !e.getKey().endsWith(".yml")
                    || !(load(new String(e.getValue(), StandardCharsets.UTF_8)) instanceof Map<?, ?> top)
                    || !(top.get("blocks") instanceof Map<?, ?> blocks)) {
                continue;
            }
            for (Map.Entry<?, ?> b : blocks.entrySet()) {
                String id = String.valueOf(b.getKey());
                if (!(b.getValue() instanceof Map<?, ?> block)) {
                    continue;
                }
                for (Map.Entry<String, Object> app : appearances(block).entrySet()) {
                    Demand d = demand(id, app.getKey());
                    if (d == null || !(app.getValue() instanceof Map<?, ?> m) || !(m.get("state") instanceof String state)) {
                        continue;
                    }
                    Assignment a = hitboxOnly.get(d.id());
                    if (a == null || !a.state().equals(state)) {
                        continue;
                    }
                    checked++;
                    if (!Boolean.TRUE.equals(m.get("transparent"))) {
                        problems.add(e.getKey() + " " + id + "#" + app.getKey() + ": hitbox-only carrier " + state
                            + " without transparent: true (the client draws the vanilla model)");
                    }
                    for (String key : List.of("model", "models", "texture", "blueprint")) {
                        if (m.containsKey(key)) {
                            problems.add(e.getKey() + " " + id + "#" + app.getKey() + ": hitbox-only carrier " + state
                                + " also brings " + key);
                        }
                    }
                    drawnEmpty.add(state);
                }
            }
        }
        // every barrel (2 upright + 16 side appearances per wood) and the rack
        assertThat(checked).isGreaterThanOrEqualTo(HitboxDemands.BARREL_WOODS.size() * 18 + 1);
        assertThat(drawnEmpty).contains("minecraft:cactus[age=10]",
            "minecraft:iron_trapdoor[facing=east,half=top,open=false,powered=false,waterlogged=false]");
        assertThat(problems).isEmpty();
    }

    /**
     * An {@code auto_state} outside the visual templates is allowed only for a full-cube appearance its generator draws
     * from a CE pool ({@link HitboxDemands#FULL_CUBE_POOLS}), and must name that pool.
     */
    static void checkPool(String path, String id, String appearance, Object autoState, List<String> problems) {
        Map.Entry<String, String> declared = HitboxDemands.FULL_CUBE_POOLS.get(id);
        if (declared == null && HitboxDemands.POOLS.containsKey(id)) {
            String want = HitboxDemands.charcoalPitPool(id, appearance);
            String pool = autoState instanceof Map<?, ?> m ? String.valueOf(m.get("type")) : String.valueOf(autoState);
            if (!pool.equals(want)) {
                problems.add(path + " " + id + "#" + appearance + ": auto_state " + pool + " but the declared pool is " + want);
            }
            return;
        }
        if (declared == null || !matches(declared.getKey(), appearance)) {
            problems.add(path + " " + id + "#" + appearance + ": auto_state outside the visual templates and no declared full cube");
            return;
        }
        String pool = autoState instanceof Map<?, ?> m ? String.valueOf(m.get("type")) : String.valueOf(autoState);
        if (!declared.getValue().equals(pool)) {
            problems.add(path + " " + id + "#" + appearance + ": auto_state " + pool + " but the declared pool is "
                + declared.getValue());
        }
    }

    private static Demand demand(String id, String appearance) {
        List<Demand> mine = HitboxDemands.all().stream().filter(d -> d.blockId().equals(id)).toList();
        if (appearance.isEmpty() && mine.size() == 1) {
            return mine.getFirst(); // PAR-HEAT-21: a block with one state: entry (the standing torches) has one demand
        }
        return mine.stream().filter(d -> matches(d.appearance(), appearance)).findFirst().orElse(null);
    }

    private static void check(String path, String id, String appearance, String state, List<String> problems) {
        Demand demand = demand(id, appearance);
        if (demand == null) {
            problems.add(path + " " + id + "#" + appearance + ": explicit state " + state + " with no HitboxDemands entry");
            return;
        }
        Assignment a = HitboxAllocation.agreed(demand.blockId(), demand.appearance());
        if (!a.state().equals(state)) {
            problems.add(path + " " + id + "#" + appearance + ": " + state + " but the selector chose " + a.state());
        }
    }

    /** A demand's appearance is a name or a {@code *} pattern ({@code heat_*}). */
    static boolean matches(String pattern, String name) {
        if (!pattern.contains("*")) {
            return pattern.equals(name);
        }
        String[] parts = pattern.split("\\*", -1);
        return name.startsWith(parts[0]) && name.endsWith(parts[parts.length - 1])
            && name.length() >= parts[0].length() + parts[parts.length - 1].length();
    }

    /** {@code state:} (one appearance, keyed "") or {@code states: appearances:}. */
    private static Map<String, Object> appearances(Map<?, ?> block) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (block.get("state") instanceof Map<?, ?> single) {
            out.put("", single);
        }
        if (block.get("states") instanceof Map<?, ?> states && states.get("appearances") instanceof Map<?, ?> apps) {
            apps.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }

    private static Object load(String text) {
        LoaderOptions opts = new LoaderOptions();
        opts.setCodePointLimit(Integer.MAX_VALUE);
        return new Yaml(opts).load(text);
    }
}
