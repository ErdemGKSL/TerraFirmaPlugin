/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation.Assignment;
import dev.tfp.datagen.hitbox.HitboxAllocation.Mode;
import dev.tfp.datagen.hitbox.HitboxAllocation.Result;
import dev.tfp.datagen.hitbox.HitboxCarrier.Choice;
import dev.tfp.datagen.hitbox.HitboxCarrier.TfcState;
import dev.tfp.datagen.hitbox.VanillaCandidates.BlockPolicy;
import dev.tfp.datagen.hitbox.VanillaCandidates.Presence;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-44: the carrier policy agrees with what players can craft. A block the selector may use as is
 * ({@code presence: absent}) must not be the result of an active recipe, else every crafted and placed one would show
 * the carrier's look (a transparent carrier on {@code heavy_weighted_pressure_plate[power=0]} drew every placed heavy
 * plate empty: TFC crafts it from two wrought-iron ingots). Recipes are read from TFC's recipe tree (generated and main
 * resources, files with a {@code neoforge:false} condition skipped) and, when present, from our exported
 * {@code content/generated/content/data}. The kept vanilla recipes TFC does not name are listed in {@link #KEPT_VANILLA}.
 */
class CarrierPolicyRecipeTest {
    private static final String HEAVY = "minecraft:heavy_weighted_pressure_plate";
    private static final String LIGHT = "minecraft:light_weighted_pressure_plate";

    /**
     * Absent blocks that a recipe makes, kept absent on purpose. Allowed only while no allocation draws a non-vanilla look
     * on them ({@link #allowListedBlocksKeepTheirVanillaLook}): a carrier in vanilla mode shows the block as it is, so a
     * crafted one looks right. The day a model or transparent look lands on one, the block needs a hide (natural) or the
     * demand another carrier.
     */
    static final Map<String, String> ALLOWED = Map.of(
        "minecraft:daylight_detector", "TFC recipe (lens + redstone + lumber); the firepit, bloom and ingot-pile carriers on"
            + " it are vanilla-look today (the device look is an entity_renderer)",
        "minecraft:lightning_rod", "TFC recipe (3 c:rods/copper); the rock spike tip ranks it but rock spikes are no hitbox"
            + " demand yet");

    /**
     * Vanilla recipes TFC keeps (its {@code data/minecraft/recipe} neither removes nor overrides them) whose block the
     * policy lists, with their inputs (vanilla 1.21.11 data). PAR-PROC-03 lets kept recipes craft on vanilla items. The
     * gold plate's gold comes from the Nether (nether gold ore, piglins, bastion loot), so with the Nether on players craft
     * and place it: never absent.
     */
    static final Map<String, String> KEPT_VANILLA = Map.of(LIGHT, "2 minecraft:gold_ingot");

    private static Path repoRoot() {
        return Path.of(System.getProperty("tfp.repoRoot", "../.."));
    }

    /** The {@code minecraft:} item ids made by every active recipe under {@code dataRoot} (any namespace), with a file each. */
    static Map<String, String> recipeResults(Path dataRoot) throws IOException {
        Map<String, String> out = new TreeMap<>();
        if (!Files.isDirectory(dataRoot)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(dataRoot)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String rel = dataRoot.relativize(f).toString().replace('\\', '/');
                String[] parts = rel.split("/");
                if (parts.length < 3 || !parts[1].equals("recipe")) {
                    continue;
                }
                JsonElement json = JsonParser.parseString(Files.readString(f));
                if (!json.isJsonObject() || disabled(json.getAsJsonObject())) {
                    continue;
                }
                Set<String> ids = new TreeSet<>();
                collect(json, false, ids);
                for (String id : ids) {
                    out.putIfAbsent(id, rel);
                }
            }
        }
        return out;
    }

    /** Whether the recipe carries a {@code neoforge:false} condition (TFC's way to remove a vanilla recipe). */
    static boolean disabled(JsonObject recipe) {
        JsonElement c = recipe.get("neoforge:conditions");
        if (c == null || !c.isJsonArray()) {
            return false;
        }
        for (JsonElement e : c.getAsJsonArray()) {
            if (e.isJsonObject() && "neoforge:false".equals(str(e.getAsJsonObject().get("type")))) {
                return true;
            }
        }
        return false;
    }

    private static String str(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    /** Item ids ({@code id} / {@code item}) under any key naming a result or output, at any depth. */
    static void collect(JsonElement e, boolean inResult, Set<String> out) {
        if (e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) {
                collect(x, inResult, out);
            }
        } else if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> kv : e.getAsJsonObject().entrySet()) {
                String key = kv.getKey().toLowerCase(Locale.ROOT);
                boolean result = inResult || key.contains("result") || key.contains("output");
                String s = str(kv.getValue());
                if (result && s != null && (key.equals("id") || key.equals("item")) && s.startsWith("minecraft:")) {
                    out.add(s);
                } else {
                    collect(kv.getValue(), result, out);
                }
            }
        } else if (inResult && e.isJsonPrimitive() && e.getAsString().startsWith("minecraft:")
            && e.getAsJsonPrimitive().isString()) {
            // a bare id as the result value ("result": "minecraft:x")
            out.add(e.getAsString());
        }
    }

    /** TFC's recipe results plus our exported data's (when generated). */
    static Map<String, String> allRecipeResults() throws IOException {
        TfcCheckout tfc = RealCheckout.open();
        Map<String, String> out = new TreeMap<>();
        out.putAll(recipeResults(tfc.root().resolve(TfcCheckout.GENERATED + "data")));
        recipeResults(tfc.root().resolve(TfcCheckout.MAIN + "data")).forEach(out::putIfAbsent);
        recipeResults(repoRoot().resolve("content/generated/content/data")).forEach(out::putIfAbsent);
        return out;
    }

    /** Absent policy blocks a recipe makes that the allow-list does not name, with the recipe. */
    static Map<String, String> offences(Map<String, BlockPolicy> policies, Map<String, String> results) {
        Map<String, String> out = new TreeMap<>();
        policies.forEach((block, p) -> {
            if (p.presence() == Presence.ABSENT && !ALLOWED.containsKey(block)) {
                if (results.containsKey(block)) {
                    out.put(block, results.get(block));
                } else if (KEPT_VANILLA.containsKey(block)) {
                    out.put(block, "kept vanilla recipe: " + KEPT_VANILLA.get(block));
                }
            }
        });
        return out;
    }

    @Test
    void theScanFindsTheKnownRecipeResults() throws IOException {
        Map<String, String> results = allRecipeResults();
        assertThat(results).containsKeys(HEAVY, "minecraft:daylight_detector", "minecraft:lightning_rod",
            "minecraft:white_carpet", "minecraft:flower_pot");
        assertThat(results.get(HEAVY)).isEqualTo("minecraft/recipe/heavy_weighted_pressure_plate.json");
        assertThat(results).as("TFC removes these vanilla recipes (neoforge:false)")
            .doesNotContainKeys("minecraft:iron_trapdoor", "minecraft:campfire", "minecraft:composter", "minecraft:lantern");
    }

    @Test
    void noAbsentCarrierIsACraftedBlock() throws IOException {
        VanillaCandidates v = VanillaCandidates.load();
        assertThat(offences(v.policies(), allRecipeResults())).as("presence: absent but a recipe makes it (make it player,"
            + " natural with a hide, or allow-list it with a reason)").isEmpty();
    }

    @Test
    void theGuardFailsOnTheOldPolicy() throws IOException {
        VanillaCandidates v = VanillaCandidates.load();
        Map<String, BlockPolicy> old = new TreeMap<>(v.policies());
        for (String b : List.of(HEAVY, LIGHT)) {
            BlockPolicy p = old.get(b);
            old.put(b, new BlockPolicy(Presence.ABSENT, null, p.statesConsumed(), p.replaceableOnly(), p.note(), p.onlyClass()));
        }
        assertThat(offences(old, allRecipeResults())).containsOnlyKeys(HEAVY, LIGHT);
    }

    @Test
    void keptVanillaRecipesAreNotNamedByTfc() {
        TfcCheckout tfc = RealCheckout.open();
        for (String block : KEPT_VANILLA.keySet()) {
            String name = block.substring("minecraft:".length());
            assertThat(tfc.exists(TfcCheckout.GENERATED + "data/minecraft/recipe/" + name + ".json"))
                .as("TFC neither removes nor overrides vanilla's %s recipe", name).isFalse();
        }
    }

    @Test
    void thePlatesArePlayerOrHidden() {
        VanillaCandidates v = VanillaCandidates.load();
        assertThat(v.policy(HEAVY).presence()).isEqualTo(Presence.NATURAL);
        assertThat(v.policy(HEAVY).hide()).isEqualTo("nearest_free_value:power");
        assertThat(v.policy(LIGHT).presence()).isEqualTo(Presence.PLAYER);
        for (VersionFamily f : VersionFamily.values()) {
            Choice c = HitboxCarrier.select(TfcState.of("tfc:charcoal_pile[layers=1]"), Profile.defaultFor(f), f);
            assertThat(c.chosen().carrier()).isEqualTo(HEAVY + "[power=0]");
            assertThat(c.chosen().hideTarget()).as("real unpressed plates are sent pressed").isEqualTo(HEAVY + "[power=1]");
        }
    }

    /** The done criterion: no non-vanilla look of any allocation sits on a crafted block without a hide mapping. */
    @Test
    void noNonVanillaLookOnACraftedBlockWithoutAHide() throws IOException {
        Map<String, String> results = allRecipeResults();
        int checked = 0;
        for (Map.Entry<String, Result> e : HitboxAllocation.standard().entrySet()) {
            for (Assignment a : e.getValue().assignments()) {
                String block = a.carrier().block();
                if (a.pool() || a.mode() == Mode.VANILLA
                    || !(results.containsKey(block) || KEPT_VANILLA.containsKey(block))) {
                    continue;
                }
                checked++;
                assertThat(a.hideTarget()).as(e.getKey() + ": " + a.demand().id() + " (" + a.mode() + ") on " + a.state()
                    + ", a crafted block").isNotNull();
            }
        }
        assertThat(checked).as("the scraping demand on the heavy plate was checked").isPositive();
    }

    @Test
    void allowListedBlocksKeepTheirVanillaLook() throws IOException {
        Map<String, String> results = allRecipeResults();
        VanillaCandidates v = VanillaCandidates.load();
        for (String block : ALLOWED.keySet()) {
            assertThat(results).as(block + " is still a recipe result (else drop it from ALLOWED)").containsKey(block);
            assertThat(v.policy(block).presence()).as(block).isEqualTo(Presence.ABSENT);
        }
        for (Map.Entry<String, Result> e : HitboxAllocation.standard().entrySet()) {
            for (Assignment a : e.getValue().assignments()) {
                if (!a.pool() && ALLOWED.containsKey(a.carrier().block())) {
                    assertThat(a.mode()).as(e.getKey() + ": " + a.demand().id() + " on " + a.state()
                        + " (allow-listed crafted block: needs a hide before a non-vanilla look)").isEqualTo(Mode.VANILLA);
                }
            }
        }
    }
}
