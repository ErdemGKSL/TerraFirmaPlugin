/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/loot_table/entities/*.json @ c3b4d9536 (golden values only)
 */
package dev.tfp.core.hunt;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.hunt.HuntCount.AnimalYield;
import dev.tfp.core.hunt.HuntCount.Bound;
import dev.tfp.core.hunt.HuntCount.Fixed;
import dev.tfp.core.hunt.HuntCount.Uniform;
import dev.tfp.core.hunt.HuntKill.Killer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Golden tests for the animal drop tables. {@code hunt_drops_golden.tsv} in this package's test resources is exported from the 83
 * TFC loot tables at c3b4d9536 by a scratch script (kept outside the repository): one row per drop, or one {@code none} row per
 * species without drops. The tests check the file and row counts, that each row is the drop the table holds at its index, and
 * every row's yield decision for three probe kills, against values computed from the TSV alone.
 */
class HuntDropsGoldenTest {
    /** The TFC loot table files under {@code entities/}. */
    private static final int LOOT_TABLE_FILES = 83;
    /** The species with an empty loot table: a {@code none} row each. */
    private static final int EMPTY_SPECIES = 7;
    /** The drop entries, one per row of kind other than {@code none}. */
    private static final int DROP_ROWS = 158;
    private static final String RESOURCE = "/dev/tfp/core/hunt/hunt_drops_golden.tsv";

    /** A kill by a player: the animal scales to 1. */
    private static final HuntKill PLAYER = new HuntKill(Killer.PLAYER, true, false, true, 46f, 0f, 0f);
    /** A kill by a land predator at the smallest size: the animal scales to 0 and {@code not_predated} fails. */
    private static final HuntKill PREDATOR = new HuntKill(Killer.LAND_PREDATOR, false, false, false, 1f, 0f, 0f);
    /** A kill by no attacker at the smallest size: scale 0, male, no player involvement. */
    private static final HuntKill BYSTANDER = new HuntKill(Killer.NONE, false, false, true, 1f, 0f, 0f);

    record Row(String species, int index, String item, String kind, float loA, float hiA, float loB, float hiB, List<String> conditions) {}

    @Test
    void fileAndRowCountsMatchTfc() throws IOException {
        List<Row> rows = rows();
        Set<String> species = new TreeSet<>();
        int drops = 0;
        for (Row row : rows) {
            species.add(row.species());
            if (!row.kind().equals("none")) {
                drops++;
            }
        }
        assertThat(rows).hasSize(DROP_ROWS + EMPTY_SPECIES);
        assertThat(species).hasSize(LOOT_TABLE_FILES);
        assertThat(drops).isEqualTo(DROP_ROWS);
        assertThat(HuntDrops.species()).containsExactlyElementsOf(species);
        assertThat(rows.stream().filter(r -> r.kind().equals("none")).count()).isEqualTo(EMPTY_SPECIES);
    }

    @Test
    void everyRowIsTheDropAtItsIndex() throws IOException {
        Map<String, Integer> dropsPerSpecies = new HashMap<>();
        for (Row row : rows()) {
            if (row.kind().equals("none")) {
                assertThat(HuntDrops.drops(row.species())).isEmpty();
                continue;
            }
            dropsPerSpecies.merge(row.species(), 1, Integer::sum);
            List<HuntDrop> drops = HuntDrops.drops(row.species());
            assertThat(drops.size()).as(row.species()).isGreaterThanOrEqualTo(row.index());
            assertThat(drops.get(row.index() - 1)).as(row.species() + " #" + row.index()).isEqualTo(expectedDrop(row));
        }
        for (String species : HuntDrops.species()) {
            assertThat(HuntDrops.drops(species)).as(species).hasSize(dropsPerSpecies.getOrDefault(species, 0));
        }
    }

    @Test
    void yieldDecisionsMatchTheRows() throws IOException {
        for (Row row : rows()) {
            if (row.kind().equals("none")) {
                continue;
            }
            HuntDrop drop = HuntDrops.drops(row.species()).get(row.index() - 1);
            for (HuntKill probe : List.of(PLAYER, PREDATOR, BYSTANDER)) {
                int expected = expectedYield(row, probe);
                assertThat(drop.yield(probe, () -> 0d))
                    .as(row.species() + " #" + row.index() + " " + row.item() + " probe " + probe.killer())
                    .isEqualTo(expected);
            }
            if (row.kind().equals("uniform") && row.conditions().stream().noneMatch(t -> t.startsWith("random")) && passes(row, BYSTANDER)) {
                // A uniform count reaches its maximum at the top of the draw range.
                assertThat(drop.yield(BYSTANDER, () -> 0.999_999d)).isEqualTo((int) row.hiA());
            }
        }
    }

