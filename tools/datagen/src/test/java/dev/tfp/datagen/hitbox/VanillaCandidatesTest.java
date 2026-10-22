/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.hitbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.hitbox.VanillaCandidates.Presence;
import dev.tfp.datagen.visuals.VersionFamily;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PAR-INFRA-13: the vanilla candidate table (dumped shapes per version + policy) and its agreement with the research CSV. */
class VanillaCandidatesTest {
    private final VanillaCandidates v = VanillaCandidates.load();

    @Test
    void dumpedShapesPerVersion() {
        assertThat(v.versions()).containsExactly("1.21.4", "1.21.11", "26.1.2", "26.3");
        assertThat(v.get("1.21.11", "minecraft:snow[layers=7]").selection()).isEqualTo(Shape.box(0, 0, 0, 16, 14, 16));
        assertThat(v.get("1.21.11", "minecraft:snow[layers=7]").collision()).isEqualTo(Shape.box(0, 0, 0, 16, 12, 16));
        assertThat(v.get("26.3", "minecraft:heavy_core[waterlogged=false]").selection()).isEqualTo(Shape.box(4, 0, 4, 12, 8, 12));
        assertThat(v.get("1.21.4", "minecraft:cactus[age=0]").collision()).isEqualTo(Shape.box(1, 0, 1, 15, 15, 15));
        // wildflowers and leaf_litter arrive in 1.21.5: never usable by the 1.21.x family.
        assertThat(v.get("1.21.4", "minecraft:leaf_litter[facing=north,segment_amount=1]")).isNull();
        assertThat(v.inFamily("minecraft:leaf_litter[facing=north,segment_amount=1]", VersionFamily.V1_21)).isFalse();
        assertThat(v.inFamily("minecraft:leaf_litter[facing=north,segment_amount=1]", VersionFamily.V26)).isTrue();
        // The 1.21.4 server already shapes pink petals per flower amount (one 8x3x8 quadrant for one flower).
        assertThat(v.get("1.21.4", "minecraft:pink_petals[facing=north,flower_amount=1]").selection())
            .isEqualTo(Shape.box(0, 0, 0, 8, 3, 8));
        assertThat(v.get("26.3", "minecraft:pointed_dripstone[thickness=base,vertical_direction=up,waterlogged=false]")
            .flag("random_offset")).isTrue();
        // The weighted plates have no collision; pressed (power > 0) they are 0.5 px.
        assertThat(v.get("1.21.11", "minecraft:heavy_weighted_pressure_plate[power=0]").collision().isEmpty()).isTrue();
        assertThat(v.get("1.21.11", "minecraft:heavy_weighted_pressure_plate[power=3]").selection().height()).isEqualTo(0.5);
        // Snow is replaceable only at one layer for other items (the material flag says all layers).
        assertThat(v.replaceable(v.get("1.21.11", "minecraft:snow[layers=1]"))).isTrue();
        assertThat(v.replaceable(v.get("1.21.11", "minecraft:snow[layers=7]"))).isFalse();
    }

    @Test
    void thePolicyAgreesWithTheResearchCsv() throws Exception {
        Path csv = Path.of(System.getProperty("tfp.repoRoot", "../..")).resolve("docs/research/vanilla-fullcube-supply.csv");
        List<String> lines = Files.readAllLines(csv);
        int checked = 0;
        for (String line : lines.subList(1, lines.size())) {
            String[] cols = line.split(",", -1);
            String block = "minecraft:" + cols[0];
            VanillaCandidates.BlockPolicy p = v.policy(block);
            if (p == null) {
                continue;
            }
            checked++;
            if (cols[1].equals("tfc_present")) {
                assertThat(p.presence()).as(block + " is tfc_present in the CSV").isIn(Presence.NATURAL, Presence.PLAYER);
            } else if (cols[1].equals("absent")) {
                assertThat(p.presence()).as(block + " is absent in the CSV").isEqualTo(Presence.ABSENT);
            }
        }
        assertThat(checked).as("snow, mud and smoker are in both tables").isGreaterThanOrEqualTo(3);
        assertThat(v.policy("minecraft:snow").presence()).isEqualTo(Presence.NATURAL);
    }
}
