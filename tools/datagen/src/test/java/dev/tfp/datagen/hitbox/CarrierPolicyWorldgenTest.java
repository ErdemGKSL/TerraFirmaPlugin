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
import dev.tfp.datagen.hitbox.HitboxCarrier.Choice;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-42: the carrier policy agrees with what our paper worldgen really places. A block the selector may use as is
 * ({@code presence: absent}) must not be a vanilla placeholder of {@code paper/.../worldgen}, else a carrier look drawn on it
 * would also be drawn on every placed placeholder (a transparent scraping carrier on the lily pad would make every floating
 * plant an invisible hitbox). The datagen module cannot import paper, so the worldgen sources are read as text.
 * PAR-INFRA-43: the buttons stay small-ground-piece carriers with just their placed states excluded, mud (one state) is
 * reserved, and the old allow-list is gone.
 */
class CarrierPolicyWorldgenTest {
    private static final List<String> WORLDGEN = List.of("PlantBlocks", "GroundcoverBlocks", "TreeBlocks", "VolcanoBlocks",
        "TfcTerrain");
    private static final Pattern MATERIAL = Pattern.compile("Material\\.([A-Z][A-Z0-9_]*)");

    /**
     * PAR-INFRA-43: absent blocks that worldgen places, with the exact states it places. {@code TfcTerrain.placeholderData}
     * puts a button on the floor and leaves the rest of the default state (facing=north, powered=false); a click on the
     * placeholder powers it. These states are in the policy's {@code excluded_states}, so no carrier look is drawn on them,
     * while the other floor facings stay the small-ground-piece carrier. Every other absent-and-placed block is an offence.
     */
    static final Map<String, List<String>> PLACED_STATES = Map.of(
        "minecraft:stone_button", List.of("minecraft:stone_button[face=floor,facing=north,powered=false]",
            "minecraft:stone_button[face=floor,facing=north,powered=true]"),
        "minecraft:oak_button", List.of("minecraft:oak_button[face=floor,facing=north,powered=false]",
            "minecraft:oak_button[face=floor,facing=north,powered=true]"));

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

    /** Absent-and-placed blocks whose placed states are not all excluded from the candidate set. */
    static Set<String> unguarded(VanillaCandidates v, Map<String, BlockPolicy> policies, Map<String, Set<String>> placed) {
        Set<String> out = new TreeSet<>();
        for (String block : absentButPlaced(policies, placed)) {
            List<String> states = PLACED_STATES.get(block);
            if (states == null || states.stream().anyMatch(st -> v.excludedState(st) == null)) {
                out.add(block);
            }
        }
        return out;
    }

    /** Whether {@code state} is a placed placeholder state of a block used as is. */
    private static boolean placedState(String state) {
        return PLACED_STATES.values().stream().anyMatch(l -> l.contains(state));
    }

    @Test
    void noPresenceAbsentCarrierIsAWorldgenPlaceholder() throws IOException {
        Map<String, Set<String>> placed = worldgenBlocks(repoRoot());
        assertThat(placed).as("the scan finds the known placeholders")
            .containsKeys("minecraft:lily_pad", "minecraft:sea_pickle", "minecraft:pink_petals", "minecraft:mud",
                "minecraft:stone_button", "minecraft:oak_button");
        VanillaCandidates v = VanillaCandidates.load();
        assertThat(unguarded(v, v.policies(), placed)).as("presence: absent but placed by paper worldgen (make them natural"
            + " with a hide, reserved when no hide target exists, or exclude every placed state)").isEmpty();
        Set<String> stale = new TreeSet<>(PLACED_STATES.keySet());
        stale.removeAll(absentButPlaced(v.policies(), placed));
        assertThat(stale).as("placed-state entries that are no longer absent-and-placed").isEmpty();
        for (List<String> states : PLACED_STATES.values()) {
            for (String st : states) {
                assertThat(v.get("1.21.11", st)).as(st + " is a dumped state").isNotNull();
                assertThat(v.excludedState(st)).as(st + " is excluded").contains("worldgen placeholder");
            }
        }
    }

    @Test
    void thePlacedButtonStatesAreTheFloorDefault() throws IOException {
        String terrain = Files.readString(repoRoot().resolve("paper/src/main/java/dev/tfp/paper/worldgen/TfcTerrain.java"));
        int at = terrain.indexOf("private BlockData placeholderData(Material material, boolean waterlogged)");
        assertThat(at).as("TfcTerrain.placeholderData").isPositive();
        String body = terrain.substring(at, terrain.indexOf("\n    }\n", at));
        assertThat(body).as("buttons are placed on the floor").contains("setAttachedFace(FaceAttachable.AttachedFace.FLOOR)");
        assertThat(body).as("the facing stays the default (north); a change here must update PLACED_STATES")
            .doesNotContain("setFacing").doesNotContain("Directional").doesNotContain("setPowered");
    }

