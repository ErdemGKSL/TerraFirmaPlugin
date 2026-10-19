/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetalFamiliesTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    /** TFC metals with the {@code DEFAULT} part type: one block kind (block, slab, stairs). */
    private static final Set<String> DEFAULT_ONLY = Set.of("BISMUTH", "GOLD", "NICKEL", "ROSE_GOLD", "TIN", "ZINC", "CAST_IRON");
    /** TFC metals with {@code DEFAULT_WEATHERING}: the block kinds and the three weathering ages of the block. */
    private static final Set<String> WEATHERING_DEFAULT = Set.of("BRASS", "SILVER", "STERLING_SILVER");
    /** TFC tiered metals that do not weather: the block and its grate, bars, chain, lamp and trapdoor. */
    private static final Set<String> ALL_ONLY = Set.of("BISMUTH_BRONZE", "BLACK_BRONZE", "BLACK_STEEL", "BLUE_STEEL", "RED_STEEL");
    /** TFC tiered metals that weather: all block kinds, and the grate in its three weathering ages. */
    private static final Set<String> ALL_WEATHERING = Set.of("BRONZE", "COPPER", "WROUGHT_IRON", "STEEL");
    /** TFC metals with the {@code INGOT} part type: no metal block. */
    private static final Set<String> INGOT_ONLY = Set.of("PIG_IRON", "WEAK_STEEL", "WEAK_BLUE_STEEL", "WEAK_RED_STEEL",
        "HIGH_CARBON_STEEL", "HIGH_CARBON_BLACK_STEEL", "HIGH_CARBON_BLUE_STEEL", "HIGH_CARBON_RED_STEEL", "UNKNOWN");
    private static final Set<String> BLOCK_AGES = Set.of("block", "exposed_block", "weathered_block", "oxidized_block");
    private static final Set<String> GRATE_AGES = Set.of("grate", "exposed_grate", "weathered_grate", "oxidized_grate");
    private static final Set<String> OTHER_KINDS = Set.of("bars", "chain", "lamp", "trapdoor");

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Dtfc.checkout, from -Ptfc.checkout or $TFC_CHECKOUT); skipped when absent. */
    @Test
    void realCheckoutHasNineteenMetalsWithTheirDecorationBlocks() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        TfcCheckout tfc = TfcCheckout.open(checkout, PIN);

        assertThat(MetalFamilies.metals(tfc.readString(MetalFamilies.SOURCE))).hasSize(28);
        assertThat(MetalFamilies.blockKinds(tfc.readString(MetalFamilies.SOURCE))).hasSize(21);

        List<MetalFamilies.Family> metals = MetalFamilies.families(tfc);
        Set<String> expectedMetals = new TreeSet<>();
        DEFAULT_ONLY.forEach(expectedMetals::add);
        WEATHERING_DEFAULT.forEach(expectedMetals::add);
        ALL_ONLY.forEach(expectedMetals::add);
        ALL_WEATHERING.forEach(expectedMetals::add);
        assertThat(metals).hasSize(19);
        Set<String> names = new TreeSet<>(metals.stream().map(f -> f.metal().toUpperCase(Locale.ROOT)).toList());
        assertThat(names).isEqualTo(expectedMetals);
        for (MetalFamilies.Family f : metals) {
            String name = f.metal().toUpperCase(Locale.ROOT);
            assertThat(new TreeSet<>(kindsOf(f))).as(name).isEqualTo(new TreeSet<>(expectedKinds(name)));
            assertThat(f.blocks()).as(name).hasSize(expectedCount(name));
            for (MetalFamilies.Block b : f.blocks()) {
                assertThat(b.shape()).as(name + " " + b.kind()).isIn("full", "slab", "stairs");
                if (!b.kind().endsWith("block")) {
                    assertThat(b.shape()).as(name + " " + b.kind()).isEqualTo("full");
                }
                String suffix = b.shape().equals("full") ? "" : "_" + b.shape();
                assertThat(b.path()).as(name).isEqualTo("metal/" + b.kind() + "/" + f.metal() + suffix);
            }
        }
        assertThat(metals.stream().mapToInt(f -> f.blocks().size()).sum()).isEqualTo(177);
        assertThat(metals.stream().flatMap(f -> f.blocks().stream()).map(MetalFamilies.Block::kind).toList()).doesNotContain("anvil");

        // Every blockstate of the folders is either in the output or an anvil: no extra or missing files.
        assertThat(tfc.childDirs(MetalFamilies.BLOCKSTATES)).hasSize(13);
        for (String folder : tfc.childDirs(MetalFamilies.BLOCKSTATES)) {
            long inOutput = metals.stream().flatMap(f -> f.blocks().stream()).filter(b -> b.kind().equals(folder)).count();
            long anvils = folder.equals("anvil") ? 9 : 0;
            assertThat(tfc.list(MetalFamilies.BLOCKSTATES + folder)).as(folder).hasSize((int) (inOutput + anvils));
        }
    }

    @Test
    void realCheckoutIngotOnlyMetalsHaveNoBlocks() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        TfcCheckout tfc = TfcCheckout.open(checkout, PIN);
        Set<String> names = MetalFamilies.families(tfc).stream().map(f -> f.metal().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());
        assertThat(names).doesNotContainAnyElementsOf(INGOT_ONLY);
        assertThat(INGOT_ONLY).hasSize(9);
    }

    @Test
    void fixtureFamiliesHaveTheRightKindsShapesAndYaml() throws IOException {
        Path root = checkout();
        MetalFixture.writeAll(root, MetalFixture.DEFAULT_METALS, MetalFixture.TFC_BLOCK_TYPES);
        TfcCheckout tfc = TfcCheckout.open(root, PIN);
        OutputTree out = new OutputTree();

        assertThat(MetalFamilies.run(tfc, out)).isEqualTo(3 + 12 + 8 + 20);

        String yaml = new String(out.get(MetalFamilies.OUTPUT), StandardCharsets.UTF_8);
        Map<?, ?> metals = (Map<?, ?>) ((Map<?, ?>) CeYamlCheck.parse(yaml, MetalFamilies.OUTPUT)).get("families");
        assertThat(metals.keySet().stream().map(String::valueOf).toList())
            .containsExactly("tin", "silver", "black_steel", "bronze");
        List<?> tin = (List<?>) ((Map<?, ?>) metals.get("tin")).get("blocks");
        assertThat(tin).hasSize(3);
        assertThat(new TreeSet<>(kindsOf(tin))).containsExactly("block");
        List<?> silver = (List<?>) ((Map<?, ?>) metals.get("silver")).get("blocks");
        assertThat(silver).hasSize(12);
        assertThat(new TreeSet<>(kindsOf(silver))).containsExactly("block", "exposed_block", "oxidized_block", "weathered_block");
        List<?> blackSteel = (List<?>) ((Map<?, ?>) metals.get("black_steel")).get("blocks");
        assertThat(blackSteel).hasSize(8);
        assertThat(kindsOf(blackSteel)).doesNotContain("anvil");
        List<?> bronze = (List<?>) ((Map<?, ?>) metals.get("bronze")).get("blocks");
        assertThat(bronze).hasSize(20);
        assertThat(kindsOf(bronze)).contains("exposed_grate", "oxidized_grate", "weathered_grate");

        assertThat(yaml).startsWith("# Generated by tools/datagen");
        assertThat(yaml).contains("      - kind: block\n        shape: stairs\n        id: tfp:metal/block/tin_stairs\n"
            + "        tfc: tfc:metal/block/tin_stairs\n");
        assertThat(yaml).contains("      - kind: block\n        shape: slab\n        id: tfp:metal/block/silver_slab\n");
        assertThat(yaml).contains("      - kind: grate\n        shape: full\n        id: tfp:metal/grate/bronze\n");
        assertThat(yaml).contains("      - kind: exposed_grate\n        shape: full\n        id: tfp:metal/exposed_grate/bronze\n");
        assertThat(yaml).doesNotContain("metal/anvil");
        assertThat(yaml).doesNotContain("pig_iron");
    }

    @Test
    void missingMetalSourceIsRefused() throws IOException {
        Path root = checkout();
        assertThatThrownBy(() -> MetalFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining(MetalFamilies.SOURCE);
    }

    @Test
    void emptyMetalEnumIsRefused() throws IOException {
        Path root = checkout();
        MetalFixture.writeSource(root, List.of(), MetalFixture.TFC_BLOCK_TYPES);
        assertThatThrownBy(() -> MetalFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("no constants in public enum Metal");
    }

    @Test
    void unknownPartTypeIsRefused() throws IOException {
        Path root = checkout();
        MetalFixture.writeSource(root, List.of(new MetalFixture.MetalSpec("TIN",
            "0xFF90A4BB, MapColor.COLOR_LIGHT_GRAY, Rarity.COMMON, -1, PartType.MYSTERY")), MetalFixture.TFC_BLOCK_TYPES);
        assertThatThrownBy(() -> MetalFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("MYSTERY");
    }

    @Test
    void unknownBlockPartTypeIsRefused() {
        assertThatThrownBy(() -> MetalFamilies.allows("MYSTERY", "ALL"))
            .isInstanceOf(DatagenException.class).hasMessageContaining("MYSTERY");
    }

    @Test
    void missingMetalBlockstateIsRefused() throws IOException {
        Path root = fullFixture();
        Files.delete(root.resolve(TfcCheckout.ASSETS + "blockstates/metal/block/tin_slab.json"));
        assertThatThrownBy(() -> MetalFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("metal/block/tin_slab");
    }

    @Test
    void missingAnvilFolderIsRefused() throws IOException {
        Path root = fullFixture();
        Path anvil = root.resolve(TfcCheckout.ASSETS + "blockstates/metal/anvil");
        try (Stream<Path> files = Files.list(anvil)) {
            for (Path p : files.toList()) {
                Files.delete(p);
            }
        }
        Files.delete(anvil);
        assertThatThrownBy(() -> MetalFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("Metal.BlockType folders");
    }

    @Test
    void unknownMetalBlockstateFolderIsRefused() throws IOException {
        Path root = fullFixture();
        Files.createDirectories(root.resolve(TfcCheckout.ASSETS + "blockstates/metal/mystery"));
        Files.writeString(root.resolve(TfcCheckout.ASSETS + "blockstates/metal/mystery/tin.json"), "{}");
        assertThatThrownBy(() -> MetalFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("mystery");
    }

    /** The kind (blockstate folder) of each block, in order. */
    private static List<String> kindsOf(MetalFamilies.Family f) {
        return f.blocks().stream().map(MetalFamilies.Block::kind).toList();
    }

    private static List<String> kindsOf(List<?> blocks) {
        List<String> out = new ArrayList<>();
        for (Object b : blocks) {
            out.add((String) ((Map<?, ?>) b).get("kind"));
        }
        return out;
    }

    /** The distinct kinds a metal of TFC gets, from its part type. */
    private static Set<String> expectedKinds(String metal) {
        if (DEFAULT_ONLY.contains(metal)) {
            return Set.of("block");
        }
        if (WEATHERING_DEFAULT.contains(metal)) {
            return BLOCK_AGES;
        }
        if (ALL_ONLY.contains(metal)) {
            Set<String> out = new TreeSet<>(OTHER_KINDS);
            out.addAll(Set.of("block", "grate"));
            return out;
        }
        if (ALL_WEATHERING.contains(metal)) {
            Set<String> out = new TreeSet<>(BLOCK_AGES);
            out.addAll(GRATE_AGES);
            out.addAll(OTHER_KINDS);
            return out;
        }
        throw new AssertionError("unexpected metal " + metal);
    }

    /** The number of blocks a metal gets: the block kinds, with three shapes for each block-kind age. */
    private static int expectedCount(String metal) {
        if (DEFAULT_ONLY.contains(metal)) {
            return 3;
        }
        if (WEATHERING_DEFAULT.contains(metal)) {
            return 3 + 9;
        }
        if (ALL_ONLY.contains(metal)) {
            return 3 + 1 + 4;
        }
        return 3 + 9 + 1 + 3 + 4;
    }

    /** A checkout at the pinned commit with no metal source yet. */
    private Path checkout() throws IOException {
        Path root = tmp.resolve("tfc");
        Fixture.write(root, ".git/HEAD", PIN + "\n");
        return root;
    }

    /** A checkout with the default metal part: the metal source and every blockstate it needs. */
    private Path fullFixture() throws IOException {
        Path root = checkout();
        MetalFixture.addDefault(root);
        return root;
    }
}
