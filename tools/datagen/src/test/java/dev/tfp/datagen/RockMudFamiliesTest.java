/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.datagen.RockMudFixture.RockSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RockMudFamiliesTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    /** The 7 igneous rocks: the only rocks with a rock anvil and a magma block. */
    private static final Set<String> IGNEOUS = Set.of("granite", "diorite", "gabbro", "rhyolite", "basalt",
        "andesite", "dacite");
    /** The 7 Rock.BlockType kinds that TFC gives slab, stair and wall variants. */
    private static final Set<String> VARIANT_KINDS = Set.of("raw", "smooth", "cobble", "bricks", "cracked_bricks",
        "mossy_bricks", "mossy_cobble");
    private static final Set<String> SOILS = Set.of("entisol", "aridisol", "oxisol", "fluvisol", "andisol", "podzol",
        "alfisol", "mollisol");
    private static final Set<String> SHAPES_ALL = Set.of("full", "slab", "stairs", "wall");

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Dtfc.checkout, from -Ptfc.checkout or $TFC_CHECKOUT); skipped when absent. */
    @Test
    void realCheckoutHasTwentyOneRocksAndEightMudBrickSoils() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        TfcCheckout tfc = TfcCheckout.open(checkout, PIN);

        List<RockFamilies.Family> rocks = RockFamilies.families(tfc);
        assertThat(rocks).hasSize(21);
        assertThat(rocks.stream().filter(f -> f.igneous()).map(RockFamilies.Family::rock).collect(java.util.stream.Collectors.toSet()))
            .isEqualTo(IGNEOUS);
        for (RockFamilies.Family f : rocks) {
            Set<String> kinds = new TreeSet<>();
            f.blocks().forEach(b -> kinds.add(b.kind()));
            Set<String> expected = new TreeSet<>(RockMudFixture.TFC_BLOCK_TYPES.stream()
                .map(e -> e.getKey().toLowerCase(Locale.ROOT)).toList());
            if (f.igneous()) {
                expected.add("anvil");
                expected.add("magma");
            }
            assertThat(kinds).as(f.rock()).isEqualTo(expected);
            assertThat(f.blocks()).as(f.rock()).hasSize(f.igneous() ? 40 : 38);
            for (String kind : kinds) {
                Set<String> shapes = new TreeSet<>();
                f.blocks().stream().filter(b -> b.kind().equals(kind)).forEach(b -> shapes.add(b.shape()));
                assertThat(shapes).as(f.rock() + " " + kind)
                    .isEqualTo(VARIANT_KINDS.contains(kind) ? SHAPES_ALL : Set.of("full"));
            }
        }
        assertThat(rocks.stream().mapToInt(f -> f.blocks().size()).sum()).isEqualTo(14 * 38 + 7 * 40);

        List<MudFamilies.Family> soils = MudFamilies.families(tfc);
        assertThat(soils).hasSize(8);
        assertThat(soils.stream().map(MudFamilies.Family::soil).collect(java.util.stream.Collectors.toSet())).isEqualTo(SOILS);
        for (MudFamilies.Family f : soils) {
            assertThat(f.blocks().stream().map(MudFamilies.Block::shape).toList())
                .as(f.soil()).containsExactly("full", "slab", "stairs", "wall");
        }
        assertThat(soils.stream().mapToInt(f -> f.blocks().size()).sum()).isEqualTo(32);

        // The folders hold exactly the blocks of the families (no extra or missing files).
        Set<String> rockKinds = new TreeSet<>();
        rocks.forEach(f -> f.blocks().forEach(b -> rockKinds.add(b.kind())));
        for (String kind : rockKinds) {
            long blocks = rocks.stream().flatMap(f -> f.blocks().stream()).filter(b -> b.kind().equals(kind)).count();
            assertThat(tfc.list(RockFamilies.BLOCKSTATES + kind)).as(kind).hasSize((int) blocks);
        }
        assertThat(tfc.list(MudFamilies.BLOCKSTATES)).hasSize(32);
    }

    @Test
    void fixtureFamiliesHaveTheRightKindsShapesAndYaml() throws IOException {
        Path root = checkout();
        RockMudFixture.writeAll(root, List.of(new RockSpec("GRANITE", "FELSIC_IGNEOUS_INTRUSIVE"),
            new RockSpec("CLAYSTONE", "SEDIMENTARY")), RockMudFixture.TFC_BLOCK_TYPES, List.of("ALFISOL", "PODZOL"), true);
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        OutputTree out = new OutputTree();

        assertThat(RockFamilies.run(tfc, out)).isEqualTo(40 + 38);
        assertThat(MudFamilies.run(tfc, out)).isEqualTo(2 * 4);

        String rock = new String(out.get(RockFamilies.OUTPUT), StandardCharsets.UTF_8);
        Map<?, ?> rocks = (Map<?, ?>) ((Map<?, ?>) CeYamlCheck.parse(rock, RockFamilies.OUTPUT)).get("families");
        assertThat(rocks.keySet().stream().map(String::valueOf).toList()).containsExactly("granite", "claystone");
        List<?> granite = (List<?>) ((Map<?, ?>) rocks.get("granite")).get("blocks");
        assertThat(granite).hasSize(40);
        assertThat(kindsOf(granite)).contains("anvil", "magma", "bricks");
        assertThat(rock).startsWith("# Generated by tools/datagen");
        assertThat(rock).contains("      - kind: bricks\n        shape: stairs\n        id: tfp:rock/bricks/granite_stairs\n"
            + "        tfc: tfc:rock/bricks/granite_stairs\n");
        assertThat(rock).contains("      - kind: anvil\n        shape: full\n        id: tfp:rock/anvil/granite\n");
        assertThat(rock).doesNotContain("rock/anvil/claystone");
        List<?> claystone = (List<?>) ((Map<?, ?>) rocks.get("claystone")).get("blocks");
        assertThat(claystone).hasSize(38);
        assertThat(kindsOf(claystone)).doesNotContain("anvil", "magma");

        String mud = new String(out.get(MudFamilies.OUTPUT), StandardCharsets.UTF_8);
        Map<?, ?> soils = (Map<?, ?>) ((Map<?, ?>) CeYamlCheck.parse(mud, MudFamilies.OUTPUT)).get("families");
        assertThat(soils.keySet().stream().map(String::valueOf).toList()).containsExactly("alfisol", "podzol");
        List<?> alfisol = (List<?>) ((Map<?, ?>) soils.get("alfisol")).get("blocks");
        assertThat(alfisol).hasSize(4);
        assertThat(mud).contains("      - kind: mud_bricks\n        shape: wall\n        id: tfp:mud_bricks/alfisol_wall\n"
            + "        tfc: tfc:mud_bricks/alfisol_wall\n");
        assertThat(mud).startsWith("# Generated by tools/datagen");
    }

    @Test
    void missingRockSourceIsRefused() throws IOException {
        Path root = checkout();
        assertThatThrownBy(() -> RockFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining(RockFamilies.SOURCE);
    }

    @Test
    void missingMudSourceAndMudBricksConstantAreRefused() throws IOException {
        Path root = checkout();
        RockMudFixture.writeMudSource(root, List.of("ALFISOL"), false);
        assertThatThrownBy(() -> MudFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("MUD_BRICKS");
        Files.delete(root.resolve(MudFamilies.SOURCE));
        assertThatThrownBy(() -> MudFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining(MudFamilies.SOURCE);
    }

    @Test
    void missingRockBlockstateIsRefused() throws IOException {
        Path root = fullFixture();
        Files.delete(root.resolve(TfcCheckout.ASSETS + "blockstates/rock/bricks/granite_stairs.json"));
        assertThatThrownBy(() -> RockFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("rock/bricks/granite_stairs");
    }

    @Test
    void missingMudBlockstateIsRefused() throws IOException {
        Path root = fullFixture();
        Files.delete(root.resolve(TfcCheckout.ASSETS + "blockstates/mud_bricks/podzol_wall.json"));
        assertThatThrownBy(() -> MudFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("mud_bricks/podzol_wall");
    }

    @Test
    void unknownRockBlockstateFolderIsRefused() throws IOException {
        Path root = fullFixture();
        Files.createDirectories(root.resolve(TfcCheckout.ASSETS + "blockstates/rock/mystery"));
        Files.writeString(root.resolve(TfcCheckout.ASSETS + "blockstates/rock/mystery/granite.json"), "{}");
        assertThatThrownBy(() -> RockFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("mystery");
    }

    @Test
    void blockKindMissingFromTheEnumIsRefused() throws IOException {
        Path root = checkout();
        List<Map.Entry<String, Boolean>> types = new ArrayList<>(RockMudFixture.TFC_BLOCK_TYPES);
        types.removeIf(e -> e.getKey().equals("ROPE_ANCHOR"));
        RockMudFixture.writeAll(root, List.of(new RockSpec("GRANITE", "FELSIC_IGNEOUS_INTRUSIVE")), types,
            List.of("ALFISOL"), true);
        Files.createDirectories(root.resolve(TfcCheckout.ASSETS + "blockstates/rock/rope_anchor"));
        Files.writeString(root.resolve(TfcCheckout.ASSETS + "blockstates/rock/rope_anchor/granite.json"), "{}");
        assertThatThrownBy(() -> RockFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("rope_anchor");
    }

    private static List<String> kindsOf(List<?> blocks) {
        List<String> out = new ArrayList<>();
        for (Object b : blocks) {
            out.add((String) ((Map<?, ?>) b).get("kind"));
        }
        return out;
    }

    /** A checkout at the pinned commit with no rock or mud sources yet. */
    private Path checkout() throws IOException {
        Path root = tmp.resolve("tfc");
        Fixture.write(root, ".git/HEAD", PIN + "\n");
        return root;
    }

    /** A checkout with the rock and mud sources of a granite (igneous) and claystone rock, and two soils. */
    private Path fullFixture() throws IOException {
        Path root = checkout();
        RockMudFixture.writeAll(root, List.of(new RockSpec("GRANITE", "FELSIC_IGNEOUS_INTRUSIVE"),
            new RockSpec("CLAYSTONE", "SEDIMENTARY")), RockMudFixture.TFC_BLOCK_TYPES, List.of("ALFISOL", "PODZOL"), true);
        return root;
    }
}
