/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/TFCFluids.java @ c3b4d9536, SimpleFluid.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536 (colours), exported to tfc_fluids.tsv by a scratch
 *       script outside the repo (one row per source registration: id, family, ARGB, temperature, descriptionId; the dye
 *       colours are vanilla's and checked against Bukkit in the paper FluidModuleTest).
 */
package dev.tfp.core.fluid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.core.fluid.TfcFluid.Category;
import dev.tfp.core.metal.Metal;
import dev.tfp.core.thirst.Drinkable;
import dev.tfp.core.thirst.Drinkables;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.tag.TagRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TfcFluidsTest {

    private static final String TSV = "/dev/tfp/core/fluid/tfc_fluids.tsv";

    /** The TFC sources, row by row: same order, id, family, colour, temperature and name key as TFC registers. */
    @Test
    void sourcesMatchTfcFluids() throws IOException {
        List<String[]> rows = rows();
        assertThat(rows).hasSize(TfcFluids.TFC_SOURCE_COUNT);
        List<TfcFluid> sources = TfcFluids.tfcSources();
        assertThat(sources).hasSize(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            TfcFluid f = sources.get(i);
            assertThat(f.id()).isEqualTo("tfp:" + r[0].substring(4));
            assertThat(f.category().name().toLowerCase(Locale.ROOT)).as(f.id()).isEqualTo(r[1]);
            if (!r[2].equals("-")) {
                assertThat(String.format("%08X", f.colour())).as(f.id()).isEqualTo(r[2]);
            }
            assertThat(f.temperature()).as(f.id()).isEqualTo(Integer.parseInt(r[3]));
            assertThat(f.descriptionId()).as(f.id()).isEqualTo(r[4]);
        }
    }

    /** 28 molten metals + 3 waters + 20 simple fluids + 16 dyes; then water, lava and milk. */
    @Test
    void countsPerFamily() {
        assertThat(count(Category.METAL)).isEqualTo(28);
        assertThat(count(Category.WATER)).isEqualTo(3);
        assertThat(count(Category.SIMPLE)).isEqualTo(20);
        assertThat(count(Category.DYE)).isEqualTo(16);
        assertThat(count(Category.VANILLA)).isEqualTo(3);
        assertThat(TfcFluids.all()).hasSize(70);
        assertThat(TfcFluids.all().stream().map(TfcFluid::id).distinct().count()).isEqualTo(70);
    }

    /** Every TFC fluid of datagen's id map that is not a flowing variant is in the registry, and nothing else. */
    @Test
    void sourcesMatchTheGeneratedIdMap() throws IOException {
        Path data = contentData();
        JsonObject fluids = JsonParser.parseString(Files.readString(data.resolve("id_map.json"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("fluids");
        Set<String> sources = fluids.entrySet().stream().map(e -> e.getValue().getAsString())
                .filter(id -> !id.substring(id.lastIndexOf('/') + 1).startsWith("flowing_")
                        && !id.startsWith("tfp:flowing_"))
                .collect(Collectors.toSet());
        assertThat(sources).containsExactlyInAnyOrderElementsOf(
                TfcFluids.tfcSources().stream().map(TfcFluid::id).toList());
    }

    /** Each molten metal has a {@code fluid_heat} definition, and every definition is a molten metal. */
    @Test
    void fluidHeatCoversTheMoltenMetals() throws IOException {
        Path dir = contentData().resolve("tfp/tfp/fluid_heat");
        Set<String> heated = new java.util.HashSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.toList()) {
                heated.add(JsonParser.parseString(Files.readString(p)).getAsJsonObject().get("fluid").getAsString());
            }
        }
        assertThat(heated).containsExactlyInAnyOrderElementsOf(
                TfcFluids.all().stream().filter(TfcFluid::molten).map(TfcFluid::id).toList());
    }

    @Test
    void metalLinkAndColours() {
        TfcFluid copper = TfcFluids.get("tfp:metal/copper").orElseThrow();
        assertThat(copper.metal()).contains(Metal.COPPER);
        assertThat(copper.colour()).isEqualTo(0xFFB64027);
        assertThat(copper.rgb()).isEqualTo(0xB64027);
        assertThat(copper.molten()).isTrue();
        assertThat(TfcFluids.get("tfp:metal/unknown").orElseThrow().metal()).isEmpty();
        assertThat(TfcFluids.get("tfp:beer").orElseThrow().metal()).isEmpty();
        for (Metal m : Metal.values()) {
            assertThat(TfcFluids.get(m.id())).as(m.id()).isPresent();
            assertThat(TfcFluids.get(m.id()).orElseThrow().metal()).contains(m);
        }
    }

    @Test
    void lookupAcceptsTfcNamespaceAndResolvesPeopleInput() {
        assertThat(TfcFluids.get("tfc:salt_water")).isEqualTo(TfcFluids.get("tfp:salt_water"));
        assertThat(TfcFluids.get("tfp:nope")).isEmpty();
        assertThat(TfcFluids.get(null)).isEmpty();
        assertThat(TfcFluids.resolve("tfp:milk").orElseThrow().id()).isEqualTo(TfcFluids.MILK);
        assertThat(TfcFluids.resolve("milk").orElseThrow().id()).isEqualTo(TfcFluids.MILK);
        assertThat(TfcFluids.resolve("beer").orElseThrow().id()).isEqualTo("tfp:beer");
        assertThat(TfcFluids.resolve("TFC:Metal/Copper").orElseThrow().id()).isEqualTo("tfp:metal/copper");
        assertThat(TfcFluids.resolve("minecraft:water").orElseThrow().id()).isEqualTo(TfcFluids.WATER);
        assertThat(TfcFluids.resolve("minecraft:beer")).isEmpty();
        assertThat(TfcFluids.resolve("foo:milk")).isEmpty();
        assertThat(TfcFluids.resolve(" ")).isEmpty();
    }

    @Test
    void vanillaFluids() {
        assertThat(TfcFluids.get(TfcFluids.LAVA).orElseThrow().temperature()).isEqualTo(1300);
        assertThat(TfcFluids.get(TfcFluids.WATER).orElseThrow().descriptionId()).isEqualTo("block.minecraft.water");
        assertThat(TfcFluids.get(TfcFluids.MILK).orElseThrow().descriptionId()).isEqualTo("fluid_type.minecraft.milk");
        assertThat(TfcFluids.get("tfp:river_water").orElseThrow().descriptionId()).isEqualTo("block.minecraft.water");
    }

    /** The drinkable link goes through TFC's {@code Drinkable.get} lookup. */
    @Test
    void drinkableLink() {
        Drinkable milk = new Drinkable(new FluidIngredient.Single(TfcFluids.MILK), 0, false, 10, List.of());
        Drinkables drinkables = new Drinkables(List.of(milk), TagRegistry.EMPTY);
        assertThat(TfcFluids.get(TfcFluids.MILK).orElseThrow().drinkable(drinkables)).contains(milk);
        assertThat(TfcFluids.get("tfp:lye").orElseThrow().drinkable(drinkables)).isEmpty();
    }

    private static long count(Category c) {
        return TfcFluids.all().stream().filter(f -> f.category() == c).count();
    }

    private static Path contentData() {
        String dir = System.getProperty("tfp.contentData");
        assumeTrue(dir != null && Files.isRegularFile(Path.of(dir, "id_map.json")), "datagen output not present");
        return Path.of(dir);
    }

    private static List<String[]> rows() throws IOException {
        try (InputStream in = TfcFluidsTest.class.getResourceAsStream(TSV)) {
            assertThat(in).as(TSV).isNotNull();
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            assertThat(lines.get(0)).isEqualTo("id\tkind\tcolour\ttemperature\tdescription_id");
            List<String[]> out = new ArrayList<>();
            for (String l : lines.subList(1, lines.size())) {
                String[] cells = l.split("\t", -1);
                assertThat(cells).hasSize(5);
                out.add(cells);
            }
            return out;
        }
    }
}
