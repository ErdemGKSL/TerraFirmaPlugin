/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.flammability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.flammability.FlammabilityTable.Fire;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class FlammabilityTableTest {
    static TfcCheckout pinned() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        try {
            return TfcCheckout.open(checkout, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit");
            return null;
        }
    }

    @Test
    void parsePresetsAndExplicitCalls() {
        assertThat(FlammabilityTable.parse("x.flammableLikeLogs().y")).isEqualTo(new Fire(5, 5, true));
        assertThat(FlammabilityTable.parse("x.flammableLikePlanks()")).isEqualTo(new Fire(20, 5, true));
        assertThat(FlammabilityTable.parse("x.flammableLikeLeaves()")).isEqualTo(new Fire(60, 30, true));
        assertThat(FlammabilityTable.parse("x.flammableLikeWool()")).isEqualTo(new Fire(100, 60, true));
        assertThat(FlammabilityTable.parse("x.flammable(20, 30).z")).isEqualTo(new Fire(20, 30, false));
        assertThat(FlammabilityTable.parse("x.flammable(60, 90).ignitedByLava()")).isEqualTo(new Fire(60, 90, true));
        assertThat(FlammabilityTable.parse("x.strength(1)")).isNull();
    }

    @Test
    void woodRowsMatchTfc() {
        var wood = FlammabilityTable.wood(pinned());
        assertThat(wood.get("log")).isEqualTo(new Fire(5, 5, true));
        assertThat(wood.get("stripped_log")).isEqualTo(new Fire(5, 5, true));
        assertThat(wood.get("wood")).isEqualTo(new Fire(5, 5, true));
        assertThat(wood.get("planks")).isEqualTo(new Fire(20, 5, true));
        assertThat(wood.get("leaves")).isEqualTo(new Fire(60, 30, true));
        assertThat(wood.get("barrel")).isEqualTo(new Fire(20, 5, true));
        assertThat(wood.get("axle")).isEqualTo(new Fire(5, 5, true));
        assertThat(wood.get("bookshelf")).isEqualTo(new Fire(20, 30, false));
        assertThat(wood.get("twig")).isEqualTo(new Fire(60, 30, true));
        assertThat(wood.get("fallen_leaves")).isEqualTo(new Fire(100, 60, true));
        assertThat(wood).doesNotContainKeys("gear_box", "crate", "windmill", "tool_rack");
    }

    @Test
    void plantsCropsFruitAndSingles() {
        TfcCheckout tfc = pinned();
        var plants = FlammabilityTable.plants(tfc);
        assertThat(plants.get("tfp:plant/athyrium_fern")).isEqualTo(new Fire(60, 30, false));
        assertThat(plants.get("tfp:plant/rose")).isEqualTo(new Fire(60, 30, false));
        assertThat(plants).doesNotContainKeys("tfp:plant/manatee_grass", "tfp:plant/duckweed");
        assertThat(FlammabilityTable.crops(tfc)).isEqualTo(new Fire(60, 30, false));
        assertThat(FlammabilityTable.fruit(tfc)).isEqualTo(new Fire(60, 30, true));
        var singles = FlammabilityTable.registered(tfc);
        assertThat(singles.get("tfp:thatch")).isEqualTo(new Fire(50, 100, false));
        assertThat(singles.get("tfp:log_pile")).isEqualTo(new Fire(60, 30, false));
        assertThat(singles.get("tfp:composter")).isEqualTo(new Fire(60, 90, false));
        assertThat(singles.get("tfp:barrel_rack")).isEqualTo(new Fire(20, 5, true));
        assertThat(singles.get("tfp:plant/dead_berry_bush")).isEqualTo(new Fire(120, 90, false));
        assertThat(singles).doesNotContainKeys("tfp:quern", "tfp:firepit");
    }

    @Test
    void fireOfRoutesIdsAndMappingSettings() {
        var table = FlammabilityTable.all(pinned());
        assertThat(table.fireOf("tfp:wood/log/oak")).isEqualTo(new Fire(5, 5, true));
        assertThat(table.fireOf("tfp:wood/leaves/oak")).isEqualTo(new Fire(60, 30, true));
        assertThat(table.fireOf("tfp:wood/gear_box/oak")).isNull();
        assertThat(table.fireOf("tfp:crop/wheat")).isEqualTo(new Fire(60, 30, false));
        assertThat(table.fireOf("tfp:berry_bush/dead")).isEqualTo(new Fire(120, 90, false));
        assertThat(table.fireOf("tfp:rock/raw/granite")).isNull();
        assertThat(table.fireOf("tfp:metal/anvil/bronze")).isNull();
        // vanilla FireBlock.setFlammable(block, igniteOdds, burnOdds): leaves 30/60 = TFC (flammability 60, speed 30)
        assertThat(FlammabilityPass.lines(new Fire(60, 30, true), "")).containsExactly("burnable: true", "burn_chance: 30",
            "fire_spread_chance: 60");
        assertThat(FlammabilityPass.lines(new Fire(120, 90, false), "")).contains("burn_chance: 90", "fire_spread_chance: 100");
    }

    @Test
    void editInsertsIntoBlocksOnlyAndKeepsOwnSettings() {
        var table = FlammabilityTable.all(pinned());
        String yaml = "blocks:\n  tfp:wood/log/oak:\n    settings:\n      template: x\n    state:\n      a: b\n"
            + "  tfp:wood/gear_box/oak:\n    settings:\n      template: x\n  tfp:log_pile:\n    settings:\n      burn_chance: 30\n"
            + "  tfp:thatch:\n    state:\n      a: b\nitems:\n  tfp:wood/log/oak:\n    template: y\n";
        int[] n = {0};
        String out = FlammabilityPass.edit(yaml, table, n);
        assertThat(n[0]).isEqualTo(2);
        assertThat(out).contains("  tfp:wood/log/oak:\n    settings:\n      burnable: true\n      burn_chance: 5\n"
            + "      fire_spread_chance: 5\n      template: x\n");
        assertThat(out).contains("  tfp:thatch:\n    settings:\n      burnable: true\n      burn_chance: 100\n"
            + "      fire_spread_chance: 50\n    state:");
        assertThat(out.indexOf("burnable")).isLessThan(out.indexOf("items:"));
        assertThat(out.substring(out.indexOf("items:"))).doesNotContain("burn");
    }
}
