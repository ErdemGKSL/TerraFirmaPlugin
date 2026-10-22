/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.animal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.livestock.LivestockSpecies;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** M5-ENT-01: the carrier table covers every TFC living entity type with a known vanilla carrier. */
class CarrierTableTest {
    /**
     * Living registrations in {@code TFCEntities.java} @ c3b4d9536 (lines 139-222, every non-MISC category): 16 freshwater
     * fish from one {@code mapOf} call plus 67 single registrations. Counted by the export script and by hand.
     */
    private static final int TFC_LIVING_TYPES = 83;

    /** Vanilla 1.21 living entity types we allow as carriers, with their {@code EntityType} hitboxes. */
    private static final Map<String, float[]> VANILLA = Map.ofEntries(
            Map.entry("minecraft:cod", new float[] {0.5f, 0.3f}),
            Map.entry("minecraft:salmon", new float[] {0.7f, 0.4f}),
            Map.entry("minecraft:tropical_fish", new float[] {0.5f, 0.4f}),
            Map.entry("minecraft:pufferfish", new float[] {0.7f, 0.7f}),
            Map.entry("minecraft:squid", new float[] {0.8f, 0.8f}),
            Map.entry("minecraft:glow_squid", new float[] {0.8f, 0.8f}),
            Map.entry("minecraft:dolphin", new float[] {0.9f, 0.6f}),
            Map.entry("minecraft:turtle", new float[] {1.2f, 0.4f}),
            Map.entry("minecraft:frog", new float[] {0.5f, 0.5f}),
            Map.entry("minecraft:polar_bear", new float[] {1.4f, 1.4f}),
            Map.entry("minecraft:wolf", new float[] {0.6f, 0.85f}),
            Map.entry("minecraft:pig", new float[] {0.9f, 0.9f}),
            Map.entry("minecraft:cow", new float[] {0.9f, 1.4f}),
            Map.entry("minecraft:goat", new float[] {0.9f, 1.3f}),
            Map.entry("minecraft:llama", new float[] {0.9f, 1.87f}),
            Map.entry("minecraft:sheep", new float[] {0.9f, 1.3f}),
            Map.entry("minecraft:chicken", new float[] {0.4f, 0.7f}),
            Map.entry("minecraft:rabbit", new float[] {0.4f, 0.5f}),
            Map.entry("minecraft:armadillo", new float[] {0.7f, 0.65f}),
            Map.entry("minecraft:fox", new float[] {0.6f, 0.7f}),
            Map.entry("minecraft:panda", new float[] {1.3f, 1.25f}),
            Map.entry("minecraft:ocelot", new float[] {0.6f, 0.7f}),
            Map.entry("minecraft:camel", new float[] {1.7f, 2.375f}),
            Map.entry("minecraft:horse", new float[] {1.3964844f, 1.6f}),
            Map.entry("minecraft:donkey", new float[] {1.3964844f, 1.5f}),
            Map.entry("minecraft:mule", new float[] {1.3964844f, 1.6f}),
            Map.entry("minecraft:cat", new float[] {0.6f, 0.7f}));

    @Test
    void oneRowPerTfcLivingType() {
        List<Carrier> rows = CarrierTable.get().all();
        assertThat(rows).hasSize(TFC_LIVING_TYPES);
        Set<String> ids = new HashSet<>();
        for (Carrier c : rows) {
            assertThat(ids.add(c.id())).as("duplicate %s", c.id()).isTrue();
            assertThat(c.id()).startsWith("tfc:");
            assertThat(c.category()).isIn("WATER_AMBIENT", "WATER_CREATURE", "UNDERGROUND_WATER_CREATURE", "CREATURE");
        }
        for (LivestockSpecies s : LivestockSpecies.values()) {
            assertThat(CarrierTable.get().find(s.name().toLowerCase(java.util.Locale.ROOT)))
                    .as("livestock %s", s).hasValueSatisfying(c -> assertThat(c.livestock()).contains(s));
        }
    }

    @Test
    void everyCarrierIsAKnownVanillaLivingTypeWithItsHitbox() {
        for (Carrier c : CarrierTable.get().all()) {
            assertThat(VANILLA).as("carrier of %s", c.id()).containsKey(c.carrier());
            float[] wh = VANILLA.get(c.carrier());
            assertThat(c.carrierWidth()).as("%s width", c.carrier()).isEqualTo(wh[0]);
            assertThat(c.carrierHeight()).as("%s height", c.carrier()).isEqualTo(wh[1]);
        }
    }

    @Test
    void hitboxDeltaIsUnderTheLimitOrFlaggedWithANote() throws IOException {
        for (Carrier c : CarrierTable.get().all()) {
            boolean over = Math.abs(c.deltaWidth()) > CarrierTable.DELTA_LIMIT + 1e-6
                    || Math.abs(c.deltaHeight()) > CarrierTable.DELTA_LIMIT + 1e-6;
            assertThat(c.flagged()).as("flag of %s (delta %s x %s)", c.id(), c.deltaWidth(), c.deltaHeight())
                    .isEqualTo(over);
            if (over) {
                assertThat(c.note()).as("note of flagged %s", c.id()).isNotBlank();
            }
        }
        // The delta columns in the file agree with the hitboxes (they are there for review).
        try (InputStream in = CarrierTable.class.getResourceAsStream(CarrierTable.RESOURCE)) {
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            int checked = 0;
            for (String line : lines) {
                if (!line.startsWith("tfc:")) {
                    continue;
                }
                String[] col = line.split("\t", -1);
                Carrier c = CarrierTable.get().find(col[0]).orElseThrow();
                assertThat(Float.parseFloat(col[8])).isCloseTo(c.deltaWidth(), within(1e-5f));
                assertThat(Float.parseFloat(col[9])).isCloseTo(c.deltaHeight(), within(1e-5f));
                checked++;
            }
            assertThat(checked).isEqualTo(TFC_LIVING_TYPES);
        }
    }

    @Test
    void cowAndDeerUseTheSpikeModels() {
        Carrier cow = CarrierTable.get().find("cow").orElseThrow();
        assertThat(cow.carrier()).isEqualTo("minecraft:cow");
        assertThat(cow.modelOrFallback()).contains("tfp_example_bovine");
        assertThat(CarrierTable.get().find("tfc:deer").orElseThrow().modelOrFallback()).contains("tfp_deer");
        assertThat(CarrierTable.get().find("tfc:bluegill").orElseThrow().modelOrFallback()).isEmpty();
        assertThat(CarrierTable.get().find("tfc:nope")).isEmpty();
        assertThat(CarrierTable.get().find("tfc:deer").orElseThrow().livestock()).isEmpty();
    }
}
