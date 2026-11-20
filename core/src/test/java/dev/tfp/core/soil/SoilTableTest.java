/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.soil;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.soil.SoilTable.Config;
import dev.tfp.core.soil.SoilTable.Tool;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-05: the tool and water conversions, from DirtBlock, ConnectedGrassBlock and TFCRootedDirtBlock. */
class SoilTableTest {
    private static final Config ON = Config.DEFAULT;

    @Test
    void hoeTillsDirtClayAndGrassToFarmlandKeepingTheVariant() {
        for (String v : SoilTable.VARIANTS) {
            for (String family : new String[] {"dirt", "clay", "grass", "clay_grass"}) {
                assertThat(SoilTable.toolResult("tfp:" + family + "/" + v, Tool.HOE, ON)).contains("tfp:farmland/" + v);
            }
        }
    }

    @Test
    void shovelFlattensToPathButNotRootedDirtOrMud() {
        assertThat(SoilTable.toolResult("tfp:dirt/podzol", Tool.SHOVEL, ON)).contains("tfp:grass_path/podzol");
        assertThat(SoilTable.toolResult("tfp:clay_grass/oxisol", Tool.SHOVEL, ON)).contains("tfp:grass_path/oxisol");
        assertThat(SoilTable.toolResult("tfp:rooted_dirt/podzol", Tool.SHOVEL, ON)).isEmpty();
        assertThat(SoilTable.toolResult("tfp:mud/podzol", Tool.SHOVEL, ON)).isEmpty();
    }

    @Test
    void hoeTurnsRootedDirtBackToDirt() {
        assertThat(SoilTable.toolResult("tfp:rooted_dirt/alfisol", Tool.HOE, ON)).contains("tfp:dirt/alfisol");
        assertThat(SoilTable.toolResult("tfp:rooted_dirt/alfisol", Tool.HOE, new Config(true, true, true, false))).isEmpty();
    }

    @Test
    void configTogglesDisable() {
        Config none = new Config(false, false, false, false);
        assertThat(SoilTable.toolResult("tfp:dirt/entisol", Tool.HOE, none)).isEmpty();
        assertThat(SoilTable.toolResult("tfp:dirt/entisol", Tool.SHOVEL, none)).isEmpty();
        assertThat(SoilTable.mudOf("tfp:dirt/entisol", none)).isEmpty();
    }

    @Test
    void mudFromDirtClayAndRootedOnly() {
        assertThat(SoilTable.mudOf("tfp:dirt/mollisol", ON)).contains("tfp:mud/mollisol");
        assertThat(SoilTable.mudOf("tfp:clay/mollisol", ON)).contains("tfp:mud/mollisol");
        assertThat(SoilTable.mudOf("tfp:rooted_dirt/mollisol", ON)).contains("tfp:mud/mollisol");
        assertThat(SoilTable.mudOf("tfp:grass/mollisol", ON)).isEmpty();
        assertThat(SoilTable.mudOf("tfp:farmland/mollisol", ON)).isEmpty();
    }

    @Test
    void otherBlocksAndUnknownVariantsDoNothing() {
        assertThat(SoilTable.toolResult("tfp:rock/raw/granite", Tool.HOE, ON)).isEqualTo(Optional.empty());
        assertThat(SoilTable.toolResult("tfp:dirt/nonsense", Tool.HOE, ON)).isEmpty();
        assertThat(SoilTable.toolResult("minecraft:dirt", Tool.HOE, ON)).isEmpty();
    }

    @Test
    void grassAndDirtPairs() {
        assertThat(SoilTable.grassOf("tfp:dirt/podzol")).contains("tfp:grass/podzol");
        assertThat(SoilTable.grassOf("tfp:clay/podzol")).contains("tfp:clay_grass/podzol");
        assertThat(SoilTable.grassOf("tfp:rooted_dirt/podzol")).isEmpty();
        assertThat(SoilTable.dirtOf("tfp:grass/podzol")).contains("tfp:dirt/podzol");
        assertThat(SoilTable.dirtOf("tfp:clay_grass/podzol")).contains("tfp:clay/podzol");
        assertThat(SoilTable.isGrassBlock("tfp:grass/podzol")).isTrue();
        assertThat(SoilTable.isDirtBlock("tfp:rooted_dirt/podzol")).isFalse();
    }
}
