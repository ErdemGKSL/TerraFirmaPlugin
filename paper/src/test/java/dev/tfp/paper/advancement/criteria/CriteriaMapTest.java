/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.advancement.criteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.advancement.CriterionRef;
import dev.tfp.paper.advancement.criteria.CriteriaMap.Kind;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-DATA-03: every criterion of TFC's 115 advancements has a route, checked against the real checkout. */
class CriteriaMapTest {
    private static final CriteriaMap MAP = CriteriaMap.DEFAULT;
    /** TFC criterion -> trigger and its conditions, from {@code data/tfc/advancement}. */
    private static final Map<CriterionRef, JsonObject> TFC = new TreeMap<>(
        java.util.Comparator.comparing(CriterionRef::toString));

    @BeforeAll
    static void load() throws IOException {
        Path root = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"), "src/main/resources/data/tfc/advancement");
        assumeTrue(Files.isDirectory(root), "TFC checkout not present");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String adv = root.relativize(f).toString().replace('\\', '/');
                adv = "tfp:" + adv.substring(0, adv.length() - 5);
                try (Reader r = Files.newBufferedReader(f)) {
                    JsonObject crit = JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("criteria");
                    for (Map.Entry<String, JsonElement> e : crit.entrySet()) {
                        TFC.put(new CriterionRef(adv, e.getKey()), e.getValue().getAsJsonObject());
                    }
                }
            }
        }
    }

    private static String ours(String id) {
        return id.replace("tfc:", "tfp:");
    }

    @Test
    void everyTfcCriterionHasARouteAndNothingElseIsMapped() {
        assertThat(TFC).hasSize(438);
        assertThat(MAP.kinds().keySet()).containsExactlyInAnyOrderElementsOf(TFC.keySet());
    }

    @Test
    void routeFollowsTheTfcTrigger() {
        for (Map.Entry<CriterionRef, JsonObject> e : TFC.entrySet()) {
            String trigger = e.getValue().get("trigger").getAsString();
            Kind kind = MAP.kind(e.getKey()).orElseThrow();
            switch (trigger) {
                case "minecraft:inventory_changed" -> assertThat(kind).as(e.getKey().toString()).isEqualTo(Kind.INVENTORY);
                case "minecraft:consume_item" -> assertThat(kind).as(e.getKey().toString()).isEqualTo(Kind.CONSUME);
                case "minecraft:tick" -> assertThat(kind).as(e.getKey().toString()).isEqualTo(Kind.TICK);
                case "minecraft:player_killed_entity" -> assertThat(kind).as(e.getKey().toString()).isEqualTo(Kind.KILL);
                case "minecraft:location" -> assertThat(kind).as(e.getKey().toString()).isIn(Kind.BIOME, Kind.POSITION);
                case "tfc:eat_rotten_food", "tfc:full_nutrition" -> assertThat(kind).isEqualTo(Kind.NUTRITION);
                case "tfc:hooked_entity", "tfc:stab_entity", "tfc:present_day" ->
                    assertThat(kind).as(e.getKey().toString()).isEqualTo(Kind.EVENT);
                default -> {
                    assertThat(kind).as(e.getKey().toString()).isEqualTo(Kind.PENDING);
                    assertThat(MAP.pendingTrigger(e.getKey())).contains(trigger);
                }
            }
        }
    }

    @Test
    void inventoryConditionsMatchTfc() {
        Set<CriterionRef> seen = new HashSet<>();
        for (Map.Entry<CriterionRef, JsonObject> e : TFC.entrySet()) {
            if (MAP.kind(e.getKey()).orElseThrow() != Kind.INVENTORY) {
                continue;
            }
            String match = e.getValue().getAsJsonObject("conditions").getAsJsonArray("items").get(0).getAsJsonObject()
                .get("items").getAsString();
            boolean tag = match.startsWith("#");
            String id = ours(tag ? match.substring(1) : match);
            // The tag test says yes only for the one tag of the condition, the id test only for the one item.
            List<CriterionRef> earned = MAP.inventory(tag ? "tfp:probe" : id, (item, t) -> tag && t.equals(id), c -> false);
            assertThat(earned).as(e.getKey() + " <- " + match).contains(e.getKey());
            seen.add(e.getKey());
        }
        assertThat(seen).hasSize(248);
    }

    @Test
    void earnedCriteriaAreSkippedAndUnrelatedItemsEarnNothing() {
        assertThat(MAP.inventory("tfp:bellows", (i, t) -> false, c -> false))
            .containsExactly(CriterionRef.parse("tfp:story/bellows#bellows"));
        assertThat(MAP.inventory("tfp:bellows", (i, t) -> false, c -> true)).isEmpty();
        assertThat(MAP.inventory("tfp:groundcover/stick", (i, t) -> false, c -> false)).isEmpty();
    }

    @Test
    void consumeBiomeAndKillConditionsMatchTfc() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<CriterionRef, JsonObject> e : TFC.entrySet()) {
            JsonObject cond = e.getValue().getAsJsonObject("conditions");
            switch (MAP.kind(e.getKey()).orElseThrow()) {
                case CONSUME -> {
                    String item = ours(cond.getAsJsonObject("item").get("items").getAsString());
                    if (!MAP.consumed(item).equals(java.util.Optional.of(e.getKey()))) {
                        problems.add("consume " + e.getKey());
                    }
                }
                case BIOME -> {
                    String biome = ours(cond.toString().replaceAll(".*\"biomes\":\"([a-z_:]+)\".*", "$1"));
                    if (!MAP.biome(biome).contains(e.getKey())) {
                        problems.add("biome " + e.getKey() + " " + biome);
                    }
                }
                case KILL -> {
                    String type = ours(cond.getAsJsonArray("entity").get(0).getAsJsonObject().getAsJsonObject("predicate")
                        .get("type").getAsString());
                    boolean tag = type.startsWith("#");
                    String id = tag ? type.substring(1) : type;
                    if (!MAP.kills(tag ? "tfp:probe" : id, (x, t) -> tag && t.equals(id)).contains(e.getKey())) {
                        problems.add("kill " + e.getKey());
                    }
                }
                default -> { }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void globeTrotterAndMealBounds() {
        assertThat(MAP.position(9999)).contains(CriteriaMap.GLOBE_HIGH);
        assertThat(MAP.position(9998)).isEmpty();
        assertThat(MAP.position(-9998)).isEmpty();
        assertThat(MAP.position(-9999)).contains(CriteriaMap.GLOBE_LOW);
        assertThat(MAP.meal(true, Float.NaN)).containsExactly(CriteriaMap.EAT_ROTTEN_FOOD);
        assertThat(MAP.meal(false, 0.999f)).containsExactly(CriteriaMap.FULL_NUTRITION);
        assertThat(MAP.meal(false, 0.9f)).isEmpty();
        assertThat(MAP.biome("minecraft:plains")).isEmpty();
        assertThat(MAP.biome("tfp:no_such_biome")).isEmpty();
        assertThat(MAP.biome("tfp:deep_ocean_trench")).containsExactlyInAnyOrder(
            CriterionRef.parse("tfp:world/adventuring_time#deep_ocean_trench"), CriterionRef.parse("tfp:world/trench#deep_ocean_trench"));
        assertThat(CriterionRef.parse("tfp:story/bronze_age#tools/bronze").criterion()).isEqualTo("tools/bronze");
    }

    @Test
    void tfcTreeIsObtainableThroughScriptedEvents() {
        // Replay one scripted event per criterion and collect what the map awards; every advancement of the tree must end
        // with at least one criterion it can earn, except those waiting for a feature event (PENDING, PAR-DATA-19).
        Set<CriterionRef> earned = new HashSet<>();
        for (Map.Entry<CriterionRef, Kind> e : MAP.kinds().entrySet()) {
            CriterionRef c = e.getKey();
            JsonObject cond = TFC.get(c).getAsJsonObject("conditions");
            switch (e.getValue()) {
                case INVENTORY -> {
                    String m = ours(cond.getAsJsonArray("items").get(0).getAsJsonObject().get("items").getAsString());
                    earned.addAll(m.startsWith("#") ? MAP.inventory("tfp:probe", (i, t) -> t.equals(m.substring(1)), x -> false)
                        : MAP.inventory(m, (i, t) -> false, x -> false));
                }
                case CONSUME -> MAP.consumed(ours(cond.getAsJsonObject("item").get("items").getAsString())).ifPresent(earned::add);
                case BIOME -> earned.addAll(MAP.biome("tfp:" + c.criterion()));
                case POSITION -> MAP.position(c.equals(CriteriaMap.GLOBE_HIGH) ? 10000 : -10000).ifPresent(earned::add);
                case NUTRITION -> earned.addAll(MAP.meal(c.equals(CriteriaMap.EAT_ROTTEN_FOOD), 1f));
                case EVENT -> {
                    String trigger = TFC.get(c).get("trigger").getAsString();
                    if (trigger.equals("tfc:hooked_entity")) {
                        String tag = cond.getAsJsonObject("entity").get("type").getAsString().replace("#tfc:", "tfp:");
                        earned.addAll(MAP.hooked("tfp:probe", (i, t) -> t.equals(tag)));
                    } else if (trigger.equals("tfc:stab_entity")) {
                        MAP.stab("tfp:rabbit", 50).ifPresent(earned::add);
                    } else {
                        MAP.presentDay(java.time.LocalDate.of(999, 12, 31), 0, 8).ifPresent(earned::add);
                    }
                }
                default -> { }
            }
        }
        for (Map.Entry<CriterionRef, Kind> e : MAP.kinds().entrySet()) {
            if (e.getValue() != Kind.PENDING && e.getValue() != Kind.TICK && e.getValue() != Kind.KILL) {
                assertThat(earned).as("scripted event for " + e.getKey()).contains(e.getKey());
            }
        }
        assertThat(MAP.kinds().values().stream().filter(k -> k == Kind.PENDING).count()).isEqualTo(17);
    }
}
