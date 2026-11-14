/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-49: the TFC block coverage gate on the real pinned checkout. */
class BlockCoverageTest {
    private static final Pattern STATUS = Pattern.compile("(?m)^status:\\s*(\\S+)\\s*$");
    private static TfcCheckout tfc;
    private static Datagen.Result full;
    private static List<String> ids;
    private static List<BlockCoverage.Coverage> rows;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        assertThat(BlockCoverage.available(tfc)).isTrue();
        full = Datagen.generate(tfc.root(), TfcCheckout.PINNED_COMMIT);
        ids = BlockCoverage.tfcIds(tfc);
        rows = BlockCoverage.coverage(ids, full.tree());
    }

    private static JsonObject report() {
        return JsonParser.parseString(new String(full.tree().get(BlockCoverage.REPORT), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    /** The done criterion: every TFC blockstate is classified, none is unowned, and the report lists each one. */
    @Test
    void everyTfcBlockstateHasAClassAndNoneIsUnowned() {
        assertThat(ids).hasSize(4521).contains("wood/workbench/oak", "fluid/salt_water", "quern", "rock/raw/granite");
        JsonObject report = report();
        assertThat(report.get("total").getAsInt()).isEqualTo(ids.size());
        assertThat(report.getAsJsonArray("unowned")).isEmpty();
        JsonObject counts = report.getAsJsonObject("counts");
        assertThat(counts.get("unowned").getAsInt()).isZero();
        assertThat(counts.get("generated").getAsInt() + counts.get("family").getAsInt() + counts.get("owned").getAsInt()
            + counts.get("not_parity").getAsInt()).isEqualTo(ids.size());
        JsonObject blocks = report.getAsJsonObject("blocks");
        assertThat(blocks.size()).isEqualTo(ids.size());
        assertThat(blocks.get("tfc:quern").getAsString()).startsWith("ce:configuration/");
        assertThat(blocks.get("tfc:firepit").getAsString()).isEqualTo("ce:configuration/devices/firepit.yml");
        assertThat(blocks.get("tfc:grill").getAsString()).isEqualTo("family:devices/grill.yml");
        assertThat(blocks.get("tfc:wood/planks/oak_stairs").getAsString()).isEqualTo("family:families/wood.yml");
        assertThat(blocks.get("tfc:wood/workbench/oak").getAsString()).isEqualTo("PAR-DECO-17");
        assertThat(blocks.get("tfc:fluid/river_water").getAsString()).isEqualTo("np:NP-FLUID-01");
        assertThat(blocks.get("tfc:fluid/spring_water").getAsString()).isEqualTo("np:NP-FLUID-01");
        assertThat(report.get("comment").getAsString()).contains("ownership, not parity");
    }

    /** Generated wins over family and every rule; family wins over every rule. */
    @Test
    void generatedIdsAreNeverShadowedByARule() {
        Map<String, String> loaded = BlockCoverage.blockKeys(full.tree(), BlockCoverage.CONFIG);
        for (BlockCoverage.Coverage c : rows) {
            boolean isLoaded = loaded.containsKey("tfp:" + c.id());
            assertThat(c.kind() == BlockCoverage.Kind.GENERATED).as(c.id()).isEqualTo(isLoaded);
        }
        assertThat(rows.stream().filter(c -> c.kind() == BlockCoverage.Kind.GENERATED)).hasSizeGreaterThan(1300);
        assertThat(rows.stream().filter(c -> c.kind() == BlockCoverage.Kind.FAMILY)).hasSizeGreaterThan(1600);
    }

    /** Every owner names an existing, not-done parity item while it still owns ids, and no rule is stale. */
    @Test
    void ownersAreRealOpenParityItemsAndNoRuleIsStale() throws Exception {
        Path items = Path.of(System.getProperty("tfp.repoRoot", "../..")).resolve("plans/parity/items");
        Assumptions.assumeTrue(Files.isDirectory(items), "no plans/parity/items at " + items);
        Map<String, String> status = new HashMap<>();
        try (Stream<Path> s = Files.walk(items)) {
            for (Path p : s.filter(p -> p.getFileName().toString().endsWith(".md")).toList()) {
                String name = p.getFileName().toString();
                Matcher m = STATUS.matcher(Files.readString(p));
                status.put(name.substring(0, name.length() - 3), m.find() ? m.group(1) : "");
            }
        }
        Map<String, List<String>> byOwner = BlockCoverage.byOwner(rows);
        for (BlockCoverage.Owner o : BlockCoverage.OWNERS) {
            assertThat(status).as("owner item file of " + o.what()).containsKey(o.owner());
            assertThat(ids.stream().anyMatch(id -> o.pattern().matcher(id).matches())).as("stale rule " + o.pattern()).isTrue();
        }
        byOwner.forEach((owner, owned) -> assertThat(status.get(owner))
            .as(owner + " is done but still owns " + owned).isNotEqualTo("done"));
    }

    /** Every NOT-PARITY rule names an entry of NOT-PARITY.md and matches TFC blocks. */
    @Test
    void notParityRulesNameRealEntries() throws Exception {
        Path np = Path.of(System.getProperty("tfp.repoRoot", "../..")).resolve("plans/parity/NOT-PARITY.md");
        Assumptions.assumeTrue(Files.isRegularFile(np), "no NOT-PARITY.md at " + np);
        String text = Files.readString(np);
        for (BlockCoverage.Owner o : BlockCoverage.NOT_PARITY) {
            assertThat(text).as(o.what()).contains("#### " + o.owner() + ":");
            assertThat(ids.stream().anyMatch(id -> o.pattern().matcher(id).matches())).as("stale rule " + o.pattern()).isTrue();
        }
        assertThat(rows.stream().filter(c -> c.kind() == BlockCoverage.Kind.NOT_PARITY).map(BlockCoverage.Coverage::id))
            .contains("fluid/salt_water", "fluid/metal/copper", "freshwater_bubble_column")
            .allMatch(id -> id.startsWith("fluid/") || id.endsWith("_bubble_column"));
    }

    /** A new TFC block with no CE block and no rule fails the run and is named. */
    @Test
    void anUnownedBlockFailsTheRunAndIsNamed() {
        List<String> withNew = new ArrayList<>(ids);
        withNew.add("made_up_block/thing");
        OutputTree out = new OutputTree();
        full.tree().files().forEach((path, bytes) -> {
            if (!path.equals(BlockCoverage.REPORT)) {
                out.put(path, bytes);
            }
        });
        assertThatThrownBy(() -> BlockCoverage.run(withNew, out)).isInstanceOf(DatagenException.class)
            .hasMessageContaining("tfc:made_up_block/thing").hasMessageContaining("(1)");
        JsonObject written = JsonParser.parseString(new String(out.get(BlockCoverage.REPORT), StandardCharsets.UTF_8))
            .getAsJsonObject();
        assertThat(written.getAsJsonArray("unowned")).hasSize(1);
        assertThat(written.getAsJsonObject("blocks").get("tfc:made_up_block/thing").getAsString()).isEqualTo("unowned");
    }
}
