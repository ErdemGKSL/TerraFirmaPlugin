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
    private static final String GROUNDCOVER_PREFIX = "tfp:groundcover/";

    /** The demands whose carrier all profiles and families agree on: everything but the per-family groundcover. */
    private static java.util.List<Demand> agreedDemands() {
        return HitboxDemands.all().stream().filter(d -> !d.blockId().startsWith(GROUNDCOVER_PREFIX)).toList();
    }

    @org.junit.jupiter.api.Test
    void groundcoverCarriersAreAllocatedInEveryFamily() {
        long demands = HitboxDemands.all().stream().filter(d -> d.blockId().startsWith(GROUNDCOVER_PREFIX)).count();
        assertThat(demands).isPositive();
        for (java.util.Map.Entry<String, HitboxAllocation.Result> e : HitboxAllocation.standard().entrySet()) {
            for (Demand d : agreedDemands()) {
                if (d.blockId().startsWith(GROUNDCOVER_PREFIX)) {
                    assertThat(e.getValue().get(d.blockId(), d.appearance()).state()).as(e.getKey() + " " + d.id()).isNotBlank();
                }
            }
        }
    }


    @Test
    void everyExplicitStateInTheGeneratedYamlComesFromTheSelector() {
        SortedMap<String, byte[]> files = Datagen.generate(RealCheckout.open().root(), TfcCheckout.PINNED_COMMIT).tree().files();
        List<String> problems = new ArrayList<>();
        int states = 0;
        int pools = 0;
        Map<String, java.util.SortedSet<String>> plantPoolStates = new java.util.TreeMap<>();
        java.util.SortedSet<String> checkedIds = new java.util.TreeSet<>();
        java.util.SortedSet<String> mappings = new java.util.TreeSet<>();
        java.util.SortedSet<String> keptVanillaIds = new java.util.TreeSet<>();
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
                        // PAR-WORLD-59: the plant looks take the selector's plant pools (PlantPools); every state of
                        // a plant pool is counted once per id against the pool's size below
                        java.util.Optional<List<String>> plantPool = PlantPools.poolState(app);
                        if (plantPool.isPresent()) {
                            plantPoolStates.computeIfAbsent(plantPool.get().get(0), k -> new java.util.TreeSet<>())
                                .add(plantPool.get().get(1));
                            if (!id.startsWith("tfp:plant/") && !HitboxDemands.POOLS.containsKey(id)) {
                                problems.add(path + " " + id + "#" + a.getKey() + ": plant pool " + plantPool.get()
                                    + " outside the plant blocks");
                            }
                        } else if (app.containsKey("auto_state")) {
                            pools++;
                            checkPool(path, id, a.getKey(), app.get("auto_state"), problems);
                        }
                        if (app.get("state") instanceof String state
                                && dev.tfp.datagen.visuals.HideTargets.KEEP_VANILLA.contains(state)) {
                            // PAR-WORLD-04: the grass blocks draw on the kept vanilla grass_block states, a visual
                            // choice (HideTargets), not a hitbox carrier of the selector.
                            keptVanillaIds.add(id);
                        } else if (app.get("state") instanceof String state) {
                            states++;
                            checkedIds.add(id);
                            check(path, id, a.getKey(), state, null, problems);
                        } else if (app.containsKey("$$fallback")) {
                            // PAR-WORLD-31: a version selector where the families' carriers differ
                            for (Map.Entry<?, ?> branch : app.entrySet()) {
                                if (branch.getValue() instanceof Map<?, ?> look && look.get("state") instanceof String state) {
                                    states++;
                                    checkedIds.add(id);
                                    check(path, id, a.getKey(), state, String.valueOf(branch.getKey()).equals("$$fallback")
                                        ? dev.tfp.datagen.visuals.VersionFamily.V1_21 : dev.tfp.datagen.visuals.VersionFamily.V26,
                                        problems);
                                }
                            }
                        }
                    }
                }
            }
            if (top.get("block_state_mappings") instanceof Map<?, ?> top0) {
                // PAR-WORLD-31: family-specific mappings sit under $$>=26.1 / $$<26.1
                Map<Object, Object> m = new java.util.LinkedHashMap<>();
                top0.forEach((k, v) -> {
                    if (String.valueOf(k).startsWith("$$") && v instanceof Map<?, ?> nested) {
                        m.putAll(nested);
                    } else {
                        m.put(k, v);
                    }
                });
                for (Map.Entry<?, ?> x : m.entrySet()) {
                    String from = String.valueOf(x.getKey());
                    String to = String.valueOf(x.getValue());
                    mappings.add(from + " -> " + to);
                    boolean fromSelector = HitboxAllocation.standard().values().stream()
                        .flatMap(r -> r.assignments().stream())
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
        // PAR-CLIM-04: the snow pile (8 layer counts, hitbox-only), the icicle (tip, pillar) and, on the leaves pool, the
        // ice pile and sea ice.
        // PAR-FARM-03: the crops, dead crops and wild crops of the 29 crops (87 block ids; every appearance is explicit).
        assertThat(states).isGreaterThanOrEqualTo(83 + 24 + 7 + 20 * 16 + 1 + 10 + 3 + 17 * 16 + 17 * 2 + 4 + 10 + 87 + 75 + 150 + 267
            + 20 * (80 + 4 + 32 + 32) // PAR-DECO-01: per wood stairs, slab (double draws the planks' visual), fence, log fence
            + 20 * (32 + 64 + 64 + 24 + 2) // PAR-DECO-02: per wood fence gate, door, trapdoor, button, pressure plate
            + 144); // PAR-METAL-13: the channel (128 states) and the mold table (16)
        // PAR-CLIM-04: the ice pile and sea ice (one leaves pool appearance each).
        // PAR-WORLD-44: the 20 twigs on their one shared tripwire state.
        // PAR-WORLD-59: the plant pools hold every distinct look (and the twigs' one shared tripwire state) within their size
        assertThat(plantPoolStates).containsOnlyKeys(PlantPools.SIZES.keySet());
        plantPoolStates.forEach((pool, ids) -> assertThat(ids.size()).as("states of the " + pool + " pool")
            .isLessThanOrEqualTo(PlantPools.SIZES.get(pool)));
        assertThat(plantPoolStates.get(PlantPools.TRIPWIRE)).contains(HitboxDemands.TWIG_SHARE_KEY);
        assertThat(pools).as("auto_state appearances of declared pools").isEqualTo(3 + 8 + 32 + 1 + 2 + 4 // PAR-FARM-23: pumpkin, melon, rotten x2 on the solid pool
            + (int) HitboxDemands.all().stream().filter(d -> d.blockId().startsWith("tfp:grass_path/") && agreedOrOld(d).pool())
                .count()); // PAR-WORLD-42: the grass paths on the leaves pool
        java.util.SortedSet<String> expected = new java.util.TreeSet<>(
            List.of("tfp:bellows", "tfp:charcoal_forge", "tfp:firepit", "tfp:grill", "tfp:pot", "tfp:quern",
                "tfp:scraping", HitboxDemands.COMPOSTER_ID, HitboxDemands.LOG_PILE_ID, HitboxDemands.CHARCOAL_PILE_ID,
                HitboxDemands.BARREL_RACK_ID, HitboxDemands.TORCH_ID, HitboxDemands.WALL_TORCH_ID,
                HitboxDemands.DEAD_TORCH_ID, HitboxDemands.DEAD_WALL_TORCH_ID, HitboxDemands.POWDERKEG_ID,
                HitboxDemands.JACK_O_LANTERN_ID, HitboxDemands.SNOW_PILE_ID, HitboxDemands.ICICLE_ID));
        HitboxDemands.BARREL_WOODS.forEach(w -> expected.add(HitboxDemands.barrelId(w)));
        // PAR-FARM-26: the farmland of each soil (one appearance, TFC's farmland outline).
        HitboxDemands.FARMLAND_SOILS.forEach(soil -> expected.add(HitboxDemands.farmlandId(soil)));
        // PAR-FARM-25: the 40 growing plants (hitbox-only, every appearance on the body plant outline).
        HitboxDemands.GROWING_PLANTS.forEach(n -> expected.add(HitboxDemands.growingPlantId(n)));
        expected.addAll(HitboxDemands.candleIds(HitboxDemands.CANDLE_ID));
        expected.addAll(HitboxDemands.candleIds(HitboxDemands.CANDLE_CAKE_ID));
        // PAR-METAL-03: the nine metal anvils (4 facings, hitbox-only) and the seven rock anvils (one appearance each).
        HitboxDemands.ANVIL_METALS.forEach(m -> expected.add(HitboxDemands.anvilId(m)));
        HitboxDemands.ANVIL_ROCKS.forEach(r -> expected.add(HitboxDemands.rockAnvilId(r)));
        for (String kind : HitboxDemands.CROP_KINDS) {
            HitboxDemands.CROPS.forEach(c -> expected.add(HitboxDemands.cropId(kind, c)));
        }
        // PAR-FARM-14: the six stationary berry bushes and the dead one (75 states, hitbox-only).
        HitboxDemands.BERRY_SPECIES.forEach(sp -> expected.add(HitboxDemands.berryId(sp)));
        expected.add(HitboxDemands.BERRY_DEAD_ID);
        // PAR-METAL-12: the crucible (one appearance for its 16 channel-connection states).
        expected.add(HitboxDemands.CRUCIBLE_ID);
        // PAR-METAL-13: the channel (128 states, 64 appearances on 16 outlines) and the mold table (16 states), hitbox-only.
        expected.addAll(List.of(HitboxDemands.CHANNEL_ID, HitboxDemands.MOLD_TABLE_ID));
        // PAR-CLIM-27: the thermometer, anemometer, vane and calendar clock (150 states, hitbox-only).
        expected.addAll(List.of(HitboxDemands.THERMOMETER_ID, HitboxDemands.ANEMOMETER_ID, HitboxDemands.VANE_ID, HitboxDemands.CLOCK_ID));
        // PAR-FARM-15: the four spreading bushes, their canes, the cranberry, the dead bush and the dead cane (267 states).
        for (String sp : HitboxDemands.SPREADING_SPECIES) {
            expected.add(HitboxDemands.SPREADING_PREFIX + sp);
            expected.add(HitboxDemands.SPREADING_PREFIX + sp + "_cane");
        }
        expected.addAll(List.of(HitboxDemands.SPREADING_PREFIX + "cranberry", HitboxDemands.SPREADING_PREFIX + "dead",
            HitboxDemands.SPREADING_PREFIX + "dead_cane"));
        for (String w : HitboxDemands.BARREL_WOODS) { // PAR-DECO-01
            expected.addAll(List.of(HitboxDemands.woodStairsId(w), HitboxDemands.woodSlabId(w), HitboxDemands.woodFenceId(w),
                HitboxDemands.woodLogFenceId(w)));
            HitboxDemands.WOOD_LOGIC_KINDS.forEach(k -> expected.add(HitboxDemands.woodLogicId(k, w))); // PAR-DECO-02
        }
        for (String w : HitboxDemands.BARREL_WOODS) { // PAR-WORLD-44: sapling, potted sapling, fallen leaves (hitbox-only)
            for (String kind : List.of(HitboxDemands.SAPLING, HitboxDemands.POTTED_SAPLING, HitboxDemands.FALLEN_LEAVES)) {
                expected.add(HitboxDemands.woodPlantId(kind, w));
            }
        }
        // PAR-FARM-16: the fruit trees' saplings, branches and growing branches (the leaves are 32 leaves-pool appearances,
        // counted above); PAR-FARM-17: the banana's sapling, plant and dead plant (hitbox-only).
        for (String tree : HitboxDemands.FRUIT_TREES) {
            expected.addAll(List.of(HitboxDemands.fruitId(tree, "sapling"), HitboxDemands.fruitId(tree, "branch"),
                HitboxDemands.fruitId(tree, "growing_branch")));
        }
        expected.addAll(List.of(HitboxDemands.BANANA_SAPLING_ID, HitboxDemands.BANANA_PLANT_ID, HitboxDemands.BANANA_DEAD_ID));
        // PAR-WORLD-47: the plant shapes with dry states (hitbox-only on the berry bush and barrel rows).
        HitboxDemands.PLANT_SHAPE_ROWS.values().forEach(names -> names.forEach(n -> expected.add(HitboxDemands.plantId(n))));
        assertThat(expected).hasSize(36 + 35 + 2 + 16 + 87 + 7 + 1 + 4 + 11 + 20 * 4 + 20 * 5 + 2 + 20 * 3 + 24 + 3 + 8 + 40 + 14); // + 8: PAR-FARM-26 farmland, + 40: PAR-FARM-25 growing plants, + 14: PAR-WORLD-47 plant shapes
        // PAR-WORLD-31: the loose and mossy loose rocks, small ores and groundcover types; PAR-WORLD-42: the grass paths
        for (String rock : HitboxDemands.LOOSE_ROCKS) {
            HitboxDemands.LOOSE_KINDS.forEach(k -> expected.add(HitboxDemands.looseRockId(k, rock)));
        }
        HitboxDemands.SMALL_ORES.forEach(o -> expected.add(HitboxDemands.smallOreId(o)));
        HitboxDemands.GROUNDCOVER_TYPES.keySet().forEach(t -> expected.add(HitboxDemands.groundcoverId(t)));
        for (Demand d : HitboxDemands.all()) { // the grass paths on an explicit state (the others take the leaves pool)
            if (d.blockId().startsWith("tfp:grass_path/") && !agreedOrOld(d).pool()) {
                expected.add(d.blockId());
            }
        }
        java.util.SortedSet<String> grass = new java.util.TreeSet<>();
        for (String soil : List.of("entisol", "aridisol", "oxisol", "fluvisol", "andisol", "podzol", "alfisol", "mollisol")) {
            dev.tfp.datagen.soil.SoilBlocks.GRASS_TYPES.forEach(t -> grass.add("tfp:" + t + "/" + soil));
        }
        dev.tfp.datagen.soil.SoilBlocks.SINGLE_GRASS.forEach(t -> grass.add("tfp:" + t));
        assertThat(keptVanillaIds).as("PAR-WORLD-04 grass blocks on the kept grass_block states").isEqualTo(grass);
        assertThat(checkedIds).as("block ids whose explicit appearance states were checked")
            .containsExactlyElementsOf(expected);
        assertThat(mappings).as("generator block_state_mappings")
            .containsExactlyInAnyOrder("minecraft:snow[layers=6] -> minecraft:snow[layers=4]",
                "minecraft:snow[layers=7] -> minecraft:snow[layers=8]",
                // PAR-PROC-37: the two-layer charcoal pile's carrier.
                "minecraft:snow[layers=2] -> minecraft:snow[layers=3]",
                "minecraft:heavy_weighted_pressure_plate[power=0] -> minecraft:heavy_weighted_pressure_plate[power=1]");
        // PAR-HEAT-36: the firepit left the daylight detector (it occludes the block below), so no detector mapping is left.
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
        check("x.yml", "tfp:bellows", "north", "minecraft:mud", null, problems);
        check("x.yml", "tfp:unknown", "only", "minecraft:dirt_path", null, problems);
        check("x.yml", "tfp:bellows", "north", HitboxAllocation.agreed("tfp:bellows", "north").state(), null, problems);
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
        for (Demand d : agreedDemands()) {
            if (d.blockId().contains("pile")) {
                assertThat(agreedOrOld(d).state()).as(d.id())
                    .doesNotStartWith("minecraft:daylight_detector").doesNotStartWith(VanillaCandidates.POOL_PREFIX);
            }
        }
    }

    @Test
    void theWeatherBlockDemandsArePinned() {
        // PAR-CLIM-04: 8 snow pile and 2 icicle demands, each on a TFC row of hitbox/tfc-shapes.json; the ice pile and sea
        // ice are leaves pool appearances. Every snow pile layer count is hitbox-only (vanilla snow's states are other
        // blocks' carriers or hide targets); the six-pixel pile borrows the eight-pixel row (its best carrier, the daylight
        // detector, is crafted in TFC).
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        long snow = HitboxDemands.all().stream().filter(d -> d.blockId().equals(HitboxDemands.SNOW_PILE_ID)).count();
        long icicle = HitboxDemands.all().stream().filter(d -> d.blockId().equals(HitboxDemands.ICICLE_ID)).count();
        long ice = HitboxDemands.all().stream().filter(d -> d.blockId().equals(HitboxDemands.ICE_PILE_ID)
            || d.blockId().equals(HitboxDemands.SEA_ICE_ID)).count();
        assertThat(List.of(snow, icicle, ice)).containsExactly(8L, 2L, 0L);
        for (Demand d : agreedDemands()) {
            if (d.blockId().equals(HitboxDemands.SNOW_PILE_ID) || d.blockId().equals(HitboxDemands.ICICLE_ID)) {
                assertThat(rows).as(d.id()).containsKey(d.tfcKey());
            }
        }
        assertThat(HitboxDemands.snowPileDemandKey(3)).isEqualTo(TfcShapes.snowPileKey(4));
        assertThat(HitboxDemands.snowPileDemandKey(5)).isEqualTo(TfcShapes.snowPileKey(5));
        for (int layers = 1; layers <= TfcShapes.SNOW_PILE_MAX; layers++) {
            Assignment a = HitboxAllocation.agreed(HitboxDemands.SNOW_PILE_ID, HitboxDemands.snowPileAppearance(layers));
            assertThat(a.mode()).as("layers " + layers).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
            assertThat(a.state()).as("layers " + layers).doesNotStartWith("minecraft:daylight_detector")
                .doesNotStartWith(VanillaCandidates.POOL_PREFIX);
        }
        assertThat(HitboxDemands.charcoalPitPool(HitboxDemands.ICE_PILE_ID, "base")).isEqualTo("leaves");
        assertThat(HitboxDemands.charcoalPitPool(HitboxDemands.SEA_ICE_ID, "base")).isEqualTo("leaves");
        // the icicle: the selector's own pick (pointed dripstone is rejected by the carrier policy, its XZ offset)
        for (boolean tip : new boolean[] {true, false}) {
            assertThat(HitboxAllocation.agreed(HitboxDemands.ICICLE_ID, HitboxDemands.icicleAppearance(tip)).state())
                .doesNotStartWith("minecraft:pointed_dripstone");
        }
    }

    @Test
    void theCropDemandsArePinned() {
        // PAR-FARM-03: 29 crops x (crop, dead_crop, wild_crop) x 3 outlines = 261 demands, each on a row of
        // hitbox/tfc-shapes.json, all hitbox-only (look transparent) and sharing one carrier per outline.
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        List<Demand> crops = HitboxDemands.all().stream().filter(d -> d.blockId().matches("tfp:(crop|dead_crop|wild_crop)/.+"))
            .toList();
        assertThat(crops).hasSize(29 * 3 * 3);
        for (Demand d : crops) {
            assertThat(rows).as(d.id()).containsKey(d.tfcKey());
            assertThat(d.look()).isEqualTo(HitboxAllocation.TRANSPARENT);
            assertThat(rows.get(d.tfcKey()).collision().isEmpty()).as(d.id() + " has no collision").isTrue();
            assertThat(agreedOrOld(d).mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
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
        // PAR-HEAT-36 (was PAR-HEAT-35's daylight detector): the four (lit, axis) appearances share one hitbox-only carrier,
        // drawn empty; TFC's models are item displays. The carrier is the grill's (the firepit asks for the grill's
        // outline row): an unlit soul campfire, a noOcclusion block, so the client culls no face of the block below (the
        // detector's 6 px slab did). It is absent from TFC worlds: no hide mapping.
        String expected = "minecraft:soul_campfire[facing=west,lit=false,signal_fire=false,waterlogged=false]";
        java.util.Set<String> carriers = new java.util.HashSet<>();
        for (boolean lit : new boolean[] {true, false}) {
            for (String axis : HitboxDemands.FIREPIT_AXES) {
                String app = HitboxDemands.firepitAppearance(lit, axis);
                Assignment a = HitboxAllocation.agreed(HitboxDemands.FIREPIT_ID, app);
                assertThat(a.mode()).as(app).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
                assertThat(a.state()).as(app).isEqualTo(expected);
                assertThat(a.demand().look()).as(app).isEqualTo(HitboxAllocation.TRANSPARENT);
                assertThat(a.hideTarget()).as(app).isNull();
                carriers.add(a.state());
            }
        }
        assertThat(carriers).hasSize(1);
        // The detector powers PAR-HEAT-23/35 took are free again: none of the firepit's demands sits on a detector.
        for (Demand d : agreedDemands()) {
            if (d.blockId().equals(HitboxDemands.FIREPIT_ID)) {
                assertThat(agreedOrOld(d).state()).as(d.id()).doesNotStartWith("minecraft:daylight_detector");
            }
        }
        // The grill shares the carrier (same transparent look); theGrillCarrierIsPinned checks that no other block puts a
        // model or a vanilla look on it.
        assertThat(HitboxAllocation.agreed(HitboxDemands.GRILL_ID, HitboxDemands.firepitAppearance(true, "x")).state())
            .isEqualTo(expected);
    }

    @Test
    void theGrillCarrierIsPinned() {
        // PAR-HEAT-24: no free state scores 0.5 or more for TFC's grill outline with a model (snow[layers=5], the old
        // vanilla-look carrier, has no free hide target), so the four (lit, axis) appearances share the best free
        // hitbox-only carrier, an unlit soul campfire (absent from TFC worlds: no hide mapping), drawn empty under the
        // item displays of the merged models. Served last (weight -7): it moved no other carrier.
        String carrier = "minecraft:soul_campfire[facing=west,lit=false,signal_fire=false,waterlogged=false]";
        for (boolean lit : new boolean[] {true, false}) {
            for (String axis : HitboxDemands.FIREPIT_AXES) {
                Assignment a = HitboxAllocation.agreed(HitboxDemands.GRILL_ID, HitboxDemands.firepitAppearance(lit, axis));
                assertThat(a.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
                assertThat(a.state()).isEqualTo(carrier);
                assertThat(a.hideTarget()).isNull();
            }
        }
        // The old vanilla-look carrier stays reserved, so the snow-layer carriers of the other blocks did not move.
        Assignment reserved = HitboxAllocation.agreed(HitboxDemands.GRILL_ID, HitboxDemands.GRILL_RESERVED);
        assertThat(reserved.state()).isEqualTo("minecraft:snow[layers=5]");
        assertThat(reserved.mode()).isEqualTo(HitboxAllocation.Mode.VANILLA);
        for (Demand d : agreedDemands()) {
            if (!d.blockId().equals(HitboxDemands.GRILL_ID)) {
                Assignment a = agreedOrOld(d);
                assertThat(a.state()).as(d.id()).isNotEqualTo(reserved.state());
                assertThat(a.state().equals(carrier) && a.mode() != HitboxAllocation.Mode.HITBOX_ONLY).as(d.id()).isFalse();
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
        for (Demand d : agreedDemands()) {
            if (!d.blockId().equals(HitboxDemands.POWDERKEG_ID)) {
                assertThat(agreedOrOld(d).state()).as(d.id())
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
            Assignment a = agreedOrOld(d);
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
        // PAR-DECO-01: plus 32 states of every fence and log fence
        assertThat(checked).isGreaterThanOrEqualTo(HitboxDemands.BARREL_WOODS.size() * (18 + 2 * 32) + 1);
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

    /**
     * The agreed carrier of a demand, or, for a demand whose carriers differ between the version families (PAR-WORLD-31
     * writes a {@code $$>=26.1} selector for those), its 1.21.x carrier.
     */
    private static Assignment agreedOrOld(Demand d) {
        Assignment first = null;
        for (HitboxAllocation.Result r : HitboxAllocation.standard().values()) {
            Assignment a = r.get(d.blockId(), d.appearance());
            if (first == null || r.family() == dev.tfp.datagen.visuals.VersionFamily.V1_21 && !a.state().equals(first.state())) {
                first = a;
            }
        }
        return first;
    }

    /** {@code family}: the version family of a {@code $$} branch (PAR-WORLD-31), or null for a plain appearance (all agree). */
    private static void check(String path, String id, String appearance, String state,
            dev.tfp.datagen.visuals.VersionFamily family, List<String> problems) {
        Demand demand = demand(id, appearance);
        if (demand == null) {
            problems.add(path + " " + id + "#" + appearance + ": explicit state " + state + " with no HitboxDemands entry");
            return;
        }
        for (HitboxAllocation.Result r : HitboxAllocation.standard().values()) {
            if (family != null && r.family() != family) {
                continue;
            }
            Assignment a = r.get(demand.blockId(), demand.appearance());
            if (!a.state().equals(state)) {
                problems.add(path + " " + id + "#" + appearance + ": " + state + " but the selector chose " + a.state());
                return;
            }
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