    @Test
    void theGuardFailsWithoutTheFixes() throws IOException {
        Map<String, Set<String>> placed = worldgenBlocks(repoRoot());
        VanillaCandidates v = VanillaCandidates.load();
        Map<String, BlockPolicy> old = new TreeMap<>(v.policies());
        for (String b : List.of("minecraft:lily_pad", "minecraft:sea_pickle", "minecraft:pink_petals", "minecraft:mud")) {
            BlockPolicy p = old.get(b);
            old.put(b, new BlockPolicy(Presence.ABSENT, null, p.statesConsumed(), p.replaceableOnly(), p.note(), p.onlyClass()));
        }
        assertThat(unguarded(v, old, placed)).containsExactly("minecraft:lily_pad", "minecraft:mud", "minecraft:pink_petals",
            "minecraft:sea_pickle");
        Map<String, BlockPolicy> path = new TreeMap<>(v.policies());
        path.put("minecraft:dirt_path", new BlockPolicy(Presence.ABSENT, null, 1, null, "", null));
        Map<String, Set<String>> morePlaced = new TreeMap<>(placed);
        morePlaced.put("minecraft:dirt_path", Set.of("TfcTerrain"));
        assertThat(unguarded(v, path, morePlaced)).as("a placed absent block without placed-state entries")
            .containsExactly("minecraft:dirt_path");
    }

    @Test
    void theFixedBlocksAreHiddenOrNeverUsed() {
        VanillaCandidates v = VanillaCandidates.load();
        assertThat(v.policy("minecraft:lily_pad").presence()).as("one state: no hide target").isEqualTo(Presence.RESERVED);
        assertThat(v.policy("minecraft:sea_pickle").presence()).isEqualTo(Presence.NATURAL);
        assertThat(v.policy("minecraft:sea_pickle").hide()).isEqualTo("nearest_free_value:pickles");
        assertThat(v.policy("minecraft:pink_petals").presence()).isEqualTo(Presence.NATURAL);
        assertThat(v.policy("minecraft:pink_petals").hide()).isEqualTo("nearest_free_value:flower_amount");
        assertThat(v.policy("minecraft:mud").presence()).as("one state: no hide target (PAR-INFRA-43)").isEqualTo(Presence.RESERVED);
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
                String what = a.demand().blockId() + "#" + a.demand().appearance() + " on " + a.state();
                assertThat(placedState(a.state())).as(what + " draws over a worldgen placeholder state").isFalse();
                if (!PLACED_STATES.containsKey(block)) {
                    assertThat(a.hideTarget()).as(what + ": placed by worldgen, real instances need a hide mapping").isNotNull();
                }
            }
        }
        assertThat(checked).as("non-vanilla looks on blocks found in the world (the snow carriers) were checked").isPositive();
    }

    @Test
    void noPinnedSelectorCarrierIsAWorldgenPlaceholder() throws IOException {
        Map<String, Set<String>> placed = worldgenBlocks(repoRoot());
        VanillaCandidates v = VanillaCandidates.load();
        int buttons = 0;
        for (TfcShapes.Row row : TfcShapes.load().values()) {
            for (VersionFamily f : VersionFamily.values()) {
                Choice c = HitboxCarrier.select(new HitboxCarrier.TfcState(row, 0, false), Profile.defaultFor(f), f);
                if (c.chosen() == null || v.isPool(c.chosen().carrier())) {
                    continue;
                }
                String block = c.chosen().block();
                String what = row.key() + " on " + f.id() + ": " + c.chosen().carrier();
                assertThat(placedState(c.chosen().carrier())).as(what + " is a worldgen placeholder state").isFalse();
                assertThat(v.policy(block).presence()).as(what).isIn(Presence.ABSENT, Presence.NATURAL);
                if (placed.containsKey(block) && !PLACED_STATES.containsKey(block)) {
                    assertThat(c.chosen().hideTarget()).as(what + " is placed by worldgen without a hide mapping").isNotNull();
                }
                if (PLACED_STATES.containsKey(block)) {
                    buttons++;
                }
            }
        }
        assertThat(buttons).as("the small ground pieces still take a button (another floor facing)").isPositive();
    }

    @Test
    void theScrapingDemandNoLongerTakesTheLilyPad() {
        Assignment scraping = HitboxAllocation.agreed(HitboxDemands.SCRAPING_ID, HitboxDemands.SCRAPING_APPEARANCE);
        assertThat(scraping.carrier().block()).isNotEqualTo("minecraft:lily_pad");
        assertThat(scraping.state()).isEqualTo("minecraft:heavy_weighted_pressure_plate[power=0]");
        // PAR-INFRA-44: players craft the heavy plate (TFC's wrought-iron recipe); real unpressed plates are sent pressed.
        assertThat(scraping.hideTarget()).isEqualTo("minecraft:heavy_weighted_pressure_plate[power=1]");
    }
}