    @Test
    void speciesYieldsAreTheRowsThatPass() throws IOException {
        Map<String, List<Row>> bySpecies = new HashMap<>();
        for (Row row : rows()) {
            bySpecies.computeIfAbsent(row.species(), k -> new ArrayList<>()).add(row);
        }
        for (HuntKill probe : List.of(PLAYER, PREDATOR, BYSTANDER)) {
            for (String species : HuntDrops.species()) {
                List<String> expected = new ArrayList<>();
                for (Row row : bySpecies.get(species)) {
                    if (!row.kind().equals("none") && expectedYield(row, probe) > 0) {
                        expected.add(row.item() + "x" + expectedYield(row, probe));
                    }
                }
                List<String> actual = new ArrayList<>();
                for (HuntYield y : HuntDrops.yields(species, probe, () -> 0d)) {
                    actual.add(y.item() + "x" + y.count());
                }
                assertThat(actual).as(species + " " + probe.killer()).isEqualTo(expected);
            }
        }
    }

    /** The drop a row describes, built from the TSV columns alone. */
    private static HuntDrop expectedDrop(Row row) {
        HuntCount count = switch (row.kind()) {
            case "fixed" -> new Fixed((int) row.loA());
            case "uniform" -> new Uniform((int) row.loA(), (int) row.hiA());
            case "animal_yield" -> new AnimalYield(Bound.between(row.loA(), row.hiA()), Bound.between(row.loB(), row.hiB()));
            default -> throw new IllegalArgumentException(row.kind());
        };
        List<HuntCondition> conditions = new ArrayList<>();
        for (String token : row.conditions()) {
            conditions.add(switch (token) {
                case "not_predated" -> HuntCondition.NOT_PREDATED;
                case "is_male" -> HuntCondition.IS_MALE;
                case "killed_by_player" -> HuntCondition.KILLED_BY_PLAYER;
                case "player_kill" -> HuntCondition.PLAYER_KILL;
                default -> HuntCondition.randomChance(Float.parseFloat(token.substring("random_chance:".length())));
            });
        }
        return new HuntDrop(row.item(), count, conditions);
    }

    /** Whether every condition of a row passes for a probe, with draws of 0 (so random chances pass). */
    private static boolean passes(Row row, HuntKill kill) {
        for (String token : row.conditions()) {
            boolean ok = switch (token) {
                case "not_predated" -> kill.killer() != Killer.LAND_PREDATOR && kill.killer() != Killer.OCEAN_PREDATOR;
                case "is_male" -> kill.male();
                case "killed_by_player" -> kill.killedByPlayer();
                case "player_kill" -> kill.killedByPlayer() || kill.damageCountsAsPlayerKill();
                default -> 0d < Float.parseFloat(token.substring("random_chance:".length()));
            };
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** The expected yield of a row for a probe with every draw at 0: the minimum bounds, scaled by the probe's size. */
    private static int expectedYield(Row row, HuntKill kill) {
        if (!passes(row, kill)) {
            return 0;
        }
        return switch (row.kind()) {
            case "fixed" -> (int) row.loA();
            case "uniform" -> (int) row.loA();
            case "animal_yield" -> {
                float scale = kill.sizeScale();
                yield Math.max(0, (int) Math.floor(row.loA() + scale * (row.loB() - row.loA())));
            }
            default -> throw new IllegalArgumentException(row.kind());
        };
    }

    private static List<Row> rows() throws IOException {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = HuntDropsGoldenTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("golden TSV on the test classpath").isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] f = line.split("\t", -1);
                List<String> conditions = f[8].equals("-") ? List.of() : List.of(f[8].split(";"));
                rows.add(new Row(f[0], Integer.parseInt(f[1]), f[2], f[3],
                    Float.parseFloat(f[4]), Float.parseFloat(f[5]), Float.parseFloat(f[6]), Float.parseFloat(f[7]), conditions));
            }
        }
        return rows;
    }
}
