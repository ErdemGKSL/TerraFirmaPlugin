/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.vanilla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VanillaRulesTest {
    @Test
    void defaultsAreTfcs() {
        assertThat(VanillaRules.DEFAULT).isEqualTo(new VanillaRules(false, false, false, true, false, false, false));
        assertThat(VanillaRules.fromProperties(k -> null)).isEqualTo(VanillaRules.DEFAULT);
    }

    @Test
    void propertiesOverride() {
        VanillaRules r = VanillaRules.fromProperties(Map.of("tfp.vanilla.bonemeal", "true", "tfp.vanilla.monsters", " false ")::get);
        assertThat(r.bonemeal()).isTrue();
        assertThat(r.monsters()).isFalse();
        assertThat(r.golems()).isFalse();
    }

    @Test
    void monsterSpawnDecisionFollowsTfc() {
        VanillaRules d = VanillaRules.DEFAULT;
        // underground, dark, on a spawnable block: allowed
        assertThat(d.deniesMonster(false, 0, false, true)).isFalse();
        assertThat(d.deniesMonster(false, 7, false, true)).as("lit").isTrue();
        assertThat(d.deniesMonster(true, 7, false, true)).as("slimes ignore light").isFalse();
        assertThat(d.deniesMonster(false, 0, true, true)).as("surface").isTrue();
        assertThat(d.deniesMonster(false, 0, false, false)).as("ground not in tag").isTrue();
        VanillaRules surface = new VanillaRules(false, false, false, true, true, false, false);
        assertThat(surface.deniesMonster(false, 15, true, false)).isFalse();
        VanillaRules none = new VanillaRules(false, false, false, false, true, false, false);
        assertThat(none.deniesMonster(false, 0, false, true)).isTrue();
    }
}
