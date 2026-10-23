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
 * templates is refused.
 */
class GeneratedCarrierTest {

    @Test
    void everyExplicitStateInTheGeneratedYamlComesFromTheSelector() {
        SortedMap<String, byte[]> files = Datagen.generate(RealCheckout.open().root(), TfcCheckout.PINNED_COMMIT).tree().files();
        List<String> problems = new ArrayList<>();
        int states = 0;
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
                            problems.add(path + " " + id + "#" + a.getKey() + ": auto_state outside the visual templates");
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
        // Today: the forge (8 heat levels), the bellows (4 facings), the firepit, grill and pot (lit, unlit); forge hide.
        // The exact sets are pinned so that a generator silently dropping out of craftengine/ fails here; a family item
        // that adds an explicit-state block adds its id to this list.
        assertThat(states).isGreaterThanOrEqualTo(18);
        assertThat(checkedIds).as("block ids whose explicit appearance states were checked")
            .containsExactly("tfp:bellows", "tfp:charcoal_forge", "tfp:firepit", "tfp:grill", "tfp:pot");
        assertThat(mappings).as("generator block_state_mappings")
            .containsExactly("minecraft:snow[layers=7] -> minecraft:snow[layers=8]");
        assertThat(problems).as("take carriers from dev.tfp.datagen.hitbox.HitboxAllocation").isEmpty();
    }

    @Test
    void theOutputGuardRefusesABareStatelessCarrier() {
        List<String> problems = new ArrayList<>();
        check("x.yml", "tfp:bellows", "north", "minecraft:mud", problems);
        check("x.yml", "tfp:unknown", "only", "minecraft:dirt_path", problems);
        check("x.yml", "tfp:bellows", "north", HitboxAllocation.agreed("tfp:bellows", "north").state(), problems);
        assertThat(problems).hasSize(2);
    }

    private static void check(String path, String id, String appearance, String state, List<String> problems) {
        Demand demand = HitboxDemands.all().stream()
            .filter(d -> d.blockId().equals(id) && matches(d.appearance(), appearance))
            .findFirst().orElse(null);
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
