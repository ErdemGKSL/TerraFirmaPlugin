/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536
 * (the capacity defaults, from the define(...) lines), src/main/java/net/dries007/tfc/common/items/TFCItems.java @ c3b4d9536
 * (the whitelist tag of each container), and the generated tags in src/generated/resources/data/tfc/tags/fluid/usable_in_*.json
 * (11 files, exported to usable_in_tags.tsv).
 */
package dev.tfp.core.fluid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.barrel.BarrelRules;
import dev.tfp.core.crucible.CrucibleRules;
import dev.tfp.core.vessel.JugRules;
import dev.tfp.core.vessel.VesselRules;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FluidContainerKindTest {

    private static final String TAGS = "/dev/tfp/core/fluid/usable_in_tags.tsv";

    /** The TFC capacity defaults, one row per container, from the {@code ServerConfig.define} default of each. */
    @Test
    void defaultCapacitiesMatchTheServerConfigDefaults() {
        assertThat(FluidContainerKind.WOODEN_BUCKET.defaultCapacity()).isEqualTo(1000);
        assertThat(FluidContainerKind.RED_STEEL_BUCKET.defaultCapacity()).isEqualTo(1000);
        assertThat(FluidContainerKind.BLUE_STEEL_BUCKET.defaultCapacity()).isEqualTo(1000);
        assertThat(FluidContainerKind.JUG.defaultCapacity()).isEqualTo(100);
        assertThat(FluidContainerKind.SILICA_GLASS_BOTTLE.defaultCapacity()).isEqualTo(500);
        assertThat(FluidContainerKind.HEMATITIC_GLASS_BOTTLE.defaultCapacity()).isEqualTo(400);
        assertThat(FluidContainerKind.VOLCANIC_GLASS_BOTTLE.defaultCapacity()).isEqualTo(400);
        assertThat(FluidContainerKind.OLIVINE_GLASS_BOTTLE.defaultCapacity()).isEqualTo(400);
        assertThat(FluidContainerKind.BARREL.defaultCapacity()).isEqualTo(10_000);
        assertThat(FluidContainerKind.SMALL_VESSEL.defaultCapacity()).isEqualTo(3000);
        assertThat(FluidContainerKind.CRUCIBLE.defaultCapacity()).isEqualTo(4000);
        assertThat(FluidContainerKind.LAMP.defaultCapacity()).isEqualTo(250);
    }

    /** The capacities that another rules class owns must agree with the table, so one number is not kept twice. */
    @Test
    void sharedCapacitiesComeFromTheOwningRules() {
        assertThat(FluidContainerKind.JUG.defaultCapacity()).isEqualTo(JugRules.CAPACITY);
        assertThat(FluidContainerKind.BARREL.defaultCapacity()).isEqualTo(BarrelRules.DEFAULT_CAPACITY);
        assertThat(FluidContainerKind.SMALL_VESSEL.defaultCapacity()).isEqualTo(VesselRules.SMALL_VESSEL_CAPACITY);
        assertThat(FluidContainerKind.CRUCIBLE.defaultCapacity()).isEqualTo(CrucibleRules.DEFAULT_CAPACITY);
    }

    /** The buckets default to {@code FluidHelpers.BUCKET_VOLUME}, which is 1000 mB. */
    @Test
    void bucketsAreOneBucketVolume() {
        assertThat(FluidContainerKind.WOODEN_BUCKET.defaultCapacity()).isEqualTo(1000);
        assertThat(FluidContainerKind.values()).hasSize(12);
    }

    /** Containers whose predicate is code have no tag. Every other container's tag is one of the exported files. */
    @Test
    void usableTagsAreTheExportedTfcTags() throws IOException {
        Map<String, String> tags = readTags();
        assertThat(tags).hasSize(11);
        for (FluidContainerKind kind : FluidContainerKind.values()) {
            if (kind.usableTag().isPresent()) {
                assertThat(tags).as(kind.name()).containsKey(stripNamespace(kind.usableTag().get()));
            }
        }
        assertThat(FluidContainerKind.SMALL_VESSEL.usableTag()).isEmpty();
        assertThat(FluidContainerKind.CRUCIBLE.usableTag()).isEmpty();
        assertThat(FluidContainerKind.LAMP.usableTag()).isEmpty();
    }

    /** The jug tag is the drinkables, and the glass bottles are whitelisted with it in TFC's TFCItems. */
    @Test
    void jugTagAndBottlesUseTheSameWhitelist() throws IOException {
        Map<String, String> tags = readTags();
        assertThat(tags.get("usable_in_jug")).isEqualTo("[\"#tfc:drinkables\"]");
        assertThat(FluidContainerKind.SILICA_GLASS_BOTTLE.usableTag()).contains("tfc:usable_in_jug");
        assertThat(FluidContainerKind.HEMATITIC_GLASS_BOTTLE.usableTag()).contains("tfc:usable_in_jug");
        assertThat(FluidContainerKind.VOLCANIC_GLASS_BOTTLE.usableTag()).contains("tfc:usable_in_jug");
        assertThat(FluidContainerKind.OLIVINE_GLASS_BOTTLE.usableTag()).contains("tfc:usable_in_jug");
        assertThat(FluidContainerKind.JUG.usableTag()).contains("tfc:usable_in_jug");
    }

    private static String stripNamespace(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /** Reads the exported TSV: a header, then one row per {@code tfc:usable_in_*} file. */
    static Map<String, String> readTags() throws IOException {
        Map<String, String> out = new HashMap<>();
        try (InputStream stream = FluidContainerKindTest.class.getResourceAsStream(TAGS)) {
            assertThat(stream).as(TAGS).isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            assertThat(lines.get(0)).isEqualTo("tag\tvalues");
            for (String row : lines.subList(1, lines.size())) {
                String[] cells = row.split("\t", -1);
                assertThat(cells).hasSize(2);
                out.put(cells[0], cells[1]);
            }
        }
        return out;
    }
}
