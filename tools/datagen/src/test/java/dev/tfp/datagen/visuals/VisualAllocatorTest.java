/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.CeBlockStates.ModelRef;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.visuals.VisualAllocator.Allocation;
import dev.tfp.datagen.visuals.VisualAllocator.Unit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class VisualAllocatorTest {
    /** Small supply: one identity duplicate, one Nether state, two absent states, the mapping targets. */
    private static final String CSV = """
        # test supply
        family,state,source,behaviour,twin
        1.21,minecraft:infested_stone_bricks,identity_dup_cross,clean,minecraft:stone_bricks
        1.21,minecraft:netherrack,nether_end,clean,
        1.21,minecraft:stone_bricks,absent,clean,
        1.21,minecraft:terracotta,absent,caveat_minor,
        1.21,minecraft:andesite,absent,clean,
        1.21,minecraft:glowstone,nether_end,avoid,
        1.21,minecraft:sand,absent,caveat_major,
        1.21,minecraft:red_sand,absent,caveat_major,
        1.21,minecraft:bedrock,tfc_present,clean,
        1.21,minecraft:stone,absent,clean,
        1.21,minecraft:dirt,absent,clean,
        1.21,minecraft:oak_planks,absent,clean,
        """;
    private static final Function<String, String> CONTENT = Function.identity();

    private static List<ModelRef> model(String path) {
        return List.of(new ModelRef(path, 0, 0, false, 1));
    }

    private static List<VisualDemand> cubes(String... ids) {
        List<VisualDemand> d = new ArrayList<>();
        for (String id : ids) {
            d.add(new VisualDemand(id, "", model("tfp:block/" + id)));
        }
        return d;
    }

    private static Map<String, String> states(Allocation a) {
        Map<String, String> m = new HashMap<>();
        a.unitByDemand().forEach((k, u) -> m.put(k, u.state() == null ? "solid:" + u.solidId() : u.state().state()));
        return m;
    }

    @Test
    void assignsIdentityThenNetherEndThenAbsentCleanThenMinorThenSolid() {
        VanillaSupply supply = VanillaSupply.parse(CSV);
        List<VisualDemand> demand = cubes("a", "b", "c", "d", "e");
        Map<String, String> off = states(VisualAllocator.allocate(demand, CONTENT, Profile.LEVEL_0,
            VersionFamily.V1_21, false, supply));
        assertThat(off).containsEntry("a", "minecraft:infested_stone_bricks")
            .containsEntry("b", "minecraft:andesite")
            .containsEntry("c", "minecraft:stone_bricks")
            .containsEntry("d", "minecraft:terracotta")
            .containsEntry("e", "solid:tfp_e");
        Map<String, String> on = states(VisualAllocator.allocate(demand, CONTENT, Profile.LEVEL_0,
            VersionFamily.V1_21, true, supply));
        assertThat(on).containsEntry("a", "minecraft:infested_stone_bricks")
            .containsEntry("b", "minecraft:netherrack")
            .containsEntry("c", "minecraft:andesite");
        // avoid, major-caveat, TFC-present and reserved states are never claimed
        assertThat(on.values()).doesNotContain("minecraft:glowstone", "minecraft:sand", "minecraft:bedrock",
            "minecraft:stone", "minecraft:dirt", "minecraft:oak_planks");
    }

    @Test
    void mappingsHideEveryClaimedStateWithAnUnclaimedLookAlike() {
        VanillaSupply supply = VanillaSupply.parse(CSV);
        Allocation one = VisualAllocator.allocate(cubes("a"), CONTENT, Profile.LEVEL_0, VersionFamily.V1_21, false, supply);
        // twin free: an identity duplicate maps to its twin
        assertThat(one.mappings()).containsExactly(Map.entry("minecraft:infested_stone_bricks", "minecraft:stone_bricks"));
        Allocation four = VisualAllocator.allocate(cubes("a", "b", "c", "d"), CONTENT, Profile.LEVEL_0,
            VersionFamily.V1_21, false, supply);
        // twin claimed too: both go to the family look-alike (stone); terracotta to red sand
        assertThat(four.mappings()).containsEntry("minecraft:infested_stone_bricks", "minecraft:stone")
            .containsEntry("minecraft:stone_bricks", "minecraft:stone")
            .containsEntry("minecraft:andesite", "minecraft:stone")
            .containsEntry("minecraft:terracotta", "minecraft:red_sand")
            .hasSize(4);
        Set<String> claimed = new java.util.HashSet<>(four.mappings().keySet());
        assertThat(four.mappings().values()).noneMatch(claimed::contains);
    }

    @Test
    void identicalModelsShareOneVisual() {
        List<VisualDemand> demand = new ArrayList<>(cubes("a", "b"));
        demand.add(new VisualDemand("hardened_a", "", model("tfp:block/a")));
        // different file, same JSON content
        demand.add(new VisualDemand("copy_b", "", model("tfp:block/b_copy")));
        Function<String, String> content = p -> p.equals("tfp:block/b_copy") ? "tfp:block/b" : p;
        Allocation a = VisualAllocator.allocate(demand, content, Profile.LEVEL_0, VersionFamily.V1_21, false,
            VanillaSupply.parse(CSV));
        assertThat(a.units()).hasSize(2);
        assertThat(a.unitByDemand().get("hardened_a")).isSameAs(a.unitByDemand().get("a"));
        assertThat(a.unitByDemand().get("copy_b")).isSameAs(a.unitByDemand().get("b"));
        assertThat(a.unitByDemand().get("copy_b").models()).isEqualTo(model("tfp:block/b"));
    }

    @Test
    void profilesShareOreGradesAndGemsCumulatively() {
        List<VisualDemand> demand = new ArrayList<>();
        for (String axis : List.of("axis_x", "axis_y", "axis_z")) {
            int x = axis.equals("axis_y") ? 0 : 90;
            int y = axis.equals("axis_x") ? 90 : 0;
            demand.add(new VisualDemand("rock/raw/granite", axis, List.of(new ModelRef("tfp:block/rock/raw/granite", x,
                y, false, 1))));
        }
        demand.addAll(cubes("ore/poor_hematite/granite", "ore/normal_hematite/granite", "ore/rich_hematite/granite",
            "ore/ruby/granite", "ore/borax/granite"));
        VanillaSupply supply = VanillaSupply.parse(CSV);
        Map<Profile, Integer> units = new HashMap<>();
        for (Profile p : Profile.values()) {
            Allocation a = VisualAllocator.allocate(demand, CONTENT, p, VersionFamily.V1_21, false, supply);
            units.put(p, a.units().size());
            if (p.ordinal() >= Profile.LEVEL_2.ordinal()) {
                assertThat(a.unitByDemand().get("ore/ruby/granite")).isSameAs(a.unitByDemand().get("rock/raw/granite#axis_y"));
            }
        }
        assertThat(units).containsEntry(Profile.LEVEL_0, 8).containsEntry(Profile.LEVEL_1, 8)
            .containsEntry(Profile.LEVEL_2, 7).containsEntry(Profile.LEVEL_3A, 6).containsEntry(Profile.LEVEL_3B, 5);
        Allocation a3a = VisualAllocator.allocate(demand, CONTENT, Profile.LEVEL_3A, VersionFamily.V1_21, false, supply);
        Unit poor = a3a.unitByDemand().get("ore/poor_hematite/granite");
        assertThat(poor).isSameAs(a3a.unitByDemand().get("ore/normal_hematite/granite"));
        assertThat(poor.models()).isEqualTo(model("tfp:block/ore/normal_hematite/granite"));
        assertThat(a3a.unitByDemand().get("ore/rich_hematite/granite")).isNotSameAs(poor);
    }

    @Test
    void woodAxesShareFromLevel1() {
        List<VisualDemand> demand = new ArrayList<>();
        for (String axis : List.of("axis_x", "axis_y", "axis_z")) {
            demand.add(new VisualDemand("wood/wood/oak", axis, List.of(new ModelRef("tfp:block/wood/wood/oak", 0,
                axis.equals("axis_y") ? 0 : 90, false, 1))));
        }
        VanillaSupply supply = VanillaSupply.parse(CSV);
        assertThat(VisualAllocator.allocate(demand, CONTENT, Profile.LEVEL_0, VersionFamily.V1_21, false, supply).units()).hasSize(2);
        assertThat(VisualAllocator.allocate(demand, CONTENT, Profile.LEVEL_1, VersionFamily.V1_21, false, supply).units()).hasSize(1);
    }

    @Test
    void overflowFailsTheBuild() {
        VanillaSupply supply = VanillaSupply.parse(CSV);
        int explicit = supply.candidates(VersionFamily.V1_21, false, HideTargets.EXCLUDED).size();
        String[] ids = new String[VersionFamily.V1_21.solidPool() + explicit];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = "b" + i;
        }
        Allocation full = VisualAllocator.allocate(cubes(ids), CONTENT, Profile.LEVEL_0, VersionFamily.V1_21, false, supply);
        assertThat(full.solidFree()).isZero();
        String[] more = java.util.Arrays.copyOf(ids, ids.length + 1);
        more[ids.length] = "one_too_many";
        assertThatThrownBy(() -> VisualAllocator.allocate(cubes(more), CONTENT, Profile.LEVEL_0, VersionFamily.V1_21,
            false, supply)).isInstanceOf(DatagenException.class).hasMessageContaining("overflows");
    }

    @Test
    void defaultsAreLevel3aOn121AndLevel2On26() {
        assertThat(Profile.defaultFor(VersionFamily.V1_21)).isEqualTo(Profile.LEVEL_3A);
        assertThat(Profile.defaultFor(VersionFamily.V26)).isEqualTo(Profile.LEVEL_2);
        assertThat(Profile.SOLID_RESERVE_WARNING).isEqualTo(500);
        assertThat(VersionFamily.V1_21.solidPool()).isEqualTo(1289);
        assertThat(VersionFamily.V26.solidPool()).isEqualTo(1489);
    }

    @Test
    void realSupplyIsOrderedAndExcludesReservedAndNetherEndWhenOff() {
        VanillaSupply supply = VanillaSupply.load();
        for (VersionFamily f : VersionFamily.values()) {
            List<VanillaSupply.State> on = supply.candidates(f, true, HideTargets.RESERVED);
            List<VanillaSupply.State> off = supply.candidates(f, false, HideTargets.RESERVED);
            assertThat(on).isSortedAccordingTo(VanillaSupply.ORDER).allMatch(VanillaSupply.State::terrainGrade);
            assertThat(on.getFirst().source().identity()).isTrue();
            assertThat(off).noneMatch(s -> s.source() == VanillaSupply.Source.NETHER_END);
            assertThat(on.size() - off.size()).isEqualTo(67);
            assertThat(on).noneMatch(s -> HideTargets.RESERVED.contains(s.state()));
            for (String r : HideTargets.RESERVED) {
                assertThat(supply.get(f, r)).as(r).isNotNull();
            }
        }
    }

    /** TFC worldgen places vanilla grass_block, so it keeps its tinted look: never allocated, never mapped to dirt. */
    @Test
    void grassBlockIsKeptVanilla() {
        VanillaSupply supply = VanillaSupply.load();
        for (VersionFamily f : VersionFamily.values()) {
            for (boolean netherEnd : List.of(true, false)) {
                assertThat(supply.candidates(f, netherEnd, HideTargets.EXCLUDED))
                    .noneMatch(s -> s.state().startsWith("minecraft:grass_block["));
            }
        }
    }

    /** The per-state resource agrees with the per-block allowlist in docs/research (same classification). */
    @Test
    void realSupplyMatchesTheResearchAllowlistPerBlock() throws IOException {
        Path csv = Path.of(System.getProperty("tfp.repoRoot", "../.."), "docs/research/vanilla-fullcube-supply.csv");
        List<String> lines = Files.readAllLines(csv);
        List<String> header = List.of(lines.getFirst().split(","));
        VanillaSupply supply = VanillaSupply.load();
        for (VersionFamily f : VersionFamily.values()) {
            Map<String, Integer> perBlock = new HashMap<>();
            supply.all(f).values().stream().filter(VanillaSupply.State::terrainGrade)
                .forEach(s -> perBlock.merge(s.block(), 1, Integer::sum));
            int total = 0;
            for (String line : lines.subList(1, lines.size())) {
                String[] cols = line.split(",", -1);
                // block names and counts precede the first quoted field, so a plain split is safe for them
                int min = Integer.MAX_VALUE;
                for (String v : f.researchedVersions()) {
                    min = Math.min(min, Integer.parseInt(cols[header.indexOf("terrain_grade_" + v)]));
                }
                assertThat(perBlock.getOrDefault("minecraft:" + cols[0], 0)).as(f.id() + " " + cols[0]).isEqualTo(min);
                total += min;
            }
            assertThat(perBlock.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(total);
            assertThat(total).isEqualTo(f == VersionFamily.V1_21 ? 664 : 668);
        }
    }

    @Test
    void datapackTagEditsRemoveOnlyUsedBlocks() {
        Set<String> used = Set.of("minecraft:orange_terracotta", "minecraft:amethyst_block");
        assertThat(VisualOutputs.editedTag(VisualOutputs.VANILLA_TAGS.get("triggers_ambient_desert_dry_vegetation_block_sounds"), used))
            .hasSize(18).contains("minecraft:terracotta", "minecraft:sand").doesNotContain("minecraft:orange_terracotta", "#minecraft:terracotta");
        assertThat(VisualOutputs.editedTag(VisualOutputs.VANILLA_TAGS.get("crystal_sound_blocks"), used))
            .containsExactly("minecraft:budding_amethyst");
        assertThat(VisualOutputs.editedTag(VisualOutputs.VANILLA_TAGS.get("triggers_ambient_desert_sand_block_sounds"), used))
            .isEqualTo(VisualOutputs.VANILLA_TAGS.get("triggers_ambient_desert_sand_block_sounds"));
    }

    @Test
    void creationPathsCoverVanillaMechanics() {
        assertThat(VisualOutputs.creationPaths("minecraft:cobblestone")).containsExactly("item", "lava_water");
        assertThat(VisualOutputs.creationPaths("minecraft:white_concrete")).contains("concrete_powder_water");
        assertThat(VisualOutputs.creationPaths("minecraft:exposed_copper")).contains("copper_weathering_scraping_waxing");
        assertThat(VisualOutputs.creationPaths("minecraft:copper_ore")).containsExactly("item");
    }
}
