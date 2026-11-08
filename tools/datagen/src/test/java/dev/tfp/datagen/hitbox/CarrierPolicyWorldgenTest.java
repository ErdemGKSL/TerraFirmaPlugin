/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.hitbox.HitboxAllocation.Assignment;
import dev.tfp.datagen.hitbox.HitboxAllocation.Mode;
import dev.tfp.datagen.hitbox.HitboxAllocation.Result;
import dev.tfp.datagen.hitbox.VanillaCandidates.BlockPolicy;
import dev.tfp.datagen.hitbox.VanillaCandidates.Presence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-42: the carrier policy agrees with what our paper worldgen really places. A block the selector may use as is
 * ({@code presence: absent}) must not be a vanilla placeholder of {@code paper/.../worldgen}, else a carrier look drawn on it
 * would also be drawn on every placed placeholder (a transparent scraping carrier on the lily pad would make every floating
 * plant an invisible hitbox). The datagen module cannot import paper, so the worldgen sources are read as text.
 */
class CarrierPolicyWorldgenTest {
    private static final List<String> WORLDGEN = List.of("PlantBlocks", "GroundcoverBlocks", "TreeBlocks", "VolcanoBlocks",
        "TfcTerrain");
    private static final Pattern MATERIAL = Pattern.compile("Material\\.([A-Z][A-Z0-9_]*)");

    /**
     * Absent blocks that worldgen places all the same, with the reason. None of them may carry a non-vanilla look in any
     * allocation ({@link #noAllocationDrawsOverAWorldgenPlaceholder}); PAR-INFRA-43 removes them.
     */
    private static final Map<String, String> ALLOWED = Map.of(
        "minecraft:stone_button", "loose-rock placeholder; the button is the design carrier of small ground pieces, which"
            + " replace the placeholders (PAR-INFRA-43); a button has no integer property for a hide mapping",
        "minecraft:oak_button", "stick placeholder; same as the stone button (HitboxCarrierTest pins oak_button for loose"
            + " rocks, small ore and small groundcover)",
        "minecraft:mud", "mud surface; one state, so no hide target; the research CSV (VanillaCandidatesTest) and"
            + " HitboxCarrierTest (charcoal_pile[layers=8]) still treat it as absent until PAR-INFRA-43");

    private static Path repoRoot() {
        return Path.of(System.getProperty("tfp.repoRoot", "../.."));
    }

    /** The {@code minecraft:} ids of every {@code Material.X} named in the worldgen sources, with the files naming them. */
    static Map<String, Set<String>> worldgenBlocks(Path root) throws IOException {
        Map<String, Set<String>> out = new TreeMap<>();
        for (String name : WORLDGEN) {
            Path file = root.resolve("paper/src/main/java/dev/tfp/paper/worldgen/" + name + ".java");
            assertThat(file).as("worldgen source " + name).isRegularFile();
            Matcher m = MATERIAL.matcher(Files.readString(file));
            while (m.find()) {
                out.computeIfAbsent("minecraft:" + m.group(1).toLowerCase(Locale.ROOT), k -> new TreeSet<>()).add(name);
            }
        }
        return out;
    }

    /** Policy blocks the selector may use without a hide mapping that our worldgen places. */
    static Set<String> absentButPlaced(Map<String, BlockPolicy> policies, Map<String, Set<String>> placed) {
        Set<String> out = new TreeSet<>();
        policies.forEach((block, p) -> {
            if (p.presence() == Presence.ABSENT && placed.containsKey(block)) {
                out.add(block);
            }
        });
        return out;
    }

    @Test
    void noPresenceAbsentCarrierIsAWorldgenPlaceholder() throws IOException {
        Map<String, Set<String>> placed = worldgenBlocks(repoRoot());
        assertThat(placed).as("the scan finds the known placeholders")
            .containsKeys("minecraft:lily_pad", "minecraft:sea_pickle", "minecraft:pink_petals", "minecraft:mud",
                "minecraft:stone_button", "minecraft:oak_button");
        Set<String> offending = absentButPlaced(VanillaCandidates.load().policies(), placed);
        offending.removeAll(ALLOWED.keySet());
        assertThat(offending).as("presence: absent but placed by paper worldgen (make them natural with a hide, or"
            + " reserved when no hide target exists)").isEmpty();
        Set<String> stale = new TreeSet<>(ALLOWED.keySet());
        stale.removeAll(absentButPlaced(VanillaCandidates.load().policies(), placed));
        assertThat(stale).as("allow-list entries that are no longer absent-and-placed").isEmpty();
    }

    @Test
    void theGuardFailsOnThePolicyBeforeTheFix() throws IOException {
        Map<String, Set<String>> placed = worldgenBlocks(repoRoot());
        Map<String, BlockPolicy> old = new TreeMap<>(VanillaCandidates.load().policies());
        for (String b : List.of("minecraft:lily_pad", "minecraft:sea_pickle", "minecraft:pink_petals")) {
            BlockPolicy p = old.get(b);
            old.put(b, new BlockPolicy(Presence.ABSENT, null, p.statesConsumed(), p.replaceableOnly(), p.note(), p.onlyClass()));
        }
        Set<String> offending = absentButPlaced(old, placed);
        offending.removeAll(ALLOWED.keySet());
        assertThat(offending).containsExactly("minecraft:lily_pad", "minecraft:pink_petals", "minecraft:sea_pickle");
    }

    @Test
    void theFixedBlocksAreHiddenOrNeverUsed() {
        VanillaCandidates v = VanillaCandidates.load();
        assertThat(v.policy("minecraft:lily_pad").presence()).as("one state: no hide target").isEqualTo(Presence.RESERVED);
        assertThat(v.policy("minecraft:sea_pickle").presence()).isEqualTo(Presence.NATURAL);
        assertThat(v.policy("minecraft:sea_pickle").hide()).isEqualTo("nearest_free_value:pickles");
        assertThat(v.policy("minecraft:pink_petals").presence()).isEqualTo(Presence.NATURAL);
        assertThat(v.policy("minecraft:pink_petals").hide()).isEqualTo("nearest_free_value:flower_amount");
    }

    @Test
    void noAllocationDrawsOverAWorldgenPlaceholder() throws IOException {
        Map<String, Set<String>> placed = worldgenBlocks(repoRoot());
        VanillaCandidates v = VanillaCandidates.load();
        int checked = 0;
        for (Result r : HitboxAllocation.standard().values()) {
            for (Assignment a : r.assignments()) {
                String block = a.carrier().block();
                boolean inWorld = placed.containsKey(block) || v.policy(block).presence() == Presence.NATURAL;
                if (a.pool() || !inWorld || a.mode() == Mode.VANILLA) {
                    continue;
                }
                checked++;
                assertThat(ALLOWED).as(a.demand().blockId() + "#" + a.demand().appearance() + " draws over the worldgen"
                    + " placeholder " + a.state()).doesNotContainKey(a.carrier().block());
                assertThat(a.hideTarget()).as(a.state() + " is placed by worldgen: real instances need a hide mapping")
                    .isNotNull();
            }
        }
        assertThat(checked).as("non-vanilla looks on blocks found in the world (the snow carriers) were checked").isPositive();
    }

    @Test
    void theScrapingDemandNoLongerTakesTheLilyPad() {
        Assignment scraping = HitboxAllocation.agreed(HitboxDemands.SCRAPING_ID, HitboxDemands.SCRAPING_APPEARANCE);
        assertThat(scraping.carrier().block()).isNotEqualTo("minecraft:lily_pad");
        assertThat(scraping.state()).isEqualTo("minecraft:heavy_weighted_pressure_plate[power=0]");
    }
}
